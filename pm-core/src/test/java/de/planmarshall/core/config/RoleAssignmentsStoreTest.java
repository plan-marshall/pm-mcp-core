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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
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
@DisplayName("Role assignment store")
class RoleAssignmentsStoreTest {

    private static final String DOCUMENT = """
            {"format_version":1,"revision":2,"roles":{
              "implement":{"harness":"acme-cli","model":"acme-standard","effort":"high"},
              "screener":{"harness":"acme-cli","model":"acme-fast","effort":null,
                          "escalation":{"harness":"acme-cli","model":"acme-standard","effort":"medium"}},
              "outline":{"harness":"acme-cli","model":"acme-frontier"}}}""";

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
                () -> assertEquals(Optional.of(new EffortValue("high")), roles.get("implement").effortValue()),
                () -> assertEquals(EffortSetting.DEFAULT, roles.get("screener").effort()),
                () -> assertEquals(EffortSetting.INHERIT, roles.get("outline").effort()),
                () -> assertEquals("acme-standard",
                        roles.get("screener").escalation().orElseThrow().model()),
                () -> assertEquals(Optional.of(new EffortValue("medium")),
                        roles.get("screener").escalation().orElseThrow().effort()),
                () -> assertEquals(3, store.read().assignments().roles().size()),
                () -> assertEquals(Optional.of(new EffortValue("high")),
                        store.read().assignments().forRole("implement").orElseThrow().effort()));
    }

    @Test
    @DisplayName("writes every effort state, the escalation and the incremented revision")
    void write() {
        var roles = Map.of(
                "implement", RoleAssignmentValue.of("acme-cli", "acme-standard", "high"),
                "screener", new RoleAssignmentValue("acme-cli", "acme-fast", EffortSetting.DEFAULT, Optional.empty(),
                        Optional.of(new Escalation("acme-cli", "acme-standard",
                                Optional.of(new EffortValue("medium"))))),
                "outline", RoleAssignmentValue.inheritEffort("acme-cli", "acme-frontier"));

        store.write(roles, 0L);

        var read = store.read();
        assertAll(
                () -> assertEquals(1L, read.revision()),
                () -> assertEquals(roles, read.roles()),
                () -> assertEquals(Optional.empty(),
                        read.assignments().forRole("outline").orElseThrow().effort()));
    }

    @Test
    @DisplayName("a change against an older revision is refused with the assignments the document holds")
    void conflict() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);

        var conflict = assertThrows(SettingsConflict.class,
                () -> store.write(Map.of("implement", RoleAssignmentValue.of("acme-cli", "acme-fast", "low")), 1L));

        assertAll(
                () -> assertEquals(1L, conflict.expectedRevision()),
                () -> assertEquals(2L, conflict.currentRevision()),
                () -> assertInstanceOf(RoleAssignmentsStore.Snapshot.class, conflict.currentValue()),
                () -> assertEquals(DOCUMENT, Files.readString(file, UTF_8)));
    }

    @Test
    @DisplayName("the project's store is guarded by the project's local-configuration lock")
    void projectStore() throws Exception {
        Files.createDirectories(base.resolve("projects").resolve("pm-mcp-core"));
        var project = new RoleAssignmentsStore(RoleAssignmentsStore.project(base, "pm-mcp-core"), new TestLocks());

        project.write(Map.of("implement", RoleAssignmentValue.of("acme-cli", "acme-standard", null)), 0L);

        assertAll(
                () -> assertEquals(1, project.read().roles().size()),
                () -> assertEquals(EffortSetting.DEFAULT, project.read().roles().get("implement").effort()),
                () -> assertTrue(Files.exists(base.resolve("projects").resolve("pm-mcp-core")
                        .resolve(RoleAssignmentsStore.FILE_NAME))));
    }

    @Test
    @DisplayName("an effort value that does not belong to its setting, or a role outside its grammar, is refused")
    void refusedValues() throws Exception {
        var fileWithBadRole = base.resolve("other.json");
        Files.writeString(fileWithBadRole, "{\"format_version\":1,\"revision\":1,\"roles\":{\"Upper\":{}}}", UTF_8);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new RoleAssignmentValue("acme-cli",
                        "acme-standard", EffortSetting.VALUE, Optional.empty(), Optional.empty())),
                () -> assertThrows(IllegalArgumentException.class, () -> new RoleAssignmentValue("acme-cli",
                        "acme-standard", EffortSetting.INHERIT, Optional.of(new EffortValue("high")),
                        Optional.empty())),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> store.write(Map.of("Upper", RoleAssignmentValue.of("acme-cli", "acme-standard", "high")),
                                0L)),
                () -> assertFalse(Files.exists(file)),
                () -> assertThrows(StoreRefusal.class, () -> RoleAssignmentsCodec.read(fileWithBadRole,
                        Files.readAllBytes(fileWithBadRole))));
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

        assertThrows(IllegalArgumentException.class, () -> new RoleAssignmentsStore(wrong, new TestLocks()));
    }
}
