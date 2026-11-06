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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The question log of a scope (PM-WF-12, PM-IMPL-2): one line per event, appended through the transaction of the scope
 * and never changed, the last readable line of a question its current line, and the two invariants the store holds
 * under the lock of the scope. The cases are tested one group per class, beside this one: the revision
 * ({@link QuestionLogRevisionTest}), a closing line ({@link QuestionLogClosingTest}), the revision of an answer
 * ({@link QuestionLogAnswerRevisionTest}), the current line ({@link QuestionLogCurrentLineTest}), a log with a refused
 * line ({@link QuestionLogRefusedLineTest}) and a line malformed for the log ({@link QuestionLogMalformedTest}). The
 * store paths are the layout contract; the entry and the epic are generated.
 */
@EnableGeneratorController
@DisplayName("Question log")
class QuestionLogTest extends QuestionLogFixture {

    @Test
    @DisplayName("lies at the path base of a plan or an epic, below workspace/ and below pending/<entry_id>/")
    void location() {
        var entryId = "ent-%016x".formatted(Generators.longs(0, Long.MAX_VALUE).next());
        var entry = new EntryId(entryId);
        var workspace = JournalScope.workspace(base, PROJECT);
        var epic = JournalScope.epic(base, PROJECT, Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT));
        var lessons = JournalScope.lessons(base, PROJECT);

        assertAll(
                () -> assertEquals(scope.pathBase().resolve("questions.jsonl"), log.store(),
                        "the plan log should lie at its path base"),
                () -> assertEquals("questions.jsonl", new QuestionLog(epic).storePath(),
                        "the epic log should be named questions.jsonl"),
                () -> assertEquals(epic.pathBase().resolve("questions.jsonl"), new QuestionLog(epic).store(),
                        "the epic log should lie at its path base"),
                () -> assertEquals("workspace/questions.jsonl", new QuestionLog(workspace).storePath(),
                        "the workspace log should lie below workspace"),
                () -> assertEquals("pending/" + entryId + "/questions.jsonl",
                        QuestionLog.ofEntry(workspace, entry).storePath(),
                        "the entry log should lie below its entry"),
                () -> assertThrows(IllegalArgumentException.class, () -> QuestionLog.ofEntry(scope, entry),
                        "an entry log of a plan should be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new QuestionLog(lessons),
                        "the lessons scope should have no question log"));
    }
}
