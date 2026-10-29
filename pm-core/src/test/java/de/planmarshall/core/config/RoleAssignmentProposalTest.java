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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
@DisplayName("Role assignment proposal")
class RoleAssignmentProposalTest {

    private static final String DOCUMENT = """
            {"format_version":1,"roles":{
              "implement":{"tier":"standard","effort":"high","preferred_harness":"acme-cli",
                           "advisory":"Implements the tasks of a plan."},
              "outline":{"tier":"standard","effort":"max","preferred_harness":"acme-cli",
                         "advisory":"Outlines the solution."},
              "screener":{"tier":"fast","effort":"high","preferred_harness":"acme-cli",
                          "advisory":"Screens untrusted text."},
              "steward":{"tier":"standard","effort":"medium","preferred_harness":"acme-cli",
                         "advisory":"Advises the operator."}}}""";

    @TempDir
    Path base;

    private static byte[] artifact(String name, RoleClass roleClass) {
        var tools = roleClass == RoleClass.WRITER
                ? "\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\",\"pm_build\",\"pm_lsp\""
                : "\"pm_wait\",\"pm_state\",\"pm_do\",\"pm_skill\",\"pm_skill_file\",\"pm_lsp\"";
        return """
                {"format_version":1,"name":"%s","description":"A role of the test.","class":"%s","tools":[%s],
                 "harness":"acme-cli","model":"acme-standard","effort":null,"form":"fresh","count":2,
                 "skills":["skill://pm/role/%s/SKILL.md"],"budget_floor_seconds":300}"""
                .formatted(name, roleClass.wireName(), tools, name).getBytes(UTF_8);
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
        return new HarnessProfile("acme-cli", true, false, Set.of(),
                Map.of(RoleClass.READ_ONLY, HarnessProfile.ToolSet.ADVISORY, RoleClass.WRITER,
                        HarnessProfile.ToolSet.ENFORCED),
                Map.of("acme-standard", List.of("low", "medium", "high"), "acme-frontier", List.of(),
                        "acme-fast", List.of()),
                Map.of("frontier", "acme-frontier", "standard", "acme-standard", "fast", "acme-fast"),
                400_000, null, new HarnessProfile.Resumability(false, null, false, null), "ask_user");
    }

    private static Optional<ModelCatalogue> catalogue() {
        return Optional.of(new ModelCatalogue(
                Map.of("acme-cli", Set.of("acme-standard", "acme-frontier", "acme-fast"))));
    }

    private static Map<String, RoleAssignmentProposal.Proposal> propose(Optional<ModelCatalogue> catalogue) {
        return RoleAssignmentProposal.propose(roleSet(), defaults(),
                List.of(new RoleAssignmentProposal.Harness("acme-cli", profile())), catalogue);
    }

    @Test
    @DisplayName("the steward is not among the roles the proposal assigns")
    void stewardOutside() {
        var proposals = propose(catalogue());

        assertAll(
                () -> assertFalse(proposals.containsKey("steward")),
                () -> assertTrue(proposals.containsKey("implement")),
                () -> assertTrue(roleSet().effective().containsKey("steward")),
                () -> assertFalse(roleSet().assignable().containsKey("steward")));
    }

    @Test
    @DisplayName("the default's tier is mapped to the harness's model, with the effort and the advisory beside it")
    void proposal() {
        var proposal = propose(catalogue()).get("implement");

        var value = proposal.assignment().orElseThrow();
        assertAll(
                () -> assertEquals("standard", proposal.tier()),
                () -> assertEquals("Implements the tasks of a plan.", proposal.advisory()),
                () -> assertTrue(proposal.unresolved().isEmpty()),
                () -> assertEquals("acme-cli", value.harness()),
                () -> assertEquals("acme-standard", value.model()),
                () -> assertEquals(EffortSetting.VALUE, value.effort()),
                () -> assertEquals(Optional.of(new EffortValue("high")), value.effortValue()),
                () -> assertEquals(Optional.of(new EffortValue("high")),
                        value.assignment().effort()));
    }

    @Test
    @DisplayName("an effort the harness does not list is narrowed to the greatest one it accepts")
    void narrowsEffort() {
        var outline = propose(catalogue()).get("outline").assignment().orElseThrow();

        assertAll(
                () -> assertEquals(EffortSetting.VALUE, outline.effort()),
                () -> assertEquals(Optional.of(new EffortValue("high")), outline.effortValue()));
    }

    @Test
    @DisplayName("a model without effort values runs with no effort flag")
    void noEffortValues() {
        var screener = propose(catalogue()).get("screener").assignment().orElseThrow();

        assertAll(
                () -> assertEquals("acme-fast", screener.model()),
                () -> assertEquals(EffortSetting.DEFAULT, screener.effort()),
                () -> assertTrue(screener.effortValue().isEmpty()));
    }

    @Test
    @DisplayName("a model the catalogue does not allow is reported without a value")
    void modelNotAllowed() {
        var proposal = propose(Optional.of(new ModelCatalogue(Map.of("acme-cli", Set.of("acme-fast")))))
                .get("implement");

        assertAll(
                () -> assertTrue(proposal.assignment().isEmpty()),
                () -> assertTrue(proposal.unresolved().orElseThrow().contains("does not allow")),
                () -> assertEquals("Implements the tasks of a plan.", proposal.advisory()));
    }

    @Test
    @DisplayName("a role the default names but no enrolled harness runs is reported without a value")
    void noHarness() {
        var proposals = RoleAssignmentProposal.propose(roleSet(), defaults(), List.of(), catalogue());

        assertAll(
                () -> assertTrue(proposals.get("implement").assignment().isEmpty()),
                () -> assertEquals("no enrolled harness runs the role's class",
                        proposals.get("implement").unresolved().orElseThrow()));
    }

    @Test
    @DisplayName("a role the default does not name is reported with the reason and no value")
    void roleWithoutDefault() {
        var set = RoleSetLoader.load(RoleSets.of(Map.of(
                "implement", new RoleSets.ShippedRole(artifact("implement", RoleClass.READ_ONLY)),
                "replan", new RoleSets.ShippedRole(artifact("replan", RoleClass.READ_ONLY)))));

        var proposals = RoleAssignmentProposal.propose(set, defaults(),
                List.of(new RoleAssignmentProposal.Harness("acme-cli", profile())), catalogue());

        assertAll(
                () -> assertTrue(proposals.get("replan").assignment().isEmpty()),
                () -> assertTrue(proposals.get("replan").unresolved().orElseThrow().contains("names no such role")),
                () -> assertTrue(proposals.get("implement").assignment().isPresent()));
    }

    @Test
    @DisplayName("the preferred harness wins where it is enrolled, else the first enrolled harness whose profile runs the class")
    void harnessResolution() {
        var first = new RoleAssignmentProposal.Harness("acme-cli", profile());
        var second = new RoleAssignmentProposal.Harness("beta-cli", profile());
        var both = Optional.of(new ModelCatalogue(Map.of("acme-cli", Set.of("acme-standard"),
                "beta-cli", Set.of("acme-standard"))));
        var defaults = DefaultAssignment.read(Path.of("default-assignment.json"), """
                {"format_version":1,"roles":{"implement":{"tier":"standard","effort":"high",
                 "preferred_harness":"beta-cli","advisory":"Implements."}}}""".getBytes(UTF_8));

        var preferred = RoleAssignmentProposal.propose(roleSet(), defaults, List.of(first, second), both);
        var fallback = RoleAssignmentProposal.propose(roleSet(), defaults, List.of(first), both);

        assertAll(
                () -> assertEquals("beta-cli", preferred.get("implement").assignment().orElseThrow().harness()),
                () -> assertEquals("acme-cli", fallback.get("implement").assignment().orElseThrow().harness()));
    }
}
