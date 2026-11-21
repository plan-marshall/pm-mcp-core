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
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;

/**
 * {@code git.resolve-message}: resolves the commit message of a call of {@code git.commit_and_push} in memory from its
 * message mode (PM-WF-16, Stage 3), or hands it to the task state {@code git.compose-message}.
 * <ul>
 * <li>{@code auto_deliverable}: {@code feat(<module>): complete deliverable <N> - <title>} from the active
 * deliverable;</li>
 * <li>{@code step_or_fallback}: the call's bound {@code message}, else {@code chore(finalize): apply <step_id>
 * changes}, or {@code chore(finalize): settle changes} for a caller that is no step;</li>
 * <li>{@code interactive}: always {@code needs_interactive};</li>
 * <li>{@code none_assert_clean}: {@code needs_interactive} when the checkout holds dirty files, else {@code ready}
 * with no message, since there is nothing to commit.</li>
 * </ul>
 * A bound {@code message} is read only under {@code step_or_fallback}. The message of {@code ready} is the fact
 * {@value #MESSAGE_FACT}; a message that {@link CommitMessages#validate(String)} refuses is never returned as
 * {@code ready} but goes to {@code needs_interactive}.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc} (Bound Parameter Contract,
 * Stage 3).
 *
 * @since 0.1
 */
public final class GitResolveMessagePrimitive
        implements Primitive<GitResolveMessagePrimitive.Params, GitResolveMessagePrimitive.Outcome> {

    /** The id of the primitive. */
    public static final String ID = "git.resolve-message";

    /** The fact that carries the resolved message. */
    public static final String MESSAGE_FACT = "message";

    private final CommitSite site;

    /**
     * @param site the commit site of the assembly
     */
    public GitResolveMessagePrimitive(CommitSite site) {
        this.site = Objects.requireNonNull(site, "site");
    }

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
        return switch (parameters.messageMode()) {
            case AUTO_DELIVERABLE -> autoDeliverable(parameters).map(GitResolveMessagePrimitive::ready)
                    .orElseGet(GitResolveMessagePrimitive::needsInteractive);
            case STEP_OR_FALLBACK -> ready(stepOrFallback(parameters));
            case INTERACTIVE -> needsInteractive();
            case NONE_ASSERT_CLEAN -> site.dirtyPaths(context).isEmpty()
                    ? new PrimitiveResult<>(Outcome.READY, Map.of(), null)
                    : needsInteractive();
        };
    }

    /**
     * @return the deliverable's message; empty when the call names no complete deliverable
     */
    private static Optional<String> autoDeliverable(Params parameters) {
        var deliverable = parameters.deliverable();
        var module = parameters.module();
        var title = parameters.title();
        if (deliverable == null || module == null || title == null) {
            return Optional.empty();
        }
        return Optional.of("feat(%s): complete deliverable %d - %s".formatted(module, deliverable, title));
    }

    private static String stepOrFallback(Params parameters) {
        var message = parameters.message();
        if (message != null) {
            return message;
        }
        var stepId = parameters.stepId();
        return stepId == null ? "chore(finalize): settle changes"
                : "chore(finalize): apply %s changes".formatted(stepId);
    }

    /**
     * A message {@link CommitMessages#validate(String)} refuses is composed by the task instead.
     */
    private static PrimitiveResult<Outcome> ready(String message) {
        if (!CommitMessages.validate(message).isEmpty()) {
            return needsInteractive();
        }
        return new PrimitiveResult<>(Outcome.READY, Map.<String, Object>of(MESSAGE_FACT, message), null);
    }

    private static PrimitiveResult<Outcome> needsInteractive() {
        return new PrimitiveResult<>(Outcome.NEEDS_INTERACTIVE, Map.of(), null);
    }

    /**
     * How a call of {@code git.commit_and_push} sources its commit message.
     *
     * @since 0.1
     */
    public enum MessageMode {
        /** The message formatted from the active deliverable. */
        AUTO_DELIVERABLE("auto_deliverable"),
        /** The caller's bound message, else the fixed message of the step. */
        STEP_OR_FALLBACK("step_or_fallback"),
        /** A task of the role {@code commit-message} composes the message. */
        INTERACTIVE("interactive"),
        /** The tree is expected clean; dirty files go to the composing task. */
        NONE_ASSERT_CLEAN("none_assert_clean");

        private final String wireName;

        MessageMode(String wireName) {
            this.wireName = wireName;
        }

        /** @return the value of the input {@code message_mode} */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The bound inputs of the call.
     *
     * @param messageMode the message mode
     * @param message     the call's bound message, {@code null} when none is bound; read only under
     *                    {@link MessageMode#STEP_OR_FALLBACK}
     * @param deliverable the number of the active deliverable, {@code null} outside a deliverable
     * @param module      the module of the active deliverable, {@code null} outside a deliverable
     * @param title       the title of the active deliverable, {@code null} outside a deliverable
     * @param stepId      the id of the step whose changes are committed, {@code null} when the caller is no step
     * @since 0.1
     */
    @Builder
    public record Params(MessageMode messageMode, @Nullable String message, @Nullable Integer deliverable,
    @Nullable String module, @Nullable String title, @Nullable String stepId) {

        /**
         * @param messageMode the message mode
         * @param message     the bound message
         * @param deliverable the deliverable number
         * @param module      the deliverable module
         * @param title       the deliverable title
         * @param stepId      the step id
         */
        public Params {
            Objects.requireNonNull(messageMode, "messageMode");
        }
    }

    /**
     * The outcomes of {@code git.resolve-message}.
     *
     * @since 0.1
     */
    public enum Outcome implements PrimitiveOutcome {
        /** The message is resolved; {@code git.stage-and-commit} follows. */
        READY("ready"),
        /** The message is composed by the task state {@code git.compose-message}. */
        NEEDS_INTERACTIVE("needs_interactive");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }
}
