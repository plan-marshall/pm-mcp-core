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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.ProcessResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@EnableGeneratorController
@DisplayName("RefusingFS")
class RefusingFSTest {

    /** The name of the hook git runs before a commit: part of git's hook contract. */
    private static final String PRE_COMMIT = "pre-commit";

    private final RefusingFS fs = new RefusingFS();

    private static String word() {
        return Generators.letterStrings(1, 12).next();
    }

    @Test
    @DisplayName("refuses every process entry point (the refusal reason and prefix are the contract)")
    void refusesProcesses() {
        var builder = new ProcessBuilder(word(), word());
        var out = new ByteArrayOutputStream();
        var input = word();

        var shell = assertThrows(RefusingFS.CapabilityRefusedException.class, () -> fs.runInShell(word(),
                new String[0]), "a shell command is refused");

        assertAll(
                () -> assertThrows(RefusingFS.CapabilityRefusedException.class,
                        () -> fs.runProcess(builder, out, out, input), "a process with text input is refused"),
                () -> assertThrows(RefusingFS.CapabilityRefusedException.class,
                        () -> fs.runProcess(builder, out, out, new ByteArrayInputStream(new byte[0])),
                        "a process with stream input is refused"),
                () -> assertThrows(RefusingFS.CapabilityRefusedException.class,
                        () -> fs.execute(builder, new ByteArrayInputStream(new byte[0])),
                        "an executed process is refused"),
                () -> assertEquals("process_execution", shell.getReason(), "the refusal names process execution"),
                () -> assertTrue(shell.getMessage().startsWith("capability_missing"),
                        "the message starts with the outcome code"));
    }

    @Test
    @DisplayName("finds no git executable and stays refusing when copied")
    void noGitExecutable() {
        assertAll(
                () -> assertNull(fs.discoverGitExe(), "no git executable is discovered"),
                () -> assertInstanceOf(RefusingFS.class, fs.newInstance(), "a copy of the file system still refuses"));
    }

    @Test
    @DisplayName("reports an absent hook as not present on both entry points")
    void absentHook(@TempDir Path dir) throws Exception {
        try (Git git = Git.init().setDirectory(dir.toFile()).setFs(fs).call()) {
            Repository repository = git.getRepository();
            var out = new ByteArrayOutputStream();

            ProcessResult viaPublic = fs.runHookIfPresent(repository, PRE_COMMIT, new String[0], out, out, null);
            ProcessResult viaInternal = fs.internalRunHookIfPresent(repository, PRE_COMMIT, new String[0], out, out,
                    null);

            assertAll(
                    () -> assertEquals(ProcessResult.Status.NOT_PRESENT, viaPublic.getStatus(),
                            "the public entry finds no hook"),
                    () -> assertEquals(ProcessResult.Status.NOT_PRESENT, viaInternal.getStatus(),
                            "the internal entry finds no hook"),
                    () -> assertFalse(viaPublic.isExecutedWithError(), "an absent hook reports no execution error"));
        }
    }
}
