/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The topics a role's qualification record must hold (Model Work § Corpus Topics of a Role). The topics are defined by
 * the {@code role} field of the corpus items, never by their paths: the items whose {@code role} names the role are its
 * {@code own} items, and the generic items ({@code role: worker}) are the {@code generic} ones for every role of the
 * classes {@code writer} and {@code read_only}.
 * <p>
 * Only items with {@code review.status} {@code draft} or {@code confirmed} count; items of the topic {@code judge/}
 * grade no role. A {@code writer} or {@code read_only} role without items of its own has the topic {@code generic}
 * alone; a {@code zero_tool} role has the topic {@code own} alone and, without items of its own, no topics at all, so it
 * cannot qualify. A judgment role's topic is {@code generic} alone, made of the {@code decision/question} items.
 *
 * @param topics the topics the record must hold; unmodifiable, in declaration order
 * @since 0.1
 */
public record RoleTopics(Set<Topic> topics) {

    /** The {@code role} field of the generic items. */
    public static final String GENERIC_ROLE = "worker";

    /** The item topic of the judge instrument check, which grades no role. */
    public static final String JUDGE_TOPIC = "judge";

    /**
     * @throws NullPointerException if the topics are {@code null}
     */
    public RoleTopics {
        topics = Collections.unmodifiableSet(Objects.requireNonNull(topics, "topics").isEmpty()
                ? EnumSet.noneOf(Topic.class)
                : EnumSet.copyOf(topics));
    }

    /**
     * @param role  the role name
     * @param items the items of the corpus
     * @return the topics the role's record must hold
     */
    private static RoleTopics of(String role, List<ImportedItem> items) {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(items, "items");
        var topics = EnumSet.noneOf(Topic.class);
        if (counted(items, item -> role.equals(item.role()))) {
            topics.add(Topic.OWN);
        }
        if (counted(items, item -> GENERIC_ROLE.equals(item.role()))) {
            topics.add(Topic.GENERIC);
        }
        return new RoleTopics(topics);
    }

    /**
     * @param role      the role name
     * @param roleClass the role's class
     * @param items     the items of the corpus
     * @return the topics the role's record must hold, with the generic topic only for a role that can be graded on it
     */
    public static RoleTopics of(String role, RoleClass roleClass, List<ImportedItem> items) {
        Objects.requireNonNull(roleClass, "roleClass");
        var all = of(role, items);
        var topics = EnumSet.noneOf(Topic.class);
        if (all.contains(Topic.OWN)) {
            topics.add(Topic.OWN);
        }
        if (roleClass != RoleClass.ZERO_TOOL && all.contains(Topic.GENERIC)) {
            topics.add(Topic.GENERIC);
        }
        return new RoleTopics(topics);
    }

    /** @return {@code true} if the role has no topic and so cannot qualify */
    public boolean isEmpty() {
        return topics.isEmpty();
    }

    /**
     * @param topic a topic
     * @return {@code true} if the record must hold the topic
     */
    public boolean contains(Topic topic) {
        return topics.contains(topic);
    }

    /**
     * @param items  the items of the corpus
     * @param filter the membership test
     * @return {@code true} if at least one counted item passes the test
     */
    private static boolean counted(List<ImportedItem> items, Predicate<ImportedItem> filter) {
        for (var item : items) {
            if (counts(item) && filter.test(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param item a corpus item
     * @return {@code true} if the item grades a role: its status is {@code draft} or {@code confirmed} and it is not
     *         an item of the judge instrument check
     */
    public static boolean counts(ImportedItem item) {
        var status = item.review().status();
        return status != ImportedItem.ReviewStatus.CONTESTED && status != ImportedItem.ReviewStatus.RETIRED
                && !JUDGE_TOPIC.equals(item.topic());
    }

    /**
     * @param items the items of the corpus
     * @param topic the topic
     * @param role  the role name
     * @return the counted items of the topic, in the order of the corpus, each item once
     */
    public static List<ImportedItem> items(List<ImportedItem> items, Topic topic, String role) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(role, "role");
        return selected(items, topic == Topic.GENERIC
                ? item -> GENERIC_ROLE.equals(item.role())
                : item -> role.equals(item.role()));
    }

    private static List<ImportedItem> selected(List<ImportedItem> items, Predicate<ImportedItem> membership) {
        var selected = new ArrayList<ImportedItem>();
        var seen = new HashSet<String>();
        for (var item : items) {
            if (!counts(item) || !seen.add(item.id()) || !membership.test(item)) {
                continue;
            }
            selected.add(item);
        }
        return List.copyOf(selected);
    }
}
