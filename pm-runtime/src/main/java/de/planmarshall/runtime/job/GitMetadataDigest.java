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

    private static final String GIT = ".git";
    private static final String GITDIR_PREFIX = "gitdir:";
    private static final String CONFIG = "config";
    private static final String CONFIG_WORKTREE = "config.worktree";
    private static final String HOOKS = "hooks";
    private static final String INCLUDE = "include";
    private static final String INCLUDE_IF = "includeif";
    private static final String PATH = "path";
    private static final String EQUALS = "=";

    /** The administration files of a linked worktree that are digested when they exist. */
    private static final List<String> ADMINISTRATION_FILES = List.of("gitdir", "commondir", "config.worktree");

    /** A section header of a git config file. */
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^\\]]+)]\\s*$");

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
            failClosed(parts, failures);
            return parts;
        }
        var config = gitDirectory.resolve(CONFIG);
        digestFile(root, config, parts, failures);
        if (Files.exists(config, LinkOption.NOFOLLOW_LINKS)) {
            includes(root, config, parts, failures, 0, new HashSet<>());
        }
        digestFile(root, gitDirectory.resolve(CONFIG_WORKTREE), parts, failures);
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
            int depth, Set<Path> visited) {
        if (depth > MAX_INCLUDE_DEPTH) {
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
                failures.add(name(root, target));
                continue;
            }
            digestFile(root, target, parts, failures);
            includes(root, target, parts, failures, depth + 1, visited);
        }
    }

    /** The paths a git config file includes, and whether a value could not be resolved. */
    private record IncludeScan(List<Path> paths, boolean unreadable) {
    }

    /** @return the paths a git config file includes, resolved against its directory */
    private static IncludeScan includePaths(Path file) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException _) {
            return new IncludeScan(List.of(), true);
        }
        var includes = new ArrayList<Path>();
        var inIncludeSection = false;
        for (var line : lines) {
            var section = SECTION.matcher(line);
            if (section.matches()) {
                var name = section.group(1).trim().toLowerCase(Locale.ROOT);
                inIncludeSection = INCLUDE.equals(name) || name.startsWith(INCLUDE_IF);
                continue;
            }
            if (!inIncludeSection) {
                continue;
            }
            var separator = line.indexOf(EQUALS);
            if (separator < 0) {
                continue;
            }
            var key = line.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            if (!PATH.equals(key)) {
                continue;
            }
            var value = unquote(comment(line.substring(separator + 1)));
            if (value.isEmpty()) {
                continue;
            }
            if (value.startsWith("%(")) {
                return new IncludeScan(List.of(), true);
            }
            includes.add(resolve(file.getParent(), expandHome(value)));
        }
        return new IncludeScan(List.copyOf(includes), false);
    }

    /** @return the value without a trailing comment, trimmed */
    private static String comment(String value) {
        for (var index = 0; index < value.length(); index++) {
            var character = value.charAt(index);
            if ((character == '#' || character == ';')
                    && (index == 0 || Character.isWhitespace(value.charAt(index - 1)))) {
                return value.substring(0, index).strip();
            }
        }
        return value.strip();
    }

    private static String unquote(String value) {
        return value.replace("\"", "").replace("'", "");
    }

    private static String expandHome(String value) {
        if ("~".equals(value)) {
            return System.getProperty("user.home");
        }
        return value.startsWith("~/") ? System.getProperty("user.home") + value.substring(1) : value;
    }

    private static void hooks(Path root, Path directory, SortedMap<String, String> parts, Set<String> failures) {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> files;
        try (var children = Files.list(directory)) {
            files = children.filter(child -> Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException _) {
            failures.add(name(root, directory));
            return;
        }
        for (var file : files) {
            var digest = digest(file);
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

    private static String digest(Path file) {
        try {
            return sha256(Files.readAllBytes(file));
        } catch (IOException _) {
            return null;
        }
    }

    private static Path administration(Path linkFile) {
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
