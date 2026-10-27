/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.question;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.queue.QueueTaskRecord.Continuation;
import de.planmarshall.core.queue.QueueTaskRecord.ContinuationForm;
import de.planmarshall.core.queue.QueueTaskRecord.FallbackReason;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.workflow.ast.StateBehavior;

/**
 * The server's decision whether a task continues after its question, and in which form (Model Work § Continuing after a
 * Question): a hold while the asking generation keeps the task, a suspension that resumes the stored harness session, or
 * the fallback in which the task closes {@code asked}.
 * <p>
 * The model never chooses the form. It follows from three sources only, and nothing here reads a representation or a
 * model's request:
 * <ul>
 * <li>the state: a state that declares no {@code ask}, or {@code ask fresh}, never continues (Workflow DSL § Task
 * States, {@code ask fresh}); an envelope task may continue whichever form its state declares;</li>
 * <li>the role artifact: the effective {@code questions} bounds of the role, already cut to what the harness declares
 * verified ({@link de.planmarshall.core.role.EffectiveRole#questions()}); a bound of zero is no continuation, so an
 * absent member, a form the harness's profile does not declare verified, and an expired bound all read the same
 * way;</li>
 * <li>the harness: through that cut, because a form the profile does not declare verified counts as absent.</li>
 * </ul>
 * <p>
 * The order is the specification's: hold while the hold limit lasts, then suspend where the task may resume, and fall
 * back otherwise. The fallback reasons this type decides are {@code not_allowed} (no form is open), {@code
 * hold_expired} (the hold limit passed and no resume is open) and {@code resume_expired} (the suspension's limit
 * passed); the reasons a stored session is refused are the supervisor's, which knows the digest, the harness version
 * and the worktree head (Model Work § Resume refused).
 *
 * @since 0.1
 */
public final class QuestionContinuation {

    private QuestionContinuation() {
    }

    /**
     * The form in force for a continuation.
     *
     * @since 0.1
     */
    public enum Form {
        /** The asking generation keeps the task and its model slot. */
        HOLD("hold"),
        /** The asking worker ends with its session stored; a new generation resumes it. */
        RESUME("resume"),
        /** The task does not continue. */
        FALLBACK("fallback");

        private final String wireName;

        Form(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the form in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The decision after an {@code ask}.
     *
     * @param form            the form in force
     * @param holdUntil       when the hold ends; {@code null} unless the form is {@code hold}
     * @param resumeUntil     when the suspension ends; {@code null} unless the form is {@code resume}
     * @param fallbackReasons why the task does not continue; empty unless the form is {@code fallback}
     * @since 0.1
     */
    public record Decision(Form form, Instant holdUntil, Instant resumeUntil, FallbackReason fallbackReasons) {

        /**
         * @throws NullPointerException     if the form is {@code null}
         * @throws IllegalArgumentException if a limit is set for another form, or a fallback carries no reason
         */
        public Decision {
            Objects.requireNonNull(form, "form");
            if ((form == Form.HOLD) != (holdUntil != null)) {
                throw new IllegalArgumentException("Only a hold carries its hold limit");
            }
            if ((form == Form.RESUME) != (resumeUntil != null)) {
                throw new IllegalArgumentException("Only a resume carries its resume limit");
            }
            if ((form == Form.FALLBACK) != (fallbackReasons != null)) {
                throw new IllegalArgumentException("Exactly a fallback carries its reason");
            }
        }

        /** @return whether the task continues in this generation */
        public boolean holds() {
            return form == Form.HOLD;
        }

        /** @return whether the task continues in a generation resumed from the stored session */
        public boolean resumes() {
            return form == Form.RESUME;
        }

        /** @return whether the task closes {@code asked} */
        public boolean fallsBack() {
            return form == Form.FALLBACK;
        }

        /**
         * @return the form of the queue task record this decision records; empty for a fallback, whose record keeps
         *         the form the task asked in and only names the reason
         */
        public Optional<ContinuationForm> recordForm() {
            return switch (form) {
                case HOLD -> Optional.of(ContinuationForm.HOLD);
                case RESUME -> Optional.of(ContinuationForm.RESUME);
                case FALLBACK -> Optional.empty();
            };
        }
    }

    /**
     * Decides the form after an {@code ask} at the instant the question was filed.
     *
     * @param askForm      the asking axis the state declares
     * @param envelopeTask whether the asking task is an envelope task, which may always continue
     * @param questions    the role's effective continuation bounds, cut to the harness; {@code null} or a bound of
     *                     zero for a member the role does not declare
     * @param at           the instant of the ask
     * @return the decision
     * @throws IllegalArgumentException if the state offers no {@code ask} link at all
     */
    public static Decision decide(StateBehavior.AskForm askForm, boolean envelopeTask, RoleArtifact.Questions questions,
            Instant at) {
        Objects.requireNonNull(askForm, "askForm");
        Objects.requireNonNull(at, "at");
        if (!askForm.declaresAsk()) {
            throw new IllegalArgumentException("A state without an 'ask' link files no question to continue");
        }
        if (!continues(askForm, envelopeTask, questions)) {
            return fallback(FallbackReason.NOT_ALLOWED);
        }
        if (questions.holdSeconds() > 0) {
            return new Decision(Form.HOLD, at.plus(Duration.ofSeconds(questions.holdSeconds())), null, null);
        }
        if (questions.resumeWithinSeconds() > 0) {
            return new Decision(Form.RESUME, null, at.plus(Duration.ofSeconds(questions.resumeWithinSeconds())),
                    null);
        }
        return fallback(FallbackReason.NOT_ALLOWED);
    }

    /**
     * Decides what follows a hold whose limit passed without an answer: the suspension where the task may resume, else
     * the fallback reason {@code hold_expired} (Model Work § Order).
     *
     * @param askForm      the asking axis the state declares
     * @param envelopeTask whether the asking task is an envelope task
     * @param questions    the role's effective continuation bounds
     * @param at           the instant the hold limit passed
     * @return the decision
     */
    public static Decision afterHold(StateBehavior.AskForm askForm, boolean envelopeTask,
            RoleArtifact.Questions questions, Instant at) {
        Objects.requireNonNull(at, "at");
        if (continues(askForm, envelopeTask, questions) && questions.resumeWithinSeconds() > 0) {
            return new Decision(Form.RESUME, null, at.plus(Duration.ofSeconds(questions.resumeWithinSeconds())),
                    null);
        }
        return fallback(FallbackReason.HOLD_EXPIRED);
    }

    /**
     * @param questions the role's effective continuation bounds, or {@code null}
     * @return whether any continuation at all is open for the role and the harness
     */
    public static boolean anyForm(RoleArtifact.Questions questions) {
        return questions != null && (questions.holdSeconds() > 0 || questions.resumeWithinSeconds() > 0);
    }

    /**
     * @param questions    the role's effective continuation bounds, or {@code null}
     * @param reason       why a continuation fell back
     * @return the decision that closes the task {@code asked} for that reason
     */
    public static Decision fallback(FallbackReason reason) {
        return new Decision(Form.FALLBACK, null, null, Objects.requireNonNull(reason, "reason"));
    }

    private static boolean continues(StateBehavior.AskForm askForm, boolean envelopeTask,
            RoleArtifact.Questions questions) {
        return (envelopeTask || askForm.allowsContinuation()) && anyForm(questions);
    }

    /**
     * @param decision the decision in force
     * @param question the question the task filed
     * @param at       the instant the continuation record is written
     * @return the continuation member of the queue task record for a hold or a resume
     * @throws IllegalArgumentException if the decision is a fallback
     */
    public static Continuation continuation(Decision decision, String question, Instant at) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(at, "at");
        var form = decision.recordForm().orElseThrow(
                () -> new IllegalArgumentException("A fallback records no continuation; it names a reason"));
        return new Continuation(question, form, decision.holdUntil(), null, null, decision.resumeUntil(), null, null,
                null);
    }
}
