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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

@EnableGeneratorController
@DisplayName("The plan's intent record")
class IntentRecordTest {

    private static final Path FILE = Path.of("intent.json");

    @DisplayName("Round-trips through submit:refine, the light submit:outline and a re-planning")
    @ParameterizedTest
    @EnumSource(IntentSubmission.class)
    void roundTripsThroughEveryEntryPoint(IntentSubmission submission) {
        var accepted = accept(null, submission, revision(1, valid()), context(FILE));

        var store = assertInstanceOf(Accepted.class, accepted, "the submission yields an accepted store").store();
        var reread = IntentCodec.read(FILE, IntentCodec.write(store));
        assertEquals(store, reread, "the written store is read back equal");
        assertEquals(1, reread.current().revision(), "the current revision is the first one");
        assertTrue(reread.history().isEmpty(), "the first revision has no history");
    }

    @DisplayName("A revision moves the current record to history[] and never changes it")
    @Test
    void revisionMovesCurrentToHistory() {
        var first = revision(1, valid());
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, first, context(FILE)), "the first revision is accepted").store();

        var second = revision(2, withCriteria(List.of(criterion("AC-3", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST))));
        var after = assertInstanceOf(Accepted.class,
                accept(store, IntentSubmission.REPLAN, second, context(FILE)), "the re-plan is accepted").store();

        assertEquals(second, after.current(), "the second revision is the current record");
        assertEquals(List.of(first), after.history(), "the first revision moved to the history");
        assertEquals(first, after.history().getFirst(), "the history keeps the first record unchanged");
        assertEquals(after, IntentCodec.read(FILE, IntentCodec.write(after)),
                "the revised store round-trips through the codec");
    }

    @DisplayName("A revision that does not follow the current one is refused")
    @Test
    void refusesRevisionOutOfSequence() {
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, valid()), context(FILE)),
                "the first revision is accepted").store();
        var outOfSequence = revision(3, valid());
        assertThrows(IllegalArgumentException.class, () -> store.accept(outOfSequence),
                "a revision beyond the next one is refused");
    }

    @DisplayName("An identifier an earlier revision removed is never reused")
    @Test
    void refusesReusedRemovedIdentifier() {
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, valid()), context(FILE)),
                "the first revision is accepted").store();
        var withoutFirst = withCriteria(List.of(criterion("AC-3", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var second = assertInstanceOf(Accepted.class,
                accept(store, IntentSubmission.REPLAN, revision(2, withoutFirst), context(FILE)),
                "the second revision is accepted").store();

        var reusing = withCriteria(List.of(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var outcome = accept(second, IntentSubmission.REPLAN, revision(3, reusing), context(FILE));

        var refusal = assertInstanceOf(Refused.class, outcome, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("AC-1"), "the refusal names the reused identifier AC-1");
    }

    @DisplayName("An operator decision without an answer is refused, naming the decision")
    @Test
    void refusesOperatorDecisionWithoutAnswer() {
        var intentRecord = withDecisions(List.of(operatorDecision("DC-1", "q-1")));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, intentRecord), context(FILE));
        var refusal = assertInstanceOf(Refused.class, outcome, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("DC-1"), "the refusal names the unanswered decision DC-1");
    }

    @DisplayName("An operator decision of another plan is refused")
    @Test
    void refusesOperatorDecisionOfAnotherPlan() {
        var intentRecord = withDecisions(List.of(operatorDecision("DC-1", "q-1")));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, intentRecord),
                answered(FILE, "q-1", "another-plan", AnswerChannel.WEB));
        var refusal = assertInstanceOf(Refused.class, outcome, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("another plan"), "the refusal names the other plan of the answer");
    }

    @DisplayName("A quorum decision answered by the operator is refused")
    @Test
    void refusesQuorumDecisionAnsweredByOperator() {
        var intentRecord = withDecisions(List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                options(), Decider.QUORUM, "q-1", null, null)));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, intentRecord),
                answered(FILE, "q-1", PLAN_ID, AnswerChannel.WEB));
        var refusal = assertInstanceOf(Refused.class, outcome, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("DC-1"), "the refusal names the quorum decision DC-1");
    }

    @DisplayName("An operator decision answered through the web is accepted")
    @Test
    void acceptsOperatorDecisionAnsweredThroughWeb() {
        var intentRecord = withDecisions(List.of(operatorDecision("DC-1", "q-1")));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, intentRecord),
                answered(FILE, "q-1", PLAN_ID, AnswerChannel.WEB));
        assertInstanceOf(Accepted.class, outcome, "an operator answer through the web is accepted");
    }

    @DisplayName("A fact decision whose evidence path does not exist is refused")
    @Test
    void refusesFactDecisionWithoutEvidenceInTree(@TempDir Path tree) {
        var intentRecord = withDecisions(List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                options(), Decider.FACT, null, "pm-core/missing.adoc", null)));
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, intentRecord), context(tree));
        var refusal = assertInstanceOf(Refused.class, outcome, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("missing.adoc"), "the refusal names the missing evidence path");
    }

    @DisplayName("A contradicting decision is refused unless the operator decided it")
    @Test
    void refusesContradictingModelDecision() {
        var adr = new AdrRef("core-store-1", AdrRelation.CONTRADICTS);
        assertThrows(IllegalArgumentException.class,
                () -> new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                        options(), Decider.MODEL, null, null, adr),
                "a model decision cannot contradict an ADR");
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

        var refusal = assertInstanceOf(Refused.class,
                accept(null, IntentSubmission.REFINE, revision(1, added), facts), "the outcome is a refusal");
        assertTrue(refusal.detail().contains("already exists"), "the refusal names the existing capability");
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
        var refusal = assertInstanceOf(Refused.class, refused, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("required claim"), "the refusal names the missing required claim");

        var intentRecord = withClaims(List.of(
                new SourceClaim("The intent record round-trips", "goal", null),
                new SourceClaim("The outline record round-trips", "goal", null),
                new SourceClaim("The simplify step runs", "goal", null),
                new SourceClaim("No chat integration", "NG-1", null),
                new SourceClaim("The plan lands with its records", "goal", null)));
        assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, intentRecord), seeded),
                "the record with every claim is accepted");
    }

    @DisplayName("A fix criterion carries its current behaviour and an unchanged criterion")
    @Test
    void refusesFixWithoutItsTriple() {
        var base = valid();
        var fix = criterion("AC-1", CriterionKind.FIX, VerifiedBy.TEST);
        var criteria = List.of(fix);
        var assumptions = base.assumptions();
        assertThrows(IllegalArgumentException.class, () -> copy(base, criteria, null, assumptions),
                "a fix criterion needs its triple");
    }

    @DisplayName("An unverified assumption marks exactly one riskiest assumption with its test")
    @Test
    void refusesUnverifiedAssumptionWithoutRiskiest() {
        var base = valid();
        var criteria = base.criteria();
        var text = Generators.letterStrings(5, 40).next();
        var unverified = new Assumption("AN-1", text, AssumptionStatus.UNVERIFIED, false, null);
        var unverifiedAssumptions = List.of(unverified);
        assertThrows(IllegalArgumentException.class, () -> copy(base, criteria, null, unverifiedAssumptions),
                "an unverified assumption needs the riskiest mark");
        var riskiest = new Assumption("AN-1", text, AssumptionStatus.UNVERIFIED, true,
                Generators.letterStrings(10, 40).next());
        assertInstanceOf(Accepted.class, accept(null, IntentSubmission.REFINE,
                revision(1, copy(base, criteria, null, List.of(riskiest))), context(FILE)),
                "the riskiest assumption with a test is accepted");
    }

    @DisplayName("Identifiers are unique and decimal")
    @Test
    void refusesDuplicateIdentifiers() {
        var duplicated = List.of(criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST),
                criterion("AC-1", CriterionKind.UNCHANGED, VerifiedBy.TEST));
        assertThrows(IllegalArgumentException.class, () -> withCriteria(duplicated),
                "duplicate identifiers are refused");
    }

    @DisplayName("A code constraint carries its evidence")
    @Test
    void refusesCodeConstraintWithoutEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> new Constraint("CN-1", Generators.letterStrings(10, 60).next(), ConstraintSource.CODE, null),
                "a code constraint needs its evidence");
    }

    @DisplayName("A source claim lands in an item of the record or is dropped with a reason")
    @Test
    void refusesClaimLandingOutsideTheRecord() {
        var text = Generators.letterStrings(10, 60).next();
        var outside = List.of(new SourceClaim(text, "AC-9", null));
        assertThrows(IllegalArgumentException.class, () -> withClaims(outside),
                "a claim outside the record is refused");
        var dropped = List.of(new SourceClaim(text, "dropped", null));
        assertThrows(IllegalArgumentException.class, () -> withClaims(dropped),
                "a dropped claim without a reason is refused");
    }

    @DisplayName("A deferred item names its reason")
    @Test
    void deferredItemNamesItsReason() {
        var text = Generators.letterStrings(5, 40).next();
        assertThrows(IllegalArgumentException.class,
                () -> new Deferred("DF-1", text, "   "), "a deferred item needs a non-blank reason");
        assertFalse(new Deferred("DF-1", text, Generators.letterStrings(5, 40).next()).reason().isBlank(),
                "the deferred reason is kept non-blank");
    }

    @DisplayName("The clarified request is rendered from the record")
    @Test
    void rendersClarifiedRequest() {
        var intentRecord = withDecisions(List.of(operatorDecision("DC-1", "q-1"),
                new Decision("DC-2", Generators.letterStrings(10, 60).next(), options(), Decider.MODEL, null,
                        null, null)));
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, intentRecord),
                        answered(FILE, "q-1", PLAN_ID, AnswerChannel.WEB)),
                "the answered operator decision is accepted").store();

        var rendered = ClarifiedRequestRenderer.render(store);

        assertTrue(rendered.startsWith(ClarifiedRequestRenderer.HEADING),
                "the render starts with its heading");
        assertTrue(rendered.contains("`AC-1`"), "the render names the criterion AC-1");
        assertTrue(rendered.contains("`NG-1`"), "the render names the non-goal NG-1");
        assertTrue(rendered.contains("`CN-1`"), "the render names the constraint CN-1");
        assertTrue(rendered.contains("### Operator Decisions"),
                "the render has the operator decisions section");
        assertTrue(rendered.contains("### Model's Calls"), "the render has the model's calls section");
        assertTrue(rendered.indexOf("### Operator Decisions") < rendered.indexOf("### Model's Calls"),
                "the operator decisions come before the model's calls");
        assertTrue(rendered.contains("\"The operator wants the intent visible\" → goal"),
                "the render states the claim's landing");
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
        assertFalse(updated.contains("stale text"), "the stale clarified request is gone");
        assertTrue(updated.contains("## Original Input"), "the original input section is kept");
        assertTrue(updated.contains("## Notes"), "the notes section is kept");
        assertTrue(updated.contains("`AC-1`"), "the new clarified request is rendered");
    }

    @DisplayName("A document without a clarified request gains one at its end")
    @Test
    void appendsClarifiedRequest() {
        var updated = ClarifiedRequestRenderer.applyTo("# Request\n\nInput.\n",
                IntentStore.first(revision(1, valid())));
        assertTrue(updated.startsWith("# Request"), "the appended document keeps its title");
        assertTrue(updated.contains(ClarifiedRequestRenderer.HEADING),
                "the appended document gains the heading");
    }

    @DisplayName("The reader refuses a document of another format version")
    @Test
    void refusesOtherFormatVersion() {
        var document = "{\"format_version\": 2, \"current\": {}, \"history\": []}".getBytes(StandardCharsets.UTF_8);
        assertThrows(StoreRefusal.class, () -> IntentCodec.read(FILE, document),
                "another format version is refused by the reader");
    }

    @DisplayName("A decision with a question id but a model decider is refused")
    @Test
    void refusesModelDecisionWithQuestionId() {
        assertThrows(IllegalArgumentException.class,
                () -> new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                        options(), Decider.MODEL, "q-1", null, null),
                "a model decision carries no question id");
    }

    @DisplayName("A non-goal names its text")
    @Test
    void nonGoalIsBounded() {
        assertThrows(IllegalArgumentException.class, () -> new NonGoal("NG-1", "no"),
                "a non-goal text below the minimum is refused");
        var outcome = accept(null, IntentSubmission.REFINE, revision(1, withCriteria(List.of(
                criterion("AC-1", CriterionKind.NEW, VerifiedBy.TEST)))), context(FILE));
        assertInstanceOf(Accepted.class, outcome, "the revision with one criterion is accepted");
    }

    @DisplayName("Two revisions whose identifiers differ resolve to the removed set")
    @Test
    void removedIdentifiersAreReported() {
        var store = IntentStore.first(revision(1, valid()));
        var second = store.accept(revision(2, withCriteria(List.of(
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST),
                criterion("AC-3", CriterionKind.NEW, VerifiedBy.TEST)))));
        assertEquals(List.of("AC-1"), second.removedIdentifiers(),
                "the removed identifier is reported alone");
    }

    @DisplayName("A capability link round-trips through the codec")
    @Test
    void capabilityLinkRoundTrips() {
        var intentRecord = withCriteria(List.of(new Criterion("AC-1",
                        "WHEN a plan is refined THE system SHALL write the intent record", CriterionKind.NEW,
                        VerifiedBy.TEST, new CapabilityLink("intent-record", "round-trip", CapabilityChange.ADDED, null)),
                criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST)));
        var store = assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, intentRecord), context(FILE)),
                "the record with a capability link is accepted").store();

        var reread = IntentCodec.read(FILE, IntentCodec.write(store));
        assertEquals(store, reread, "the capability link round-trips through the codec");
        assertEquals("intent-record", reread.current().intentRecord().criterion("AC-1").orElseThrow().capability()
                .capability(), "the capability link keeps its capability name");
    }

    @DisplayName("A current behaviour without a fix criterion, or outside its form, is refused")
    @Test
    void checksCurrentBehaviour() {
        var base = valid();
        var criteria = base.criteria();
        var assumptions = base.assumptions();
        assertThrows(IllegalArgumentException.class,
                () -> copy(base, criteria, "WHEN a plan is refined THEN THE system writes the record", assumptions),
                "a current behaviour without a fix criterion is refused");

        var fix = new Criterion("AC-1", "WHEN a plan is refined THE system SHALL write the record",
                CriterionKind.FIX, VerifiedBy.TEST, null);
        var unchanged = criterion("AC-2", CriterionKind.UNCHANGED, VerifiedBy.TEST);
        var withFix = List.of(fix, unchanged);
        assertThrows(IllegalArgumentException.class,
                () -> copy(base, withFix, "the record was prose before", assumptions),
                "a current behaviour outside its form is refused");

        var conforming = new IntentRecord(valid().why(), valid().goal(), List.of(fix, unchanged),
                "WHEN a plan is refined THEN THE system writes the record as prose", valid().nonGoals(),
                valid().constraints(), valid().decisions(), valid().assumptions(), valid().openQuestions(),
                valid().deferred(), valid().sourceClaims());
        assertInstanceOf(Accepted.class,
                accept(null, IntentSubmission.REFINE, revision(1, conforming), context(FILE)),
                "the conforming fix and current behaviour is accepted");
    }

    @DisplayName("A first revision other than 1 is refused, not thrown")
    @Test
    void refusesFirstRevisionOtherThanOne() {
        var outcome = accept(null, IntentSubmission.REFINE, revision(2, valid()), context(FILE));
        var refusal = assertInstanceOf(Refused.class, outcome, "the outcome is a refusal");
        assertTrue(refusal.detail().contains("revision 1"), "the refusal names the first revision");
    }

    @DisplayName("A fact decision whose evidence is no path is refused, not thrown")
    @Test
    void refusesEvidenceThatIsNoPath(@TempDir Path tree) {
        var intentRecord = withDecisions(List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                options(), Decider.FACT, null, "bad\u0000path", null)));
        var refusal = assertInstanceOf(Refused.class,
                accept(null, IntentSubmission.REFINE, revision(1, intentRecord), context(tree)),
                "the outcome is a refusal");
        assertTrue(refusal.detail().contains("no path"), "the refusal names the evidence that is no path");
    }

    @DisplayName("A fact decision whose evidence is a symlink outside the tree is refused")
    @Test
    void refusesSymlinkedEvidenceOutsideTheTree(@TempDir Path base) throws Exception {
        var tree = base.resolve("tree");
        Files.createDirectories(tree);
        var outside = base.resolve("outside.adoc");
        Files.writeString(outside, Generators.letterStrings(5, 20).next());
        Files.createSymbolicLink(tree.resolve("link.adoc"), outside);
        var intentRecord = withDecisions(List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                options(), Decider.FACT, null, "link.adoc", null)));

        var refusal = assertInstanceOf(Refused.class,
                accept(null, IntentSubmission.REFINE, revision(1, intentRecord), context(tree)),
                "the outcome is a refusal");
        assertTrue(refusal.detail().contains("outside the tree"),
                "the symlinked evidence outside the tree is refused");
    }

    @DisplayName("A fact decision whose evidence lies outside the tree is refused")
    @Test
    void refusesEvidenceOutsideTheTree(@TempDir Path tree) {
        var intentRecord = withDecisions(List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                options(), Decider.FACT, null, "../outside.adoc", null)));
        var refusal = assertInstanceOf(Refused.class,
                accept(null, IntentSubmission.REFINE, revision(1, intentRecord), context(tree)),
                "the outcome is a refusal");
        assertTrue(refusal.detail().contains("outside the tree"),
                "the relative evidence outside the tree is refused");

        var absolute = withDecisions(List.of(new Decision("DC-1", Generators.letterStrings(10, 60).next(),
                options(), Decider.FACT, null, "/etc/hosts", null)));
        var absoluteRefusal = assertInstanceOf(Refused.class,
                accept(null, IntentSubmission.REFINE, revision(1, absolute), context(tree)),
                "the outcome is a refusal");
        assertTrue(absoluteRefusal.detail().contains("outside the tree"),
                "the absolute evidence outside the tree is refused");
    }

    private static Outcome accept(IntentStore before, IntentSubmission submission, Revision revision,
            IntentAcceptance.Context context) {
        return IntentAcceptance.accept(before, submission, revision, context);
    }

    private static IntentRecord copy(IntentRecord base, List<Criterion> criteria, String currentBehaviour,
            List<Assumption> assumptions) {
        return new IntentRecord(base.why(), base.goal(), criteria, currentBehaviour, base.nonGoals(),
                base.constraints(), base.decisions(), assumptions, base.openQuestions(), base.deferred(),
                base.sourceClaims());
    }

    private static List<String> options() {
        return List.of(Generators.letterStrings(2, 20).next(), Generators.letterStrings(2, 20).next());
    }
}
