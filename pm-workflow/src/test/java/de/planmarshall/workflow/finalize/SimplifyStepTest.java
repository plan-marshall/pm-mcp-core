/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.finalize.CloneDetector.SourceFile;
import de.planmarshall.workflow.finalize.ProposalRecord.Disposition;

@EnableGeneratorController
@DisplayName("The review-simplify restraint ladder (finalize.simplify)")
class SimplifyStepTest {

    @DisplayName("A run of 49 tokens is no candidate and 50 is")
    @Test
    void cloneCandidateThreshold() {
        var tree = List.of(new SourceFile("old/Library.java", code(120), List.of()));

        var shortRun = CloneDetector.detect(List.of(new SourceFile("new/Copy.java", code(49), List.of())), tree);
        assertTrue(shortRun.isEmpty(), "a run of 49 tokens is no clone candidate");

        var longRun = CloneDetector.detect(List.of(new SourceFile("new/Copy.java", code(50), List.of())), tree);
        assertEquals(1, longRun.size(), "a run of 50 tokens is one clone candidate");
        assertEquals(50, longRun.getFirst().tokens(), "the candidate reports its 50 tokens");
        assertEquals("new/Copy.java", longRun.getFirst().addedPath(), "the candidate names the added file");
        assertEquals("old/Library.java", longRun.getFirst().existingPath(), "the candidate names the existing file");
    }

    @DisplayName("At most 20 candidates are named, the longest first")
    @Test
    void cloneCandidatesAreLongestFirstAndBounded() {
        var tree = List.of(new SourceFile("old/Library.java", code(400), List.of()));
        var changed = new ArrayList<SourceFile>();
        for (var index = 0; index < 25; index++) {
            changed.add(new SourceFile("new/Copy" + index + ".java", code(50 + index), List.of()));
        }

        var candidates = CloneDetector.detect(changed, tree);

        assertEquals(20, candidates.size(), "at most twenty candidates are named");
        assertEquals(74, candidates.getFirst().tokens(), "the longest candidate is named first");
        assertEquals(55, candidates.getLast().tokens(), "the shortest candidate is named last");
        for (var position = 1; position < candidates.size(); position++) {
            assertTrue(candidates.get(position - 1).tokens() >= candidates.get(position).tokens(),
                    "candidates are ordered longest first");
        }
    }

    @DisplayName("Two added hunks separated by unchanged code are never joined into one run")
    @Test
    void addedRangesAreSeparateRuns() {
        var tree = List.of(new SourceFile("old/Library.java", code(300), List.of()));
        var text = code(30) + "\n" + code(40) + "\n" + code(30);
        var changed = List.of(new SourceFile("new/Copy.java", text,
                List.of(new LineRange(1, 1), new LineRange(3, 3))));

        assertTrue(CloneDetector.detect(changed, tree).isEmpty(), "two separated added runs are no candidate");
    }

    @DisplayName("A text block before the added lines keeps the reported line range right")
    @Test
    void textBlockKeepsTheLineNumbers() {
        var tree = List.of(new SourceFile("old/Library.java", code(200), List.of()));
        var text = "String text = \"\"\"\n    body\n    \"\"\";\n" + code(60);
        var candidates = CloneDetector.detect(
                List.of(new SourceFile("new/Copy.java", text, List.of(new LineRange(4, 4)))), tree);

        assertEquals(1, candidates.size(), "the text block leaves one candidate");
        assertEquals(4, candidates.getFirst().addedRange().first(), "the added range starts on line four");
        assertEquals(4, candidates.getFirst().addedRange().last(), "the added range ends on line four");
    }

    @DisplayName("A run that only repeats itself is no candidate")
    @Test
    void selfRepeatIsNoCandidate() {
        var candidates = CloneDetector.detect(List.of(new SourceFile("new/Copy.java", code(120), List.of())),
                List.of(new SourceFile("old/Other.java", "int other = 1;", List.of())));
        assertTrue(candidates.isEmpty(), "a self-repeating run is no candidate");
    }

    @DisplayName("A candidate alone is never a finding and the review task carries no write tool")
    @Test
    void candidateIsNoFinding() {
        var candidate = new CloneCandidate("new/Copy.java", new LineRange(1, 10), "old/Library.java",
                new LineRange(2, 11), 50);

        assertTrue(SimplifyStep.descriptor().filesFindings().isEmpty(), "the simplify step files no finding");
        assertFalse(SimplifyStep.reviewTask().carriesWriteTool(), "the review task carries no write tool");
        assertFalse(SimplifyStep.reviewTask().takesWriterSlot(), "the review task takes no writer slot");
        assertEquals(SimplifyStep.REVIEW_ROLE, SimplifyStep.reviewTask().role(), "the task carries the review role");
        assertEquals(SimplifyStep.SKILL, SimplifyStep.reviewTask().skill(), "the task carries the simplify skill");
        assertEquals(List.of(candidate), SimplifyStep.reviewFacts(List.of(candidate)).get("clone_candidates"),
                "the review facts carry the candidates");
        assertFalse(SimplifyStep.reviewFacts(List.of(candidate)).containsKey("findings"),
                "the review facts carry no findings");
    }

    @DisplayName("The review is read-only at order 5 and the step files no finding")
    @Test
    void descriptorIsReadOnlyInTheSettleBand() {
        var descriptor = SimplifyStep.descriptor();

        assertEquals(SimplifyStep.NAME, descriptor.name(), "the descriptor carries the step name");
        assertEquals(FinalizeStepDescriptor.SIMPLIFY_ORDER, descriptor.order(), "the declared simplify order applies");
        assertEquals(5, descriptor.order(), "the simplify step sits at order five");
        assertFalse(descriptor.mutatesSource(), "the simplify step mutates no source");
        assertEquals(SimplifyStep.REVIEW_ROLE, descriptor.persona(), "the review runs under its own role");
        var lane = Objects.requireNonNull(descriptor.lane(), "lane");
        assertEquals("land_lane", lane.prunableWhen(), "the land lane prunes the step");
        assertNull(lane.tier(), "the lane names no tier");
        assertNull(descriptor.cannotReach(), "the step names no unreachable defect");
        assertTrue(descriptor.filesFindings().isEmpty(), "the descriptor files no finding");
        assertTrue(FinalizePipelineValidator.validate(List.of(descriptor)).isEmpty(),
                "the descriptor passes every pipeline rule");
    }

    @DisplayName("Proposals are recorded as SP-<n> records, open, and never reused")
    @Test
    void proposalsAreRecordedOpen() {
        var first = SimplifyStep.recordProposals(List.of(proposal(), proposal()), 1);
        var second = SimplifyStep.recordProposals(List.of(proposal()), 3);

        assertEquals(List.of("SP-1", "SP-2"), first.stream().map(ProposalRecord::proposalId).toList(),
                "the proposals are numbered in order");
        assertEquals("SP-3", second.getFirst().proposalId(), "a new firing starts at SP-3");
        assertTrue(first.stream().allMatch(recorded -> recorded.disposition() == Disposition.OPEN),
                "every proposal is recorded open");
        assertEquals(List.of(first.getFirst(), first.get(1)),
                SimplifyStep.applyFacts(first).get("proposals"), "the apply facts carry both proposals");
    }

    @DisplayName("The apply task applies the accepted subset and declines every other proposal")
    @Test
    void applyAcceptsTheSubsetAndDeclinesTheRest() {
        var records = SimplifyStep.recordProposals(List.of(proposal(), proposal(), proposal()), 1);

        var result = SimplifyStep.apply(records, List.of("SP-2"));

        assertTrue(result.applied(), "the accepted subset is applied");
        assertEquals(SimplifyStep.SETTLE_STATE, result.settleState(), "the step settles into the settle state");
        assertEquals(Disposition.DECLINED, result.records().get(0).disposition(), "the unaccepted SP-1 is declined");
        assertEquals(Disposition.ACCEPTED, result.records().get(1).disposition(), "SP-2 is the accepted proposal");
        assertEquals(Disposition.DECLINED, result.records().get(2).disposition(), "SP-3 is declined as unaccepted");
    }

    @DisplayName("An empty accepted set declines every proposal and routes nowhere")
    @Test
    void emptyAcceptedSetDeclinesEverything() {
        var records = SimplifyStep.recordProposals(List.of(proposal(), proposal()), 1);
        var result = SimplifyStep.apply(records, List.of());

        assertFalse(result.applied(), "an empty accepted set applies nothing");
        assertTrue(result.records().stream().allMatch(recorded -> recorded.disposition() == Disposition.DECLINED),
                "every proposal is declined");
    }

    @DisplayName("An accepted id that is no open proposal of the firing is refused")
    @Test
    void unknownAcceptedIdIsRefused() {
        var records = SimplifyStep.recordProposals(List.of(proposal()), 1);
        var unknownId = List.of("SP-9");
        assertThrows(IllegalArgumentException.class, () -> SimplifyStep.apply(records, unknownId),
                "an unknown accepted id is refused");
        var applied = SimplifyStep.apply(records, List.of("SP-1"));
        var appliedRecords = applied.records();
        var reusedId = List.of("SP-1");
        assertThrows(IllegalArgumentException.class, () -> SimplifyStep.apply(appliedRecords, reusedId),
                "a reused proposal id is refused");
    }

    @DisplayName("A proposal's file is repository-relative and its text is bounded")
    @Test
    void proposalIsBounded() {
        var text = Generators.letterStrings(1, 20).next();
        var rationale = Generators.letterStrings(1, 20).next();
        assertThrows(IllegalArgumentException.class,
                () -> new SimplifyProposal("/absolute/Path.java", text, rationale), "an absolute path is refused");
        assertThrows(IllegalArgumentException.class, () -> new SimplifyProposal("../outside.java", text, rationale),
                "a path outside the repository is refused");
        assertThrows(IllegalArgumentException.class, () -> new SimplifyProposal("a.java", "", rationale),
                "an empty proposal text is refused");
        var name = Generators.letterStrings(1, 10).next();
        assertEquals("pm-core/src/main/java/%s.java".formatted(name), proposal(name).file(),
                "a proposal names a repository-relative file");
    }

    @DisplayName("A candidate shorter than 50 tokens is refused")
    @Test
    void candidateIsAtLeast50Tokens() {
        var range = new LineRange(1, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new CloneCandidate("a.java", range, "b.java", range, 49), "a run under 50 tokens is refused");
    }

    @DisplayName("A line range is one-based and inclusive")
    @Test
    void lineRangeIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new LineRange(0, 1), "a line range starts at one");
        assertThrows(IllegalArgumentException.class, () -> new LineRange(2, 1), "a range ends before its start");
        assertEquals(3, new LineRange(1, 3).last(), "the line range is inclusive of its last");
    }

    private static SimplifyProposal proposal() {
        return proposal(Generators.letterStrings(1, 10).next());
    }

    private static SimplifyProposal proposal(String name) {
        return new SimplifyProposal("pm-core/src/main/java/%s.java".formatted(name),
                Generators.letterStrings(10, 40).next(), Generators.letterStrings(5, 30).next());
    }

    /** A source text of exactly {@code tokens} normalized tokens. */
    private static String code(int tokens) {
        var text = new StringBuilder();
        for (var index = 0; index < tokens; index++) {
            text.append(index % 2 == 0 ? "value" : ";").append(' ');
        }
        return text.toString();
    }
}
