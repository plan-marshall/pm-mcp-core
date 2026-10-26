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
import java.util.ArrayList;
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
                    Arguments.of(ProfileFixtures.valid().set("/headless/supported_versions", ">=99999999999.0.0"),
                            "/headless/supported_versions", "a version component beyond an integer"),
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
                    Arguments.of(ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme/"),
                            "/headless/writable_paths/0", "a writable path with a trailing slash"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme/."),
                            "/headless/writable_paths/0", "a writable path with a '.' segment"),
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
                                    .set("/worker/ack_deadline_seconds", 300),
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
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "argv", "pointer", null, "argv", List.of("acme", "probe"),
                                            "pattern", "(id-\\d+)")),
                            "/headless/weights_probe/argv", "an argv weights probe without the model slot"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/events", map("kind_pointer", "/type",
                                    "kinds", map("text", null, "tool_call", null, "tool_result", null,
                                    "file_changed", null, "turn", map("match", List.of("result"))))),
                            "/headless/events/kinds/turn/stop_reason", "a turn without its stop reason"),
                    Arguments.of(ProfileFixtures.valid().set("/schema_version", 2),
                            "/schema_version", "another schema version"),
                    Arguments.of(ProfileFixtures.valid().set("/display_name", ""),
                            "/display_name", "an empty display name"),
                    Arguments.of(ProfileFixtures.valid().set("/min_daemon_version", "newest"),
                            "/min_daemon_version", "a minimum daemon version that is no version"),
                    Arguments.of(ProfileFixtures.valid().set("/icon", map("form", "source",
                                    "source", "https://example.test/icons/acme.svg", "bundled", "acme-icon")),
                            "/icon/bundled", "an icon of the form source with a bundled name"),
                    Arguments.of(ProfileFixtures.valid().set("/icon", map("form", "bundled",
                                    "source", "https://example.test/icons/acme.svg", "bundled", "acme-icon")),
                            "/icon/source", "an icon of the form bundled with a source URL"),
                    Arguments.of(ProfileFixtures.valid().set("/icon", map("form", "bundled",
                                    "source", null, "bundled", "Acme")),
                            "/icon/bundled", "a bundled icon name that is no name"),
                    Arguments.of(ProfileFixtures.valid().set("/icon", 1),
                            "/icon", "an icon that is no object"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/executable", "Acme"),
                            "/headless/executable", "an executable name that is no bare name"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/models_probe",
                                    map("argv", List.of("acme", "models"), "format", "lines", "pointer", "/id")),
                            "/headless/models_probe/pointer", "a line-wise models probe with a pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "models_listing", "pointer", null, "argv", null, "pattern", null)),
                            "/headless/weights_probe/pointer", "a models-listing weights probe without a pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "models_listing", "pointer", "id", "argv", null, "pattern", null)),
                            "/headless/weights_probe/pointer", "a models-listing pointer that is no pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "models_listing", "pointer", "/id", "argv", null,
                                            "pattern", "(id)")),
                            "/headless/weights_probe/pattern", "a models-listing weights probe with a pattern"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "argv", "pointer", "/id", "argv", List.of("acme", "{{model}}"),
                                            "pattern", "(id)")),
                            "/headless/weights_probe/pointer", "an argv weights probe with a pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "argv", "pointer", null, "argv", null, "pattern", "(id)")),
                            "/headless/weights_probe/argv", "an argv weights probe without an argv"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/weights_probe",
                                    map("source", "argv", "pointer", null,
                                            "argv", List.of("acme", "{{model}}"), "pattern", null)),
                            "/headless/weights_probe/pattern", "an argv weights probe without a pattern"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/update_evidence/method", "code_signature")
                                    .set("/headless/update_evidence/signer", ""),
                            "/headless/update_evidence/signer", "an empty signer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/mcp_config/delivery", "file"),
                            "/headless/mcp_config/path", "a delivery file without a path"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/mcp_config/path", "settings.json"),
                            "/headless/mcp_config/path", "a delivery slot with a path"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/env", map("x-y", "1")),
                            "/headless/env/x-y", "an environment variable name that is no name"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/capabilities",
                                    List.of("project_settings_control", "extension_isolation", "bogus")),
                            "/headless/capabilities/2", "an undeclared capability"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/output/result_pointer", "result"),
                            "/headless/output/result_pointer", "a result pointer that is no pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/events", map("kind_pointer", "type",
                                    "kinds", map("text", null, "tool_call", null, "tool_result", null,
                                    "file_changed", null, "turn", map("match", List.of("result"),
                                    "stop_reason", "/stop_reason")))),
                            "/headless/events/kind_pointer", "a kind pointer that is no pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/endpoints", List.of("acme")),
                            "/headless/endpoints/0", "an endpoint without a port"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/endpoints", List.of("acme:0")),
                            "/headless/endpoints/0", "an endpoint with port zero"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/endpoints", List.of("acme:65536")),
                            "/headless/endpoints/0", "an endpoint beyond the port range"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/writable_paths/0", "/etc/acme"),
                            "/headless/writable_paths/0", "a writable path that is no home-relative path"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/data_home",
                                    map("variable", "x-y", "login_file", ".auth")),
                            "/headless/data_home/variable", "a data home variable that is no name"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/data_home",
                                    map("variable", "LD_PRELOAD", "login_file", ".auth")),
                            "/headless/data_home/variable", "a data home that relocates a job variable"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/resumability/max_hold_seconds", null)
                                    .set("/headless/resumability/hold", true),
                            "/headless/resumability/max_hold_seconds", "a hold without its max_hold_seconds"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/resumability/resume",
                                    map("argv", List.of("--resume", "{{session_id}}"), "session_id", "session",
                                            "max_age_seconds", 600)),
                            "/headless/resumability/resume/session_id", "a resume session_id that is no pointer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/resumability/resume",
                                    map("argv", List.of("--resume", "{{session_id}}"), "session_id", "/session",
                                            "max_age_seconds", 60)),
                            "/headless/resumability/resume/max_age_seconds", "a resume max_age_seconds below the bound"),
                    Arguments.of(ProfileFixtures.valid().set("/connection/transport", "http"),
                            "/connection/transport", "a transport that is not stdio"),
                    Arguments.of(ProfileFixtures.valid().set("/headless", null),
                            "/worker/eligible", "an eligible worker host without a headless block"),
                    Arguments.of(ProfileFixtures.valid().set("/worker/ack_informational", true),
                            "/worker/ack_deadline_seconds", "an informational acknowledgement without a deadline"),
                    Arguments.of(ProfileFixtures.valid().set("/worker/restricted_models", null),
                            "/worker/restricted_models", "an enforced class without restricted_models"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/detection/process_markers", List.of("x-y")),
                            "/detection/process_markers/0", "a process marker that is no variable"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/detection/reaches", List.of("command", "command")),
                            "/detection/reaches/1", "a reach that occurs twice"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/detection/primary_signals/0/variable", "x-y"),
                            "/detection/primary_signals/0/variable", "a signal variable that is no variable"),
                    Arguments.of(ProfileFixtures.valid().set("/identity/version_pattern", null),
                            "/identity", "a version probe without its pattern"),
                    Arguments.of(ProfileFixtures.valid().set("/display_name", "Acme\nCLI"),
                            "/display_name", "a display name that is no single line"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/argv", List.of("--task", "{{bogus}}")),
                            "/headless/argv/1", "an argv placeholder that is not closed"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/argv", List.of("--task", "")),
                            "/headless/argv/1", "an empty argv item"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/argv", List.of("~/acme")),
                            "/headless/argv/0", "a home path in argv"),
                    Arguments.of(ProfileFixtures.valid().set("/identity/version_pattern", "a".repeat(129)),
                            "/identity/version_pattern", "a version pattern longer than 128 characters"),
                    Arguments.of(ProfileFixtures.valid().set("/identity/version_pattern", "(a)\\1"),
                            "/identity/version_pattern", "a version pattern with a backreference"),
                    Arguments.of(ProfileFixtures.valid().set("/identity/version_pattern", "acme"),
                            "/identity/version_pattern", "a version pattern without a capture group"),
                    Arguments.of(ProfileFixtures.valid().set("/client_id", 1),
                            "/client_id", "a client id that is no string"),
                    Arguments.of(ProfileFixtures.valid().set("/schema_version", "1"),
                            "/schema_version", "a schema version that is no integer"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/tool_result_limit_chars", 1),
                            "/headless/tool_result_limit_chars", "a tool-result limit below the bound"),
                    Arguments.of(ProfileFixtures.valid().set("/worker/eligible", "yes"),
                            "/worker/eligible", "an eligibility that is no boolean"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/agent_mode", "bogus"),
                            "/headless/agent_mode", "an undeclared agent mode"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/classes", map("writer", null, "read_only", null, "zero_tool", null))
                                    .set("/headless/capabilities", 1),
                            "/headless/capabilities", "capabilities that are no array"),
                    Arguments.of(ProfileFixtures.valid().set("/headless/endpoints", List.of()),
                            "/headless/endpoints", "an empty endpoint list"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/classes", map("writer", null, "read_only", null, "zero_tool", null))
                                    .set("/headless/capabilities", List.of("")),
                            "/headless/capabilities/0", "an empty capability"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/headless/classes", map("writer", null, "read_only", null, "zero_tool", null))
                                    .set("/headless/capabilities", List.of("ephemeral", "ephemeral")),
                            "/headless/capabilities/1", "a capability that occurs twice"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/tools/idioms", map("lint", map("disposition", "bogus", "replacement", null))),
                            "/tools/idioms/lint/disposition", "an undeclared idiom disposition"),
                    Arguments.of(ProfileFixtures.valid()
                                    .set("/tools/idioms", map("lint", map("disposition", "rewrite", "replacement",
                                            "x;y"))),
                            "/tools/idioms/lint/replacement", "an idiom replacement a model cannot read"));
        }

        @Test
        @DisplayName("the optional blocks and every alternative form load")
        void acceptedExtensions() {
            var idioms = ProfileFixtures.valid()
                    .set("/tools/idioms", map("lint", map("disposition", "rewrite", "replacement", "replace x")));
            var efforts = ProfileFixtures.valid()
                    .set("/headless/model_efforts", map("acme-frontier", List.of("high")));
            var budget = ProfileFixtures.valid().set("/headless/context_budget_tokens", 20000);
            var models = ProfileFixtures.valid().set("/headless/models_probe",
                    map("argv", List.of("acme", "models"), "format", "json", "pointer", "/id"));
            var hold = ProfileFixtures.valid().set("/headless/resumability/hold", true)
                    .set("/headless/resumability/max_hold_seconds", 120);
            var prefix = ProfileFixtures.valid()
                    .set("/headless/model_tiers", map("frontier", "acme/frontier", "standard", "acme/standard",
                            "fast", "acme/fast"))
                    .set("/worker/restricted_models", List.of("acme/*"));
            var signal = ProfileFixtures.valid().set("/detection/primary_signals/0/equals", "acme");
            var ports = ProfileFixtures.valid().set("/headless/endpoints", List.of("acme:1", "acme:65535"));

            assertAll(
                    () -> ProfileValidator.validate(idioms.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(efforts.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(budget.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(models.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(hold.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(prefix.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(signal.bytes(), DOCUMENT, ProfileRules.none()),
                    () -> ProfileValidator.validate(ports.bytes(), DOCUMENT, ProfileRules.none()));
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
            var bytes = fixture.bytes();
            var rules = ProfileRules.none();
            var refusal = assertThrows(ProfileRefusal.class,
                    () -> ProfileValidator.validate(bytes, DOCUMENT, rules), because);

            assertAll(
                    () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome(), because),
                    () -> assertEquals(DOCUMENT, refusal.document(), because),
                    () -> assertEquals(pointer, refusal.pointer(), because));
        }
    }

    @Test
    @DisplayName("an empty class args is a schema-valid launch, and a turn carries a null or a pointer stop reason")
    void emptyArgsAndBothStopReasonsAccepted() {
        for (var stopReason : new Object[]{null, "/stop_reason"}) {
            var fixture = ProfileFixtures.valid()
                    .set("/headless/classes/writer/args", new ArrayList<>())
                    .set("/headless/events", mapOf("kind_pointer", "/type", "kinds", mapOf("text", null,
                            "tool_call", null, "tool_result", null, "file_changed", null, "turn",
                            mapOf("match", List.of("result"), "stop_reason", stopReason))));

            ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none());
        }
    }

    @Test
    @DisplayName("both forms of the weights probe load")
    void weightsProbeAccepted() {
        var listing = ProfileFixtures.valid().set("/headless/weights_probe",
                mapOf("source", "models_listing", "pointer", "/weights/id", "argv", null, "pattern", null));
        var argv = ProfileFixtures.valid().set("/headless/weights_probe",
                mapOf("source", "argv", "pointer", null, "argv", List.of("acme", "weights", "{{model}}"),
                        "pattern", "(id-\\d+)"));

        ProfileValidator.validate(listing.bytes(), DOCUMENT, ProfileRules.none());
        ProfileValidator.validate(argv.bytes(), DOCUMENT, ProfileRules.none());
    }

    private static Map<String, Object> mapOf(Object... pairs) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index < pairs.length; index += 2) {
            map.put((String) pairs[index], pairs[index + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("a repeated path segment is a value, not an unchecked failure")
    void repeatedPathSegmentAccepted() {
        var fixture = ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.cache/acme/acme");

        ProfileValidator.validate(fixture.bytes(), DOCUMENT, ProfileRules.none());
    }

    @Test
    @DisplayName("a number beyond the scale and precision bound is refused")
    void oversizedDecimalRefused() {
        var document = "{\"x\":1e1001}".getBytes(StandardCharsets.UTF_8);

        var refusal = assertThrows(ProfileRefusal.class,
                () -> JsonDocument.parse(document, DOCUMENT));

        assertEquals("/x", refusal.pointer());
    }

    @Test
    @DisplayName("a writable path that covers a managed bundle file is refused")
    void managedPathRefused() {
        var rules = new ProfileRules(Set.of("~/.acme/settings.json"), Set.of());
        var fixture = ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme");
        var bytes = fixture.bytes();

        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(bytes, DOCUMENT, rules));

        assertEquals("/headless/writable_paths/0", refusal.pointer());
    }

    @Test
    @DisplayName("a harness home is never a writable path as a whole")
    void harnessHomeRefused() {
        var rules = new ProfileRules(Set.of(), Set.of("~/.acme"));
        var fixture = ProfileFixtures.valid().set("/headless/writable_paths/0", "~/.acme");
        var bytes = fixture.bytes();

        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(bytes, DOCUMENT, rules));

        assertEquals("/headless/writable_paths/0", refusal.pointer());
    }

    @Test
    @DisplayName("a member twice is refused by the reader")
    void duplicateMemberRefused() {
        var document = "{\"schema_version\":1,\"schema_version\":1}".getBytes(StandardCharsets.UTF_8);

        var rules = ProfileRules.none();
        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(document, DOCUMENT, rules));

        assertAll(
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()),
                () -> assertEquals("/schema_version", refusal.pointer()));
    }

    @Test
    @DisplayName("a document larger than 64 KiB is refused before any value is used")
    void oversizedDocumentRefused() {
        var document = new byte[JsonDocument.MAX_BYTES + 1];

        var rules = ProfileRules.none();
        var refusal = assertThrows(ProfileRefusal.class,
                () -> ProfileValidator.validate(document, DOCUMENT, rules));

        assertAll(
                () -> assertEquals(ProfileRefusal.Outcome.PROFILE_INVALID, refusal.outcome()),
                () -> assertEquals("", refusal.pointer()));
    }
}
