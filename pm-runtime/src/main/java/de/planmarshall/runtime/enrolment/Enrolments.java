/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LiveWork;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;

/**
 * The operator writes of the enrolment store (PM-EXEC-2, PM-MIG-1, PM-IMPL-7): {@code enrol}, {@code unenrol} and the
 * re-baseline of {@code accept-edit enrolment}, which the runtime executes on an operator verb only.
 * <p>
 * Each write runs in one transaction that holds the project's enrolment lock, so two writes of one project never
 * interleave the file and its sidecar, and every write leaves the file and a sidecar record over its exact bytes
 * (size, SHA-256 and HMAC under the integrity key), or neither. {@code unenrol} asks for the project's live work while
 * it holds that lock, so the answer and the removal belong to one transaction; a future scope-creation path must close
 * the remaining window by reading the enrolment under the same lock. Every write is logged and handed to the audit
 * sink.
 *
 * @since 0.1
 */
public final class Enrolments {

    private static final CuiLogger LOGGER = new CuiLogger(Enrolments.class);

    private final EnrolmentStore store;
    private final EnrolmentKey key;
    private final EnrolmentFileCheck fileCheck;
    private final LockManager lockManager;
    private final LiveWork liveWork;
    private final AuditSink auditSink;

    /**
     * @param store       the enrolment store
     * @param key         the enrolment integrity key
     * @param fileCheck   the per-use checks of the file a re-baseline reads
     * @param lockManager the lock manager of the runtime
     * @param liveWork    the live work of the projects, which refuses an {@code unenrol}
     * @param auditSink   where every write and refusal is recorded
     */
    public Enrolments(EnrolmentStore store, EnrolmentKey key, EnrolmentFileCheck fileCheck, LockManager lockManager,
            LiveWork liveWork, AuditSink auditSink) {
        this.store = Objects.requireNonNull(store, "store");
        this.key = Objects.requireNonNull(key, "key");
        this.fileCheck = Objects.requireNonNull(fileCheck, "fileCheck");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.liveWork = Objects.requireNonNull(liveWork, "liveWork");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    /**
     * Writes the project's enrolment file and its sidecar record. The written enrolment is a pilot exactly if
     * {@code pilot} is set, and is not released: enrolling a released project again clears its release.
     *
     * @param enrolment the enrolment the operator confirmed
     * @param pilot     whether the project is enrolled as a pilot of the lifecycle gate ({@code --pilot})
     * @return the enrolment as written
     * @throws SecretStoreException if the integrity key cannot be read or created; nothing was written then
     * @throws UncheckedIOException if a file cannot be written or the record cannot be appended to the audit log
     */
    public ProjectEnrolment enrol(ProjectEnrolment enrolment, boolean pilot) {
        Objects.requireNonNull(enrolment, "enrolment");
        var written = asEnrolled(enrolment, pilot);
        var projectId = written.projectId();
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            var sidecar = EnrolmentHmac.sidecar(key.readOrCreate(), EnrolmentCodec.write(written));
            store.write(transaction, written, sidecar);
        }
        LOGGER.info(INFO.ENROLMENT_WRITTEN, projectId, pilot);
        auditSink.append(AuditEvent.enrolmentWritten(projectId));
        return written;
    }

    /**
     * Removes the project's enrolment file and its sidecar, and nothing else: the project's store below
     * {@code projects/<project_id>/} stays as it is.
     *
     * @param projectId the project
     * @return whether the enrolment file or its sidecar existed
     * @throws ScopeActiveException if the project has live work; nothing was removed then
     * @throws UncheckedIOException if a file cannot be removed or the record cannot be appended to the audit log
     */
    public boolean unenrol(String projectId) {
        boolean removed;
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            var items = List.copyOf(liveWork.of(projectId));
            if (!items.isEmpty()) {
                var refusal = new ScopeActiveException(projectId, items);
                LOGGER.warn(WARN.UNENROL_REFUSED, projectId, refusal.namedItems());
                auditSink.append(AuditEvent.unenrolRefused(projectId));
                throw refusal;
            }
            removed = store.remove(transaction, projectId);
        }
        if (removed) {
            LOGGER.info(INFO.ENROLMENT_REMOVED, projectId);
            auditSink.append(AuditEvent.enrolmentRemoved(projectId));
        }
        return removed;
    }

    /**
     * Re-baselines the project's enrolment file the operator reviewed ({@code accept-edit enrolment}): rewrites its
     * sidecar record over the current bytes of the file. The file must pass the per-use checks and read strictly as
     * an enrolment of the project; nothing else of it is changed.
     *
     * @param projectId the project
     * @return the recorded and the current size and SHA-256 of the file, for the operator's display
     * @throws StoreRefusal         as {@code absent} if the file does not exist, as {@code enrolment_insecure} if it
     *                              fails a per-use check, as {@code unreadable}, {@code format_outdated} or
     *                              {@code format_newer} if it does not read strictly; nothing was written then
     * @throws SecretStoreException if the integrity key cannot be read or created; nothing was written then
     * @throws UncheckedIOException if a file cannot be read or written or the record cannot be appended to the audit
     *                              log
     */
    public Rebaseline rebaseline(String projectId) {
        var file = store.file(projectId);
        Optional<StoreDigest> recorded;
        StoreDigest current;
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            var content = store.readFile(transaction, projectId)
                    .orElseThrow(() -> StoreRefusal.absent(file, ProjectEnrolment.FORMAT_VERSION));
            fileCheck.check(file);
            store.decode(projectId, content);
            recorded = store.readSidecar(transaction, projectId).flatMap(bytes -> recordedDigest(projectId, bytes));
            var sidecar = EnrolmentHmac.sidecar(key.readOrCreate(), content);
            store.writeSidecar(transaction, projectId, sidecar);
            current = sidecar.digest();
        }
        LOGGER.info(INFO.ENROLMENT_REBASELINED, projectId);
        auditSink.append(AuditEvent.enrolmentRebaselined(projectId));
        return new Rebaseline(file, recorded, current);
    }

    private Optional<StoreDigest> recordedDigest(String projectId, byte[] sidecar) {
        try {
            return Optional.of(store.decodeSidecar(projectId, sidecar).digest());
        } catch (StoreRefusal _) {
            return Optional.empty();
        }
    }

    private static ProjectEnrolment asEnrolled(ProjectEnrolment enrolment, boolean pilot) {
        return new ProjectEnrolment(enrolment.projectId(), enrolment.projectRoot(), enrolment.enrolledAt(), pilot,
                enrolment.containerEngine(), null, enrolment.wrappers(), enrolment.hostTools(), enrolment.build(),
                enrolment.providers(), enrolment.projectScripts(), enrolment.languageServers(),
                enrolment.commandBudgets(), enrolment.writePaths(), enrolment.buildEnvironment());
    }

    /**
     * The outcome of a re-baseline: what the operator reviewed against what was recorded.
     *
     * @param file     the re-baselined enrolment file
     * @param recorded the size and SHA-256 the old sidecar recorded; empty if there was no readable sidecar
     * @param current  the size and SHA-256 of the file, as the new sidecar records them
     * @since 0.1
     */
    public record Rebaseline(Path file, Optional<StoreDigest> recorded, StoreDigest current) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Rebaseline {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(recorded, "recorded");
            Objects.requireNonNull(current, "current");
        }
    }

    /**
     * An {@code unenrol} refused with {@code scope_active} because the project has live work; it names every item.
     *
     * @since 0.1
     */
    public static final class ScopeActiveException extends RuntimeException {

        /** The outcome code of the refusal. */
        public static final String CODE = "scope_active";

        @Serial
        private static final long serialVersionUID = 1L;

        /** The project whose {@code unenrol} was refused. */
        private final String projectId;

        /** The live work; a live work item is not serializable, so it is not kept across serialization. */
        private final transient List<LiveWork.Item> items;

        /**
         * @param projectId the project whose {@code unenrol} was refused
         * @param items     the live work of the project, not empty
         */
        ScopeActiveException(String projectId, List<LiveWork.Item> items) {
            super("unenrol of '%s' refused with %s, live work: %s".formatted(projectId, CODE, names(items)));
            this.projectId = projectId;
            this.items = List.copyOf(items);
        }

        /** @return the project whose {@code unenrol} was refused */
        public String projectId() {
            return projectId;
        }

        /** @return the live work of the project; empty only after the refusal was deserialized */
        public List<LiveWork.Item> items() {
            return items == null ? List.of() : items;
        }

        String namedItems() {
            return names(items());
        }

        private static String names(List<LiveWork.Item> items) {
            return items.stream().map(LiveWork.Item::toString).collect(Collectors.joining(", "));
        }
    }
}
