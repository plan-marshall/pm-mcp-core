/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * The weights identity of a model (PM-WORK-6, Client Profiles § Weights Identity): read from a pointer of the models
 * listing or from a probe run without a shell, and left unread where neither names an identity.
 */
@DisplayName("Weights reader")
class WeightsReaderTest {

    private static EndpointProfile profile(EndpointProfile.WeightsProbe probe) {
        var base = EndpointFixtures.profile();
        return new EndpointProfile(base.clientId(), base.executable(), base.identity(), base.updateEvidence(),
                base.endpoint(), base.modelTiers(), probe);
    }

    private static JsonNode listing() {
        return JsonDocument.parse("[{\"id\":\"mid\",\"digest\":\"abc123\"},{\"id\":\"big\"}]"
                .getBytes(StandardCharsets.UTF_8), "models");
    }

    @Test
    @DisplayName("the identity is the pointer of the model's entry in the listing")
    void fromListing() {
        var listing = JsonDocument.parse("[{\"id\":\"mid\",\"digest\":\"abc123\"}]"
                .getBytes(StandardCharsets.UTF_8), "models");

        var reading = new WeightsReader().read(profile(new EndpointProfile.WeightsProbe("models_listing", "/digest",
                null, null)), "mid", listing);

        assertAll(
                () -> assertTrue(reading.isRead()),
                () -> assertEquals("mid", reading.model()),
                () -> assertEquals("abc123", reading.weightsId()));
    }

    @Test
    @DisplayName("a model the listing does not name, and an entry without the identity, leave the reading unread")
    void unreadListingEntries() {
        var reader = new WeightsReader();
        var probe = new EndpointProfile.WeightsProbe("models_listing", "/digest", null, null);

        assertAll(
                () -> assertFalse(reader.read(profile(probe), "absent", listing()).isRead()),
                () -> assertFalse(reader.read(profile(probe), "big", listing()).isRead()),
                () -> assertFalse(reader.read(profile(probe), "mid", null).isRead()));
    }

    @Test
    @DisplayName("a pointer that does not resolve leaves the reading unread")
    void unresolvedPointer() {
        var reading = new WeightsReader().read(profile(new EndpointProfile.WeightsProbe("models_listing",
                "/absent", null, null)), "mid", listing());

        assertFalse(reading.isRead());
    }

    @Test
    @DisplayName("a profile without a weights probe reads nothing")
    void noProbe() {
        var reading = new WeightsReader().read(profile(null), "mid", listing());

        assertAll(
                () -> assertFalse(reading.isRead()),
                () -> assertTrue(reading.detail().contains("no weights probe")));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("an argv probe reports the identity in the first group of its pattern")
    void fromArgvProbe() {
        var probe = new EndpointProfile.WeightsProbe("argv", null, List.of("/bin/echo", "weights:abc123"),
                "weights:([a-z0-9]+)");

        var reading = new WeightsReader().read(profile(probe), "mid", null);

        assertAll(
                () -> assertTrue(reading.isRead(), reading.detail()),
                () -> assertEquals("abc123", reading.weightsId()));
    }

    @Test
    @EnabledOnOs(OS.LINUX)
    @DisplayName("a probe that fails or reports nothing leaves the reading unread")
    void failingProbe() {
        var reader = new WeightsReader();

        assertAll(
                () -> assertFalse(reader.read(profile(new EndpointProfile.WeightsProbe("argv", null,
                        List.of("/bin/false"), "([a-z]+)")), "mid", null).isRead()),
                () -> assertFalse(reader.read(profile(new EndpointProfile.WeightsProbe("argv", null,
                        List.of("/bin/echo", "nothing here"), "weights:([a-z0-9]+)")), "mid", null).isRead()));
    }

    @Test
    @DisplayName("the pattern's first group is the identity, and a pattern that captures nothing takes the match")
    void patternGroups() {
        assertAll(
                () -> assertEquals("abc", WeightsReader.match("weights: abc", "weights: ([a-z]+)").orElseThrow()),
                () -> assertEquals("abc", WeightsReader.match("abc def", "[a-z]+").orElseThrow()),
                () -> assertTrue(WeightsReader.match("nothing", "weights: ([a-z]+)").isEmpty()),
                () -> assertTrue(WeightsReader.match("abc", null).isEmpty()));
    }
}
