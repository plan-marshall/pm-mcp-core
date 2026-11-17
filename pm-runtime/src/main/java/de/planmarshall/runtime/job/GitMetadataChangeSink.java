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
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import lombok.Builder;

import de.planmarshall.core.store.JobId;

/**
 * The sink that is called when a project's git metadata differs from its baseline (PM-WATCH-JOB-48). The runtime's part
 * is the refusal of the job, the security audit record and this call; the assembly wires the sink to the plan's hold,
 * so the engine and the operator's act stay outside this repository.
 * <p>
 * The default records nothing, like {@code NO_AUDIT}: a runtime that was given no sink still refuses the job.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface GitMetadataChangeSink {

    /** The sink of a runtime whose caller names none: the change is neither reported nor recorded by it. */
    GitMetadataChangeSink NONE = _ -> {
    };

    /**
     * @param change the change that was found
     */
    void changed(Change change);

    /**
     * A difference of a project's git metadata.
     *
     * @param projectRoot the project whose metadata differed
     * @param point       where the comparison found it
     * @param jobId       the job that ended, or the {@code git} job that was refused
     * @param parts       the names of the parts that differ, sorted; never their contents
     * @since 0.1
     */
    @Builder
    record Change(Path projectRoot, Point point, JobId jobId, Set<String> parts) {

        /** @throws NullPointerException if a component is {@code null} */
        public Change {
            Objects.requireNonNull(projectRoot, "projectRoot");
            Objects.requireNonNull(point, "point");
            Objects.requireNonNull(jobId, "jobId");
            parts = Collections.unmodifiableSet(new TreeSet<>(Objects.requireNonNull(parts, "parts")));
        }

        /** @return the parts as a sorted, immutable list */
        public List<String> partNames() {
            return List.copyOf(parts);
        }
    }

    /** The two points at which the runtime compares the git metadata with the baseline. */
    enum Point {
        /** A worker ended; the comparison runs after its fate was recorded. */
        WORKER_END("worker_end"),
        /** A {@code git} job of the project is about to start; a difference refuses it. */
        GIT_JOB("git_job");

        private final String wireName;

        Point(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the point in the audit record */
        public String wireName() {
            return wireName;
        }
    }
}
