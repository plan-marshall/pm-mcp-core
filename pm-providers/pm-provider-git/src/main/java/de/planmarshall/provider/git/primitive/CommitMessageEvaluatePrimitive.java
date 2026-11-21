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

import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;

/**
 * {@code commit-message.evaluate}: the return evaluation of the entity kind {@code commit-message} (PM-WF-16), run
 * when the task of {@code git.compose-message} submits its message.
 * <p>
 * The submitted message is already validated where the link bound it: a text that is no Conventional Commit or that
 * carries a trailer line is refused there and never reaches this primitive. Its one outcome is {@code valid}, with the
 * message as the fact {@value #MESSAGE_FACT}, which {@code git.stage-and-commit} commits.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc} (Stage 3).
 *
 * @since 0.1
 */
public final class CommitMessageEvaluatePrimitive
        implements Primitive<CommitMessageEvaluatePrimitive.Params, CommitMessageEvaluatePrimitive.Outcome> {

    /** The id of the primitive. */
    public static final String ID = "commit-message.evaluate";

    /** The fact that carries the submitted message. */
    public static final String MESSAGE_FACT = "message";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public Class<Outcome> outcomeType() {
        return Outcome.class;
    }

    @Override
    public AwaitedEvent awaitedEvent() {
        return AwaitedEvent.NONE;
    }

    @Override
    public PrimitiveResult<Outcome> execute(WorkflowExecutionContext context, Params parameters) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(parameters, "parameters");
        return new PrimitiveResult<>(Outcome.VALID, Map.<String, Object>of(MESSAGE_FACT, parameters.message()), null);
    }

    /**
     * The submission of the task.
     *
     * @param message the submitted commit message, the type {@code commit_message[1..2000]}
     * @since 0.1
     */
    public record Params(@FreeParam(minLength = 1, maxLength = CommitMessages.MAX_LENGTH) String message) {

        /**
         * @param message the submitted message
         */
        public Params {
            Objects.requireNonNull(message, "message");
        }
    }

    /**
     * The outcome of {@code commit-message.evaluate}.
     *
     * @since 0.1
     */
    public enum Outcome implements PrimitiveOutcome {
        /** The submitted message is the message to commit. */
        VALID;

        @Override
        public String wireName() {
            return "valid";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }
}
