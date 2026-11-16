/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The digest of a project's git metadata (PM-WATCH-JOB-48): config bytes with the files it includes, the hooks with
 * their mode, and the link and administration files of every linked worktree; {@code HEAD}, the index, refs and tracked
 * source stay out. An unreadable input, an include cycle and a bound exceeded are the fail-closed part
 * {@code unreadable}.
 * <p>
 * Contract literals that stay: the directory and pointer names git writes, the include section and key, the
 * {@code unreadable} part name, and the paths a job changes legitimately. The trees and file contents are generated.
 */
@EnableGeneratorController
@DisplayName("Git metadata digest")
class GitMetadataDigestTest {

    @TempDir
    Path base;

    @Test
    @DisplayName("Should digest an unchanged tree equally")
    void shouldDigestUnchangedTreeEqually() throws Exception {
        var root = repository();

        var first = GitMetadataDigest.of(root);
        var second = GitMetadataDigest.of(root);

        assertAll("Unchanged tree",
                () -> assertFalse(first.isEmpty(), "the digest covers the metadata"),
                () -> assertEquals(first, second, "two reads of an unchanged tree agree"),
                () -> assertTrue(GitMetadataDigest.differences(first, second).isEmpty(),
                        "an unchanged tree has no difference"));
    }

    @Test
    @DisplayName("Should differ when a config key changes")
    void shouldDifferOnConfigKey() throws Exception {
        var root = repository();
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/config"), "[core]\n\tfsmonitor = attacker\n");

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(".git/config"), changed, "the changed config is named");
    }

    @Test
    @DisplayName("Should differ when an included file changes")
    void shouldDifferOnIncludedFile() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = extra.cfg\n");
        Files.writeString(root.resolve(".git/extra.cfg"), "[core]\n\thooksPath = " + name() + "\n");
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/extra.cfg"), "[core]\n\thooksPath = " + name() + "\n");

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(".git/extra.cfg"), changed, "the included file is digested and named");
    }

    @Test
    @DisplayName("Should differ when a hook file is added or its mode changes")
    void shouldDifferOnHooks() throws Exception {
        var root = repository();
        var hook = root.resolve(".git/hooks/pre-commit");
        Files.writeString(hook, "#!/bin/sh\nexit 0\n");
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rwx------"));
        var before = GitMetadataDigest.of(root);
        Files.setPosixFilePermissions(hook, PosixFilePermissions.fromString("rw-------"));

        var modeChanged = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));
        var added = root.resolve(".git/hooks/post-checkout");
        Files.writeString(added, "#!/bin/sh\nexit 0\n");
        var afterAdd = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertAll("Hooks",
                () -> assertEquals(Set.of(".git/hooks/pre-commit"), modeChanged,
                        "a changed mode is named"),
                () -> assertTrue(afterAdd.contains(".git/hooks/post-checkout"), "an added hook is named"));
    }

    @Test
    @DisplayName("Should differ when a link file or a gitdir record is rewritten")
    void shouldDifferOnWorktreeFiles() throws Exception {
        var root = repository();
        var worktree = root.resolve("wt");
        var admin = linkedWorktree(root, worktree);
        var before = GitMetadataDigest.of(root);
        Files.writeString(worktree.resolve(".git"), "gitdir: " + admin + "   \n");

        var linkChanged = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));
        var beforeRecord = GitMetadataDigest.of(root);
        Files.writeString(admin.resolve("gitdir"), worktree.resolve(".git") + "  \n");

        var recordChanged = GitMetadataDigest.differences(beforeRecord, GitMetadataDigest.of(root));

        assertAll("Worktree files",
                () -> assertEquals(Set.of("wt/.git"), linkChanged, "the link file is digested"),
                () -> assertTrue(recordChanged.stream().anyMatch(part -> part.endsWith("gitdir")),
                        "the administration record is digested"));
    }

    @Test
    @DisplayName("Should not differ when HEAD, the index, a ref or tracked source changes")
    void shouldIgnoreLegitimateChanges() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/HEAD"), "ref: refs/heads/" + name() + "\n");
        Files.write(root.resolve(".git/index"), new byte[]{1, 2, 3});
        Files.createDirectories(root.resolve(".git/refs/heads"));
        Files.writeString(root.resolve(".git/refs/heads/main"), name() + "\n");
        Files.writeString(root.resolve("README.md"), name());
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/HEAD"), "ref: refs/heads/" + name() + "\n");
        Files.write(root.resolve(".git/index"), new byte[]{4, 5, 6});
        Files.writeString(root.resolve(".git/refs/heads/main"), name() + "\n");
        Files.writeString(root.resolve("README.md"), name());

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(), changed, "the legitimate changes stay outside the digest");
    }

    @Test
    @DisplayName("Should answer unreadable for an include cycle")
    void shouldAnswerUnreadableForCycle() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = a.cfg\n");
        Files.writeString(root.resolve(".git/a.cfg"), "[include]\n\tpath = b.cfg\n");
        Files.writeString(root.resolve(".git/b.cfg"), "[include]\n\tpath = a.cfg\n");

        var parts = GitMetadataDigest.of(root);

        assertTrue(parts.containsKey(GitMetadataDigest.UNREADABLE), "a cycle is the fail-closed part");
    }

    @Test
    @DisplayName("Should answer unreadable for an include that cannot be read")
    void shouldAnswerUnreadableForUnreadableInclude() throws Exception {
        var root = repository();
        Files.createDirectories(root.resolve(".git/not-a-file"));
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = not-a-file\n");

        var parts = GitMetadataDigest.of(root);

        assertTrue(parts.containsKey(GitMetadataDigest.UNREADABLE), "an unreadable include is fail closed");
    }

    @Test
    @DisplayName("Should answer unreadable when the include depth is exceeded")
    void shouldAnswerUnreadableBeyondTheBound() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = include-0.cfg\n");
        for (var depth = 0; depth <= GitMetadataDigest.MAX_INCLUDE_DEPTH; depth++) {
            Files.writeString(root.resolve(".git/include-" + depth + ".cfg"),
                    "[include]\n\tpath = include-" + (depth + 1) + ".cfg\n");
        }

        var parts = GitMetadataDigest.of(root);

        assertTrue(parts.containsKey(GitMetadataDigest.UNREADABLE), "the bound is fail closed");
    }

    @Test
    @DisplayName("Should differ when the worktree config of the plan's own worktree changes")
    void shouldDifferOnConfigWorktree() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config.worktree"), "[core]\n\tfsmonitor = " + name() + "\n");
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/config.worktree"), "[core]\n\tfsmonitor = attacker\n");

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(".git/config.worktree"), changed, "the worktree config is digested");
    }

    @Test
    @DisplayName("Should answer unreadable for a refused metadata tree")
    void shouldAnswerUnreadableForRefusedTree() throws Exception {
        var root = Files.createDirectories(base.resolve(name()));
        Files.createSymbolicLink(root.resolve(".git"), Files.createDirectories(base.resolve(name())));

        var parts = GitMetadataDigest.of(root);

        assertTrue(parts.containsKey(GitMetadataDigest.UNREADABLE), "a refused tree is fail closed");
    }

    @Test
    @DisplayName("Should ignore a missing include, as git does")
    void shouldIgnoreAMissingInclude() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = " + name() + ".cfg\n");

        var parts = GitMetadataDigest.of(root);

        assertFalse(parts.containsKey(GitMetadataDigest.UNREADABLE), "a missing include is absent, not unreadable");
    }

    @Test
    @DisplayName("Should read a quoted include path with a trailing comment")
    void shouldReadQuotedIncludeWithComment() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = \"extra.cfg\" ; a comment\n");
        Files.writeString(root.resolve(".git/extra.cfg"), "[core]\n\tfsmonitor = " + name() + "\n");
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/extra.cfg"), "[core]\n\tfsmonitor = attacker\n");

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(".git/extra.cfg"), changed, "a quoted path with a comment is digested");
    }

    @Test
    @DisplayName("Should keep a hash inside a quoted include path and an apostrophe in a path")
    void shouldKeepQuotedHashAndApostrophe() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"),
                "[include]\n\tpath = \"extra #1.cfg\"\n\tpath = o'brien.cfg\n");
        Files.writeString(root.resolve(".git/extra #1.cfg"), "[core]\n\thooksPath = " + name() + "\n");
        Files.writeString(root.resolve(".git/o'brien.cfg"), "[core]\n\tfsmonitor = " + name() + "\n");
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/extra #1.cfg"), "[core]\n\thooksPath = attacker\n");
        Files.writeString(root.resolve(".git/o'brien.cfg"), "[core]\n\tfsmonitor = attacker\n");

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(".git/extra #1.cfg", ".git/o'brien.cfg"), changed,
                "a quoted hash and an apostrophe stay in the path");
    }

    @Test
    @DisplayName("Should read an escaped quote in a quoted include path")
    void shouldReadEscapedQuote() throws Exception {
        var root = repository();
        Files.writeString(root.resolve(".git/config"), "[include]\n\tpath = \"a\\\"b.cfg\"\n");
        Files.writeString(root.resolve(".git/a\"b.cfg"), "[core]\n\tfsmonitor = " + name() + "\n");
        var before = GitMetadataDigest.of(root);
        Files.writeString(root.resolve(".git/a\"b.cfg"), "[core]\n\tfsmonitor = attacker\n");

        var changed = GitMetadataDigest.differences(before, GitMetadataDigest.of(root));

        assertEquals(Set.of(".git/a\"b.cfg"), changed, "an escaped quote stays in the path");
    }

    @Test
    @DisplayName("Should digest nothing for a project without git")
    void shouldDigestNothingWithoutGit() throws Exception {
        var root = Files.createDirectories(base.resolve(name()));

        var parts = GitMetadataDigest.of(root);

        assertTrue(parts.isEmpty(), "a project without git has no metadata");
    }

    private Path repository() throws IOException {
        var root = Files.createDirectories(base.resolve(name()));
        var git = Files.createDirectories(root.resolve(".git"));
        Files.writeString(git.resolve("config"), "[core]\n\trepositoryformatversion = 0\n");
        Files.createDirectories(git.resolve("hooks"));
        Files.writeString(root.resolve("README.md"), name());
        return root;
    }

    private Path linkedWorktree(Path root, Path worktree) throws IOException {
        var admin = Files.createDirectories(root.resolve(".git/worktrees").resolve(name()));
        Files.writeString(admin.resolve("commondir"), "../..\n");
        Files.writeString(admin.resolve("config.worktree"), name());
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), "gitdir: " + admin + "\n");
        Files.writeString(admin.resolve("gitdir"), worktree.resolve(".git") + "\n");
        return admin;
    }

    private static String name() {
        return Generators.letterStrings(4, 12).next();
    }
}
