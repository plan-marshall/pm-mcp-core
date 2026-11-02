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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.LinkedHashSet;
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

import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.Refusal;
import de.planmarshall.core.store.LessonResolution.Unavailable;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The store of the project lesson records (Store Schemas § Lesson Record; PM-IMPL-2, PM-IMPL-7): the id of the current
 * hour and its per-hour sequence drawn under the lesson store lock, a created lesson written as the bytes of its codec
 * with its sidecar entry, every server-set member derived by the store, and create, revise and retire leaving the old
 * or the new lesson after a write stopped at any step of the transaction (R14).
 */
@EnableGeneratorController
@DisplayName("Lesson store")
class LessonStoreTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Instant NOW = Instant.parse("2026-10-13T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String CREATED_ID = "2026-10-13-09-001";

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

    /**
     * Runs work in a transaction of its own and does NOT commit it: a transition that changes nothing adds no target,
     * so a commit would be refused. The transaction is closed without writing, which is what the store leaves behind
     * for a state it does not act on.
     */
    private <T> T withoutCommit(Function<ScopeTransaction, T> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            return work.apply(ScopeTransaction.open(transaction, lessons, audit, CLOCK));
        }
    }

    /** Allocates an id and creates its lesson in one transaction, as a start does. */
    private Lesson enter(Clock clock) {
        return inTransaction(write -> {
            var lessonId = store.allocate(write, clock);
            return store.create(write, lessonId, LessonFixtures.newLesson().build(), clock);
        });
    }

    /** The path of a lesson derived from the layout of the store, never from the object under test (R14 (9)). */
    private Path expectedPath(LessonId lessonId) {
        return LockKey.projectStore(base, PROJECT).resolve("lessons").resolve(lessonId.fileName());
    }

    private void recover() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(lessons.lockKey());
            JournalRecovery.recover(transaction, lessons, audit);
        }
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("writes the lesson as the codec's bytes, with its sidecar entry and no journal left")
        void created() throws Exception {
            var lessonId = new LessonId(CREATED_ID);

            var created = inTransaction(write -> store.create(write, lessonId,
                    LessonFixtures.newLesson().body("# Body\n").build(), CLOCK));

            var document = LessonCodec.encode(created);
            assertAll(
                    () -> assertArrayEquals(document, Files.readAllBytes(expectedPath(lessonId)),
                            "the lesson file is the codec's bytes"),
                    () -> assertEquals(Optional.of(StoreDigest.of(document)),
                            DigestSidecar.load(lessons.sidecarFile()).digest(lessonId.fileName()),
                            "the sidecar records the lesson the transaction wrote"),
                    () -> assertFalse(Files.exists(lessons.journalFile()), "the journal is closed"),
                    () -> assertEquals(Optional.of(created), read(lessonId), "the lesson reads back equal"));
        }

        @Test
        @DisplayName("derives the format version, the status, the revision, the claim and the retirement")
        void serverSetMembers() {
            var lessonId = new LessonId(CREATED_ID);
            var newLesson = LessonFixtures.newLesson().audience(Audience.UPSTREAM).build();

            var created = inTransaction(write -> store.create(write, lessonId, newLesson, CLOCK));

            var frontMatter = created.frontMatter();
            var bodySha256 = StoreDigest.of(created.body().getBytes(StandardCharsets.UTF_8)).sha256();
            assertAll(
                    () -> assertEquals(LessonFrontMatter.FORMAT_VERSION, frontMatter.formatVersion(),
                            "the store writes format version 1"),
                    () -> assertEquals(lessonId, frontMatter.lessonId(), "the store writes the drawn id"),
                    () -> assertEquals(Status.UPSTREAM_PENDING, frontMatter.status(),
                            "an upstream lesson is created upstream_pending"),
                    () -> assertEquals(NOW, frontMatter.createdAt(), "the store writes the clock's instant"),
                    () -> assertEquals(1, frontMatter.revision(), "the first revision is 1"),
                    () -> assertEquals(1, frontMatter.revisions().size(), "the first revision is recorded"),
                    () -> assertEquals(1, frontMatter.revisions().getFirst().n(), "its n is 1"),
                    () -> assertEquals(NOW, frontMatter.revisions().getFirst().at(), "its instant is the clock's"),
                    () -> assertEquals(newLesson.sources(), frontMatter.revisions().getFirst().sources(),
                            "it carries the sources of the body"),
                    () -> assertEquals(bodySha256, frontMatter.revisions().getFirst().bodySha256(),
                            "it carries the SHA-256 of the body"),
                    () -> assertEquals(newLesson.sources(), frontMatter.sources(),
                            "the current sources are the ones given"),
                    () -> assertEquals(null, frontMatter.claim(), "a new lesson carries no claim"),
                    () -> assertEquals(null, frontMatter.retired(), "a new lesson carries no retirement"),
                    () -> assertTrue(frontMatter.promotions().isEmpty(), "a new lesson carries no promotion"),
                    () -> assertTrue(frontMatter.mergedFrom().isEmpty(), "a new lesson merges nothing"));
        }

        @Test
        @DisplayName("of a local audience is created active")
        void localIsActive() {
            var lessonId = new LessonId(CREATED_ID);

            var created = inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(),
                    CLOCK));

            assertEquals(Status.ACTIVE, created.frontMatter().status(), "a local lesson is created active");
        }

        @Test
        @DisplayName("over an existing id is refused by the commit and leaves the existing lesson")
        void overExistingId() throws Exception {
            var lessonId = new LessonId(CREATED_ID);
            run(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            var existing = Files.readAllBytes(expectedPath(lessonId));

            var refusal = assertThrows(IllegalStateException.class,
                    () -> run(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK)));

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(lessonId.fileName()), refusal::getMessage),
                    () -> assertArrayEquals(existing, Files.readAllBytes(expectedPath(lessonId)),
                            "the refused create leaves the existing lesson"));
        }

        @Test
        @DisplayName("of a lesson the same transaction holds already is refused while it is collected")
        void sameTransactionTwice() {
            var lessonId = new LessonId(CREATED_ID);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
                store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK);

                assertThrows(IllegalArgumentException.class,
                        () -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            }
        }

        @Test
        @DisplayName("of a transaction of another scope is refused")
        void otherScope() throws Exception {
            var plan = JournalScope.plan(base, PROJECT, "a-plan");
            Files.createDirectories(plan.journalFile().getParent());
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(plan.lockKey());
                var write = ScopeTransaction.open(transaction, plan, audit, CLOCK);

                assertAll(
                        () -> assertThrows(IllegalArgumentException.class, () -> store.create(write,
                                new LessonId(CREATED_ID), LessonFixtures.newLesson().build(), CLOCK)),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.allocate(write, CLOCK)),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.get(write, new LessonId(CREATED_ID))),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.claim(write, new LessonId(CREATED_ID), "a-plan", NOW)));
            }
        }

        @Test
        @DisplayName("belongs to the lesson scope only")
        void lessonScopeOnly() {
            var workspace = JournalScope.workspace(base, PROJECT);

            assertThrows(IllegalArgumentException.class, () -> new LessonStore(workspace));
        }
    }

    @Nested
    @DisplayName("allocation")
    class Allocation {

        @Test
        @DisplayName("continues the sequence of the hour with at least three digits")
        void continuesTheSequence() {
            var first = enter(CLOCK);
            var second = enter(CLOCK);

            assertAll(
                    () -> assertEquals(CREATED_ID, first.frontMatter().lessonId().value(), "the first id is 001"),
                    () -> assertEquals("2026-10-13-09-002", second.frontMatter().lessonId().value(),
                            "the second id follows the first"),
                    () -> assertNotEquals(first.frontMatter().lessonId(), second.frontMatter().lessonId(),
                            "two writers never share an id"));
        }

        @Test
        @DisplayName("draws the id of the next hour after the clock moves")
        void nextHour() {
            enter(CLOCK);
            var moved = Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC);

            var created = enter(moved);

            assertEquals("2026-10-13-10-001", created.frontMatter().lessonId().value(),
                    "the id names the hour of the clock");
        }

        @Test
        @DisplayName("counts a larger sequence of the hour, four digits and more")
        void growsPastThreeDigits() throws Exception {
            var highest = new LessonId("2026-10-13-09-999");
            Files.writeString(expectedPath(highest),
                    new String(LessonCodec.encode(new Lesson(frontMatterWithId(highest), "body")),
                            StandardCharsets.UTF_8));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());

                var drawn = store.allocate(ScopeTransaction.open(transaction, lessons, audit, CLOCK), CLOCK);

                assertEquals("2026-10-13-09-1000", drawn.value(), "999 is followed by 1000");
            }
        }

        @Test
        @DisplayName("within one transaction never hands out an id the transaction already writes")
        void withinOneTransaction() {
            var ids = inTransaction(write -> {
                var first = store.allocate(write, CLOCK);
                store.create(write, first, LessonFixtures.newLesson().build(), CLOCK);
                var second = store.allocate(write, CLOCK);
                store.create(write, second, LessonFixtures.newLesson().build(), CLOCK);
                return List.of(first, second);
            });

            assertAll(
                    () -> assertNotEquals(ids.getFirst(), ids.getLast(), "the second draw skips the first"),
                    () -> assertEquals("2026-10-13-09-002", ids.getLast().value(), "the second draw is 002"));
        }

        @Test
        @DisplayName("is unique over 200 concurrent creations of two threads on one hour")
        void twoHundredConcurrentCreations() throws Exception {
            var serializing = new SerializingLockManager();
            var start = new CountDownLatch(1);
            Callable<List<LessonId>> writer = () -> {
                start.await();
                var own = new RecordingAuditSink();
                var ids = new ArrayList<LessonId>();
                for (var index = 0; index < 100; index++) {
                    try (var transaction = serializing.openTransaction()) {
                        transaction.acquire(lessons.lockKey());
                        var write = ScopeTransaction.open(transaction, lessons, own, CLOCK);
                        var lessonId = store.allocate(write, CLOCK);
                        store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK);
                        write.commit();
                        ids.add(lessonId);
                    }
                }
                return ids;
            };
            var ids = new ArrayList<LessonId>();
            try (var executor = Executors.newFixedThreadPool(2)) {
                var futures = List.of(executor.submit(writer), executor.submit(writer));
                start.countDown();
                for (var future : futures) {
                    ids.addAll(future.get());
                }
            }

            assertAll(
                    () -> assertEquals(200, ids.size(), "both threads recorded every id"),
                    () -> assertEquals(200, new LinkedHashSet<>(ids).size(), "no id was shared"),
                    () -> assertEquals(200, store.list().size(), "every creation wrote one lesson"),
                    () -> assertEquals(200, countFiles(), "every creation wrote one file"));
        }
    }

    @Nested
    @DisplayName("revision")
    class Revision {

        @Test
        @DisplayName("replaces the body wholesale and appends the revision with its digest and sources")
        void revisionReplacesTheBody() {
            var lesson = enter(CLOCK);
            var lessonId = lesson.frontMatter().lessonId();
            var sources = List.of(new LessonFrontMatter.Source(LessonFrontMatter.Source.Kind.FINDING,
                    LessonFixtures.text(), null));

            var outcome = inTransaction(write -> store.revise(write, lessonId, "# Second\n", sources, false,
                    NOW.plusSeconds(60)));

            var revised = assertInstanceOf(LessonResolution.Revised.class, outcome);
            var frontMatter = revised.lesson().frontMatter();
            assertAll(
                    () -> assertEquals("# Second\n", revised.lesson().body(), "the body is replaced wholesale"),
                    () -> assertEquals(2, frontMatter.revision(), "the revision counter is incremented"),
                    () -> assertEquals(2, frontMatter.revisions().size(), "the history holds both revisions"),
                    () -> assertEquals(1, frontMatter.revisions().getFirst().n(), "the first entry stays"),
                    () -> assertEquals(2, frontMatter.revisions().getLast().n(), "the new entry is the last"),
                    () -> assertEquals(sources, frontMatter.revisions().getLast().sources(),
                            "the new entry carries the new sources"),
                    () -> assertEquals(StoreDigest.of("# Second\n".getBytes(StandardCharsets.UTF_8)).sha256(),
                            frontMatter.revisions().getLast().bodySha256(), "the new entry carries the body digest"),
                    () -> assertEquals(sources, frontMatter.sources(), "the current sources are the new ones"),
                    () -> assertEquals(Optional.of(revised.lesson()), read(lessonId), "the revision is recorded"));
        }

        @Test
        @DisplayName("without sources is refused")
        void noSources() {
            var lessonId = new LessonId(CREATED_ID);
            run(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));

            assertThrows(IllegalArgumentException.class,
                    () -> run(write -> store.revise(write, lessonId, "# Body\n", List.of(), false, NOW)));
        }

        @Test
        @DisplayName("of a lesson whose sources name an untrusted lesson becomes untrusted")
        void citesUntrusted() {
            var lesson = enter(CLOCK);
            var lessonId = lesson.frontMatter().lessonId();
            var cited = enter(CLOCK);
            var citedId = cited.frontMatter().lessonId();
            run(write -> store.revise(write, citedId, "# Cited\n", List.of(LessonFixtures.source()), true, NOW));
            var source = new LessonFrontMatter.Source(LessonFrontMatter.Source.Kind.LESSON, citedId.value(), null);

            var outcome = inTransaction(
                    write -> store.revise(write, lessonId, "# Body\n", List.of(source), false, NOW));

            var revised = assertInstanceOf(LessonResolution.Revised.class, outcome);
            assertTrue(revised.lesson().frontMatter().derivedFromUntrusted(),
                    "a revision that cites an untrusted lesson becomes untrusted");
        }

        @Test
        @DisplayName("of a retired lesson changes nothing and reports the state")
        void retiredIsNotRevisable() {
            var lessonId = new LessonId(CREATED_ID);
            run(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            run(write -> store.retire(write, lessonId,
                    LessonFixtures.retirement(Verdict.OBSOLETE).build()));
            var recorded = read(lessonId);

            var outcome = withoutCommit(
                    write -> store.revise(write, lessonId, "# Changed\n", List.of(LessonFixtures.source()), false,
                            NOW));

            var unavailable = assertInstanceOf(Unavailable.class, outcome);
            assertAll(
                    () -> assertEquals(Refusal.RETIRED, unavailable.refusal(), "the refusal names the retirement"),
                    () -> assertEquals(recorded, read(lessonId), "the retired lesson is untouched"));
        }
    }

    @Nested
    @DisplayName("crash windows")
    class CrashWindows {

        @Test
        @DisplayName("of a create stopped at any step leave the old or the new lesson and a matching sidecar")
        void createAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-10-13-09-1" + "%03d".formatted(stop.ordinal() + 1));
                var newLesson = LessonFixtures.newLesson().body("# New\n").build();
                var newBytes = newLessonDocument(lessonId, newLesson);

                stopAt(stop, write -> store.create(write, lessonId, newLesson, CLOCK));
                recover();

                assertAll(
                        () -> assertConsistent(lessonId, null, newBytes),
                        () -> assertEquals(stop != Step.STAGE, isListed(lessonId),
                                "the lesson is listed once the journal was written"));
            }
        }

        @Test
        @DisplayName("of a revise stopped at any step leave the old or the new body and a matching sidecar")
        void reviseAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-10-13-09-2" + "%03d".formatted(stop.ordinal() + 1));
                var before = inTransaction(write -> store.create(write, lessonId,
                        LessonFixtures.newLesson().build(), CLOCK));
                var next = before.body() + "next\n";
                var sources = List.of(LessonFixtures.source());
                var newBytes = encodeAfter(before, before.frontMatter().toBuilder()
                        .revision(2)
                        .sources(sources)
                        .revisions(appended(before.frontMatter(), 2, sources, next))
                        .build(), next);

                stopAt(stop, write -> store.revise(write, lessonId, next, sources, false, NOW));
                recover();

                assertAll(
                        () -> assertConsistent(lessonId, LessonCodec.encode(before), newBytes),
                        () -> assertTrue(isListed(lessonId), "the lesson stays listed"));
            }
        }

        @Test
        @DisplayName("of a claim stopped at any step leave the old or the new claim and a matching sidecar")
        void claimAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-10-13-09-3" + "%03d".formatted(stop.ordinal() + 1));
                var before = inTransaction(write -> store.create(write, lessonId,
                        LessonFixtures.newLesson().build(), CLOCK));
                var newBytes = encodeAfter(before, before.frontMatter().toBuilder()
                        .claim(new LessonFrontMatter.Claim("a-plan", NOW)).build());

                stopAt(stop, write -> store.claim(write, lessonId, "a-plan", NOW));
                recover();

                assertAll(
                        () -> assertConsistent(lessonId, LessonCodec.encode(before), newBytes),
                        () -> assertTrue(isListed(lessonId), "the lesson stays listed"));
            }
        }

        @Test
        @DisplayName("of an end applied stopped at any step leave the old or the retired lesson")
        void endAtEveryStep() {
            for (var stop : Step.values()) {
                var lessonId = new LessonId("2026-10-13-09-4" + "%03d".formatted(stop.ordinal() + 1));
                var created = inTransaction(write -> store.create(write, lessonId,
                        LessonFixtures.newLesson().build(), CLOCK));
                run(write -> store.claim(write, lessonId, "a-plan", NOW));
                var before = read(lessonId).orElseThrow();
                var newBytes = encodeAfterEnd(before);

                stopAt(stop, write -> store.endClaimApplied(write, lessonId, "a-plan", NOW));
                recover();

                assertAll(
                        () -> assertConsistent(lessonId, LessonCodec.encode(before), newBytes),
                        () -> assertTrue(isListed(lessonId), "the lesson stays listed"),
                        () -> assertTrue(created.frontMatter().lessonId().equals(lessonId),
                                "the fixture created the lesson"));
            }
        }
    }

    @Nested
    @DisplayName("characters the YAML reader could refuse")
    class RoundTrip {

        /** The characters the landing analysis of PLAN-V2-11 named: NEL, a C1 control, two noncharacters and two separators. */
        private static final String TRICKY = "a\u0085b\u009fc\ufffed\uffffe\u2028f\u2029g";

        @Test
        @DisplayName("a created lesson reads back the title, the statement and the sources unchanged")
        void createRoundTrips() {
            var lessonId = new LessonId(CREATED_ID);
            var newLesson = LessonFixtures.newLesson()
                    .title(TRICKY)
                    .remedy(new LessonFrontMatter.Remedy(LessonFrontMatter.Remedy.Kind.CHECK,
                            TRICKY + " and a statement", null))
                    .sources(List.of(new LessonFrontMatter.Source(LessonFrontMatter.Source.Kind.LOG, TRICKY, null)))
                    .build();

            var created = inTransaction(write -> store.create(write, lessonId, newLesson, CLOCK));
            var resolved = store.get(lessonId);

            var found = assertInstanceOf(Found.class, resolved);
            assertAll(
                    () -> assertEquals(TRICKY, created.frontMatter().title(), "the title is written verbatim"),
                    () -> assertEquals(TRICKY, found.lesson().frontMatter().title(),
                            "the title reads back verbatim"),
                    () -> assertEquals(newLesson.remedy(), found.lesson().frontMatter().remedy(),
                            "the statement reads back verbatim"),
                    () -> assertEquals(newLesson.sources(), found.lesson().frontMatter().sources(),
                            "the sources read back verbatim"),
                    () -> assertEquals(StoreIntegrity.VERIFIED, found.integrity(),
                            "the written lesson matches its sidecar"));
        }
    }

    // ---------------------------------------------------------------- helpers of the crash windows

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
            assertThrows(StopWrite.class, write::commit);
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
            assertTrue(oldBytes == null, "the operation created the lesson, so it cannot be absent");
            return false;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new AssertionError("The lesson file cannot be read: " + file, e);
        }
        assertAll(
                () -> assertTrue(Arrays.equals(bytes, oldBytes) || Arrays.equals(bytes, newBytes),
                        "the lesson is the old or the new one, never a torn one"),
                () -> assertEquals(Optional.of(StoreDigest.of(bytes)), recorded,
                        "the sidecar entry matches the lesson file"));
        return Arrays.equals(bytes, newBytes);
    }

    private byte[] newLessonDocument(LessonId lessonId, LessonStore.NewLesson newLesson) {
        var frontMatter = LessonFrontMatter.builder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION)
                .lessonId(lessonId)
                .title(newLesson.title())
                .component(newLesson.component())
                .category(newLesson.category())
                .bundle(newLesson.bundle())
                .rule(newLesson.rule())
                .remedy(newLesson.remedy())
                .status(newLesson.audience() == Audience.UPSTREAM ? Status.UPSTREAM_PENDING : Status.ACTIVE)
                .provenance(newLesson.provenance())
                .derivedFromUntrusted(newLesson.derivedFromUntrusted())
                .audience(newLesson.audience())
                .createdAt(NOW)
                .toolVersion(newLesson.toolVersion())
                .promotions(List.of())
                .revision(1)
                .sources(newLesson.sources())
                .revisions(List.of(new LessonFrontMatter.Revision(1, NOW, newLesson.sources(),
                        StoreDigest.of(newLesson.body().getBytes(StandardCharsets.UTF_8)).sha256())))
                .mergedFrom(List.of())
                .claim(null)
                .retired(null)
                .build();
        return LessonCodec.encode(new Lesson(frontMatter, newLesson.body()));
    }

    private byte[] encodeAfter(Lesson before, LessonFrontMatter frontMatter) {
        return encodeAfter(before, frontMatter, before.body());
    }

    private static List<LessonFrontMatter.Revision> appended(LessonFrontMatter frontMatter, int revision,
            List<LessonFrontMatter.Source> sources, String body) {
        var revisions = new ArrayList<>(frontMatter.revisions());
        revisions.add(new LessonFrontMatter.Revision(revision, NOW, sources,
                StoreDigest.of(body.getBytes(StandardCharsets.UTF_8)).sha256()));
        return List.copyOf(revisions);
    }

    private static byte[] encodeAfter(Lesson before, LessonFrontMatter frontMatter, String body) {
        return LessonCodec.encode(new Lesson(frontMatter, body));
    }

    private byte[] encodeAfterEnd(Lesson before) {
        var frontMatter = before.frontMatter().toBuilder()
                .status(Status.RETIRED)
                .claim(null)
                .retired(LessonFrontMatter.Retired.builder()
                        .at(NOW)
                        .by("a-plan")
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

    private long countFiles() {
        try (var entries = Files.list(lessons.pathBase())) {
            return entries.filter(entry -> entry.getFileName().toString().endsWith(".md")).count();
        } catch (IOException e) {
            throw new AssertionError("The lesson store cannot be listed", e);
        }
    }

    private static LessonFrontMatter frontMatterWithId(LessonId lessonId) {
        return LessonFixtures.frontMatterBuilder().lessonId(lessonId).build();
    }
}
