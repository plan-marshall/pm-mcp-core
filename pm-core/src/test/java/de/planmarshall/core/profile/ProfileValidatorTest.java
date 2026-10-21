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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The client profile validator refuses every value that breaks the schema or a hardening rule, as a whole, with the
 * document and the JSON Pointer of the failing value (PM-CLIENT-5).
 */
@DisplayName("Client profile validation")
class ProfileValidatorTest {

    private static final String DOCUMENT = "profiles/acme-cli.json";

    @Test
    @DisplayName("the valid fixture loads")
    void validProfileLoads() {
        var profile = ProfileValidator.validate(ProfileFixtures.validBytes(), DOCUMENT, ProfileRules.none());

        assertAll(
                () -> assertEquals("acme-cli", ProfileValidator.clientId(profile)),
                () -> assertEquals("Acme CLI", profile.member("display_name").flatMap(JsonNode::text).orElseThrow()));
    }

    @Test
    @DisplayName("an icon of the form source is an https URL of an SVG or PNG file")
    void iconSourceAccepted() {
        var icon = new LinkedHashMap<String, Object>();
        icon.put("form", "source");
        icon.put("source", "https://example.test/icons/acme.svg");
        icon.put("bundled", null);
        var fixture = ProfileFixtures.valid().set("/icon", icon);

        ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none());
    }

    @Test
    @DisplayName("code_signature and package_provenance with a signer load")
    void updateEvidenceAccepted() {
        for (var method : List.of("code_signature", "package_provenance")) {
            var fixture = ProfileFixtures.valid()
                    .set("/headless/update_evidence/method", method)
                    .set("/headless/update_evidence/signer", "Acme Corporation");

            ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none());
        }
    }

    @Test
    @DisplayName("a profile whose MCP configuration is exclusive loads every class it declares")
    void exclusiveConfigurationLoadsEveryClass() {
        var fixture = ProfileFixtures.valid();

        var profile = ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none());
        var classes = profile.member("headless").flatMap(headless -> headless.member("classes")).orElseThrow();

        assertTrue(classes.members().values().stream().noneMatch(JsonNode::isNull),
                "Every declared class must be runnable on an exclusive configuration");
    }

    @Nested
    @DisplayName("refuses as a whole")
    class Refusals {

        static Stream<Arguments> refusals() {
            return Stream.of(
                    Arguments.of(ProfileFixtures.valid().set("/headless/bogus", 1),
                            "/headless/bogus", "an undeclared member"),
                    Arguments.of(ProfileFixtures.valid().remove("/headless/executable"),
                            "/headless/executable", "a missing member"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/install_locations/0", "relative/bin"),
                            "/headless/install_locations/0", "install_locations"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/install_hint", "Not A Key"),
                            "/headless/install_hint", "install_hint"),
                    Arguments.of(ProfileFixtures.valid().set("/icon", map("form", "source",
                                    "source", "http://example.test/icons/acme.svg", "bundled", null)),
                            "/icon/source", "icon.source over http"),
                    Arguments.of(ProfileFixtures.valid().set("/icon", map("form", "source",
                                    "source", "https://example.test/icons/acme.gif", "bundled", null)),
                            "/icon/source", "icon.source that is not SVG or PNG"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/resumability/resume",
                                    Map.of("argv", List.of("--resume"), "session_id", "/session",
                                            "max_age_seconds", 600)),
                            "/headless/resumability/resume/argv", "resume.argv without {{session_id}}"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/resumability/resume",
                                    Map.of("argv", List.of("--resume", "{{session_id}}", "{{session_id}}"),
                                            "session_id", "/session", "max_age_seconds", 600)),
                            "/headless/resumability/resume/argv", "resume.argv with {{session_id}} twice"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/update_evidence/method", "code_signature"),
                            "/headless/update_evidence/signer", "code_signature without a signer"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/update_evidence/method", "package_provenance"),
                            "/headless/update_evidence/signer", "package_provenance without a signer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/update_evidence/signer", "Acme"),
                            "/headless/update_evidence/signer", "none with a signer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/mcp_config/exclusive", false),
                            "/headless/classes", "a class on a non-exclusive configuration"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/supported_versions", "the newest"),
                            "/headless/supported_versions", "supported_versions"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/supported_versions", ">2.0.0 <1.0.0"),
                            "/headless/supported_versions", "an empty supported_versions range"),
                    Arguments.of(ProfileFixtures.valid().set("/client_id", "Acme"),
                            "/client_id", "client_id"),
                    Arguments.of(ProfileFixtures.valid().set("/display_name", "Acme; drop table"),
                            "/display_name", "a sentence in a text a model reads"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/argv", List.of("https://example.test/acme")),
                            "/headless/argv/0", "a URL in argv"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/argv", List.of("/usr/bin/acme")),
                            "/headless/argv/0", "an absolute path in argv"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/env", Map.of("LD_PRELOAD", "libx.so")),
                            "/headless/env/LD_PRELOAD", "LD_PRELOAD in env"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/env", Map.of("ACME_API_TOKEN", "x")),
                            "/headless/env/ACME_API_TOKEN", "a credential-pattern variable in env"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/env", Map.of("ACME_URL", "https://x.test")),
                            "/headless/env/ACME_URL", "a URL in an env value"),
                    Arguments.of(ProfileFixtures.valid().set("/tools/read_file", "read file please"),
                            "/tools/read_file", "a sentence-like tool name"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme/../etc"),
                            "/headless/writable_paths/0", "a writable path with '..'"),
                    Arguments.of(ProfileFixtures.valid().set("/identity/version_pattern", "(?=acme)(acme)"),
                            "/identity/version_pattern", "a look-around in version_pattern"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/tool_result_limit_chars", 8000),
                            "/headless/tool_result_limit_chars", "a tool-result limit without resource tools"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/session_residue", "ephemeral")
                                    .set("/headless/resumability/resume", Map.of("argv", List.of(
                                                    "--resume", "{{session_id}}"), "session_id", "/session",
                                            "max_age_seconds", 600)),
                            "/headless/resumability/resume", "a resume on ephemeral session residue"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/capabilities", List.of("extension_isolation")),
                            "/headless/classes/zero_tool",
                            "zero_tool without project_settings_control"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/capabilities", List.of("project_settings_control")),
                            "/headless/classes", "writer without extension_isolation"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/classes/writer/tool_set", "advisory")
                                    .set("/headless/classes/read_only/tool_set", "advisory"),
                            "/worker/restricted_models", "restricted_models without an enforced class"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/worker/restricted_models", List.of("acme-frontier")),
                            "/worker/restricted_models", "a tier model outside restricted_models"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/worker/ack_informational", true)
                                    .set("/worker/ack_deadline_seconds", 999),
                            "/worker/ack_deadline_seconds", "an informational acknowledgement deadline"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/connection/progress_resets_timeout", false)
                                    .set("/connection/bounded_wait_seconds", 240)
                                    .set("/connection/idle_timeout_seconds", 240),
                            "/connection/bounded_wait_seconds", "a bounded wait not below the idle timeout"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/models_probe",
                                    map("argv", List.of("acme", "models"), "format", "json", "pointer", null)),
                            "/headless/models_probe/pointer", "a JSON models probe without a pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/mcp_config/delivery", "file")
                                    .set("/headless/mcp_config/path", "../settings.json"),
                            "/headless/mcp_config/path", "an mcp_config.path with '..'"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/ident_ping", null),
                            "/headless/ident_ping", "an unknown headless member"),
                    Arguments.of(ProfileFixtures.valid().set("/display_name", "Acme\u0301"),
                            "/display_name", "a string that is not NFC"),
                    Arguments.of(ProfileFixtures.valid().set("/display_name", "Acme\u0001"),
                            "/display_name", "a control character in a string"),
                    Arguments.of(ProfileFixtures.valid().set("/display_name", "A".repeat(65)),
                            "/display_name", "a display name longer than 64 characters"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/data_home",
                                    map("variable", "XDG_DATA_HOME", "login_file", ".auth.json")),
                            "/headless/data_home/login_file", "a login file with a leading dot"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "models_listing", "pointer", "/id", "argv", List.of("acme"),
                                            "pattern", null)),
                            "/headless/weights_probe/argv", "a models-listing weights probe with an argv"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "models_listing", "pointer", "/id", "argv", null, "pattern", null,
                                            "bogus", 1)),
                            "/headless/weights_probe/bogus", "an unknown weights-probe member"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/events", map("kind_pointer", "/type",
                                    "kinds", map("text", null, "tool_call", null, "tool_result", null,
                                            "file_changed", null, "turn", map("match", List.of("result"))))),
                            "/headless/events/kinds/turn/stop_reason", "a turn without its stop reason"));
        }

        /** A map that, unlike {@link Map#of}, admits a null value. */
        private static Map<String, Object> map(Object... pairs) {
            var map = new LinkedHashMap<String, Object>();
            for (var index = 0; index < pairs.length; index += 2) {
                map.put((String) pairs[index], pairs[index + 1]);
            }
            return map;
        }

        @ParameterizedTest(name = "{2} -> {1}")
        @MethodSource("refusals")
        @DisplayName("a failing value refuses the profile with its document and pointer")
        void refuses(ProfileFixtures.ProfileFixture fixture, String pointer, String because) {
            var refusal = assertThrows(ProfileRefusal.class,
                    () -> ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none()));

            assertAll(
                    () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(), because),
                    () -> assertEquals(DOCUMENT, refusal.document(), because),
                    () -> assertEquals(pointer, refusal.pointer(), because));
        }
    }

    @Test
    @DisplayName("an empty class args is a schema-valid launch, as is a turn without a stop reason")
    void emptyArgsAndNullStopReasonAccepted() {
        var fixture = ProfileFixtures.valid()
                .set("/headless/classes/writer/args", new java.util.ArrayList<>())
                .set("/headless/events", mapOf("kind_pointer", "/type", "kinds", mapOf("text", null,
                        "tool_call", null, "tool_result", null, "file_changed", null, "turn",
                        mapOf("match", List.of("result"), "stop_reason", null))));

        ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none());
    }

    private static java.util.Map<String, Object> mapOf(Object... pairs) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("a writable path that covers a managed bundle file is refused")
    void managedPathRefused() {
        var rules = new ProfileRules(Set.of("~/.acme/settings.json"), Set.of());
        var fixture = ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme");

        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(fixture.bytes(), DOCUMENT, rules));

        assertEquals("/headless/writable_paths/0", refusal.pointer());
    }

    @Test
    @DisplayName("a harness home is never a writable path as a whole")
    void harnessHomeRefused() {
        var rules = new ProfileRules(Set.of(), Set.of("~/.acme"));
        var fixture = ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme");

        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(fixture.bytes(), DOCUMENT, rules));

        assertEquals("/headless/writable_paths/0", refusal.pointer());
    }

    @Test
    @DisplayName("a member twice is refused by the reader")
    void duplicateMemberRefused() {
        var document = "{\"schema_version\":1,\"schema_version\":1}".getBytes(StandardCharsets.UTF_8);

        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(document, DOCUMENT, ProfileRules.none()));

        assertAll(
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()),
                () -> assertEquals("/schema_version", refusal.pointer()));
    }

    @Test
    @DisplayName("a document larger than 64 KiB is refused before any value is used")
    void oversizedDocumentRefused() {
        var document = new byte[JsonDocument.MAX_BYTES + 1];

        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(document, DOCUMENT, ProfileRules.none()));

        assertAll(
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()),
                () -> assertEquals("", refusal.pointer()));
    }
}
