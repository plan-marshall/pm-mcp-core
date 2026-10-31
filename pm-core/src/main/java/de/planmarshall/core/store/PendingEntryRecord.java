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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.With;

import de.planmarshall.core.store.WorkflowStatus.CallFrame;
import de.planmarshall.core.store.WorkflowStatus.PendingCondition;

/**
 * The pending entry record {@code pending/<entry_id>.json} of the workspace scope (PM-CLIENT-1): what a client named
 * when it opened an entry for a scope that does not exist yet, and the state store of that entry's bootstrap until a
 * terminal of the bootstrap deletes it.
 * <p>
 * The envelope is typed: the id, the kind, what the user named ({@code requested_id}, {@code hint}, recorded as given
 * and validated only by the bootstrap), the parent epic of a plan the server starts, and the instants. Of the bootstrap
 * state the members the engine owns are typed as well: the state position, the call frames and the condition overlay,
 * in the shapes of the workflow state store, and the reason of the last refused re-check. The members a source unit and
 * the bootstrap's states own, {@code facts}, {@code source} and {@code options}, are kept as the raw JSON text they
 * were read with and written back unchanged, as the status document keeps the members the engine does not own
 * (ADR 001 of {@code core.store}).
 * <p>
 * The creation instant is the age the pending-record sweep compares with the pending entry record maximum age; the
 * update instant changes with every bootstrap transition and never makes a record younger.
 *
 * @param entryId     the id, which names the record
 * @param kind        whether the entry starts a plan or an epic
 * @param requestedId the id the client named when it opened the entry, as given, at most 64 characters;
 *                    {@code null} when none
 * @param hint        the user's words the client relayed when it opened the entry, at most 4000 characters;
 *                    {@code null} when none
 * @param fromEpic    the parent epic of a plan the server starts for an epic row; {@code null} otherwise and always
 *                    for an epic
 * @param createdAt   the instant the record was created
 * @param updatedAt   the instant of the last write
 * @param bootstrap   the state of the entry's bootstrap
 * @since 0.1
 */
@With
@Builder(toBuilder = true)
public record PendingEntryRecord(EntryId entryId, Kind kind, @Nullable String requestedId, @Nullable String hint,
@Nullable String fromEpic, Instant createdAt, Instant updatedAt, Bootstrap bootstrap) {

    /** The format version of a pending entry record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The longest {@code requested_id} the schema admits. */
    public static final int MAX_REQUESTED_ID = 64;

    /** The longest {@code hint} the schema admits. */
    public static final int MAX_HINT = 4000;

    private static final Pattern EPIC_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");
    private static final Pattern REFUSAL = Pattern.compile("[a-z][a-z0-9_]*");

    /**
     * What an entry starts: it selects the bootstrap unit.
     *
     * @since 0.1
     */
    public enum Kind {
        /** A plan, through {@code bootstrap-plan}. */
        PLAN("plan"),
        /** An epic, through {@code bootstrap-epic}. */
        EPIC("epic");

        private final String wireName;

        Kind(String wireName) {
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
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The state of an entry's bootstrap.
     *
     * @param state            the fully qualified state id of the bootstrap entity
     * @param callStack        the call frames of the called source unit, at most {@value WorkflowStatus#MAX_CALL_DEPTH}
     * @param facts            the state facts of the bootstrap, a JSON object as raw text, kept unchanged
     * @param source           the source record a source unit returned, a JSON object as raw text, kept unchanged;
     *                         {@code null} until one returned
     * @param options          the commit options chosen in {@code start.ready}, a JSON object as raw text, kept
     *                         unchanged
     * @param pendingCondition the condition overlay of the bootstrap entity; {@code null} when there is none
     * @param lastRefusal      the reason of the last refused re-check of {@code start.create-scope}; {@code null}
     *                         otherwise
     * @since 0.1
     */
    @With
    @Builder(toBuilder = true)
    public record Bootstrap(String state, List<CallFrame> callStack, String facts, @Nullable String source,
    String options, @Nullable PendingCondition pendingCondition, @Nullable String lastRefusal) {

        /**
         * @throws NullPointerException     if the state, the call stack, the facts or the options are {@code null}
         * @throws IllegalArgumentException if the state is blank, the call stack is deeper than the schema admits, a
         *                                  raw member is no JSON object, or the refusal reason breaks its form
         */
        public Bootstrap {
            Objects.requireNonNull(state, "state");
            if (state.isBlank()) {
                throw new IllegalArgumentException("'bootstrap.state' must not be blank");
            }
            callStack = List.copyOf(Objects.requireNonNull(callStack, "callStack"));
            if (callStack.size() > WorkflowStatus.MAX_CALL_DEPTH) {
                throw new IllegalArgumentException("'bootstrap.call_stack' has %d frames, at most %d are allowed"
                        .formatted(callStack.size(), WorkflowStatus.MAX_CALL_DEPTH));
            }
            requireObject(Objects.requireNonNull(facts, "facts"), "bootstrap.facts");
            if (source != null) {
                requireObject(source, "bootstrap.source");
            }
            requireObject(Objects.requireNonNull(options, "options"), "bootstrap.options");
            if (lastRefusal != null && !REFUSAL.matcher(lastRefusal).matches()) {
                throw new IllegalArgumentException("'bootstrap.last_refusal' is not a reason: " + lastRefusal);
            }
        }

        /** @return the condition overlay of the bootstrap entity; empty when there is none */
        public Optional<PendingCondition> condition() {
            return Optional.ofNullable(pendingCondition);
        }

        private static void requireObject(String raw, String name) {
            var text = raw.strip();
            if (!text.startsWith("{") || !text.endsWith("}")) {
                throw new IllegalArgumentException("'%s' must be a JSON object: %s".formatted(name, raw));
            }
        }
    }

    /**
     * @throws NullPointerException     if the id, the kind, an instant or the bootstrap is {@code null}
     * @throws IllegalArgumentException if a value breaks its form, if an epic names a parent epic, or if the record
     *                                  was updated before it was created
     */
    public PendingEntryRecord {
        Objects.requireNonNull(entryId, "entryId");
        Objects.requireNonNull(kind, "kind");
        if (requestedId != null && requestedId.length() > MAX_REQUESTED_ID) {
            throw new IllegalArgumentException("'requested_id' has more than %d characters".formatted(
                    MAX_REQUESTED_ID));
        }
        if (hint != null && hint.length() > MAX_HINT) {
            throw new IllegalArgumentException("'hint' has more than %d characters".formatted(MAX_HINT));
        }
        if (fromEpic != null && !EPIC_ID.matcher(fromEpic).matches()) {
            throw new IllegalArgumentException("'from_epic' is not an epic id: " + fromEpic);
        }
        if (kind == Kind.EPIC && fromEpic != null) {
            throw new IllegalArgumentException("An epic entry names no parent epic: " + fromEpic);
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(createdAt)) {
            throw new IllegalArgumentException("'updated_at' %s lies before 'created_at' %s".formatted(updatedAt,
                    createdAt));
        }
        Objects.requireNonNull(bootstrap, "bootstrap");
    }

    /**
     * @param instant an instant, typically the instant the maximum age reaches back to
     * @return whether the record was created before it
     */
    public boolean createdBefore(Instant instant) {
        return createdAt.isBefore(Objects.requireNonNull(instant, "instant"));
    }
}
