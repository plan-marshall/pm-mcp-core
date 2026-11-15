/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfileFixtures;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;

/**
 * The reading of {@code headless.long_context_thresholds} (PM-CLIENT-5, Harness Profiles § Model Tiers &amp; Models of a
 * Harness): the entry of a model, no entry for any other model, the empty value of a profile that declares none and of
 * an endpoint profile, and the byte-exact match of the model id.
 */
// Every datum in this class is a contract: the member names, the model ids and the thresholds of the schema.
@DisplayName("Long-context thresholds")
class LongContextThresholdsTest {

    private static final String DOCUMENT = "profiles/acme-cli.json";

    private static final String PROFILE = """
            {
              "client_id": "acme-cli",
              "headless": {
                "long_context_thresholds": {"acme-frontier": 200000, "acme-standard": 128000}
              }
            }
            """;

    @Test
    @DisplayName("the model with an entry answers its threshold")
    void entryOfTheModel() {
        var thresholds = thresholds(PROFILE);

        assertAll(
                () -> assertEquals(OptionalInt.of(200_000), thresholds.forModel("acme-frontier"),
                        "The model's own threshold is answered"),
                () -> assertEquals(Map.of("acme-frontier", 200_000, "acme-standard", 128_000), thresholds.byModel(),
                        "Both declared entries are read"),
                () -> assertEquals(OptionalInt.of(200_000),
                        LongContextThresholds.of(loadedProfile(PROFILE)).forModel("acme-frontier"),
                        "The loaded profile's threshold is read"));
    }

    @Test
    @DisplayName("a model without an entry answers nothing, also by prefix and in another case")
    void noEntryForTheOtherModels() {
        var thresholds = thresholds(PROFILE);

        assertAll(
                () -> assertTrue(thresholds.forModel("acme-unlisted").isEmpty(), "Another model answers no threshold"),
                () -> assertTrue(thresholds.forModel("acme").isEmpty(), "A prefix of the id answers nothing"),
                () -> assertTrue(thresholds.forModel("ACME-FRONTIER").isEmpty(), "The case-folded id answers nothing"),
                () -> assertTrue(thresholds.forModel("acme-frontier-x").isEmpty(), "A longer id answers nothing"));
    }

    @Test
    @DisplayName("a profile without the member and one without a headless block have no thresholds")
    void absentMember() {
        var withoutMember = thresholds("""
                {"client_id": "acme-cli", "headless": {"executable": "acme"}}
                """);
        var withoutHeadless = thresholds("""
                {"client_id": "acme-cli", "headless": null}
                """);

        assertAll(
                () -> assertTrue(withoutMember.byModel().isEmpty(), "A profile without the member has no entry"),
                () -> assertTrue(withoutMember.forModel("acme-frontier").isEmpty(),
                        "An absent member answers no threshold"),
                () -> assertTrue(withoutHeadless.byModel().isEmpty(), "A null headless block names no threshold"));
    }

    @Test
    @DisplayName("an endpoint profile declares no threshold")
    void endpointProfile() {
        var thresholds = LongContextThresholds.of(EndpointProfileFixtures.loaded());

        assertAll(
                () -> assertTrue(thresholds.byModel().isEmpty(), "An endpoint profile declares no threshold"),
                () -> assertTrue(thresholds.forModel("big").isEmpty(), "An endpoint model answers no threshold"));
    }

    /** Reads the thresholds of a profile document. */
    private static LongContextThresholds thresholds(String document) {
        return LongContextThresholds.of(JsonDocument.parse(document.getBytes(UTF_8), DOCUMENT));
    }

    /** @return the loaded profile over the document's bytes */
    private static LoadedProfile loadedProfile(String document) {
        var bytes = document.getBytes(UTF_8);
        return new LoadedProfile("acme-cli", ProfileSetKind.PROJECT, JsonDocument.parse(bytes, DOCUMENT), bytes,
                LoadedProfile.State.VERIFIED, null);
    }
}
