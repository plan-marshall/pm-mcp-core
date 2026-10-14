/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.audit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import javax.crypto.SecretKey;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.AuditAnchor;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditHeadRecord;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The head of the audit log {@code logs/audit.head} (PM-SEC-6; Store Schemas, Audit Head) and the anchor of the
 * writer: it fills {@link AuditAnchor}, which {@link AuditLog} calls under the audit lock in every append.
 * <ul>
 * <li>Before an append it lets {@link AuditRotation} rotate the log when a limit is reached, and complete a pending
 * rotation, one interrupted after its checkpoint.</li>
 * <li>When the log holds no complete line, the line chains to the {@code checkpoint_hash} of the last entry of the
 * {@link AuditIndex}, the checkpoint that closed the rotated log.</li>
 * <li>After every append it writes the head: the record count, the last event id, the SHA-256 of the last line and of
 * the last index line, and the HMAC-SHA-256 over the canonical JSON of these fields under the enrolment integrity key.
 * The head is replaced atomically, forced to disk, readable and writable by its owner only.</li>
 * </ul>
 * The key is only read ({@link EnrolmentKey#read()}), never created, and kept once present, since it is never
 * replaced; reading it takes no lock, so the audit lock stays the leaf of the lock order. While no key exists (no
 * enrolment yet), no head is written and no rotation starts, the append stands, and nothing is logged; the first
 * append after the key exists writes the head over the whole log. A key that cannot be read, or a head that cannot be
 * written, is logged as a warning naming the reason code, never the key, and the append stands: a gap of the head is
 * what the next verification reports. The one failure that is thrown into the append is a rotation that stays
 * pending after its checkpoint, because no record may follow a checkpoint.
 *
 * @since 0.1
 */
public final class AuditHead implements AuditAnchor {

    /** The name of the head file beside the log. */
    public static final String FILE_NAME = "audit.head";

    /** The reason a head is not written over a log that no longer holds the line the previous head anchored. */
    static final String ANCHORED_LINE_MISSING = "anchored_line_missing";

    private static final CuiLogger LOGGER = new CuiLogger(AuditHead.class);

    private final EnrolmentKey integrityKey;
    private final AuditIndex index;
    private final AuditRotation rotation;
    private final AtomicStoreFile store;
    private volatile SecretKey cachedKey;

    /**
     * @param base         the base directory of the runtime
     * @param integrityKey the enrolment integrity key, only read
     * @param limits       the limits of the rotation; {@link AuditRotation.Limits#DEFAULTS} by default
     * @param clock        the clock of the age of the log and of the checkpoint record
     * @param osPrincipal  the OS principal of the runtime, the actor of the records the rotation writes
     * @throws NullPointerException if an argument is {@code null}
     */
    public AuditHead(Path base, EnrolmentKey integrityKey, AuditRotation.Limits limits, Clock clock,
            String osPrincipal) {
        this.integrityKey = Objects.requireNonNull(integrityKey, "integrityKey");
        this.index = new AuditIndex(base);
        this.rotation = new AuditRotation(index, limits, clock, osPrincipal);
        this.store = new AtomicStoreFile(headFile(base), LockKey.auditLog(base));
    }

    /**
     * @param base the base directory of the runtime
     * @return the head file {@code <base>/logs/audit.head}
     */
    public static Path headFile(Path base) {
        return LockKey.auditLog(Objects.requireNonNull(base, "base")).lockFile().resolveSibling(FILE_NAME);
    }

    /** @return the head file */
    public Path file() {
        return store.store();
    }

    /** @return the audit index the rotation enters the rotated logs in */
    public AuditIndex index() {
        return index;
    }

    /**
     * Rotates the log when a limit is reached and the key is present, and completes a pending rotation with or without
     * the key. A rotation that cannot be carried out before its checkpoint is logged, and the record is appended to
     * the current log. A rotation that stays pending after its checkpoint is logged and thrown: no record may follow
     * the checkpoint, so the record is not appended.
     *
     * @throws UncheckedIOException if a rotation stays pending after its checkpoint
     * @throws StoreRefusal         if a rotation stays pending after its checkpoint because an intent file is
     *                              unreadable
     */
    @Override
    public void beforeAppend(LockTransaction transaction, AuditLog log) {
        var key = key(false);
        try {
            if (key.isPresent()) {
                rotation.rotateIfDue(transaction, log, key.get());
            } else {
                rotation.completePending(transaction, log);
            }
        } catch (UncheckedIOException | StoreRefusal e) {
            if (stillPending(log, e)) {
                LOGGER.warn(e, WARN.AUDIT_ROTATION_PENDING, e.getMessage());
                throw e;
            }
            LOGGER.warn(e, WARN.AUDIT_ROTATION_FAILED, e.getMessage());
        }
    }

    @Override
    public Optional<String> emptyLogPredecessor() {
        return index.snapshot().lastCheckpointHash();
    }

    /**
     * Writes the head over the log as it is after the append. Without a key nothing is written; a key that cannot be
     * read or a head that cannot be written is logged. A head is never written over a log that no longer holds the
     * line the previous head anchored: the previous head stays, so the verification reports the cut, and the gap is
     * logged.
     */
    @Override
    public void afterAppend(LockTransaction transaction, AuditLog log, AuditRecord appended, String lineHash) {
        var key = key(true);
        if (key.isEmpty()) {
            return;
        }
        try {
            var indexSnapshot = index.read(transaction);
            var recordCount = recordCount(transaction, log, appended, key.get(), indexSnapshot);
            if (recordCount.isEmpty()) {
                LOGGER.warn(WARN.AUDIT_HEAD_NOT_WRITTEN, ANCHORED_LINE_MISSING);
                return;
            }
            var unsigned = new AuditHeadRecord(AuditHeadRecord.FILE, recordCount.getAsLong(), appended.eventId(),
                    lineHash, indexSnapshot.headHash(), AuditCodec.GENESIS_HASH);
            store.write(transaction, AuditCodec.writeHead(sign(key.get(), unsigned)));
        } catch (UncheckedIOException | StoreRefusal e) {
            LOGGER.warn(e, WARN.AUDIT_HEAD_NOT_WRITTEN, "head_unwritable");
        }
    }

    /**
     * Reads a head without a lock.
     *
     * @param headFile the head file
     * @return the head; empty if the file does not exist
     * @throws StoreRefusal         if the head does not parse as a head of the current format version
     * @throws UncheckedIOException if the head exists and cannot be read
     */
    public static Optional<AuditHeadRecord> read(Path headFile) {
        try {
            return Optional.of(AuditCodec.readHead(headFile, Files.readAllBytes(headFile)));
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("The audit head cannot be read: " + headFile, e);
        }
    }

    /**
     * @param key      the enrolment integrity key
     * @param unsigned a head; its {@code hmac} is not read
     * @return the head with the HMAC-SHA-256 over its other fields under the key
     */
    public static AuditHeadRecord sign(SecretKey key, AuditHeadRecord unsigned) {
        return unsigned.withHmac(EnrolmentHmac.hmac(key, AuditCodec.signedContent(unsigned)));
    }

    /**
     * Compares the HMAC of a head with the HMAC of its other fields in constant time.
     *
     * @param key  the enrolment integrity key
     * @param head a head
     * @return whether the head was signed under the key and is unchanged since
     */
    public static boolean verifies(SecretKey key, AuditHeadRecord head) {
        return EnrolmentHmac.verify(key, AuditCodec.signedContent(head), head.hmac());
    }

    /**
     * The number of records after the append: one more than the previous head counts when that head verifies and
     * anchors exactly the line the appended record chains to. Otherwise the lines of the log are counted, but only
     * where the count cannot hide a cut: no verifying previous head, a rotation since it (the last index entry chains
     * to the index line it anchored), or a log that still holds the line it anchored at its position, the gap a stop
     * between an append and its head leaves.
     *
     * @return the record count; empty when the log no longer holds the line the previous head anchored
     */
    private OptionalLong recordCount(LockTransaction transaction, AuditLog log, AuditRecord appended, SecretKey key,
            AuditIndex.Snapshot indexSnapshot) {
        var previous = verifyingHead(key);
        if (previous.isEmpty()) {
            return OptionalLong.of(log.recordCount(transaction));
        }
        var anchored = previous.get();
        if (anchored.headHash().equals(appended.prevHash())) {
            return OptionalLong.of(anchored.recordCount() + 1);
        }
        if (indexSnapshot.last().map(AuditIndexEntry::prevHash).filter(anchored.indexHeadHash()::equals).isPresent()) {
            return OptionalLong.of(log.recordCount(transaction));
        }
        var lines = log.read(transaction).lines();
        var position = anchored.recordCount();
        if (position >= 1 && position <= lines.size()
                && AuditCodec.sha256Hex(lines.get(Math.toIntExact(position - 1)).content())
                .equals(anchored.headHash())) {
            return OptionalLong.of(lines.size());
        }
        return OptionalLong.empty();
    }

    /** @return the head on disk when it parses and verifies under the key */
    private Optional<AuditHeadRecord> verifyingHead(SecretKey key) {
        try {
            return read(file()).filter(head -> verifies(key, head));
        } catch (StoreRefusal _) {
            // a head that does not parse anchors nothing; the lines are counted
            return Optional.empty();
        }
    }

    /**
     * Whether the failed rotation left the log closed by its checkpoint. A log that cannot be read after the failure
     * counts as pending, so no record is appended behind a checkpoint that could not be ruled out.
     */
    private static boolean stillPending(AuditLog log, RuntimeException failure) {
        try {
            return AuditRotation.pending(log);
        } catch (UncheckedIOException readFailure) {
            failure.addSuppressed(readFailure);
            return true;
        }
    }

    private Optional<SecretKey> key(boolean warn) {
        var key = cachedKey;
        if (key != null) {
            return Optional.of(key);
        }
        try {
            var read = integrityKey.read();
            read.ifPresent(present -> cachedKey = present);
            return read;
        } catch (SecretStoreException e) {
            if (warn) {
                LOGGER.warn(WARN.AUDIT_HEAD_NOT_WRITTEN, e.reason().code());
            }
            return Optional.empty();
        }
    }
}
