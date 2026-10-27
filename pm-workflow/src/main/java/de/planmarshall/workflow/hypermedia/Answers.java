/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.hypermedia;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The {@code facts.answers} an offer carries (Hypermedia Format § Answers to Earlier Questions): every item of a
 * question the receiving task continues from, in the order the questions were closed.
 * <p>
 * The task's representation renders the items as one table, {@code answers[N]} of {@link AnswerFact#COLUMNS}, and a
 * task with nothing to continue from carries none. Items are keyed by question: replaying the delivery of the same
 * answer replaces its item in place, so a retried delivery never doubles a row.
 *
 * @param items the items, one per answered part of a closed decision
 * @since 0.1
 */
public record Answers(List<AnswerFact> items) {

    /**
     * @throws NullPointerException if the list or one of its items is {@code null}
     */
    public Answers {
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
        for (var item : items) {
            Objects.requireNonNull(item, "item");
        }
    }

    /** @return the empty set of answers */
    public static Answers empty() {
        return new Answers(List.of());
    }

    /**
     * @param item the item to carry
     * @return these answers with the item added, or with the item's question replaced where it is already carried
     */
    public Answers plus(AnswerFact item) {
        Objects.requireNonNull(item, "item");
        var merged = new ArrayList<AnswerFact>(items.size() + 1);
        var replaced = false;
        for (var existing : items) {
            if (existing.questionId().equals(item.questionId())) {
                merged.add(item);
                replaced = true;
            } else {
                merged.add(existing);
            }
        }
        if (!replaced) {
            merged.add(item);
        }
        return new Answers(merged);
    }

    /**
     * @param other the answers to add
     * @return these answers with every item of the other set, an item of the other set replacing the one of the same
     *         question
     */
    public Answers plusAll(Answers other) {
        Objects.requireNonNull(other, "other");
        var merged = this;
        for (var item : other.items()) {
            merged = merged.plus(item);
        }
        return merged;
    }

    /** @return whether there is no answer to carry */
    public boolean isEmpty() {
        return items.isEmpty();
    }

    /**
     * @return the table's rows in {@link AnswerFact#COLUMNS} order, one row per item
     */
    public List<List<String>> rows() {
        return items.stream().map(AnswerFact::cells).toList();
    }
}
