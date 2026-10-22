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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The question log of a scope (PM-WF-12, PM-IMPL-2): one line per event, appended through the transaction of the scope
 * and never changed, the last readable line of a question its current line, and the two invariants the store holds
 * under the lock of the scope. The cases are tested one group per class, beside this one: the revision
 * ({@link QuestionLogRevisionTest}), a closing line ({@link QuestionLogClosingTest}), the revision of an answer
 * ({@link QuestionLogAnswerRevisionTest}), the current line ({@link QuestionLogCurrentLineTest}), a log with a refused
 * line ({@link QuestionLogRefusedLineTest}) and a line malformed for the log ({@link QuestionLogMalformedTest}).
 */
@DisplayName("Question log")
class QuestionLogTest extends QuestionLogFixture {

    @Test
    @DisplayName("lies at the path base of a plan or an epic, below workspace/ and below pending/<entry_id>/")
    void location() {
        var entry = new EntryId("ent-0123456789abcdef");
        var workspace = JournalScope.workspace(base, PROJECT);
        var epic = JournalScope.epic(base, PROJECT, "an-epic");
        var lessons = JournalScope.lessons(base, PROJECT);

        assertAll(
                () -> assertEquals(scope.pathBase().resolve("questions.jsonl"), log.store()),
                () -> assertEquals("questions.jsonl", new QuestionLog(epic).storePath()),
                () -> assertEquals(epic.pathBase().resolve("questions.jsonl"), new QuestionLog(epic).store()),
                () -> assertEquals("workspace/questions.jsonl", new QuestionLog(workspace).storePath()),
                () -> assertEquals("pending/ent-0123456789abcdef/questions.jsonl",
                        QuestionLog.ofEntry(workspace, entry).storePath()),
                () -> assertThrows(IllegalArgumentException.class, () -> QuestionLog.ofEntry(scope, entry)),
                () -> assertThrows(IllegalArgumentException.class, () -> new QuestionLog(lessons)));
    }
}
