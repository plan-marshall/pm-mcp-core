/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.Mac;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.FileSecretStore;
import de.planmarshall.runtime.enrolment.EnrolmentCheck;
import de.planmarshall.runtime.enrolment.EnrolmentFileCheck;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.Enrolments;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The real enrolment store of the recovery tests: store, key file, lock manager, check and operator writes over one
 * base, with an audit sink that records, refuses or stops on demand.
 */
final class EnrolmentFixture {

    static final String USER = System.getProperty("user.name");

    /** What the audit sink does with an append. */
    enum SinkMode {
        WORKING, REFUSING, STOPPING
    }

    /** What a killed process does to its caller: an unchecked failure that is not a refused append. */
    static final class ProcessStopped extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        ProcessStopped() {
            super("process stopped at the audit append");
        }
    }

    final Path base;
    final List<AuditEvent> events = new CopyOnWriteArrayList<>();
    final AuditSink sink;
    final EnrolmentStore store;
    final EnrolmentKey key;
    final FileLockManager locks;
    final EnrolmentCheck check;
    final Enrolments enrolments;
    volatile SinkMode mode = SinkMode.WORKING;

    EnrolmentFixture(Path base) throws IOException {
        this.base = base;
        Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
        var state = Files.createDirectories(base.resolve("state"));
        sink = this::append;
        store = new EnrolmentStore(base);
        key = new EnrolmentKey(new FileSecretStore(base.resolve("credentials")), state, USER, new SecureRandom());
        locks = new FileLockManager(sink);
        check = new EnrolmentCheck(store, key, new EnrolmentFileCheck(USER), sink);
        enrolments = new Enrolments(store, key, new EnrolmentFileCheck(USER), locks, projectId -> List.of(), sink);
    }

    private void append(AuditEvent event) {
        switch (mode) {
            case WORKING -> events.add(event);
            case REFUSING -> throw new UncheckedIOException(new IOException("the audit log cannot be written"));
            case STOPPING -> throw new ProcessStopped();
        }
    }

    static ProjectEnrolment enrolment(String projectId, boolean pilot) {
        return new ProjectEnrolment(projectId, "/home/dev/git/" + projectId, Instant.parse("2026-10-11T08:00:00Z"),
                pilot, null, null, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    ProjectEnrolment enrol(String projectId, boolean pilot) {
        return enrolments.enrol(enrolment(projectId, pilot), pilot);
    }

    ProjectEnrolment verify(String projectId) {
        try (var transaction = locks.openTransaction()) {
            return check.verify(transaction, projectId);
        }
    }

    /**
     * Leaves what an unenrol stopped between the removal of the enrolment file and of its sidecar leaves: the sidecar
     * without its file and the unconfirmed {@code enrolment_removed} record owed in the project's intent, as the
     * unenrol owes it before its mutation.
     *
     * @return the owed unconfirmed record
     */
    AuditEvent interruptUnenrol(String projectId) throws IOException {
        enrol(projectId, false);
        var unconfirmed = AuditEvent
                .enrolmentRemoved(projectId, TransactionId.generate(Clock.systemUTC()).value()).notConfirmed();
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(store.lockKey(projectId));
            store.oweAudit(transaction, projectId, unconfirmed);
        }
        Files.delete(store.file(projectId));
        return unconfirmed;
    }

    /** Replaces the project's sidecar by a directory with an entry, so that it cannot be deleted. */
    Path undeletableSidecar(String projectId) throws IOException {
        Files.delete(store.sidecar(projectId));
        var blocker = Files.createDirectory(store.sidecar(projectId));
        Files.writeString(blocker.resolve("entry"), projectId);
        return blocker;
    }

    /** @return the staged name a stopped write of the transaction leaves for the target */
    static Path staged(Path target, TransactionId transactionId) {
        return target.resolveSibling(target.getFileName() + ".tmp." + transactionId.value());
    }

    /** @return the sidecar record of the content under the integrity key, as the runtime writes it */
    byte[] signedSidecar(byte[] content) {
        try {
            var mac = Mac.getInstance(EnrolmentKey.ALGORITHM);
            mac.init(key.read().orElseThrow());
            var hmac = HexFormat.of().formatHex(mac.doFinal(content));
            return new EnrolmentSidecar(StoreDigest.of(content), hmac).write();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    List<String> names() throws IOException {
        try (var entries = Files.list(store.directory())) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }
}
