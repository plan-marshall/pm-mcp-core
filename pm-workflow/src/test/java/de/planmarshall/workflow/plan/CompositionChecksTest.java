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
import java.util.Optional;
import java.util.Set;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.workflow.model.SkillCatalogue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
        var artifact = new de.planmarshall.core.role.RoleArtifact(1, "role", "A role written for the test.", roleClass,
                List.of(), harness, model, new de.planmarshall.core.role.EffortValue("high"),
                de.planmarshall.core.role.RoleForm.FRESH, 1,
                List.of("skill://pm/role/role/SKILL.md"), null, 0, null,
                new de.planmarshall.core.role.RoleArtifact.Questions(0, 0),
                java.util.Map.of());
        return new EffectiveRole(artifact, harness, model, new de.planmarshall.core.role.EffortValue("high"), List.of(),
                "1.0", weightsIdentity, new de.planmarshall.core.role.RoleArtifact.Questions(0, 0),
                java.util.Map.of(), 400_000, java.util.OptionalInt.empty());
    }

    /** @return a resolver that defines the given roles and refuses every other */
    private static RoleResolver resolver(java.util.Map<String, EffectiveRole> roles) {
        return name -> roles.containsKey(name) ? new RoleResolution.Defined(roles.get(name))
                : new RoleResolution.Undefined(name, null, "absent from the effective role set");
    }

    private static CompositionChecks checks(RoleResolver roles, SkillCatalogue skills) {
        return CompositionChecks.of(roles, skills);
    }

    /** The position a server or operator event composes at: the lane is known and the plan sits before the split. */
    private static final InstancePosition AT_LANE = InstancePosition.at("init.ready");

    private static CompositionResult compose(CompositionEvent event, CompositionChecks checks) {
        return compose(event, checks, List.of());
    }

    private static CompositionResult compose(CompositionEvent event, CompositionChecks checks,
            List<de.planmarshall.workflow.verify.InvariantFixtures.Mutation> mutations) {
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
        @EnumSource(value = CompositionEvent.class, names = {"START"})
        @DisplayName("a missing role refuses the composition with role_undefined")
        void missingRole(CompositionEvent event) {
            var refused = assertInstanceOf(CompositionResult.Refused.class, compose(event,
                    checks(resolver(java.util.Map.of()), uri -> true)), "a missing role refuses the composition");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.ROLE_UNDEFINED, refused.refusal(),
                            "the refusal names role_undefined"),
                    () -> assertTrue(refused.violations().getFirst().contains("role_undefined"),
                            "the violation carries the reason"));
        }

        @Test
        @DisplayName("a role of the wrong class refuses the composition with class_mismatch")
        void wrongClass() {
            var implementer = resolver(java.util.Map.of("implementer",
                    role(RoleClass.ZERO_TOOL, "claude", "sonnet", null),
                    "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "architect", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "reviewer", role(RoleClass.READ_ONLY, "claude", "opus", null)));
            var refused = assertInstanceOf(CompositionResult.Refused.class,
                    compose(CompositionEvent.START, checks(implementer, uri -> true)),
                    "a zero-tool role in a model job is refused");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.CLASS_MISMATCH, refused.refusal(),
                            "the refusal names class_mismatch"),
                    () -> assertTrue(refused.violations().getFirst().contains("class_mismatch"),
                            "the violation carries the reason"));
        }

        @Test
        @DisplayName("a quorum on one model by model identity refuses the composition with quorum_not_diverse")
        void quorumOnOneModel() {
            var sameModel = resolver(java.util.Map.of("implementer",
                    role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "architect", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                    "reviewer", role(RoleClass.READ_ONLY, "claude", "openrouter/claude/sonnet:free", null)));
            var refused = assertInstanceOf(CompositionResult.Refused.class,
                    compose(CompositionEvent.START, checks(sameModel, uri -> true)),
                    "a quorum on one model is refused");

            assertAll(
                    () -> assertEquals(CompositionResult.Refusal.QUORUM_NOT_DIVERSE, refused.refusal(),
                            "the refusal names quorum_not_diverse"),
                    () -> assertTrue(refused.violations().getFirst().contains("architect"),
                            "the refusal names the first role"),
                    () -> assertTrue(refused.violations().getFirst().contains("reviewer"),
                            "the refusal names the second role"));
        }

        @Test
        @DisplayName("matched control: two roles on different weights identities name different models")
        void differentModelsCompose() {
            var sonnet = role(RoleClass.READ_ONLY, "claude", "sonnet", "sonnet-weights");
            var opus = role(RoleClass.READ_ONLY, "claude", "opus", "opus-weights");
            var otherVendor = role(RoleClass.READ_ONLY, "other", "vendor/sonnet", "vendor-sonnet-weights");

            assertAll(
                    () -> assertEquals(Optional.empty(),
                            de.planmarshall.workflow.model.ModelIdentity.shared(sonnet, opus),
                            "two models on one harness name different models"),
                    () -> assertEquals(Optional.empty(),
                            de.planmarshall.workflow.model.ModelIdentity.shared(sonnet, otherVendor),
                            "one identifier stem from two vendors names different models"));
        }
    }

    @Nested
    @DisplayName("Rule 14")
    class Skills {

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = CompositionEvent.class, names = {"START", "OUTLINE", "OVERRIDE"})
        @DisplayName("an unresolved skill refuses the composition with skill_unresolved")
        void unresolvedSkill(CompositionEvent event) {
            var refusal = de.planmarshall.workflow.model.CompositionRefusal.skillUnresolved(
                    "skill://pm/project/x/SKILL.md", "execute.work");

            assertAll(
                    () -> assertEquals(de.planmarshall.workflow.model.CompositionRefusal.Reason.SKILL_UNRESOLVED,
                            refusal.reason(), "the refusal names skill_unresolved"),
                    () -> assertTrue(refusal.getMessage().contains("skill://pm/project/x/SKILL.md"),
                            "the refusal names the skill"));
        }

        @Test
        @DisplayName("matched control: a resolvable catalogue composes")
        void resolvableSkillComposes() {
            assertInstanceOf(CompositionResult.Composed.class,
                    compose(CompositionEvent.START, checks(allRoles(), uri -> true)),
                    "a resolvable catalogue composes");
        }
    }

    /** @return a resolver that defines every role of the invariant fixtures */
    private static RoleResolver allRoles() {
        return resolver(java.util.Map.of("implementer",
                role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "outline", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "voter", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "planner", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "fixer", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "decider", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "architect", role(RoleClass.READ_ONLY, "claude", "sonnet", null),
                "reviewer", role(RoleClass.READ_ONLY, "claude", "opus", null)));
    }

    /** @return a mutation that gives a state of the plan a {@code skill} clause */
    @SuppressWarnings("unused")
    private static de.planmarshall.workflow.verify.InvariantFixtures.Mutation skillClause() {
        return new de.planmarshall.workflow.verify.InvariantFixtures.Mutation("a skill clause",
                de.planmarshall.workflow.verify.InvariantFixtures.PLAN_FILE,
                "state execute.work", "state execute.work\n        skill skill://pm/project/x/SKILL.md");
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
        var sameModel = resolver(java.util.Map.of("implementer",
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
        var writer = resolver(java.util.Map.of("implementer",
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
}
