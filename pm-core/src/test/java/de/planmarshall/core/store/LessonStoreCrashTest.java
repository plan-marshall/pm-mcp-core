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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.Serial;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The crash windows and the concurrency of the lesson store (Store Schemas § Lesson Record; R14 (5), (6), (8), (9)): a
 * write stopped after any step of its transaction leaves, after {@link JournalRecovery}, the old or the new lesson and a
 * sidecar entry that matches — never a torn one; a repeated release or end is a no-op; and the staged namespace
 * {@code <lesson_id>.md.tmp.<transaction id>} is disjoint from the legal names.
 */
@EnableGeneratorController
@DisplayName("Lesson store crash windows")
class LessonStoreCrashTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Instant NOW = Instant.parse("2026-11-02T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private String plan;
    private String otherPlan;
    private String planA;
    private String planB;

    @TempDir
    Path base;

    private JournalScope lessons;
    private LessonStore store;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    /** What the step hook throws to stop a commit at a step of the transaction. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite() {
            super("stopped at a step of the transaction");
        }
    }

    @BeforeEach
    void createStore() throws IOException {
        lessons = JournalScope.lessons(base, PROJECT);
        Files.createDirectories(lessons.pathBase());
        store = new LessonStore(lessons);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
        plan = "plan-" + Generators.integers(1000, 9999).next();
        otherPlan = "plan-other-" + Generators.integers(1000, 9999).next();
        planA = "plan-a-" + Generators.integers(1000, 9999).next();
        planB = "plan-b-" + Generators.integers(1000, 9999).next();
    }

    private <T> T inTransaction(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
            var result = work.apply(write);
            write.commit();
            return result;
        }
    }

    private void run(Consumer<ScopeTransaction> work) {
        inTransaction(write -> {
            work.accept(write);
            return null;
        });
    }

    private <T> T withoutCommit(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            return work.apply(ScopeTransaction.open(transaction, lessons, audit, CLOCK));
        }
    }

    private Path expectedPath(LessonId lessonId) {
        return LockKey.projectStore(base, PROJECT).resolve("lessons").resolve(lessonId.fileName());
    }

    private void recover() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            JournalRecovery.recover(transaction, lessons, audit);
        }
    }

    /** Runs the operation in a transaction whose commit stops after the given step. */
    private void stopAt(Step stop, Consumer<ScopeTransaction> operation) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            ScopeTransaction.StepHook hook = (step, target) -> {
                if (step == stop) {
                    throw new StopWrite();
                }
            };
            var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK, hook);
            operation.accept(write);
            assertThrows(StopWrite.class, write::commit, "the commit stops at the chosen step");
        }
    }

    /**
     * @param lessonId the lesson
     * @param oldBytes the bytes before the operation; {@code null} when the lesson did not exist
     * @param newBytes the bytes the operation writes
     * @return whether the lesson file holds the new bytes
     */
    private boolean assertConsistent(LessonId lessonId, byte[] oldBytes, byte[] newBytes) {
        var file = expectedPath(lessonId);
        var recorded = DigestSidecar.load(lessons.sidecarFile()).digest(lessonId.fileName());
        if (!Files.exists(file)) {
            assertEquals(Optional.empty(), recorded, "an absent lesson has no sidecar entry");
            assertNull(oldBytes, "the operation created the lesson, so it cannot be absent");
            return false;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new AssertionError("The lesson file cannot be read: " + file, e);
        }
        assertAll("the lesson file and its sidecar",
                () -> assertTrue(Arrays.equals(bytes, oldBytes) || Arrays.equals(bytes, newBytes),
                        "the lesson is the old or the new one, never a torn one"),
                () -> assertEquals(Optional.of(StoreDigest.of(bytes)), recorded,
                        "the sidecar entry matches the lesson file"));
        return Arrays.equals(bytes, newBytes);
    }

    private byte[] newLessonDocument(LessonId lessonId, LessonStore.NewLesson newLesson) {
        var body = newLesson.body();
        var frontMatter = LessonFrontMatter.builder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION)
                .lessonId(lessonId)
                .title(newLesson.title())
                .component(newLesson.component())
                .category(newLesson.category())
                .bundle(newLesson.bundle())
                .rule(newLesson.rule())
                .remedy(newLesson.remedy())
                .status(newLesson.audience() == LessonFrontMatter.Audience.UPSTREAM
                        ? Status.UPSTREAM_PENDING : Status.ACTIVE)
                .provenance(newLesson.provenance())
                .derivedFromUntrusted(newLesson.derivedFromUntrusted())
                .audience(newLesson.audience())
                .createdAt(NOW)
                .toolVersion(newLesson.toolVersion())
                .promotions(List.of())
                .revision(1)
                .sources(newLesson.sources())
                .revisions(List.of(new LessonFrontMatter.Revision(1, NOW, newLesson.sources(),
                        StoreDigest.of(body.getBytes(StandardCharsets.UTF_8)).sha256())))
                .mergedFrom(List.of())
                .claim(null)
                .retired(null)
                .build();
        return LessonCodec.encode(new Lesson(frontMatter, body));
    }

    private static List<LessonFrontMatter.Revision> appended(LessonFrontMatter frontMatter, int revision,
            List<LessonFrontMatter.Source> sources, String body) {
        var revisions = new ArrayList<>(frontMatter.revisions());
        revisions.add(new LessonFrontMatter.Revision(revision, NOW, sources,
                StoreDigest.of(body.getBytes(StandardCharsets.UTF_8)).sha256()));
        return List.copyOf(revisions);
    }

    private static byte[] encodeAfter(LessonFrontMatter frontMatter, String body) {
        return LessonCodec.encode(new Lesson(frontMatter, body));
    }

    private static byte[] encodeAfter(Lesson before, LessonFrontMatter frontMatter) {
        return encodeAfter(frontMatter, before.body());
    }

    private byte[] encodeAfterEnd(Lesson before) {
        var frontMatter = before.frontMatter().toBuilder()
                .status(Status.RETIRED)
                .claim(null)
                .retired(LessonFrontMatter.Retired.builder()
                        .at(NOW)
                        .by(plan)
                        .verdict(Verdict.APPLIED)
                        .reason(LessonStore.END_OF_CLAIM_REASON)
                        .lessonState(LessonFrontMatter.Retired.LessonState.FOUND)
                        .build())
                .build();
        return encodeAfter(before, frontMatter);
    }

    private Optional<Lesson> read(LessonId lessonId) {
        return store.get(lessonId) instanceof Found found ? Optional.of(found.lesson()) : Optional.empty();
    }

    private boolean isListed(LessonId lessonId) {
        return store.list().stream().anyMatch(resolution -> resolution.lessonId().equals(lessonId));
    }

    /** Runs the operation and proves it added no target: its commit is refused as a transaction without one. */
    private void assertAddsNoTarget(Function<ScopeTransaction, LessonResolution.Outcome> operation) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
            operation.apply(write);
            var refusal = assertThrows(IllegalStateException.class, write::commit,
                    "a transaction without a target is refused");
            assertTrue(refusal.getMessage().contains("no target"), refusal::getMessage);
        }
    }

    @Nested
    @DisplayName("a write stopped after each step")
    class WriteStopped {

        @Test
        @DisplayName("of a create leaves the old or the new lesson and a matching sidecar")
        void createAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-11-02-09-1%03d".formatted(stop.ordinal() + 1));
                var newLesson = LessonFixtures.newLesson().body(LessonFixtures.body()).build();
                var newBytes = newLessonDocument(lessonId, newLesson);

                stopAt(stop, write -> store.create(write, lessonId, newLesson, CLOCK));
                recover();

                assertAll("a stopped create",
                        () -> assertConsistent(lessonId, null, newBytes),
                        () -> assertEquals(stop != Step.STAGE, isListed(lessonId),
                                "the lesson is listed once the journal was written"));
            }
        }

        @Test
        @DisplayName("of a revise leaves the old or the new body and a matching sidecar")
        void reviseAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-11-02-09-2%03d".formatted(stop.ordinal() + 1));
                var before = inTransaction(write -> store.create(write, lessonId,
                        LessonFixtures.newLesson().build(), CLOCK));
                var next = LessonFixtures.body() + " rewritten\n";
                var sources = List.of(LessonFixtures.source());
                var newBytes = encodeAfter(before.frontMatter().toBuilder()
                        .revision(2)
                        .sources(sources)
                        .revisions(appended(before.frontMatter(), 2, sources, next))
                        .build(), next);

                stopAt(stop, write -> store.revise(write, lessonId, next, sources, false, NOW));
                recover();

                assertAll("a stopped revise",
                        () -> assertEquals(stop != Step.STAGE,
                                assertConsistent(lessonId, LessonCodec.encode(before), newBytes),
                                "a stop after the journal rolls forward to the new revision"),
                        () -> assertTrue(isListed(lessonId), "the lesson stays listed"));
            }
        }

        @Test
        @DisplayName("of a claim leaves the old or the new claim and a matching sidecar")
        void claimAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-11-02-09-3%03d".formatted(stop.ordinal() + 1));
                var before = inTransaction(write -> store.create(write, lessonId,
                        LessonFixtures.newLesson().build(), CLOCK));
                var newBytes = encodeAfter(before, before.frontMatter().toBuilder()
                        .claim(new LessonFrontMatter.Claim(plan, NOW)).build());

                stopAt(stop, write -> store.claim(write, lessonId, plan, NOW));
                recover();

                assertAll("a stopped claim",
                        () -> assertEquals(stop != Step.STAGE,
                                assertConsistent(lessonId, LessonCodec.encode(before), newBytes),
                                "a stop after the journal rolls forward to the new claim"),
                        () -> assertTrue(isListed(lessonId), "the lesson stays listed"));
            }
        }

        @Test
        @DisplayName("of an end applied leaves the old or the retired lesson and a matching sidecar")
        void endAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-11-02-09-4%03d".formatted(stop.ordinal() + 1));
                inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
                run(write -> store.claim(write, lessonId, plan, NOW));
                var before = read(lessonId).orElseThrow();
                var newBytes = encodeAfterEnd(before);

                stopAt(stop, write -> store.endClaimApplied(write, lessonId, plan, NOW));
                recover();

                assertAll("a stopped end",
                        () -> assertEquals(stop != Step.STAGE,
                                assertConsistent(lessonId, LessonCodec.encode(before), newBytes),
                                "a stop after the journal rolls forward to the retirement"),
                        () -> assertTrue(isListed(lessonId), "the lesson stays listed"),
                        () -> assertFalse(Files.exists(lessons.journalFile()), "the journal is closed"));
            }
        }
    }

    @Nested
    @DisplayName("a repeated call")
    class Repeated {

        @Test
        @DisplayName("of a release is a no-op that reports that no claim is held")
        void release() {
            var lessonId = new LessonId("2026-11-02-09-501");
            inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            run(write -> store.claim(write, lessonId, plan, NOW));
            run(write -> store.release(write, lessonId, plan));

            assertAddsNoTarget(write -> store.release(write, lessonId, plan));

            assertAll("a repeated release",
                    () -> assertEquals(LessonResolution.Refusal.UNCLAIMED,
                            assertInstanceOf(LessonResolution.Unavailable.class,
                                    withoutCommit(write -> store.release(write, lessonId, plan)),
                                    "the outcome reports an unavailable state").refusal(),
                            "the second release reports that no claim is held"),
                    () -> assertTrue(Files.exists(expectedPath(lessonId)), "the lesson stays in place"));
        }

        @Test
        @DisplayName("of an end is a no-op that reports the retirement")
        void end() {
            var lessonId = new LessonId("2026-11-02-09-502");
            inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            run(write -> store.claim(write, lessonId, plan, NOW));
            run(write -> store.endClaimApplied(write, lessonId, plan, NOW));

            var outcome = withoutCommit(write -> store.endClaimApplied(write, lessonId, plan, NOW));
            assertAddsNoTarget(write -> store.endClaimApplied(write, lessonId, plan, NOW));

            assertAll("a repeated end",
                    () -> assertInstanceOf(LessonResolution.Retired.class, outcome,
                            "a repeated end reports the retirement"),
                    () -> assertTrue(Files.exists(expectedPath(lessonId)), "the lesson stays in place"));
        }

        @Test
        @DisplayName("of a release by another plan is refused and names the plan that holds the claim")
        void releaseByAnotherPlan() {
            var lessonId = new LessonId("2026-11-02-09-503");
            inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            run(write -> store.claim(write, lessonId, plan, NOW));

            var outcome = withoutCommit(write -> store.release(write, lessonId, otherPlan));

            assertEquals(plan, assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                    "the outcome reports an unavailable state").claimingPlan(),
                    "the release is authorized by the durable claim alone");
        }
    }

    @Nested
    @DisplayName("two concurrent writers")
    class ConcurrentWriters {

        @Test
        @DisplayName("two claims of one lesson end with exactly one claimant")
        void twoClaims() throws Exception {
            var lessonId = created("2026-11-02-09-701");

            var outcomes = concurrently(
                    write -> store.claim(write, lessonId, planA, NOW),
                    write -> store.claim(write, lessonId, planB, NOW));

            assertAll("two concurrent claims",
                    () -> assertEquals(1, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Claimed).count(),
                            "exactly one claim succeeded"),
                    () -> assertEquals(1, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Unavailable).count(),
                            "exactly one claim was refused"),
                    () -> assertNotNull(read(lessonId).orElseThrow().frontMatter().claim(),
                            "the lesson carries exactly one claim"));
        }

        @Test
        @DisplayName("a claim racing a retirement ends in one of the two orders, never both")
        void claimRacingRetirement() throws Exception {
            var lessonId = created("2026-11-02-09-702");

            var outcomes = concurrently(
                    write -> store.claim(write, lessonId, planA, NOW),
                    write -> store.retire(write, lessonId, LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            var claim = outcomes.getFirst();
            var retirement = outcomes.getLast();
            var claimed = claim instanceof LessonResolution.Claimed;
            var retired = retirement instanceof LessonResolution.Retired;
            assertAll("a claim racing a retirement",
                    () -> assertNotEquals(claimed, retired, "one transition won and one was refused"),
                    () -> assertTrue(claimed
                                    ? retirement instanceof LessonResolution.Unavailable claimRefused
                                    && claimRefused.refusal() == LessonResolution.Refusal.CLAIMED
                                    : claim instanceof LessonResolution.Unavailable retireRefused
                                    && retireRefused.refusal() == LessonResolution.Refusal.RETIRED,
                            "the refused transition names the state the winner left"));
        }

        @Test
        @DisplayName("two releases of one claim end with exactly one release")
        void twoReleases() throws Exception {
            var lessonId = created("2026-11-02-09-703");
            run(write -> store.claim(write, lessonId, planA, NOW));

            var outcomes = concurrently(
                    write -> store.release(write, lessonId, planA),
                    write -> store.release(write, lessonId, planA));

            assertAll("two concurrent releases",
                    () -> assertEquals(1, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Released).count(),
                            "exactly one release succeeded"),
                    () -> assertEquals(1, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Unavailable).count(),
                            "exactly one release found no claim"),
                    () -> assertNull(read(lessonId).orElseThrow().frontMatter().claim(),
                            "the claim is cleared once"));
        }

        @Test
        @DisplayName("two ends of one claim retire the lesson once")
        void twoEnds() throws Exception {
            var lessonId = created("2026-11-02-09-704");
            run(write -> store.claim(write, lessonId, planA, NOW));

            var outcomes = concurrently(
                    write -> store.endClaimApplied(write, lessonId, planA, NOW),
                    write -> store.endClaimApplied(write, lessonId, planA, NOW));

            var retired = read(lessonId).orElseThrow().frontMatter().retired();
            assertAll("two concurrent ends",
                    () -> assertEquals(2, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Retired).count(),
                            "both ends report the retirement"),
                    () -> assertEquals(Verdict.APPLIED, retired.verdict(), "the lesson is retired applied"),
                    () -> assertEquals(planA, retired.by(), "the plan stays the author of the retirement"));
        }

        @Test
        @DisplayName("two creates of one id end with exactly one lesson")
        void twoCreatesOfOneId() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-705");
            var serializing = new SerializingLockManager();
            var start = new CountDownLatch(1);
            Callable<Boolean> writer = () -> {
                start.await();
                try (var transaction = serializing.openTransaction()) {
                    transaction.acquire(lessons.lockKey());
                    var write = ScopeTransaction.open(transaction, lessons, new RecordingAuditSink(), CLOCK);
                    store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK);
                    write.commit();
                    return true;
                } catch (IllegalArgumentException | IllegalStateException _) {
                    return false;
                }
            };
            try (var executor = Executors.newFixedThreadPool(2)) {
                var futures = List.of(executor.submit(writer), executor.submit(writer));
                start.countDown();
                var written = List.of(futures.get(0).get(), futures.get(1).get());
                assertAll("two concurrent creates",
                        () -> assertEquals(1, written.stream().filter(Boolean::booleanValue).count(),
                                "exactly one create wrote the lesson"),
                        () -> assertEquals(1, store.list().size(), "exactly one lesson exists"));
            }
        }

        @Test
        @DisplayName("two revisions of one lesson leave one of the two bodies")
        void twoRevisions() throws Exception {
            var lessonId = created("2026-11-02-09-706");
            var sources = List.of(LessonFixtures.source());
            var first = LessonFixtures.body() + " first revision\n";
            var second = LessonFixtures.body() + " second revision\n";

            var outcomes = concurrently(
                    write -> store.revise(write, lessonId, first, sources, false, NOW),
                    write -> store.revise(write, lessonId, second, sources, false, NOW));

            var body = read(lessonId).orElseThrow().body();
            assertAll("two concurrent revisions",
                    () -> assertEquals(2, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Revised).count(),
                            "both revisions were written, one after the other"),
                    () -> assertTrue(first.equals(body) || second.equals(body),
                            "the lesson holds one of the two bodies, never a mixture"),
                    () -> assertEquals(3, read(lessonId).orElseThrow().frontMatter().revisions().size(),
                            "the history keeps every revision written"));
        }

        @Test
        @DisplayName("two retirements of one lesson end with exactly one retirement")
        void twoRetirements() throws Exception {
            var lessonId = created("2026-11-02-09-707");

            var outcomes = concurrently(
                    write -> store.retire(write, lessonId, LessonFixtures.retirement(Verdict.OBSOLETE).build()),
                    write -> store.retire(write, lessonId, LessonFixtures.retirement(Verdict.REDUNDANT).build()));

            assertAll("two concurrent retirements",
                    () -> assertEquals(1, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Retired).count(),
                            "exactly one retirement was written"),
                    () -> assertEquals(1, outcomes.stream()
                                    .filter(outcome -> outcome instanceof LessonResolution.Unavailable).count(),
                            "exactly one retirement found the lesson retired"),
                    () -> assertNotNull(read(lessonId).orElseThrow().frontMatter().retired(),
                            "the lesson carries one retirement"));
        }

        private LessonId created(String value) {
            var lessonId = new LessonId(value);
            inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            return lessonId;
        }

        /** Runs one operation per writer under the serializing lock manager and returns both outcomes. */
        private List<LessonResolution.Outcome> concurrently(
                Function<ScopeTransaction, LessonResolution.Outcome> first,
                Function<ScopeTransaction, LessonResolution.Outcome> second) throws Exception {
            var serializing = new SerializingLockManager();
            var start = new CountDownLatch(1);
            var outcomes = new ArrayList<LessonResolution.Outcome>();
            try (var executor = Executors.newFixedThreadPool(2)) {
                var futures = List.of(executor.submit(() -> write(serializing, start, first)),
                        executor.submit(() -> write(serializing, start, second)));
                start.countDown();
                for (var future : futures) {
                    outcomes.add(future.get());
                }
            }
            return outcomes;
        }

        private LessonResolution.Outcome write(SerializingLockManager manager, CountDownLatch start,
                Function<ScopeTransaction, LessonResolution.Outcome> operation) throws InterruptedException {
            start.await();
            try (var transaction = manager.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var write = ScopeTransaction.open(transaction, lessons, new RecordingAuditSink(), CLOCK);
                var outcome = operation.apply(write);
                if (!(outcome instanceof LessonResolution.Unavailable)) {
                    commitOrNoTarget(write);
                }
                return outcome;
            }
        }

        /**
         * Commits a successful outcome, and tolerates the one successful outcome that changes nothing: a repeated end
         * reports the retirement it finds, so its transaction has no target and its commit is refused.
         */
        private void commitOrNoTarget(ScopeTransaction write) {
            try {
                write.commit();
            } catch (IllegalStateException refusal) {
                if (!refusal.getMessage().contains("no target")) {
                    throw refusal;
                }
            }
        }
    }

    @Nested
    @DisplayName("the staged namespace")
    class StagedNamespace {

        @Test
        @DisplayName("is disjoint from the legal names, so a staged file is never a lesson")
        void stagedFileIsNeverALesson() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-601");
            inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            var staged = lessons.pathBase().resolve(lessonId.fileName() + ".tmp.0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");
            Files.writeString(staged, "staged bytes");

            assertAll("a staged file beside a lesson",
                    () -> assertTrue(TransactionId.isStagedName(staged.getFileName().toString()),
                            "the name is the staged name of a transaction"),
                    () -> assertFalse(LessonId.fromFileName(staged.getFileName().toString()).isPresent(),
                            "a staged name is no lesson id"),
                    () -> assertEquals(List.of(lessonId), store.list().stream()
                            .map(LessonResolution::lessonId).toList(), "the staged file is never listed"));
        }
    }
}
