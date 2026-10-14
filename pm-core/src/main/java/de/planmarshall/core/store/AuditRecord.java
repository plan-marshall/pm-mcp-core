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

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

/**
 * One record of the audit log {@code logs/audit.jsonl} (PM-SEC-6): one line of the hash chain.
 * <p>
 * Every record names the SHA-256 of the canonical line before it ({@code prev_hash}), so a removed, reordered or
 * edited record breaks the chain at the first line after it. The first record of a log that never rotated chains to
 * {@link AuditCodec#GENESIS_HASH}; the first record after a rotation chains to the checkpoint line that closed the
 * rotated log (the rule is {@link AuditLog}'s).
 * <p>
 * The category, the outcome and the type of the scope are closed sets. The details are the fields of the event; a
 * value is a string, an integer, a boolean or a list of strings.
 *
 * @param eventId   the id of the record: a lower-case UUID of version 7
 * @param prevHash  the SHA-256 of the previous canonical line, in lower-case hex
 * @param timestamp the instant of the record
 * @param category  the category of the event
 * @param event     the name of the event
 * @param scopeType the type of the scope the event belongs to; {@code null} when it has none
 * @param scopeId   the id of the scope; {@code null} for a scope without an id or no scope
 * @param actor     the client profile id or the OS principal that caused the event
 * @param outcome   the outcome the event records
 * @param details   the fields of the event, in the order they were given; unmodifiable
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record AuditRecord(String eventId, String prevHash, Instant timestamp, Category category, String event,
Scope scopeType, String scopeId, String actor, Outcome outcome, Map<String, Object> details) {

    /** The format version of a record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The name of the event a rotation appends as the last record of the rotated log. */
    public static final String EVENT_CHECKPOINT = "checkpoint";

    /** The form of an {@code event_id}: a lower-case UUID of version 7. */
    static final Pattern EVENT_ID = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    /** The form of a SHA-256 or an HMAC-SHA-256 in lower-case hex. */
    static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    /**
     * The category of an audit record, a closed set.
     *
     * @since 0.1
     */
    public enum Category {
        /** A state transition. */
        TRANSITION("transition"),
        /** A job execution. */
        JOB("job"),
        /** An operator action. */
        OPERATOR("operator"),
        /** An integrity or per-use check. */
        SECURITY("security"),
        /** The identity of a session that enters a scope. */
        IDENTITY("identity"),
        /** A credential lookup or refresh. */
        CREDENTIAL("credential"),
        /** A store or one of its locks. */
        STORE("store");

        private final String wireName;

        Category(String wireName) {
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
        public static Optional<Category> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The outcome an audit record records, a closed set.
     *
     * @since 0.1
     */
    public enum Outcome {
        /** An action that was allowed. */
        ALLOWED("allowed"),
        /** An action that was refused. */
        REFUSED("refused"),
        /** An action that was carried out. */
        SUCCEEDED("succeeded"),
        /** An action that was not carried out, or whose completion was not recorded. */
        FAILED("failed"),
        /** A condition the operator should know of. */
        WARNING("warning");

        private final String wireName;

        Outcome(String wireName) {
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
        public static Optional<Outcome> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The type of the scope an audit record belongs to, a closed set.
     *
     * @since 0.1
     */
    public enum Scope {
        /** A plan, named by its plan id. */
        PLAN("plan"),
        /** An epic, named by its epic id. */
        EPIC("epic"),
        /** The workspace scope of a project. */
        WORKSPACE("workspace"),
        /** A project. */
        PROJECT("project"),
        /** The machine: the runtime, an operator verb, a sweep, a lease or the audit chain. */
        MACHINE("machine");

        private final String wireName;

        Scope(String wireName) {
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
        public static Optional<Scope> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Normalizes an {@link Integer} detail to a {@link Long}, so a record read back equals the record written.
     *
     * @throws NullPointerException     if a required component, a detail name or a detail value is {@code null}
     * @throws IllegalArgumentException if the event id or the hash breaks its form, the event or the actor is blank,
     *                                  or a detail value is not a string, an integer, a boolean or a list of strings
     */
    public AuditRecord {
        requireForm(EVENT_ID, Objects.requireNonNull(eventId, "eventId"), "event_id");
        requireForm(HASH, Objects.requireNonNull(prevHash, "prevHash"), "prev_hash");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(category, "category");
        requireText(event, "event");
        requireText(actor, "actor");
        Objects.requireNonNull(outcome, "outcome");
        details = normalized(Objects.requireNonNull(details, "details"));
    }

    /** @return whether this record is the checkpoint that closes a rotated log */
    public boolean isCheckpoint() {
        return EVENT_CHECKPOINT.equals(event);
    }

    /**
     * @param pattern the form
     * @param value   a value
     * @param field   the name of the field in the refusal
     * @throws IllegalArgumentException if the value does not have the form
     */
    static void requireForm(Pattern pattern, String value, String field) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' breaks its form: %s".formatted(field, value));
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException("'%s' must not be blank".formatted(field));
        }
    }

    private static Map<String, Object> normalized(Map<String, Object> details) {
        var normalized = new LinkedHashMap<String, Object>();
        details.forEach((name, value) -> normalized.put(Objects.requireNonNull(name, "detail name"),
                normalizedValue(name, Objects.requireNonNull(value, name))));
        return Collections.unmodifiableMap(normalized);
    }

    private static Object normalizedValue(String name, Object value) {
        return switch (value) {
            case String text -> text;
            case Long number -> number;
            case Integer number -> number.longValue();
            case Boolean flag -> flag;
            case List<?> items when items.stream().allMatch(String.class::isInstance) -> List.copyOf(items);
            default -> throw new IllegalArgumentException(
                    "Detail '%s' is not a string, an integer, a boolean or a list of strings: %s".formatted(name,
                            value));
        };
    }
}
