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
 * <li>{@code step_or_fallback}: the call's bound {@code message}, else the fixed message of the call site the
 * assembly binds ({@link CallSite}), else, with no call site bound, {@code chore(finalize): apply <step_id> changes}
 * or {@code chore(finalize): settle changes} for a caller that is no step;</li>
 * <li>{@code interactive}: always {@code needs_interactive};</li>
 * <li>{@code none_assert_clean}: {@code needs_interactive} when the checkout holds dirty files, else {@code ready}
 * with no message, since there is nothing to commit.</li>
 * </ul>
 * A bound {@code message} and the call site are read only under {@code step_or_fallback}; the inputs a call site's
 * message names are bound by the assembly, never read from a store here. A call site whose message lacks an input it
 * names goes to {@code needs_interactive}. The message of {@code ready} is the fact {@value #MESSAGE_FACT}; a message
 * that {@link CommitMessages#validate(String)} refuses is never returned as {@code ready} but goes to
 * {@code needs_interactive}.
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
            case STEP_OR_FALLBACK -> stepOrFallback(parameters).map(GitResolveMessagePrimitive::ready)
                    .orElseGet(GitResolveMessagePrimitive::needsInteractive);
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

    /**
     * @return the bound message, else the fixed message of the call site; empty when the call site lacks an input
     */
    private static Optional<String> stepOrFallback(Params parameters) {
        var message = parameters.message();
        if (message != null) {
            return Optional.of(message);
        }
        var callSite = parameters.callSite();
        return callSite == null ? Optional.of(settleMessage(parameters.stepId())) : callSite.message(parameters);
    }

    /**
     * The message of {@code finalize.settle-commit}: the step of the persisted trigger, or none for order {@code 0}.
     */
    private static String settleMessage(@Nullable String stepId) {
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
        /** The caller's bound message, else the fixed message of the call site. */
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
     * The closed set of call sites with a fixed message under {@code step_or_fallback} (Stage 3), bound by the
     * assembly. Each names the inputs of {@link Params} its message reads.
     *
     * @since 0.1
     */
    public enum CallSite {
        /**
         * {@code chore(finalize): apply <step_id> changes}, or {@code chore(finalize): settle changes} without a step.
         */
        FINALIZE_SETTLE_COMMIT("finalize.settle-commit") {

            @Override
            Optional<String> message(Params parameters) {
                return Optional.of(settleMessage(parameters.stepId()));
            }
        },
        /** The pull-request title as the subject and the bound server sections, if any, as the body. */
        FINALIZE_LAND_MAIN("finalize.land-main") {

            @Override
            Optional<String> message(Params parameters) {
                var body = parameters.body();
                return Optional.ofNullable(parameters.prTitle())
                        .map(subject -> body == null ? subject : "%s\n\n%s".formatted(subject, body));
            }
        },
        /**
         * {@code chore(orchestrator): update <epic_id> ledger}, or {@code archive} while the epic archives.
         */
        EPIC_COMMIT_LEDGER("epic.commit-ledger") {

            @Override
            Optional<String> message(Params parameters) {
                var verb = parameters.archiving() ? "archive" : "update";
                return Optional.ofNullable(parameters.epicId())
                        .map(epicId -> "chore(orchestrator): %s %s ledger".formatted(verb, epicId));
            }
        },
        /** {@code chore(orchestrator): record <epic_id> decomposition}. */
        DECOMPOSITION_COMMIT("decomposition.commit") {

            @Override
            Optional<String> message(Params parameters) {
                return Optional.ofNullable(parameters.epicId())
                        .map("chore(orchestrator): record %s decomposition"::formatted);
            }
        },
        /** {@code chore(land): remove documents moved into a source directory}. */
        LAND_REMOVAL("git.land-removal") {

            @Override
            Optional<String> message(Params parameters) {
                return Optional.of("chore(land): remove documents moved into a source directory");
            }
        },
        /** {@code chore(land): place <unit_id>}, or {@code remove} for a removed unit. */
        LAND_UNIT("git.land-unit") {

            @Override
            Optional<String> message(Params parameters) {
                var verb = parameters.removing() ? "remove" : "place";
                return Optional.ofNullable(parameters.unitId())
                        .map(unitId -> "chore(land): %s %s".formatted(verb, unitId));
            }
        },
        /** {@code chore(land): apply project setup changes}. */
        LAND_SETUP("git.land-setup") {

            @Override
            Optional<String> message(Params parameters) {
                return Optional.of("chore(land): apply project setup changes");
            }
        },
        /** {@code chore(finalize): settle changes at hand-back}. */
        FINALIZE_HAND_BACK("finalize.hand-back") {

            @Override
            Optional<String> message(Params parameters) {
                return Optional.of("chore(finalize): settle changes at hand-back");
            }
        },
        /**
         * The ADR renumbering of the landing ({@code finalize.merge-sync}, {@code finalize.land-main},
         * {@code finalize.land-files}): {@code chore(finalize): renumber ADRs against <base_branch>}.
         */
        ADR_RENUMBERING("adr-renumbering") {

            @Override
            Optional<String> message(Params parameters) {
                return Optional.ofNullable(parameters.baseBranch())
                        .map("chore(finalize): renumber ADRs against %s"::formatted);
            }
        };

        private final String wireName;

        CallSite(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the call site */
        public String wireName() {
            return wireName;
        }

        /**
         * @param parameters the bound inputs of the call
         * @return the fixed message of the call site; empty when an input it names is not bound
         */
        abstract Optional<String> message(Params parameters);
    }

    /**
     * The bound inputs of the call.
     *
     * @param messageMode the message mode
     * @param message     the call's bound message, {@code null} when none is bound; read only under
     *                    {@link MessageMode#STEP_OR_FALLBACK}
     * @param callSite    the call site whose fixed message applies, {@code null} when the assembly binds none; read
     *                    only under {@link MessageMode#STEP_OR_FALLBACK}
     * @param deliverable the number of the active deliverable, {@code null} outside a deliverable
     * @param module      the module of the active deliverable, {@code null} outside a deliverable
     * @param title       the title of the active deliverable, {@code null} outside a deliverable
     * @param stepId      the id of the step whose changes are committed, {@code null} when the caller is no step
     * @param epicId      the epic of {@link CallSite#EPIC_COMMIT_LEDGER} and {@link CallSite#DECOMPOSITION_COMMIT},
     *                    {@code null} outside an epic
     * @param archiving   whether the epic archives, for {@link CallSite#EPIC_COMMIT_LEDGER}
     * @param unitId      the workflow unit of {@link CallSite#LAND_UNIT}, {@code null} for every other call site
     * @param removing    whether the unit of {@link CallSite#LAND_UNIT} is removed rather than placed
     * @param prTitle     the plan's pull-request title for {@link CallSite#FINALIZE_LAND_MAIN}, {@code null} when none
     *                    is recorded
     * @param body        the server sections of {@link CallSite#FINALIZE_LAND_MAIN}, {@code null} for no body
     * @param baseBranch  the base branch of {@link CallSite#ADR_RENUMBERING}, {@code null} for every other call site
     * @since 0.1
     */
    @Builder
    public record Params(MessageMode messageMode, @Nullable String message, @Nullable CallSite callSite,
    @Nullable Integer deliverable, @Nullable String module, @Nullable String title, @Nullable String stepId,
    @Nullable String epicId, boolean archiving, @Nullable String unitId, boolean removing, @Nullable String prTitle,
    @Nullable String body, @Nullable String baseBranch) {

        /**
         * @param messageMode the message mode
         * @param message     the bound message
         * @param callSite    the call site
         * @param deliverable the deliverable number
         * @param module      the deliverable module
         * @param title       the deliverable title
         * @param stepId      the step id
         * @param epicId      the epic id
         * @param archiving   the archive flag of the epic
         * @param unitId      the unit id
         * @param removing    the removal flag of the unit
         * @param prTitle     the pull-request title
         * @param body        the body of the landing commit
         * @param baseBranch  the base branch
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
