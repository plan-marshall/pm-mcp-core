/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.question;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Open;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Resolved;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Unreadable;
import de.planmarshall.core.spi.QuestionRecordReader.ReadRequest;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.QuestionChannel;
import de.planmarshall.core.store.QuestionEvent;
import de.planmarshall.core.store.QuestionLog;
import de.planmarshall.core.store.QuestionRecord;
import de.planmarshall.core.store.QuestionRecord.Answer;
import de.planmarshall.core.store.QuestionRecord.AnswerKind;
import de.planmarshall.core.store.QuestionRecord.Blocks;
import de.planmarshall.core.store.QuestionRecord.Decision;
import de.planmarshall.core.store.QuestionRecord.Origin;
import de.planmarshall.core.store.QuestionRecord.QuestionClass;
import de.planmarshall.core.store.QuestionRecord.Reversibility;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkspaceRoots;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.workspace.WorkspaceConfinement;

/**
 * The read of the adapter as a read (PM-SEC-2, ADR {@code core-store/032}): it takes no lock an append waits on, writes
 * nothing, and passes the log path through the workspace confinement, where a refused path is a value.
 */
@DisplayName("Question log record reader: the read is a read")
class QuestionLogRecordReaderReadTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";
    private static final String QUESTION = "Q-001";
    private static final Instant AT = Instant.parse("2026-10-19T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AT, ZoneOffset.UTC);

    @TempDir
    Path temp;

    private Path base;
    private JournalScope plan;
    private FileLockManager locks;
    private QuestionLogRecordReader reader;

    @BeforeEach
    void createRuntime() throws IOException {
        base = temp.toRealPath();
        plan = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(plan.journalFile().getParent());
        locks = new FileLockManager(event -> {
            // a lock acquisition of these tests records nothing
        });
        reader = new QuestionLogRecordReader(base, PROJECT,
                new WorkspaceConfinement(new WorkspaceRoots(base, Set.of())));
    }

    private static ReadRequest request(String scopeId, String questionId) {
        return new ReadRequest(Optional.of(scopeId), questionId);
    }

    private static QuestionRecord asked(String questionId) {
        return QuestionRecord.builder()
                .event(QuestionEvent.ASKED)
                .questionId(questionId)
                .questionClass(QuestionClass.ASK)
                .origin(Origin.SERVER)
                .sourceEntity("question-log-record-reader-read-test")
                .questionSet(questionId)
                .revision(1)
                .decision(new Decision("A decision", "A header", "A description", List.of(), Blocks.NOTHING,
                        Reversibility.REVERSIBLE))
                .envelope("{}")
                .at(AT)
                .build();
    }

    private static QuestionRecord answered(String questionId) {
        return QuestionRecord.builder()
                .event(QuestionEvent.ANSWERED)
                .questionId(questionId)
                .revision(1)
                .decisionId("D-0001")
                .answer(new Answer(AnswerKind.CHOSEN, List.of("option_a"), null, null, null, null, false))
                .channel(QuestionChannel.CLI)
                .at(AT)
                .build();
    }

    private void append(QuestionRecord... records) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(plan.lockKey());
            var write = ScopeTransaction.open(transaction, plan, event -> {
                // these fixtures record nothing
            }, CLOCK);
            var refusal = new QuestionLog(plan).append(write, List.of(records));
            assertTrue(refusal.isEmpty(), "the fixture append should be accepted: " + refusal);
            write.commit();
        }
    }

    @Nested
    @DisplayName("the file tree of the scope")
    class FileTree {

        @Test
        @DisplayName("is identical before and after a read: same names, sizes and modification times")
        void leavesTheTreeUnchanged() throws IOException {
            append(asked(QUESTION), answered(QUESTION));
            var before = tree(plan.pathBase());

            var read = reader.read(request(PLAN, QUESTION));

            assertEquals(new Resolved("answered"), read);
            assertEquals(before, tree(plan.pathBase()),
                    "a read writes nothing and creates no file: the tree, its sizes and its times are the same");
        }

        private static Map<String, String> tree(Path root) throws IOException {
            try (var walk = Files.walk(root)) {
                return walk.collect(Collectors.toMap(
                        path -> root.relativize(path).toString(),
                        FileTree::describe,
                        (left, right) -> left,
                        TreeMap::new));
            }
        }

        private static String describe(Path path) {
            try {
                if (Files.isDirectory(path)) {
                    return "directory:" + Files.getLastModifiedTime(path).toMillis();
                }
                return "file:" + Files.size(path) + ":" + Files.getLastModifiedTime(path).toMillis();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    @DisplayName("an append that holds the lock")
    class AppendLock {

        @Test
        @DisplayName("does not delay the read, and the read does not block the append it follows")
        void readsWhileAnotherThreadHoldsTheAppendLock() throws Exception {
            append(asked(QUESTION));
            var locked = new CountDownLatch(1);
            var release = new CountDownLatch(1);
            var failure = new AtomicReference<Throwable>();
            var holder = Thread.ofPlatform().start(() -> {
                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(plan.lockKey());
                    locked.countDown();
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (RuntimeException e) {
                    failure.set(e);
                }
            });

            try {
                assertTrue(locked.await(10, SECONDS), "the holder should hold the append lock");
                var read = assertTimeout(Duration.ofSeconds(10),
                        () -> reader.read(request(PLAN, QUESTION)),
                        "the read takes no lock and returns although an append holds it");
                assertEquals(new Open(), read);
            } finally {
                release.countDown();
                holder.join(10_000);
            }
            assertTrue(failure.get() == null, "the lock holder should not fail: " + failure.get());

            append(answered(QUESTION));

            assertEquals(new Resolved("answered"), reader.read(request(PLAN, QUESTION)),
                    "the append the read did not block is in the log");
        }
    }

    @Nested
    @DisplayName("the confinement of the log path")
    class Confinement {

        @Test
        @DisplayName("answers Unreadable, as a value, for a log outside the confinement")
        void refusesAPathOutsideTheConfinement() throws IOException {
            append(asked(QUESTION));
            var elsewhere = Files.createDirectories(base.resolve("elsewhere"));
            var confined = new QuestionLogRecordReader(base, PROJECT,
                    new WorkspaceConfinement(new WorkspaceRoots(elsewhere, Set.of())));

            var read = assertDoesNotThrow(() -> confined.read(request(PLAN, QUESTION)),
                    "a refused path is a value, never an exception");

            assertNotEquals(new Open(), read, "a refused log is never reported open");
            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertEquals("the question log is refused with path_traversal_rejected: outside_workspace",
                    unreadable.reason());
        }

        @Test
        @DisplayName("answers Unreadable for every contained path when the confinement accepts no path")
        void refusesEveryPathOfAnEmptyWorkspace() {
            // the enrolled root itself is missing, so the check cannot canonicalize it: a refusal, not an exception
            var missing = base.resolve("missing-root");
            var confined = new QuestionLogRecordReader(base, PROJECT,
                    new WorkspaceConfinement(new WorkspaceRoots(missing, Set.of())));

            var read = assertDoesNotThrow(() -> confined.read(request(PLAN, QUESTION)));

            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertEquals("the question log is refused with path_traversal_rejected: canonicalization_failed",
                    unreadable.reason());
        }

        @Test
        @DisabledOnOs(OS.WINDOWS)
        @DisplayName("answers Unreadable for a scope directory reached through a symbolic link")
        void refusesASymbolicLink() throws IOException {
            append(asked(QUESTION));
            var linkedScope = plan.pathBase().getParent().resolve("linked-plan");
            Files.createSymbolicLink(linkedScope, plan.pathBase());

            var read = assertDoesNotThrow(() -> reader.read(request("linked-plan", QUESTION)));

            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertEquals("the question log is not reached by its canonical path", unreadable.reason());
        }
    }
}
