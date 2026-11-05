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

import static de.planmarshall.core.store.OutlineFixtures.deliverable;
import static de.planmarshall.core.store.OutlineFixtures.revision;
import static de.planmarshall.core.store.OutlineFixtures.valid;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.OutlineRecord.AddedDependency;
import de.planmarshall.core.store.OutlineRecord.AffectedFile;
import de.planmarshall.core.store.OutlineRecord.ChangeType;
import de.planmarshall.core.store.OutlineRecord.Deliverable;
import de.planmarshall.core.store.OutlineRecord.ExecutionMode;
import de.planmarshall.core.store.OutlineRecord.FileIntent;
import de.planmarshall.core.store.OutlineRecord.Interface;
import de.planmarshall.core.store.OutlineRecord.KeyLink;
import de.planmarshall.core.store.OutlineRecord.Profile;
import de.planmarshall.core.store.OutlineRecord.ScopeEstimate;
import de.planmarshall.core.store.OutlineRecord.Verification;

/**
 * The outline record and its store. The rendered {@code outline.md} headings and labels, the satisfies identifier
 * prefixes ({@code AC-}, {@code DC-}) and the store documents are contracts; titles, notes and texts are generated.
 */
@EnableGeneratorController
@DisplayName("The plan's outline record")
class OutlineRecordTest {

    // Fixture path: the name the codec reports a refused document under.
    private static final Path FILE = Path.of("outline.json");

    /** @return a text within the length grammar of a deliverable title, 3 to 120 characters */
    private static String text() {
        return text(3, 30);
    }

    /** @return a text of a length within the bounds of the member it fills */
    private static String text(int min, int max) {
        return Generators.letterStrings(min, max).next();
    }

    private static String criterion() {
        return "AC-" + Generators.integers(1, 99).next();
    }

    @DisplayName("Round-trips through submit:outline and renders outline.md from the record (rendered headings are wire)")
    @Test
    void roundTripsAndRenders() {
        var store = OutlineStore.first(revision(1, valid()));
        var reread = OutlineCodec.read(FILE, OutlineCodec.write(store));

        assertEquals(store, reread, "the outline store should round-trip");
        var outline = OutlineRenderer.render(reread);
        assertAll(
                () -> assertTrue(outline.startsWith("# Solution Outline"), "the outline should open with its title"),
                () -> assertTrue(outline.contains("## 1. Write the intent record"),
                        "the first deliverable should be rendered"),
                () -> assertTrue(outline.contains("## 2. Render the clarified request"),
                        "the second deliverable should be rendered"),
                () -> assertTrue(outline.contains("**Affected files:**"), "the affected files should be rendered"),
                () -> assertTrue(outline.contains("- `pm-core/src/main/java/1.java` (write-new)"),
                        "the affected file should name its intent"),
                () -> assertTrue(outline.contains("**Satisfies:** AC-1"), "the satisfies entry should be rendered"),
                () -> assertTrue(outline.contains("**Change per file:**"),
                        "the change per file should be rendered"),
                () -> assertTrue(outline.contains("**Verification:**"), "the verification should be rendered"),
                () -> assertTrue(outline.contains("**Added dependencies:**"),
                        "the added dependencies should be rendered"),
                () -> assertTrue(outline.contains("**Interfaces:**"), "the interfaces should be rendered"),
                () -> assertTrue(outline.contains("**Key links:**"), "the key links should be rendered"),
                () -> assertTrue(outline.contains("**Intent gloss:**"), "the intent gloss should be rendered"),
                () -> assertTrue(outline.contains("**Profiles:** implementation, module_testing"),
                        "the profiles should be rendered in order"));
    }

    @DisplayName("A revision moves the current record to history[] and keeps the status of every deliverable")
    @Test
    void revisionMovesCurrentToHistory() {
        var first = revision(1, valid());
        var store = OutlineStore.first(first);
        var second = revision(2, new OutlineRecord(valid().summary(), null, ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, text(), List.of(), criterion(), text()))));
        var after = store.accept(second);

        assertAll(
                () -> assertEquals(second, after.current(), "the new revision should be current"),
                () -> assertEquals(List.of(first), after.history(), "the old revision should move to history"),
                () -> assertEquals(OutlineStore.DeliveryStatus.ACTIVE, after.status(1).orElseThrow(),
                        "the first deliverable should stay active"),
                () -> assertEquals(OutlineStore.DeliveryStatus.ACTIVE, after.status(2).orElseThrow(),
                        "the second deliverable should stay active"),
                () -> assertEquals(after, OutlineCodec.read(FILE, OutlineCodec.write(after)),
                        "the revised store should round-trip"));
    }

    @DisplayName("A deferred deliverable keeps its state in the record")
    @Test
    void deliverableStatusIsServerOwned() {
        var store = OutlineStore.first(revision(1, valid()));
        assertTrue(store.active(2), "a new deliverable should be active");
        var statuses = new LinkedHashMap<>(store.deliverableStatus());
        statuses.put(2, OutlineStore.DeliveryStatus.DEFERRED);
        var deferred = new OutlineStore(store.current(), statuses, store.history());
        assertAll(
                () -> assertFalse(deferred.active(2), "a deferred deliverable should not be active"),
                () -> assertTrue(OutlineRenderer.render(deferred).contains("## 2. Render the clarified request"),
                        "a deferred deliverable should still render"));
    }

    @DisplayName("A dependency must name a deliverable of the outline, and never itself")
    @Test
    void refusesUnresolvedAndSelfDependency() {
        var summary = valid().summary();
        var unresolved = List.of(deliverable(1, text(), List.of(Generators.integers(2, 99).next()), criterion(),
                text()));
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, unresolved),
                "an unresolved dependency should be refused");
        var selfDependent = List.of(deliverable(1, text(), List.of(1), criterion(), text()));
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, selfDependent),
                "a self dependency should be refused");
    }

    @DisplayName("A write-conditional file names its gate, and no other file carries one")
    @Test
    void checksTheGateOfAFile() {
        var path = Generators.letterStrings(1, 20).next() + ".java";
        var gate = text();
        assertThrows(IllegalArgumentException.class,
                () -> new AffectedFile(path, FileIntent.WRITE_CONDITIONAL, null),
                "a conditional file without a gate should fail");
        assertThrows(IllegalArgumentException.class,
                () -> new AffectedFile(path, FileIntent.READ, gate),
                "a read file with a gate should be refused");
        assertEquals(gate, new AffectedFile(path, FileIntent.WRITE_CONDITIONAL, gate).gate(),
                "the conditional file should keep its gate");
    }

    @DisplayName("A satisfies entry is a criterion or a decision identifier (the NG- prefix is the foreign grammar)")
    @Test
    void refusesForeignSatisfiesEntry() {
        var foreign = "NG-" + Generators.integers(1, 99).next();
        assertThrows(IllegalArgumentException.class, () -> outlineSatisfying(foreign),
                "a foreign satisfies entry should be refused");
    }

    @DisplayName("A deliverable names at least one profile, once each")
    @Test
    void refusesMissingOrDuplicateProfiles() {
        List<Integer> noDepends = List.of();
        List<Profile> noProfiles = List.of();
        List<AffectedFile> noFiles = List.of();
        List<AddedDependency> noDependencies = List.of();
        List<Interface> noInterfaces = List.of();
        List<KeyLink> noLinks = List.of();
        // Lengths within the member bounds, so that only the profiles break the record.
        var verification = List.of(new Verification(text(5, 30), text(5, 30)));
        var satisfies = List.of(criterion());
        var title = text();
        var goal = text(5, 60);
        var domain = text().toLowerCase(Locale.ROOT);
        var module = text().toLowerCase(Locale.ROOT);
        var change = text(10, 60);
        assertThrows(IllegalArgumentException.class, () -> new Deliverable(1, title, goal,
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, domain, module, noDepends, null, noProfiles,
                noFiles, change, verification, satisfies, noDependencies, noInterfaces, noLinks),
                "a deliverable without a profile should fail");
        var duplicatedProfiles = List.of(Profile.IMPLEMENTATION, Profile.IMPLEMENTATION);
        assertThrows(IllegalArgumentException.class, () -> new Deliverable(1, title, goal,
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, domain, module, noDepends, null,
                duplicatedProfiles, noFiles, change,
                verification, satisfies, noDependencies, noInterfaces, noLinks),
                "a duplicated profile should be refused");
    }

    @DisplayName("A deliverable number is unique in the outline")
    @Test
    void refusesDuplicateDeliverableNumber() {
        var summary = valid().summary();
        var deliverables = List.of(deliverable(1, text(), List.of(), criterion(), text()),
                deliverable(1, text(), List.of(), criterion(), text()));
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, deliverables),
                "a duplicated deliverable number should fail");
    }

    @DisplayName("The transitive dependencies of a deliverable are every deliverable it rests on")
    @Test
    void computesTransitiveDependencies() {
        var decision = "DC-" + Generators.integers(1, 99).next();
        var outlineRecord = new OutlineRecord(valid().summary(), null, ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, text(), List.of(), criterion(), text()),
                        deliverable(2, text(), List.of(1), criterion(), text()),
                        deliverable(3, text(), List.of(2), decision, text())));
        assertAll(
                () -> assertEquals(List.of(2, 1), outlineRecord.transitiveDependencies(3),
                        "the dependencies should be transitive"),
                () -> assertTrue(outlineRecord.deliverable(3).orElseThrow().satisfies(decision),
                        "the deliverable should satisfy its decision"));
    }

    @DisplayName("A duplicate dependency is refused")
    @Test
    void refusesDuplicateDependency() {
        var summary = valid().summary();
        var duplicatedDependency = new Deliverable(2, text(), text(5, 60), ChangeType.FEATURE,
                ExecutionMode.AUTOMATED, text().toLowerCase(Locale.ROOT), text().toLowerCase(Locale.ROOT),
                List.of(1, 1), null, List.of(Profile.IMPLEMENTATION), List.of(), text(10, 60),
                List.of(new Verification(text(5, 30), text(5, 30))),
                List.of(criterion()), List.of(), List.of(), List.of());
        var deliverables = List.of(deliverable(1, text(), List.of(), criterion(), text()), duplicatedDependency);
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, deliverables),
                "a duplicated dependency should be refused");
    }

    @DisplayName("A store with a deliverable that carries no state is refused")
    @Test
    void refusesMissingDeliverableStatus() {
        var first = revision(1, valid());
        Map<Integer, OutlineStore.DeliveryStatus> statuses = Map.of();
        List<OutlineStore.Revision> history = List.of();
        assertThrows(IllegalArgumentException.class, () -> new OutlineStore(first, statuses, history),
                "a deliverable without a state should fail");
    }

    @DisplayName("A noncanonical deliverable-state key is refused (the key form is the wire form)")
    @Test
    void refusesNoncanonicalStatusKey() {
        var store = OutlineStore.first(revision(1, valid()));
        var document = new String(OutlineCodec.write(store), StandardCharsets.UTF_8)
                .replace("\"1\":", "\"01\":");
        var bytes = document.getBytes(StandardCharsets.UTF_8);
        assertThrows(StoreRefusal.class, () -> OutlineCodec.read(FILE, bytes),
                "a noncanonical state key should be refused");
    }

    @DisplayName("The reader refuses a document of another format version (wire document)")
    @Test
    void refusesOtherFormatVersion() {
        var document = "{\"format_version\": 2, \"current\": {}, \"deliverable_status\": {}, \"history\": []}"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(StoreRefusal.class, () -> OutlineCodec.read(FILE, document),
                "another format version should be refused");
    }

    private static OutlineRecord outlineSatisfying(String entry) {
        return new OutlineRecord(valid().summary(), null, ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, text(), List.of(), entry, text())));
    }
}
