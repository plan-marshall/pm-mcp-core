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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.domain.EmailGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
@EnableGeneratorController
@DisplayName("JGitOperations cache repository operations")
class JGitCacheRepositoryTest {

    /** The fixture path of the plain file of a source repository. */
    static final String CSV_PATH = "data/test.csv";
    /** The fixture path of the LFS pointer of a source repository. */
    static final String POINTER_PATH = "data/big.jsonl";
    /** The length of an abbreviated commit id: a boundary of the identity rule. */
    private static final int ABBREVIATED = 12;

    @TempDir
    Path temp;

    private JGitOperations git;
    private Path source;
    private Path cache;
    private String commit;
    private String csv;
    private String pointer;

    /** @return a generated full commit id: 40 lower-case hex digits */
    static String commitId() {
        return Generators.strings("0123456789abcdef", 40, 40).next();
    }

    /** @return generated text of one line, ended by a line feed */
    static String line() {
        return Generators.letterStrings(1, 40).next() + "\n";
    }

    /**
     * @return a generated LFS pointer; its three keys and their order are the wire format of the Git LFS
     *         specification, the object id and size are generated
     */
    static String lfsPointer() {
        return "version https://git-lfs.github.com/spec/v1\noid sha256:"
                + Generators.strings("0123456789abcdef", 64, 64).next() + "\nsize "
                + Generators.integers(1, 100_000).next() + "\n";
    }

    /**
     * Creates a source repository with a plain file and an LFS pointer, configured to serve any commit by its id.
     *
     * @param git     the operations creating it
     * @param root    the working tree of the source repository
     * @param csv     the content of the plain file {@value #CSV_PATH}
     * @param pointer the content of the LFS pointer {@value #POINTER_PATH}
     * @return the id of its one commit
     */
    static String sourceRepository(JGitOperations git, Path root, String csv, String pointer) throws IOException {
        var author = new Identity(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
        assertTrue(git.init(root, Generators.strings("ghijklmnopqrstuvwxyz", 4, 12).next()).isOk(),
                "the source repository is initialised");
        Files.createDirectories(root.resolve("data"));
        Files.writeString(root.resolve(CSV_PATH), csv, UTF_8);
        Files.writeString(root.resolve(POINTER_PATH), pointer, UTF_8);
        var committed = git.commit(new CommitInput(root, Generators.letterStrings(3, 30).next(), author, author));
        assertEquals(GitOutcome.OK, committed.outcome(), "the source repository's commit is made");
        try (Git jgit = Git.open(root.toFile())) {
            var config = jgit.getRepository().getConfig();
            // the configuration key by which a remote serves any commit by its id
            config.setBoolean("uploadpack", null, "allowAnySHA1InWant", true);
            config.save();
        }
        return committed.value().orElseThrow().id();
    }

    @BeforeEach
    void repositories() throws IOException {
        git = new JGitOperations(RemotePolicy.localOnly());
        csv = line() + line();
        pointer = lfsPointer();
        // fixture paths of the source repository and the cache
        source = temp.resolve("source");
        commit = sourceRepository(git, source, csv, pointer);
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
                    () -> assertEquals(GitOutcome.OK, first.outcome(), "the first initCache creates the cache"),
                    () -> assertEquals(first, second, "a second initCache returns the same result"),
                    () -> assertEquals(cache.toRealPath(), first.value().orElseThrow(),
                            "the result is the canonical cache path"),
                    () -> assertTrue(Files.isDirectory(cache.resolve("objects")),
                            "the bare cache holds an objects directory"),
                    () -> assertFalse(Files.exists(cache.resolve(".git")), "a bare cache holds no .git directory"));
        }

        @Test
        @DisplayName("refuses a directory that holds anything but a repository")
        void notARepository() throws Exception {
            Files.createDirectories(cache);
            Files.writeString(cache.resolve(Generators.letterStrings(3, 12).next() + ".txt"), line());

            assertEquals(GitOutcome.NOT_A_REPOSITORY, git.initCache(cache).outcome(),
                    "a directory of plain files is no cache");
        }

        @Test
        @DisplayName("refuses a working tree, which is no bare repository")
        void workingTree() {
            assertEquals(GitOutcome.NOT_A_REPOSITORY, git.initCache(source.resolve(".git")).outcome(),
                    "the git directory of a work tree is no cache");
        }

        @Test
        @DisplayName("a cache that cannot be created, below a regular file, is a failure")
        void cannotCreate() throws Exception {
            var file = Files.writeString(temp.resolve("cache-file"), line());

            var result = git.initCache(file.resolve("alpha.git"));

            assertEquals(GitOutcome.FAILED, result.outcome(), "a cache below a regular file cannot be made");
        }
    }

    @Nested
    @DisplayName("fetchCommit and readFile")
    class FetchAndRead {

        @BeforeEach
        void initCache() {
            assertTrue(git.initCache(cache).isOk(), "the cache repository is initialised");
        }

        @Test
        @DisplayName("fetches the pinned commit by its id and reads a plain file and an LFS pointer raw")
        void fetchesAndReads() {
            var fetched = fetch(url(), commit, RemotePolicy.localOnly());

            var file = read(commit, CSV_PATH);
            var lfs = read(commit, POINTER_PATH);

            assertAll(
                    () -> assertEquals(GitOutcome.OK, fetched.outcome(), "the pinned commit is fetched by its id"),
                    () -> assertEquals(commit, fetched.value().orElseThrow(), "the fetch returns the pinned commit id"),
                    () -> assertArrayEquals(csv.getBytes(UTF_8), file.value().orElseThrow(),
                            "the plain file is read as it is stored"),
                    () -> assertArrayEquals(pointer.getBytes(UTF_8), lfs.value().orElseThrow(),
                            "the LFS file is read as its pointer"));
        }

        @Test
        @DisplayName("a commit already in the cache is served without contacting the remote")
        void cachedCommit() throws Exception {
            assertTrue(fetch(url(), commit, RemotePolicy.localOnly()).isOk(), "the first fetch fills the cache");
            FileUtils.delete(source.toFile(), FileUtils.RECURSIVE);

            var again = fetch(url(), commit, RemotePolicy.localOnly());

            assertAll(
                    () -> assertEquals(GitOutcome.OK, again.outcome(), "a cached commit needs no remote"),
                    () -> assertTrue(read(commit, CSV_PATH).isOk(), "the cached commit's file is readable"));
        }

        @Test
        @DisplayName("a commit the remote does not have is an unknown revision")
        void unknownCommit() {
            var fetched = fetch(url(), commitId(), RemotePolicy.localOnly());

            assertEquals(GitOutcome.UNKNOWN_REVISION, fetched.outcome(), "an unserved commit is an unknown revision");
        }

        @Test
        @DisplayName("an abbreviated commit id is an unknown revision, nothing fetched")
        void abbreviatedCommit() {
            var fetched = fetch(url(), commit.substring(0, ABBREVIATED), RemotePolicy.localOnly());

            assertEquals(GitOutcome.UNKNOWN_REVISION, fetched.outcome(), "an abbreviated id is an unknown revision");
        }

        @Test
        @DisplayName("a remote that does not answer is a failure")
        void missingRemote() {
            var fetched = fetch(temp.resolve("gone").toUri().toString(), commit, RemotePolicy.localOnly());

            assertEquals(GitOutcome.FAILED, fetched.outcome(), "a remote that does not answer is a failure");
        }

        @Test
        @DisplayName("a commit not fetched into the cache is an unknown revision to readFile")
        void readUnfetchedCommit() {
            assertEquals(GitOutcome.UNKNOWN_REVISION, read(commit, CSV_PATH).outcome(),
                    "an unfetched commit is unknown to readFile");
        }

        @Test
        @DisplayName("a path missing at the commit, or a directory, is not found")
        void missingPath() {
            assertTrue(fetch(url(), commit, RemotePolicy.localOnly()).isOk(), "the pinned commit is fetched");

            // the extension of no fixture file, so a generated name never matches data/test.csv
            assertAll(
                    () -> assertEquals(GitOutcome.PATH_NOT_FOUND,
                            read(commit, "data/" + Generators.letterStrings(3, 12).next() + ".absent").outcome(),
                            "a path absent at the commit is not found"),
                    () -> assertEquals(GitOutcome.PATH_NOT_FOUND, read(commit, "data").outcome(),
                            "a directory is no file and is not found"));
        }

        @Test
        @DisplayName("a file whose object the cache lost is a failure to readFile, not a missing path")
        void lostObject() throws Exception {
            var fileName = Generators.letterStrings(3, 12).next() + ".csv";
            ObjectId blob;
            ObjectId cached;
            try (Repository repository = new FileRepositoryBuilder().setGitDir(cache.toFile()).setBare().build();
                 ObjectInserter inserter = repository.newObjectInserter()) {
                blob = inserter.insert(Constants.OBJ_BLOB, csv.getBytes(UTF_8));
                var tree = new TreeFormatter();
                tree.append(fileName, FileMode.REGULAR_FILE, blob);
                var commitBuilder = new CommitBuilder();
                commitBuilder.setTreeId(inserter.insert(tree));
                var ident = new PersonIdent(Generators.letterStrings(3, 12).next(), new EmailGenerator().next());
                commitBuilder.setAuthor(ident);
                commitBuilder.setCommitter(ident);
                commitBuilder.setMessage(Generators.letterStrings(3, 30).next());
                cached = inserter.insert(commitBuilder);
                inserter.flush();
            }
            // the loose object layout of git: a directory of the first two hex digits, a file of the rest
            Files.delete(cache.resolve("objects").resolve(blob.name().substring(0, 2))
                    .resolve(blob.name().substring(2)));

            var result = read(cached.name(), fileName);

            assertEquals(GitOutcome.FAILED, result.outcome(), "a lost object is a failure, not a missing path");
        }

        @Test
        @DisplayName("a cache that is no repository is refused")
        void cacheIsNoRepository() throws Exception {
            var plain = Files.createDirectories(temp.resolve("plain"));

            assertAll(
                    () -> assertEquals(GitOutcome.NOT_A_REPOSITORY, git.fetchCommit(
                                    new FetchCommitInput(plain, url(), commit, RemotePolicy.localOnly())).outcome(),
                            "fetchCommit refuses a cache that is no repo"),
                    () -> assertEquals(GitOutcome.NOT_A_REPOSITORY,
                            git.readFile(new ReadFileInput(plain, commit, CSV_PATH)).outcome(),
                            "readFile refuses a cache that is no repository"));
        }
    }

    /**
     * @return the remotes outside a policy; the URLs and their schemes are the contract of the remote-policy grammar
     */
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
        assertTrue(git.initCache(cache).isOk(), "the cache repository is initialised");

        var fetched = fetch(url, commit, policy);

        assertAll(
                () -> assertEquals(GitOutcome.REMOTE_ORIGIN_MISMATCH, fetched.outcome(),
                        "a remote outside the policy is refused"),
                () -> assertEquals(GitOutcome.UNKNOWN_REVISION, read(commit, CSV_PATH).outcome(),
                        "nothing was fetched into the cache"));
    }
}
