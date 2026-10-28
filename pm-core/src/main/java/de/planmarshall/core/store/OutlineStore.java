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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A plan's {@code outline.json} (Store Schemas § Outline Record): the accepted outline as {@code current}, the
 * server-owned state of every deliverable, and every earlier revision in {@code history}. {@code outline.md} is
 * rendered from {@link #current()} and never read back.
 * <p>
 * The accepting transaction moves the record it replaces into the history and keeps it unchanged. A deliverable a
 * re-planning defers or removes keeps its state in {@code deliverableStatus} and its content in the record, so the
 * plan's history is not rewritten.
 *
 * @param current           the accepted revision
 * @param deliverableStatus the state of every deliverable, by its number
 * @param history           every earlier revision, oldest first
 * @since 0.1
 */
public record OutlineStore(Revision current, Map<Integer, DeliveryStatus> deliverableStatus,
List<Revision> history) {

    /** The format version of an outline store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /**
     * @throws IllegalArgumentException if a member is out of its bounds, a current deliverable carries no state, or a
     *                                  revision is out of order
     * @throws NullPointerException     if a value is {@code null}
     */
    public OutlineStore {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(deliverableStatus, "deliverableStatus");
        deliverableStatus = Map.copyOf(new LinkedHashMap<>(deliverableStatus));
        Objects.requireNonNull(history, "history");
        history = List.copyOf(history);
        var previous = 0;
        for (var revision : history) {
            if (revision.revision() <= previous || revision.revision() >= current.revision()) {
                throw new IllegalArgumentException(
                        "the history revisions are older than the current revision %d and strictly increasing: %d"
                                .formatted(current.revision(), revision.revision()));
            }
            previous = revision.revision();
        }
        if (!history.isEmpty() && current.revision() != history.getLast().revision() + 1) {
            throw new IllegalArgumentException(
                    "the current revision follows the last history revision: %d after %d".formatted(
                            current.revision(), history.getLast().revision()));
        }
        for (var deliverable : current.outlineRecord().deliverables()) {
            if (!deliverableStatus.containsKey(deliverable.number())) {
                throw new IllegalArgumentException(
                        "the deliverable %d of the current revision carries no state".formatted(deliverable.number()));
            }
        }
    }

    /**
     * @param revision the first revision of a plan's outline
     * @return the store that holds it, every deliverable active
     */
    public static OutlineStore first(Revision revision) {
        Objects.requireNonNull(revision, "revision");
        if (revision.revision() != 1) {
            throw new IllegalArgumentException("the first revision of an outline is revision 1: " + revision.revision());
        }
        var statuses = new LinkedHashMap<Integer, DeliveryStatus>();
        revision.outlineRecord().deliverables()
                .forEach(deliverable -> statuses.put(deliverable.number(), DeliveryStatus.ACTIVE));
        return new OutlineStore(revision, statuses, List.of());
    }

    /**
     * Accepts the next revision and moves the current one into the history. A deliverable the new revision no longer
     * holds keeps its state.
     *
     * @param next the revision a task submitted
     * @return the store after the acceptance
     * @throws IllegalArgumentException if the revision does not follow the current one
     */
    public OutlineStore accept(Revision next) {
        Objects.requireNonNull(next, "next");
        if (next.revision() != current.revision() + 1) {
            throw new IllegalArgumentException(
                    "the submitted revision is %d, the next one is %d".formatted(next.revision(),
                            current.revision() + 1));
        }
        var statuses = new LinkedHashMap<>(deliverableStatus);
        next.outlineRecord().deliverables().forEach(deliverable -> statuses.putIfAbsent(deliverable.number(),
                DeliveryStatus.ACTIVE));
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(current);
        return new OutlineStore(next, statuses, nextHistory);
    }

    /**
     * @param number a deliverable number
     * @return the state of the deliverable; empty when the outline never held it
     */
    public Optional<DeliveryStatus> status(int number) {
        return Optional.ofNullable(deliverableStatus.get(number));
    }

    /**
     * @param number a deliverable number
     * @return whether the deliverable is active
     */
    public boolean active(int number) {
        return status(number).orElse(DeliveryStatus.REMOVED) == DeliveryStatus.ACTIVE;
    }

    /** The state of a deliverable. */
    public enum DeliveryStatus {
        /** The deliverable is part of the plan. */
        ACTIVE("active"),
        /** A re-planning deferred it. */
        DEFERRED("deferred"),
        /** A re-planning removed it. */
        REMOVED("removed");

        private final String wireName;

        DeliveryStatus(String wireName) {
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
        public static Optional<DeliveryStatus> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One accepted revision.
     *
     * @param revision      the revision number, from 1
     * @param submittedAt   the instant the transaction accepted it
     * @param taskId        the task whose submission carried the record
     * @param outlineRecord the submitted record
     * @since 0.1
     */
    public record Revision(int revision, Instant submittedAt, String taskId, OutlineRecord outlineRecord) {

        /**
         * @throws IllegalArgumentException if the revision or the task id is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Revision {
            if (revision < 1) {
                throw new IllegalArgumentException("a revision number is at least 1: " + revision);
            }
            Objects.requireNonNull(submittedAt, "submittedAt");
            Objects.requireNonNull(taskId, "taskId");
            if (taskId.isEmpty() || taskId.length() > 128) {
                throw new IllegalArgumentException("a task id is 1 to 128 characters: " + taskId.length());
            }
            Objects.requireNonNull(outlineRecord, "outlineRecord");
        }
    }
}
