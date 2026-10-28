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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
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

@DisplayName("The plan's outline record")
class OutlineRecordTest {

    private static final Path FILE = Path.of("outline.json");

    @DisplayName("Round-trips through submit:outline and renders outline.md from the record")
    @Test
    void roundTripsAndRenders() {
        var store = OutlineStore.first(revision(1, valid()));
        var reread = OutlineCodec.read(FILE, OutlineCodec.write(store));

        assertEquals(store, reread);
        var outline = OutlineRenderer.render(reread);
        assertTrue(outline.startsWith("# Solution Outline"));
        assertTrue(outline.contains("## 1. Write the intent record"));
        assertTrue(outline.contains("## 2. Render the clarified request"));
        assertTrue(outline.contains("**Affected files:**"));
        assertTrue(outline.contains("- `pm-core/src/main/java/1.java` (write-new)"));
        assertTrue(outline.contains("**Satisfies:** AC-1"));
        assertTrue(outline.contains("**Change per file:**"));
        assertTrue(outline.contains("**Verification:**"));
        assertTrue(outline.contains("**Added dependencies:**"));
        assertTrue(outline.contains("**Interfaces:**"));
        assertTrue(outline.contains("**Key links:**"));
        assertTrue(outline.contains("**Intent gloss:**"));
        assertTrue(outline.contains("**Profiles:** implementation, module_testing"));
    }

    @DisplayName("A revision moves the current record to history[] and keeps the status of every deliverable")
    @Test
    void revisionMovesCurrentToHistory() {
        var first = revision(1, valid());
        var store = OutlineStore.first(first);
        var second = revision(2, new OutlineRecord(valid().summary(), null, ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, "Write the intent record", List.of(), "AC-1", "the record"))));
        var after = store.accept(second);

        assertEquals(second, after.current());
        assertEquals(List.of(first), after.history());
        assertEquals(OutlineStore.DeliveryStatus.ACTIVE, after.status(1).orElseThrow());
        assertEquals(OutlineStore.DeliveryStatus.ACTIVE, after.status(2).orElseThrow());
        assertEquals(after, OutlineCodec.read(FILE, OutlineCodec.write(after)));
    }

    @DisplayName("A deferred deliverable keeps its state in the record")
    @Test
    void deliverableStatusIsServerOwned() {
        var store = OutlineStore.first(revision(1, valid()));
        assertTrue(store.active(2));
        var statuses = new LinkedHashMap<>(store.deliverableStatus());
        statuses.put(2, OutlineStore.DeliveryStatus.DEFERRED);
        var deferred = new OutlineStore(store.current(), statuses, store.history());
        assertFalse(deferred.active(2));
        assertTrue(OutlineRenderer.render(deferred).contains("## 2. Render the clarified request"));
    }

    @DisplayName("A dependency must name a deliverable of the outline, and never itself")
    @Test
    void refusesUnresolvedAndSelfDependency() {
        var summary = valid().summary();
        var unresolved = List.of(deliverable(1, "One", List.of(9), "AC-1", "one"));
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, unresolved));
        var selfDependent = List.of(deliverable(1, "One", List.of(1), "AC-1", "one"));
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, selfDependent));
    }

    @DisplayName("A write-conditional file names its gate, and no other file carries one")
    @Test
    void checksTheGateOfAFile() {
        assertThrows(IllegalArgumentException.class,
                () -> new AffectedFile("a.java", FileIntent.WRITE_CONDITIONAL, null));
        assertThrows(IllegalArgumentException.class,
                () -> new AffectedFile("a.java", FileIntent.READ, "a gate"));
        assertEquals("a gate",
                new AffectedFile("a.java", FileIntent.WRITE_CONDITIONAL, "a gate").gate());
    }

    @DisplayName("A satisfies entry is a criterion or a decision identifier")
    @Test
    void refusesForeignSatisfiesEntry() {
        assertThrows(IllegalArgumentException.class, () -> outlineSatisfying("NG-1"));
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
        var verification = List.of(new Verification("mvn verify", "green"));
        var satisfies = List.of("AC-1");
        assertThrows(IllegalArgumentException.class, () -> new Deliverable(1, "One", "The deliverable one",
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, "core", "pm-core", noDepends, null, noProfiles,
                noFiles, "Create the class", verification, satisfies, noDependencies, noInterfaces, noLinks));
        var duplicatedProfiles = List.of(Profile.IMPLEMENTATION, Profile.IMPLEMENTATION);
        assertThrows(IllegalArgumentException.class, () -> new Deliverable(1, "One", "The deliverable one",
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, "core", "pm-core", noDepends, null,
                duplicatedProfiles, noFiles, "Create the class",
                verification, satisfies, noDependencies, noInterfaces, noLinks));
    }

    @DisplayName("A deliverable number is unique in the outline")
    @Test
    void refusesDuplicateDeliverableNumber() {
        var summary = valid().summary();
        var deliverables = List.of(deliverable(1, "One", List.of(), "AC-1", "one"),
                deliverable(1, "Two", List.of(), "AC-2", "two"));
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, deliverables));
    }

    @DisplayName("The transitive dependencies of a deliverable are every deliverable it rests on")
    @Test
    void computesTransitiveDependencies() {
        var outlineRecord = new OutlineRecord(valid().summary(), null, ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, "One", List.of(), "AC-1", "one"),
                        deliverable(2, "Two", List.of(1), "AC-2", "two"),
                        deliverable(3, "Three", List.of(2), "DC-1", "three")));
        assertEquals(List.of(2, 1), outlineRecord.transitiveDependencies(3));
        assertTrue(outlineRecord.deliverable(3).orElseThrow().satisfies("DC-1"));
    }

    @DisplayName("A duplicate dependency is refused")
    @Test
    void refusesDuplicateDependency() {
        var summary = valid().summary();
        var duplicatedDependency = new Deliverable(2, "Two", "The deliverable two", ChangeType.FEATURE,
                ExecutionMode.AUTOMATED, "core", "pm-core", List.of(1, 1), null,
                List.of(Profile.IMPLEMENTATION), List.of(), "Change the file of the deliverable",
                List.of(new Verification("mvn verify", "the build is green")),
                List.of("AC-2"), List.of(), List.of(), List.of());
        var deliverables = List.of(deliverable(1, "One", List.of(), "AC-1", "one"), duplicatedDependency);
        assertThrows(IllegalArgumentException.class,
                () -> new OutlineRecord(summary, null, ScopeEstimate.SINGLE_MODULE, deliverables));
    }

    @DisplayName("A store with a deliverable that carries no state is refused")
    @Test
    void refusesMissingDeliverableStatus() {
        var first = revision(1, valid());
        Map<Integer, OutlineStore.DeliveryStatus> statuses = Map.of();
        List<OutlineStore.Revision> history = List.of();
        assertThrows(IllegalArgumentException.class, () -> new OutlineStore(first, statuses, history));
    }

    @DisplayName("The reader refuses a document of another format version")
    @Test
    void refusesOtherFormatVersion() {
        var document = "{\"format_version\": 2, \"current\": {}, \"deliverable_status\": {}, \"history\": []}"
                .getBytes(StandardCharsets.UTF_8);
        assertThrows(StoreRefusal.class, () -> OutlineCodec.read(FILE, document));
    }

    private static OutlineRecord outlineSatisfying(String entry) {
        return new OutlineRecord(valid().summary(), null, ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, "One", List.of(), entry, "one")));
    }
}
