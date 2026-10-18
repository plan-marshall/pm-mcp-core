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

import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * One line of the question log {@code questions.jsonl} of a scope (PM-WF-12, PM-IMPL-2): one event of one question,
 * the part of a decision.
 * <p>
 * The record has a closed schema in {@code format_version} 1 ({@link QuestionRecordCodec}). A question is never
 * changed in place: every change is a new line, and the last readable line of a {@link #questionId()} is current
 * ({@link QuestionLog}). The record has no member that names a deadline or an expiry: a question ends only by its one
 * closing line ({@link QuestionEvent#closes()}).
 * <p>
 * Each member belongs to the events the schema names for it, and the events that require it have it:
 * <ul>
 * <li>{@link QuestionEvent#ASKED}: the class, the origin, the source entity, the asking role (none for a question the
 * server raised), the phase, the question set, the envelope and the revision 1; the decision on the first question of
 * the set, and only there; the plan task only for a question of the class {@code task};</li>
 * <li>{@link QuestionEvent#REVISED}: the envelope and the new revision, and the decision on the first question of the
 * set;</li>
 * <li>{@link QuestionEvent#ADJUDICATED}: the revision and the decision record of its adjudication;</li>
 * <li>{@link QuestionEvent#DELIVERED}: the revision delivered;</li>
 * <li>{@link QuestionEvent#ANSWERED}: the revision answered, the answer, the channel, the device for the channel
 * {@code web} and none otherwise, the decision record of the decision taken, and an amendment where the answer created
 * one;</li>
 * <li>{@link QuestionEvent#WITHDRAWN} and {@link QuestionEvent#CANCELLED}: the reason the server closed it, if one is
 * given.</li>
 * </ul>
 * The envelope is the question as the part of its decision, valid against a parameter schema the engine owns; the
 * record keeps it as the JSON text of one object and writes it back verbatim.
 *
 * @param event         what the line records
 * @param questionId    the question, {@code Q-} and 3 to 6 digits
 * @param questionClass the class of the question; asked only
 * @param origin        whether a task or the server created the question; asked only
 * @param sourceEntity  the entity of the asking task, or the creating state; asked only
 * @param askingRole    the role of the asking task; asked only, {@code null} for a question of the server
 * @param phase         the phase of the asking scope at creation; asked only, {@code null} outside a plan
 * @param questionSet   the first question of the set, which names the decision; asked only
 * @param planTaskId    the plan task that asked, {@code TASK-} and digits; class {@code task} only
 * @param revision      the revision of the decision the line is about, from 1; {@code null} on a closing line of the
 *                      server
 * @param decision      what the decision as a whole carries; on the first question of the set only
 * @param envelope      the part as the JSON text of one object; asked and revised only
 * @param decisionId    the decision record, {@code D-} and 4 to 7 digits; adjudicated and answered only
 * @param answer        the answer to the part; answered only
 * @param channel       the channel of the answer; answered only
 * @param deviceId      the paired device of a web answer, {@code dev-} and 12 hex digits; {@code null} otherwise
 * @param amendmentId   the amendment the answer created, {@code A-} and digits; {@code null} if it created none
 * @param reason        why the server closed the question; withdrawn and cancelled only, may be {@code null}
 * @param at            when the event happened
 * @since 0.1
 */
@Builder(toBuilder = true)
public record QuestionRecord(QuestionEvent event, String questionId, QuestionClass questionClass, Origin origin,
String sourceEntity, String askingRole, String phase, String questionSet, String planTaskId, Integer revision,
Decision decision, String envelope, String decisionId, Answer answer, QuestionChannel channel, String deviceId,
String amendmentId, String reason, Instant at) {

    /** The format version of a question record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern QUESTION_ID = Pattern.compile("Q-[0-9]{3,6}");
    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern PLAN_TASK_ID = Pattern.compile("TASK-[0-9]{3,}");
    private static final Pattern DECISION_ID = Pattern.compile("D-[0-9]{4,7}");
    private static final Pattern DEVICE_ID = Pattern.compile("dev-[0-9a-f]{12}");
    private static final Pattern AMENDMENT_ID = Pattern.compile("A-[0-9]+");
    private static final Pattern REASON = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern BRANCH_KEY = Pattern.compile("[a-z][a-z0-9_]{1,31}");
    private static final Pattern QUEUE_TASK_ID = Pattern.compile("qt-[0-9a-f]{16}");

    private static final Set<QuestionEvent> ASKED = EnumSet.of(QuestionEvent.ASKED);
    private static final Set<QuestionEvent> WITH_ENVELOPE = EnumSet.of(QuestionEvent.ASKED, QuestionEvent.REVISED);
    private static final Set<QuestionEvent> WITH_DECISION_ID = EnumSet.of(QuestionEvent.ADJUDICATED,
            QuestionEvent.ANSWERED);
    private static final Set<QuestionEvent> ANSWERED = EnumSet.of(QuestionEvent.ANSWERED);
    private static final Set<QuestionEvent> CLOSED_BY_SERVER = EnumSet.of(QuestionEvent.WITHDRAWN,
            QuestionEvent.CANCELLED);
    private static final Set<QuestionEvent> WITH_REVISION = EnumSet.complementOf(EnumSet.of(QuestionEvent.WITHDRAWN,
            QuestionEvent.CANCELLED));

    /**
     * A closed value whose name in a record is its constant name in lower case.
     *
     * @since 0.1
     */
    public interface WireName {

        /** @return the constant name, as {@link Enum#name()} returns it */
        String name();

        /** @return the name of the value in a record */
        default String wireName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * @param type     a closed value type
     * @param wireName the name of a value in a record
     * @param <E>      the type
     * @return the value of that name; empty if no value has it
     */
    public static <E extends Enum<E> & WireName> Optional<E> fromWireName(Class<E> type, String wireName) {
        return Arrays.stream(type.getEnumConstants()).filter(value -> value.wireName().equals(wireName)).findFirst();
    }

    /**
     * The class of a question.
     *
     * @since 0.1
     */
    public enum QuestionClass implements WireName {
        /** Raised by an init classifier. */
        INIT,
        /** A clarification of the request. */
        CLARIFICATION,
        /** A review, such as the outline review. */
        REVIEW,
        /** Asked by a plan task. */
        TASK,
        /** A choice of the workspace setup. */
        SETUP,
        /** An optional question a model asked outside the other classes. */
        ASK
    }

    /**
     * Who created a question.
     *
     * @since 0.1
     */
    public enum Origin implements WireName {
        /** A task created it. */
        MODEL,
        /** A primitive of the server raised it. */
        SERVER
    }

    /**
     * What a decision blocks while it is open.
     *
     * @since 0.1
     */
    public enum Blocks implements WireName {
        /** The plan. */
        PLAN,
        /** The asking task. */
        TASK,
        /** Nothing. */
        NOTHING
    }

    /**
     * How reversible a decision is.
     *
     * @since 0.1
     */
    public enum Reversibility implements WireName {
        /** It can be undone. */
        REVERSIBLE,
        /** It can be undone at a cost. */
        COSTLY,
        /** It cannot be undone. */
        IRREVERSIBLE
    }

    /**
     * The kind of a piece of evidence of a decision.
     *
     * @since 0.1
     */
    public enum EvidenceKind implements WireName {
        /** A file. */
        FILE,
        /** A diff. */
        DIFF,
        /** A finding. */
        FINDING,
        /** A link. */
        LINK
    }

    /**
     * How a part was answered.
     *
     * @since 0.1
     */
    public enum AnswerKind implements WireName {
        /** Options or a value were picked. */
        CHOSEN,
        /** Own words were mapped onto an option and confirmed at the read-back. */
        INTERPRETED,
        /** Words that fit no option, recorded as the operator's own option. */
        OWN_OPTION,
        /** The operator left the choice to the decision. */
        DELEGATED,
        /** The whole decision was answered none fits. */
        NONE_FITS
    }

    /**
     * The confirmed result of an interpretation of the operator's own words.
     *
     * @since 0.1
     */
    public enum InterpretationResult implements WireName {
        /** An option. */
        OPTION,
        /** An option with a condition. */
        OPTION_WITH_CONDITION,
        /** The operator's own option. */
        OWN_OPTION,
        /** A delegation. */
        DELEGATION
    }

    /**
     * One piece of evidence of a decision.
     *
     * @param kind  what the evidence is
     * @param ref   where it is, 1 to 2048 characters
     * @param label its label, at most 200 characters; {@code null} if it has none
     * @since 0.1
     */
    public record Evidence(EvidenceKind kind, String ref, String label) {

        /**
         * @throws NullPointerException     if the kind or the ref is {@code null}
         * @throws IllegalArgumentException if a text breaks its length
         */
        public Evidence {
            Objects.requireNonNull(kind, "kind");
            requireLength(Objects.requireNonNull(ref, "ref"), "ref", 1, 2048);
            requireLength(label, "label", 0, 200);
        }
    }

    /**
     * What a decision as a whole carries, on the first question of its set.
     *
     * @param title         the title, 1 to 120 characters
     * @param header        the header, 1 to 300 characters
     * @param description   the description, at most 8000 characters
     * @param evidence      the evidence, at most 50 pieces
     * @param blocks        what the decision blocks while open
     * @param reversibility how reversible it is
     * @since 0.1
     */
    public record Decision(String title, String header, String description, List<Evidence> evidence, Blocks blocks,
    Reversibility reversibility) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if a text breaks its length, or there are more than 50 pieces of evidence
         */
        public Decision {
            requireLength(Objects.requireNonNull(title, "title"), "title", 1, 120);
            requireLength(Objects.requireNonNull(header, "header"), "header", 1, 300);
            requireLength(Objects.requireNonNull(description, "description"), "description", 0, 8000);
            evidence = List.copyOf(evidence);
            if (evidence.size() > 50) {
                throw new IllegalArgumentException("A decision has at most 50 pieces of evidence, not "
                        + evidence.size());
            }
            Objects.requireNonNull(blocks, "blocks");
            Objects.requireNonNull(reversibility, "reversibility");
        }
    }

    /**
     * The value of a part whose answer type is a value: a text or a number.
     *
     * @since 0.1
     */
    public sealed interface AnswerValue permits AnswerValue.Text, AnswerValue.Numeric {

        /**
         * A text value.
         *
         * @param text the text
         * @since 0.1
         */
        record Text(String text) implements AnswerValue {

            /** @throws NullPointerException if the text is {@code null} */
            public Text {
                Objects.requireNonNull(text, "text");
            }
        }

        /**
         * A number value, kept with its scale so that it is written back as it was read.
         *
         * @param number the number
         * @since 0.1
         */
        record Numeric(BigDecimal number) implements AnswerValue {

            /** @throws NullPointerException if the number is {@code null} */
            public Numeric {
                Objects.requireNonNull(number, "number");
            }
        }
    }

    /**
     * The confirmed result of an interpretation of the operator's own words.
     *
     * @param result      what the words were confirmed as
     * @param condition   the condition of an option with a condition, at most 2000 characters; {@code null} otherwise
     * @param passages    the passages of the words the result rests on, each 1 to 1000 characters
     * @param queueTaskId the task of the interpreting role, {@code qt-} and 16 hex digits
     * @since 0.1
     */
    public record Interpretation(InterpretationResult result, String condition, List<String> passages,
    String queueTaskId) {

        /**
         * @throws NullPointerException     if the result, the passages or the task is {@code null}
         * @throws IllegalArgumentException if a text breaks its length or the task id its form
         */
        public Interpretation {
            Objects.requireNonNull(result, "result");
            requireLength(condition, "condition", 0, 2000);
            passages = List.copyOf(passages);
            passages.forEach(passage -> requireLength(Objects.requireNonNull(passage, "passage"), "passage", 1,
                    1000));
            requireMatch(QUEUE_TASK_ID, Objects.requireNonNull(queueTaskId, "queueTaskId"),
                    "queue task id (qt- and 16 hex digits)");
        }
    }

    /**
     * The answer to one part.
     *
     * @param kind           how the part was answered
     * @param branchKeys     the options taken; distinct
     * @param value          the value of a part whose answer type is a value; {@code null} otherwise
     * @param ownWords       the operator's own words, verbatim, at most 4000 characters; {@code null} if none
     * @param note           the operator's note, at most 2000 characters; {@code null} if none
     * @param interpretation the confirmed interpretation of an interpreted, own-option or delegated answer;
     *                       {@code null} for any other kind
     * @param afterChat      whether the answer followed a chat in the decision
     * @since 0.1
     */
    public record Answer(AnswerKind kind, List<String> branchKeys, AnswerValue value, String ownWords, String note,
    Interpretation interpretation, boolean afterChat) {

        private static final Set<AnswerKind> INTERPRETED = EnumSet.of(AnswerKind.INTERPRETED, AnswerKind.OWN_OPTION,
                AnswerKind.DELEGATED);

        /**
         * @throws NullPointerException     if the kind or the branch keys are {@code null}
         * @throws IllegalArgumentException if a branch key breaks its form or occurs twice, a text breaks its length,
         *                                  or the interpretation is missing for its kinds or present for another
         */
        public Answer {
            Objects.requireNonNull(kind, "kind");
            branchKeys = List.copyOf(branchKeys);
            if (new HashSet<>(branchKeys).size() != branchKeys.size()) {
                throw new IllegalArgumentException("The branch keys of an answer are distinct: " + branchKeys);
            }
            branchKeys.forEach(key -> requireMatch(BRANCH_KEY, key, "branch key"));
            requireLength(ownWords, "ownWords", 0, 4000);
            requireLength(note, "note", 0, 2000);
            if (INTERPRETED.contains(kind) != (interpretation != null)) {
                throw new IllegalArgumentException("An answer of the kind %s %s an interpretation".formatted(
                        kind.wireName(), INTERPRETED.contains(kind) ? "carries" : "carries no"));
            }
        }
    }

    /**
     * @throws NullPointerException     if the event, the question id or the instant is {@code null}
     * @throws IllegalArgumentException if a value breaks its form, a member is present on an event it does not belong
     *                                  to, or an event lacks a member it requires
     */
    public QuestionRecord {
        Objects.requireNonNull(event, "event");
        requireMatch(QUESTION_ID, Objects.requireNonNull(questionId, "questionId"),
                "question id (Q- and 3 to 6 digits)");
        Objects.requireNonNull(at, "at");
        onlyOn(event, ASKED, questionClass, "class");
        onlyOn(event, ASKED, origin, "origin");
        onlyOn(event, ASKED, sourceEntity, "source_entity");
        onlyOn(event, ASKED, askingRole, "asking_role");
        onlyOn(event, ASKED, phase, "phase");
        onlyOn(event, ASKED, questionSet, "question_set");
        onlyOn(event, ASKED, planTaskId, "plan_task_id");
        onlyOn(event, WITH_ENVELOPE, decision, "decision");
        requiredOn(event, WITH_ENVELOPE, envelope, "envelope");
        requiredOn(event, WITH_DECISION_ID, decisionId, "decision_id");
        requiredOn(event, ANSWERED, answer, "answer");
        requiredOn(event, ANSWERED, channel, "channel");
        onlyOn(event, ANSWERED, deviceId, "device_id");
        onlyOn(event, ANSWERED, amendmentId, "amendment_id");
        onlyOn(event, CLOSED_BY_SERVER, reason, "reason");
        requiredOn(event, WITH_REVISION, revision, "revision");
        if (revision != null && revision < 1) {
            throw new IllegalArgumentException("A revision counts from 1: " + revision);
        }
        if (envelope != null) {
            requireJsonObject(envelope);
        }
        requireForms(askingRole, phase, planTaskId, decisionId, deviceId, amendmentId, reason);
        if (event == QuestionEvent.ASKED) {
            requireAsked(questionId, questionClass, origin, sourceEntity, askingRole, questionSet, planTaskId,
                    revision, decision);
        }
        if (channel != null && (channel == QuestionChannel.WEB) != (deviceId != null)) {
            throw new IllegalArgumentException("An answer names its device exactly when its channel is web");
        }
    }

    private static void requireAsked(String questionId, QuestionClass questionClass, Origin origin,
            String sourceEntity, String askingRole, String questionSet, String planTaskId, Integer revision,
            Decision decision) {
        Objects.requireNonNull(questionClass, "class");
        Objects.requireNonNull(origin, "origin");
        requireLength(Objects.requireNonNull(sourceEntity, "source_entity"), "source_entity", 1, Integer.MAX_VALUE);
        requireMatch(QUESTION_ID, Objects.requireNonNull(questionSet, "question_set"), "question set");
        if (revision != 1) {
            throw new IllegalArgumentException("An asked line has the revision 1, not " + revision);
        }
        if ((origin == Origin.MODEL) != (askingRole != null)) {
            throw new IllegalArgumentException("A question names its asking role exactly when a task created it");
        }
        if (planTaskId != null && questionClass != QuestionClass.TASK) {
            throw new IllegalArgumentException("Only a question of the class task names its plan task");
        }
        if (questionId.equals(questionSet) != (decision != null)) {
            throw new IllegalArgumentException(
                    "The decision is carried by the first question of the set, and only there: " + questionId);
        }
    }

    private static void requireForms(String askingRole, String phase, String planTaskId, String decisionId,
            String deviceId, String amendmentId, String reason) {
        if (askingRole != null) {
            requireMatch(ROLE, askingRole, "role");
        }
        if (phase != null && phase.isEmpty()) {
            throw new IllegalArgumentException("The phase of a question is empty");
        }
        if (planTaskId != null) {
            requireMatch(PLAN_TASK_ID, planTaskId, "plan task id (TASK- and 3 or more digits)");
        }
        if (decisionId != null) {
            requireMatch(DECISION_ID, decisionId, "decision id (D- and 4 to 7 digits)");
        }
        if (deviceId != null) {
            requireMatch(DEVICE_ID, deviceId, "device id (dev- and 12 hex digits)");
        }
        if (amendmentId != null) {
            requireMatch(AMENDMENT_ID, amendmentId, "amendment id (A- and digits)");
        }
        if (reason != null) {
            requireMatch(REASON, reason, "reason (lower case and underscores)");
        }
    }

    /** @return whether this line closes its question */
    public boolean closes() {
        return event.closes();
    }

    private static void onlyOn(QuestionEvent event, Set<QuestionEvent> events, Object member, String name) {
        if (member != null && !events.contains(event)) {
            throw new IllegalArgumentException("'%s' does not belong to a %s line".formatted(name, event.wireName()));
        }
    }

    private static void requiredOn(QuestionEvent event, Set<QuestionEvent> events, Object member, String name) {
        onlyOn(event, events, member, name);
        if (member == null && events.contains(event)) {
            throw new IllegalArgumentException("A %s line has '%s'".formatted(event.wireName(), name));
        }
    }

    /**
     * The envelope is written into a log of one record per line as the verbatim text it holds, so a line terminator
     * in it, between two tokens of the object, would split the record and let the text after the terminator stand as a
     * line of its own: a forged record of the log. A document that is read never has one, as it is one line.
     */
    private static void requireJsonObject(String envelope) {
        if (envelope.indexOf('\n') >= 0 || envelope.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(
                    "The envelope of a question is the text of one line, without a line break");
        }
        try (var parser = StrictJson.factory().createParser(envelope)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("The envelope of a question is one JSON object");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("The envelope of a question is one JSON object, nothing after it");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("The envelope of a question is not JSON: " + e.getMessage(), e);
        }
    }

    private static void requireMatch(Pattern pattern, String value, String name) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a %s: %s".formatted(name, value));
        }
    }

    private static void requireLength(String value, String name, int min, int max) {
        if (value != null && (value.length() < min || value.length() > max)) {
            throw new IllegalArgumentException("'%s' has %d to %d characters, not %d".formatted(name, min, max,
                    value.length()));
        }
    }
}
