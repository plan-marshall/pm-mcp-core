/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.Checkout;
import de.planmarshall.core.role.qualification.GradingConsent;
import de.planmarshall.core.role.qualification.GradingHarness;
import de.planmarshall.core.role.qualification.GradingOutcome;
import de.planmarshall.core.role.qualification.GradingPlan;
import de.planmarshall.core.role.qualification.GradingProfile;
import de.planmarshall.core.role.qualification.ItemCheckout;
import de.planmarshall.core.role.qualification.JudgeConfiguration;
import de.planmarshall.core.role.qualification.JudgeScorer;
import de.planmarshall.core.role.qualification.QualificationRecord;
import de.planmarshall.core.role.qualification.QualificationRecordStore;
import de.planmarshall.core.role.qualification.TokenTotal;
import de.planmarshall.core.role.qualification.Topic;

/**
 * One grading run of a role's configuration (Model Work § Grading; § Judge Instrument Check; § Corpus Sources): a run
 * starts only on the operator's consent and never on a failed import; it checks the judge as an instrument first; it
 * offers every item in the fresh form and, for a role that is not zero-tool, the warm form; a screener's items are
 * offered five times; an item of the project's repository is graded at its origin commit, or in the checkout when that
 * commit is missing; and the record holds every item's tokens and the form's totals.
 */
@DisplayName("Role grading run")
class RoleGraderTest {

    @TempDir
    Path base;

    @Nested
    @DisplayName("measurements")
    class Measurements {

        @Test
        @DisplayName("a grading over a complete corpus writes the record with its tokens")
        void writesRecord() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.complete());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            var record = graded.record();
            assertEquals(GraderFixtures.ROLE, record.role());
            assertEquals(plan.role().qualificationDigest(), record.qualificationDigest());
            assertEquals(GraderFixtures.CORPUS_REVISION, record.corpusRevision());
            assertEquals(List.of(), record.missedSources());
            assertEquals(0, record.ungraded());
            var fresh = record.form(RoleForm.FRESH).orElseThrow();
            assertEquals(new BigDecimal("100"), fresh.topics().get(Topic.OWN).agreement());
            assertEquals(1, fresh.topics().get(Topic.OWN).items());
            assertEquals(TokenTotal.measured(2_000), fresh.totalTokens());
            assertEquals(List.of(RoleForm.FRESH, RoleForm.WARM), List.copyOf(record.forms().keySet()));
        }

        @Test
        @DisplayName("the record is read back from the project set")
        void wroteToStore() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.complete());

            fixtures.grader.grade(plan, GradingConsent.GRANTED);
            var stored = fixtures.store.lookup(GraderFixtures.ROLE);

            assertTrue(stored.isPresent());
            assertEquals(plan.role().qualificationDigest(), stored.orElseThrow().qualificationDigest());
        }

        @Test
        @DisplayName("the offer shows the expected tokens of the plan")
        void offer() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.complete());

            var offer = fixtures.grader.offer(plan);

            assertEquals(2, offer.items());
            assertEquals(List.of(RoleForm.FRESH, RoleForm.WARM), offer.forms());
            assertTrue(offer.expectedTokens() > 0);
        }

        @Test
        @DisplayName("the warm-fresh gap is the largest over the topics")
        void warmFreshGap() {
            var fixtures = new Fixtures();
            fixtures.harness.answers(RoleForm.WARM, "review/own", "defective");
            var plan = plan(fixtures, GraderFixtures.complete());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(new BigDecimal("100"), graded.record().warmFreshGap());
        }

        @Test
        @DisplayName("a role that is zero-tool is graded fresh only")
        void zeroToolFreshOnly() {
            var fixtures = new Fixtures();
            var screener = ModelFixtures.role("screener", RoleClass.ZERO_TOOL, RoleForm.FRESH);
            var item = GraderFixtures.item("screen/injected", "screener", "suspicious").build();
            var plan = GraderFixtures.plan(screener, List.of(item), fixtures.judgeConfiguration(),
                    GraderFixtures.complete());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertFalse(graded.record().form(RoleForm.WARM).isPresent());
            assertEquals(GradingProfile.SCREENER_RUNS, graded.record().items().size());
        }
    }

    @Nested
    @DisplayName("the run's preconditions")
    class Preconditions {

        @Test
        @DisplayName("a declined grading is owed and writes no record")
        void declined() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.complete());

            var declined = assertInstanceOf(GradingOutcome.Declined.class,
                    fixtures.grader.grade(plan, GradingConsent.DECLINED));

            assertTrue(declined.owed());
            assertTrue(fixtures.harness.offered().isEmpty());
            assertEquals(Optional.empty(), fixtures.store.lookup(GraderFixtures.ROLE));
        }

        @Test
        @DisplayName("a grading over a failed import is refused and writes no record")
        void failedImport() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.failed());

            var refused = assertInstanceOf(GradingOutcome.Refused.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(GradingOutcome.Refused.CORPUS_IMPORT_FAILED, refused.reason());
            assertTrue(fixtures.harness.offered().isEmpty());
            assertEquals(Optional.empty(), fixtures.store.lookup(GraderFixtures.ROLE));
        }

        @Test
        @DisplayName("a grading over a partial import names its missed sources")
        void partialImport() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.partial());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(List.of("papers"), graded.record().missedSources());
        }

        @Test
        @DisplayName("a judge that does not follow the reference refuses the grading judge_unchecked")
        void judgeUnchecked() {
            var fixtures = new Fixtures();
            fixtures.judge.scores(prompt -> Optional.of(BigDecimal.ONE));
            var plan = plan(fixtures, GraderFixtures.complete());

            var refused = assertInstanceOf(GradingOutcome.Refused.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(GradingOutcome.Refused.JUDGE_UNCHECKED, refused.reason());
            assertTrue(fixtures.harness.offered().isEmpty());
            assertEquals(Optional.empty(), fixtures.store.lookup(GraderFixtures.ROLE));
        }

        @Test
        @DisplayName("a valid judge check record is reused instead of running the check again")
        void judgeCheckReused() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.complete());
            fixtures.grader.grade(plan, GradingConsent.GRANTED);
            var writes = fixtures.judgeChecks.writes();
            var prompts = fixtures.judge.prompts().size();
            fixtures.harness.offered().clear();

            fixtures.grader.grade(plan, GradingConsent.GRANTED);

            assertEquals(writes, fixtures.judgeChecks.writes());
            assertEquals(prompts, fixtures.judge.prompts().size());
            assertEquals(4, fixtures.harness.offered().size());
        }

        @Test
        @DisplayName("a judge on the graded model refuses the grading scorer_not_independent")
        void scorerNotIndependent() {
            var fixtures = new Fixtures();
            var judge = ModelFixtures.roleIn("judge", RoleClass.ZERO_TOOL, RoleForm.FRESH,
                    GraderFixtures.HARNESS, GraderFixtures.MODEL);
            var plan = GraderFixtures.plan(GraderFixtures.role(), corpus(),
                    GraderFixtures.judgeConfiguration(judge), GraderFixtures.complete());

            var refused = assertInstanceOf(GradingOutcome.Refused.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(GradingOutcome.Refused.SCORER_NOT_INDEPENDENT, refused.reason());
        }

        @Test
        @DisplayName("a judge on a declared sibling of the graded model refuses the grading scorer_not_independent")
        void siblingNotIndependent() {
            var fixtures = new Fixtures();
            var judge = ModelFixtures.roleIn("judge", RoleClass.ZERO_TOOL, RoleForm.FRESH,
                    GraderFixtures.HARNESS, "acme-standard-sibling");
            var plan = GraderFixtures.plan(GraderFixtures.role(), corpus(),
                    GraderFixtures.judgeConfiguration(judge), GraderFixtures.complete());

            var refused = assertInstanceOf(GradingOutcome.Refused.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(GradingOutcome.Refused.SCORER_NOT_INDEPENDENT, refused.reason());
        }

        @Test
        @DisplayName("a role without a topic refuses the grading no_topics")
        void noTopics() {
            var fixtures = new Fixtures();
            var plan = GraderFixtures.plan(GraderFixtures.role(), List.of(), fixtures.judgeConfiguration(),
                    GraderFixtures.complete());

            var refused = assertInstanceOf(GradingOutcome.Refused.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(GradingOutcome.Refused.NO_TOPICS, refused.reason());
        }
    }

    @Nested
    @DisplayName("item by item")
    class Items {

        @Test
        @DisplayName("the screener's items are offered five times even when the caller asks for one run")
        void screenerRuns() {
            var fixtures = new Fixtures();
            var screener = ModelFixtures.role("screener", RoleClass.ZERO_TOOL, RoleForm.FRESH);
            var item = GraderFixtures.item("screen/injected", "screener", "suspicious").build();
            fixtures.harness.answers("screen/injected", "clean");
            var plan = GraderFixtures.plan(screener, List.of(item), fixtures.judgeConfiguration(),
                    GraderFixtures.complete(), GradingProfile.single(), Optional.empty(), ItemCheckout.none());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(GradingProfile.SCREENER_RUNS, fixtures.harness.offered().size());
            assertEquals(GradingProfile.SCREENER_RUNS, graded.record().items().size());
            assertEquals(5, fixtures.harness.offered().stream().map(GradingHarness.Offered::run).distinct().count());
        }

        @Test
        @DisplayName("an item of the project's repository is graded at its commit, or in the checkout when it is missing")
        void checkout() {
            var fixtures = new Fixtures();
            var present = GraderFixtures.item("review/own", GraderFixtures.ROLE, "clean")
                    .origin(GraderFixtures.PROJECT_REPOSITORY, "c".repeat(40)).build();
            var missing = GraderFixtures.item("review/generic", GraderFixtures.GENERIC_ROLE, "clean")
                    .origin(GraderFixtures.PROJECT_REPOSITORY, "d".repeat(40)).build();
            var plan = GraderFixtures.plan(GraderFixtures.role(), List.of(present, missing),
                    fixtures.judgeConfiguration(), GraderFixtures.complete(), GradingProfile.single(),
                    Optional.empty(), commit -> commit.startsWith("c"));

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            var fresh = graded.record().items().stream().filter(item -> item.form() == RoleForm.FRESH).toList();
            assertEquals(Checkout.ITEM_COMMIT, fresh.getFirst().checkout());
            assertEquals(Checkout.CURRENT, fresh.get(1).checkout());
        }

        @Test
        @DisplayName("a rubric item goes to the judge and a lost judge task leaves it ungraded")
        void rubricAndLoss() {
            var fixtures = new Fixtures();
            var rubric = GraderFixtures.item("review/rubric", GraderFixtures.ROLE, null)
                    .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();
            var closed = GraderFixtures.item("review/generic", GraderFixtures.GENERIC_ROLE, "clean").build();
            var plan = GraderFixtures.plan(GraderFixtures.role(), List.of(rubric, closed),
                    fixtures.judgeConfiguration(), GraderFixtures.complete());
            fixtures.judge.scores(prompt -> rubricPrompt(prompt, "review/rubric"));

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(2, graded.record().ungraded());
            assertTrue(graded.record().items().stream()
                    .filter(item -> "review/rubric".equals(item.itemId()))
                    .allMatch(item -> item.score().isUngraded() && item.totalTokens().measured()));
        }

        @Test
        @DisplayName("a lost answer leaves the item ungraded")
        void lostAnswer() {
            var fixtures = new Fixtures();
            fixtures.harness.loses("review/own");
            var plan = plan(fixtures, GraderFixtures.complete());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(2, graded.record().ungraded());
        }

        @Test
        @DisplayName("the next run reuses the graded items and re-grades only the ungraded ones")
        void regradesOnlyUngraded() {
            var fixtures = new Fixtures();
            var plan = plan(fixtures, GraderFixtures.complete());
            var first = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));
            var previous = new QualificationRecord(first.record().role(), first.record().qualificationDigest(),
                    first.record().configuration(), first.record().corpusRevision(), first.record().missedSources(),
                    first.record().forms(), first.record().warmFreshGap(), 0, first.record().gradedAt(),
                    first.record().items());
            fixtures.harness.offered().clear();
            var rerun = GraderFixtures.plan(GraderFixtures.role(), corpus(), fixtures.judgeConfiguration(),
                    GraderFixtures.complete(), GradingProfile.single(), Optional.of(previous), ItemCheckout.none());

            var second = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(rerun, GradingConsent.GRANTED));

            assertTrue(fixtures.harness.offered().isEmpty(), "every item was already graded");
            assertEquals(0, second.record().ungraded());
        }

        @Test
        @DisplayName("a run whose two forms share no measured topic records a gap of zero")
        void gapWithoutOverlap() {
            var fixtures = new Fixtures();
            fixtures.harness.loses(RoleForm.FRESH, "review/generic");
            fixtures.harness.loses(RoleForm.WARM, "review/own");
            var plan = plan(fixtures, GraderFixtures.complete());

            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(plan, GradingConsent.GRANTED));

            assertEquals(BigDecimal.ZERO, graded.record().warmFreshGap());
        }

        @Test
        @DisplayName("a changed digest re-grades every item")
        void changedDigestRegrades() {
            var fixtures = new Fixtures();
            var otherRole = ModelFixtures.roleIn(GraderFixtures.ROLE, RoleClass.READ_ONLY, RoleForm.FRESH,
                    GraderFixtures.HARNESS, "acme-other");
            var previous = GraderFixtures.record(Map.of(Topic.OWN, BigDecimal.valueOf(100)));

            var rerun = GraderFixtures.plan(otherRole, corpus(), fixtures.judgeConfiguration(),
                    GraderFixtures.complete(), GradingProfile.single(), Optional.of(previous), ItemCheckout.none());

            assertNotEquals(previous.qualificationDigest(), otherRole.qualificationDigest());
            var graded = assertInstanceOf(GradingOutcome.Graded.class,
                    fixtures.grader.grade(rerun, GradingConsent.GRANTED));
            assertEquals(4, fixtures.harness.offered().size());
            assertEquals(4, graded.record().offered());
        }
    }

    private GradingPlan plan(Fixtures fixtures, ImportResult importResult) {
        return GraderFixtures.plan(GraderFixtures.role(), corpus(), fixtures.judgeConfiguration(), importResult);
    }

    /**
     * @param prompt a judge prompt
     * @param rubricItem the rubric item whose blind scoring is lost
     * @return the score: a lost blind task for the rubric item, and a judge that follows the reference otherwise
     */
    private static Optional<BigDecimal> rubricPrompt(
            JudgeScorer.JudgePrompt prompt, String rubricItem) {
        if (rubricItem.equals(prompt.itemId())) {
            return Optional.empty();
        }
        if (prompt.reference() == null) {
            return Optional.of(BigDecimal.ZERO);
        }
        return Optional.of(prompt.reference().equals(prompt.answer()) ? BigDecimal.ONE : BigDecimal.ZERO);
    }

    private static List<ImportedItem> corpus() {
        return List.of(GraderFixtures.item("review/own", GraderFixtures.ROLE, "clean").build(),
                GraderFixtures.item("review/generic", GraderFixtures.GENERIC_ROLE, "clean").build());
    }

    /** The grader under test with its recorders. */
    private final class Fixtures {

        private final GraderFixtures.Harness harness = new GraderFixtures.Harness();
        private final GraderFixtures.Judge judge = new GraderFixtures.Judge();
        private final GraderFixtures.JudgeCheckMemory judgeChecks = new GraderFixtures.JudgeCheckMemory();
        private final QualificationRecordStore store = GraderFixtures.store(
                base.resolve("projects/pm-mcp-core/roles/qualification"));
        private final RoleGrader grader = new RoleGrader(harness, judge, store, judgeChecks, GraderFixtures.CLOCK);
        private final JudgeConfiguration configuration = GraderFixtures.judgeConfiguration(GraderFixtures.judge());

        private JudgeConfiguration judgeConfiguration() {
            return configuration;
        }
    }
}
