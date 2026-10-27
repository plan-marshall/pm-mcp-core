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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.ProfileStages.StartContext;

/**
 * The verification record keeps the {@code start_context} of the {@code usage} stage (PM-CLIENT-5, Profile Verification):
 * the first-turn tokens per class and model, an integer or {@code unmeasured}, in the closed shape of the record.
 */
@DisplayName("Verification record start context")
class VerificationRecordTest {

    private static final String RECORD = """
            {
              "format_version": 1,
              "client_id": "acme-cli",
              "profile_schema_version": 1,
              "profile_digest": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
              "verified_at": "2026-10-25T12:00:00Z",
              "tool": { "version": "0.1.99", "commit": "1a2b3c4" },
              "harness": { "executable": "claude", "version": "1.5.0",
                           "executable_sha256": "sha256:0000000000000000000000000000000000000000000000000000000000000000",
                           "models": ["a-model"] },
              "platform": { "os": "linux", "arch": "x86_64", "confinement": "landlock" },
              "stages": {
                "usage": {
                  "status": "passed",
                  "results": { "input_tokens": "reported", "cache_creation": "not_applicable" },
                  "start_context": [
                    { "class": "writer", "model": "a-model", "tokens": 27104 },
                    { "class": "zero_tool", "model": "a-model", "tokens": "unmeasured" }
                  ]
                }
              },
              "values": []
            }
            """;

    @Test
    @DisplayName("the start context of the usage stage is read per class and model")
    void startContextRead() {
        var parsed = VerificationRecord.read(RECORD.getBytes(UTF_8), "record");

        assertAll(() -> assertEquals(List.of(new StartContext("writer", "a-model", OptionalLong.of(27104)),
                                new StartContext("zero_tool", "a-model", OptionalLong.empty())),
                        parsed.stages().get("usage").startContext()),
                () -> assertEquals("not_applicable",
                        parsed.stages().get("usage").results().get("cache_creation")));
    }

    @Test
    @DisplayName("a start context entry of an unknown class is refused")
    void unknownClassRefused() {
        var bytes = RECORD.replace("\"writer\"", "\"planner\"").getBytes(UTF_8);

        var refusal = assertThrows(ProfileRefusal.class, () -> VerificationRecord.read(bytes, "record"));

        assertEquals("/stages/usage/start_context/0", refusal.pointer());
    }

    @Test
    @DisplayName("a start context model absent from the harness models is refused")
    void unlistedModelRefused() {
        var bytes = RECORD.replace("\"model\": \"a-model\"", "\"model\": \"b-model\"").getBytes(UTF_8);

        var refusal = assertThrows(ProfileRefusal.class, () -> VerificationRecord.read(bytes, "record"));

        assertEquals("/stages/usage/start_context/0/model", refusal.pointer());
    }

    @Test
    @DisplayName("a start context entry whose tokens are neither an integer nor unmeasured is refused")
    void unknownTokensRefused() {
        var bytes = RECORD.replace("\"tokens\": 27104", "\"tokens\": \"measured\"").getBytes(UTF_8);

        var refusal = assertThrows(ProfileRefusal.class, () -> VerificationRecord.read(bytes, "record"));

        assertEquals("/stages/usage/start_context/0/tokens", refusal.pointer());
    }
}
