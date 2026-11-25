/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.StoredConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.GitOperations.CommitInput;

/**
 * The refusing file system of {@link JGitOperations}: a repository hook and a filter driver are a missing capability,
 * never a process that starts.
 */
@EnableGeneratorController
@DisplayName("JGitOperations (native git variant): refusing file system")
class JGitOperationsCapabilityTest extends JGitOperationsFixtures {

    @Test
    @DisplayName("refuses a commit when a repository hook is present, writing nothing")
    void hookPresent() throws Exception {
        // the hook name and the refusal detail are the contract of git and of the outcome
        Path hook = repo.resolve(".git/hooks/pre-commit");
        var marker = names.next();
        Files.createDirectories(hook.getParent());
        Files.writeString(hook, "#!/bin/sh\ntouch " + marker + "\n");
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwxr-xr-x"));
        String before = git.open(repo).value().head();
        write(file(), text());

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of()));

        assertAll(
                () -> assertEquals(GitOutcome.CAPABILITY_MISSING, result.outcome(),
                        "a present hook is a missing capability"),
                () -> assertEquals("repository_hook", result.detail(), "the detail names the repository hook"),
                () -> assertEquals(before, git.open(repo).value().head(),
                        "the refused commit leaves HEAD unchanged"),
                () -> assertFalse(Files.exists(repo.resolve(marker)), "the hook is never run"));
    }

    @Test
    @DisplayName("refuses filter drivers instead of running them")
    void filterDriver() throws Exception {
        // the attribute and configuration keys of a git filter driver, and the refusal detail, are the contract
        var filter = names.next();
        write(".gitattributes", "*.txt filter=" + filter + "\n");
        try (Git jgit = Git.open(repo.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString("filter", filter, "clean", "tr a-z A-Z");
            config.save();
        }
        write(file(), text());

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of()));

        assertAll(
                () -> assertEquals(GitOutcome.CAPABILITY_MISSING, result.outcome(),
                        "a filter driver is a missing capability"),
                () -> assertEquals("process_execution", result.detail(),
                        "the detail names the process execution"));
    }
}
