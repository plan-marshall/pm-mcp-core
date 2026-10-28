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

import static de.planmarshall.core.store.IntentFixtures.PLAN_ID;
import static de.planmarshall.core.store.IntentFixtures.answered;
import static de.planmarshall.core.store.IntentFixtures.context;
import static de.planmarshall.core.store.IntentFixtures.criterion;
import static de.planmarshall.core.store.IntentFixtures.operatorDecision;
import static de.planmarshall.core.store.IntentFixtures.revision;
import static de.planmarshall.core.store.IntentFixtures.valid;
import static de.planmarshall.core.store.IntentFixtures.withClaims;
import static de.planmarshall.core.store.IntentFixtures.withCriteria;
import static de.planmarshall.core.store.IntentFixtures.withDecisions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.store.IntentAcceptance.Accepted;
import de.planmarshall.core.store.IntentAcceptance.AnswerChannel;
import de.planmarshall.core.store.IntentAcceptance.Outcome;
import de.planmarshall.core.store.IntentAcceptance.Refused;
import de.planmarshall.core.store.IntentRecord.AdrRef;
import de.planmarshall.core.store.IntentRecord.AdrRelation;
import de.planmarshall.core.store.IntentRecord.Assumption;
import de.planmarshall.core.store.IntentRecord.AssumptionStatus;
import de.planmarshall.core.store.IntentRecord.CapabilityChange;
import de.planmarshall.core.store.IntentRecord.CapabilityLink;
import de.planmarshall.core.store.IntentRecord.Constraint;
import de.planmarshall.core.store.IntentRecord.ConstraintSource;
import de.planmarshall.core.store.IntentRecord.Criterion;
import de.planmarshall.core.store.IntentRecord.CriterionKind;
import de.planmarshall.core.store.IntentRecord.Decider;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentRecord.Deferred;
import de.planmarshall.core.store.IntentRecord.NonGoal;
import de.planmarshall.core.store.IntentRecord.SourceClaim;
import de.planmarshall.core.store.IntentRecord.VerifiedBy;
import de.planmarshall.core.store.IntentStore.Revision;

@DisplayName("The plan's intent record")
class IntentRecordTest {

    private static final Path FILE = Path.of("intent.json");

    @DisplayName("Round-trips through submit:refine, the light submit:outline and a re-planning")
    @ParameterizedTest
    @EnumSource(IntentSubmission.class)
    void roundTripsThroughEveryEntryPoint(IntentSubmission submission) {
        var accepted = accept(null, submission, revision(1, valid()), context(FILE));

        var store = assertInstanceOf(Accepted.class, accepted).store();
        var reread = IntentCodec.read(FILE, IntentCodec.write(store));
        assertEquals(store, reread);
        assertEquals(1, reread.current().revision());
        assertTrue(reread.history().isEmpty());
    }

    @DisplayName("A revision moves the current record to history[] and never changes it")
    @Test
    void revisionMovesCurrentToHistory() {
        var first = revision(1, valid());
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, first, context(FILE))).store();

        var second = revision(2, withCriteria(List.of(criterion("AC-3", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST))));
        var after = assertInstanceOf(Accepted.class,
                accept(store, IntentSubmission.REPLAN, second, context(FILE))).store();

        assertEquals(second, after.current());
        assertEquals(List.of(first), after.history());
        assertEquals(first, after.history().getFirst());
        assertEquals(after, IntentCodec.read(FILE, IntentCodec.write(after)));
    }

    @DisplayName("A revision that does not follow the current one is refused")
    @Test
    void refusesRevisionOutOfSequence() {
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, valid()), context(FILE))).store();
        assertThrows(IllegalArgumentException.class, () -> store.accept(revision(3, valid())));
    }

    @DisplayName("An identifier an earlier revision removed is never reused")
    @Test
    void refusesReusedRemovedIdentifier() {
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, valid()), context(FILE))).store();
        var withoutFirst = withCriteria(List.of(criterion("AC-3", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var second = assertInstanceOf(Accepted.class,
                accept(store, IntentSubmission.REPLAN, revision(2, withoutFirst), context(FILE))).store();

        var reusing = withCriteria(List.of(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var outcome = accept(second, IntentSubmission.REPLAN, revision(3, reusing), context(FILE));

        assertTrue(assertInstanceOf(Refused.class, outcome).detail().contains("AC-1"));
    }

    @DisplayName("An operator decision without an answer is refused, naming the decision")
    @Test
    void refusesOperatorDecisionWithoutAnswer() {
        var record = withDecisions(List.of(operatorDecision("DC-1", "q-1")));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, record), context(FILE));
        assertTrue(assertInstanceOf(Refused.class, outcome).detail().contains("DC-1"));
    }

    @DisplayName("An operator decision of another plan is refused")
    @Test
    void refusesOperatorDecisionOfAnotherPlan() {
        var record = withDecisions(List.of(operatorDecision("DC-1", "q-1")));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, record),
                answered(FILE, "q-1", "another-plan", AnswerChannel.WEB));
        assertTrue(assertInstanceOf(Refused.class, outcome).detail().contains("another plan"));
    }

    @DisplayName("A quorum decision answered by the operator is refused")
    @Test
    void refusesQuorumDecisionAnsweredByOperator() {
        var record = withDecisions(List.of(new Decision("DC-1", "The quorum chose the one-file record",
                List.of("a store", "one file"), Decider.QUORUM, "q-1", null, null)));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, record),
                answered(FILE, "q-1", PLAN_ID, AnswerChannel.WEB));
        assertTrue(assertInstanceOf(Refused.class, outcome).detail().contains("DC-1"));
    }

    @DisplayName("An operator decision answered through the web is accepted")
    @Test
    void acceptsOperatorDecisionAnsweredThroughWeb() {
        var record = withDecisions(List.of(operatorDecision("DC-1", "q-1")));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, record),
                answered(FILE, "q-1", PLAN_ID, AnswerChannel.WEB));
        assertInstanceOf(Accepted.class, outcome);
    }

    @DisplayName("A fact decision whose evidence path does not exist is refused")
    @Test
    void refusesFactDecisionWithoutEvidenceInTree(@TempDir Path tree) {
        var record = withDecisions(List.of(new Decision("DC-1", "The tree shows the store layout",
                List.of("a store", "one file"), Decider.FACT, null, "pm-core/missing.adoc", null)));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, record), context(tree));
        assertTrue(assertInstanceOf(Refused.class, outcome).detail().contains("missing.adoc"));
    }

    @DisplayName("A contradicting decision is refused unless the operator decided it")
    @Test
    void refusesContradictingModelDecision() {
        assertThrows(IllegalArgumentException.class,
                () -> new Decision("DC-1", "The record departs from the store layout ADR",
                        List.of("follow", "depart"), Decider.MODEL, null, null,
                        new AdrRef("core-store-1", AdrRelation.CONTRADICTS)));
    }

    @DisplayName("A capability requirement's presence and base fingerprint are checked")
    @Test
    void checksCapabilityRequirements() {
        var added = withCriteria(List.of(
                new Criterion("AC-1", "WHEN a plan is refined THE system SHALL write the intent record",
                        CriterionKind.NEW, VerifiedBy.TEST,
                        new CapabilityLink("intent-record", "round-trip", CapabilityChange.ADDED, null)),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var facts = new IntentAcceptance.Context(PLAN_ID, question -> Optional.empty(),
                (capability, requirement) -> Optional.of("a".repeat(64)), Optional.empty(), FILE);

        assertTrue(assertInstanceOf(Refused.class,
                accept(null, IntentSubmission.REFINE, revision(1, added), facts)).detail().contains("already exists"));
    }

    @DisplayName("The epic's required claims are seeded and must appear in the record")
    @Test
    void seedsRequiredClaimsFromEpic() {
        var epic = new EpicIntent("The planning phases state their intent and outline as records",
                List.of("The intent record round-trips", "The outline record round-trips",
                        "The simplify step runs"),
                List.of("No chat integration"), List.of());
        var seeded = new IntentAcceptance.Context(PLAN_ID, question -> Optional.empty(),
                (capability, requirement) -> Optional.empty(),
                Optional.of(new IntentAcceptance.EpicSeed(epic, "The plan lands with its records")), FILE);

        var refused = accept(null, IntentSubmission.REFINE, revision(1, valid()), seeded);
        assertTrue(assertInstanceOf(Refused.class, refused).detail().contains("required claim"));

        var record = withClaims(List.of(
                new SourceClaim("The intent record round-trips", "goal", null),
                new SourceClaim("The outline record round-trips", "goal", null),
                new SourceClaim("The simplify step runs", "goal", null),
                new SourceClaim("No chat integration", "NG-1", null),
                new SourceClaim("The plan lands with its records", "goal", null)));
        assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, record), seeded));
    }

    @DisplayName("A fix criterion carries its current behaviour and an unchanged criterion")
    @Test
    void refusesFixWithoutItsTriple() {
        var fix = criterion("AC-1", CriterionKind.FIX, VerifiedBy.TEST);
        assertThrows(IllegalArgumentException.class, () -> new IntentRecord(valid().why(), valid().goal(),
                List.of(fix), null, valid().nonGoals(), valid().constraints(), valid().decisions(),
                valid().assumptions(), valid().openQuestions(), valid().deferred(), valid().sourceClaims()));
    }

    @DisplayName("An unverified assumption marks exactly one riskiest assumption with its test")
    @Test
    void refusesUnverifiedAssumptionWithoutRiskiest() {
        var assumption = new Assumption("AN-1", "The store is writable", AssumptionStatus.UNVERIFIED, false, null);
        assertThrows(IllegalArgumentException.class, () -> new IntentRecord(valid().why(), valid().goal(),
                valid().criteria(), null, valid().nonGoals(), valid().constraints(), valid().decisions(),
                List.of(assumption), valid().openQuestions(), valid().deferred(), valid().sourceClaims()));
        var riskiest = new Assumption("AN-1", "The store is writable", AssumptionStatus.UNVERIFIED, true,
                "a write to a scratch file");
        assertInstanceOf(Accepted.class, accept(null, IntentSubmission.REFINE,
                revision(1, new IntentRecord(valid().why(), valid().goal(), valid().criteria(), null,
                        valid().nonGoals(), valid().constraints(), valid().decisions(), List.of(riskiest),
                        valid().openQuestions(), valid().deferred(), valid().sourceClaims())),
                context(FILE)));
    }

    @DisplayName("Identifiers are unique and decimal")
    @Test
    void refusesDuplicateIdentifiers() {
        assertThrows(IllegalArgumentException.class,
                () -> withCriteria(List.of(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                        criterion("AC-1", CriterionKind.UNCHANGED, VerifiedBy.TEST))));
    }

    @DisplayName("A code constraint carries its evidence")
    @Test
    void refusesCodeConstraintWithoutEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> new Constraint("CN-1", "No direct HTTP client", ConstraintSource.CODE, null));
    }

    @DisplayName("A source claim lands in an item of the record or is dropped with a reason")
    @Test
    void refusesClaimLandingOutsideTheRecord() {
        assertThrows(IllegalArgumentException.class,
                () -> withClaims(List.of(new SourceClaim("A claim of the source", "AC-9", null))));
        assertThrows(IllegalArgumentException.class,
                () -> withClaims(List.of(new SourceClaim("A claim of the source", "dropped", null))));
    }

    @DisplayName("A deferred item names its reason")
    @Test
    void deferredItemNamesItsReason() {
        assertThrows(IllegalArgumentException.class,
                () -> new Deferred("DF-1", "The chat", "   "));
        assertFalse(new Deferred("DF-1", "The chat", "It is another plan").reason().isBlank());
    }

    @DisplayName("The clarified request is rendered from the record")
    @Test
    void rendersClarifiedRequest() {
        var record = withDecisions(List.of(operatorDecision("DC-1", "q-1"),
                new Decision("DC-2", "The record is one file", List.of("a store", "one file"), Decider.MODEL, null,
                        null, null)));
        var store = assertInstanceOf(Accepted.class, accept(null, IntentSubmission.REFINE, revision(1, record),
                answered(FILE, "q-1", PLAN_ID, AnswerChannel.WEB))).store();

        var rendered = ClarifiedRequestRenderer.render(store);

        assertTrue(rendered.startsWith(ClarifiedRequestRenderer.HEADING));
        assertTrue(rendered.contains("`AC-1`"));
        assertTrue(rendered.contains("`NG-1`"));
        assertTrue(rendered.contains("`CN-1`"));
        assertTrue(rendered.contains("### Operator Decisions"));
        assertTrue(rendered.contains("### Model's Calls"));
        assertTrue(rendered.indexOf("### Operator Decisions") < rendered.indexOf("### Model's Calls"));
        assertTrue(rendered.contains("\"The operator wants the intent visible\" → goal"));
    }

    @DisplayName("An existing clarified request is replaced in place")
    @Test
    void replacesExistingClarifiedRequest() {
        var store = IntentStore.first(revision(1, valid()));
        var request = """
                # Request

                ## Original Input

                The operator's words.

                ## Clarified Request

                stale text

                ## Notes

                Kept.
                """;
        var updated = ClarifiedRequestRenderer.applyTo(request, store);
        assertFalse(updated.contains("stale text"));
        assertTrue(updated.contains("## Original Input"));
        assertTrue(updated.contains("## Notes"));
        assertTrue(updated.contains("`AC-1`"));
    }

    @DisplayName("A document without a clarified request gains one at its end")
    @Test
    void appendsClarifiedRequest() {
        var updated = ClarifiedRequestRenderer.applyTo("# Request\n\nInput.\n",
                IntentStore.first(revision(1, valid())));
        assertTrue(updated.startsWith("# Request"));
        assertTrue(updated.contains(ClarifiedRequestRenderer.HEADING));
    }

    @DisplayName("The reader refuses a document of another format version")
    @Test
    void refusesOtherFormatVersion() {
        var document = "{\"format_version\": 2, \"current\": {}, \"history\": []}".getBytes(StandardCharsets.UTF_8);
        assertThrows(StoreRefusal.class, () -> IntentCodec.read(FILE, document));
    }

    @DisplayName("A decision with a question id but a model decider is refused")
    @Test
    void refusesModelDecisionWithQuestionId() {
        assertThrows(IllegalArgumentException.class, () -> new Decision("DC-1", "The model chose the record",
                List.of("a store", "one file"), Decider.MODEL, "q-1", null, null));
    }

    @DisplayName("A non-goal names its text")
    @Test
    void nonGoalIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new NonGoal("NG-1", "no"));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, withCriteria(List.of(
                criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST)))), context(FILE));
        assertInstanceOf(Accepted.class, outcome);
    }

    @DisplayName("Two revisions whose identifiers differ resolve to the removed set")
    @Test
    void removedIdentifiersAreReported() {
        var store = IntentStore.first(revision(1, valid()));
        var second = store.accept(revision(2, withCriteria(List.of(
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST),
                criterion("AC-3", CriterionKind.NEW, VerifiedBy.TEST)))));
        assertEquals(List.of("AC-1"), second.removedIdentifiers());
    }

    @DisplayName("A capability link round-trips through the codec")
    @Test
    void capabilityLinkRoundTrips() {
        var record = withCriteria(List.of(new Criterion("AC-1",
                        "WHEN a plan is refined THE system SHALL write the intent record", CriterionKind.NEW,
                        VerifiedBy.TEST, new CapabilityLink("intent-record", "round-trip", CapabilityChange.ADDED, null)),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, record), context(FILE))).store();

        var reread = IntentCodec.read(FILE, IntentCodec.write(store));
        assertEquals(store, reread);
        assertEquals("intent-record", reread.current().record().criterion("AC-1").orElseThrow().capability()
                .capability());
    }

    @DisplayName("A current behaviour without a fix criterion, or outside its form, is refused")
    @Test
    void checksCurrentBehaviour() {
        assertThrows(IllegalArgumentException.class, () -> new IntentRecord(valid().why(), valid().goal(),
                valid().criteria(), "WHEN a plan is refined THEN THE system writes the record", valid().nonGoals(),
                valid().constraints(), valid().decisions(), valid().assumptions(), valid().openQuestions(),
                valid().deferred(), valid().sourceClaims()));

        var fix = new Criterion("AC-1", "WHEN a plan is refined THE system SHALL write the record",
                CriterionKind.FIX, VerifiedBy.TEST, null);
        var unchanged = criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST);
        assertThrows(IllegalArgumentException.class, () -> new IntentRecord(valid().why(), valid().goal(),
                List.of(fix, unchanged), "the record was prose before", valid().nonGoals(), valid().constraints(),
                valid().decisions(), valid().assumptions(), valid().openQuestions(), valid().deferred(),
                valid().sourceClaims()));

        var conforming = new IntentRecord(valid().why(), valid().goal(), List.of(fix, unchanged),
                "WHEN a plan is refined THEN THE system writes the record as prose", valid().nonGoals(),
                valid().constraints(), valid().decisions(), valid().assumptions(), valid().openQuestions(),
                valid().deferred(), valid().sourceClaims());
        assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, conforming), context(FILE)));
    }

    private static Outcome accept(IntentStore before, IntentSubmission submission, Revision revision,
            IntentAcceptance.Context context) {
        return IntentAcceptance.accept(before, submission, revision, context);
    }
}
