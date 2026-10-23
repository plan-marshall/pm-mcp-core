/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.AuditAnchor;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.JsonlStoreFile;

/**
 * The {@code agent_started} detection is written into the machine's audit log as one {@code security} record naming
 * the executable (Model Work § A Worker Off Track).
 */
@DisplayName("Agent-started security audit")
class AuditLogSecurityAuditTest {

    @Test
    @DisplayName("the detection is a security record naming the executable, the generation and the task")
    void recordsTheDetection(@TempDir Path base) throws Exception {
        Files.createDirectories(base.resolve("logs"));
        var clock = Clock.fixed(Instant.parse("2026-10-23T10:00:00Z"), ZoneOffset.UTC);
        var auditLog = new AuditLog(base, AuditAnchor.NONE, clock, "tester");
        var audit = new AuditLogSecurityAudit(auditLog, new TestLocks(), clock, base);
        var event = new SecurityAudit.Event("/usr/local/bin/opencode", TaskFixtures.GENERATION,
                "qt-0000000000000001", 2, Instant.parse("2026-10-23T10:00:00Z"));

        audit.agentStarted(event);

        JsonlStoreFile.Content content = auditLog.snapshot();
        var line = new String(content.lines().getFirst().content(), StandardCharsets.UTF_8);
        assertAll(
                () -> assertEquals(1, content.lines().size()),
                () -> assertTrue(line.contains("\"category\":\"security\""), line),
                () -> assertTrue(line.contains("\"event\":\"agent_started\""), line),
                () -> assertTrue(line.contains("\"actor\":\"supervisor\""), line),
                () -> assertTrue(line.contains("\"executable\":\"/usr/local/bin/opencode\""), line),
                () -> assertTrue(line.contains("\"generation_id\":\"" + TaskFixtures.GENERATION + "\""), line),
                () -> assertTrue(line.contains("\"attempt\":2"), line));
    }
}
