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

import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The baseline of a project's git metadata and its comparison (PM-WATCH-JOB-48): {@code record} takes the digest at a
 * worker's spawn, {@code verify} compares the metadata at the two points the specification names, and
 * {@code rebaseline} is the operator's act that ends a hold. The guard never changes a file and never repairs the git
 * metadata.
 * <p>
 * The baseline is kept in memory per project. A runtime restart loses it, and the recovery ends a running worker at
 * restart, so the next worker's spawn records a fresh one; the operator's {@code rebaseline} is the act that clears a
 * false difference after a legitimate build job changed the metadata.
 * <p>
 * A project without git has no baseline and never compares.
 *
 * @since 0.1
 */
public final class GitMetadataGuard {

    private final Map<Path, SortedMap<String, String>> baselines = new ConcurrentHashMap<>();

    /**
     * The projects whose metadata could not be read when a baseline would have been taken. Such a project holds no
     * baseline, so every comparison answers {@link Changed} with the fail-closed part until the operator's
     * {@link #rebaseline(Path)} records the accepted state.
     */
    private final Set<Path> withheld = ConcurrentHashMap.newKeySet();

    /**
     * Takes the baseline of a project when it holds none and its metadata can be read. A tree that is unreadable at the
     * spawn takes no baseline: {@link #verify(Path)} then answers {@link Changed} until the operator's
     * {@link #rebaseline(Path)} accepts it. The first readable spawn of a project in this runtime takes the baseline; a
     * later spawn keeps it, so a change made between two spawns is not baselined away.
     *
     * @param projectRoot the canonical enrolled project root
     */
    public void record(Path projectRoot) {
        var key = key(projectRoot);
        var digest = GitMetadataDigest.of(projectRoot);
        if (digest.containsKey(GitMetadataDigest.UNREADABLE)) {
            withheld.add(key);
            return;
        }
        withheld.remove(key);
        baselines.putIfAbsent(key, digest);
    }

    /**
     * Compares the project's git metadata with the baseline.
     *
     * @param projectRoot the canonical enrolled project root
     * @return {@link Unchanged} for a project without a baseline or with an unchanged metadata tree, otherwise
     *         {@link Changed} with the parts that differ; an unreadable tree whose baseline was withheld is
     *         {@link Changed} with the part {@code unreadable}
     */
    public Verdict verify(Path projectRoot) {
        var key = key(projectRoot);
        if (withheld.contains(key)) {
            return new Changed(new TreeSet<>(Set.of(GitMetadataDigest.UNREADABLE)));
        }
        var baseline = baselines.get(key);
        if (baseline == null) {
            return new Unchanged();
        }
        var parts = GitMetadataDigest.differences(baseline, GitMetadataDigest.of(projectRoot));
        return parts.isEmpty() ? new Unchanged() : new Changed(parts);
    }

    /**
     * Moves the baseline to the metadata as it is now: the operator's act that ends a hold. An unreadable tree can be
     * accepted this way, which stores the fail-closed state as the baseline and ends the refusal.
     *
     * @param projectRoot the canonical enrolled project root
     */
    public void rebaseline(Path projectRoot) {
        var key = key(projectRoot);
        baselines.put(key, GitMetadataDigest.of(projectRoot));
        withheld.remove(key);
    }

    private static Path key(Path projectRoot) {
        return Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
    }

    /** What a comparison found. */
    public sealed interface Verdict permits Unchanged, Changed {
    }

    /** The git metadata is unchanged, or the project holds no baseline to compare with. */
    public record Unchanged() implements Verdict {
    }

    /**
     * The git metadata differs.
     *
     * @param parts the names of the parts that differ, sorted; never their contents
     * @since 0.1
     */
    public record Changed(SortedSet<String> parts) implements Verdict {

        /** @throws NullPointerException if the parts are {@code null} */
        public Changed {
            Objects.requireNonNull(parts, "parts");
            parts = Collections.unmodifiableSortedSet(new TreeSet<>(parts));
        }
    }
}
