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
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
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
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JsonlStoreFile;
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
 * rotation, one interrupted after its checkpoint, when that checkpoint verifies under the key.</li>
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
 * pending after its checkpoint, because no record may follow a checkpoint; a pending checkpoint that does not verify,
 * or cannot be verified without the key, stays pending as well.
 * <p>
 * Since the head is replaced atomically, the one head that anchors nothing is an absent head file: the lines are then
 * counted. A head file that exists but does not parse or does not verify under the key is never replaced, neither by a
 * recount nor by a rotation: it stays in place, so the verification reports it, and the gap is logged with the reason
 * {@value #HEAD_UNVERIFIABLE}. Nor is a log closed by a checkpoint while it no longer holds the line a verifying head
 * anchors ({@link #closeCheck(SecretKey)}): a checkpoint signs the record count of the log, so one over a cut log
 * would sign the cut.
 *
 * @since 0.1
 */
public final class AuditHead implements AuditAnchor {

    /** The name of the head file beside the log. */
    public static final String FILE_NAME = "audit.head";

    /** The reason a head is not written over a log that no longer holds the line the previous head anchored. */
    static final String ANCHORED_LINE_MISSING = "anchored_line_missing";

    /** The reason a head is not written, and a log not rotated, while the head file does not parse or verify. */
    static final String HEAD_UNVERIFIABLE = "head_unverifiable";

    /** The reason a head is not written because the head cannot be read or written. */
    static final String HEAD_UNWRITABLE = "head_unwritable";

    private static final CuiLogger LOGGER = new CuiLogger(AuditHead.class);

    private final EnrolmentKey integrityKey;
    private final AuditIndex index;
    private final AuditRotation rotation;
    private final AtomicStoreFile store;
    private final LockKey lockKey;
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
        this.lockKey = LockKey.auditLog(base);
        this.store = new AtomicStoreFile(headFile(base), lockKey);
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
     * Rotates the log when a limit is reached, the key is present and the {@link #closeCheck(SecretKey)} lets the log
     * be closed, and completes a pending rotation when the key is present and its checkpoint verifies under it. A
     * rotation that cannot be carried out before its checkpoint, or that the check refuses, is logged, and the record is
     * appended to the current log. A rotation that stays pending after its checkpoint is logged and thrown: no record
     * may follow the checkpoint, so the record is not appended. So is a pending rotation whose checkpoint does not
     * verify, or cannot be verified because the key is absent or unreadable: it is logged with the reason
     * {@value AuditRotation#CHECKPOINT_UNVERIFIABLE}, and the log stays as it is, so the verification reports it.
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
                rotation.rotateIfDue(transaction, log, key.get(), closeCheck(key.get()));
            } else {
                AuditRotation.refusePending(log);
            }
        } catch (AuditRotation.PendingRefusal e) {
            LOGGER.warn(WARN.AUDIT_ROTATION_PENDING, e.reason());
            throw e;
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
     * read or a head that cannot be read or written is logged. A head file that does not parse or does not verify is
     * never replaced: it stays, so the verification reports it, and the gap is logged. Nor is a head written over a
     * log that no longer holds the line the previous head anchored: the previous head stays, so the verification
     * reports the cut, and the gap is logged.
     */
    @Override
    public void afterAppend(LockTransaction transaction, AuditLog log, AuditRecord appended, String lineHash) {
        var key = key(true);
        if (key.isEmpty()) {
            return;
        }
        try {
            var indexSnapshot = index.read(transaction);
            var previous = previous(key.get());
            if (previous instanceof Previous.Unverifiable) {
                LOGGER.warn(WARN.AUDIT_HEAD_NOT_WRITTEN, HEAD_UNVERIFIABLE);
                return;
            }
            var recordCount = previous instanceof Previous.Verifying(var anchored)
                    ? recordCount(key.get(), transaction, log, appended, anchored, indexSnapshot)
                    : OptionalLong.of(log.recordCount(transaction));
            if (recordCount.isEmpty()) {
                LOGGER.warn(WARN.AUDIT_HEAD_NOT_WRITTEN, ANCHORED_LINE_MISSING);
                return;
            }
            var unsigned = new AuditHeadRecord(AuditHeadRecord.FILE, recordCount.getAsLong(), appended.eventId(),
                    lineHash, indexSnapshot.headHash(), AuditCodec.GENESIS_HASH);
            store.write(transaction, AuditCodec.writeHead(sign(key.get(), unsigned)));
        } catch (UncheckedIOException | StoreRefusal e) {
            LOGGER.warn(e, WARN.AUDIT_HEAD_NOT_WRITTEN, HEAD_UNWRITABLE);
        }
    }

    /**
     * The check a rotation runs before it signs a checkpoint over the current log. A checkpoint signs the record count
     * of the log, and the recount after the rotation accepts the new log, so a checkpoint over a log cut below the
     * line the head anchors would hide the cut. The log may be closed when no head file exists, since nothing is
     * anchored yet, or when the head verifies, anchors the current last line of the index, and the log still holds
     * the line it anchors at its position. The check refuses with {@value #HEAD_UNVERIFIABLE} for a head file that
     * does not parse or does not verify, and with {@value #ANCHORED_LINE_MISSING} for every other head, also for one
     * that anchors the index line before the last entry: a stop between a rotation and the head of the next append.
     * The record of that append is then written to the current log, and its head, counted after the rotation, lets
     * the next due append rotate.
     *
     * @param key the enrolment integrity key
     * @return the check, which reads the head on disk
     */
    AuditRotation.CloseCheck closeCheck(SecretKey key) {
        Objects.requireNonNull(key, "key");
        return (transaction, log, indexSnapshot) -> switch (previous(key)) {
            case Previous.Absent _ -> Optional.empty();
            case Previous.Unverifiable _ -> Optional.of(HEAD_UNVERIFIABLE);
            case Previous.Verifying(var anchored) -> anchored.indexHeadHash().equals(indexSnapshot.headHash())
                    && holdsAnchoredLine(log.read(transaction).lines(), anchored) ? Optional.empty()
                    : Optional.of(ANCHORED_LINE_MISSING);
        };
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
     * The number of records after the append under a verifying previous head: one more than that head counts when it
     * anchors exactly the line the appended record chains to. Otherwise the lines of the log are counted, but only
     * where the count cannot hide a cut: a rotation since the head ({@code rotatedSince}), or a log that still holds
     * the line it anchored at its position, the gap a stop between an append and its head leaves.
     *
     * @return the record count; empty when the log no longer holds the line the previous head anchored
     */
    private OptionalLong recordCount(SecretKey key, LockTransaction transaction, AuditLog log, AuditRecord appended,
            AuditHeadRecord anchored, AuditIndex.Snapshot indexSnapshot) {
        if (anchored.headHash().equals(appended.prevHash())) {
            return OptionalLong.of(anchored.recordCount() + 1);
        }
        if (rotatedSince(key, log, anchored, indexSnapshot)) {
            return OptionalLong.of(log.recordCount(transaction));
        }
        var lines = log.read(transaction).lines();
        return holdsAnchoredLine(lines, anchored) ? OptionalLong.of(lines.size()) : OptionalLong.empty();
    }

    /**
     * Whether the log was rotated since the head, over the line the head anchored: the last index entry chains to the
     * index line the head anchored, and the rotated file it names is closed by a checkpoint that verifies under the
     * key, as the {@link #closeCheck(SecretKey)} let sign only a log that held the line the head anchored, and still
     * holds that line at its position. Index entries and renames are plain file operations; without this, a log cut
     * and closed by a forged checkpoint, entered and renamed by anyone who can write the files, would be re-signed by
     * the next head and pass the incremental verification.
     * <p>
     * This is also how the writer heals a stop between a rotation's rename and the first record of the new log: the
     * head of that record counts the new log and anchors the last index line, the state the verification checks
     * against the same evidence while the record is missing ({@code AuditVerification}).
     */
    private boolean rotatedSince(SecretKey key, AuditLog log, AuditHeadRecord anchored,
            AuditIndex.Snapshot indexSnapshot) {
        var entry = indexSnapshot.last().filter(last -> last.prevHash().equals(anchored.indexHeadHash()));
        if (entry.isEmpty()) {
            return false;
        }
        var rotatedFile = log.logFile().resolveSibling(entry.get().file());
        if (!Files.exists(rotatedFile, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        var lines = new JsonlStoreFile(rotatedFile, lockKey).snapshot().lines();
        if (lines.isEmpty() || lines.size() != entry.get().recordCount()
                || !AuditCodec.sha256Hex(lines.getLast().content()).equals(entry.get().checkpointHash())) {
            return false;
        }
        AuditRecord checkpoint;
        try {
            checkpoint = AuditCodec.readRecord(rotatedFile, lines.getLast().content());
        } catch (StoreRefusal _) {
            return false;
        }
        return AuditRotation.checkpointVerifies(key, checkpoint, lines)
                && holdsAnchoredLine(lines.subList(0, lines.size() - 1), anchored);
    }

    /** @return whether the lines hold the line the head anchored at its position */
    private static boolean holdsAnchoredLine(List<JsonlStoreFile.Line> lines, AuditHeadRecord anchored) {
        var position = anchored.recordCount();
        return position >= 1 && position <= lines.size()
                && AuditCodec.sha256Hex(lines.get(Math.toIntExact(position - 1)).content())
                .equals(anchored.headHash());
    }

    /**
     * Reads the head on disk under the key.
     *
     * @throws UncheckedIOException if the head exists and cannot be read
     */
    private Previous previous(SecretKey key) {
        Optional<AuditHeadRecord> head;
        try {
            head = read(file());
        } catch (StoreRefusal _) {
            return new Previous.Unverifiable();
        }
        if (head.isEmpty()) {
            return new Previous.Absent();
        }
        return verifies(key, head.get()) ? new Previous.Verifying(head.get()) : new Previous.Unverifiable();
    }

    /** The head on disk before an append or a rotation. */
    private sealed interface Previous {

        /** No head file: nothing is anchored yet, so the lines may be counted. */
        record Absent() implements Previous {
        }

        /** @param head a head that parses and verifies under the key */
        record Verifying(AuditHeadRecord head) implements Previous {
        }

        /** A head file that does not parse or does not verify: it anchors nothing, and it is never replaced. */
        record Unverifiable() implements Previous {
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
