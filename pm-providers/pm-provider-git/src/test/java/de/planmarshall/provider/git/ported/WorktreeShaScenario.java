/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.ported;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;

import de.planmarshall.provider.git.GitOperations.CommitInput;
import de.planmarshall.provider.git.GitOperations.Identity;
import de.planmarshall.provider.git.GitOperations.WorktreeSha;
import de.planmarshall.provider.git.GitOutcome;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.JGitOperations;
import de.planmarshall.provider.git.RemotePolicy;
import de.planmarshall.provider.git.ported.PortedFixtures.Directive;
import de.planmarshall.provider.git.ported.PortedFixtures.Fixture;
import de.planmarshall.provider.git.ported.PortedFixtures.Shape;

/**
 * Runs a {@code git.worktree-sha} fixture on a fresh repository and records its labelled results (PM-TEST-2).
 * <p>
 * The repository lives in a temporary directory and is created and committed through
 * {@link JGitOperations#init} and {@link JGitOperations#commit}, which open it with the refusing file system, so no
 * process is started. Staging a single path, setting a configuration key and detaching {@code HEAD} are no operations
 * of the git contract; they go through JGit directly. Every {@code result} directive calls
 * {@link JGitOperations#worktreeSha}, the public contract, and keeps the result under its label.
 * <p>
 * The legacy mapping of {@code script-shared/scripts/worktree_sha.py} lives here and nowhere else: the original's
 * {@code None} digest is {@link GitOutcome#UNAVAILABLE} (a failing step named in the detail), and its
 * {@code str | Path} argument is a {@link Path}. The {@code initial} repository of a fixture is the original suite's
 * {@code _init_repo}: one commit holding {@code tracked.txt} with the content {@code original\n}.
 * <p>
 * The directives, one per line, arguments separated by one space ({@code <content>} takes the rest of the line, with
 * the escapes {@code \n}, {@code \t}, {@code \0} and {@code \\}):
 *
 * <pre>
 * repository initial | unborn | none     the repository the fixture starts from (first directive)
 * write &lt;path&gt; &lt;content&gt;              write a file, creating its parent directories
 * stage &lt;path&gt;                         add the path to the index
 * commit &lt;message&gt;                     commit every change through the contract
 * delete &lt;path&gt;                        delete a file or a symbolic link
 * config &lt;section.key&gt; &lt;value&gt;         set a repository configuration key
 * detach                               check out the HEAD commit detached
 * symlink &lt;path&gt; &lt;target&gt;              create a symbolic link
 * unreadable &lt;path&gt;                    remove every permission of a file
 * result &lt;label&gt;                       record git.worktree-sha under a label
 * same | different &lt;label&gt; &lt;label&gt;     expected relation of two successful results
 * unavailable &lt;label&gt; &lt;step&gt;           expected unavailable result failing in a step
 * </pre>
 *
 * @since 0.1
 */
final class WorktreeShaScenario {

    /** The class-path directory of the fixtures. */
    static final String DIRECTORY = "ported/worktree-sha";

    /** The capability of a fixture that needs POSIX permissions enforced on the running user. */
    static final String POSIX_PERMISSIONS = "posix-permissions";

    /** The directives of a {@code git.worktree-sha} fixture. */
    static final Map<String, Shape> GRAMMAR = Map.ofEntries(
            Map.entry("repository", new Shape(1, false)),
            Map.entry("write", new Shape(2, true)),
            Map.entry("stage", new Shape(1, false)),
            Map.entry("commit", new Shape(1, true)),
            Map.entry("delete", new Shape(1, false)),
            Map.entry("config", new Shape(2, false)),
            Map.entry("detach", new Shape(0, false)),
            Map.entry("symlink", new Shape(2, false)),
            Map.entry("unreadable", new Shape(1, false)),
            Map.entry("result", new Shape(1, false)),
            Map.entry("same", new Shape(2, false)),
            Map.entry("different", new Shape(2, false)),
            Map.entry("unavailable", new Shape(2, false)));

    private static final Identity AUTHOR = new Identity("Ported Test", "ported@example.com");
    private static final String TRACKED = "tracked.txt";
    private static final String TRACKED_CONTENT = "original\n";

    private WorktreeShaScenario() {
    }

    /**
     * The outcome of a run: the labelled results and the expectations to check against them.
     *
     * @param fixture      the fixture that ran
     * @param results      the {@code git.worktree-sha} result per label, in recording order
     * @param expectations the {@code same}, {@code different} and {@code unavailable} directives
     */
    record Run(Fixture fixture, Map<String, GitResult<WorktreeSha>> results, List<Directive> expectations) {
    }

    /**
     * @return every fixture of the directory
     */
    static List<Fixture> fixtures() {
        return PortedFixtures.load(DIRECTORY, GRAMMAR);
    }

    /**
     * Runs the steps of a fixture.
     *
     * @param fixture the fixture
     * @param temp    an empty temporary directory
     * @return the labelled results and the expectations
     * @throws IOException if a step cannot write the fixture tree
     * @throws GitAPIException if a JGit step fails
     */
    static Run run(Fixture fixture, Path temp) throws IOException, GitAPIException {
        var git = new JGitOperations(RemotePolicy.localOnly());
        Path root = temp.resolve("repo");
        var results = new LinkedHashMap<String, GitResult<WorktreeSha>>();
        var expectations = new ArrayList<Directive>();
        List<Directive> directives = fixture.directives();
        Directive first = directives.getFirst();
        if (!"repository".equals(first.keyword())) {
            throw new IllegalArgumentException("fixture " + fixture + " does not start with a repository directive");
        }
        repository(git, root, first.arguments().getFirst(), fixture);
        for (Directive directive : directives.subList(1, directives.size())) {
            List<String> args = directive.arguments();
            switch (directive.keyword()) {
                case "write" -> write(root.resolve(args.get(0)), unescape(args.get(1)));
                case "stage" -> {
                    try (Git jgit = Git.open(root.toFile())) {
                        jgit.add().addFilepattern(args.getFirst()).call();
                    }
                }
                case "commit" -> commit(git, root, args.getFirst(), fixture);
                case "delete" -> Files.delete(root.resolve(args.getFirst()));
                case "config" -> config(root, args.get(0), args.get(1));
                case "detach" -> {
                    try (Git jgit = Git.open(root.toFile())) {
                        ObjectId head = jgit.getRepository().resolve(Constants.HEAD);
                        jgit.checkout().setName(head.name()).call();
                    }
                }
                case "symlink" -> Files.createSymbolicLink(root.resolve(args.get(0)), Path.of(args.get(1)));
                case "unreadable" -> Files.setPosixFilePermissions(root.resolve(args.getFirst()),
                        PosixFilePermissions.fromString("---------"));
                case "result" -> {
                    if (results.put(args.getFirst(), git.worktreeSha(root)) != null) {
                        throw new IllegalArgumentException("fixture " + fixture + " line " + directive.line()
                                + " records the label " + args.getFirst() + " twice");
                    }
                }
                case "same", "different", "unavailable" -> expectations.add(directive);
                case "repository" -> throw new IllegalArgumentException(
                        "fixture " + fixture + " line " + directive.line() + " names a second repository");
                default -> throw new IllegalArgumentException(
                        "fixture " + fixture + " line " + directive.line() + " has no step " + directive.keyword());
            }
        }
        return new Run(fixture, results, List.copyOf(expectations));
    }

    private static void repository(JGitOperations git, Path root, String kind, Fixture fixture) throws IOException {
        switch (kind) {
            case "none" -> Files.createDirectories(root);
            case "unborn" -> require(git.init(root, "main"), fixture, "init");
            case "initial" -> {
                require(git.init(root, "main"), fixture, "init");
                write(root.resolve(TRACKED), TRACKED_CONTENT);
                commit(git, root, "init", fixture);
            }
            default -> throw new IllegalArgumentException("fixture " + fixture + " names an unknown repository " + kind);
        }
    }

    private static void commit(JGitOperations git, Path root, String message, Fixture fixture) {
        require(git.commit(new CommitInput(root, message, AUTHOR, AUTHOR)), fixture, "commit");
    }

    private static void require(GitResult<?> result, Fixture fixture, String step) {
        if (!result.isOk()) {
            throw new IllegalStateException("fixture " + fixture + " step " + step + " failed: " + result.outcome()
                    + " " + result.detail());
        }
    }

    private static void config(Path root, String key, String value) throws IOException {
        int dot = key.indexOf('.');
        if (dot <= 0 || dot == key.length() - 1) {
            throw new IllegalArgumentException("configuration key " + key + " is not <section>.<name>");
        }
        try (Git jgit = Git.open(root.toFile())) {
            StoredConfig config = jgit.getRepository().getConfig();
            config.setString(key.substring(0, dot), null, key.substring(dot + 1), value);
            config.save();
        }
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    /**
     * Resolves the escapes {@code \n}, {@code \t}, {@code \0} and {@code \\} of a fixture content.
     *
     * @param content the content as written in the fixture
     * @return the content to write
     */
    static String unescape(String content) {
        var out = new StringBuilder(content.length());
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c != '\\') {
                out.append(c);
                continue;
            }
            if (i + 1 == content.length()) {
                throw new IllegalArgumentException("content ends with an unfinished escape: " + content);
            }
            char next = content.charAt(++i);
            out.append(switch (next) {
                case 'n' -> '\n';
                case 't' -> '\t';
                case '0' -> '\0';
                case '\\' -> '\\';
                default -> throw new IllegalArgumentException("content has an unknown escape \\" + next + ": " + content);
            });
        }
        return out.toString();
    }
}
