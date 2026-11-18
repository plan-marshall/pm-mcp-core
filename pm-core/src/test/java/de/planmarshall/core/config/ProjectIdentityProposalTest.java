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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The identity a project onboarding proposes (State Catalogue § Project Onboarding, {@code project.identity}): the id
 * from the remote's name, valid by the identifier grammar and unique on the machine, and the display name from the
 * remote's owner and name.
 */
@DisplayName("Project identity proposal")
class ProjectIdentityProposalTest {

    @Test
    @DisplayName("Should propose the id and display name of an HTTPS remote")
    void proposesFromHttpsRemote() {
        var proposal = proposal("https://github.com/plan-marshall/pm-mcp-core.git");

        assertAll(
                () -> assertEquals("pm-mcp-core", proposal.projectId(), "The id is the remote's name"),
                () -> assertEquals("plan-marshall/pm-mcp-core", proposal.displayName(),
                        "The display name is owner and name"));
    }

    @Test
    @DisplayName("Should propose from a scp-like and an ssh remote alike")
    void proposesFromScpLikeAndSshRemotes() {
        var scp = proposal("git@github.com:plan-marshall/pm-mcp-core.git");
        var ssh = proposal("ssh://git@github.com/plan-marshall/pm-mcp-core.git");

        assertAll(
                () -> assertEquals("pm-mcp-core", scp.projectId(), "A scp-like remote is read alike"),
                () -> assertEquals("pm-mcp-core", ssh.projectId(), "An ssh remote is read alike"));
    }

    @Test
    @DisplayName("Should make a name valid for the grammar")
    void makesTheNameValid() {
        var proposal = proposal("https://example.org/Some_Owner/My.Project_Name.git");

        assertEquals("my-project-name", proposal.projectId(), "The id is lower case with single hyphens");
    }

    @Test
    @DisplayName("Should suffix an id the machine already enrols")
    void suffixesATakenId() {
        var proposal = ProjectIdentityProposal.fromRemote("https://example.org/owner/name.git",
                Set.of("name", "name-2")).orElseThrow();

        assertEquals("name-3", proposal.projectId(), "The first free suffix follows the taken ids");
    }

    @Test
    @DisplayName("Should propose nothing for a remote without owner and name")
    void proposesNothingWithoutARepository() {
        assertAll(
                () -> assertTrue(ProjectIdentityProposal.fromRemote("https://example.org", Set.of()).isEmpty(),
                        "A remote without a repository proposes nothing"),
                () -> assertTrue(ProjectIdentityProposal.fromRemote("/name.git", Set.of()).isEmpty(),
                        "A path without an owner proposes nothing"),
                () -> assertTrue(ProjectIdentityProposal.fromRemote("", Set.of()).isEmpty(),
                        "An empty remote proposes nothing"),
                () -> assertTrue(ProjectIdentityProposal.fromRemote("https://example.org/owner/___.git",
                        Set.of()).isEmpty(), "A name with no valid character proposes nothing"));
    }

    @Test
    @DisplayName("Should keep a long name inside the identifier length")
    void keepsALongNameInsideTheGrammar() {
        var longName = "a".repeat(80);
        var proposal = ProjectIdentityProposal.fromRemote("https://example.org/owner/" + longName + ".git",
                Set.of()).orElseThrow();

        assertAll(
                () -> assertTrue(proposal.projectId().length() <= 63, "The id stays inside 63 characters"),
                () -> assertTrue(proposal.projectId().length() >= 2, "The id keeps the grammar's minimum"));
    }

    private static ProjectIdentityProposal proposal(String remote) {
        return ProjectIdentityProposal.fromRemote(remote, Set.of()).orElseThrow();
    }
}
