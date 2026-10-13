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

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Why the verify-not-resolve pipeline refused a job (PM-EXEC-2): a value, never an exception.
 * <p>
 * The detail is a diagnostic for the caller and the log; it never carries a value of the argument vector or a secret.
 * A refusal as {@link Reason#BUILD_CONFIG_CHANGED} names every changed repository-relative path; a refusal whose
 * reason the operator resolves by re-baselining names the operator command that does it.
 *
 * @param reason          why the job is refused
 * @param detail          the diagnostic, without a secret and without a value of the argument vector
 * @param changedPaths    the changed repository-relative paths, sorted; not empty only for
 *                        {@link Reason#BUILD_CONFIG_CHANGED}
 * @param operatorCommand the operator command that re-baselines what was refused; present only for
 *                        {@link Reason#BUILD_CONFIG_CHANGED} and {@link Reason#ENROLMENT_TAMPERED}
 * @since 0.1
 */
public record JobRefusal(Reason reason, String detail, List<String> changedPaths, Optional<String> operatorCommand) {

    /**
     * Why a job is refused. The set is closed; the code is the reason as it is recorded.
     *
     * @since 0.1
     */
    public enum Reason {
        /** The enrolment file does not match its integrity sidecar (stage 0). */
        ENROLMENT_TAMPERED("enrolment_tampered"),
        /** The enrolment file or its sidecar fails a per-use check (stage 0). */
        ENROLMENT_INSECURE("enrolment_insecure"),
        /** The enrolment cannot be read strictly or violates the enrolment rules (stage 0). */
        ENROLMENT_INVALID("enrolment_invalid"),
        /** The project has no enrolment (stage 0). */
        NOT_ENROLLED("not_enrolled"),
        /** The working directory lies outside the workspace of the project (stage 1). */
        PATH_TRAVERSAL_REJECTED("path_traversal_rejected"),
        /** The executable of the job is not enrolled (stage 2). */
        EXECUTABLE_NOT_ENROLLED("executable_not_enrolled"),
        /** The executable of the job, its interpreter or its script does not match its enrolled digest (stage 2). */
        EXECUTABLE_DIGEST_MISMATCH("executable_digest_mismatch"),
        /** The build wrapper or one of its pinned files does not match its enrolment (stage 2). */
        BUILD_CONFIG_CHANGED("build_config_changed"),
        /** An argument of the job is refused (stage 3). */
        ARGUMENT_REJECTED("argument_rejected"),
        /** The verb of the job has no template, or no module-argument group for a module-scoped job (stage 3). */
        VERB_NOT_SUPPORTED("verb_not_supported");

        private final String code;

        Reason(String code) {
            this.code = code;
        }

        /** @return the name of the reason as it is recorded */
        public String code() {
            return code;
        }

        private boolean hasOperatorCommand() {
            return this == BUILD_CONFIG_CHANGED || this == ENROLMENT_TAMPERED;
        }
    }

    /**
     * @throws NullPointerException     if a component or a path is {@code null}
     * @throws IllegalArgumentException if the detail is empty, if changed paths are named by a reason other than
     *                                  {@link Reason#BUILD_CONFIG_CHANGED} or missing for it, or if the operator
     *                                  command is present for a reason that has none or missing for one that has
     */
    public JobRefusal {
        Objects.requireNonNull(reason, "reason");
        if (Objects.requireNonNull(detail, "detail").isEmpty()) {
            throw new IllegalArgumentException("detail is not empty");
        }
        changedPaths = List.copyOf(new TreeSet<>(Objects.requireNonNull(changedPaths, "changedPaths")));
        Objects.requireNonNull(operatorCommand, "operatorCommand");
        if (changedPaths.isEmpty() == (reason == Reason.BUILD_CONFIG_CHANGED)) {
            throw new IllegalArgumentException(
                    "Changed paths are named exactly by build_config_changed, not by " + reason.code());
        }
        if (operatorCommand.isPresent() != reason.hasOperatorCommand()) {
            throw new IllegalArgumentException("The reason %s %s an operator command".formatted(reason.code(),
                    reason.hasOperatorCommand() ? "names" : "names no"));
        }
    }

    /**
     * @param reason a reason that names neither changed paths nor an operator command
     * @param detail the diagnostic
     * @return the refusal
     * @throws IllegalArgumentException if the reason is {@link Reason#BUILD_CONFIG_CHANGED} or
     *                                  {@link Reason#ENROLMENT_TAMPERED}
     */
    public static JobRefusal of(Reason reason, String detail) {
        return new JobRefusal(reason, detail, List.of(), Optional.empty());
    }

    /**
     * @param projectId the project whose enrolment is refused
     * @param detail    what failed
     * @return the refusal as {@link Reason#ENROLMENT_TAMPERED}, naming the command that re-baselines the enrolment
     *         after the operator reviewed it
     */
    public static JobRefusal enrolmentTampered(String projectId, String detail) {
        return new JobRefusal(Reason.ENROLMENT_TAMPERED, detail, List.of(),
                Optional.of("pm-operator accept-edit enrolment " + projectId));
    }

    /**
     * @param projectId    the project whose build configuration changed
     * @param changedPaths every changed repository-relative path; not empty
     * @param detail       what changed
     * @return the refusal as {@link Reason#BUILD_CONFIG_CHANGED}, naming the paths in their natural order and the
     *         command that enrols the project again
     */
    public static JobRefusal buildConfigChanged(String projectId, Collection<String> changedPaths, String detail) {
        return new JobRefusal(Reason.BUILD_CONFIG_CHANGED, detail, List.copyOf(changedPaths),
                Optional.of("pm-operator enrol " + projectId));
    }
}
