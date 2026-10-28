/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.IntentRecord.CapabilityChange;
import de.planmarshall.core.store.IntentRecord.CapabilityLink;
import de.planmarshall.core.store.IntentRecord.Decision;
import de.planmarshall.core.store.IntentStore.Revision;

/**
 * The transaction that accepts a submitted intent record (Phase Workflows § The Intent Record, server checks; PM-WATCH
 * PHASE-107): it checks the rules that need the plan — a decision's true decider, a capability requirement's base
 * fingerprint in the tree the plan started from, and the epic's required claims — and moves the current record into
 * the history in the same acceptance.
 * <p>
 * A refusal is the {@code invalid_link_args} of the submission, its detail naming the member; the state is never
 * mutated then. The checks that need only the record are those of {@link IntentRecord}, which the submitted record
 * already passed.
 *
 * @since 0.1
 */
public final class IntentAcceptance {

    /** The channel a question part was answered through. */
    public enum AnswerChannel {
        /** The operator answered through the web client. */
        WEB,
        /** The operator answered through the command line. */
        CLI,
        /** A quorum answered. */
        QUORUM
    }

    /**
     * A question part the record's decision cites, as the question log holds it.
     *
     * @param planId  the plan the question belongs to
     * @param channel the channel it was answered through
     * @since 0.1
     */
    public record AnsweredPart(String planId, AnswerChannel channel) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public AnsweredPart {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(channel, "channel");
        }
    }

    /**
     * The answered question parts of the plan, read from its question log.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface QuestionAnswers {

        /**
         * @param questionId the identifier the decision cites
         * @return the answered part; empty when the question is unknown, unanswered, or a part of another plan
         */
        Optional<AnsweredPart> answered(String questionId);
    }

    /**
     * The capability requirements of the tree the plan started from.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface CapabilityFacts {

        /**
         * @param capability  the capability name
         * @param requirement the requirement name
         * @return the requirement's fingerprint; empty when the requirement does not exist
         */
        Optional<String> fingerprint(String capability, String requirement);
    }

    /**
     * The epic a plan started from, with the {@code done_when} of its plan row.
     *
     * @param intent      the epic's intent
     * @param rowDoneWhen the {@code done_when} of the plan's row
     * @since 0.1
     */
    public record EpicSeed(EpicIntent intent, String rowDoneWhen) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public EpicSeed {
            Objects.requireNonNull(intent, "intent");
            Objects.requireNonNull(rowDoneWhen, "rowDoneWhen");
        }
    }

    /**
     * What the acceptance needs to know beyond the record.
     *
     * @param planId       the plan the record belongs to
     * @param questions    the answered question parts of the plan
     * @param capabilities the capability requirements of the tree the plan started from
     * @param epic         the epic the plan started from; empty for a plan it did not start
     * @param treeRoot     the root a {@code fact} decision's evidence path is resolved against
     * @since 0.1
     */
    public record Context(String planId, QuestionAnswers questions, CapabilityFacts capabilities,
    Optional<EpicSeed> epic, Path treeRoot) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public Context {
            Objects.requireNonNull(planId, "planId");
            Objects.requireNonNull(questions, "questions");
            Objects.requireNonNull(capabilities, "capabilities");
            Objects.requireNonNull(epic, "epic");
            Objects.requireNonNull(treeRoot, "treeRoot");
        }
    }

    /** The outcome of an acceptance. */
    public sealed interface Outcome permits Accepted, Refused {
    }

    /**
     * The record was accepted.
     *
     * @param store the store after the acceptance
     * @since 0.1
     */
    public record Accepted(IntentStore store) implements Outcome {

        /**
         * @throws NullPointerException if the store is {@code null}
         */
        public Accepted {
            Objects.requireNonNull(store, "store");
        }
    }

    /**
     * The record was refused and nothing was written.
     *
     * @param detail the reason, naming the member
     * @since 0.1
     */
    public record Refused(String detail) implements Outcome {

        /**
         * @throws NullPointerException if the detail is {@code null}
         */
        public Refused {
            Objects.requireNonNull(detail, "detail");
        }
    }

    private IntentAcceptance() {
    }

    /**
     * Accepts a submission of the next revision.
     *
     * @param before     the store before the submission; {@code null} for the first revision of a plan
     * @param submission the entry point the record arrived through
     * @param next       the revision the task submitted
     * @param context    what the checks need beyond the record
     * @return the accepted store, or the refusal with its detail
     */
    public static Outcome accept(IntentStore before, IntentSubmission submission, Revision next, Context context) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(context, "context");
        var deciders = deciderRefusal(next.intentRecord(), context);
        if (deciders.isPresent()) {
            return new Refused(deciders.get());
        }
        var capabilities = capabilityRefusal(next.intentRecord(), context);
        if (capabilities.isPresent()) {
            return new Refused(capabilities.get());
        }
        var claims = claimRefusal(next.intentRecord(), context);
        if (claims.isPresent()) {
            return new Refused(claims.get());
        }
        IntentStore base;
        try {
            base = before == null ? IntentStore.first(next) : before.accept(next);
        } catch (IllegalArgumentException e) {
            return new Refused(e.getMessage());
        }
        return new Accepted(base);
    }

    private static Optional<String> deciderRefusal(IntentRecord intentRecord, Context context) {
        for (var decision : intentRecord.decisions()) {
            var refusal = switch (decision.decider()) {
                case OPERATOR -> answeredRefusal(decision, context, AnswerChannel.WEB, AnswerChannel.CLI);
                case QUORUM -> answeredRefusal(decision, context, AnswerChannel.QUORUM);
                case FACT -> evidenceRefusal(decision, context);
                case MODEL -> Optional.<String>empty();
            };
            if (refusal.isPresent()) {
                return refusal;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> answeredRefusal(Decision decision, Context context, AnswerChannel... channels) {
        var answered = context.questions().answered(decision.questionId());
        if (answered.isEmpty()) {
            return Optional.of("the %s decision '%s' cites the question part '%s', which this plan holds no answer for"
                    .formatted(decision.decider().wireName(), decision.id(), decision.questionId()));
        }
        if (!context.planId().equals(answered.get().planId())) {
            return Optional.of("the %s decision '%s' cites a question part of another plan ('%s')".formatted(
                    decision.decider().wireName(), decision.id(), answered.get().planId()));
        }
        var channel = answered.get().channel();
        var admitted = false;
        for (var candidate : channels) {
            admitted = admitted || candidate == channel;
        }
        if (!admitted) {
            return Optional.of("the %s decision '%s' cites a part answered through '%s'".formatted(
                    decision.decider().wireName(), decision.id(), channel));
        }
        return Optional.empty();
    }

    private static Optional<String> evidenceRefusal(Decision decision, Context context) {
        var evidence = decision.evidence();
        if (evidence == null || evidence.startsWith("pm://")) {
            return Optional.empty();
        }
        var root = context.treeRoot().toAbsolutePath().normalize();
        var resolved = root.resolve(evidence).normalize();
        if (Path.of(evidence).isAbsolute() || !resolved.startsWith(root)) {
            return Optional.of("the fact decision '%s' names the evidence '%s', which lies outside the tree"
                    .formatted(decision.id(), evidence));
        }
        if (!Files.exists(resolved)) {
            return Optional.of("the fact decision '%s' names the evidence '%s', which does not exist in the tree"
                    .formatted(decision.id(), evidence));
        }
        return Optional.empty();
    }

    private static Optional<String> capabilityRefusal(IntentRecord intentRecord, Context context) {
        for (var criterion : intentRecord.criteria()) {
            var link = criterion.capability();
            if (link == null) {
                continue;
            }
            var refusal = capabilityRefusal(criterion, link, context);
            if (refusal.isPresent()) {
                return refusal;
            }
        }
        return Optional.empty();
    }

    private static Optional<String> capabilityRefusal(IntentRecord.Criterion criterion, CapabilityLink link,
            Context context) {
        var fingerprint = context.capabilities().fingerprint(link.capability(), link.requirement());
        if (link.change() == CapabilityChange.ADDED && fingerprint.isPresent()) {
            return Optional.of("the criterion '%s' adds the requirement '%s/%s', which already exists".formatted(
                    criterion.id(), link.capability(), link.requirement()));
        }
        if (link.change() != CapabilityChange.ADDED && fingerprint.isEmpty()) {
            return Optional.of("the criterion '%s' changes the requirement '%s/%s', which does not exist".formatted(
                    criterion.id(), link.capability(), link.requirement()));
        }
        if (link.change() != CapabilityChange.ADDED && !link.base().equals(fingerprint.orElseThrow())) {
            return Optional.of(
                    ("the criterion '%s' changes the requirement '%s/%s' against the base fingerprint '%s', but the "
                            + "tree holds '%s'").formatted(criterion.id(), link.capability(), link.requirement(),
                            link.base(), fingerprint.orElseThrow()));
        }
        return Optional.empty();
    }

    private static Optional<String> claimRefusal(IntentRecord intentRecord, Context context) {
        if (context.epic().isEmpty()) {
            return Optional.empty();
        }
        var seed = context.epic().orElseThrow();
        for (var required : seed.intent().requiredClaims(seed.rowDoneWhen())) {
            if (!intentRecord.holdsClaim(required)) {
                return Optional.of(
                        "the source_claims omit the required claim '%s' the epic seeds".formatted(required));
            }
        }
        return Optional.empty();
    }
}
