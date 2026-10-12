/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The engine port that starts the job of a {@code job} state (PM-IMPL-5, bullet 1: a job is an explicit side effect
 * of the state that owns it). The engine calls it once when it enters a job state that has no job bound yet and
 * records the returned {@code job_id} as the state's {@code active_job} binding in the status store, so the job
 * belongs to persisted state and never to the memory of a running engine (PM-ARCH-4, bullets 4 and 5).
 * <p>
 * The job runtime of {@code pm-runtime} implements the port; no class of {@code pm-core} does. A start that the
 * runtime declines is a {@link StartResult.Refused} value, never an exception, so the engine routes it like every
 * other result.
 *
 * @since 0.1
 */
public interface JobStarter {

    /**
     * Starts the job of a job state.
     *
     * @param request the state the job is started for
     * @return the started job, or the refusal of the start
     */
    StartResult start(StartRequest request);

    /**
     * The kind of a job the port starts, named by the first character of its {@code job_id} (PM-IMPL-5). Worker
     * jobs ({@code l-}) are never started through this port: a task state creates a queue task instead
     * ({@link QueueTaskCreator}).
     */
    enum JobKind {
        /** A build job, {@code job_id} prefix {@code b}. */
        BUILD('b', "build"),
        /** A CI job, {@code job_id} prefix {@code c}. */
        CI('c', "ci"),
        /** A git job, {@code job_id} prefix {@code g}. */
        GIT('g', "git"),
        /** A project script job, {@code job_id} prefix {@code p}. */
        PROJECT_SCRIPT('p', "project_script");

        private final char prefix;
        private final String wireName;

        JobKind(char prefix, String wireName) {
            this.prefix = prefix;
            this.wireName = wireName;
        }

        /**
         * @return the first character of the {@code job_id} of a job of this kind
         */
        public char prefix() {
            return prefix;
        }

        /**
         * @return the name of the kind in the stores, for example {@code project_script}
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param prefix the first character of a {@code job_id}
         * @return the kind the character names
         * @throws IllegalArgumentException if no kind of this port has the prefix
         */
        static JobKind ofPrefix(char prefix) {
            return Arrays.stream(values())
                    .filter(kind -> kind.prefix == prefix)
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("no job kind has the prefix " + prefix));
        }
    }

    /**
     * What the engine asks the port to start: the job of one state of one scope.
     *
     * @param scopeId the identifier of the plan or epic the state belongs to; empty for the workspace scope
     * @param stateId the identifier of the job state the job is started for
     * @param primitiveId the canonical name of the state's job primitive, which decides the kind of the job
     * @since 0.1
     */
    record StartRequest(Optional<String> scopeId, String stateId, String primitiveId) {

        /**
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if the state or primitive identifier is blank
         */
        public StartRequest {
            Objects.requireNonNull(scopeId, "scopeId");
            requireNonBlank(stateId, "stateId");
            requireNonBlank(primitiveId, "primitiveId");
        }
    }

    /**
     * What one start returns: the started job or the refusal.
     *
     * @since 0.1
     */
    sealed interface StartResult permits StartResult.Started, StartResult.Refused {

        /**
         * The job was started; the engine binds its identifier to the state.
         *
         * @param jobId the identifier of the started job, {@code ^[bcgp]-[0-9a-f]{16}$}
         * @since 0.1
         */
        record Started(String jobId) implements StartResult {

            private static final Pattern JOB_ID = Pattern.compile("^[bcgp]-[0-9a-f]{16}$");

            /**
             * @throws NullPointerException if the identifier is {@code null}
             * @throws IllegalArgumentException if the identifier does not match {@code ^[bcgp]-[0-9a-f]{16}$}
             */
            public Started {
                Objects.requireNonNull(jobId, "jobId");
                if (!JOB_ID.matcher(jobId).matches()) {
                    throw new IllegalArgumentException("job_id does not match ^[bcgp]-[0-9a-f]{16}$: " + jobId);
                }
            }

            /**
             * @return the kind of the started job, named by the first character of its identifier
             */
            public JobKind kind() {
                return JobKind.ofPrefix(jobId.charAt(0));
            }
        }

        /**
         * The runtime declined the start; nothing was started and nothing is bound.
         *
         * @param reason why the start was declined
         * @since 0.1
         */
        record Refused(String reason) implements StartResult {

            /**
             * @throws NullPointerException if the reason is {@code null}
             * @throws IllegalArgumentException if the reason is blank
             */
            public Refused {
                requireNonBlank(reason, "reason");
            }
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
