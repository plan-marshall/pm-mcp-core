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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The topics of a role are defined by the {@code role} field of the corpus items, never by their paths (Model Work
 * § Corpus Topics of a Role): the review statuses {@code contested} and {@code retired} do not count, the {@code judge/}
 * topic grades no role, a {@code zero_tool} role has the own topic alone, and a judgment role is graded on
 * {@code decision/question} alone.
 */
@DisplayName("Role topics")
class RoleTopicsTest {

    private static final String ROLE = "review-code";

    @Test
    @DisplayName("a writer role has the own and the generic topic")
    void writerTopics() {
        var items = List.of(
                item("review/own", ROLE, ImportedItem.ReviewStatus.CONFIRMED),
                item("review/draft", ROLE, ImportedItem.ReviewStatus.DRAFT),
                item("review/generic", "worker", ImportedItem.ReviewStatus.CONFIRMED));

        var topics = RoleTopics.of(ROLE, RoleClass.WRITER, items);

        assertEquals(Set.of(Topic.OWN, Topic.GENERIC), topics.topics());
    }

    @Test
    @DisplayName("only draft and confirmed items count, and the judge topic grades no role")
    void statuses() {
        var items = List.of(
                item("review/contested", ROLE, ImportedItem.ReviewStatus.CONTESTED),
                item("review/retired", ROLE, ImportedItem.ReviewStatus.RETIRED),
                item("judge/right", ROLE, ImportedItem.ReviewStatus.CONFIRMED));

        assertTrue(RoleTopics.of(ROLE, RoleClass.READ_ONLY, items).isEmpty());
        assertEquals(0, RoleTopics.items(items, Topic.OWN, ROLE).size());
    }

    @Test
    @DisplayName("a zero_tool role has the own topic alone, and without items of its own no topic")
    void zeroTool() {
        var generic = List.of(item("review/generic", "worker", ImportedItem.ReviewStatus.CONFIRMED));
        var own = List.of(item("review/own", ROLE, ImportedItem.ReviewStatus.CONFIRMED));

        assertAll(
                () -> assertTrue(RoleTopics.of(ROLE, RoleClass.ZERO_TOOL, generic).isEmpty()),
                () -> assertEquals(Set.of(Topic.OWN), RoleTopics.of(ROLE, RoleClass.ZERO_TOOL, own).topics()),
                () -> assertEquals(Set.of(Topic.OWN, Topic.GENERIC),
                        RoleTopics.of(ROLE, RoleClass.READ_ONLY, concat(generic, own)).topics()));
    }

    @Test
    @DisplayName("a judgment role is graded on the decision/question items alone")
    void judgment() {
        var items = List.of(
                item("decision/question/one", "architecture", ImportedItem.ReviewStatus.CONFIRMED),
                item("review/own", ROLE, ImportedItem.ReviewStatus.CONFIRMED));

        var topics = RoleTopics.judgment(items);

        assertEquals(Set.of(Topic.GENERIC), topics.topics());
        assertEquals(1, RoleTopics.judgmentItems(items).size());
    }

    @Test
    @DisplayName("the topic items keep the corpus order and each item once")
    void topicItems() {
        var first = item("review/first", ROLE, ImportedItem.ReviewStatus.CONFIRMED);
        var second = item("review/second", ROLE, ImportedItem.ReviewStatus.CONFIRMED);

        var selected = RoleTopics.items(List.of(first, second, first), Topic.OWN, ROLE);

        assertEquals(List.of(first, second), selected);
    }

    @Test
    @DisplayName("an item of another role is in no topic of this role")
    void foreignItem() {
        var items = List.of(item("review/other", "review-plan", ImportedItem.ReviewStatus.CONFIRMED));

        assertFalse(RoleTopics.items(items, Topic.OWN, ROLE).size() > 0);
    }

    private static ImportedItem item(String id, String role, ImportedItem.ReviewStatus status) {
        return QualificationFixtures.item(id, role, "clean").status(status).build();
    }

    private static List<ImportedItem> concat(List<ImportedItem> first, List<ImportedItem> second) {
        var all = new ArrayList<ImportedItem>(first);
        all.addAll(second);
        return all;
    }
}
