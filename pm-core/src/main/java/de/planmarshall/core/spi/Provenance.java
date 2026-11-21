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

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * The cause of the advance that runs a primitive (PM-IMPL-5): what made the engine advance, not who acted. Every
 * {@link Command} carries one.
 * <p>
 * The reference names the thing the cause is about, where the cause has one: the job id of a settled job, the
 * question id of a resolved question, the state and link of an operator act. A component that may be absent is
 * {@code @Nullable}, by the rule of the repository; an absent reference is {@code null}, never blank.
 *
 * @param trigger    the cause of the advance
 * @param triggerRef the reference of the cause, or {@code null} when the cause names none; never blank
 * @since 0.1
 */
public record Provenance(Trigger trigger, @Nullable String triggerRef) {

    /**
     * @throws NullPointerException     if the trigger is {@code null}
     * @throws IllegalArgumentException if the reference is present and blank
     */
    public Provenance {
        Objects.requireNonNull(trigger, "trigger");
        if (triggerRef != null && triggerRef.isBlank()) {
            throw new IllegalArgumentException("a trigger reference must not be blank");
        }
    }

    /**
     * @param trigger the cause of the advance
     * @return a provenance of the cause without a reference
     * @throws NullPointerException if the trigger is {@code null}
     */
    public static Provenance of(Trigger trigger) {
        return new Provenance(trigger, null);
    }

    /**
     * The closed set of causes of an advance: the four entry points of the engine that run a primitive on their own
     * ({@link #START}, {@link #RESUME}, {@link #STEP_OUT}, {@link #FALLBACK}) and one per event that resumes a
     * resting status.
     *
     * @since 0.1
     */
    public enum Trigger {
        /** The workflow was started. */
        START,
        /** A resting status was advanced without an event. */
        RESUME,
        /** The job a status waited for settled. */
        JOB_SETTLED,
        /** The queue tasks a status waited for settled. */
        TASKS_SETTLED,
        /** The question a status waited for was resolved. */
        QUESTION_RESOLVED,
        /** The operator acted on a link of the resting state. */
        OPERATOR_ACT,
        /** The time a status waited for elapsed. */
        TIME_ELAPSED,
        /** A session-driven sub-workflow was left through its step out. */
        STEP_OUT,
        /** The engine unwound to the fallback of the workflow. */
        FALLBACK;

        /**
         * @return the name a log or a diagnostic prints the trigger by: its constant in lower case with underscores
         */
        public String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
