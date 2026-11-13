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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.LessonCodec.UnreadableReason;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Status;
import de.planmarshall.core.store.LessonResolution.DeletedOutOfBand;
import de.planmarshall.core.store.LessonResolution.EmptyShell;
import de.planmarshall.core.store.LessonResolution.Found;
import de.planmarshall.core.store.LessonResolution.PresentUnreadable;
import de.planmarshall.core.store.LessonResolution.StoreTampered;
import de.planmarshall.core.store.LessonResolution.Untracked;
import de.planmarshall.core.store.LessonStore.Claims;

/**
 * The one resolver of the lesson store (Store Schemas § Lesson Record, One Resolver, Four States; PM-IMPL-7): every
 * listed lesson resolves by the id it is listed under, the ported fixtures of PM-WATCH-STORE-14 list and resolve, a
 * headerless file is {@code present_unreadable} and never {@code absent}, the three integrity states of the digest
 * sidecar are decided under the lock, and a lock-free read reports a difference as unverified and decides nothing — but
 * for one observed absence: a sidecar entry whose file is gone is {@code deleted_out_of_band} there too, and never
 * {@code absent}.
 */
@EnableGeneratorController
@DisplayName("Lesson resolver")
class LessonResolverTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-02T09:00:00Z"), ZoneOffset.UTC);
    private static final List<String> ALL_LISTED = Stream.concat(LessonFixtures.PORTED_YAML_IDS.stream(),
            LessonFixtures.PORTED_HEADERLESS_IDS.stream()).toList();

    @TempDir
    Path base;

    private String project;
    private String plan;
    private String otherPlan;
    private JournalScope lessons;
    private LessonStore store;
    private RecordingLockManager locks;
    private AuditSink audit;

    @BeforeEach
    void createStore() throws IOException {
        project = Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
        plan = "plan-" + Generators.integers(1000, 9999).next();
        otherPlan = "plan-other-" + Generators.integers(1000, 9999).next();
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

    private LessonResolution lockedGet(LessonId lessonId) {
        return withoutCommit(write -> store.get(write, lessonId));
    }

    private Path expectedPath(LessonId lessonId) {
        return LockKey.projectStore(base, project).resolve("lessons").resolve(lessonId.fileName());
    }

    /** Copies a committed fixture of {@code src/test/resources/ported/lessons} into the store. */
    private Path copyFixture(String lessonId) throws IOException {
        var target = expectedPath(new LessonId(lessonId));
        try (InputStream resource = getClass().getResourceAsStream(
                     "/" + LessonFixtures.PORTED_DIRECTORY + "/" + lessonId + ".md")) {
            assertNotNull(resource, "the fixture " + lessonId + " is committed");
            Files.write(target, resource.readAllBytes());
        }
        return target;
    }

    private Lesson create(LessonId lessonId) {
        return inTransaction(write -> store.create(write, lessonId, LessonFixtures.newLesson().build(), CLOCK));
    }

    private void replaceByte(Path file, byte replacement) throws IOException {
        var bytes = Files.readAllBytes(file);
        bytes[bytes.length - 1] = replacement;
        Files.write(file, bytes);
    }

    @Nested
    @DisplayName("the ported fixtures")
    class PortedFixtures {

        @Test
        @DisplayName("all fifteen list and resolve by the exact id they are listed under")
        void allListAndResolve() throws Exception {
            for (var lessonId : ALL_LISTED) {
                copyFixture(lessonId);
            }

            var resolutions = store.list();

            assertAll(
                    "Every fixture lists and resolves by its id",
                    () -> assertEquals(ALL_LISTED.size(), resolutions.size(), "every fixture is listed"),
                    () -> resolutions.forEach(resolution -> assertTrue(
                            ALL_LISTED.contains(resolution.lessonId().value()),
                            "the resolution names a listed id")),
                    () -> resolutions.forEach(resolution -> assertEquals(resolution.lessonId(),
                            store.get(resolution.lessonId()).lessonId(),
                            "get resolves the id the listing names")));
        }

        @Test
        @DisplayName("the nine YAML fixtures resolve found, under their file name although their front matter states another id")
        void yamlFixturesAreFound() throws Exception {
            for (var lessonId : LessonFixtures.PORTED_YAML_IDS) {
                copyFixture(lessonId);
            }

            var resolutions = store.list();

            assertAll(
                    "Every YAML fixture resolves found",
                    () -> assertEquals(LessonFixtures.PORTED_YAML_IDS.size(), resolutions.size(),
                            "every YAML fixture is listed"),
                    () -> resolutions.forEach(resolution -> assertInstanceOf(Found.class, resolution,
                            "a YAML fixture resolves found: " + resolution)),
                    () -> assertEquals(Found.class, store.get(new LessonId(LessonFixtures.PORTED_ID_YAML_FIRST))
                            .getClass(), "the first YAML fixture resolves found"),
                    () -> LessonFixtures.PORTED_YAML_IDS.forEach(lessonId -> assertTrue(
                            statedLegacyIdDiffers(lessonId),
                            "the front matter states the legacy id, not the file name: " + lessonId)));
        }

        @Test
        @DisplayName("the six headerless fixtures are present_unreadable and never absent")
        void headerlessArePresentUnreadable() throws Exception {
            for (var lessonId : LessonFixtures.PORTED_HEADERLESS_IDS) {
                copyFixture(lessonId);
            }

            var resolutions = store.list();

            assertAll(
                    "Every headerless fixture is present_unreadable",
                    () -> assertEquals(LessonFixtures.PORTED_HEADERLESS_IDS.size(), resolutions.size(),
                            "every headerless fixture is listed"),
                    () -> resolutions.forEach(resolution -> assertInstanceOf(PresentUnreadable.class, resolution,
                            "a headerless fixture is never absent")),
                    () -> resolutions.forEach(resolution -> assertEquals(UnreadableReason.NO_OPENING_FENCE,
                            ((PresentUnreadable) resolution).reason(), "the reason names the missing fence")),
                    () -> resolutions.forEach(resolution -> assertEquals(expectedPath(resolution.lessonId()),
                            ((PresentUnreadable) resolution).path(), "the listing carries the path")));
        }

        @Test
        @DisplayName("the fifteen fixture lists name every committed file of the directory")
        void fixtureListsMatchTheDirectory() throws Exception {
            try (var entries = Files.list(Path.of(
                         getClass().getResource("/" + LessonFixtures.PORTED_DIRECTORY).toURI()))) {
                var files = entries.filter(entry -> entry.getFileName().toString().endsWith(".md"))
                        .map(entry -> entry.getFileName().toString().replace(".md", ""))
                        .sorted().toList();

                assertEquals(ALL_LISTED.stream().sorted().toList(), files,
                        "every committed fixture is named by one of the two lists");
            }
        }

        private boolean statedLegacyIdDiffers(String lessonId) {
            return store.get(new LessonId(lessonId)) instanceof Found found
                    && !found.lesson().frontMatter().lessonId().value().equals(lessonId);
        }
    }

    @Nested
    @DisplayName("the four states")
    class FourStates {

        @Test
        @DisplayName("a lesson of the store resolves found and has no body verdict")
        void found() {
            var lessonId = new LessonId("2026-11-02-09-001");
            var created = create(lessonId);

            var resolution = store.get(lessonId);

            var found = assertInstanceOf(Found.class, resolution, "a stored lesson resolves found");
            assertAll(
                    "A stored lesson resolves found",
                    () -> assertEquals(created, found.lesson(), "the lesson is the one written"),
                    () -> assertEquals(expectedPath(lessonId), found.path(), "the resolution carries the path"),
                    () -> assertEquals(StoreIntegrity.VERIFIED, found.integrity(), "the lesson matches its sidecar"));
        }

        @Test
        @DisplayName("a lesson that never existed is absent")
        void absent() {
            var lessonId = new LessonId("2026-11-02-09-001");

            assertAll(
                    "A lesson that never existed is absent",
                    () -> assertEquals(LessonResolution.State.ABSENT, store.get(lessonId).state(),
                            "no file and no entry is absent"),
                    () -> assertEquals(LessonResolution.State.ABSENT, lockedGet(lessonId).state(),
                            "the locked read agrees"));
        }

        @Test
        @DisplayName("a body without a non-whitespace character is an empty shell")
        void emptyShell() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            var lesson = new Lesson(LessonFixtures.frontMatterBuilder().lessonId(lessonId).build(), " \n\t\n");
            Files.write(expectedPath(lessonId), LessonCodec.encode(lesson));

            var resolution = store.get(lessonId);

            var shell = assertInstanceOf(EmptyShell.class, resolution, "a blank body resolves as an empty shell");
            assertAll(
                    "A blank body is an empty shell",
                    () -> assertEquals(LessonResolution.State.EMPTY_SHELL, shell.state(), "the state is empty_shell"),
                    () -> assertEquals(lesson.frontMatter(), shell.lesson().frontMatter(),
                            "the front matter is readable"),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, shell.integrity(),
                            "a blank body carries the integrity of its read"));
        }

        @Test
        @DisplayName("a blank body that differs from its entry is an empty shell and unverified")
        void tamperedEmptyShell() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            create(lessonId);
            Files.write(expectedPath(lessonId), LessonCodec.encode(
                    new Lesson(LessonFixtures.frontMatterBuilder().lessonId(lessonId).build(), "   \n")));

            var shell = assertInstanceOf(EmptyShell.class, store.get(lessonId),
                    "a differing blank body is still an empty shell");

            assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, shell.integrity(),
                    "a blank body that differs from its entry is still unverified");
        }

        @Test
        @DisplayName("a headerless file is present_unreadable with its path, never absent")
        void presentUnreadable() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            Files.writeString(expectedPath(lessonId), "# No front matter\n");

            var resolution = store.get(lessonId);

            var unreadable = assertInstanceOf(PresentUnreadable.class, resolution,
                    "a headerless file is present_unreadable");
            assertAll(
                    "A headerless file is present_unreadable",
                    () -> assertEquals(UnreadableReason.NO_OPENING_FENCE, unreadable.reason(),
                            "the reason names the missing fence"),
                    () -> assertEquals(expectedPath(lessonId), unreadable.path(), "the resolution carries the path"),
                    () -> assertFalse(store.get(lessonId) instanceof LessonResolution.Absent,
                            "an unreadable file is never absent"));
        }
    }

    @Nested
    @DisplayName("the integrity states")
    class IntegrityStates {

        @Test
        @DisplayName("a changed byte is store_tampered under the lock and only unverified without it")
        void changedByte() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            create(lessonId);
            replaceByte(expectedPath(lessonId), (byte) '!');

            var locked = lockedGet(lessonId);
            var lockFree = store.get(lessonId);

            var tampered = assertInstanceOf(StoreTampered.class, locked,
                    "a changed byte is store_tampered under the lock");
            var found = assertInstanceOf(Found.class, lockFree, "a lock-free read is found, never a decision");
            assertAll(
                    "A changed byte is store_tampered under the lock",
                    () -> assertEquals(expectedPath(lessonId), tampered.path(), "the refusal names the file"),
                    () -> assertEquals(StoreDigest.ofFile(expectedPath(lessonId)).orElseThrow(),
                            tampered.current(), "the refusal carries the current digest"),
                    () -> assertEquals(StoreIntegrity.DIGEST_UNVERIFIED, found.integrity(),
                            "a lock-free read is unverified, never a decision"),
                    () -> assertFalse(lockFree instanceof StoreTampered,
                            "a lock-free read never decides an edit"));
        }

        @Test
        @DisplayName("a deleted file is deleted_out_of_band and never absent")
        void deletedOutOfBand() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            create(lessonId);
            Files.delete(expectedPath(lessonId));

            assertAll(
                    "A deleted file is deleted_out_of_band",
                    () -> assertEquals(LessonResolution.State.DELETED_OUT_OF_BAND, store.get(lessonId).state(),
                            "the lock-free read names the entry without its file"),
                    () -> assertEquals(LessonResolution.State.DELETED_OUT_OF_BAND, lockedGet(lessonId).state(),
                            "the locked read decides it"),
                    () -> assertInstanceOf(DeletedOutOfBand.class, lockedGet(lessonId),
                            "the state is deleted_out_of_band"));
        }

        @Test
        @DisplayName("a file without an entry is untracked under the lock and unchecked without it")
        void untracked() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            var lesson = new Lesson(LessonFixtures.frontMatterBuilder().lessonId(lessonId).build(),
                    LessonFixtures.body());
            Files.write(expectedPath(lessonId), LessonCodec.encode(lesson));

            var locked = lockedGet(lessonId);
            var lockFree = store.get(lessonId);

            assertAll(
                    "A file without an entry is untracked",
                    () -> assertInstanceOf(Untracked.class, locked, "the locked read names the file without an entry"),
                    () -> assertEquals(StoreIntegrity.UNCHECKED, ((Found) lockFree).integrity(),
                            "the lock-free read compared nothing"),
                    () -> assertEquals(expectedPath(lessonId), ((Untracked) locked).path(),
                            "the refusal names the file"));
        }
    }

    @Nested
    @DisplayName("listing")
    class Listing {

        @Test
        @DisplayName("names only the files whose name parses as a lesson id")
        void onlyLessonNames() throws Exception {
            create(new LessonId("2026-11-02-09-001"));
            Files.writeString(lessons.pathBase().resolve("notes.md"), "{}");
            Files.writeString(lessons.pathBase().resolve("2026-11-02-09-002.md.tmp.0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b"),
                    "{}");
            Files.writeString(lessons.pathBase().resolve("2026-11-02-09"), "{}");
            Files.createDirectories(lessons.pathBase().resolve("2026-11-02-09-003.md"));

            assertEquals(List.of("2026-11-02-09-001"),
                    store.list().stream().map(resolution -> resolution.lessonId().value()).toList(),
                    "only the parsable lesson name is listed");
        }

        @Test
        @DisplayName("of a store without a directory is empty")
        void absentDirectory() throws Exception {
            Files.delete(lessons.pathBase());

            assertEquals(List.of(), store.list(), "an absent store lists nothing");
        }

        @Test
        @DisplayName("lists the lessons of the sidecar whose file is gone, so an entry is never silent")
        void sidecarEntriesAreListed() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            create(lessonId);
            Files.delete(expectedPath(lessonId));

            assertEquals(List.of(LessonResolution.State.DELETED_OUT_OF_BAND),
                    store.list().stream().map(LessonResolution::state).toList(),
                    "the entry without its file is listed");
        }
    }

    @Nested
    @DisplayName("claims and candidates")
    class ClaimsAndCandidates {

        @Test
        @DisplayName("unclaimed lists active, unclaimed lessons newest first and carries the untrusted mark")
        void unclaimed() {
            create(new LessonId("2026-11-02-09-001"));
            inTransaction(write -> store.create(write, new LessonId("2026-11-02-09-002"),
                    LessonFixtures.newLesson().derivedFromUntrusted(true).build(), CLOCK));
            var claimed = create(new LessonId("2026-11-02-09-003"));
            run(write -> store.claim(write, claimed.frontMatter().lessonId(), plan, CLOCK.instant()));
            var retired = create(new LessonId("2026-11-02-09-004"));
            run(write -> store.retire(write, retired.frontMatter().lessonId(),
                    LessonFixtures.retirement(Verdict.OBSOLETE).build()));

            var candidates = store.unclaimed();

            assertAll(
                    "Unclaimed lists active unclaimed lessons newest first",
                    () -> assertEquals(List.of("2026-11-02-09-002", "2026-11-02-09-001"),
                            candidates.stream().map(found -> found.lessonId().value()).toList(),
                            "only active unclaimed lessons, newest first"),
                    () -> assertTrue(candidates.getFirst().lesson().frontMatter().derivedFromUntrusted(),
                            "the untrusted mark travels on the candidate"),
                    () -> assertEquals(Status.ACTIVE, candidates.getLast().lesson().frontMatter().status(),
                            "a candidate is active"));
        }

        @Test
        @DisplayName("unclaimed orders the ids of one hour by sequence, four digits after three")
        void unclaimedOrdersBySequence() {
            create(new LessonId("2026-11-02-09-999"));
            create(new LessonId("2026-11-02-09-1000"));

            var candidates = store.unclaimed();

            assertEquals(List.of("2026-11-02-09-1000", "2026-11-02-09-999"),
                    candidates.stream().map(found -> found.lessonId().value()).toList(),
                    "the four-digit sequence is the newer one");
        }

        @Test
        @DisplayName("unclaimed excludes a lesson whose file differs from its entry")
        void unclaimedExcludesTampered() throws Exception {
            var lessonId = new LessonId("2026-11-02-09-001");
            create(lessonId);
            replaceByte(expectedPath(lessonId), (byte) '!');

            assertAll(
                    "A tampered lesson is no candidate",
                    () -> assertEquals(List.of(), store.unclaimed(),
                            "a lesson with an unverified digest is no candidate"),
                    () -> assertEquals(LessonResolution.State.FOUND, store.get(lessonId).state(),
                            "the lesson is still listed as found, with an unverified digest"));
        }

        @Test
        @DisplayName("claims separates the claimed lessons from the unreadable ids")
        void claims() throws Exception {
            var claimed = create(new LessonId("2026-11-02-09-001"));
            run(write -> store.claim(write, claimed.frontMatter().lessonId(), plan, CLOCK.instant()));
            create(new LessonId("2026-11-02-09-002"));
            Files.writeString(expectedPath(new LessonId("2026-11-02-09-003")), "# No front matter\n");

            Claims claims = store.claims();

            assertAll(
                    "Claims separates claimed from unreadable",
                    () -> assertEquals(List.of(claimed.frontMatter().lessonId()), claims.claimed().stream()
                            .map(found -> found.lessonId()).toList(), "the claim is listed"),
                    () -> assertEquals(List.of(new LessonId("2026-11-02-09-003")),
                            List.copyOf(claims.unreadable()), "the unreadable lesson is listed by id"),
                    () -> assertEquals(List.of(claimed.frontMatter().lessonId()),
                            store.claimedBy(plan).stream().map(found -> found.lessonId()).toList(),
                            "claimedBy names the plan's claim"),
                    () -> assertEquals(List.of(), store.claimedBy(otherPlan), "another plan has none"));
        }
    }

    @Nested
    @DisplayName("property")
    class Property {

        @Test
        @DisplayName("no listed state of a generated store is reported absent")
        void noListedStateIsAbsent() throws Exception {
            for (var seed = 0; seed < 100; seed++) {
                resetStore();
                var ids = buildState(seed);
                var label = seed;

                var resolutions = store.list();

                assertAll(
                        "No listed state of a generated store is absent",
                        () -> assertFalse(resolutions.stream()
                                        .anyMatch(resolution -> resolution.state() == LessonResolution.State.ABSENT),
                                "no listed state is absent for seed " + label),
                        () -> assertEquals(ids.size(), resolutions.size(),
                                "every listed id of seed " + label + " resolves"));
            }
        }

        private void resetStore() throws IOException {
            try (var files = Files.list(lessons.pathBase())) {
                for (var file : files.toList()) {
                    Files.deleteIfExists(file);
                }
            }
            Files.deleteIfExists(lessons.sidecarFile());
        }

        /** Builds one generated store state and returns the ids it lists. */
        private List<LessonId> buildState(int seed) throws IOException {
            var shape = Generators.integers(0, 5).next() + seed % 6;
            var ids = new ArrayList<LessonId>();
            for (var index = 0; index < 3; index++) {
                var lessonId = new LessonId("2026-11-02-09-%03d".formatted(index + 1));
                ids.add(lessonId);
                switch ((shape + index) % 4) {
                    case 0 -> Files.write(expectedPath(lessonId), LessonCodec.encode(
                            new Lesson(LessonFixtures.frontMatterBuilder().lessonId(lessonId).build(),
                                    LessonFixtures.body())));
                    case 1 -> Files.writeString(expectedPath(lessonId), "# No front matter\n");
                    case 2 -> Files.write(expectedPath(lessonId), LessonCodec.encode(
                            new Lesson(LessonFixtures.frontMatterBuilder().lessonId(lessonId).build(), " \n")));
                    default -> {
                        Files.write(expectedPath(lessonId), LessonCodec.encode(
                                new Lesson(LessonFixtures.frontMatterBuilder().lessonId(lessonId).build(),
                                        LessonFixtures.body())));
                        replaceByte(expectedPath(lessonId), (byte) '?');
                    }
                }
            }
            var ghost = new LessonId("2026-11-02-09-004");
            Files.write(expectedPath(ghost), LessonCodec.encode(
                    new Lesson(LessonFixtures.frontMatterBuilder().lessonId(ghost).build(), LessonFixtures.body())));
            var ghostBytes = Files.readAllBytes(expectedPath(ghost));
            Files.delete(expectedPath(ghost));
            Files.write(lessons.sidecarFile(),
                    DigestSidecar.of(Map.of(ghost.fileName(), StoreDigest.of(ghostBytes))).write());
            ids.add(ghost);
            return ids;
        }
    }
}
