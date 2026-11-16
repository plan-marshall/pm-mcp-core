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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.Builder;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Finds the git metadata paths of a project (PM-SEC-5, PM-WATCH-JOB-48): the git directory of the repository and the
 * {@code .git} link file of every linked worktree, as values and never as an exception (R09).
 * <p>
 * The directory is the anchored project root's {@code .git} directory, or, when the root is itself a linked worktree,
 * the common git directory its {@code commondir} pointer names. The link files are the root's own {@code .git} file and
 * every {@code .git} file a record below {@code <git directory>/worktrees/} names. A record whose target is missing or
 * does not name its administration directory back is skipped and named with its reason. A pointer file that cannot be
 * read is a skip of its own, never a refusal of the whole result.
 * <p>
 * A symbolic link as the {@code .git} path, as the git directory or as a link file is refused as the value
 * {@link #PATH_TRAVERSAL_REJECTED}, never followed, in the pattern of {@code PathConfinement}. No git process runs and
 * no JGit is used.
 * <p>
 * The result holds for the moment of the scan. A link replaced between the scan and the mount the launcher applies is
 * not seen here; the digest of the same paths at a worker's spawn and its comparison at the end are the net for it
 * ({@link GitMetadataDigest}).
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Git Metadata).
 *
 * @since 0.1
 */
@UtilityClass
public final class GitMetadataPaths {

    /** The name of the git directory and of a worktree's link file. */
    static final String GIT = ".git";

    /** The reason of a path refused because it is a symbolic link. */
    public static final String PATH_TRAVERSAL_REJECTED = "path_traversal_rejected";

    /** The reason of a worktree record whose target does not exist. */
    public static final String TARGET_MISSING = "target_missing";

    /** The reason of a worktree record that does not name its administration directory back. */
    public static final String NOT_NAMED_BACK = "not_named_back";

    /** The reason of a pointer file that cannot be read or names nothing. */
    public static final String UNREADABLE_POINTER = "unreadable_pointer";

    /** The prefix of a {@code .git} link file's record. */
    private static final String GITDIR_PREFIX = "gitdir:";

    /** The file of an administration directory that names the common git directory. */
    private static final String COMMONDIR = "commondir";

    /** The file of a worktree's administration directory that names the worktree's link file. */
    private static final String GITDIR = "gitdir";

    /** The directory of the worktree administration directories. */
    private static final String WORKTREES = "worktrees";

    /** A pointer file holds one path; a larger file is none. */
    private static final long POINTER_FILE_LIMIT = 8192;

    /**
     * Finds the metadata paths of a project.
     *
     * @param projectRoot the canonical enrolled project root
     * @return the git directory, the link files, what was skipped and what was refused; empty for a project without git
     */
    public static Result of(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        var root = projectRoot.toAbsolutePath().normalize();
        var dotGit = root.resolve(GIT);
        var attributes = attributes(dotGit);
        if (attributes == null) {
            return empty();
        }
        if (attributes.isSymbolicLink()) {
            return refused(dotGit);
        }
        var linkFiles = new ArrayList<Path>();
        var skipped = new ArrayList<Skipped>();
        var refused = new ArrayList<Refused>();
        @Nullable Path gitDirectory;
        if (attributes.isDirectory()) {
            gitDirectory = dotGit;
        } else if (attributes.isRegularFile()) {
            gitDirectory = linkedWorktreeGitDirectory(dotGit, skipped, refused);
            linkFiles.add(dotGit);
        } else {
            skipped.add(new Skipped(dotGit, UNREADABLE_POINTER));
            gitDirectory = null;
        }
        if (gitDirectory == null) {
            return new Result(null, List.copyOf(linkFiles), List.copyOf(skipped), List.copyOf(refused));
        }
        if (Files.isSymbolicLink(gitDirectory)) {
            refused.add(new Refused(gitDirectory, PATH_TRAVERSAL_REJECTED));
            return new Result(null, List.copyOf(linkFiles), List.copyOf(skipped), List.copyOf(refused));
        }
        scanWorktrees(gitDirectory, linkFiles, skipped, refused);
        return new Result(gitDirectory, sorted(linkFiles), List.copyOf(skipped), List.copyOf(refused));
    }

    /**
     * The git directory of a root that is itself a linked worktree: the {@code commondir} target of the administration
     * directory its {@code .git} link file names. A missing {@code commondir} record leaves the administration directory
     * itself, which is the layout of a gitfile that points at a git directory directly.
     */
    private static @Nullable Path linkedWorktreeGitDirectory(Path linkFile, List<Skipped> skipped,
            List<Refused> refused) {
        var administration = pointer(linkFile, GITDIR_PREFIX);
        if (administration == null) {
            skipped.add(new Skipped(linkFile, UNREADABLE_POINTER));
            return null;
        }
        var administrationDirectory = resolve(linkFile.getParent(), administration);
        if (Files.isSymbolicLink(administrationDirectory)) {
            refused.add(new Refused(administrationDirectory, PATH_TRAVERSAL_REJECTED));
            return null;
        }
        var common = pointer(administrationDirectory.resolve(COMMONDIR), "");
        if (common == null) {
            return administrationDirectory;
        }
        return resolve(administrationDirectory, common);
    }

    /** Adds every linked worktree's link file, skipping the records that do not name one. */
    private static void scanWorktrees(Path gitDirectory, List<Path> linkFiles, List<Skipped> skipped,
            List<Refused> refused) {
        var registry = gitDirectory.resolve(WORKTREES);
        if (!Files.isDirectory(registry, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        List<Path> records;
        try (var children = Files.list(registry)) {
            records = children.filter(child -> Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS))
                    .map(child -> child.resolve(GITDIR)).sorted(Comparator.comparing(Path::toString)).toList();
        } catch (IOException _) {
            skipped.add(new Skipped(registry, UNREADABLE_POINTER));
            return;
        }
        for (var record : records) {
            var target = pointer(record, "");
            if (target == null) {
                skipped.add(new Skipped(record, UNREADABLE_POINTER));
                continue;
            }
            var linkFile = resolve(record.getParent(), target);
            if (!Files.exists(linkFile, LinkOption.NOFOLLOW_LINKS)) {
                skipped.add(new Skipped(linkFile, TARGET_MISSING));
            } else if (Files.isSymbolicLink(linkFile)) {
                refused.add(new Refused(linkFile, PATH_TRAVERSAL_REJECTED));
            } else if (!namesBack(linkFile, record.getParent())) {
                skipped.add(new Skipped(record, NOT_NAMED_BACK));
            } else {
                linkFiles.add(linkFile);
            }
        }
    }

    /** @return whether the link file names the administration directory back */
    private static boolean namesBack(Path linkFile, Path administration) {
        var back = pointer(linkFile, GITDIR_PREFIX);
        if (back == null) {
            return false;
        }
        return resolve(linkFile.getParent(), back).equals(administration.toAbsolutePath().normalize());
    }

    /**
     * @return the path a pointer file names after its prefix, or {@code null} when the file is no regular file, is too
     *         large, lacks the prefix or names nothing
     */
    private static @Nullable String pointer(Path file, String prefix) {
        try {
            var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.size() > POINTER_FILE_LIMIT) {
                return null;
            }
            var content = Files.readString(file).strip();
            if (!content.startsWith(prefix)) {
                return null;
            }
            var target = content.substring(prefix.length()).strip();
            return target.isEmpty() ? null : target;
        } catch (IOException _) {
            return null;
        }
    }

    /** @return the pointer target resolved against its directory, absolute and normalized */
    private static Path resolve(Path directory, String target) {
        return directory.resolve(target).toAbsolutePath().normalize();
    }

    /** @return the attributes of the path without following a link, or {@code null} when nothing has that name */
    private static @Nullable BasicFileAttributes attributes(Path path) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException _) {
            return null;
        } catch (IOException _) {
            return null;
        }
    }

    private static Result empty() {
        return new Result(null, List.of(), List.of(), List.of());
    }

    private static Result refused(Path path) {
        return new Result(null, List.of(), List.of(), List.of(new Refused(path, PATH_TRAVERSAL_REJECTED)));
    }

    private static List<Path> sorted(List<Path> paths) {
        var unique = new LinkedHashSet<Path>();
        for (var path : paths) {
            unique.add(path.toAbsolutePath().normalize());
        }
        var sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.comparing(Path::toString));
        return List.copyOf(sorted);
    }

    /**
     * The git metadata paths of a project.
     *
     * @param gitDirectory the git directory; {@code null} for a project without git
     * @param linkFiles    the {@code .git} link files of the linked worktrees, sorted and free of duplicates
     * @param skipped      the worktree records that were skipped, with the reason
     * @param refused      the paths refused because they are symbolic links
     * @since 0.1
     */
    @Builder
    public record Result(@Nullable Path gitDirectory, List<Path> linkFiles, List<Skipped> skipped,
    List<Refused> refused) {

        /** @throws NullPointerException if a list is {@code null} */
        public Result {
            Objects.requireNonNull(linkFiles, "linkFiles");
            Objects.requireNonNull(skipped, "skipped");
            Objects.requireNonNull(refused, "refused");
        }

        /** @return every metadata path of the result: the git directory and the link files, sorted and deduplicated */
        public List<Path> paths() {
            var all = new ArrayList<Path>(linkFiles);
            if (gitDirectory != null) {
                all.add(gitDirectory);
            }
            return sorted(all);
        }
    }

    /**
     * A worktree record that was skipped.
     *
     * @param path   the record or the target it names
     * @param reason the reason of the skip
     * @since 0.1
     */
    public record Skipped(Path path, String reason) {

        /** @throws NullPointerException if a component is {@code null} */
        public Skipped {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * A path refused because it is a symbolic link.
     *
     * @param path   the refused path
     * @param reason the reason, always {@link #PATH_TRAVERSAL_REJECTED}
     * @since 0.1
     */
    public record Refused(Path path, String reason) {

        /** @throws NullPointerException if a component is {@code null} */
        public Refused {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(reason, "reason");
        }
    }
}
