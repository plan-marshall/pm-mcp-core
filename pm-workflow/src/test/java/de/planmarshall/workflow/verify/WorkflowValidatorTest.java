/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The static verifier (PM-IMPL-5, PM-TEST-3): every refusing fixture states its expected rule, state and line in a
 * leading comment and is refused with exactly that violation; the unit that uses every construct passes every check
 * and is the matched negative control; inline units cover the exemptions and the variants of each check.
 */
@DisplayName("WorkflowValidator")
class WorkflowValidatorTest {

    private static final Pattern EXPECTATION =
            Pattern.compile("^#\\s*expect:\\s*([A-Z_]+)\\s+state\\s+(\\S+)\\s+line\\s+(\\d+)");

    private static final List<String> RELEASED = List.of("verify.entry");

    private static final String DONE_TERMINAL = """

            state verify.done terminal done success
                phase verify
            """;

    private final WorkflowValidator validator = new WorkflowValidator(TestPrimitives.registry(), RELEASED);

    static List<String> refusedFixtures() {
        return VerifyFixtures.list(VerifyFixtures.REFUSED);
    }

    private static WorkflowDefinition unit(String scope, String states) {
        return VerifyFixtures.parseSource("inline.pmwf", """
                workflow verify.inline version 1 format 1
                scope %s
                start verify.entry

                %s""".formatted(scope, states));
    }

    private List<Violation> violations(WorkflowDefinition definition) {
        return validator.verify(definition).violations();
    }

    @Nested
    @DisplayName("Fixtures")
    class Fixtures {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.verify.WorkflowValidatorTest#refusedFixtures")
        @DisplayName("Should refuse each fixture with exactly the violation it expects")
        void shouldRefuseWithExactlyTheExpectedViolation(String resource) {
            var expectation = EXPECTATION.matcher(VerifyFixtures.read(resource).lines().findFirst().orElseThrow());
            assertTrue(expectation.find(), () -> resource + " states no expectation");

            var found = violations(VerifyFixtures.parse(resource));

            assertEquals(1, found.size(), () -> resource + " reported " + found);
            var violation = found.getFirst();
            assertAll(resource,
                    () -> assertEquals(VerificationRule.valueOf(expectation.group(1)), violation.rule()),
                    () -> assertEquals(expectation.group(2), violation.stateId()),
                    () -> assertEquals(resource, violation.location().resourceName()),
                    () -> assertEquals(Integer.parseInt(expectation.group(3)), violation.location().line()));
        }

        @Test
        @DisplayName("Should hold a refusing fixture for every check, one for each outcome variant")
        void shouldCoverEveryCheck() {
            var expected = refusedFixtures().stream()
                    .map(resource -> EXPECTATION.matcher(VerifyFixtures.read(resource)))
                    .filter(Matcher::find)
                    .map(matcher -> VerificationRule.valueOf(matcher.group(1)))
                    .toList();

            assertAll(() -> assertEquals(8, expected.size()),
                    () -> assertEquals(EnumSet.allOf(VerificationRule.class), EnumSet.copyOf(expected)));
        }

        @Test
        @DisplayName("Should pass the unit that uses every construct")
        void shouldPassEveryConstruct() {
            var result = validator.verify(VerifyFixtures.parse(VerifyFixtures.PASSING + "/every-construct.pmwf"));

            assertTrue(result.passed(), result::toString);
        }
    }

    @Nested
    @DisplayName("Primitive binding")
    class PrimitiveBinding {

        @Test
        @DisplayName("Should report every unregistered site and check nothing else of it")
        void shouldReportEveryUnregisteredSite() {
            var definition = unit("plan", """
                    state verify.entry
                        phase verify
                        facts test.missing
                        link next proceed ()
                            guard in_worktree and predicate:test.missing
                            do test.missing
                                done -> verify.waiting

                    state verify.waiting
                        phase verify
                        job test.missing
                            done -> verify.composite

                    state verify.composite
                        phase verify
                        composite_job test.missing
                            done -> verify.done
                    """ + DONE_TERMINAL);

            var found = violations(definition);

            assertAll(() -> assertEquals(5, found.size(), found::toString),
                    () -> assertTrue(found.stream().allMatch(v -> v.rule() == VerificationRule.PRIMITIVE_BINDING
                            && v.message().contains(TestPrimitives.MISSING)), found::toString));
        }
    }

    @Nested
    @DisplayName("Parameter equivalence")
    class ParameterEquivalence {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
                "(note: text[1..80], comment: text?);0;",
                "(note: text[1..80], comment: text);0;",
                "(note: text[1..80]);1;omits the free parameter 'comment'",
                "(note: text[1..80], comment: text, extra: text);1;'extra', which the parameter record has not",
                "(note: int[1..80], comment: text);1;but the parameter record declares a text",
                "(note: text, comment: text);1;length 0..2147483647"
        })
        @DisplayName("Should compare names, type and bounds, but not optionality")
        void shouldCompareFreeParameters(String params, int count, String fragment) {
            var definition = unit("plan", """
                    state verify.entry
                        phase verify
                        link submit choose [submit] %s
                            do test.choose
                                chosen -> verify.done
                                declined -> verify.entry
                    """.formatted(params) + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(count, found.size(), found::toString);
            found.forEach(violation -> assertAll(
                    () -> assertEquals(VerificationRule.PARAMETER_EQUIVALENCE, violation.rule()),
                    () -> assertTrue(violation.message().contains(fragment), violation::message)));
        }
    }

    @Nested
    @DisplayName("Outcome coverage")
    class OutcomeCoverage {

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
                "a job state without cancelled;job test.build|succeeded -> verify.done|failed -> verify.done;'cancelled'",
                "an auto state routing an outcome twice;"
                        + "auto test.prepare|done -> verify.done|skipped -> verify.done|done -> verify.done;twice",
                "a link routing one of two outcomes;link next choose (note: text[1..80], comment: text)"
                        + "|do test.choose|chosen -> verify.done;'declined'"
        })
        @DisplayName("Should refuse every gap of the bijection with the outcome enum")
        void shouldRefuseGaps(String description, String body, String fragment) {
            var states = "state verify.entry\nphase verify\n" + body.replace('|', '\n') + "\n";

            var found = violations(unit("plan", states + DONE_TERMINAL));

            assertAll(description, () -> assertEquals(1, found.size(), found::toString),
                    () -> assertEquals(VerificationRule.OUTCOME_COVERAGE, found.getFirst().rule()),
                    () -> assertTrue(found.getFirst().message().contains(fragment), found::toString));
        }
    }

    @Nested
    @DisplayName("Reachability")
    class Reachability {

        @Test
        @DisplayName("Should count the abort terminals as reachable although nothing targets them")
        void shouldExemptAbortTerminals() {
            var definition = unit("plan", """
                    state verify.entry
                        phase verify
                        auto test.prepare
                            done -> verify.done
                            skipped -> verify.done

                    state plan.aborted terminal aborted failure
                        phase aborted

                    state epic.aborted terminal aborted failure
                        phase aborted
                    """ + DONE_TERMINAL);

            assertEquals(List.of(), violations(definition));
        }
    }

    @Nested
    @DisplayName("Exit path")
    class ExitPath {

        private static final String SELF_LINK = """
                state verify.entry
                    phase verify
                    link next proceed ()
                        do test.proceed
                            done -> verify.entry
                """;

        @ParameterizedTest(name = "{0}")
        @CsvSource({"subworkflow, 0", "plan, 1"})
        @DisplayName("Should let a link state of a sub-workflow exit through the implicit step_out")
        void shouldExitThroughStepOut(String scope, int count) {
            assertEquals(count, violations(unit(scope, SELF_LINK)).size());
        }

        @ParameterizedTest(name = "lost declared: {0}")
        @CsvSource({"false, 0", "true, 1"})
        @DisplayName("Should let a task state exit through the default lost route unless it declares lost")
        void shouldExitThroughDefaultLost(boolean declaresLost, int count) {
            var definition = unit("plan", """
                    state verify.entry
                        phase verify
                        job llm change role reviewer
                            approved -> verify.entry
                    """ + (declaresLost ? "        lost -> verify.entry\n" : ""));

            var found = violations(definition);

            assertEquals(count, found.size(), found::toString);
            found.forEach(violation -> assertEquals(VerificationRule.EXIT_PATH, violation.rule()));
        }

        @ParameterizedTest(name = "done -> {0}")
        @CsvSource({"verify.entry, 0", "verify.stuck, 1"})
        @DisplayName("Should require every state of a workspace unit to return to the start state")
        void shouldReturnToStartInWorkspace(String doneTarget, int count) {
            var definition = unit("workspace", """
                    state verify.entry
                        phase setup
                        link next work ()
                            do test.proceed
                                done -> verify.stuck

                    state verify.stuck
                        phase setup
                        auto test.prepare
                            done -> %s
                            skipped -> verify.stuck
                    """.formatted(doneTarget));

            var found = violations(definition);

            assertEquals(count, found.size(), found::toString);
            found.forEach(violation -> assertAll(() -> assertEquals(VerificationRule.EXIT_PATH, violation.rule()),
                    () -> assertEquals("verify.stuck", violation.stateId())));
        }
    }

    @Nested
    @DisplayName("Link identifiers")
    class LinkIdentifiers {

        @ParameterizedTest(name = "{0}")
        @CsvSource({"subworkflow, 1", "plan, 0"})
        @DisplayName("Should refuse a declared step_out only where the engine renders the implicit one")
        void shouldRefuseDeclaredStepOut(String scope, int count) {
            var definition = unit(scope, """
                    state verify.entry
                        phase verify
                        link next step_out ()
                            do test.proceed
                                done -> verify.done
                    """ + DONE_TERMINAL);

            var found = violations(definition);

            assertEquals(count, found.size(), found::toString);
            found.forEach(violation -> assertEquals(VerificationRule.LINK_ID_UNIQUE, violation.rule()));
        }
    }

    @Nested
    @DisplayName("Released states")
    class ReleasedStates {

        private final WorkflowValidator retiring = new WorkflowValidator(TestPrimitives.registry(),
                List.of("verify.entry", "verify.old"));

        private List<Violation> verify(String migration) {
            return retiring.verify(VerifyFixtures.parseSource("inline.pmwf", """
                    workflow verify.inline version 1 format 1
                    scope plan
                    start verify.entry
                    %s
                    state verify.entry
                        phase verify
                        auto test.prepare
                            done -> verify.done
                            skipped -> verify.done
                    """.formatted(migration) + DONE_TERMINAL)).violations();
        }

        @Test
        @DisplayName("Should accept a released name migrated to a state")
        void shouldAcceptMigrationToLiveState() {
            assertEquals(List.of(), verify("migrate verify.old -> verify.entry\n"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = ';', quoteCharacter = '"', value = {
                "migrate verify.old -> verify.gone;4;targets 'verify.gone'",
                "\"# no migration\";1;no migrate declaration names it"
        })
        @DisplayName("Should refuse a released name that is neither a state nor migrated to one")
        void shouldRefuseDroppedName(String migration, int line, String fragment) {
            var found = verify(migration + "\n");

            assertEquals(1, found.size(), found::toString);
            var violation = found.getFirst();
            assertAll(() -> assertEquals(VerificationRule.RELEASED_STATE, violation.rule()),
                    () -> assertEquals("verify.old", violation.stateId()),
                    () -> assertEquals(line, violation.location().line()),
                    () -> assertTrue(violation.message().contains(fragment), violation::message));
        }
    }

    @Nested
    @DisplayName("Result")
    class Result {

        @Test
        @DisplayName("Should report every defect of a unit, ordered by state and then by rule")
        void shouldReportEveryDefectInOrder() {
            var definition = unit("plan", """
                    state verify.entry
                        phase verify
                        auto test.missing
                            done -> verify.done

                    state verify.orphan
                        phase verify
                        link next proceed ()
                            do test.proceed
                                done -> verify.orphan
                        link alt proceed ()
                            do test.proceed
                                done -> verify.orphan
                    """ + DONE_TERMINAL);
            var withRetiredName = new WorkflowValidator(TestPrimitives.registry(),
                    List.of("verify.retired", "verify.entry"));

            var found = withRetiredName.verify(definition).violations();

            var stateAndRule = found.stream().map(violation -> violation.stateId() + " " + violation.rule()).toList();
            assertEquals(List.of("verify.entry PRIMITIVE_BINDING", "verify.orphan REACHABILITY",
                            "verify.orphan EXIT_PATH", "verify.orphan LINK_ID_UNIQUE", "verify.retired RELEASED_STATE"),
                    stateAndRule);
        }

        @Test
        @DisplayName("Should refuse a null argument")
        void shouldRefuseNull() {
            var registry = TestPrimitives.registry();
            assertAll(() -> assertThrows(NullPointerException.class, () -> new WorkflowValidator(null, RELEASED)),
                    () -> assertThrows(NullPointerException.class, () -> new WorkflowValidator(registry, null)),
                    () -> assertThrows(NullPointerException.class, () -> validator.verify(null)));
        }
    }
}
