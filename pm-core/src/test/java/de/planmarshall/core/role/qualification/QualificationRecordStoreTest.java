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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The qualification records of a role set (Store Schemas § Role Qualification Record; § Judge Check Record): one file
 * per role below {@code qualification/}, written by atomic rename under the store's lock, and a lookup that answers
 * empty for a set that holds none and refuses a record that does not parse.
 */
@DisplayName("Qualification record store")
class QualificationRecordStoreTest {

    @TempDir
    Path base;

    @Test
    @DisplayName("a written record is read back from the role set")
    void writeAndRead() throws Exception {
        var store = store();
        var record = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, new BigDecimal("90")),
                new BigDecimal("10"));

        store.write(record);

        assertTrue(Files.exists(base.resolve("projects/pm-mcp-core/roles/qualification/review-code.json")));
        assertEquals(Optional.of(record), store.lookup("review-code"));
    }

    @Test
    @DisplayName("a role set without the role's record answers empty")
    void absent() {
        assertEquals(Optional.empty(), store().lookup("review-code"));
    }

    @Test
    @DisplayName("a role name outside its grammar is refused before it reaches a path")
    void roleGrammar() {
        assertThrows(IllegalArgumentException.class, () -> store().file("../escape"));
    }

    @Test
    @DisplayName("an unreadable record is refused, never answered as an absent one")
    void unreadable() throws Exception {
        var file = directory().resolve("review-code.json");
        Files.createDirectories(directory());
        Files.writeString(file, "{\"format_version\":1}", UTF_8);

        assertThrows(StoreRefusal.class, () -> store().lookup("review-code"));
    }

    @Test
    @DisplayName("a lookup reads the project set before the artifact's set")
    void firstOf() {
        var project = store();
        var projectRecord = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, new BigDecimal("90")),
                new BigDecimal("10"));
        project.write(projectRecord);
        QualificationRecords shipped = role -> "review-code".equals(role)
                ? Optional.of(QualificationFixtures.record(Map.of(Topic.OWN, new BigDecimal("70"))))
                : Optional.empty();

        var lookup = QualificationRecords.firstOf(project, shipped);

        assertEquals(Optional.of(projectRecord), lookup.lookup("review-code"));
        assertEquals(Optional.empty(), lookup.lookup("another-role"));
    }

    @Test
    @DisplayName("the judge check record is written beside the role records")
    void judgeCheck() {
        var checks = new JudgeCheckStore(directory(), lock(base), QualificationFixtures.locks());
        var record = new JudgeCheck.Record("a".repeat(64), QualificationFixtures.HARNESS,
                QualificationFixtures.JUDGE_MODEL, QualificationFixtures.CORPUS_REVISION, new BigDecimal("100"),
                BigDecimal.ZERO, new BigDecimal("50"), 0, QualificationFixtures.GRADED_AT, List.of());

        checks.write(record);

        assertEquals(JudgeCheckStore.FILE_NAME, checks.file().getFileName().toString());
        assertEquals(Optional.of(record), checks.lookup());
        assertTrue(Files.exists(directory().resolve("judge-check.json")));
    }

    private QualificationRecordStore store() {
        return new QualificationRecordStore(directory(), lock(base), QualificationFixtures.locks());
    }

    private Path directory() {
        return base.resolve("projects/pm-mcp-core/roles/qualification");
    }

    private static LockKey lock(Path base) {
        return LockKey.workspace(base, "pm-mcp-core");
    }
}
