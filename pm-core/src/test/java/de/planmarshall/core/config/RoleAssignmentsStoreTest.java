/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The role assignment store (Store Schemas § Role Assignments, PM-WORK-1): per role its harness, model, effort and optional
 * escalation, with the effort's three states, and the revision rule of every write.
 */
@EnableGeneratorController
@DisplayName("Role assignment store")
class RoleAssignmentsStoreTest {

    private static final String HARNESS = "p" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String STANDARD_MODEL = "m" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FAST_MODEL = "f" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FRONTIER_MODEL = "r" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String PROJECT = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

    private static final String DOCUMENT = """
            {"format_version":1,"revision":2,"roles":{
              "implement":{"harness":"%s","model":"%s","effort":"high"},
              "screener":{"harness":"%s","model":"%s","effort":null,
                          "escalation":{"harness":"%s","model":"%s","effort":"medium"}},
              "outline":{"harness":"%s","model":"%s"}}}"""
            .formatted(HARNESS, STANDARD_MODEL, HARNESS, FAST_MODEL, HARNESS, STANDARD_MODEL,
                    HARNESS, FRONTIER_MODEL);

    @TempDir
    Path base;

    private Path file;
    private RoleAssignmentsStore store;

    @BeforeEach
    void createStore() {
        store = new RoleAssignmentsStore(RoleAssignmentsStore.machine(base), new TestLocks());
        file = base.resolve(RoleAssignmentsStore.FILE_NAME);
    }

    @Test
    @DisplayName("reads the three effort states and the escalation")
    void read() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);

        var roles = store.read().roles();
        assertAll(
                () -> assertEquals(2L, store.read().revision()),
                () -> assertEquals(EffortSetting.VALUE, roles.get("implement").effort()),
                () -> assertEquals(new EffortValue("high"), roles.get("implement").effortValue()),
                () -> assertEquals(EffortSetting.DEFAULT, roles.get("screener").effort()),
                () -> assertEquals(EffortSetting.INHERIT, roles.get("outline").effort()),
                () -> assertEquals(STANDARD_MODEL, roles.get("screener").escalation().model()),
                () -> assertEquals(new EffortValue("medium"), roles.get("screener").escalation().effort()),
                () -> assertEquals(3, store.read().assignments().roles().size()),
                () -> assertEquals(new EffortValue("high"),
                        store.read().assignments().forRole("implement").orElseThrow().effort()));
    }

    @Test
    @DisplayName("writes every effort state, the escalation and the incremented revision")
    void write() {
        var roles = Map.of(
                "implement", RoleAssignmentValue.of(HARNESS, STANDARD_MODEL, "high"),
                "screener", new RoleAssignmentValue(HARNESS, FAST_MODEL, EffortSetting.DEFAULT, null,
                        new Escalation(HARNESS, STANDARD_MODEL, new EffortValue("medium"))),
                "outline", RoleAssignmentValue.inheritEffort(HARNESS, FRONTIER_MODEL));

        store.write(roles, 0L);

        var read = store.read();
        assertAll(
                () -> assertEquals(1L, read.revision()),
                () -> assertEquals(roles, read.roles()),
                () -> assertNull(read.assignments().forRole("outline").orElseThrow().effort()));
    }

    @Test
    @DisplayName("a change against an older revision is refused with the assignments the document holds")
    void conflict() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);
        var stale = Map.of("implement", RoleAssignmentValue.of(HARNESS, FAST_MODEL, "low"));

        var conflict = assertThrows(SettingsConflict.class, () -> store.write(stale, 1L));

        assertAll(
                () -> assertEquals(1L, conflict.expectedRevision()),
                () -> assertEquals(2L, conflict.currentRevision()),
                () -> assertInstanceOf(RoleAssignmentsStore.Snapshot.class, conflict.currentValue()),
                () -> assertEquals(DOCUMENT, Files.readString(file, UTF_8)));
    }

    @Test
    @DisplayName("the project's store is guarded by the project's local-configuration lock")
    void projectStore() throws Exception {
        Files.createDirectories(base.resolve("projects").resolve(PROJECT));
        var project = new RoleAssignmentsStore(RoleAssignmentsStore.project(base, PROJECT), new TestLocks());

        project.write(Map.of("implement", RoleAssignmentValue.of(HARNESS, STANDARD_MODEL, null)), 0L);

        assertAll(
                () -> assertEquals(1, project.read().roles().size()),
                () -> assertEquals(EffortSetting.DEFAULT, project.read().roles().get("implement").effort()),
                () -> assertTrue(Files.exists(base.resolve("projects").resolve(PROJECT)
                        .resolve(RoleAssignmentsStore.FILE_NAME))));
    }

    @Test
    @DisplayName("an effort value that does not belong to its setting, or a role outside its grammar, is refused")
    void refusedValues() throws Exception {
        var fileWithBadRole = base.resolve("other.json");
        Files.writeString(fileWithBadRole, "{\"format_version\":1,\"revision\":1,\"roles\":{\"Upper\":{}}}", UTF_8);
        var highEffort = new EffortValue("high");
        EffortValue noEffort = null;
        Escalation noEscalation = null;
        var missingEffort = assertThrows(IllegalArgumentException.class,
                () -> new RoleAssignmentValue(HARNESS, STANDARD_MODEL, EffortSetting.VALUE, noEffort,
                        noEscalation));
        var effortWithoutSetting = assertThrows(IllegalArgumentException.class,
                () -> new RoleAssignmentValue(HARNESS, STANDARD_MODEL, EffortSetting.INHERIT, highEffort,
                        noEscalation));
        var valueWithoutEffort = assertThrows(IllegalArgumentException.class,
                () -> new RoleAssignmentValue(HARNESS, STANDARD_MODEL, EffortSetting.VALUE, new EffortValue(null),
                        noEscalation));
        var upper = Map.of("Upper", RoleAssignmentValue.of(HARNESS, STANDARD_MODEL, "high"));
        var badRole = assertThrows(IllegalArgumentException.class, () -> store.write(upper, 0L));
        var roleBytes = Files.readAllBytes(fileWithBadRole);
        var readRefused = assertThrows(StoreRefusal.class,
                () -> RoleAssignmentsCodec.read(fileWithBadRole, roleBytes));
        var missingMessage = missingEffort.getMessage();
        var settingMessage = effortWithoutSetting.getMessage();
        var valueMessage = valueWithoutEffort.getMessage();
        var badRoleMessage = badRole.getMessage();

        assertAll(
                () -> assertTrue(missingMessage.contains("effort")),
                () -> assertTrue(settingMessage.contains("effort")),
                () -> assertTrue(valueMessage.contains("must not be null")),
                () -> assertTrue(badRoleMessage.contains("Upper")),
                () -> assertFalse(Files.exists(file)),
                () -> assertEquals(fileWithBadRole, readRefused.file()));
    }

    @Test
    @DisplayName("an absent document reads no assignment at revision 0")
    void absent() {
        assertAll(
                () -> assertTrue(store.read().roles().isEmpty()),
                () -> assertEquals(0L, store.read().revision()));
    }

    @Test
    @DisplayName("the store is guarded by the assignment lock of the machine or a project's local configuration")
    void lockKey() {
        var wrong = new AtomicStoreFile(file, LockKey.harnesses(base));
        var wrongLocks = new TestLocks();

        var refused = assertThrows(IllegalArgumentException.class, () -> new RoleAssignmentsStore(wrong, wrongLocks));

        assertTrue(refused.getMessage().contains("role assignments"));
    }
}
