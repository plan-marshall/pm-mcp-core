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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.finalize.CloneDetector.SourceFile;
import de.planmarshall.workflow.finalize.ProposalRecord.Disposition;

@DisplayName("The review-simplify restraint ladder (finalize.simplify)")
class SimplifyStepTest {

    @DisplayName("A run of 49 tokens is no candidate and 50 is")
    @Test
    void cloneCandidateThreshold() {
        var tree = List.of(new SourceFile("old/Library.java", code(120), List.of()));

        var shortRun = CloneDetector.detect(List.of(new SourceFile("new/Copy.java", code(49), List.of())), tree);
        assertTrue(shortRun.isEmpty());

        var longRun = CloneDetector.detect(List.of(new SourceFile("new/Copy.java", code(50), List.of())), tree);
        assertEquals(1, longRun.size());
        assertEquals(50, longRun.getFirst().tokens());
        assertEquals("new/Copy.java", longRun.getFirst().addedPath());
        assertEquals("old/Library.java", longRun.getFirst().existingPath());
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

        assertEquals(20, candidates.size());
        assertEquals(74, candidates.getFirst().tokens());
        assertEquals(55, candidates.getLast().tokens());
        for (var position = 1; position < candidates.size(); position++) {
            assertTrue(candidates.get(position - 1).tokens() >= candidates.get(position).tokens());
        }
    }

    @DisplayName("A run that only repeats itself is no candidate")
    @Test
    void selfRepeatIsNoCandidate() {
        var candidates = CloneDetector.detect(List.of(new SourceFile("new/Copy.java", code(120), List.of())),
                List.of(new SourceFile("old/Other.java", "int other = 1;", List.of())));
        assertTrue(candidates.isEmpty());
    }

    @DisplayName("A candidate alone is never a finding and the review task carries no write tool")
    @Test
    void candidateIsNoFinding() {
        var candidate = new CloneCandidate("new/Copy.java", new LineRange(1, 10), "old/Library.java",
                new LineRange(2, 11), 50);

        assertTrue(SimplifyStep.descriptor().filesFindings().isEmpty());
        assertFalse(SimplifyStep.reviewTask().carriesWriteTool());
        assertFalse(SimplifyStep.reviewTask().takesWriterSlot());
        assertEquals(SimplifyStep.REVIEW_ROLE, SimplifyStep.reviewTask().role());
        assertEquals(SimplifyStep.SKILL, SimplifyStep.reviewTask().skill());
        assertEquals(List.of(candidate), SimplifyStep.reviewFacts(List.of(candidate)).get("clone_candidates"));
        assertFalse(SimplifyStep.reviewFacts(List.of(candidate)).containsKey("findings"));
    }

    @DisplayName("The review is read-only at order 5 and the step files no finding")
    @Test
    void descriptorIsReadOnlyInTheSettleBand() {
        var descriptor = SimplifyStep.descriptor();

        assertEquals(SimplifyStep.NAME, descriptor.name());
        assertEquals(FinalizeStepDescriptor.SIMPLIFY_ORDER, descriptor.order());
        assertEquals(5, descriptor.order());
        assertFalse(descriptor.mutatesSource());
        assertEquals(Optional.of(SimplifyStep.REVIEW_ROLE), descriptor.persona());
        assertEquals(Optional.of("land_lane"), descriptor.lane().orElseThrow().prunableWhen());
        assertTrue(descriptor.filesFindings().isEmpty());
        assertTrue(FinalizePipelineValidator.validate(List.of(descriptor)).isEmpty());
    }

    @DisplayName("Proposals are recorded as SP-<n> records, open, and never reused")
    @Test
    void proposalsAreRecordedOpen() {
        var first = SimplifyStep.record(List.of(proposal("one"), proposal("two")), 1);
        var second = SimplifyStep.record(List.of(proposal("three")), 3);

        assertEquals(List.of("SP-1", "SP-2"), first.stream().map(ProposalRecord::proposalId).toList());
        assertEquals("SP-3", second.getFirst().proposalId());
        assertTrue(first.stream().allMatch(record -> record.disposition() == Disposition.OPEN));
        assertEquals(List.of(first.getFirst(), first.get(1)),
                SimplifyStep.applyFacts(first).get("proposals"));
    }

    @DisplayName("The apply task applies the accepted subset and declines every other proposal")
    @Test
    void applyAcceptsTheSubsetAndDeclinesTheRest() {
        var records = SimplifyStep.record(List.of(proposal("one"), proposal("two"), proposal("three")), 1);

        var result = SimplifyStep.apply(records, List.of("SP-2"));

        assertTrue(result.applied());
        assertEquals(SimplifyStep.SETTLE_STATE, result.settleState());
        assertEquals(Disposition.DECLINED, result.records().get(0).disposition());
        assertEquals(Disposition.ACCEPTED, result.records().get(1).disposition());
        assertEquals(Disposition.DECLINED, result.records().get(2).disposition());
    }

    @DisplayName("An empty accepted set declines every proposal and routes nowhere")
    @Test
    void emptyAcceptedSetDeclinesEverything() {
        var records = SimplifyStep.record(List.of(proposal("one"), proposal("two")), 1);
        var result = SimplifyStep.apply(records, List.of());

        assertFalse(result.applied());
        assertTrue(result.records().stream().allMatch(record -> record.disposition() == Disposition.DECLINED));
    }

    @DisplayName("An accepted id that is no open proposal of the firing is refused")
    @Test
    void unknownAcceptedIdIsRefused() {
        var records = SimplifyStep.record(List.of(proposal("one")), 1);
        assertThrows(IllegalArgumentException.class, () -> SimplifyStep.apply(records, List.of("SP-9")));
        var applied = SimplifyStep.apply(records, List.of("SP-1"));
        assertThrows(IllegalArgumentException.class, () -> SimplifyStep.apply(applied.records(), List.of("SP-1")));
    }

    @DisplayName("A proposal's file is repository-relative and its text is bounded")
    @Test
    void proposalIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new SimplifyProposal("/absolute/Path.java", "x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new SimplifyProposal("../outside.java", "x", "y"));
        assertThrows(IllegalArgumentException.class, () -> new SimplifyProposal("a.java", "", "y"));
        assertEquals("pm-core/src/main/java/one.java", proposal("one").file());
    }

    @DisplayName("A candidate shorter than 50 tokens is refused")
    @Test
    void candidateIsAtLeast50Tokens() {
        assertThrows(IllegalArgumentException.class, () -> new CloneCandidate("a.java", new LineRange(1, 1),
                "b.java", new LineRange(1, 1), 49));
    }

    @DisplayName("A line range is one-based and inclusive")
    @Test
    void lineRangeIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new LineRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LineRange(2, 1));
        assertEquals(3, new LineRange(1, 3).last());
    }

    private static SimplifyProposal proposal(String name) {
        return new SimplifyProposal("pm-core/src/main/java/%s.java".formatted(name),
                "Inline the pass-through method %s".formatted(name), "The method hides nothing");
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
