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

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "the remote form is not the protocol system_one | \"protocol\": \"system_one\" | \"protocol\": "
                    + "\"openai_chat\" | /endpoint/locality",
            "a remote endpoint names an http origin | https://judge.example.test | http://127.0.0.1:11434 "
                    + "| /endpoint/base_url",
            "a remote endpoint names no credential key | \"credential_key\": \"judge-token\" | \"credential_key\": "
                    + "null | /endpoint/credential_key",
            "a remote endpoint names a one-character credential key | \"credential_key\": \"judge-token\" | "
                    + "\"credential_key\": \"x\" | /endpoint/credential_key",
            "a remote endpoint names an executable | \"executable\": null | \"executable\": \"ollama\" | /executable",
            "a remote endpoint names an identity | \"identity\": null | \"identity\": {\"version_probe\": "
                    + "[\"--version\"], \"version_pattern\": \"version ([0-9.]+)\"} | /identity",
            "a remote endpoint names update evidence | \"update_evidence\": null | \"update_evidence\": "
                    + "{\"method\": \"none\", \"signer\": null} | /update_evidence",
            "a remote endpoint runs an argv weights probe | \"weights_probe\": null | \"weights_probe\": "
                    + "{\"source\": \"argv\", \"pointer\": null, \"argv\": [\"probe\", \"{{model}}\"], "
                    + "\"pattern\": \"(.*)\"} | /weights_probe/source"
    })
    @DisplayName("a rule the remote form states refuses the profile with the member named")
    void remoteRefusedWithMemberNamed(String name, String from, String to, String pointer) {
        var document = EndpointProfileFixtures.remoteVariant(from, to);

        var refusal = assertThrows(ProfileRefusal.class, () -> EndpointProfileFixtures.validate(document));

        assertAll(
                () -> assertEquals(pointer, refusal.pointer()),
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()));
    }

    @Test
    @DisplayName("a remote-form document validates and projects the credential key it reads")
    void remoteFormValidated() {
        var profile = EndpointProfile.of(EndpointProfileFixtures.remote());

        assertAll(
                () -> assertEquals("hosted-judge", profile.clientId()),
                () -> assertNull(profile.executable()),
                () -> assertNull(profile.identity()),
                () -> assertNull(profile.updateEvidence()),
                () -> assertEquals("system_one", profile.endpoint().protocol()),
                () -> assertEquals("remote", profile.endpoint().locality()),
                () -> assertEquals("https://judge.example.test", profile.endpoint().baseUrl()),
                () -> assertEquals("judge-token", profile.endpoint().credentialKey()),
                () -> assertTrue(profile.endpoint().remote()),
                () -> assertTrue(profile.endpoint().judgment()),
                () -> assertEquals(7680, profile.endpoint().contextBudgetTokens()));
    }

    @Test
    @DisplayName("the protocol system_one is admitted on a loopback endpoint too")
    void judgmentOnLoopbackAdmitted() {
        var document = EndpointProfileFixtures.variant("\"protocol\": \"openai_chat\"",
                "\"protocol\": \"system_one\"");

        var profile = EndpointProfile.of(EndpointProfileFixtures.validate(document));

        assertAll(
                () -> assertTrue(profile.endpoint().judgment()),
                () -> assertFalse(profile.endpoint().remote()),
                () -> assertNull(profile.endpoint().credentialKey()));
    }

    @Test
    @DisplayName("the stage list of a judgment endpoint leaves the classes stage out")
    void judgmentStages() {
        assertAll(
                () -> assertEquals(List.of("identity", "launch", "classes", "limits"),
                        ProfileStages.stagesFor("openai_chat")),
                () -> assertEquals(List.of("identity", "launch", "limits"), ProfileStages.stagesFor("system_one")));
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
                () -> assertEquals("models_listing", EndpointProfile.weightsProbeOf(endpoint).source()),
                () -> assertNull(EndpointProfile.weightsProbeOf(client)),
                () -> assertEquals("/digest", EndpointProfile.weightsProbeOf(clientWithProbe).pointer()));
    }
}
