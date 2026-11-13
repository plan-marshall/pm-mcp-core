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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
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
import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.Refusal;
import de.planmarshall.core.store.LessonResolution.Unavailable;

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

    private String planA;
    private String planB;

    @TempDir
    Path base;

    private JournalScope lessons;
    private LessonStore store;
    private RecordingLockManager locks;
    private AuditSink audit;

    @BeforeEach
    void createStore() throws IOException {
        lessons = JournalScope.lessons(base, PROJECT);
        Files.createDirectories(lessons.pathBase());
        store = new LessonStore(lessons);
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
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

    /** Creates a lesson in its own transaction, as a start does; asserted refusals call this in one invocation. */
    private void createInTransaction(LessonId lessonId, LessonStore.NewLesson newLesson) {
        run(write -> store.create(write, lessonId, newLesson, CLOCK));
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

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        @DisplayName("writes the lesson as the codec's bytes, with its sidecar entry and no journal left")
        void created() {
            var lessonId = new LessonId(CREATED_ID);

            var created = inTransaction(write -> store.create(write, lessonId,
                    LessonFixtures.newLesson().body(LessonFixtures.body()).build(), CLOCK));

            var document = LessonCodec.encode(created);
            assertAll("a created lesson",
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
            assertAll("the server-set members",
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
                    () -> assertNull(frontMatter.claim(), "a new lesson carries no claim"),
                    () -> assertNull(frontMatter.retired(), "a new lesson carries no retirement"),
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
        @DisplayName("over an existing id is refused by the store and leaves the existing lesson")
        void overExistingId() throws Exception {
            var lessonId = new LessonId(CREATED_ID);
            run(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            var existing = Files.readAllBytes(expectedPath(lessonId));
            var replacement = LessonFixtures.newLesson().build();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> createInTransaction(lessonId, replacement),
                    "a create over an existing id is refused");

            assertAll("a refused create",
                    () -> assertTrue(refusal.getMessage().contains(lessonId.value()), refusal::getMessage),
                    () -> assertArrayEquals(existing, Files.readAllBytes(expectedPath(lessonId)),
                            "the refused create leaves the existing lesson"));
        }

        @Test
        @DisplayName("of a lesson the same transaction holds already is refused while it is collected")
        void sameTransactionTwice() {
            var lessonId = new LessonId(CREATED_ID);
            var twice = LessonFixtures.newLesson().build();

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
                store.create(write, lessonId, twice, CLOCK);

                assertThrows(IllegalArgumentException.class, () -> store.create(write, lessonId, twice, CLOCK),
                        "a second create in one transaction is refused");
            }
        }

        @Test
        @DisplayName("of a transaction of another scope is refused")
        void otherScope() throws Exception {
            var plan = JournalScope.plan(base, PROJECT, planA);
            Files.createDirectories(plan.journalFile().getParent());
            var lessonId = new LessonId(CREATED_ID);
            var newLesson = LessonFixtures.newLesson().build();
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(plan.lockKey());
                var write = ScopeTransaction.open(transaction, plan, audit, CLOCK);

                assertAll("a transaction of another scope",
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.create(write, lessonId, newLesson, CLOCK),
                                "a create through another scope is refused"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.allocate(write, CLOCK),
                                "an allocation through another scope is refused"),
                        () -> assertThrows(IllegalArgumentException.class, () -> store.get(write, lessonId),
                                "a read through another scope is refused"),
                        () -> assertThrows(IllegalArgumentException.class,
                                () -> store.claim(write, lessonId, planA, NOW),
                                "a claim through another scope is refused"));
            }
        }

        @Test
        @DisplayName("belongs to the lesson scope only")
        void lessonScopeOnly() {
            var workspace = JournalScope.workspace(base, PROJECT);

            assertThrows(IllegalArgumentException.class, () -> new LessonStore(workspace),
                    "a workspace scope cannot open a lesson store");
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

            assertAll("the per-hour sequence",
                    () -> assertEquals(CREATED_ID, first.frontMatter().lessonId().value(),
                            "the first drawn id takes the sequence 001"),
                    () -> assertEquals("2026-10-13-09-002", second.frontMatter().lessonId().value(),
                            "the second id follows the first"),
                    () -> assertNotEquals(first.frontMatter().lessonId(), second.frontMatter().lessonId(),
                            "two writers under the lock never share an id"));
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
                    new String(LessonCodec.encode(new Lesson(frontMatterWithId(highest), LessonFixtures.body())),
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

            assertAll("within one transaction",
                    () -> assertNotEquals(ids.getFirst(), ids.getLast(), "the second draw skips the first"),
                    () -> assertEquals("2026-10-13-09-002", ids.getLast().value(), "the second draw is 002"));
        }

        @Test
        @DisplayName("counts an all-zero sequence as the number zero")
        void allZeroSequence() {
            run(write -> store.create(write, new LessonId("2026-10-13-09-000"),
                    LessonFixtures.newLesson().build(), CLOCK));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var drawn = store.allocate(ScopeTransaction.open(transaction, lessons, audit, CLOCK), CLOCK);

                assertEquals("2026-10-13-09-001", drawn.value(),
                        "000 is the number 0, so the next sequence is 001");
            }
        }

        @Test
        @DisplayName("counts a sequence written with leading zeros as the number it is")
        void leadingZeros() {
            run(write -> store.create(write, new LessonId("2026-10-13-09-999"),
                    LessonFixtures.newLesson().build(), CLOCK));
            run(write -> store.create(write, new LessonId("2026-10-13-09-0001"),
                    LessonFixtures.newLesson().build(), CLOCK));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var drawn = store.allocate(ScopeTransaction.open(transaction, lessons, audit, CLOCK), CLOCK);

                assertEquals("2026-10-13-09-1000", drawn.value(),
                        "0001 is the number 1, so 999 is still the highest");
            }
        }

        @Test
        @DisplayName("is unique over 200 concurrent creations of two threads on one hour")
        void twoHundredConcurrentCreations() throws Exception {
            var serializing = new SerializingLockManager();
            var start = new CountDownLatch(1);
            Callable<List<LessonId>> writer = () -> {
                start.await();
                var own = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
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

            assertAll("the concurrent allocations",
                    () -> assertEquals(200, ids.size(), "both threads recorded every id"),
                    () -> assertEquals(200, new LinkedHashSet<>(ids).size(), "no id was shared by the two writers"),
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
            var body = LessonFixtures.body() + "revised\n";

            var outcome = inTransaction(write -> store.revise(write, lessonId, body, sources, false,
                    NOW.plusSeconds(60)));

            var revised = assertInstanceOf(LessonResolution.Revised.class, outcome,
                    "the revision reports the revised lesson");
            var frontMatter = revised.lesson().frontMatter();
            assertAll("the revision",
                    () -> assertEquals(body, revised.lesson().body(), "the body is replaced wholesale"),
                    () -> assertEquals(2, frontMatter.revision(), "the revision counter is incremented"),
                    () -> assertEquals(2, frontMatter.revisions().size(), "the history holds both revisions"),
                    () -> assertEquals(1, frontMatter.revisions().getFirst().n(), "the first entry stays"),
                    () -> assertEquals(2, frontMatter.revisions().getLast().n(), "the new entry is the last"),
                    () -> assertEquals(sources, frontMatter.revisions().getLast().sources(),
                            "the new entry carries the new sources"),
                    () -> assertEquals(StoreDigest.of(body.getBytes(StandardCharsets.UTF_8)).sha256(),
                            frontMatter.revisions().getLast().bodySha256(), "the new entry carries the body digest"),
                    () -> assertEquals(sources, frontMatter.sources(), "the current sources are the new ones"),
                    () -> assertEquals(Optional.of(revised.lesson()), read(lessonId), "the revision is recorded"));
        }

        @Test
        @DisplayName("without sources is refused")
        void noSources() {
            var lessonId = new LessonId(CREATED_ID);
            run(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));

            assertThrows(IllegalArgumentException.class, () -> run(
                            write -> store.revise(write, lessonId, LessonFixtures.body(), List.of(), false, NOW)),
                    "a revision without sources is refused");
        }

        @Test
        @DisplayName("of a lesson whose sources name an untrusted lesson becomes untrusted")
        void citesUntrusted() {
            var lesson = enter(CLOCK);
            var lessonId = lesson.frontMatter().lessonId();
            var cited = enter(CLOCK);
            var citedId = cited.frontMatter().lessonId();
            run(write -> store.revise(write, citedId, LessonFixtures.body(), List.of(LessonFixtures.source()), true,
                    NOW));
            var source = new LessonFrontMatter.Source(LessonFrontMatter.Source.Kind.LESSON, citedId.value(), null);

            var outcome = inTransaction(
                    write -> store.revise(write, lessonId, LessonFixtures.body(), List.of(source), false, NOW));

            var revised = assertInstanceOf(LessonResolution.Revised.class, outcome,
                    "the revision reports the revised lesson");
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
                    write -> store.revise(write, lessonId, LessonFixtures.body(), List.of(LessonFixtures.source()), false,
                            NOW));

            var unavailable = assertInstanceOf(Unavailable.class, outcome,
                    "the retired lesson reports the state");
            assertAll("a retired lesson",
                    () -> assertEquals(Refusal.RETIRED, unavailable.refusal(), "the refusal names the retirement"),
                    () -> assertEquals(recorded, read(lessonId), "the retired lesson is untouched"));
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

            var found = assertInstanceOf(Found.class, resolved, "the created lesson is found");
            assertAll("the created lesson round trips",
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

    @Nested
    @DisplayName("the transition table")
    class Transitions {

        /** The lesson state of one cell of the table of Deliverable 5. */
        enum Cell {
            UNCLAIMED,
            CLAIMED_BY_A,
            CLAIMED_BY_B,
            RETIRED,
            PRESENT_UNREADABLE,
            STORE_TAMPERED,
            ABSENT
        }

        private static final LessonId TABLE_ID = new LessonId("2026-10-13-09-500");

        private Path tablePath() {
            return expectedPath(TABLE_ID);
        }

        private void arrange(Cell cell) throws IOException {
            switch (cell) {
                case UNCLAIMED -> inTransaction(write -> store.create(write, TABLE_ID,
                        LessonFixtures.newLesson().build(), CLOCK));
                case CLAIMED_BY_A -> {
                    inTransaction(write -> store.create(write, TABLE_ID, LessonFixtures.newLesson().build(), CLOCK));
                    run(write -> store.claim(write, TABLE_ID, planA, NOW));
                }
                case CLAIMED_BY_B -> {
                    inTransaction(write -> store.create(write, TABLE_ID, LessonFixtures.newLesson().build(), CLOCK));
                    run(write -> store.claim(write, TABLE_ID, planB, NOW));
                }
                case RETIRED -> {
                    inTransaction(write -> store.create(write, TABLE_ID, LessonFixtures.newLesson().build(), CLOCK));
                    run(write -> store.retire(write, TABLE_ID,
                            LessonFixtures.retirement(Verdict.OBSOLETE).build()));
                }
                case PRESENT_UNREADABLE -> {
                    Files.writeString(tablePath(), "# No front matter\n");
                    try (var transaction = locks.openTransaction()) {
                        transaction.acquire(lessons.lockKey());
                        ScopeIntegrity.rebaseline(transaction, lessons, TABLE_ID.fileName(), audit);
                    }
                }
                case STORE_TAMPERED -> {
                    inTransaction(write -> store.create(write, TABLE_ID, LessonFixtures.newLesson().build(), CLOCK));
                    var bytes = Files.readAllBytes(tablePath());
                    bytes[bytes.length - 1] = '!';
                    Files.write(tablePath(), bytes);
                }
                case ABSENT -> {
                    // no file and no entry
                }
            }
        }

        private LessonResolution.Outcome revise() {
            return withoutCommit(write -> store.revise(write, TABLE_ID, LessonFixtures.body(),
                    List.of(LessonFixtures.source()), false, NOW));
        }

        private LessonResolution.Outcome retire() {
            return withoutCommit(write -> store.retire(write, TABLE_ID,
                    LessonFixtures.retirement(Verdict.OBSOLETE).build()));
        }

        @ParameterizedTest
        @EnumSource(Cell.class)
        @DisplayName("a revision acts on every found lesson, claimed or not, and on no other state")
        void reviseEveryCell(Cell cell) throws Exception {
            arrange(cell);

            var outcome = revise();

            switch (cell) {
                case UNCLAIMED, CLAIMED_BY_A, CLAIMED_BY_B ->
                    assertInstanceOf(LessonResolution.Revised.class, outcome, "a found lesson is revised");
                case RETIRED -> assertEquals(LessonResolution.Refusal.RETIRED,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "a retired lesson is not revised");
                case PRESENT_UNREADABLE -> assertEquals(LessonResolution.Refusal.PRESENT_UNREADABLE,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "an unreadable lesson is not revised");
                case STORE_TAMPERED -> assertEquals(LessonResolution.Refusal.STORE_TAMPERED,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "a tampered lesson is not revised");
                case ABSENT -> assertEquals(LessonResolution.Refusal.ABSENT,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "an absent lesson is not revised");
            }
        }

        @ParameterizedTest
        @EnumSource(Cell.class)
        @DisplayName("a retirement acts on an unclaimed found lesson and on no other state")
        void retireEveryCell(Cell cell) throws Exception {
            arrange(cell);

            var outcome = retire();

            switch (cell) {
                case UNCLAIMED -> assertInstanceOf(LessonResolution.Retired.class, outcome,
                        "an unclaimed found lesson is retired");
                case CLAIMED_BY_A -> assertEquals(planA,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").claimingPlan(),
                        "a lesson claimed by A is not retired");
                case CLAIMED_BY_B -> assertEquals(planB,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").claimingPlan(),
                        "a lesson claimed by B is not retired");
                case RETIRED -> assertEquals(LessonResolution.Refusal.RETIRED,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "a retired lesson is not retired again");
                case PRESENT_UNREADABLE -> assertEquals(LessonResolution.Refusal.PRESENT_UNREADABLE,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "an unreadable lesson is not retired");
                case STORE_TAMPERED -> assertEquals(LessonResolution.Refusal.STORE_TAMPERED,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "a tampered lesson is not retired");
                case ABSENT -> assertEquals(LessonResolution.Refusal.ABSENT,
                        assertInstanceOf(LessonResolution.Unavailable.class, outcome,
                                "an unavailable outcome is reported").refusal(),
                        "an absent lesson is not retired");
            }
        }

        @ParameterizedTest
        @EnumSource(Cell.class)
        @DisplayName("a create writes an absent id and is refused over every state that has a file")
        void createEveryCell(Cell cell) throws Exception {
            arrange(cell);
            var replacement = LessonFixtures.newLesson().build();

            if (cell == Cell.ABSENT) {
                var created = inTransaction(write -> store.create(write, TABLE_ID, replacement, CLOCK));
                assertEquals(TABLE_ID, created.frontMatter().lessonId(), "an absent id is created");
                return;
            }
            assertThrows(RuntimeException.class, () -> createInTransaction(TABLE_ID, replacement),
                    "a create over an existing file is refused");
            assertEquals(1, store.list().size(), "the refused create wrote no second lesson");
        }

        @Test
        @DisplayName("a create over an id the sidecar records without a file is refused")
        void createOverDeletedOutOfBand() throws Exception {
            var lessonId = new LessonId("2026-10-13-09-510");
            inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
            Files.delete(expectedPath(lessonId));
            var replacement = LessonFixtures.newLesson().build();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> createInTransaction(lessonId, replacement),
                    "a create over a recorded identity is refused");
            assertAll("a create over a deleted lesson",
                    () -> assertTrue(refusal.getMessage().contains(lessonId.value()), refusal::getMessage),
                    () -> assertEquals(LessonResolution.State.DELETED_OUT_OF_BAND, store.get(lessonId).state(),
                            "the lesson keeps its identity and its entry"));
        }

        @Test
        @DisplayName("allocation never reuses an id whose file was removed out of band")
        void allocateAfterOutOfBandDelete() throws Exception {
            var used = new LessonId("2026-10-13-09-001");
            inTransaction(write -> store.create(write, used, LessonFixtures.newLesson().build(), CLOCK));
            Files.delete(expectedPath(used));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var drawn = store.allocate(ScopeTransaction.open(transaction, lessons, audit, CLOCK), CLOCK);

                assertEquals("2026-10-13-09-002", drawn.value(),
                        "the id of the deleted lesson is not handed out again");
            }
        }

        @Test
        @DisplayName("counts a higher lesson the transaction supplies before the first draw")
        void higherTargetInOneTransaction() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
                var supplied = new LessonId("2026-10-13-09-900");
                store.create(write, supplied, LessonFixtures.newLesson().build(), CLOCK);

                var drawn = store.allocate(write, CLOCK);

                assertEquals("2026-10-13-09-901", drawn.value(),
                        "the draw follows a lesson the transaction supplied itself");
            }
        }

        @Test
        @DisplayName("refuses a create whose id names another hour than the clock's")
        void createOfAnotherHour() {
            var otherHour = new LessonId("2026-10-13-08-001");
            var lesson = LessonFixtures.newLesson().build();

            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> createInTransaction(otherHour, lesson),
                    "a create named for another hour is refused");
            assertAll("a create of another hour",
                    () -> assertTrue(refusal.getMessage().contains(otherHour.value()), refusal::getMessage),
                    () -> assertEquals(List.of(), store.list(), "the refused create wrote nothing"));
        }

        @Test
        @DisplayName("two draws before the first create of a transaction return the same id")
        void twoDrawsInOneTransaction() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(lessons.lockKey());
                var write = ScopeTransaction.open(transaction, lessons, audit, CLOCK);
                var first = store.allocate(write, CLOCK);
                var second = store.allocate(write, CLOCK);
                store.create(write, first, LessonFixtures.newLesson().build(), CLOCK);
                var third = store.allocate(write, CLOCK);

                assertAll("two draws before the first create",
                        () -> assertEquals(first, second, "neither draw is a target of the transaction yet"),
                        () -> assertNotEquals(first, third, "the lesson the transaction holds is counted"));
            }
        }
    }

    private Optional<Lesson> read(LessonId lessonId) {
        return store.get(lessonId) instanceof Found found ? Optional.of(found.lesson()) : Optional.empty();
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
