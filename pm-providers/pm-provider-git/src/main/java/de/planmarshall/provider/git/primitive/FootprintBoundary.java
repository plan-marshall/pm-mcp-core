/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;

import de.planmarshall.provider.git.primitive.StagingSets.StagingSet;

/**
 * The footprint check a commit runs before anything is staged (PM-WF-16, Stage 4): (1) no dirty path of the target
 * checkout lies outside the staging set of its scope, (2) no pinned build file changed, and (3) for a plan, no path of
 * the main checkout was changed. The first half is judged here from the staging set; the other two are the breaches the
 * commit site observed.
 * <p>
 * A violation names the offending files, bounded to {@value #OFFENDING_FILES_LIMIT} names with the total count.
 *
 * @since 0.1
 */
@UtilityClass
public final class FootprintBoundary {

    /** The most offending files a violation names. */
    public static final int OFFENDING_FILES_LIMIT = 50;

    /**
     * Judges the footprint check.
     *
     * @param stagingSet           the staging set of the scope; its {@code outside} paths break the worktree boundary
     * @param pinnedFilesChanged   the pinned build files that changed in the target checkout
     * @param mainCheckoutBreaches the paths of the main checkout a plan changed; empty for any other scope
     * @return {@link Within} when nothing breaks the boundary, else {@link Violated} naming the offending files
     */
    public static Verdict check(StagingSet stagingSet, Collection<String> pinnedFilesChanged,
            Collection<String> mainCheckoutBreaches) {
        Objects.requireNonNull(stagingSet, "stagingSet");
        Objects.requireNonNull(pinnedFilesChanged, "pinnedFilesChanged");
        Objects.requireNonNull(mainCheckoutBreaches, "mainCheckoutBreaches");
        var offending = new TreeSet<>(stagingSet.outside());
        offending.addAll(pinnedFilesChanged);
        offending.addAll(mainCheckoutBreaches);
        if (offending.isEmpty()) {
            return new Within();
        }
        return new Violated(offending.stream().limit(OFFENDING_FILES_LIMIT).toList(), offending.size());
    }

    /**
     * The verdict of the footprint check.
     *
     * @since 0.1
     */
    public sealed interface Verdict permits Within, Violated {
    }

    /**
     * Nothing breaks the boundary.
     *
     * @since 0.1
     */
    public record Within() implements Verdict {
    }

    /**
     * The boundary is broken.
     *
     * @param offendingFiles the offending files, sorted, at most {@value FootprintBoundary#OFFENDING_FILES_LIMIT}
     * @param offendingCount the number of all offending files, at least the size of {@code offendingFiles}
     * @since 0.1
     */
    public record Violated(List<String> offendingFiles, int offendingCount) implements Verdict {

        /**
         * @param offendingFiles the offending files, copied
         * @param offendingCount the number of all offending files
         * @throws IllegalArgumentException if the list is empty, longer than the limit or longer than the count
         */
        public Violated {
            offendingFiles = List.copyOf(offendingFiles);
            if (offendingFiles.isEmpty() || offendingFiles.size() > OFFENDING_FILES_LIMIT
                    || offendingCount < offendingFiles.size()) {
                throw new IllegalArgumentException("offendingFiles must name 1 to %d of the %d offending files"
                        .formatted(OFFENDING_FILES_LIMIT, offendingCount));
            }
        }
    }
}
