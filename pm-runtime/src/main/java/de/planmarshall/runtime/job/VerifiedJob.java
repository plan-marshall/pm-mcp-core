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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.ProjectEnrolment;

/**
 * A job that passed every stage of the verify-not-resolve pipeline (PM-EXEC-2, PM-EXEC-3): what is launched is
 * exactly what this record names, nothing is resolved at launch.
 *
 * @param workingDirectory       the canonical working directory, as the workspace confinement answered it
 * @param argv                   the verified argument vector; its first element is an enrolled absolute path, the
 *                               interpreter's where one runs the target
 * @param templateSource         where the template of a build job came from; {@code null} for a job that is no build
 *                               job
 * @param projectScriptArguments the arguments of a project script job, validated against the script's enrolled
 *                               argument schema; the script receives them as a value, not in the argument vector.
 *                               {@code null} for a job that is no project script job
 * @param enrolment              the verified enrolment the job was checked against
 * @since 0.1
 */
public record VerifiedJob(Path workingDirectory, List<String> argv, @Nullable TemplateSource templateSource,
JobRequest.@Nullable Value projectScriptArguments, ProjectEnrolment enrolment) {

    /**
     * Where the template of a build job came from. The code is the source as it is recorded.
     *
     * @since 0.1
     */
    public enum TemplateSource {
        /** The template enrolled for the build system and verb. */
        ENROLLED("enrolled"),
        /** The default template the caller supplied, because none is enrolled. */
        COMPILED_DEFAULT("compiled_default");

        private final String code;

        TemplateSource(String code) {
            this.code = code;
        }

        /** @return the name of the source as it is recorded */
        public String code() {
            return code;
        }

        /**
         * @param code the name of a source as it is recorded
         * @return the source of that name; empty if no source has it
         */
        public static Optional<TemplateSource> fromCode(String code) {
            return Arrays.stream(values()).filter(value -> value.code.equals(code)).findFirst();
        }
    }

    /**
     * @throws NullPointerException     if a non-nullable component or an argument is {@code null}
     * @throws IllegalArgumentException if the argument vector is empty
     */
    public VerifiedJob {
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
        if (argv.isEmpty()) {
            throw new IllegalArgumentException("A verified argument vector has at least the executable");
        }
        Objects.requireNonNull(enrolment, "enrolment");
    }
}
