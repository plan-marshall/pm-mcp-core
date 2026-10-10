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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.service.LockTransaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
                    () -> assertEquals(List.of(AuditEvent.jsonlTailRepaired(store(WORK_LOG), TORN.length(),
                            StoreSnapshot.sha256(bytes(TORN)))), audit.events()),
                    () -> assertEquals(DigestSidecar.of(Map.of(WORK_LOG, StoreDigest.of(bytes(FIRST, SECOND)))),
                            sidecar(), "the digest is of the repaired store with the block"),
                    () -> assertEquals(List.of(), stagedFiles()));
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
        void openWithoutLock() throws Exception {
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
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(OLD_STATUS));
                write.append(WORK_LOG, bytes(FIRST));

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(STATUS, bytes(NEW_STATUS))),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.append(WORK_LOG, bytes(SECOND))),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(WORK_LOG, bytes(NEW_STATUS))));
            }
        }

        @Test
        @DisplayName("the journal, the sidecar and a path outside the scope are no targets")
        void notAStore() {
            var journal = scope.pathBase().relativize(scope.journalFile()).toString();
            var sidecar = scope.pathBase().relativize(scope.sidecarFile()).toString();
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(journal, bytes(NEW_STATUS))),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace(sidecar, bytes(NEW_STATUS))),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> write.replace("../another-plan/status.json", bytes(NEW_STATUS))));
            }
        }

        @Test
        @DisplayName("a store below the path base of the workspace but outside its owned directories is no target")
        void outsideOwnedDirectories() {
            var workspace = JournalScope.workspace(base, PROJECT);
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(workspace.lockKey());
                var write = ScopeTransaction.open(transaction, workspace, audit, CLOCK);

                assertThrows(IllegalArgumentException.class,
                        () -> write.replace("plans/" + PLAN + "/" + STATUS, bytes(NEW_STATUS)));
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
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = openUnder(transaction);
                write.replace(STATUS, bytes(NEW_STATUS));
                write.commit();

                assertAll(
                        () -> assertThrows(IllegalStateException.class, write::commit),
                        () -> assertThrows(IllegalStateException.class,
                                () -> write.replace(REFERENCES, bytes(NEW_REFERENCES))),
                        () -> assertThrows(IllegalStateException.class,
                                () -> write.append(WORK_LOG, bytes(FIRST))));
            }
        }
    }
}
