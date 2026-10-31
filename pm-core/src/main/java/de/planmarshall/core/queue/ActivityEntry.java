/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;
import de.planmarshall.core.store.StrictJson;

/**
 * One entry of a task's activity journal {@code tasks/activity/<queue_task_id>.jsonl} (Store Schemas § Activity
 * Journal): what a worker did while it served the task, or what the server recorded about it.
 * <p>
 * The harness kinds are {@code text}, {@code tool_call}, {@code tool_result}, {@code file_changed} and {@code turn};
 * the server kinds are {@code link}, {@code question}, {@code answer_ack}, {@code signal} and {@code attempt_end}. Each
 * member belongs to the kinds that name it. A {@code usage} object belongs to another package; it is kept as the raw
 * JSON text it was read with and written back unchanged.
 * <p>
 * An {@code agent_started} signal always carries the action {@code restart}: the schema's {@code if}/{@code then}
 * refuses any other action, and so does this record.
 *
 * @param seq           the position in the journal, one above the last ever written; never reused
 * @param attempt       the attempt of the queue task
 * @param source        whether the harness or the server recorded the entry
 * @param kind          the kind of the activity
 * @param text          what the model wrote, masked and cut to 2000 characters; text only
 * @param truncated     whether the text was cut to the bound; text only
 * @param tool          the tool call; tool_call only
 * @param result        the tool result; tool_result only
 * @param path          a path relative to the worktree; file_changed only
 * @param stopReason    the stop reason of the turn; turn only
 * @param usage         the turn's usage figures, a JSON object as raw text, kept unchanged: the five token components and
 *                      tool_uses as integers, {@code unmeasured} or {@code not_applicable}, and the reported cost as a
 *                      number or {@code unmeasured}; turn only
 * @param link          the link the worker followed; link only
 * @param questionId    the question; question and answer_ack only
 * @param signal        the off-track signal and its action; signal only
 * @param end           the attempt's end; attempt_end only
 * @param unmappedLines the output lines of the attempt that matched no mapping; attempt_end only
 * @param at            when the entry was written
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record ActivityEntry(int seq, int attempt, Source source, Kind kind, @Nullable String text,
boolean truncated, @Nullable Tool tool, @Nullable Result result, @Nullable String path,
@Nullable StopReason stopReason, @Nullable String usage, @Nullable Link link,
@Nullable String questionId, @Nullable Signal signal, @Nullable String end,
@Nullable Integer unmappedLines, Instant at) {

    /** The format version of an activity entry that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The compiled bound of a {@code text} entry. */
    public static final int TEXT_BOUND = 2000;

    private static final Pattern QUESTION_ID = Pattern.compile("Q-\\d{3,6}");

    /** The label of the signal member in a refusal. */
    private static final String SIGNAL_LABEL = "signal";

    /**
     * Whether the harness or the server recorded an entry.
     *
     * @since 0.1
     */
    public enum Source {
        /** A line of the worker's output stream. */
        HARNESS("harness"),
        /** A record of the server. */
        SERVER("server");

        private final String wireName;

        Source(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Source> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The kind of an activity entry.
     *
     * @since 0.1
     */
    public enum Kind {
        /** What the model wrote, masked and cut. */
        TEXT("text", Source.HARNESS),
        /** The tool's name and its target. */
        TOOL_CALL("tool_call", Source.HARNESS),
        /** The size of a tool result and whether it failed. */
        TOOL_RESULT("tool_result", Source.HARNESS),
        /** A path of the worktree. */
        FILE_CHANGED("file_changed", Source.HARNESS),
        /** A turn's usage and stop reason. */
        TURN("turn", Source.HARNESS),
        /** A link of the task the worker followed. */
        LINK("link", Source.SERVER),
        /** A question the worker asked. */
        QUESTION("question", Source.SERVER),
        /** The acknowledgement of an answer. */
        ANSWER_ACK("answer_ack", Source.SERVER),
        /** An off-track signal with the action taken. */
        SIGNAL(SIGNAL_LABEL, Source.SERVER),
        /** How the attempt ended. */
        ATTEMPT_END("attempt_end", Source.SERVER);

        private final String wireName;
        private final Source source;

        Kind(String wireName, Source source) {
            this.wireName = wireName;
            this.source = source;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /** @return the source that records an entry of this kind */
        public Source source() {
            return source;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The stop reason of a turn as the profile maps it.
     *
     * @since 0.1
     */
    public enum StopReason {
        /** The model ended its turn. */
        END_TURN("end_turn"),
        /** The model called a tool. */
        TOOL_USE("tool_use"),
        /** The model reached its token limit. */
        MAX_TOKENS("max_tokens"),
        /** The model refused. */
        REFUSAL("refusal"),
        /** Any other reason. */
        OTHER("other");

        private final String wireName;

        StopReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<StopReason> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The link of a task a worker followed.
     *
     * @since 0.1
     */
    public enum LinkRelation {
        /** The acknowledgement of an offer. */
        ACK("ack"),
        /** A submission. */
        SUBMIT("submit"),
        /** A question. */
        ASK("ask"),
        /** A consultation. */
        CONSULT("consult"),
        /** A yield of the envelope. */
        YIELD("yield"),
        /** A report that the task is infeasible. */
        INFEASIBLE("infeasible");

        private final String wireName;

        LinkRelation(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<LinkRelation> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The tool of a tool call.
     *
     * @param name   the tool's name
     * @param target a path, a command line, or a server link with its id; masked
     * @since 0.1
     */
    public record Tool(String name, @Nullable String target) {

        /**
         * @throws NullPointerException     if the name is {@code null}
         * @throws IllegalArgumentException if the name or the target is longer than the schema admits
         */
        public Tool {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty() || name.length() > 128) {
                throw new IllegalArgumentException("A tool name has 1 to 128 characters");
            }
            if (target != null && target.length() > 1000) {
                throw new IllegalArgumentException("A tool target has at most 1000 characters");
            }
        }
    }

    /**
     * The result of a tool call.
     *
     * @param bytes  the size of the result
     * @param failed whether the call failed
     * @since 0.1
     */
    public record Result(int bytes, boolean failed) {

        /**
         * @throws IllegalArgumentException if the size is negative
         */
        public Result {
            if (bytes < 0) {
                throw new IllegalArgumentException("The size of a tool result is not negative: " + bytes);
            }
        }
    }

    /**
     * The link a worker followed.
     *
     * @param rel    the relation
     * @param target the entity or role the link names
     * @since 0.1
     */
    public record Link(LinkRelation rel, @Nullable String target) {

        /**
         * @throws NullPointerException     if the relation is {@code null}
         * @throws IllegalArgumentException if the target is too long
         */
        public Link {
            Objects.requireNonNull(rel, "rel");
            if (target != null && target.length() > 128) {
                throw new IllegalArgumentException("A link target has at most 128 characters");
            }
        }
    }

    /**
     * The off-track signal of a {@code signal} entry, the same entry as the queue task record's {@code signals[]}.
     *
     * @param signal the signal
     * @param action the action taken
     * @since 0.1
     */
    public record Signal(SignalName signal, SignalAction action) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if an {@code agent_started} signal does not take the action {@code restart}
         */
        public Signal {
            Objects.requireNonNull(signal, SIGNAL_LABEL);
            Objects.requireNonNull(action, "action");
            if (signal == SignalName.AGENT_STARTED && action != SignalAction.RESTART) {
                throw new IllegalArgumentException(
                        "An 'agent_started' signal always takes the action 'restart', not '%s'"
                                .formatted(action.wireName()));
            }
        }
    }

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if a value breaks its rule or a member is present on a kind it does not belong
     *                                  to
     */
    @SuppressWarnings("java:S3776") // the closed-schema member table is one flat check list by design
    public ActivityEntry {
        if (seq < 1) {
            throw new IllegalArgumentException("'seq' counts from 1: " + seq);
        }
        if (attempt < 1) {
            throw new IllegalArgumentException("'attempt' counts from 1: " + attempt);
        }
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        if (kind.source() != source) {
            throw new IllegalArgumentException(
                    "The kind '%s' is recorded by the %s, not the %s".formatted(kind.wireName(),
                            kind.source().wireName(), source.wireName()));
        }
        onlyOn(kind, Kind.TEXT, text, "text");
        if (truncated && kind != Kind.TEXT) {
            throw new IllegalArgumentException("'truncated' belongs to a text entry, not a "
                    + kind.wireName() + " entry");
        }
        onlyOn(kind, Kind.TOOL_CALL, tool, "tool");
        onlyOn(kind, Kind.TOOL_RESULT, result, "result");
        onlyOn(kind, Kind.FILE_CHANGED, path, "path");
        onlyOn(kind, Kind.TURN, stopReason, "stop_reason");
        onlyOn(kind, Kind.TURN, usage, "usage");
        onlyOn(kind, Kind.LINK, link, "link");
        if (questionId != null && kind != Kind.QUESTION && kind != Kind.ANSWER_ACK) {
            throw new IllegalArgumentException("'question_id' belongs to a question or an answer_ack entry, not a "
                    + kind.wireName() + " entry");
        }
        onlyOn(kind, Kind.SIGNAL, signal, SIGNAL_LABEL);
        onlyOn(kind, Kind.ATTEMPT_END, end, "end");
        onlyOn(kind, Kind.ATTEMPT_END, unmappedLines, "unmapped_lines");
        if (kind == Kind.TOOL_CALL && tool == null) {
            throw new IllegalArgumentException("A tool_call entry has its tool");
        }
        if (kind == Kind.TOOL_RESULT && result == null) {
            throw new IllegalArgumentException("A tool_result entry has its result");
        }
        if (kind == Kind.LINK && link == null) {
            throw new IllegalArgumentException("A link entry has its link");
        }
        if (kind == Kind.SIGNAL && signal == null) {
            throw new IllegalArgumentException("A signal entry has its signal");
        }
        if (kind == Kind.ATTEMPT_END && end == null) {
            throw new IllegalArgumentException("An attempt_end entry has its end");
        }
        if (kind == Kind.QUESTION && questionId == null) {
            throw new IllegalArgumentException("A question entry has its question id");
        }
        if (text != null) {
            var length = text.codePointCount(0, text.length());
            if (length > TEXT_BOUND) {
                throw new IllegalArgumentException("A text entry has at most %d characters, not %d"
                        .formatted(TEXT_BOUND, length));
            }
        }
        if (path != null && path.length() > 1000) {
            throw new IllegalArgumentException("A path has at most 1000 characters");
        }
        if (questionId != null && !QUESTION_ID.matcher(questionId).matches()) {
            throw new IllegalArgumentException("Not a question id: " + questionId);
        }
        if (unmappedLines != null && unmappedLines < 0) {
            throw new IllegalArgumentException("'unmapped_lines' is not negative: " + unmappedLines);
        }
        requireJsonObject(usage);
    }

    /** @return whether this entry is a server entry */
    public boolean isServerEntry() {
        return source == Source.SERVER;
    }

    private static void onlyOn(Kind kind, Kind owner, @Nullable Object member, String name) {
        if (member != null && kind != owner) {
            throw new IllegalArgumentException("'%s' belongs to a %s entry, not a %s entry"
                    .formatted(name, owner.wireName(), kind.wireName()));
        }
    }

    private static void requireJsonObject(@Nullable String raw) {
        if (raw == null) {
            return;
        }
        if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("'usage' is one line: a line break splits the JSONL entry");
        }
        try (var parser = StrictJson.factory().createParser(raw)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalArgumentException("'usage' is one JSON object");
            }
            parser.skipChildren();
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("'usage' is exactly one JSON object");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("'usage' is no JSON object: " + raw, e);
        }
    }
}
