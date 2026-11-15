/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleSet;
import de.planmarshall.core.role.RoleSetLoader;
import de.planmarshall.core.role.RoleSets;

/**
 * The proposal {@code roles.default} applies to the catalogue (Model Work § Default Role Assignment, State Catalogue §
 * Role Assignment): the default's tier on the preferred harness, the model the harness maps it to, the narrowed effort,
 * a role the default cannot meet reported without a value, and the steward outside the assignment.
 */
@EnableGeneratorController
@DisplayName("Role assignment proposal")
class RoleAssignmentProposalTest {

    private static final String HARNESS = "p" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FALLBACK_HARNESS = "q" + Generators.letterStrings(3, 8).next()
            .toLowerCase(Locale.ROOT);
    private static final String STANDARD_MODEL = "m" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FRONTIER_MODEL = "r" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FAST_MODEL = "f" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String ROLE_DESCRIPTION = Generators.letterStrings(10, 40).next();
    private static final int BUDGET_FLOOR = Generators.integers(1, 1_000_000).next();

    private static final String DOCUMENT = """
            {"format_version":1,"roles":{
              "implement":{"tier":"standard","effort":"high","preferred_harness":"%s",
                           "advisory":"Implements the tasks of a plan."},
              "outline":{"tier":"standard","effort":"max","preferred_harness":"%s",
                         "advisory":"Outlines the solution."},
              "screener":{"tier":"fast","effort":"high","preferred_harness":"%s",
                          "advisory":"Screens untrusted text."},
              "steward":{"tier":"standard","effort":"medium","preferred_harness":"%s",
                         "advisory":"Advises the operator."}}}"""
            .formatted(HARNESS, HARNESS, HARNESS, HARNESS);

    private static byte[] artifact(String name, RoleClass roleClass) {
        var tools = roleClass == RoleClass.WRITER
                ? "\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\",\"pm_build\",\"pm_lsp\""
                : "\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\",\"pm_lsp\"";
        return """
                {"format_version":1,"name":"%s","description":"%s","class":"%s","tools":[%s],
                 "harness":"%s","model":"%s","effort":null,"form":"fresh","count":2,
                 "skills":["skill://pm/role/%s/SKILL.md"],"budget_floor_seconds":300}"""
                .formatted(name, ROLE_DESCRIPTION, roleClass.wireName(), tools, HARNESS, STANDARD_MODEL, name)
                .getBytes(UTF_8);
    }

    private static RoleSet roleSet() {
        return RoleSetLoader.load(RoleSets.of(Map.of(
                "implement", new RoleSets.ShippedRole(artifact("implement", RoleClass.READ_ONLY)),
                "outline", new RoleSets.ShippedRole(artifact("outline", RoleClass.READ_ONLY)),
                "screener", new RoleSets.ShippedRole(artifact("screener", RoleClass.READ_ONLY)),
                "steward", new RoleSets.ShippedRole(artifact("steward", RoleClass.READ_ONLY)))));
    }

    private static DefaultAssignment defaults() {
        return DefaultAssignment.read(Path.of("default-assignment.json"), DOCUMENT.getBytes(UTF_8));
    }

    private static HarnessProfile profile() {
        return new HarnessProfile(HARNESS, true, false, Set.of(),
                Map.of(RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY, RoleClass.WRITER,
                        HarnessProfile.ToolSet.ENFORCED),
                Map.of(STANDARD_MODEL, List.of("low", "medium", "high"), FRONTIER_MODEL, List.of(),
                        FAST_MODEL, List.of()),
                Map.of("frontier", FRONTIER_MODEL, "standard", STANDARD_MODEL, "fast", FAST_MODEL),
                BUDGET_FLOOR, null, new HarnessProfile.Resumability(false, null, false, null), "ask_user");
    }

    private static Optional<ModelCatalogue> catalogue() {
        return Optional.of(new ModelCatalogue(
                Map.of(HARNESS, Set.of(STANDARD_MODEL, FRONTIER_MODEL, FAST_MODEL))));
    }

    private static Map<String, RoleAssignmentProposal.Proposal> propose(Optional<ModelCatalogue> catalogue) {
        return RoleAssignmentProposal.propose(roleSet(), defaults(),
                List.of(new RoleAssignmentProposal.Harness(HARNESS, profile())), catalogue.orElse(null));
    }

    @Test
    @DisplayName("the steward is not among the roles the proposal assigns")
    void stewardOutside() {
        var proposals = propose(catalogue());

        assertAll(
                () -> assertFalse(proposals.containsKey("steward"), "the steward is not proposed"),
                () -> assertTrue(proposals.containsKey("implement"), "the implement role is proposed"),
                () -> assertTrue(roleSet().effective().containsKey("steward"),
                        "the steward is an effective role"),
                () -> assertFalse(roleSet().assignable().containsKey("steward"),
                        "the steward is not assignable"));
    }

    @Test
    @DisplayName("the default's tier is mapped to the harness's model, with the effort and the advisory beside it")
    void proposal() {
        var proposal = propose(catalogue()).get("implement");

        var value = proposal.assignment();
        assertAll(
                () -> assertEquals("standard", proposal.tier(), "the proposal keeps the default tier"),
                () -> assertEquals("Implements the tasks of a plan.", proposal.advisory(),
                        "the proposal keeps the advisory"),
                () -> assertNull(proposal.unresolved(), "the proposal has no unresolved reason"),
                () -> assertEquals(HARNESS, value.harness(), "the value uses the preferred harness"),
                () -> assertEquals(STANDARD_MODEL, value.model(), "the tier maps to the standard model"),
                () -> assertEquals(EffortSetting.VALUE, value.effort(), "the value carries an effort setting"),
                () -> assertEquals(new EffortValue("high"), value.effortValue(),
                        "the value carries the high effort"),
                () -> assertEquals(new EffortValue("high"), value.assignment().effort(),
                        "the assignment carries the high effort"));
    }

    @Test
    @DisplayName("an effort the harness does not list is narrowed to the greatest one it accepts")
    void narrowsEffort() {
        var outline = propose(catalogue()).get("outline").assignment();

        assertAll(
                () -> assertEquals(EffortSetting.VALUE, outline.effort(), "the outline role keeps an effort value"),
                () -> assertEquals(new EffortValue("high"), outline.effortValue(),
                        "max is narrowed to the high effort"));
    }

    @Test
    @DisplayName("a model without effort values runs with no effort flag")
    void noEffortValues() {
        var screener = propose(catalogue()).get("screener").assignment();

        assertAll(
                () -> assertEquals(FAST_MODEL, screener.model(), "the screener uses the fast model"),
                () -> assertEquals(EffortSetting.DEFAULT, screener.effort(),
                        "a model without effort gets DEFAULT"),
                () -> assertNull(screener.effortValue(), "no effort value is set"));
    }

    @Test
    @DisplayName("a model the catalogue does not allow is reported without a value")
    void modelNotAllowed() {
        var proposal = propose(Optional.of(new ModelCatalogue(Map.of(HARNESS, Set.of(FAST_MODEL)))))
                .get("implement");

        assertAll(
                () -> assertNull(proposal.assignment(), "a disallowed model has no assignment"),
                () -> assertTrue(proposal.unresolved().contains("does not allow"),
                        "the reason names the catalogue refusal"),
                () -> assertEquals("Implements the tasks of a plan.", proposal.advisory(),
                        "the advisory is still reported"));
    }

    @Test
    @DisplayName("a missing catalogue is reported as such, not as an allowance")
    void absentCatalogue() {
        var proposal = propose(Optional.empty()).get("implement");

        assertAll(
                () -> assertNull(proposal.assignment(), "a missing catalogue has no assignment"),
                () -> assertEquals("no effective model catalogue exists yet", proposal.unresolved(),
                        "the reason names the missing catalogue"));
    }

    @Test
    @DisplayName("a role the default names but no enrolled harness runs is reported without a value")
    void noHarness() {
        var proposals = RoleAssignmentProposal.propose(roleSet(), defaults(), List.of(), catalogue().orElse(null));

        assertAll(
                () -> assertNull(proposals.get("implement").assignment(),
                        "no enrolled harness has no assignment"),
                () -> assertEquals("no enrolled harness runs the role's class",
                        proposals.get("implement").unresolved(), "the reason names the missing harness"));
    }

    @Test
    @DisplayName("a role the default does not name is reported with the reason and no value")
    void roleWithoutDefault() {
        var set = RoleSetLoader.load(RoleSets.of(Map.of(
                "implement", new RoleSets.ShippedRole(artifact("implement", RoleClass.READ_ONLY)),
                "replan", new RoleSets.ShippedRole(artifact("replan", RoleClass.READ_ONLY)))));

        var proposals = RoleAssignmentProposal.propose(set, defaults(),
                List.of(new RoleAssignmentProposal.Harness(HARNESS, profile())), catalogue().orElse(null));

        assertAll(
                () -> assertNull(proposals.get("replan").assignment(), "a role without default has no value"),
                () -> assertTrue(proposals.get("replan").unresolved().contains("names no such role"),
                        "the reason names the absent default"),
                () -> assertNotNull(proposals.get("implement").assignment(),
                        "the defaulted role still has a value"));
    }

    @Test
    @DisplayName("the preferred harness wins where it is enrolled, else the first enrolled harness whose profile runs the class")
    void harnessResolution() {
        var first = new RoleAssignmentProposal.Harness(HARNESS, profile());
        var second = new RoleAssignmentProposal.Harness(FALLBACK_HARNESS, profile());
        var both = Optional.of(new ModelCatalogue(Map.of(HARNESS, Set.of(STANDARD_MODEL),
                FALLBACK_HARNESS, Set.of(STANDARD_MODEL))));
        var defaults = DefaultAssignment.read(Path.of("default-assignment.json"), """
                {"format_version":1,"roles":{"implement":{"tier":"standard","effort":"high",
                 "preferred_harness":"%s","advisory":"Implements."}}}""".formatted(FALLBACK_HARNESS).getBytes(UTF_8));

        var preferred = RoleAssignmentProposal.propose(roleSet(), defaults, List.of(first, second),
                both.orElse(null));
        var fallback = RoleAssignmentProposal.propose(roleSet(), defaults, List.of(first), both.orElse(null));

        assertAll(
                () -> assertEquals(FALLBACK_HARNESS, preferred.get("implement").assignment().harness(),
                        "the unenrolled preferred harness is skipped"),
                () -> assertEquals(HARNESS, fallback.get("implement").assignment().harness(),
                        "the enrolled harness is the fallback"));
    }
}
