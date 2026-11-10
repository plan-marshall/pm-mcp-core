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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link RoleAssignmentProposal.Proposal} builds the value the canonical constructor builds, and a
 * missing required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Role assignment proposal builder")
class RoleAssignmentProposalBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var role = Generators.letterStrings(1, 10).next();
        var tier = Generators.letterStrings(1, 10).next();
        var advisory = Generators.letterStrings(1, 10).next();
        var unresolved = Generators.letterStrings(1, 10).next();

        var canonical = new RoleAssignmentProposal.Proposal(role, tier, advisory, null, unresolved);
        var built = RoleAssignmentProposal.Proposal.builder().role(canonical.role()).tier(canonical.tier())
                .advisory(canonical.advisory()).assignment(canonical.assignment())
                .unresolved(canonical.unresolved()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new RoleAssignmentProposal.Proposal(null, tier, advisory, null, unresolved),
                            "the constructor refuses a null role");
                    var incomplete = RoleAssignmentProposal.Proposal.builder().tier(tier).advisory(advisory)
                            .unresolved(unresolved);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing role");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent role with the same message");
                });
    }
}
