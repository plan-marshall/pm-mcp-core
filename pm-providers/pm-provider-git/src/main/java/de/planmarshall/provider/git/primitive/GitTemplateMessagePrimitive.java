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

import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.provider.git.primitive.CommitSite.DiffSummary;

/**
 * {@code git.template-message}: renders the caller step's template message from the site's diff summary (PM-WF-16),
 * the message a call takes when the task of {@code git.compose-message} closes {@code infeasible} or {@code lost}.
 * <p>
 * The message is {@code <type>(<scope>): <subject>}: the type is the suggested type of highest priority in the order
 * of {@link #TYPE_PRIORITY}, {@code chore} when none is suggested; the scope is the summary's module scope and is left out
 * when the changes span several modules; the subject is the caller step's. It is a pure function of the summary and the
 * subject and decides nothing: its one outcome is {@code ready}, with the message as the fact {@value #MESSAGE_FACT}.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/06-reusable-sub-workflows.adoc} (Stage 3).
 *
 * @since 0.1
 */
public final class GitTemplateMessagePrimitive
        implements Primitive<GitTemplateMessagePrimitive.Params, GitTemplateMessagePrimitive.Outcome> {

    /** The id of the primitive. */
    public static final String PRIMITIVE_ID = "git.template-message";

    /** The fact that carries the rendered message. */
    public static final String MESSAGE_FACT = "message";

    /** The type taken when no type of {@link #TYPE_PRIORITY} is suggested; declared first, the list names it. */
    private static final String FALLBACK_TYPE = "chore";

    /** The priority of the Conventional Commit types, highest first. */
    public static final List<String> TYPE_PRIORITY =
            List.of("fix", "feat", "perf", "refactor", "docs", "style", "test", FALLBACK_TYPE, "ci");

    private final CommitSite site;

    /**
     * @param site the commit site of the assembly
     */
    public GitTemplateMessagePrimitive(CommitSite site) {
        this.site = Objects.requireNonNull(site, "site");
    }

    @Override
    public String id() {
        return PRIMITIVE_ID;
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
        var message = render(site.diffSummary(context), parameters.subject());
        return new PrimitiveResult<>(Outcome.READY, Map.<String, Object>of(MESSAGE_FACT, message), null);
    }

    /**
     * Renders the template message.
     *
     * @param summary the diff summary
     * @param subject the caller step's subject
     * @return {@code <type>(<scope>): <subject>}, or {@code <type>: <subject>} without a module scope
     */
    public static String render(DiffSummary summary, String subject) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(subject, "subject");
        var type = TYPE_PRIORITY.stream().filter(summary.suggestedTypes()::contains).findFirst()
                .orElse(FALLBACK_TYPE);
        var scope = summary.moduleScope();
        return scope == null ? "%s: %s".formatted(type, subject) : "%s(%s): %s".formatted(type, scope, subject);
    }

    /**
     * The bound inputs of the call.
     *
     * @param subject the caller step's subject of the template message
     * @since 0.1
     */
    public record Params(String subject) {

        /**
         * @param subject the subject
         */
        public Params {
            Objects.requireNonNull(subject, "subject");
        }
    }

    /**
     * The outcome of {@code git.template-message}.
     *
     * @since 0.1
     */
    public enum Outcome implements PrimitiveOutcome {
        /** The template message is rendered; {@code git.stage-and-commit} follows. */
        READY;

        @Override
        public String wireName() {
            return "ready";
        }

        @Override
        public OutcomeClass outcomeClass() {
            return OutcomeClass.SUCCESS;
        }
    }
}
