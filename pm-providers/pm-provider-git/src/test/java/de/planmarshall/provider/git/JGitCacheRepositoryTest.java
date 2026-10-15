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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.FetchCommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitOperations.ReadFileInput;

/**
 * The cache repository operations of the git contract (PM-EXEC-5) against a JGit repository on disk, reached over
 * {@code file://} and configured to serve a commit by its id: a bare cache is created once, the pinned commit is
 * fetched by id, and its files are read raw, an LFS pointer as the pointer; an unknown commit, a missing path and a
 * remote outside the policy end in their outcomes.
 */
@DisplayName("JGitOperations cache repository operations")
class JGitCacheRepositoryTest {

    private static final Identity AUTHOR = new Identity("Operator", "operator@example.com");
    private static final String CSV = "question_id,question\nq-1,Is the sky blue?\n";
    private static final String POINTER = "version https://git-lfs.github.com/spec/v1\n"
            + "oid sha256:" + "a".repeat(64) + "\nsize 12\n";
    private static final String UNKNOWN_COMMIT = "1".repeat(40);

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path source;
    private Path cache;
    private String commit;

    /**
     * Creates a source repository with a plain file and an LFS pointer, configured to serve any commit by its id.
     *
     * @param git     the operations creating it
     * @param root    the working tree of the source repository
     * @param csv     the content of the plain file {@code data/test.csv}
     * @param pointer the content of the LFS pointer {@code data/big.jsonl}
     * @return the id of its one commit
     */
    static String sourceRepository(JGitOperations git, Path root, String csv, String pointer) throws IOException {
        assertTrue(git.init(root, "main").isOk());
        Files.createDirectories(root.resolve("data"));
        Files.writeString(root.resolve("data/test.csv"), csv, UTF_8);
        Files.writeString(root.resolve("data/big.jsonl"), pointer, UTF_8);
        var committed = git.commit(new CommitInput(root, "dataset", AUTHOR, AUTHOR));
        assertEquals(GitOutcome.OK, committed.outcome(), committed.detail());
        try (Git jgit = Git.open(root.toFile())) {
            var config = jgit.getRepository().getConfig();
            config.setBoolean("uploadpack", null, "allowAnySHA1InWant", true);
            config.save();
        }
        return committed.value().orElseThrow().id();
    }

    @BeforeEach
    void repositories() throws IOException {
        git = new JGitOperations(RemotePolicy.localOnly());
        source = temp.resolve("source");
        commit = sourceRepository(git, source, CSV, POINTER);
        cache = temp.resolve("cache/alpha.git");
    }

    private String url() {
        return source.toUri().toString();
    }

    private GitResult<String> fetch(String url, String id, RemotePolicy policy) {
        return git.fetchCommit(new FetchCommitInput(cache, url, id, policy));
    }

    private GitResult<byte[]> read(String id, String path) {
        return git.readFile(new ReadFileInput(cache, id, path));
    }

    @Nested
    @DisplayName("initCache")
    class InitCache {

        @Test
        @DisplayName("creates a bare repository, and opens it again unchanged")
        void idempotent() {
            var first = git.initCache(cache);
            var second = git.initCache(cache);

            assertAll(
                    () -> assertEquals(GitOutcome.OK, first.outcome(), first.detail()),
                    () -> assertEquals(first, second),
                    () -> assertEquals(cache.toRealPath(), first.value().orElseThrow()),
                    () -> assertTrue(Files.isDirectory(cache.resolve("objects"))),
                    () -> assertFalse(Files.exists(cache.resolve(".git"))));
        }

        @Test
        @DisplayName("refuses a directory that holds anything but a repository")
        void notARepository() throws Exception {
            Files.createDirectories(cache);
            Files.writeString(cache.resolve("notes.txt"), "not a repository");

            assertEquals(GitOutcome.NOT_A_REPOSITORY, git.initCache(cache).outcome());
        }

        @Test
        @DisplayName("refuses a working tree, which is no bare repository")
        void workingTree() {
            assertEquals(GitOutcome.NOT_A_REPOSITORY, git.initCache(source.resolve(".git")).outcome());
        }

        @Test
        @DisplayName("a cache that cannot be created, below a regular file, is a failure")
        void cannotCreate() throws Exception {
            var file = Files.writeString(temp.resolve("cache-file"), "no directory");

            var result = git.initCache(file.resolve("alpha.git"));

            assertEquals(GitOutcome.FAILED, result.outcome(), result.detail());
        }
    }

    @Nested
    @DisplayName("fetchCommit and readFile")
    class FetchAndRead {

        @BeforeEach
        void initCache() {
            assertTrue(git.initCache(cache).isOk());
        }

        @Test
        @DisplayName("fetches the pinned commit by its id and reads a plain file and an LFS pointer raw")
        void fetchesAndReads() {
            var fetched = fetch(url(), commit, RemotePolicy.localOnly());

            var file = read(commit, "data/test.csv");
            var pointer = read(commit, "data/big.jsonl");

            assertAll(
                    () -> assertEquals(GitOutcome.OK, fetched.outcome(), fetched.detail()),
                    () -> assertEquals(commit, fetched.value().orElseThrow()),
                    () -> assertArrayEquals(CSV.getBytes(UTF_8), file.value().orElseThrow()),
                    () -> assertArrayEquals(POINTER.getBytes(UTF_8), pointer.value().orElseThrow()));
        }

        @Test
        @DisplayName("a commit already in the cache is served without contacting the remote")
        void cachedCommit() throws Exception {
            assertTrue(fetch(url(), commit, RemotePolicy.localOnly()).isOk());
            FileUtils.delete(source.toFile(), FileUtils.RECURSIVE);

            var again = fetch(url(), commit, RemotePolicy.localOnly());

            assertAll(
                    () -> assertEquals(GitOutcome.OK, again.outcome(), again.detail()),
                    () -> assertTrue(read(commit, "data/test.csv").isOk()));
        }

        @Test
        @DisplayName("a commit the remote does not have is an unknown revision")
        void unknownCommit() {
            var fetched = fetch(url(), UNKNOWN_COMMIT, RemotePolicy.localOnly());

            assertEquals(GitOutcome.UNKNOWN_REVISION, fetched.outcome(), fetched.detail());
        }

        @Test
        @DisplayName("an abbreviated commit id is an unknown revision, nothing fetched")
        void abbreviatedCommit() {
            var fetched = fetch(url(), commit.substring(0, 12), RemotePolicy.localOnly());

            assertEquals(GitOutcome.UNKNOWN_REVISION, fetched.outcome(), fetched.detail());
        }

        @Test
        @DisplayName("a remote that does not answer is a failure")
        void missingRemote() {
            var fetched = fetch(temp.resolve("gone").toUri().toString(), commit, RemotePolicy.localOnly());

            assertEquals(GitOutcome.FAILED, fetched.outcome(), fetched.detail());
        }

        @Test
        @DisplayName("a commit not fetched into the cache is an unknown revision to readFile")
        void readUnfetchedCommit() {
            assertEquals(GitOutcome.UNKNOWN_REVISION, read(commit, "data/test.csv").outcome());
        }

        @Test
        @DisplayName("a path missing at the commit, or a directory, is not found")
        void missingPath() {
            assertTrue(fetch(url(), commit, RemotePolicy.localOnly()).isOk());

            assertAll(
                    () -> assertEquals(GitOutcome.PATH_NOT_FOUND, read(commit, "data/other.csv").outcome()),
                    () -> assertEquals(GitOutcome.PATH_NOT_FOUND, read(commit, "data").outcome()));
        }

        @Test
        @DisplayName("a file whose object the cache lost is a failure to readFile, not a missing path")
        void lostObject() throws Exception {
            ObjectId blob;
            ObjectId cached;
            try (Repository repository = new FileRepositoryBuilder().setGitDir(cache.toFile()).setBare().build();
                 ObjectInserter inserter = repository.newObjectInserter()) {
                blob = inserter.insert(Constants.OBJ_BLOB, CSV.getBytes(UTF_8));
                var tree = new TreeFormatter();
                tree.append("test.csv", FileMode.REGULAR_FILE, blob);
                var commitBuilder = new CommitBuilder();
                commitBuilder.setTreeId(inserter.insert(tree));
                var ident = new PersonIdent(AUTHOR.name(), AUTHOR.email());
                commitBuilder.setAuthor(ident);
                commitBuilder.setCommitter(ident);
                commitBuilder.setMessage("cached");
                cached = inserter.insert(commitBuilder);
                inserter.flush();
            }
            Files.delete(cache.resolve("objects").resolve(blob.name().substring(0, 2))
                    .resolve(blob.name().substring(2)));

            var result = read(cached.name(), "test.csv");

            assertEquals(GitOutcome.FAILED, result.outcome(), result.detail());
        }

        @Test
        @DisplayName("a cache that is no repository is refused")
        void cacheIsNoRepository() throws Exception {
            var plain = Files.createDirectories(temp.resolve("plain"));

            assertAll(
                    () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.fetchCommit(
                            new FetchCommitInput(plain, url(), commit, RemotePolicy.localOnly())).outcome()),
                    () -> assertEquals(GitOutcome.NOT_A_REPOSITORY,
                            git.readFile(new ReadFileInput(plain, commit, "data/test.csv")).outcome()));
        }
    }

    static Stream<Arguments> refusedRemotes() {
        return Stream.of(
                Arguments.of("plain http under the https policy", "http://github.com/example/alpha",
                        RemotePolicy.httpsOrigin(URI.create("https://github.com"))),
                Arguments.of("a foreign https origin", "https://gitlab.example.com/example/alpha",
                        RemotePolicy.httpsOrigin(URI.create("https://github.com"))),
                Arguments.of("a network remote under the local policy", "https://github.com/example/alpha",
                        RemotePolicy.localOnly()),
                Arguments.of("an ssh remote under the local policy", "ssh://git@github.com/example/alpha",
                        RemotePolicy.localOnly()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusedRemotes")
    @DisplayName("a remote the policy does not admit is refused before anything is sent")
    void remoteOutsidePolicy(String name, String url, RemotePolicy policy) {
        assertTrue(git.initCache(cache).isOk());

        var fetched = fetch(url, commit, policy);

        assertAll(
                () -> assertEquals(GitOutcome.REMOTE_ORIGIN_MISMATCH, fetched.outcome(), fetched.detail()),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, read(commit, "data/test.csv").outcome()));
    }
}
