/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code tool} component of a verification record (PM-CLIENT-5): the reader keeps its version and commit for the
 * verifying-binary control, and the closed shape keeps refusing a missing member, an undeclared member and a record
 * without a {@code tool} at all.
 */
@DisplayName("The tool component of a verification record")
class VerificationRecordToolTest {

    private static final String DIGEST = "sha256:" + "0".repeat(64);

    private static VerificationRecord readWithTool(String toolMember) {
        return VerificationRecord.read(document(toolMember), "verification.json");
    }

    private static byte[] document(String toolMember) {
        return """
                {
                  "format_version": 1,
                  "client_id": "acme-cli",
                  "profile_schema_version": 1,
                  "profile_digest": "%1$s",
                  "verified_at": "2026-10-25T12:00:00Z",
                  %2$s
                  "harness": {"executable": "claude", "version": "1.2.3", "executable_sha256": "%1$s",
                              "models": ["a-model"]},
                  "platform": {"os": "linux", "arch": "x86_64", "confinement": "landlock"},
                  "stages": {"identity": {"status": "passed"}},
                  "values": [{"pointer": "/worker/eligible", "status": "measured"}]
                }
                """.formatted(DIGEST, toolMember).getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the reader keeps the tool version and the tool commit")
    void toolVersionAndCommit() {
        var record = readWithTool("\"tool\": {\"version\": \"0.1.99\", \"commit\": \"1a2b3c4\"},");

        assertAll(
                () -> assertEquals("0.1.99", record.tool().version(), "the version is kept as recorded"),
                () -> assertEquals("1a2b3c4", record.tool().commit(), "the commit is kept as recorded"));
    }

    @Test
    @DisplayName("a tool without its version is refused at its pointer")
    void missingVersion() {
        var refusal = assertThrows(ProfileRefusal.class,
                () -> readWithTool("\"tool\": {\"commit\": \"1a2b3c4\"},"));

        assertAll(
                () -> assertEquals("/tool/version", refusal.pointer(), "the refusal names the missing version"),
                () -> assertEquals(ProfileRefusal.Outcome.RECORD_INVALID, refusal.outcome(),
                        "a broken closed shape refuses the record"));
    }

    @Test
    @DisplayName("a tool without its commit is refused at its pointer")
    void missingCommit() {
        var refusal = assertThrows(ProfileRefusal.class,
                () -> readWithTool("\"tool\": {\"version\": \"0.1.99\"},"));

        assertAll(
                () -> assertEquals("/tool/commit", refusal.pointer(), "the refusal names the missing commit"),
                () -> assertEquals(ProfileRefusal.Outcome.RECORD_INVALID, refusal.outcome(),
                        "a broken closed shape refuses the record"));
    }

    @Test
    @DisplayName("a tool carrying an undeclared member is refused at that member")
    void unknownMember() {
        var refusal = assertThrows(ProfileRefusal.class,
                () -> readWithTool("\"tool\": {\"version\": \"0.1.99\", \"commit\": \"1a2b3c4\", \"dirty\": true},"));

        assertAll(
                () -> assertEquals("/tool/dirty", refusal.pointer(), "the refusal names the undeclared member"),
                () -> assertEquals(ProfileRefusal.Outcome.RECORD_INVALID, refusal.outcome(),
                        "an undeclared member refuses the record"));
    }

    @Test
    @DisplayName("a record without a tool is refused at the tool member")
    void missingTool() {
        var refusal = assertThrows(ProfileRefusal.class, () -> readWithTool(""));

        assertAll(
                () -> assertEquals("/tool", refusal.pointer(), "the refusal names the missing tool"),
                () -> assertEquals(ProfileRefusal.Outcome.RECORD_INVALID, refusal.outcome(),
                        "a missing tool refuses the record"));
    }
}
