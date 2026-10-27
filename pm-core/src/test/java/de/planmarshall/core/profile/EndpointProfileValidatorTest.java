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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The endpoint profile schema and its validation (PM-CLIENT-2, Client Profiles § Endpoint Profile Schema, § Loopback
 * Only Here): the document of the profile sets is told apart by {@code profile_kind}, carries none of the client
 * profile's blocks, and every combination of {@code locality} the schema cannot express is refused with the member
 * named.
 */
@DisplayName("Endpoint profile validation")
class EndpointProfileValidatorTest {

    @Test
    @DisplayName("a loopback endpoint profile validates and projects its values")
    void validProfileProjected() {
        var profile = EndpointProfile.of(EndpointProfileFixtures.valid());

        assertAll(
                () -> assertEquals("local-runner", profile.clientId()),
                () -> assertEquals("ollama", profile.executable()),
                () -> assertEquals(List.of("--version"), profile.identity().versionProbe()),
                () -> assertEquals("version ([0-9.]+)", profile.identity().versionPattern()),
                () -> assertFalse(profile.updateEvidence().verifiable()),
                () -> assertEquals("openai_chat", profile.endpoint().protocol()),
                () -> assertEquals("loopback", profile.endpoint().locality()),
                () -> assertEquals("http://127.0.0.1:11434", profile.endpoint().baseUrl()),
                () -> assertEquals("/api/tags", profile.endpoint().readinessOrModelsPath()),
                () -> assertEquals(7168, profile.endpoint().contextBudgetTokens()),
                () -> assertTrue(profile.endpoint().sendsResultSchema()),
                () -> assertEquals("models_listing", profile.weightsProbe().source()),
                () -> assertEquals("/digest", profile.weightsProbe().pointer()),
                () -> assertEquals(Map.of("frontier", "big", "standard", "mid", "fast", "small"), profile.modelTiers()));
    }

    @Test
    @DisplayName("a document that is not an endpoint profile is not projected as one")
    void notAnEndpointProfile() {
        var bytes = ProfileFixtures.validBytes();
        var client = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());

        assertAll(
                () -> assertFalse(ProfileValidator.isEndpoint(client)),
                () -> assertThrows(IllegalArgumentException.class, () -> EndpointProfile.of(client)));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "the profile_kind is undeclared | \"profile_kind\": \"endpoint\" | \"profile_kind\": \"runner\" | /profile_kind",
            "a client block is present | \"executable\": \"ollama\", | \"tools\": {}, \"executable\": \"ollama\", "
                    + "| /tools",
            "a headless block is present | \"executable\": \"ollama\", | \"headless\": null, \"executable\": "
                    + "\"ollama\", | /headless",
            "the answer reserve does not stay below the context | \"answer_reserve_tokens\": 1024 | "
                    + "\"answer_reserve_tokens\": 8192 | /endpoint/answer_reserve_tokens",
            "the weights probe fills the other source | \"argv\": null, \"pattern\": null} | \"argv\": [\"--x\"], "
                    + "\"pattern\": null} | /weights_probe/argv",
            "a loopback endpoint names an https URL | http://127.0.0.1:11434 | https://127.0.0.1:11434 "
                    + "| /endpoint/base_url",
            "a loopback endpoint names no loopback host | http://127.0.0.1:11434 | http://10.0.0.2:11434 "
                    + "| /endpoint/base_url",
            "a loopback endpoint names a credential key | \"credential_key\": null | \"credential_key\": "
                    + "\"remote-token\" | /endpoint/credential_key",
            "a loopback endpoint names no executable | \"executable\": \"ollama\" | \"executable\": null | /executable",
            "a loopback endpoint names no identity | \"identity\": {\"version_probe\": [\"--version\"], "
                    + "\"version_pattern\": \"version ([0-9.]+)\"} | \"identity\": null | /identity",
            "the remote form is refused | \"locality\": \"loopback\" | \"locality\": \"remote\" | /endpoint/locality",
            "the protocol system_one is refused | \"protocol\": \"openai_chat\" | \"protocol\": \"system_one\" "
                    + "| /endpoint/protocol",
            "the models pointer is not a pointer | \"models_pointer\": \"/models\" | \"models_pointer\": \"models\" "
                    + "| /endpoint/models_pointer",
            "an endpoint path is not a path | \"readiness_path\": \"/api/tags\" | \"readiness_path\": \"api/tags\" "
                    + "| /endpoint/readiness_path"
    })
    @DisplayName("a rule the schema cannot express refuses the profile with the member named")
    void refusedWithMemberNamed(String name, String from, String to, String pointer) {
        var document = EndpointProfileFixtures.variant(from, to);

        var refusal = assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(document));

        assertAll(
                () -> assertEquals(pointer, refusal.pointer()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()));
    }

    @Test
    @DisplayName("an endpoint profile ships no profile: every shipped document is a client profile")
    void noShippedEndpointProfile() {
        var shipped = ShippedProfiles.read().shipped();

        assertAll(
                () -> assertFalse(shipped.isEmpty()),
                () -> assertTrue(shipped.values().stream().noneMatch(profile -> ProfileValidator.isEndpoint(
                        ProfileValidator.validate(profile.document(), "shipped", ProfileRules.none())))));
    }

    @Test
    @DisplayName("a remote-form document is structurally admissible before the locality rule refuses it")
    void remoteFormRefusedWithTheLocalityMember() {
        var document = EndpointProfileFixtures.variant("\"locality\": \"loopback\"", "\"locality\": \"remote\"")
                .replace("\"credential_key\": null", "\"credential_key\": \"remote-token\"")
                .replace("http://127.0.0.1:11434", "https://runner.example.test");

        var refusal = assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(document));

        assertEquals("/endpoint/locality", refusal.pointer());
    }

    @Test
    @DisplayName("the endpoint profile declares no nullable readiness path as a readiness check of its own kind")
    void readinessPathMayBeNull() {
        var document = EndpointProfileFixtures.variant("\"readiness_path\": \"/api/tags\"",
                "\"readiness_path\": null");

        var profile = EndpointProfile.of(EndpointProfileFixtures.validate(document));

        assertAll(
                () -> assertNull(profile.endpoint().readinessPath()),
                () -> assertEquals("/api/tags", profile.endpoint().readinessOrModelsPath()));
    }
}
