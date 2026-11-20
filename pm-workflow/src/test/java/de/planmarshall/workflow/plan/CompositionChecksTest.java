/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.workflow.model.CompositionRefusal;
import de.planmarshall.workflow.model.ModelIdentity;
import de.planmarshall.workflow.model.SkillCatalogue;
import de.planmarshall.workflow.verify.InvariantFixtures;

/**
 * Rules 13 and 14 at composition (Workflow DSL § Static AST Verification Rules): a missing role, a role of the wrong
 * class, a quorum on one model by model identity and an unresolved skill each refuse a composition with its reason, at
 * {@code start}, at a server event and at an operator event.
 */
@EnableGeneratorController
@DisplayName("Rules 13 and 14 at composition")
class CompositionChecksTest {

    private static final InstancePosition AT_INIT = InstancePosition.of("phase-1-init", "init.begin");

    /** @return a role of the given class on the given model and harness */
    private static EffectiveRole role(RoleClass roleClass, String harness, String model, String weightsIdentity) {
        var artifact = new RoleArtifact(1, "role", "A role written for the test.", roleClass,
                List.of(), harness, model, new EffortValue("high"),
                RoleForm.FRESH, 1,
                List.of("skill://pm/role/role/SKILL.md"), null, 0, null,
                new RoleArtifact.Questions(0, 0),
                Map.of());
        return new EffectiveRole(artifact, harness, model, new EffortValue("high"), List.of(),
                "1.0", weightsIdentity, new RoleArtifact.Questions(0, 0),
                Map.of(), 400_000, null);
    }

    /** @return a resolver that defines the given roles and refuses every other */
    private static RoleResolver resolver(Map<String, EffectiveRole> roles) {
        return name -> roles.containsKey(name) ? new RoleResolution.Defined(roles.get(name))
                : new RoleResolution.Undefined(name, null, "absent from the effective role set");
    }

    private static CompositionChecks checks(RoleResolver roles, SkillCatalogue skills) {
        return CompositionChecks.of(roles, skills);
    }

    /** The position a server or operator event composes at: the lane is known and the plan sits in the outline phase. */
    private static final InstancePosition AT_LANE = InstancePosition.of("phase-3-outline", "outline.deep");

    /** The fixture file of the execute phase, the one the skill-clause mutation changes. */
    private static final String EXECUTE_FILE = "execute.pmwf";

    private static CompositionResult compose(CompositionEvent event, CompositionChecks checks) {
        return compose(event, checks, List.of());
    }

    private static CompositionResult compose(CompositionEvent event, CompositionChecks checks,
            List<InvariantFixtures.Mutation> mutations) {
        var rules = ((CompositionRules.Loaded) CompositionRules.shipped()).rules();
        var start = PlanFixtures.mergedRequest(CompositionEvent.START, PlanFixtures.facts().build(), null, AT_INIT,
                mutations);
        if (event == CompositionEvent.START) {
            return PlanFixtures.mergedComposer(rules, checks).compose(start);
        }
        var previous = ((CompositionResult.Composed) PlanFixtures.mergedComposer(rules, CompositionChecks.NONE)
                .compose(start)).instance();
        var request = PlanFixtures.mergedRequest(event, PlanFixtures.facts().build(), previous, AT_LANE, mutations);
        return PlanFixtures.mergedComposer(rules, checks).compose(request);
    }

    @Nested
    @DisplayName("Rule 13")
    class Roles {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("a missing role refuses the composition with role_undefined")
        void missingRole(CompositionEvent event) {
            var refused = assertInstanceOf(CompositionResult.Refused.class, compose(event,
                    checks(resolver(Map.of()), uri -> true)), "a missing role refuses the composition");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.ROLE_UNDEFINED, refused.refusal(),
                            "the refusal names role_undefined"),
                    () -> assertTrue(refused.violations().getFirst().contains("role_undefined"),
                            "the violation carries the reason"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("a role of the wrong class refuses the composition with class_mismatch")
        void wrongClass(CompositionEvent event) {
            var implementer = resolver(Map.of("implementer",
                    role(RoleClass.ZERO_TOOL, "claude", "sonnet", null),
                    "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "architect", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "reviewer", role(RoleClass.READ_ONLY, "claude", "opus", null)));
            var refused = assertInstanceOf(CompositionResult.Refused.class,
                    compose(event, checks(implementer, uri -> true)),
                    "a zero-tool role in a model job is refused");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.CLASS_MISMATCH, refused.refusal(),
                            "the refusal names class_mismatch"),
                    () -> assertTrue(refused.violations().getFirst().contains("class_mismatch"),
                            "the violation carries the reason"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("a quorum on one model by model identity refuses the composition with quorum_not_diverse")
        void quorumOnOneModel(CompositionEvent event) {
            var sameModel = resolver(Map.of("implementer",
                    role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "architect", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "reviewer", role(RoleClass.READ_ONLY, "claude", "openrouter/claude/sonnet:free", null)));
            var refused = assertInstanceOf(CompositionResult.Refused.class,
                    compose(event, checks(sameModel, uri -> true)),
                    "a quorum on one model is refused");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.QUORUM_NOT_DIVERSE, refused.refusal(),
                            "the refusal names quorum_not_diverse"),
                    () -> assertTrue(refused.violations().getFirst().contains("architect"),
                            "the refusal names the first role"),
                    () -> assertTrue(refused.violations().getFirst().contains("reviewer"),
                            "the refusal names the second role"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("matched control: two different models compose at every event")
        void differentModelsCompose(CompositionEvent event) {
            assertInstanceOf(CompositionResult.Composed.class,
                    compose(event, checks(allRoles(), uri -> true)),
                    "a quorum of two different models composes");
        }
    }

    @Nested
    @DisplayName("Rule 14")
    class Skills {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("an unresolved skill refuses the composition with skill_unresolved")
        void unresolvedSkill(CompositionEvent event) {
            var refused = assertInstanceOf(CompositionResult.Refused.class,
                    compose(event, checks(allRoles(), uri -> false), List.of(skillClause())),
                    "an unresolved skill refuses the composition");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.SKILL_UNRESOLVED, refused.refusal(),
                            "the refusal names skill_unresolved"),
                    () -> assertTrue(refused.violations().getFirst().contains("pm/project/x"),
                            "the refusal names the skill"),
                    () -> assertTrue(refused.violations().getFirst().contains("execute.envelope"),
                            "the refusal names the state that carries the skill"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("matched control: a catalogue that resolves the clause composes")
        void resolvableSkillComposes(CompositionEvent event) {
            assertInstanceOf(CompositionResult.Composed.class,
                    compose(event, checks(allRoles(), uri -> true), List.of(skillClause())),
                    "a resolvable catalogue composes the version");
        }
    }

    /**
     * @return a named change of the execute phase that gives its model job a {@code skill} clause of the project; the
     *         smallest fixture that carries one, as no passing fixture states a skill
     */
    private static InvariantFixtures.Mutation skillClause() {
        return new InvariantFixtures.Mutation("a skill clause", EXECUTE_FILE,
                "state execute.envelope\n    job llm change role implementer",
                "state execute.envelope\n    skill pm/project/x\n    job llm change role implementer");
    }

    /** @return a resolver that defines every role of the invariant fixtures */
    private static RoleResolver allRoles() {
        return resolver(Map.of("implementer",
                role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "architect", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "reviewer", role(RoleClass.READ_ONLY, "claude", "opus", null)));
    }

    /**
     * @param architectModel the identifier of the quorum's first role
     * @param reviewerModel  the identifier of the quorum's second role
     * @return a resolver that defines every role of the invariant fixtures and the quorum's two on the given identifiers
     */
    private static RoleResolver resolverWithPair(String architectModel, String reviewerModel) {
        return resolver(Map.of("implementer",
                role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "architect", role(RoleClass.READ_ONLY, "claude", architectModel, null),
                "reviewer", role(RoleClass.READ_ONLY, "claude", reviewerModel, null)));
    }

    @Test
    @DisplayName("the model-check refusals carry the reasons' wire names")
    void refusalWireNamesMatchTheReasons() {
        assertAll(
                () -> assertEquals(CompositionRefusal.Reason.ROLE_UNDEFINED.wireName(),
                        CompositionResult.Refusal.ROLE_UNDEFINED.wireName(), "role_undefined is one spelling"),
                () -> assertEquals(CompositionRefusal.Reason.CLASS_MISMATCH.wireName(),
                        CompositionResult.Refusal.CLASS_MISMATCH.wireName(), "class_mismatch is one spelling"),
                () -> assertEquals(CompositionRefusal.Reason.QUORUM_INVALID.wireName(),
                        CompositionResult.Refusal.QUORUM_INVALID.wireName(), "quorum_invalid is one spelling"),
                () -> assertEquals(CompositionRefusal.Reason.QUORUM_NOT_DIVERSE.wireName(),
                        CompositionResult.Refusal.QUORUM_NOT_DIVERSE.wireName(), "quorum_not_diverse is one spelling"),
                () -> assertEquals(CompositionRefusal.Reason.SKILL_UNRESOLVED.wireName(),
                        CompositionResult.Refusal.SKILL_UNRESOLVED.wireName(), "skill_unresolved is one spelling"),
                () -> assertEquals(2, CompositionResult.Refusal.values().length
                        - CompositionRefusal.Reason.values().length,
                        "the two refusals without a reason are the only others"));
    }

    @Test
    @DisplayName("at start a refused composition returns the refusal and no version")
    void refusedStartCreatesNothing() {
        var result = compose(CompositionEvent.START, checks(resolver(Map.of()), uri -> true));

        assertAll(
                () -> assertInstanceOf(CompositionResult.Refused.class, result,
                        "the start composition is refused as a value"),
                () -> assertEquals(CompositionResult.Refusal.ROLE_UNDEFINED,
                        ((CompositionResult.Refused) result).refusal(),
                        "the caller of start has no version to create the plan from"));
    }

    @Test
    @DisplayName("no checks wired composes, as the rules are scoped to a plan's composition")
    void noChecksComposes() {
        assertInstanceOf(CompositionResult.Composed.class,
                compose(CompositionEvent.START, CompositionChecks.NONE),
                "a composition without a project checks no role");
    }

    @Test
    @DisplayName("the quorum check reads the adjudication unit although no call site calls it")
    void quorumIsReadFromTheAdjudicationUnit() {
        var sameModel = resolver(Map.of("implementer",
                role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "architect", role(RoleClass.READ_ONLY, "claude", "qwen3:32b", null),
                "reviewer", role(RoleClass.READ_ONLY, "claude", "qwen3:8b", null)));
        var refused = assertInstanceOf(CompositionResult.Refused.class,
                compose(CompositionEvent.START, checks(sameModel, uri -> true)),
                "the adjudication unit's quorum is checked");

        assertEquals(CompositionResult.Refusal.QUORUM_NOT_DIVERSE, refused.refusal(),
                "two tags of one model name count as one model");
    }

    @Test
    @DisplayName("a quorum of a role that is not read_only is quorum_invalid")
    void quorumInvalid() {
        var writer = resolver(Map.of("implementer",
                role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "architect", role(RoleClass.WRITER, "claude", "sonnet", null),
                "reviewer", role(RoleClass.READ_ONLY, "claude", "opus", null)));
        var refused = assertInstanceOf(CompositionResult.Refused.class,
                compose(CompositionEvent.START, checks(writer, uri -> true)),
                "a writer role in a quorum is refused");

        assertEquals(CompositionResult.Refusal.QUORUM_INVALID, refused.refusal(),
                "a writer role in a quorum is quorum_invalid");
    }

    @Test
    @DisplayName("a composition reads no catalogue when the checks name none")
    void answeredAbsence() {
        assertAll(
                () -> assertEquals(Optional.empty(),
                        Optional.ofNullable(CompositionChecks.NONE.roles()),
                        "the checks wire no role resolver"),
                () -> assertEquals(Optional.empty(),
                        Optional.ofNullable(CompositionChecks.NONE.skills()),
                        "the checks wire no skill catalogue"));
    }

    @Nested
    @DisplayName("the model identity of a quorum (PM-WATCH-DISP-67)")
    class ModelIdentities {

        @ParameterizedTest(name = "{0} and {1} refuse the composition as one model")
        @CsvSource({
                "sonnet, openrouter/sonnet",
                "sonnet, openrouter/anthropic/sonnet",
                "sonnet, sonnet:free",
                "Sonnet, sonnet",
                "qwen3:32b, qwen3:8b"})
        @DisplayName("two listed roles of one model refuse the composition with quorum_not_diverse")
        void oneModelRefusesTheQuorum(String first, String second) {
            var refused = assertInstanceOf(CompositionResult.Refused.class,
                    compose(CompositionEvent.OUTLINE, checks(resolverWithPair(first, second), uri -> true)),
                    "a quorum of two identifiers of one model is refused");

            assertEquals(CompositionResult.Refusal.QUORUM_NOT_DIVERSE, refused.refusal(),
                    "the refusal names quorum_not_diverse");
        }

        @Test
        @DisplayName("matched control: two different models compose at the same event")
        void differentModelsCompose() {
            assertEquals(CompositionResult.Composed.class,
                    compose(CompositionEvent.OUTLINE,
                            checks(resolverWithPair("sonnet", "opus"), uri -> true)).getClass(),
                    "a quorum of two models composes");
        }

        @ParameterizedTest(name = "{0} and {1} are one model: {2}")
        @CsvSource({
                "sonnet, openrouter/sonnet, sonnet",
                "sonnet, openrouter/anthropic/sonnet, sonnet",
                "sonnet, sonnet:free, sonnet",
                "Sonnet, sonnet, sonnet",
                "qwen3:32b, qwen3:8b, qwen3"})
        @DisplayName("two identifiers of one model share its identity")
        void oneModelByIdentifier(String first, String second, String identity) {
            assertEquals(Optional.of(identity),
                    ModelIdentity.shared(role(RoleClass.READ_ONLY, "claude", first, null),
                            role(RoleClass.READ_ONLY, "claude", second, null)),
                    "the two identifiers name one model");
        }

        @Test
        @DisplayName("two roles of equal weights identities are one model under different names")
        void equalWeightsIdentities() {
            assertEquals(Optional.of("weights-1"),
                    ModelIdentity.shared(role(RoleClass.READ_ONLY, "claude", "sonnet", "weights-1"),
                            role(RoleClass.READ_ONLY, "other", "sonnet-v2", "weights-1")),
                    "equal weights identities name one model");
        }

        @Test
        @DisplayName("matched control: two different models name no shared identity")
        void differentModels() {
            assertAll(
                    () -> assertEquals(Optional.empty(),
                            ModelIdentity.shared(role(RoleClass.READ_ONLY, "claude", "sonnet", "weights-a"),
                                    role(RoleClass.READ_ONLY, "claude", "opus", "weights-b")),
                            "two models on one harness name different models"),
                    () -> assertEquals(Optional.empty(),
                            ModelIdentity.shared(role(RoleClass.READ_ONLY, "vendor-a", "shared-name", "weights-a"),
                                    role(RoleClass.READ_ONLY, "vendor-b", "shared-name", "weights-b")),
                            "one identifier stem from two vendors names different models"));
        }
    }
}
