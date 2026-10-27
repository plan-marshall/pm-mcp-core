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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.CapabilitySpec.Requirement;
import de.planmarshall.core.store.CapabilitySpec.Scenario;

@DisplayName("The capability spec and its index")
class CapabilitySpecTest {

    @DisplayName("A requirement's fingerprint ignores plan_id and verified_by")
    @Test
    void fingerprintIgnoresPlanAndTests() {
        var first = requirement("round-trip", "plan-a", List.of("TestA.java"));
        var second = requirement("round-trip", "plan-b", List.of("TestB.java"));

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length());
    }

    @DisplayName("A requirement's fingerprint changes with its text, scenarios and name")
    @Test
    void fingerprintFollowsTheRequirement() {
        var base = requirement("round-trip", "plan-a", List.of());
        var otherText = new Requirement(base.name(), base.text() + " and verify the rendering", base.scenarios(),
                base.planId(), base.verifiedBy());
        var otherName = new Requirement("other-name", base.text(), base.scenarios(), base.planId(),
                base.verifiedBy());

        assertNotEquals(base.fingerprint(), otherText.fingerprint());
        assertNotEquals(base.fingerprint(), otherName.fingerprint());
    }

    @DisplayName("The index reads the capability specs of a tree and resolves their fingerprints")
    @Test
    void indexReadsTheCapabilities(@TempDir Path root) throws Exception {
        write(root, "intent-record", "The plan's intent record");
        var index = CapabilityIndex.read(root);

        assertTrue(index.capability("intent-record").isPresent());
        assertTrue(index.fingerprint("intent-record", "round-trip").isPresent());
        assertEquals(Optional.empty(), index.fingerprint("intent-record", "missing"));
        assertEquals(Optional.empty(), index.fingerprint("missing", "round-trip"));
        assertEquals(1, index.facts().size());
        assertEquals(List.of("round-trip"), index.facts().getFirst().get("requirements"));
    }

    @DisplayName("The index of a tree without capability specs is empty")
    @Test
    void indexOfATreeWithoutCapabilities(@TempDir Path root) {
        var index = CapabilityIndex.read(root);
        assertTrue(index.capabilities().isEmpty());
        assertEquals(List.of(), index.facts());
    }

    @DisplayName("A spec whose file name is not its capability is refused")
    @Test
    void refusesMismatchedFileName(@TempDir Path root) throws Exception {
        write(root, "intent-record", "The plan's intent record");
        var file = root.resolve(".marshall/capabilities/intent-record.json");
        var other = file.resolveSibling("other.json");
        Files.move(file, other);

        assertThrows(StoreRefusal.class, () -> CapabilityIndex.read(root));
    }

    @DisplayName("The acceptance checks a capability change against the index")
    @Test
    void acceptanceChecksAgainstTheIndex(@TempDir Path root) throws Exception {
        write(root, "intent-record", "The plan's intent record");
        var index = CapabilityIndex.read(root);
        var fingerprint = index.fingerprint("intent-record", "round-trip").orElseThrow();

        var added = withCapability(new IntentRecord.CapabilityLink("intent-record", "round-trip",
                IntentRecord.CapabilityChange.ADDED, null));
        var refused = IntentAcceptance.accept(null, IntentSubmission.REFINE, revision(added), context(root, index));
        assertTrue(((IntentAcceptance.Refused) refused).detail().contains("already exists"));

        var modified = withCapability(new IntentRecord.CapabilityLink("intent-record", "round-trip",
                IntentRecord.CapabilityChange.MODIFIED, fingerprint));
        assertInstanceOf(IntentAcceptance.Accepted.class, IntentAcceptance.accept(null, IntentSubmission.REFINE, revision(modified),
                context(root, index)));

        var stale = withCapability(new IntentRecord.CapabilityLink("intent-record", "round-trip",
                IntentRecord.CapabilityChange.MODIFIED, "b".repeat(64)));
        assertTrue(((IntentAcceptance.Refused) IntentAcceptance.accept(null, IntentSubmission.REFINE,
                revision(stale), context(root, index))).detail().contains("tree holds"));
    }

    @DisplayName("A spec round-trips through its codec")
    @Test
    void specRoundTrips(@TempDir Path root) {
        var spec = new CapabilitySpec("intent-record", "The plan's intent record and its revisions",
                List.of(requirement("round-trip", "plan-a", List.of("TestA.java"))));
        var reread = CapabilitySpec.read(Path.of("intent-record.json"), CapabilitySpec.write(spec));
        assertEquals(spec, reread);
    }

    @DisplayName("A requirement's text is in EARS form")
    @Test
    void requirementTextIsInEarsForm() {
        assertThrows(IllegalArgumentException.class,
                () -> new Requirement("round-trip", "the record round-trips", List.of(scenario()), "plan-a",
                        List.of()));
    }

    @DisplayName("The canonical JSON of a requirement sorts its object keys")
    @Test
    void canonicalJsonSortsKeys() {
        var canonical = CapabilitySpec.canonical(Map.of("text", "a", "name", "b"));
        assertEquals("{\"name\":\"b\",\"text\":\"a\"}", canonical);
        assertEquals("{}", CapabilitySpec.canonical(Map.of()));
    }

    private static void write(Path root, String capability, String purpose) throws IOException {
        var spec = new CapabilitySpec(capability, purpose, List.of(requirement("round-trip", "plan-a", List.of())));
        var directory = root.resolve(".marshall/capabilities");
        Files.createDirectories(directory);
        Files.write(directory.resolve(capability + ".json"), CapabilitySpec.write(spec));
    }

    private static Requirement requirement(String name, String planId, List<String> verifiedBy) {
        return new Requirement(name, "WHEN a plan is refined THE system SHALL round-trip its intent record",
                List.of(scenario()), planId, verifiedBy);
    }

    private static Scenario scenario() {
        return new Scenario("A refined plan", null, "a plan is refined", "the record round-trips");
    }

    private static IntentRecord withCapability(IntentRecord.CapabilityLink link) {
        var base = IntentFixtures.valid();
        var criterion = new IntentRecord.Criterion("AC-1",
                "WHEN a plan is refined THE system SHALL round-trip its intent record",
                IntentRecord.CriterionKind.NEW, IntentRecord.VerifiedBy.TEST, link);
        return new IntentRecord(base.why(), base.goal(), List.of(criterion, base.criteria().get(1)),
                base.currentBehaviour(), base.nonGoals(), base.constraints(), base.decisions(), base.assumptions(),
                base.openQuestions(), base.deferred(), base.sourceClaims());
    }

    private static IntentStore.Revision revision(IntentRecord record) {
        return new IntentStore.Revision(1, IntentFixtures.AT, "TASK-001", record);
    }

    private static IntentAcceptance.Context context(Path root, CapabilityIndex index) {
        return new IntentAcceptance.Context(IntentFixtures.PLAN_ID, question -> Optional.empty(), index,
                Optional.empty(), root);
    }
}
