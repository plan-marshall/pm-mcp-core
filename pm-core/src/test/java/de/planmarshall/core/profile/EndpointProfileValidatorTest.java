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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The endpoint profile schema and its validation (PM-CLIENT-2, Client Profiles § Endpoint Profile Schema, § Loopback
 * Only Here): the document of the profile sets is told apart by {@code profile_kind}, carries none of the client
 * profile's blocks, names a loopback endpoint of the protocol {@code openai_chat} served by a program with its identity,
 * and every rule the schema cannot express is refused with the member named.
 */
// Every datum in this class is a contract: the endpoint wire names, the schema member names and the refusal pointers.
@DisplayName("Endpoint profile validation")
class EndpointProfileValidatorTest {

    @Test
    @DisplayName("a loopback endpoint profile validates and projects its values")
    void validProfileProjected() {
        var profile = EndpointProfile.of(EndpointProfileFixtures.valid());

        assertAll(
                () -> assertEquals("local-runner", profile.clientId(), "The client id is projected"),
                () -> assertEquals("ollama", profile.executable(), "The serving program is projected"),
                () -> assertEquals(List.of("--version"), profile.identity().versionProbe(),
                        "The version probe is projected"),
                () -> assertEquals("version ([0-9.]+)", profile.identity().versionPattern(),
                        "The version pattern is projected"),
                () -> assertFalse(profile.updateEvidence().verifiable(), "Update evidence 'none' is not verifiable"),
                () -> assertEquals("openai_chat", profile.endpoint().protocol(), "The protocol is projected"),
                () -> assertEquals("http://127.0.0.1:11434", profile.endpoint().baseUrl(),
                        "The loopback base URL is projected"),
                () -> assertEquals("/api/tags", profile.endpoint().readinessOrModelsPath(),
                        "The readiness path is projected"),
                () -> assertEquals(7168, profile.endpoint().contextBudgetTokens(),
                        "The budget is context less the reserve"),
                () -> assertTrue(profile.endpoint().sendsResultSchema(), "An enforced result schema is sent"),
                () -> assertEquals("models_listing", profile.weightsProbe().source(),
                        "The weights probe source is projected"),
                () -> assertEquals("/digest", profile.weightsProbe().pointer(),
                        "The weights probe pointer is projected"),
                () -> assertEquals(Map.of("frontier", "big", "standard", "mid", "fast", "small"), profile.modelTiers(),
                        "The model tiers are projected"));
    }

    @Test
    @DisplayName("a document that is not an endpoint profile is not projected as one")
    void notAnEndpointProfile() {
        var bytes = ProfileFixtures.validBytes();
        var client = ProfileValidator.validate(bytes, "profiles/acme-cli.json", ProfileRules.none());

        assertAll(
                () -> assertFalse(ProfileValidator.isEndpoint(client), "A client profile is not an endpoint"),
                () -> assertThrows(IllegalArgumentException.class, () -> EndpointProfile.of(client),
                        "A client profile is refused as an endpoint"));
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
            "a loopback endpoint names a port above the range | http://127.0.0.1:11434 | http://127.0.0.1:99999 "
                    + "| /endpoint/base_url",
            "a loopback endpoint names no executable | \"executable\": \"ollama\" | \"executable\": null | /executable",
            "a loopback endpoint names no identity | \"identity\": {\"version_probe\": [\"--version\"], "
                    + "\"version_pattern\": \"version ([0-9.]+)\"} | \"identity\": null | /identity",
            "the models pointer is not a pointer | \"models_pointer\": \"/models\" | \"models_pointer\": \"models\" "
                    + "| /endpoint/models_pointer",
            "an endpoint path is not a path | \"readiness_path\": \"/api/tags\" | \"readiness_path\": \"api/tags\" "
                    + "| /endpoint/readiness_path"
    })
    @DisplayName("a rule the schema cannot express refuses the profile with the member named")
    void refusedWithMemberNamed(String name, String from, String to, String pointer) {
        var document = EndpointProfileFixtures.variant(from, to);

        var refusal = assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(document),
                "The variant profile is refused");

        assertAll(
                () -> assertEquals(pointer, refusal.pointer(), "The refusal names the offending member"),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                        "The refusal outcome is profile_invalid"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "the protocol system_one | \"protocol\": \"openai_chat\" | \"protocol\": \"system_one\" "
                    + "| /endpoint/protocol",
            "a locality member | \"protocol\": \"openai_chat\", | \"protocol\": \"openai_chat\", \"locality\": "
                    + "\"loopback\", | /endpoint/locality",
            "a credential_key member | \"protocol\": \"openai_chat\", | \"protocol\": \"openai_chat\", "
                    + "\"credential_key\": null, | /endpoint/credential_key"
    })
    @DisplayName("a member or value of the deleted remote and judgment forms refuses the profile")
    void deletedFormRefused(String name, String from, String to, String pointer) {
        var document = EndpointProfileFixtures.variant(from, to);

        var refusal = assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(document),
                "A deleted form's member is refused");

        assertAll(
                () -> assertEquals(pointer, refusal.pointer(), "The refusal names the deleted member"),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(),
                        "The refusal outcome is profile_invalid"));
    }

    @Test
    @DisplayName("an endpoint profile ships no profile: every shipped document is a client profile")
    void noShippedEndpointProfile() {
        var shipped = ShippedProfiles.read().shipped();

        assertAll(
                () -> assertFalse(shipped.isEmpty(), "The shipped profile set is not empty"),
                () -> assertTrue(shipped.values().stream().noneMatch(profile -> ProfileValidator.isEndpoint(
                        ProfileValidator.validate(profile.document(), "shipped", ProfileRules.none()))),
                        "No shipped profile is an endpoint profile"));
    }

    @Test
    @DisplayName("the endpoint profile declares no nullable readiness path as a readiness check of its own kind")
    void readinessPathMayBeNull() {
        var document = EndpointProfileFixtures.variant("\"readiness_path\": \"/api/tags\"",
                "\"readiness_path\": null");

        var profile = EndpointProfile.of(EndpointProfileFixtures.validate(document));

        assertAll(
                () -> assertNull(profile.endpoint().readinessPath(), "A null readiness path is projected as null"),
                () -> assertEquals("/api/tags", profile.endpoint().readinessOrModelsPath(),
                        "The models path stands in for readiness"));
    }

    @Test
    @DisplayName("the weights probe is read from either kind of profile")
    void weightsProbeOfEitherKind() {
        var endpoint = EndpointProfileFixtures.valid();
        var client = ProfileValidator.validate(ProfileFixtures.validBytes(), "profiles/acme-cli.json",
                ProfileRules.none());
        var probe = new LinkedHashMap<String, Object>();
        probe.put("source", "models_listing");
        probe.put("pointer", "/digest");
        probe.put("argv", null);
        probe.put("pattern", null);
        var clientWithProbe = ProfileValidator.validate(
                ProfileFixtures.valid().set("/headless/weights_probe", probe).bytes(), "profiles/acme-cli.json",
                ProfileRules.none());

        assertAll(
                () -> assertEquals("models_listing", EndpointProfile.weightsProbeOf(endpoint).orElseThrow().source(),
                        "The endpoint's weights probe is read"),
                () -> assertTrue(EndpointProfile.weightsProbeOf(client).isEmpty(), "A client without probe is empty"),
                () -> assertEquals("/digest", EndpointProfile.weightsProbeOf(clientWithProbe).orElseThrow().pointer(),
                        "The client's weights probe is read"));
    }
}
