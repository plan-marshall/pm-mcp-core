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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import javax.crypto.SecretKey;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditHeadRecord;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditLogSink;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * An audit trail on a test base: the hash-chained log written through the production sink, anchored by an
 * {@link AuditHead} under an enrolment integrity key held in an in-memory keyring, with the helpers that edit the
 * files into the tamper cases. The key does not exist until a test creates it.
 */
final class AuditTrailFixture {

    /** The OS principal of the records. */
    static final String USER = System.getProperty("user.name");

    /** The instant the clock starts at. */
    static final Instant START = Instant.parse("2026-10-01T08:00:00Z");

    /** The name of the event the fixture appends. */
    static final String EVENT = "test_event";

    final Path base;
    final Keyring keyring = new Keyring();
    final EnrolmentKey integrityKey;
    final MutableClock clock = new MutableClock(START);
    final FileLockManager locks = new FileLockManager(_ -> {
    });
    final AuditHead head;
    final AuditLog log;
    final AuditLogSink sink;

    private long appended;

    /**
     * @param base   the test base
     * @param limits the limits of the rotation
     */
    AuditTrailFixture(Path base, AuditRotation.Limits limits) {
        this.base = base;
        try {
            Files.createDirectories(base.resolve("state"));
            Files.createDirectories(LockKey.auditLog(base).lockFile().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        integrityKey = new EnrolmentKey(keyring, base.resolve("state"), USER, new SecureRandom());
        head = new AuditHead(base, integrityKey, limits, clock, USER);
        log = new AuditLog(base, head, clock, USER);
        sink = new AuditLogSink(locks, log);
    }

    /** @param base the test base, with the default limits of the rotation */
    AuditTrailFixture(Path base) {
        this(base, AuditRotation.Limits.DEFAULTS);
    }

    /** @return the integrity key, created now if it does not exist yet */
    SecretKey createKey() {
        return integrityKey.readOrCreate();
    }

    /** Appends records through the production sink, one per call of the sink. */
    void append(int records) {
        for (var i = 0; i < records; i++) {
            sink.append(new AuditEvent(EVENT, AuditEvent.CATEGORY_STORE, AuditEvent.OUTCOME_SUCCEEDED,
                    Map.of("n", appended++)));
        }
    }

    /** Runs the work in a transaction that holds the audit lock. */
    void underAuditLock(Consumer<LockTransaction> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(LockKey.auditLog(base));
            work.accept(transaction);
        }
    }

    /** @return a verification of the trail */
    AuditVerification verification() {
        return new AuditVerification(base, integrityKey);
    }

    Path logFile() {
        return LockKey.auditLog(base).lockFile();
    }

    Path headFile() {
        return AuditHead.headFile(base);
    }

    Path indexFile() {
        return logFile().resolveSibling(AuditIndex.FILE_NAME);
    }

    /** @return the rotated log of the first event id */
    Path rotatedFile(String firstEventId) {
        return logFile().resolveSibling("audit-" + firstEventId + ".jsonl");
    }

    /** @return the head as it is on disk */
    Optional<AuditHeadRecord> readHead() {
        return AuditHead.read(headFile());
    }

    /** @return the complete lines of a file, without their newlines; none if it does not exist */
    static List<String> lines(Path file) {
        if (!Files.exists(file)) {
            return List.of();
        }
        try {
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Replaces a file by the lines, each ending with a newline. */
    static void writeLines(Path file, List<String> lines) {
        var content = new StringBuilder();
        lines.forEach(line -> content.append(line).append('\n'));
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Edits one line of a file, counted from 1. */
    static void editLine(Path file, int number, UnaryOperator<String> edit) {
        var lines = new ArrayList<>(lines(file));
        lines.set(number - 1, edit.apply(lines.get(number - 1)));
        writeLines(file, lines);
    }

    /** Removes one line of a file, counted from 1. */
    static void removeLine(Path file, int number) {
        var lines = new ArrayList<>(lines(file));
        lines.remove(number - 1);
        writeLines(file, lines);
    }

    /** @return the record of a line of a file, counted from 1 */
    static AuditRecord recordAt(Path file, int number) {
        return AuditCodec.readRecord(file, lines(file).get(number - 1).getBytes(StandardCharsets.UTF_8));
    }

    /** @return the SHA-256 of a line of a file, counted from 1 */
    static String hashOf(Path file, int number) {
        return AuditCodec.sha256Hex(lines(file).get(number - 1).getBytes(StandardCharsets.UTF_8));
    }

    /** @return every file below {@code logs/} with its content, to show that nothing changed */
    Map<String, String> files() {
        var files = new TreeMap<String, String>();
        try (var entries = Files.list(logFile().getParent())) {
            for (var file : entries.toList()) {
                if (Files.isRegularFile(file)) {
                    files.put(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return files;
    }

    /** A keyring in memory, with an optional failure of every read. */
    static final class Keyring implements SecretStore {

        final Map<String, String> entries = new HashMap<>();

        SecretStoreException failure;

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            entries.put(account.account(), secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(entries.get(account.account()));
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return entries.remove(account.account()) != null;
        }

        /** The fixture stores global entries only: the integrity key. */
        @Override
        public List<CredentialAccount> entries() {
            if (failure != null) {
                throw failure;
            }
            return entries.keySet().stream().sorted().map(CredentialAccount::global).toList();
        }
    }

    /** A clock that stands still until it is advanced. */
    static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
