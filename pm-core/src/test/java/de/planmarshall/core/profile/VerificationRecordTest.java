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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.ProfileStages.StartContext;

/**
 * The verification record keeps the {@code start_context} of the {@code usage} stage (PM-CLIENT-5, Profile Verification):
 * the first-turn tokens per class and model, an integer or {@code unmeasured}, in the closed shape of the record.
 */
@EnableGeneratorController
@DisplayName("Verification record start context")
class VerificationRecordTest {

    private static final String DIGEST = "sha256:" + "%064x".formatted(Generators.longs().next());
    private static final String VERIFIED_AT = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next()).toString();
    private static final String MODEL = Generators.letterStrings(3, 10).next();
    private static final String OTHER_MODEL = MODEL + "-b";
    private static final String UNKNOWN_CLASS = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final int TOKENS = Generators.integers(1, 100_000).next();

    private static final String RECORD = """
            {
              "format_version": 1,
              "client_id": "acme-cli",
              "profile_schema_version": 1,
              "profile_digest": "%1$s",
              "verified_at": "%2$s",
              "tool": { "version": "0.1.99", "commit": "1a2b3c4" },
              "harness": { "executable": "claude", "version": "1.5.0",
                           "executable_sha256": "%1$s",
                           "models": ["%3$s"] },
              "platform": { "os": "linux", "arch": "x86_64", "confinement": "landlock" },
              "stages": {
                "usage": {
                  "status": "passed",
                  "results": { "input_tokens": "reported", "cache_creation": "not_applicable" },
                  "start_context": [
                    { "class": "writer", "model": "%3$s", "tokens": %4$d },
                    { "class": "zero_tool", "model": "%3$s", "tokens": "unmeasured" }
                  ]
                }
              },
              "values": []
            }
            """.formatted(DIGEST, VERIFIED_AT, MODEL, TOKENS);

    @Test
    @DisplayName("the start context of the usage stage is read per class and model")
    void startContextRead() {
        var parsed = VerificationRecord.read(RECORD.getBytes(UTF_8), "record");

        assertAll(() -> assertEquals(List.of(new StartContext("writer", MODEL, (long) TOKENS),
                                new StartContext("zero_tool", MODEL, null)),
                        parsed.stages().get("usage").startContext(), "start context is read per class"),
                () -> assertEquals("not_applicable",
                        parsed.stages().get("usage").results().get("cache_creation"),
                        "cache creation reads not_applicable"));
    }

    @Test
    @DisplayName("a start context entry of an unknown class is refused")
    void unknownClassRefused() {
        var bytes = RECORD.replace("\"writer\"", "\"" + UNKNOWN_CLASS + "\"").getBytes(UTF_8);

        var refusal = assertThrows(ProfileRefusal.class, () -> VerificationRecord.read(bytes, "record"),
                "unknown class is refused");

        assertEquals("/stages/usage/start_context/0", refusal.pointer(), "refusal points at the first entry");
    }

    @Test
    @DisplayName("a start context model absent from the harness models is refused")
    void unlistedModelRefused() {
        var bytes = RECORD.replace("\"model\": \"" + MODEL + "\"", "\"model\": \"" + OTHER_MODEL + "\"")
                .getBytes(UTF_8);

        var refusal = assertThrows(ProfileRefusal.class, () -> VerificationRecord.read(bytes, "record"),
                "unlisted model is refused");

        assertEquals("/stages/usage/start_context/0/model", refusal.pointer(),
                "refusal points at the model");
    }

    @Test
    @DisplayName("a record with the old client_id member is read as before")
    void legacyClientIdIsRead() {
        var parsed = VerificationRecord.read(RECORD.getBytes(UTF_8), "record");

        assertEquals("acme-cli", parsed.harnessId(), "The old member is read as the harness id");
    }

    @Test
    @DisplayName("a record with the new harness_id member is read")
    void harnessIdIsRead() {
        var bytes = RECORD.replace("\"client_id\"", "\"harness_id\"").getBytes(UTF_8);

        var parsed = VerificationRecord.read(bytes, "record");

        assertEquals("acme-cli", parsed.harnessId(), "The new member is read as the harness id");
    }

    @Test
    @DisplayName("a record that carries both id members is refused")
    void bothIdMembersRefused() {
        var bytes = RECORD.replace("\"client_id\": \"acme-cli\",",
                "\"harness_id\": \"acme-cli\", \"client_id\": \"acme-cli\",").getBytes(UTF_8);

        var refusal = assertThrows(ProfileRefusal.class, () -> VerificationRecord.read(bytes, "record"),
                "both id members are refused");

        assertEquals("/harness_id", refusal.pointer(), "the refusal names the canonical member");
    }
}
