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
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * The operator's projects {@code explorer.json} (Store Schemas § The Operator's Projects): the favourites in the
 * operator's order, the acts per UTC day within the window, the revision that refuses a stale change, and the cleanup
 * of a project that is no longer enrolled.
 */
@EnableGeneratorController
@DisplayName("Operator's projects store")
class ExplorerStoreTest {

    private static final Instant NOW = Instant.parse("2026-11-18T01:00:00Z");

    @TempDir
    Path base;

    private RecordingLockManager locks;
    private ExplorerStore store;

    @BeforeEach
    void createStore() {
        locks = new RecordingLockManager();
        store = new ExplorerStore(ExplorerStore.file(base), locks, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Should read an absent document as no favourites and no activity")
    void readsAnAbsentDocumentAsEmpty() {
        var snapshot = store.read();

        assertAll(
                () -> assertEquals(0L, snapshot.revision(), "An absent document holds revision zero"),
                () -> assertTrue(snapshot.favourites().isEmpty(), "An absent document holds no favourite"),
                () -> assertTrue(snapshot.activity().isEmpty(), "An absent document holds no activity"));
    }

    @Test
    @DisplayName("Should keep the operator's order of the favourites")
    void keepsTheOrderOfTheFavourites() {
        var first = id();
        var second = id();
        var third = id();

        store.setFavourites(List.of(second, first, third), Set.of(first, second, third), 0);

        var snapshot = store.read();
        assertAll(
                () -> assertEquals(List.of(second, first, third), snapshot.favourites(),
                        "The favourites keep the operator's order"),
                () -> assertEquals(1L, snapshot.revision(), "A write increments the revision"));
    }

    @Test
    @DisplayName("Should move and remove a favourite in one ordered list")
    void movesAndRemovesAFavourite() {
        var first = id();
        var second = id();
        store.setFavourites(List.of(first, second), Set.of(first, second), 0);

        store.setFavourites(List.of(second), Set.of(first, second), 1);

        assertEquals(List.of(second), store.read().favourites(), "The moved favourite is alone, in order");
    }

    @Test
    @DisplayName("Should drop a favourite of a project that is no longer enrolled")
    void dropsAFavouriteOfAnUnenrolledProject() {
        var enrolled = id();
        var unenrolled = id();

        store.setFavourites(List.of(unenrolled, enrolled), Set.of(enrolled), 0);

        assertEquals(List.of(enrolled), store.read().favourites(), "Only an enrolled project stays a favourite");
    }

    @Test
    @DisplayName("Should refuse a change against an older revision")
    void refusesAStaleRevision() {
        var project = id();
        store.setFavourites(List.of(project), Set.of(project), 0);

        var refusal = assertThrows(StoreRefusal.class,
                () -> store.setFavourites(List.of(project), Set.of(project), 0),
                "A change against the read revision is refused");

        assertEquals(StoreRefusal.Outcome.CONFLICT, refusal.outcome(), "The refusal is a conflict");
    }

    @Test
    @DisplayName("Should record the act on its UTC day and count the day's acts")
    void recordsTheActOnItsUtcDay() {
        var project = id();
        var day = LocalDate.ofInstant(NOW, ZoneOffset.UTC).toString();

        store.recordActivity(project, Set.of(project), 0);
        store.recordActivity(project, Set.of(project), 1);

        var activity = store.read().activity().get(project);
        assertAll(
                () -> assertEquals(NOW, activity.lastAt(), "The last act keeps the store clock's instant"),
                () -> assertEquals(Map.of(day, 2), activity.days(), "Both acts count on the same UTC day"),
                () -> assertEquals(2, activity.actsInWindow(), "The window sums the day's acts"));
    }

    @Test
    @DisplayName("Should drop the days older than the compiled window on the next write")
    void dropsDaysOlderThanTheWindow() {
        var project = id();
        store.recordActivity(project, Set.of(project), 0);

        var laterInstant = NOW.plus(ExplorerStore.ACTIVITY_WINDOW_DAYS, DAYS);
        var later = new ExplorerStore(ExplorerStore.file(base), locks, Clock.fixed(laterInstant, ZoneOffset.UTC));
        later.recordActivity(project, Set.of(project), 1);

        var activity = later.read().activity().get(project);
        var today = LocalDate.ofInstant(laterInstant, ZoneOffset.UTC).toString();
        assertEquals(Map.of(today, 1), activity.days(), "The window keeps only the act inside it");
    }

    @Test
    @DisplayName("Should drop the activity of a project that is no longer enrolled")
    void dropsTheActivityOfAnUnenrolledProject() {
        var gone = id();
        var enrolled = id();
        store.recordActivity(gone, Set.of(gone, enrolled), 0);

        store.recordActivity(enrolled, Set.of(enrolled), 1);

        var activity = store.read().activity();
        assertAll(
                () -> assertEquals(Set.of(enrolled), activity.keySet(),
                        "Only the enrolled project keeps its activity"),
                () -> assertEquals(2L, store.read().revision(), "Both writes incremented the revision"));
    }

    @Test
    @DisplayName("Should refuse an act of a project that is not enrolled")
    void refusesAnActOfAnUnenrolledProject() {
        var project = id();

        assertThrows(IllegalArgumentException.class, () -> store.recordActivity(project, Set.of(), 0),
                "An act is recorded for an enrolled project");
    }

    @Test
    @DisplayName("Should refuse a duplicate favourite and a project id outside the grammar")
    void refusesWhatIsNoFavourite() {
        var project = id();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> store.setFavourites(List.of(project, project), Set.of(project), 0),
                        "A favourite names a project once"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> store.setFavourites(List.of("No-Project"), Set.of("No-Project"), 0),
                        "A favourite is a valid project id"));
    }

    @Test
    @DisplayName("Should name the most used and the most recent projects in their order")
    void namesMostUsedAndRecent() {
        var often = id();
        var recently = id();
        var snapshot = new ExplorerStore.Snapshot(List.of(), Map.of(
                often, new ExplorerStore.Activity(NOW, Map.of("2026-11-17", 3)),
                recently, new ExplorerStore.Activity(NOW.plusSeconds(60), Map.of("2026-11-18", 1))), 0);

        assertAll(
                () -> assertEquals(List.of(often, recently), snapshot.mostUsed(2),
                        "The most used project comes first"),
                () -> assertEquals(List.of(recently, often), snapshot.recent(2),
                        "The most recent project comes first"));
    }

    @Test
    @DisplayName("Should refuse a document with a member the schema does not name")
    void refusesAnUndeclaredMember() throws IOException {
        Files.writeString(base.resolve("explorer.json"), """
                {"format_version": 1, "revision": 0, "favourites": [], "activity": {}, "extra": true}
                """, UTF_8);

        var refusal = assertThrows(StoreRefusal.class, () -> store.read(),
                "An undeclared member is refused as an unreadable store");

        assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(), "The refusal is unreadable");
    }

    private static String id() {
        return "p" + Generators.letterStrings(5, 10).next().toLowerCase(Locale.ROOT);
    }
}
