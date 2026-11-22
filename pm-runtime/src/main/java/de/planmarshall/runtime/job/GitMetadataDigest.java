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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The digest of a project's git metadata (PM-WATCH-JOB-48): the bytes of the git directory's config and of every file it
 * includes, every regular file of its hook directory with its mode, and the bytes of the link file and of the
 * administration files ({@code gitdir}, {@code commondir}, {@code config.worktree}) of every linked worktree.
 * <p>
 * {@code HEAD}, the index, refs, logs, objects and the tracked source stay out, because a server {@code git} job changes
 * them legitimately. The include scan is hand-written (no JGit, no new dependency); it reads {@code include.path} and
 * {@code includeIf.*.path} relative to the including file and follows them to a fixed bound.
 * <p>
 * The digest is read-only: it reads files, never writes or repairs one, starts no process and stores the part names with
 * their digests, never the file contents. An input that exists but cannot be read, an include cycle and a bound
 * exceeded are the fail-closed part {@link #UNREADABLE}: an unreadable metadata tree is a difference, never a pass.
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Git Metadata); watch
 * {@code doc/implementation-watch/job-runtime.adoc} ({@code PM-WATCH-JOB-48}).
 *
 * @since 0.1
 */
@UtilityClass
public final class GitMetadataDigest {

    /** The part of a fail-closed digest: an input that could not be read, a cycle or a bound exceeded. */
    public static final String UNREADABLE = "unreadable";

    /** How deep an include chain is followed, the bound git itself applies to a running process. */
    static final int MAX_INCLUDE_DEPTH = 10;

    private static final String GITDIR_PREFIX = "gitdir:";
    private static final String CONFIG = "config";
    private static final String CONFIG_WORKTREE = "config.worktree";
    private static final String HOOKS = "hooks";
    private static final String INCLUDE = "include";
    private static final String INCLUDE_IF = "includeif";
    private static final String PATH = "path";
    private static final String EQUALS = "=";

    /** The administration files of a linked worktree that are digested when they exist. */
    private static final List<String> ADMINISTRATION_FILES = List.of("gitdir", "commondir", CONFIG_WORKTREE);

    /** Largest metadata input the digest reads; a larger file is fail-closed rather than read. */
    private static final long MAX_INPUT_BYTES = 8L * 1024 * 1024;

    /** How many include files one digest may visit; a larger traversal is fail-closed. */
    private static final int MAX_INCLUDE_FILES = 1024;

    /** A section header of a git config file, with an optional key or comment after the bracket. */
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^\\]]+)](.*)$");

    /**
     * Digests the git metadata of a project.
     *
     * @param projectRoot the canonical enrolled project root
     * @return the part names with the digest of their bytes, sorted; empty for a project without git
     */
    public static SortedMap<String, String> of(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        var root = projectRoot.toAbsolutePath().normalize();
        var paths = GitMetadataPaths.of(root);
        var parts = new TreeMap<String, String>();
        var failures = new TreeSet<String>();
        for (var refusal : paths.refused()) {
            failures.add(name(root, refusal.path()));
        }
        var gitDirectory = paths.gitDirectory();
        if (gitDirectory == null) {
            // a pointer file that was skipped is not a project without git: the tree could not be read, so it fails
            // closed instead of baselining an empty digest
            for (var skipped : paths.skipped()) {
                failures.add(name(root, skipped.path()));
            }
            failClosed(parts, failures);
            return parts;
        }
        if (!Files.isDirectory(gitDirectory, LinkOption.NOFOLLOW_LINKS)) {
            // a git file that names no directory leaves the tree unreadable, never an empty digest to baseline
            failures.add(name(root, gitDirectory));
            failClosed(parts, failures);
            return parts;
        }
        var config = gitDirectory.resolve(CONFIG);
        var worktreeConfig = gitDirectory.resolve(CONFIG_WORKTREE);
        var budget = new TraversalBudget();
        digestFile(root, config, parts, failures);
        includes(root, config, parts, failures, 0, new HashSet<>(), budget);
        digestFile(root, worktreeConfig, parts, failures);
        includes(root, worktreeConfig, parts, failures, 0, new HashSet<>(), budget);
        hooks(root, gitDirectory.resolve(HOOKS), parts, failures);
        for (var linkFile : paths.linkFiles()) {
            digestFile(root, linkFile, parts, failures);
            var administration = administration(linkFile);
            if (administration == null) {
                failures.add(name(root, linkFile));
                continue;
            }
            for (var file : ADMINISTRATION_FILES) {
                var candidate = administration.resolve(file);
                if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                    digestFile(root, candidate, parts, failures);
                    if (CONFIG_WORKTREE.equals(file)) {
                        includes(root, candidate, parts, failures, 0, new HashSet<>(), budget);
                    }
                }
            }
        }
        failClosed(parts, failures);
        return parts;
    }

    /** Adds the fail-closed part when any input could not be read, so such a tree can never compare equal to a clean one. */
    private static void failClosed(SortedMap<String, String> parts, Set<String> failures) {
        if (!failures.isEmpty()) {
            parts.put(UNREADABLE, sha256(String.join("\n", failures).getBytes(StandardCharsets.UTF_8)));
        }
    }

    /**
     * @param baseline the parts of a recorded baseline
     * @param current  the parts read now
     * @return the names of the parts whose digest differs, or that only one side has
     */
    public static SortedSet<String> differences(Map<String, String> baseline, Map<String, String> current) {
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(current, "current");
        var names = new TreeSet<String>();
        names.addAll(baseline.keySet());
        names.addAll(current.keySet());
        var changed = new TreeSet<String>();
        for (var name : names) {
            if (!Objects.equals(baseline.get(name), current.get(name))) {
                changed.add(name);
            }
        }
        return changed;
    }

    private static void includes(Path root, Path file, SortedMap<String, String> parts, Set<String> failures,
            int depth, Set<Path> visited, TraversalBudget budget) {
        if (depth > MAX_INCLUDE_DEPTH) {
            failures.add(name(root, file));
            return;
        }
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        if (!budget.take()) {
            failures.add(name(root, file));
            return;
        }
        var canonical = file.toAbsolutePath().normalize();
        if (!visited.add(canonical)) {
            failures.add(name(root, canonical));
            return;
        }
        var scan = includePaths(file);
        if (scan.unreadable()) {
            failures.add(name(root, file));
        }
        for (var target : scan.paths()) {
            if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            digestFile(root, target, parts, failures);
            includes(root, target, parts, failures, depth + 1, visited, budget);
        }
        visited.remove(canonical);
    }

    /** The aggregate budget of one digest's include traversal, so a fan-out cannot multiply the work. */
    private static final class TraversalBudget {

        private int remaining = MAX_INCLUDE_FILES;

        /** @return whether one more file may be visited */
        boolean take() {
            return remaining-- > 0;
        }
    }

    /** The paths a git config file includes, and whether a value could not be resolved. */
    private record IncludeScan(List<Path> paths, boolean unreadable) {
    }

    /** @return the paths a git config file includes, resolved against its directory */
    private static IncludeScan includePaths(Path file) {
        List<String> lines;
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_INPUT_BYTES) {
                return new IncludeScan(List.of(), true);
            }
            lines = logicalLines(Files.readAllLines(file, StandardCharsets.UTF_8));
        } catch (IOException _) {
            return new IncludeScan(List.of(), true);
        }
        var includes = new ArrayList<Path>();
        var inIncludeSection = false;
        for (var line : lines) {
            var step = scan(line, inIncludeSection, file.getParent());
            inIncludeSection = step.inIncludeSection();
            if (step.unreadable()) {
                return new IncludeScan(List.of(), true);
            }
            if (step.include() != null) {
                includes.add(step.include());
            }
        }
        return new IncludeScan(List.copyOf(includes), false);
    }

    /**
     * @return the physical lines joined as git reads them: a trailing backslash joins the next line, so a value may
     *         continue on the following physical line
     */
    private static List<String> logicalLines(List<String> lines) {
        var logical = new ArrayList<String>();
        var current = new StringBuilder();
        for (var line : lines) {
            if (line.endsWith("\\")) {
                current.append(line, 0, line.length() - 1);
            } else {
                current.append(line);
                logical.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            logical.add(current.toString());
        }
        return logical;
    }

    /**
     * What scanning one line of a config file found.
     *
     * @param inIncludeSection whether the following line is inside an include section
     * @param include          the include the line names, or {@code null}
     * @param unreadable       whether the line names a value that cannot be resolved
     */
    private record ScanStep(boolean inIncludeSection, @Nullable Path include, boolean unreadable) {
    }

    /** @return what one line of the config file contributes */
    private static ScanStep scan(String line, boolean inIncludeSection, Path directory) {
        var section = SECTION.matcher(line);
        if (section.matches()) {
            var name = section.group(1).trim().toLowerCase(Locale.ROOT);
            var inSection = INCLUDE.equals(name) || name.startsWith(INCLUDE_IF);
            var remainder = section.group(2).strip();
            if (remainder.isEmpty() || remainder.startsWith("#") || remainder.startsWith(";")) {
                return new ScanStep(inSection, null, false);
            }
            return scan(remainder, inSection, directory);
        }
        if (!inIncludeSection) {
            return new ScanStep(false, null, false);
        }
        var separator = line.indexOf(EQUALS);
        if (separator < 0 || !PATH.equals(line.substring(0, separator).trim().toLowerCase(Locale.ROOT))) {
            return new ScanStep(true, null, false);
        }
        var value = value(line.substring(separator + 1));
        if (value.startsWith("%(")) {
            return new ScanStep(true, null, true);
        }
        try {
            return new ScanStep(true, value.isEmpty() ? null : resolve(directory, expandHome(value)), false);
        } catch (InvalidPathException _) {
            return new ScanStep(true, null, true);
        }
    }

    /**
     * @return the value as git's config parser gives it: surrounding quotes removed and a comment outside the quotes cut
     *         off, so a {@code #} inside a quoted path is part of the path
     */
    private static String value(String raw) {
        var value = new StringBuilder();
        var quoted = false;
        var index = 0;
        while (index < raw.length()) {
            var character = raw.charAt(index);
            var escaped = quoted && character == '\\' && index + 1 < raw.length();
            if (escaped) {
                index++;
                var escapedCharacter = raw.charAt(index);
                value.append(switch (escapedCharacter) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    default -> escapedCharacter;
                });
            } else if (character == '"') {
                quoted = !quoted;
            } else if (quoted || (character != '#' && character != ';')) {
                value.append(character);
            } else {
                break;
            }
            index++;
        }
        return value.toString().strip();
    }

    private static String expandHome(String value) {
        if ("~".equals(value)) {
            return System.getProperty("user.home");
        }
        return value.startsWith("~/") ? System.getProperty("user.home") + value.substring(1) : value;
    }

    private static void hooks(Path root, Path directory, SortedMap<String, String> parts, Set<String> failures) {
        if (Files.isSymbolicLink(directory)) {
            failures.add(name(root, directory));
            return;
        }
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> files;
        try (var children = Files.list(directory)) {
            files = children.filter(child -> !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException _) {
            failures.add(name(root, directory));
            return;
        }
        for (var file : files) {
            var digest = Files.isSymbolicLink(file) ? null : digest(file);
            if (digest == null) {
                failures.add(name(root, file));
                continue;
            }
            parts.put(name(root, file), digest + ":" + mode(file, failures, root));
        }
    }

    private static void digestFile(Path root, Path file, SortedMap<String, String> parts, Set<String> failures) {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        var digest = digest(file);
        if (digest == null) {
            failures.add(name(root, file));
            return;
        }
        parts.put(name(root, file), digest);
    }

    private static String mode(Path file, Set<String> failures, Path root) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file, LinkOption.NOFOLLOW_LINKS);
            return PosixFilePermissions.toString(permissions);
        } catch (IOException _) {
            failures.add(name(root, file));
            return UNREADABLE;
        } catch (UnsupportedOperationException _) {
            return "unknown";
        }
    }

    private static @Nullable String digest(Path file) {
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_INPUT_BYTES) {
                return null;
            }
            try (var input = Files.newInputStream(file)) {
                var bytes = input.readNBytes((int) MAX_INPUT_BYTES + 1);
                return bytes.length > MAX_INPUT_BYTES ? null : sha256(bytes);
            }
        } catch (IOException _) {
            return null;
        }
    }

    private static @Nullable Path administration(Path linkFile) {
        try {
            var content = Files.readString(linkFile).strip();
            if (!content.startsWith(GITDIR_PREFIX)) {
                return null;
            }
            var target = content.substring(GITDIR_PREFIX.length()).strip();
            if (target.isEmpty()) {
                return null;
            }
            return linkFile.getParent().resolve(target).toAbsolutePath().normalize();
        } catch (IOException _) {
            return null;
        }
    }

    private static Path resolve(Path directory, String value) {
        var path = Path.of(value);
        return (path.isAbsolute() ? path : directory.resolve(path)).toAbsolutePath().normalize();
    }

    private static String name(Path root, Path path) {
        var absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(root)) {
            return root.relativize(absolute).toString();
        }
        return absolute.toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is available on every Java platform", e);
        }
    }
}
