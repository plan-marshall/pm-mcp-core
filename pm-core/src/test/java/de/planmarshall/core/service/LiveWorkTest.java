/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.LiveWork.Item;
import de.planmarshall.core.service.LiveWork.Kind;

/**
 * The live work of a project (PM-EXEC-2): each item is named by its kind and its id, and an empty report means the
 * project has none.
 */
@DisplayName("Live work")
class LiveWorkTest {

    @Nested
    @DisplayName("an item")
    class ItemContract {

        @ParameterizedTest(name = "{0} {1} is named ''{2}''")
        @CsvSource({"PLAN, add-login, plan add-login", "EPIC, auth-rework, epic auth-rework",
                "JOB, 01970000-0000-7000-8000-000000000001, job 01970000-0000-7000-8000-000000000001"})
        @DisplayName("is named by the code of its kind and its id")
        void namedByKindAndId(Kind kind, String id, String name) {
            var item = new Item(kind, id);

            assertAll(
                    () -> assertEquals(kind, item.kind()),
                    () -> assertEquals(id, item.id()),
                    () -> assertEquals(name, item.toString()));
        }

        @Test
        @DisplayName("refuses a missing kind or id")
        void missingComponents() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new Item(null, "add-login")),
                    () -> assertThrows(NullPointerException.class, () -> new Item(Kind.PLAN, null)));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t"})
        @DisplayName("refuses a blank id")
        void blankId(String id) {
            assertThrows(IllegalArgumentException.class, () -> new Item(Kind.JOB, id));
        }
    }

    @Test
    @DisplayName("the kinds are exactly plan, epic and job")
    void kinds() {
        assertEquals(List.of("plan", "epic", "job"), List.of(Kind.values()).stream().map(Kind::code).toList());
    }

    @Nested
    @DisplayName("a report")
    class Report {

        @Test
        @DisplayName("that is empty means the project has no live work")
        void empty() {
            LiveWork none = projectId -> List.of();

            assertTrue(none.of("pm-mcp-core").isEmpty());
        }

        @Test
        @DisplayName("names every item of the project it was asked for")
        void namesItems() {
            var items = List.of(new Item(Kind.PLAN, "add-login"), new Item(Kind.JOB, "job-1"));
            LiveWork work = projectId -> "pm-mcp-core".equals(projectId) ? items : List.of();

            assertAll(
                    () -> assertEquals(items, work.of("pm-mcp-core")),
                    () -> assertTrue(work.of("other-project").isEmpty()));
        }
    }
}
