/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;

/**
 * The journaled write of the stores of a scope (PM-IMPL-7): a commit writes every target, the digests of the sidecar
 * and leaves neither the journal nor a staged file; it runs only under the lock of the scope, takes each store once,
 * and repairs a torn JSONL tail before it appends.
 * <p>
 * What a commit stopped between its steps leaves, and how the recovery completes it, is the subject of
 * {@link JournalRecoveryTest}.
 */
@DisplayName("Scope transaction")
class ScopeTransactionTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "store-substrate";
    private static final String STATUS = "status.json";
    private static final String REFERENCES = "references.json";
    private static final String WORK_LOG = "logs/work.jsonl";
    private static final String OLD_STATUS = "{\"format_version\":1,\"phase\":\"4-plan\"}";
    private static final String NEW_STATUS = "{\"format_version\":1,\"phase\":\"5-execute\"}";
    private static final String NEW_REFERENCES = "{\"format_version\":1,\"files\":[]}";
    private static final String FIRST = "{\"format_version\":1,\"n\":1}\n";
    private static final String SECOND = "{\"format_version\":1,\"n\":2}\n";
    private static final String TORN = "{\"format_version\":1,\"n\":";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        Files.createDirectories(scope.resolve(WORK_LOG).getParent());
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static byte[] bytes(String... parts) {
        return String.join("", parts).getBytes(UTF_8);
    }

    private Path store(String relativePath) {
        return scope.resolve(relativePath);
    }

    private ScopeTransaction openUnder(LockTransaction transaction) {
        return ScopeTransaction.open(transaction, scope, audit, CLOCK);
    }

    private DigestSidecar sidecar() {
        return DigestSidecar.load(scope.sidecarFile());
    }

    /** @return the files below the path base whose name marks them as staged by a transaction */
    private List<Path> stagedFiles() throws IOException {
        try (Stream<Path> files = Files.walk(scope.pathBase())) {
            return files.filter(Files::isRegularFile).filter(file -> {
                var name = file.getFileName().toString();
                return name.contains(".tmp.") || name.contains(".append.");
            }).toList();
        }
    }

    @Nested
    @DisplayName("commit")
    class Committing {

        @Test
        @DisplayName("two replaces and one append leave the stores, their digests, no journal and no staged file")
        void replacesAndAppend() throws Exception {
            Files.write(store(STATUS), bytes(OLD_STATUS));
            Files.write(store(WORK_LOG), bytes(FIRST));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(NEW_STATUS));
                write.replace(REFERENCES, bytes(NEW_REFERENCES));
                write.append(WORK_LOG, bytes(SECOND));
                write.commit();
            }

            var expectedSidecar = DigestSidecar.of(Map.of(
                    STATUS, StoreDigest.of(bytes(NEW_STATUS)),
                    REFERENCES, StoreDigest.of(bytes(NEW_REFERENCES)),
                    WORK_LOG, StoreDigest.of(bytes(FIRST, SECOND))));
            assertAll(
                    () -> assertArrayEquals(bytes(NEW_STATUS), Files.readAllBytes(store(STATUS))),
                    () -> assertArrayEquals(bytes(NEW_REFERENCES), Files.readAllBytes(store(REFERENCES))),
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG)),
                            "the block appended once, earlier records kept"),
                    () -> assertEquals(expectedSidecar, sidecar()),
                    () -> assertArrayEquals(expectedSidecar.write(), Files.readAllBytes(scope.sidecarFile())),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal is closed"),
                    () -> assertEquals(List.of(), stagedFiles(), "no staged file remains"));
        }

        /** Matched control of the roll-forward: a commit that is not stopped has nothing rolled forward. */
        @Test
        @DisplayName("a commit that is not stopped records no transaction_rolled_forward and leaves nothing to recover")
        void noRollForward() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(NEW_STATUS));
                write.append(WORK_LOG, bytes(FIRST));
                write.commit();

                var outcome = JournalRecovery.recover(transaction, scope, audit);

                assertAll(
                        () -> assertEquals(new JournalRecovery.NothingToRecover(List.of()), outcome),
                        () -> assertEquals(List.of(), audit.events().stream().map(AuditEvent::event)
                                .filter(AuditEvent.TRANSACTION_ROLLED_FORWARD::equals).toList()),
                        () -> assertEquals(List.of(), audit.events()));
            }
        }

        @Test
        @DisplayName("the digests of the stores the transaction does not write stay in the sidecar")
        void otherDigestsKept() {
            var other = StoreDigest.of(bytes(OLD_STATUS));
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                new AtomicStoreFile(scope.sidecarFile(), scope.lockKey()).write(transaction,
                        DigestSidecar.of(Map.of("other.json", other)).write());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(NEW_STATUS));
                write.commit();
            }

            assertEquals(DigestSidecar.of(Map.of("other.json", other, STATUS, StoreDigest.of(bytes(NEW_STATUS)))),
                    sidecar());
        }

        @Test
        @DisplayName("the digests of a transaction are written over a temporary sidecar its stopped write left")
        void digestsOverLeftoverTemporary() throws Exception {
            var transactionId = TransactionId.generate(CLOCK);
            var journal = new TransactionJournal(transactionId, JournalScopeType.PLAN, PLAN, CLOCK.instant(),
                    List.of(new TransactionJournal.Target(STATUS, TransactionJournal.Mode.REPLACE,
                            transactionId.stagedName(STATUS, TransactionJournal.Mode.REPLACE),
                            OptionalLong.empty(), StoreDigest.of(bytes(NEW_STATUS)))));
            var leftover = scope.sidecarFile().resolveSibling(transactionId.stagedName(
                    scope.sidecarFile().getFileName().toString(), TransactionJournal.Mode.REPLACE));
            Files.write(leftover, bytes(OLD_STATUS));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                ScopeTransaction.writeDigests(scope, transaction, journal);
            }

            assertAll(
                    () -> assertEquals(DigestSidecar.of(Map.of(STATUS, StoreDigest.of(bytes(NEW_STATUS)))), sidecar()),
                    () -> assertFalse(Files.exists(leftover), "the temporary sidecar was replaced and moved"));
        }

        @Test
        @DisplayName("an append onto a torn tail repairs it first, with one jsonl_tail_repaired record")
        void tornTailRepaired() throws Exception {
            Files.write(store(WORK_LOG), bytes(FIRST, TORN));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.append(WORK_LOG, bytes(SECOND));
                write.commit();
            }

            assertAll(
                    () -> assertArrayEquals(bytes(FIRST, SECOND), Files.readAllBytes(store(WORK_LOG))),
                    () -> assertEquals(List.of(RecordingAuditSink.tailRepairWithoutId(store(WORK_LOG), bytes(TORN))),
                            RecordingAuditSink.withoutTransactionIds(audit.events())),
                    () -> assertEquals(DigestSidecar.of(Map.of(WORK_LOG, StoreDigest.of(bytes(FIRST, SECOND)))),
                            sidecar(), "the digest is of the repaired store with the block"),
                    () -> assertEquals(List.of(), stagedFiles()));
        }

        @Test
        @DisplayName("an append to a store of several buffer sizes records the digest of the whole resulting store")
        void largeAppend() throws Exception {
            var line = bytes(FIRST);
            var large = new ByteArrayOutputStream();
            while (large.size() < 4 * StoreDigest.Builder.BUFFER_SIZE + 5) {
                large.writeBytes(line);
            }
            Files.write(store(WORK_LOG), large.toByteArray());

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.append(WORK_LOG, bytes(SECOND));
                write.commit();
            }

            large.writeBytes(bytes(SECOND));
            assertAll(
                    () -> assertArrayEquals(large.toByteArray(), Files.readAllBytes(store(WORK_LOG))),
                    () -> assertEquals(Optional.of(StoreDigest.of(large.toByteArray())),
                            sidecar().digest(WORK_LOG)));
        }

        @Test
        @DisplayName("an append to a store that does not exist yet records the digest of the block, size before 0")
        void appendToMissingStore() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.append(WORK_LOG, bytes(FIRST));
                write.commit();
            }

            assertEquals(Optional.of(StoreDigest.of(bytes(FIRST))), sidecar().digest(WORK_LOG));
        }

        @Test
        @DisplayName("the content handed to replace is copied, so a later change of the array is not written")
        void contentCopied() throws Exception {
            var content = bytes(NEW_STATUS);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, content);
                content[0] = 'x';
                write.commit();
            }

            assertArrayEquals(bytes(NEW_STATUS), Files.readAllBytes(store(STATUS)));
        }

        @Test
        @DisplayName("a commit that cannot stage a target fails, and the transaction is not committed again")
        void unwritableTarget() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace("missing/status.json", bytes(NEW_STATUS));

                assertThrows(UncheckedIOException.class, write::commit);
                assertAll(
                        () -> assertThrows(IllegalStateException.class, write::commit),
                        () -> assertFalse(Files.exists(scope.journalFile()), "nothing was journaled"));
            }
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a transaction is refused without the lock of the scope, and nothing is written")
        void openWithoutLock() {
            try (var transaction = locks.openTransaction()) {
                var fault = assertThrows(InternalFaultException.class, () -> openUnder(transaction));

                assertAll(
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                        () -> assertEquals(scope.journalFile().toString(), fault.details().get("store")),
                        () -> assertEquals(List.of(), stagedFiles()));
            }
        }

        @Test
        @DisplayName("a transaction is refused under the key of another plan")
        void openUnderAnotherKey() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(LockKey.plan(base, PROJECT, "another-plan"));

                var fault = assertThrows(InternalFaultException.class, () -> openUnder(transaction));

                assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason());
            }
        }

        @Test
        @DisplayName("a commit after the lock of the scope was released is refused, and nothing is written")
        void commitWithoutLock() throws Exception {
            Files.write(store(STATUS), bytes(OLD_STATUS));
            ScopeTransaction write;
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                write = openUnder(transaction);
                write.replace(STATUS, bytes(NEW_STATUS));
            }

            var fault = assertThrows(InternalFaultException.class, write::commit);

            assertAll(
                    () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, fault.reason()),
                    () -> assertArrayEquals(bytes(OLD_STATUS), Files.readAllBytes(store(STATUS))),
                    () -> assertFalse(Files.exists(scope.journalFile())),
                    () -> assertFalse(Files.exists(scope.sidecarFile())),
                    () -> assertEquals(List.of(), stagedFiles()));
        }

        @Test
        @DisplayName("a store is a target of a transaction once: a second replace or append of its path is refused")
        void samePathTwice() {
            var newStatus = bytes(NEW_STATUS);
            var second = bytes(SECOND);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(OLD_STATUS));
                write.append(WORK_LOG, bytes(FIRST));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(STATUS, newStatus)),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.append(WORK_LOG, second)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(WORK_LOG, newStatus)));
            }
        }

        @Test
        @DisplayName("a store named through another spelling of its path is the same target and is refused")
        void sameStoreOtherSpelling() {
            var content = bytes(NEW_STATUS);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(OLD_STATUS));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("./" + STATUS, content)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("logs/../" + STATUS, content)));
            }
        }

        @Test
        @DisplayName("the journal and the sidecar name a store in the normalized form of its path")
        void normalizedStorePath() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace("./logs/../" + STATUS, bytes(NEW_STATUS));
                write.commit();
            }

            assertEquals(DigestSidecar.of(Map.of(STATUS, StoreDigest.of(bytes(NEW_STATUS)))), sidecar());
        }

        @Test
        @DisplayName("the journal, the sidecar and a path outside the scope are no targets")
        void notAStore() {
            var journal = scope.pathBase().relativize(scope.journalFile()).toString();
            var sidecar = scope.pathBase().relativize(scope.sidecarFile()).toString();
            var content = bytes(NEW_STATUS);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(journal, content)),
                        () -> assertThrows(IllegalArgumentException.class, () -> write.replace(sidecar, content)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("../another-plan/status.json", content)));
            }
        }

        @Test
        @DisplayName("a store that is a symbolic link to a file outside the scope is no target; nothing is written")
        void linkedStore() throws Exception {
            var outside = Files.writeString(base.resolve("outside.json"), OLD_STATUS);
            SymbolicLinks.createOrSkip(store(STATUS), outside);
            var content = bytes(NEW_STATUS);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertThrows(IllegalArgumentException.class, () -> write.replace(STATUS, content));
            }
            assertAll(
                    () -> assertEquals(OLD_STATUS, Files.readString(outside)),
                    () -> assertEquals(List.of(), stagedFiles()));
        }

        @Test
        @DisplayName("a store below a symbolic link to a directory outside the scope is no target; nothing is written")
        void linkedParent() throws Exception {
            var outside = Files.createDirectories(base.resolve("outside"));
            var linked = scope.pathBase().resolve("linked");
            SymbolicLinks.createOrSkip(linked, outside);
            var block = bytes(FIRST);
            var content = bytes(NEW_STATUS);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.append("linked/work.jsonl", block)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("linked/status.json", content)));
            }
            try (Stream<Path> files = Files.list(outside)) {
                assertEquals(List.of(), files.toList(), "nothing was written outside the scope");
            }
        }

        @Test
        @DisplayName("a store below the path base of the workspace but outside its owned directories is no target")
        void outsideOwnedDirectories() {
            var workspace = JournalScope.workspace(base, PROJECT);
            var planStore = "plans/" + PLAN + "/" + STATUS;
            var content = bytes(NEW_STATUS);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, CLOCK);

                assertThrows(IllegalArgumentException.class, () -> write.replace(planStore, content));
            }
        }

        @Test
        @DisplayName("an empty block and a block without a trailing newline are no append")
        void invalidBlock() {
            var empty = new byte[0];
            var unterminated = bytes(FIRST.strip());
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> write.append(WORK_LOG, empty)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.append(WORK_LOG, unterminated)));
            }
        }

        @Test
        @DisplayName("a transaction without a target is not committed")
        void noTarget() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertThrows(IllegalStateException.class, write::commit);
            }
        }

        @Test
        @DisplayName("a committed transaction takes no further target and is not committed again")
        void committedOnce() {
            var references = bytes(NEW_REFERENCES);
            var first = bytes(FIRST);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(NEW_STATUS));
                write.commit();

                assertAll(
                        () -> assertThrows(IllegalStateException.class, write::commit),
                        () -> assertThrows(IllegalStateException.class, () -> write.replace(REFERENCES, references)),
                        () -> assertThrows(IllegalStateException.class, () -> write.append(WORK_LOG, first)));
            }
        }
    }
}
