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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One project value over the machine's (Store Schemas § Project Values over Machine Values): the machine's value is the
 * proposal, accepting it records nothing, and a changed value is the one the project records.
 */
@DisplayName("Project value over the machine's")
class ProjectValueProposalTest {

    @Test
    @DisplayName("Should follow the machine while the project records no value")
    void followsTheMachine() {
        var proposal = ProjectValueProposal.following("same");

        assertAll(
                () -> assertEquals("same", proposal.effective(), "The machine's value is effective"),
                () -> assertTrue(proposal.followsMachine(), "The project follows the machine"),
                () -> assertFalse(proposal.recordsChange("same"), "Accepting the proposal records nothing"),
                () -> assertTrue(proposal.recorded("same").isEmpty(), "Nothing is recorded for the proposal"));
    }

    @Test
    @DisplayName("Should record a changed value and make it effective")
    void recordsAChangedValue() {
        var proposal = ProjectValueProposal.following("text");

        assertAll(
                () -> assertTrue(proposal.recordsChange("mermaid"), "A changed value is recorded"),
                () -> assertEquals("mermaid", proposal.recorded("mermaid").orElseThrow(),
                        "The recorded value is the changed one"));
    }

    @Test
    @DisplayName("Should keep the project's recorded value as the effective one")
    void keepsTheProjectValue() {
        var proposal = new ProjectValueProposal<>("text", "mermaid");

        assertAll(
                () -> assertEquals("mermaid", proposal.effective(), "The project's own value wins"),
                () -> assertFalse(proposal.followsMachine(), "A recorded value is not the machine's"));
    }
}
