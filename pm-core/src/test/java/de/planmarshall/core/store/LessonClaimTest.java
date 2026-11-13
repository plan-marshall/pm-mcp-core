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

import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.Outcome;
import de.planmarshall.core.store.LessonResolution.Refusal;
import de.planmarshall.core.store.LessonResolution.Unavailable;

/**
 * The claim of a lesson by a plan, its release and its end (Store Schemas § Lesson Record, Claim and § A Claimed Lesson
 * Ends with the Landing; R14): a claim is set from the front matter actually recorded, never from the argument; the
 * release and the end are authorized by the durable {@code claim.plan_id} alone; a repeated end is a no-op that reports
 * the retirement; and every cell of the lesson-state by operation table leaves a lesson the store did not act on
 * unchanged.
 */
@EnableGeneratorController
@DisplayName("Lesson claim")
class LessonClaimTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
    private static final Instant NOW = CLOCK.instant();
    private static final LessonId LESSON_ID = new LessonId("2026-11-02-09-001");

    private String project;
    private String planA;
    private String planB;

    @TempDir
    Path base;

    private JournalScope lessons;
    private LessonStore store;
    private RecordingLockManager locks;
    private AuditSink audit;

    /** The lesson state of one cell of the table of Deliverable 5. */
    enum State {
        UNCLAIMED,
        CLAIMED_BY_A,
        CLAIMED_BY_B,
        RETIRED,
        PRESENT_UNREADABLE,
        STORE_TAMPERED,
        ABSENT
    }

    @BeforeEach
    void createStore() throws IOException {
        project = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        planA = "plan-a-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        planB = "plan-b-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        lessons = JournalScope.lessons(base, project);
        Files.createDirectories(lessons.pathBase());
        store = new LessonStore(lessons);
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
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

    private Path expectedPath() {
        return LockKey.projectStore(base, project).resolve("lessons").resolve(LESSON_ID.fileName());
    }

    private void acceptEdit() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            ScopeIntegrity.rebaseline(transaction, lessons, LESSON_ID.fileName(), audit);
        }
    }

    /** Arranges one cell's lesson state. */
    private void arrange(State state) throws IOException {
        switch (state) {
            case UNCLAIMED -> create();
            case CLAIMED_BY_A -> {
                create();
                run(write -> store.claim(write, LESSON_ID, planA, NOW));
            }
            case CLAIMED_BY_B -> {
                create();
                run(write -> store.claim(write, LESSON_ID, planB, NOW));
            }
            case RETIRED -> {
                create();
                run(write -> store.retire(write, LESSON_ID, LessonFixtures.retirement(Verdict.OBSOLETE).build()));
            }
            case PRESENT_UNREADABLE -> {
                Files.writeString(expectedPath(), "# No front matter\n");
                acceptEdit();
            }
            case STORE_TAMPERED -> {
                create();
                var bytes = Files.readAllBytes(expectedPath());
                bytes[bytes.length - 1] = '!';
                Files.write(expectedPath(), bytes);
            }
            case ABSENT -> {
                // no file and no entry
            }
        }
    }

    private Lesson create() {
        return inTransaction(write -> store.create(write, LESSON_ID, LessonFixtures.newLesson().build(), CLOCK));
    }

    private byte[] bytesOrNull() {
        try {
            return Files.exists(expectedPath()) ? Files.readAllBytes(expectedPath()) : null;
        } catch (IOException e) {
            throw new AssertionError("The lesson file cannot be read", e);
        }
    }

    private void assertUnchanged(byte[] before) {
        assertArrayEquals(before, bytesOrNull(), "a cell the store does not act on leaves the lesson unchanged");
    }

    private static Unavailable refused(Outcome outcome, Refusal refusal, String claimingPlan) {
        var unavailable = assertInstanceOf(Unavailable.class, outcome, "the outcome is a refusal");
        assertEquals(refusal, unavailable.refusal(), "the refusal names the state");
        assertEquals(claimingPlan, unavailable.claimingPlan(), "the refusal names the claiming plan");
        return unavailable;
    }

    private Refusal resolutionRefusal(State state) {
        return switch (state) {
            case PRESENT_UNREADABLE -> Refusal.PRESENT_UNREADABLE;
            case STORE_TAMPERED -> Refusal.STORE_TAMPERED;
            case ABSENT -> Refusal.ABSENT;
            default -> throw new IllegalArgumentException("The state has a lesson the store reads: " + state);
        };
    }

    @Nested
    @DisplayName("claim")
    class Claim {

        @Test
        @DisplayName("sets the plan and the instant from the front matter actually recorded")
        void setsTheClaim() {
            create();

            var outcome = inTransaction(write -> store.claim(write, LESSON_ID, planA, NOW));

            var claimed = assertInstanceOf(LessonResolution.Claimed.class, outcome,
                    "an unclaimed lesson is claimed");
            var claim = claimed.lesson().frontMatter().claim();
            assertAll("a claim sets the plan and instant from the file",
                    () -> assertEquals(planA, claim.planId(), "the claim names the plan"),
                    () -> assertEquals(NOW, claim.claimedAt(), "the claim carries the instant"),
                    () -> assertEquals(Status.ACTIVE, claimed.lesson().frontMatter().status(),
                            "the status stays active"),
                    () -> assertEquals(planA, readFound().frontMatter().claim().planId(),
                            "the claim is recorded"));
        }

        @ParameterizedTest
        @EnumSource(State.class)
        @DisplayName("of a lesson in another state changes nothing and reports the state")
        void otherStates(State state) throws Exception {
            arrange(state);
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.claim(write, LESSON_ID, planA, NOW));

            switch (state) {
                case UNCLAIMED -> assertInstanceOf(LessonResolution.Claimed.class, outcome,
                        "an unclaimed lesson is claimed");
                case CLAIMED_BY_A -> refused(outcome, Refusal.CLAIMED, planA);
                case CLAIMED_BY_B -> refused(outcome, Refusal.CLAIMED, planB);
                case RETIRED -> refused(outcome, Refusal.RETIRED, null);
                default -> refused(outcome, resolutionRefusal(state), null);
            }
            if (state != State.UNCLAIMED) {
                assertUnchanged(before);
            }
        }

        @ParameterizedTest
        @EnumSource(State.class)
        @DisplayName("by plan B changes nothing in another state and reports the state")
        void otherStatesByB(State state) throws Exception {
            arrange(state);
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.claim(write, LESSON_ID, planB, NOW));

            switch (state) {
                case UNCLAIMED -> assertInstanceOf(LessonResolution.Claimed.class, outcome,
                        "an unclaimed lesson is claimed");
                case CLAIMED_BY_A -> refused(outcome, Refusal.CLAIMED, planA);
                case CLAIMED_BY_B -> refused(outcome, Refusal.CLAIMED, planB);
                case RETIRED -> refused(outcome, Refusal.RETIRED, null);
                default -> refused(outcome, resolutionRefusal(state), null);
            }
            if (state != State.UNCLAIMED) {
                assertUnchanged(before);
            }
        }
    }

    @Nested
    @DisplayName("release")
    class Release {

        @Test
        @DisplayName("clears the claim and leaves the lesson active")
        void clearsTheClaim() {
            create();
            run(write -> store.claim(write, LESSON_ID, planA, NOW));

            var outcome = inTransaction(write -> store.release(write, LESSON_ID, planA));

            var released = assertInstanceOf(LessonResolution.Released.class, outcome,
                    "the plan's own claim is released");
            assertAll("a release clears the claim and leaves the lesson active",
                    () -> assertNull(released.lesson().frontMatter().claim(), "the claim is cleared"),
                    () -> assertEquals(Status.ACTIVE, released.lesson().frontMatter().status(),
                            "the lesson is not retired"),
                    () -> assertNull(readFound().frontMatter().claim(), "the release is recorded"));
        }

        @Test
        @DisplayName("keeps an upstream_pending lesson upstream_pending")
        void keepsTheUpstreamStatus() {
            inTransaction(write -> store.create(write, LESSON_ID,
                    LessonFixtures.newLesson()
                            .audience(LessonFrontMatter.Audience.UPSTREAM).build(), CLOCK));
            run(write -> store.claim(write, LESSON_ID, planA, NOW));

            var outcome = inTransaction(write -> store.release(write, LESSON_ID, planA));

            var released = assertInstanceOf(LessonResolution.Released.class, outcome,
                    "the plan's own claim is released");
            assertEquals(Status.UPSTREAM_PENDING, released.lesson().frontMatter().status(),
                    "a release does not retire and does not reclassify");
        }

        @ParameterizedTest
        @EnumSource(State.class)
        @DisplayName("of a lesson in another state changes nothing and reports the state")
        void otherStates(State state) throws Exception {
            arrange(state);
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.release(write, LESSON_ID, planA));

            switch (state) {
                case CLAIMED_BY_A -> assertInstanceOf(LessonResolution.Released.class, outcome,
                        "the plan's own claim is released");
                case UNCLAIMED -> refused(outcome, Refusal.UNCLAIMED, null);
                case CLAIMED_BY_B -> refused(outcome, Refusal.CLAIMED, planB);
                case RETIRED -> refused(outcome, Refusal.RETIRED, null);
                default -> refused(outcome, resolutionRefusal(state), null);
            }
            if (state != State.CLAIMED_BY_A) {
                assertUnchanged(before);
            }
        }

        @ParameterizedTest
        @EnumSource(State.class)
        @DisplayName("by plan B changes nothing in another state and reports the state")
        void otherStatesByB(State state) throws Exception {
            arrange(state);
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.release(write, LESSON_ID, planB));

            switch (state) {
                case CLAIMED_BY_B -> assertInstanceOf(LessonResolution.Released.class, outcome,
                        "the plan's own claim is released");
                case UNCLAIMED -> refused(outcome, Refusal.UNCLAIMED, null);
                case CLAIMED_BY_A -> refused(outcome, Refusal.CLAIMED, planA);
                case RETIRED -> refused(outcome, Refusal.RETIRED, null);
                default -> refused(outcome, resolutionRefusal(state), null);
            }
            if (state != State.CLAIMED_BY_B) {
                assertUnchanged(before);
            }
        }
    }

    @Nested
    @DisplayName("the end of a claim")
    class End {

        @Test
        @DisplayName("retires with the verdict applied by the plan and clears the claim")
        void applied() {
            create();
            run(write -> store.claim(write, LESSON_ID, planA, NOW));

            var outcome = inTransaction(write -> store.endClaimApplied(write, LESSON_ID, planA, NOW));

            var retired = assertInstanceOf(LessonResolution.Retired.class, outcome,
                    "the claim ends with a retirement");
            var retirement = retired.lesson().frontMatter().retired();
            assertAll("the end of a claim retires with the applied verdict",
                    () -> assertEquals(Verdict.APPLIED, retirement.verdict(), "the verdict is applied"),
                    () -> assertEquals(planA, retirement.by(), "the plan is the author"),
                    () -> assertEquals(NOW, retirement.at(), "the instant is the one given"),
                    () -> assertEquals(LessonStore.END_OF_CLAIM_REASON, retirement.reason(),
                            "the reason states the landing"),
                    () -> assertEquals(LessonFrontMatter.Retired.LessonState.FOUND, retirement.lessonState(),
                            "the recorded state is the found lesson"),
                    () -> assertNull(retired.lesson().frontMatter().claim(),
                            "the claim is cleared and the plan stays in retired.by"),
                    () -> assertEquals(Status.RETIRED, retired.lesson().frontMatter().status(),
                            "the lesson is retired"),
                    () -> assertEquals(planA, readFound().frontMatter().retired().by(),
                            "reach is derivable from retired.by"));
        }

        @Test
        @DisplayName("a second call after the end is a no-op that reports the retirement")
        void repeatedIsANoOp() {
            create();
            run(write -> store.claim(write, LESSON_ID, planA, NOW));
            run(write -> store.endClaimApplied(write, LESSON_ID, planA, NOW));
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.endClaimApplied(write, LESSON_ID, planA, NOW));

            assertAll("a repeated end reports the retirement and writes nothing",
                    () -> assertInstanceOf(LessonResolution.Retired.class, outcome,
                            "a repeated end reports the retirement"),
                    () -> assertUnchanged(before));
        }

        @Test
        @DisplayName("after a release changes nothing and reports that no claim is held")
        void afterRelease() {
            create();
            run(write -> store.claim(write, LESSON_ID, planA, NOW));
            run(write -> store.release(write, LESSON_ID, planA));

            var outcome = withoutCommit(write -> store.endClaimApplied(write, LESSON_ID, planA, NOW));

            refused(outcome, Refusal.UNCLAIMED, null);
        }

        @Test
        @DisplayName("of a lesson another plan retired is refused and reports the state")
        void retiredByAnother() {
            create();
            run(write -> store.retire(write, LESSON_ID, LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            var outcome = withoutCommit(write -> store.endClaimApplied(write, LESSON_ID, planA, NOW));

            refused(outcome, Refusal.RETIRED, null);
        }

        @ParameterizedTest
        @EnumSource(State.class)
        @DisplayName("of a lesson in another state changes nothing and reports the state")
        void otherStates(State state) throws Exception {
            arrange(state);
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.endClaimApplied(write, LESSON_ID, planA, NOW));

            switch (state) {
                case CLAIMED_BY_A -> assertInstanceOf(LessonResolution.Retired.class, outcome,
                        "the plan's own claim is retired applied");
                case UNCLAIMED -> refused(outcome, Refusal.UNCLAIMED, null);
                case CLAIMED_BY_B -> refused(outcome, Refusal.CLAIMED, planB);
                case RETIRED -> refused(outcome, Refusal.RETIRED, null);
                default -> refused(outcome, resolutionRefusal(state), null);
            }
            if (state != State.CLAIMED_BY_A) {
                assertUnchanged(before);
            }
        }

        @ParameterizedTest
        @EnumSource(State.class)
        @DisplayName("by plan B changes nothing in another state and reports the state")
        void otherStatesByB(State state) throws Exception {
            arrange(state);
            var before = bytesOrNull();

            var outcome = withoutCommit(write -> store.endClaimApplied(write, LESSON_ID, planB, NOW));

            switch (state) {
                case CLAIMED_BY_B -> assertInstanceOf(LessonResolution.Retired.class, outcome,
                        "the plan's own claim is retired applied");
                case UNCLAIMED -> refused(outcome, Refusal.UNCLAIMED, null);
                case CLAIMED_BY_A -> refused(outcome, Refusal.CLAIMED, planA);
                case RETIRED -> refused(outcome, Refusal.RETIRED, null);
                default -> refused(outcome, resolutionRefusal(state), null);
            }
            if (state != State.CLAIMED_BY_B) {
                assertUnchanged(before);
            }
        }
    }

    private Lesson readFound() {
        return assertInstanceOf(Found.class, store.get(LESSON_ID)).lesson();
    }
}
