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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.primitive.StagingSets;

/**
 * The commit's path rule: a path is a literal repository path of one file, so a glob character, an option-shaped name
 * and a symbolic link are committed as they are, while a pathspec magic, a directory, an outside path, a non-normal
 * name and a path through a link to outside are refused before anything is staged.
 */
@EnableGeneratorController
@DisplayName("JGitOperations (native git variant): literal paths and refusals")
class JGitOperationsPathTest extends JGitOperationsFixtures {

    @Test
    @DisplayName("refuses a directory before anything below it is staged")
    void refusesADirectory() throws Exception {
        write("below/one.txt", text());
        write("below/two.txt", text());

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("below")));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, result.outcome(), "a directory is no commit path"),
                () -> assertEquals("below", result.detail(), "the refusal names the directory"),
                () -> assertEquals(1, git.log(repo, "HEAD", 5).value().size(),
                        "nothing below the directory was committed"));
    }

    @Test
    @DisplayName("refuses a pathspec magic before anything is staged")
    void refusesAPathspecMagic() {
        var magic = git.commit(new CommitInput(repo, text(), author, agent, List.of(":(exclude)x")));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, magic.outcome(),
                        "a pathspec magic is no commit path"),
                () -> assertEquals(":(exclude)x", magic.detail(), "the refusal names the magic"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("commits a name with bracket, star and question mark as that literal file")
    void commitsGlobCharactersLiterally() throws Exception {
        var stem = names.next();
        // the specification's example of a literal path (Stage 4), beside a file its glob reading would match
        var bracket = "app/[" + stem + "]/page.tsx";
        var bracketMatch = "app/" + stem.charAt(0) + "/page.tsx";
        var star = stem + "*.txt";
        var starMatch = stem + "x.txt";
        var question = stem + "?.md";
        var questionMatch = stem + "q.md";
        for (var path : List.of(bracket, bracketMatch, star, starMatch, question, questionMatch)) {
            write(path, text());
        }

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(bracket, star, question)));

        var commit = result.value();
        assertAll(
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the literal names are committed"),
                () -> assertTrue(inCommit(commit.id(), bracket), "the bracket name is committed literally"),
                () -> assertTrue(inCommit(commit.id(), star), "the star name is committed literally"),
                () -> assertTrue(inCommit(commit.id(), question), "the question mark name is committed"),
                () -> assertFalse(inCommit(commit.id(), bracketMatch), "the bracket glob matches nothing"),
                () -> assertFalse(inCommit(commit.id(), starMatch), "the star glob matches nothing"),
                () -> assertFalse(inCommit(commit.id(), questionMatch), "the question glob matches nothing"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("commits a name shaped like an option as that literal file")
    void commitsAnOptionShapedNameLiterally() throws Exception {
        // the option form of the specification's example, which JGit reads as a plain name
        var option = "-rf" + names.next();
        write(option, text());

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(option)));

        assertAll(
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the option-shaped name is committed"),
                () -> assertTrue(inCommit(result.value().id(), option), "the option-shaped name is in the tree"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("commits a symbolic link to a directory as the link, not the directory")
    void commitsALinkToADirectory() throws Exception {
        var directory = names.next();
        var below = directory + "/" + file();
        var link = names.next();
        write(below, text());
        Files.createSymbolicLink(repo.resolve(link), Path.of(directory));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(link)));

        assertAll(
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the link to a directory is committed"),
                () -> assertEquals(FileMode.SYMLINK, modeInCommit(result.value().id(), link),
                        "the link is recorded as a link"),
                () -> assertFalse(inCommit(result.value().id(), below), "nothing below the target is committed"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("commits a dangling symbolic link as the link")
    void commitsADanglingLink() throws Exception {
        var link = names.next();
        Files.createSymbolicLink(repo.resolve(link), Path.of(names.next()));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(link)));

        assertAll(
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the dangling link is committed"),
                () -> assertEquals(FileMode.SYMLINK, modeInCommit(result.value().id(), link),
                        "the dangling link is recorded as a link"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("refuses a path below a link that leaves the root, before a valid path is staged")
    void refusesAPathBelowAnOutwardLink() throws Exception {
        // fixture path: a directory beside the repository, outside its root
        Path outside = Files.createDirectories(temp.resolve(names.next()));
        var secret = names.next() + "*.txt";
        Files.writeString(outside.resolve(secret), text(), StandardCharsets.UTF_8);
        var alias = names.next();
        Files.createSymbolicLink(repo.resolve(alias), outside);
        var safe = file();
        write(safe, text());
        var below = alias + "/" + secret;
        var missingBelow = alias + "/" + names.next() + "/" + file();

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(safe, below)));
        var missing = git.commit(new CommitInput(repo, text(), author, agent, List.of(missingBelow)));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, result.outcome(), "a path below the link is refused"),
                () -> assertEquals(below, result.detail(), "the refusal names the path below the link"),
                () -> assertEquals(GitOutcome.PATH_REFUSED, missing.outcome(),
                        "a missing path below the link is refused"),
                () -> assertFalse(isStaged(safe), "the valid listed path was not staged"),
                () -> assertEquals(1, git.log(repo, HEAD, 5).value().size(),
                        "no refused commit reached the history"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("commits a link that replaces a tracked directory as that link")
    void commitsALinkThatReplacesATrackedDirectory() throws Exception {
        var node = names.next();
        var below = node + "/" + file();
        write(below, text());
        git.commit(new CommitInput(repo, text(), author, agent, List.of(below)));
        var target = names.next();
        write(target + "/" + file(), text());
        Files.delete(repo.resolve(below));
        Files.delete(repo.resolve(node));
        Files.createSymbolicLink(repo.resolve(node), Path.of(target));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(node)));

        assertAll(
                () -> assertEquals(GitOutcome.OK, result.outcome(), "the replacing link is committed"),
                () -> assertEquals(FileMode.SYMLINK, modeInCommit(result.value().id(), node),
                        "the replacing link is recorded as a link"),
                () -> assertFalse(inCommit(result.value().id(), below),
                        "the replaced directory entry is gone"));
    }

    @Test
    @DisplayName("refuses a directory staged in a repository without commits once it is removed")
    void refusesAStagedDirectoryWithoutHead() throws Exception {
        // fixture path of a second repository that never gets a commit
        Path fresh = temp.resolve(names.next());
        assertTrue(git.init(fresh, branch).isOk(), "the repository without commits is initialised");
        var directory = names.next();
        Path staged = fresh.resolve(directory).resolve(file());
        Files.createDirectories(staged.getParent());
        Files.writeString(staged, text(), StandardCharsets.UTF_8);
        try (var jgit = Git.open(fresh.toFile())) {
            jgit.add().addFilepattern(directory).call();
        }
        Files.delete(staged);
        Files.delete(staged.getParent());

        var result = git.commit(new CommitInput(fresh, text(), author, agent, List.of(directory)));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, result.outcome(),
                        "a staged directory is refused without HEAD"),
                () -> assertEquals(directory, result.detail(), "the refusal names the staged directory"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, git.log(fresh, HEAD, 1).outcome(),
                        "the repository still has no commit"));
    }

    @Test
    @DisplayName("refuses an absolute, a parent, an outside and a non-normal path")
    void refusesPathsOutsideTheRoot() {
        var name = file();
        // the contract's examples of paths that name no file of the root
        var absolute = repo.resolve(name).toAbsolutePath().toString();
        var parent = "../" + name;
        var outside = names.next() + "/../../" + name;
        var nonNormal = names.next() + "/./" + name;
        var trailing = names.next() + "/";

        assertAll(
                () -> assertRefused(absolute, "an absolute path is refused"),
                () -> assertRefused(parent, "a parent-relative path is refused"),
                () -> assertRefused(outside, "a path leaving the root is refused"),
                () -> assertRefused(nonNormal, "a path with a dot segment is refused"),
                () -> assertRefused(trailing, "a path with an empty segment is refused"),
                () -> assertEquals(1, git.log(repo, HEAD, 5).value().size(),
                        "no refused path reached a commit"));
    }

    @Test
    @DisplayName("refuses a tracked directory whose worktree copy is gone")
    void refusesATrackedDirectoryGone() throws Exception {
        write("gone/one.txt", text());
        git.commit(new CommitInput(repo, text(), author, agent, List.of("gone/one.txt")));
        Files.delete(repo.resolve("gone/one.txt"));
        Files.delete(repo.resolve("gone"));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("gone")));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, result.outcome(),
                        "a deleted tracked directory is no commit path"),
                () -> assertEquals("gone", result.detail(), "the refusal names the directory"),
                () -> assertEquals(2, git.log(repo, "HEAD", 5).value().size(),
                        "the deletion below the directory was not committed"));
    }

    @Test
    @DisplayName("refuses a path that is no legal path of the file system")
    void refusesAnInvalidPath() {
        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("a\u0000b")));

        assertAll(
                () -> assertEquals(GitOutcome.PATH_REFUSED, result.outcome(),
                        "a path with NUL is refused, not thrown"),
                () -> assertEquals("a\u0000b", result.detail(), "the refusal names the path"));
    }

    @Test
    @DisplayName("commits a change of the executable bit alone")
    void commitsAModeChange() throws Exception {
        var script = "run.sh";
        write(script, text());
        git.commit(new CommitInput(repo, text(), author, agent, List.of(script)));
        Files.setPosixFilePermissions(repo.resolve(script),
                PosixFilePermissions.fromString("rwxr-xr-x"));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(script)));

        assertAll(
                () -> assertEquals(GitOutcome.OK, result.outcome(),
                        "a mode-only change is a change"),
                () -> assertEquals(3, git.log(repo, "HEAD", 5).value().size(),
                        "the mode change is committed"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("refuses a path whose nearest existing ancestor is a dangling link")
    void refusesAPathBelowADanglingAncestor() throws Exception {
        var ancestor = names.next();
        var below = ancestor + "/" + file();
        write(below, text());
        Files.delete(repo.resolve(below));
        Files.delete(repo.resolve(ancestor));
        Files.createSymbolicLink(repo.resolve(ancestor), Path.of(names.next()));

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(below)));

        assertAll("dangling ancestor",
                () -> assertEquals(GitOutcome.PATH_REFUSED, result.outcome(),
                        "a dangling ancestor is no confined parent"),
                () -> assertEquals(below, result.detail(), "the refusal names the path below it"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("globCandidates")
    @DisplayName("JGit stages the literal file name of a path carrying a glob character")
    void addFilepatternStagesTheLiteralName(String literal, String patternMatch) throws Exception {
        // the two file names are the contract: the literal name and the name a wildcard would match instead
        write(literal, text());
        write(patternMatch, text());
        try (Git jgit = Git.open(repo.toFile())) {
            jgit.add().addFilepattern(literal).call();
            var index = jgit.getRepository().readDirCache();

            assertAll(
                    () -> assertNotNull(index.getEntry(literal), "the literal file name is staged"),
                    () -> assertNull(index.getEntry(patternMatch), "no wildcard match is staged"));
        }
    }

    @Test
    @DisplayName("a modified submodule is outside the staging set and refused by the commit")
    void modifiedSubmoduleIsRefusedByBoth() throws Exception {
        var submodule = names.next();
        // a real nested repository, recorded as a gitlink in the outer index
        Path nested = repo.resolve(submodule);
        try (Git jgit = Git.init().setDirectory(nested.toFile()).call()) {
            Files.writeString(nested.resolve(file()), text(), StandardCharsets.UTF_8);
            jgit.add().addFilepattern(".").call();
            commitThroughGit(jgit);
        }
        try (Git jgit = Git.open(repo.toFile()); Git inner = Git.open(nested.toFile())) {
            recordGitlink(jgit.getRepository(), inner.getRepository(), submodule);
            commitThroughGit(jgit);
        }
        // the nested repository moves to a commit of its own, so the worktree differs from the gitlink
        try (Git jgit = Git.open(nested.toFile())) {
            Files.writeString(nested.resolve(file()), text(), StandardCharsets.UTF_8);
            jgit.add().addFilepattern(".").call();
            commitThroughGit(jgit);
        }

        var staging = StagingSets.derive(ScopeType.PLAN, repo, List.of(submodule), false);
        var refused = git.commit(new CommitInput(repo, text(), author, agent, List.of(submodule)));

        assertAll("modified submodule",
                () -> assertTrue(gitStatus().getModified().contains(submodule),
                        "git status lists it by its name, without a trailing slash"),
                () -> assertEquals(Set.of(submodule), staging.outside(),
                        "The staging set reports the modified submodule outside"),
                () -> assertEquals(GitOutcome.PATH_REFUSED, refused.outcome(),
                        "The commit refuses the same name"),
                () -> assertEquals(submodule, refused.detail(), "The refusal names the submodule"));
    }

    @Test
    @DisplayName("a tracked directory replaced by a file is outside the staging set and refused by the commit")
    void trackedDirectoryReplacedByAFileIsRefusedByBoth() throws Exception {
        var directory = names.next();
        var below = directory + "/" + file();
        write(below, text());
        commit(text());
        Files.delete(repo.resolve(below));
        Files.delete(repo.resolve(directory));
        Files.writeString(repo.resolve(directory), text(), StandardCharsets.UTF_8);

        var staging = StagingSets.derive(ScopeType.PLAN, repo, List.of(directory, below), false);
        var refused = git.commit(new CommitInput(repo, text(), author, agent, List.of(directory)));

        assertAll("replaced tracked directory",
                () -> assertEquals(Set.of(directory), staging.outside(),
                        "The replaced directory is outside the staging set"),
                () -> assertEquals(Set.of(below), staging.staged(),
                        "The deletion below it is still staged"),
                () -> assertEquals(GitOutcome.PATH_REFUSED, refused.outcome(),
                        "The commit refuses the replaced directory"),
                () -> assertEquals(directory, refused.detail(), "The refusal names the replaced directory"));
    }

    /**
     * Records {@code path} as a gitlink of the outer repository's index, the entry git writes for a submodule, pointing
     * at the nested repository's current commit.
     */
    private static void recordGitlink(Repository repository, Repository nestedRepository, String path)
            throws Exception {
        var head = Objects.requireNonNull(nestedRepository.resolve(Constants.HEAD), "the nested repository's head");
        var editor = repository.lockDirCache().editor();
        editor.add(new DirCacheEditor.PathEdit(path) {

            @Override
            public void apply(DirCacheEntry entry) {
                entry.setFileMode(FileMode.GITLINK);
                entry.setObjectId(head);
            }
        });
        assertTrue(editor.commit(), "the gitlink is recorded in the index");
    }

    private Status gitStatus() throws Exception {
        try (var jgit = Git.open(repo.toFile())) {
            return jgit.status().call();
        }
    }

    @Test
    @DisplayName("the provider commits a path whose name carries a glob character, literally")
    void providerCommitsTheGlobPath() throws Exception {
        // the file names are the contract: the literal page path and the name a wildcard would match
        write("pages/[id].tsx", text());
        write("pages/i.tsx", text());

        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of("pages/[id].tsx")));

        try (Git jgit = Git.open(repo.toFile())) {
            var tree = jgit.getRepository().resolve("HEAD^{tree}");
            var literal = TreeWalk.forPath(jgit.getRepository(), "pages/[id].tsx", tree);
            var wildcardMatch = TreeWalk.forPath(jgit.getRepository(), "pages/i.tsx", tree);

            assertAll(
                    () -> assertEquals(GitOutcome.OK, result.outcome(), "the literal page path is committed"),
                    () -> assertNotNull(literal, "the committed tree holds the literal name"),
                    () -> assertNull(wildcardMatch, "the wildcard match stays uncommitted"));
        }
    }

    static Stream<Arguments> globCandidates() {
        return Stream.of(
                Arguments.of("pages/[id].tsx", "pages/i.tsx"),
                Arguments.of("notes*.md", "notes-a.md"),
                Arguments.of("what?.md", "what1.md"));
    }

    private boolean isStaged(String path) throws Exception {
        try (var jgit = Git.open(repo.toFile())) {
            return jgit.getRepository().readDirCache().findEntry(path) >= 0;
        }
    }

    private void assertRefused(String path, String message) {
        var result = git.commit(new CommitInput(repo, text(), author, agent, List.of(path)));
        assertEquals(GitOutcome.PATH_REFUSED, result.outcome(), message);
        assertEquals(path, result.detail(), "the refusal names the refused path");
    }

    private FileMode modeInCommit(String commitId, String path) throws Exception {
        try (var jgit = Git.open(repo.toFile())) {
            var tree = jgit.getRepository().parseCommit(jgit.getRepository().resolve(commitId)).getTree();
            try (var walk = TreeWalk.forPath(jgit.getRepository(), path, tree)) {
                assertNotNull(walk, "the path is in the committed tree");
                return walk.getFileMode(0);
            }
        }
    }

    private boolean inCommit(String commitId, String path) throws Exception {
        try (var jgit = Git.open(repo.toFile())) {
            var tree = jgit.getRepository().parseCommit(
                    jgit.getRepository().resolve(commitId)).getTree();
            return TreeWalk.forPath(jgit.getRepository(), path, tree) != null;
        }
    }
}
