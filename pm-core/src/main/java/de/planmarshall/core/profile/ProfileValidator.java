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

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates a client profile as a whole against the client profile schema and the rules stated with its blocks
 * (PM-CLIENT-5).
 * <p>
 * The validator accepts every mechanism the schema names and no other: every object is closed, every enumeration is
 * closed, every array has its bound, every string is NFC, single-line, control-character-free and bounded, and the
 * hardening rules of <em>A Profile Is Untrusted Input</em> are checked before any value of the profile is used. A value
 * that breaks a rule refuses the profile as a whole with a {@link ProfileRefusal} naming the document and the JSON
 * Pointer of the failing value; the runtime never runs on a part of a profile.
 * <p>
 * The validator names no harness: what a harness is, is the document. The paths a bundle manages and the harness homes
 * of an installation are data of the installation ({@link ProfileRules}), never a literal of this class.
 *
 * @since 0.1
 */
public final class ProfileValidator {

    /** The one schema version a profile declares. */
    public static final int SCHEMA_VERSION = 1;

    private static final Set<String> TOP_LEVEL = Set.of("schema_version", "client_id", "display_name", "icon",
            "min_daemon_version", "tools", "skill_loading", "headless", "connection", "worker", "detection",
            "identity");

    private static final Set<String> HEADLESS = Set.of("executable", "install_locations", "install_hint", "auth_probe",
            "models_probe", "weights_probe", "model_tiers", "supported_versions", "update_evidence", "agent_mode",
            "argv", "mcp_config", "classes", "env", "capabilities", "output", "usage", "events", "endpoints",
            "writable_paths", "data_home", "tool_result_limit_chars", "resource_tools", "session_residue",
            "resumability");

    private static final Set<String> HEADLESS_OPTIONAL = Set.of("model_efforts", "context_budget_tokens");

    private static final Set<String> CAPABILITIES = Set.of("result_schema", "ephemeral", "usage_fields", "cost_field",
            "exit_reliable", "project_settings_control", "extension_isolation");

    private static final Set<String> SLOTS = Set.of("prompt_file", "model", "effort", "mcp_config", "result_schema",
            "tools", "cwd", "agent");

    private static final Set<String> SESSION_SLOTS = Set.of("prompt_file", "model", "effort", "mcp_config",
            "result_schema", "tools", "cwd", "agent", "session_id");

    private static final Set<String> JOB_ENVIRONMENT = Set.of("PATH", "HOME", "NODE_OPTIONS", "JAVA_TOOL_OPTIONS");

    private static final List<String> JOB_ENVIRONMENT_PREFIXES = List.of("LD_", "DYLD_", "PYTHON", "PM_MCP_");

    private static final List<String> CREDENTIAL_MARKERS = List.of("TOKEN", "SECRET", "PASSWORD", "PASSWD", "APIKEY",
            "API_KEY", "CREDENTIAL", "AUTH");

    private static final String MODEL_READABLE_CHARACTERS = " _.:/-$=,()\"{}<>";

    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern VARIABLE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");
    private static final Pattern VERSION = Pattern.compile("^[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9.-]+)?$");
    private static final Pattern EXECUTABLE = Pattern.compile("^[a-z][a-z0-9-]*$");
    private static final Pattern INSTALL_LOCATION = Pattern.compile("^(~|\\{app_dir\\}|/)[A-Za-z0-9_./ -]*$");
    private static final Pattern INSTALL_HINT = Pattern.compile("^[a-z][a-z0-9._-]*$");
    private static final Pattern TOOL_NAME = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final Pattern ICON_SOURCE = Pattern.compile("^https://[A-Za-z0-9.-]+/[A-Za-z0-9._/-]+\\.(svg|png)$");
    private static final Pattern BUNDLED_ICON = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern RELATIVE_PATH = Pattern.compile("^[A-Za-z0-9_.][A-Za-z0-9_./-]*$");
    private static final Pattern HOME_PATH = Pattern.compile("^~/[A-Za-z0-9_./-]+$");
    private static final Pattern ENDPOINT = Pattern.compile(
            "^[a-z0-9.-]+:([1-9][0-9]{0,3}|[1-5][0-9]{4}|6[0-4][0-9]{3}|65[0-4][0-9]{2}|655[0-2][0-9]|6553[0-5])$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]*)\\}\\}");

    private ProfileValidator() {
    }

    /**
     * @param document the profile document, UTF-8 encoded
     * @param name     the document, as a refusal names it
     * @param rules    what the installation knows beyond the document
     * @return the validated tree
     * @throws ProfileRefusal if the document is unreadable or breaks a rule of the schema
     */
    public static JsonNode validate(byte[] document, String name, ProfileRules rules) {
        return validate(JsonDocument.parse(document, name), name, rules);
    }

    /**
     * @param profile  the profile tree
     * @param document the document, as a refusal names it
     * @param rules    what the installation knows beyond the document
     * @return the validated tree
     * @throws ProfileRefusal if the profile breaks a rule of the schema
     */
    public static JsonNode validate(JsonNode profile, String document, ProfileRules rules) {
        var checker = new Checker(document, rules);
        checker.checkProfile(profile);
        return profile;
    }

    /**
     * @param profile a validated profile
     * @return its {@code client_id}
     */
    public static String clientId(JsonNode profile) {
        return profile.member("client_id").flatMap(JsonNode::text).orElse("");
    }

    /** The checks of one document; it carries the document name and the installation's rules. */
    private static final class Checker {

        private final String document;
        private final ProfileRules rules;

        private Checker(String document, ProfileRules rules) {
            this.document = document;
            this.rules = rules;
        }

        private void checkProfile(JsonNode profile) {
            object(profile, "", "profile", TOP_LEVEL, TOP_LEVEL);
            if (integer(required(profile, "", "schema_version"), "/schema_version", "schema_version", 1,
                    SCHEMA_VERSION) != SCHEMA_VERSION) {
                throw invalid("/schema_version", "'schema_version' must be %d".formatted(SCHEMA_VERSION));
            }
            var clientId = text(required(profile, "", "client_id"), "/client_id", "client_id", 32);
            if (!CLIENT_ID.matcher(clientId).matches()) {
                throw invalid("/client_id", "not a client id: '%s'".formatted(clientId));
            }
            var displayName = text(required(profile, "", "display_name"), "/display_name", "display_name", 64);
            if (displayName.isEmpty()) {
                throw invalid("/display_name", "a display name is not empty");
            }
            stringRule(displayName, "/display_name", true);
            var minDaemonVersion = text(required(profile, "", "min_daemon_version"), "/min_daemon_version",
                    "min_daemon_version", 256);
            if (!VERSION.matcher(minDaemonVersion).matches()) {
                throw invalid("/min_daemon_version", "not a version: '%s'".formatted(minDaemonVersion));
            }
            icon(required(profile, "", "icon"), "/icon");
            tools(required(profile, "", "tools"), "/tools");
            skillLoading(required(profile, "", "skill_loading"), "/skill_loading");
            headless(required(profile, "", "headless"), "/headless");
            connection(required(profile, "", "connection"), "/connection");
            worker(required(profile, "", "worker"), "/worker", profile);
            detection(required(profile, "", "detection"), "/detection");
            identity(required(profile, "", "identity"), "/identity");
        }

        private void icon(JsonNode icon, String pointer) {
            if (icon.isNull()) {
                return;
            }
            object(icon, pointer, "icon", Set.of("form", "source", "bundled"), Set.of("form", "source", "bundled"));
            var form = enumValue(required(icon, pointer, "form"), pointer + "/form", "form", Set.of("source", "bundled"));
            var source = optional(icon, "source");
            var bundled = optional(icon, "bundled");
            if ("source".equals(form)) {
                var url = string(source, pointer + "/source", "source", 256);
                if (!ICON_SOURCE.matcher(url).matches()) {
                    throw invalid(pointer + "/source", "an icon source is an https URL of an SVG or PNG file: '%s'"
                            .formatted(url));
                }
                if (bundled != null && !bundled.isNull()) {
                    throw invalid(pointer + "/bundled", "an icon of the form source has no bundled name");
                }
            } else {
                var name = string(bundled, pointer + "/bundled", "bundled", 32);
                if (!BUNDLED_ICON.matcher(name).matches()) {
                    throw invalid(pointer + "/bundled", "not a bundled icon name: '%s'".formatted(name));
                }
                if (source != null && !source.isNull()) {
                    throw invalid(pointer + "/source", "an icon of the form bundled has no source URL");
                }
            }
        }

        private void tools(JsonNode tools, String pointer) {
            var members = Set.of("read_file", "write_file", "edit_file", "search_code", "find_files", "run_shell",
                    "ask_user");
            var allowed = Set.of("read_file", "write_file", "edit_file", "search_code", "find_files", "run_shell",
                    "ask_user", "idioms");
            object(tools, pointer, "tools", members, allowed);
            for (var name : List.of("read_file", "write_file", "edit_file", "search_code", "find_files", "run_shell",
                    "ask_user")) {
                var value = text(required(tools, pointer, name), pointer + "/" + name, name, 256);
                toolName(value, pointer + "/" + name);
            }
            var idioms = optional(tools, "idioms");
            if (idioms == null) {
                return;
            }
            object(idioms, pointer + "/idioms", "idioms");
            for (var member : idioms.members().entrySet()) {
                var idiomPointer = pointer + "/idioms/" + escape(member.getKey());
                object(member.getValue(), idiomPointer, "idiom", Set.of("disposition", "replacement"),
                        Set.of("disposition", "replacement"));
                enumValue(required(member.getValue(), idiomPointer, "disposition"), idiomPointer + "/disposition",
                        "disposition", Set.of("rewrite", "preserve", "omit"));
                var replacement = optional(member.getValue(), "replacement");
                if (replacement != null) {
                    var text = text(replacement, idiomPointer + "/replacement", "replacement", 256);
                    stringRule(text, idiomPointer + "/replacement", true);
                }
            }
        }

        private void skillLoading(JsonNode skill, String pointer) {
            var members = Set.of("mode", "load_directive_template", "schema_residency", "skills_extension");
            object(skill, pointer, "skill_loading", members, members);
            enumValue(required(skill, pointer, "mode"), pointer + "/mode", "mode",
                    Set.of("tool_call", "mcp_resource", "view_file"));
            var template = text(required(skill, pointer, "load_directive_template"),
                    pointer + "/load_directive_template", "load_directive_template", 256);
            stringRule(template, pointer + "/load_directive_template", true);
            enumValue(required(skill, pointer, "schema_residency"), pointer + "/schema_residency", "schema_residency",
                    Set.of("resident", "on_demand"));
            bool(required(skill, pointer, "skills_extension"), pointer + "/skills_extension", "skills_extension");
        }

        private void headless(JsonNode headless, String pointer) {
            if (headless.isNull()) {
                return;
            }
            var headlessMembers = new HashSet<>(HEADLESS);
            headlessMembers.addAll(HEADLESS_OPTIONAL);
            object(headless, pointer, "headless", HEADLESS, headlessMembers);
            var executable = text(required(headless, pointer, "executable"), pointer + "/executable", "executable", 64);
            if (!EXECUTABLE.matcher(executable).matches()) {
                throw invalid(pointer + "/executable", "not a bare executable name: '%s'".formatted(executable));
            }
            installLocations(required(headless, pointer, "install_locations"), pointer + "/install_locations");
            var installHint = text(required(headless, pointer, "install_hint"), pointer + "/install_hint", "install_hint",
                    64);
            if (!INSTALL_HINT.matcher(installHint).matches()) {
                throw invalid(pointer + "/install_hint", "not a message key: '%s'".formatted(installHint));
            }
            authProbe(required(headless, pointer, "auth_probe"), pointer + "/auth_probe");
            modelsProbe(required(headless, pointer, "models_probe"), pointer + "/models_probe");
            var weightsProbe = required(headless, pointer, "weights_probe");
            if (!weightsProbe.isNull() && !weightsProbe.isObject()) {
                throw invalid(pointer + "/weights_probe", "a weights probe is an object or null");
            }
            modelTiers(required(headless, pointer, "model_tiers"), pointer + "/model_tiers");
            var supported = text(required(headless, pointer, "supported_versions"), pointer + "/supported_versions",
                    "supported_versions", 256);
            SupportedVersions.parse(supported, document, pointer + "/supported_versions");
            updateEvidence(required(headless, pointer, "update_evidence"), pointer + "/update_evidence");
            enumValue(required(headless, pointer, "agent_mode"), pointer + "/agent_mode", "agent_mode",
                    Set.of("inline", "flags"));
            launchArguments(required(headless, pointer, "argv"), pointer + "/argv", "argv", SLOTS);
            var mcpConfig = required(headless, pointer, "mcp_config");
            var exclusive = mcpConfig(mcpConfig, pointer + "/mcp_config");
            classes(required(headless, pointer, "classes"), pointer + "/classes", exclusive, headless, pointer);
            environment(required(headless, pointer, "env"), pointer + "/env");
            capabilities(required(headless, pointer, "capabilities"), pointer + "/capabilities");
            var efforts = optional(headless, "model_efforts");
            if (efforts != null) {
                modelEfforts(efforts, pointer + "/model_efforts");
            }
            output(required(headless, pointer, "output"), pointer + "/output");
            usage(required(headless, pointer, "usage"), pointer + "/usage");
            events(required(headless, pointer, "events"), pointer + "/events");
            endpoints(required(headless, pointer, "endpoints"), pointer + "/endpoints");
            writablePaths(required(headless, pointer, "writable_paths"), pointer + "/writable_paths");
            dataHome(required(headless, pointer, "data_home"), pointer + "/data_home");
            var limit = optional(headless, "tool_result_limit_chars");
            if (limit != null && !limit.isNull()) {
                integer(limit, pointer + "/tool_result_limit_chars", "tool_result_limit_chars", 4000,
                        Integer.MAX_VALUE);
                var resources = required(headless, pointer, "resource_tools");
                stringArray(resources, pointer + "/resource_tools", "resource_tools", 0, 64, true);
                if (resources.elements().isEmpty()) {
                    throw invalid(pointer + "/tool_result_limit_chars",
                            "a tool-result limit requires a non-empty resource_tools");
                }
            }
            stringArray(required(headless, pointer, "resource_tools"), pointer + "/resource_tools", "resource_tools", 0,
                    64, true);
            enumValue(required(headless, pointer, "session_residue"), pointer + "/session_residue", "session_residue",
                    Set.of("ephemeral", "persisted"));
            resumability(required(headless, pointer, "resumability"), pointer + "/resumability",
                    enumValue(required(headless, pointer, "session_residue"), pointer + "/session_residue",
                            "session_residue", Set.of("ephemeral", "persisted")));
            var budget = optional(headless, "context_budget_tokens");
            if (budget != null) {
                integer(budget, pointer + "/context_budget_tokens", "context_budget_tokens", 10000, Integer.MAX_VALUE);
            }
        }

        private void installLocations(JsonNode locations, String pointer) {
            var elements = list(locations, pointer, "install_locations", 0, 16);
            for (var index = 0; index < elements.size(); index++) {
                var location = text(elements.get(index), pointer + "/" + index, "install_locations[" + index + "]", 256);
                if (!INSTALL_LOCATION.matcher(location).matches()) {
                    throw invalid(pointer + "/" + index, "not an install location: '%s'".formatted(location));
                }
            }
        }

        private void authProbe(JsonNode probe, String pointer) {
            if (probe.isNull()) {
                return;
            }
            var members = Set.of("argv", "logged_in_exit_codes", "logged_out_exit_codes", "login_command");
            object(probe, pointer, "auth_probe", members, members);
            launchArguments(required(probe, pointer, "argv"), pointer + "/argv", "argv", SLOTS);
            exitCodes(required(probe, pointer, "logged_in_exit_codes"), pointer + "/logged_in_exit_codes",
                    "logged_in_exit_codes");
            exitCodes(required(probe, pointer, "logged_out_exit_codes"), pointer + "/logged_out_exit_codes",
                    "logged_out_exit_codes");
            launchArguments(required(probe, pointer, "login_command"), pointer + "/login_command", "login_command",
                    SLOTS);
        }

        private void exitCodes(JsonNode codes, String pointer, String name) {
            var elements = list(codes, pointer, name, 1, 64);
            for (var index = 0; index < elements.size(); index++) {
                integer(elements.get(index), pointer + "/" + index, name + "[" + index + "]", 0, 255);
            }
        }

        private void modelsProbe(JsonNode probe, String pointer) {
            if (probe.isNull()) {
                return;
            }
            var members = Set.of("argv", "format", "pointer");
            object(probe, pointer, "models_probe", members, members);
            launchArguments(required(probe, pointer, "argv"), pointer + "/argv", "argv", SLOTS);
            var format = enumValue(required(probe, pointer, "format"), pointer + "/format", "format",
                    Set.of("lines", "json"));
            var modelPointer = optional(probe, "pointer");
            if ("json".equals(format)) {
                if (modelPointer == null || modelPointer.isNull()) {
                    throw invalid(pointer + "/pointer", "a JSON models probe names its pointer");
                }
                string(modelPointer, pointer + "/pointer", "pointer", 256);
            } else if (modelPointer != null && !modelPointer.isNull()) {
                throw invalid(pointer + "/pointer", "a line-wise models probe has a null pointer");
            }
        }

        private void modelTiers(JsonNode tiers, String pointer) {
            var members = Set.of("frontier", "standard", "fast");
            object(tiers, pointer, "model_tiers", members, members);
            for (var tier : members) {
                var value = required(tiers, pointer, tier);
                if (!value.isNull()) {
                    text(value, pointer + "/" + tier, tier, 128);
                }
            }
        }

        private void updateEvidence(JsonNode evidence, String pointer) {
            var members = Set.of("method", "signer");
            object(evidence, pointer, "update_evidence", members, members);
            var method = enumValue(required(evidence, pointer, "method"), pointer + "/method", "method",
                    Set.of("code_signature", "package_provenance", "none"));
            var signer = optional(evidence, "signer");
            if ("none".equals(method)) {
                if (signer != null && !signer.isNull()) {
                    throw invalid(pointer + "/signer", "the method none has a null signer");
                }
            } else {
                if (signer == null || signer.isNull()) {
                    throw invalid(pointer + "/signer", "the method %s requires a signer".formatted(method));
                }
                var value = text(signer, pointer + "/signer", "signer", 256);
                if (value.isEmpty()) {
                    throw invalid(pointer + "/signer", "a signer is not empty");
                }
            }
        }

        /** @return whether the MCP configuration is exclusive */
        private boolean mcpConfig(JsonNode config, String pointer) {
            var members = Set.of("delivery", "path", "exclusive");
            object(config, pointer, "mcp_config", members, members);
            var delivery = enumValue(required(config, pointer, "delivery"), pointer + "/delivery", "delivery",
                    Set.of("slot", "file"));
            var path = optional(config, "path");
            if ("file".equals(delivery)) {
                if (path == null || path.isNull()) {
                    throw invalid(pointer + "/path", "the delivery file names its path");
                }
                var value = text(path, pointer + "/path", "path", 256);
                relativePath(value, pointer + "/path", "mcp_config.path");
            } else if (path != null && !path.isNull()) {
                throw invalid(pointer + "/path", "the delivery slot has a null path");
            }
            return bool(required(config, pointer, "exclusive"), pointer + "/exclusive", "exclusive");
        }

        private void classes(JsonNode classes, String pointer, boolean exclusive, JsonNode headless,
                String headlessPointer) {
            var members = Set.of("writer", "read_only", "zero_tool");
            object(classes, pointer, "classes", members, members);
            var writer = toolClass(required(classes, pointer, "writer"), pointer + "/writer", "writer");
            var readOnly = toolClass(required(classes, pointer, "read_only"), pointer + "/read_only", "read_only");
            var zeroTool = required(classes, pointer, "zero_tool");
            var zeroToolRunnable = false;
            if (!zeroTool.isNull()) {
                object(zeroTool, pointer + "/zero_tool", "zero_tool", Set.of("args"), Set.of("args"));
                launchArguments(required(zeroTool, pointer + "/zero_tool", "args"), pointer + "/zero_tool/args",
                        "zero_tool.args", SLOTS);
                zeroToolRunnable = true;
            }
            if (!exclusive && (writer != null || readOnly != null || zeroToolRunnable)) {
                throw invalid(pointer,
                        "a profile whose MCP configuration is not exclusive declares writer, read_only and zero_tool "
                                + "all null");
            }
            var capabilities = required(headless, headlessPointer, "capabilities");
            if (zeroToolRunnable && !hasCapability(capabilities, "project_settings_control")) {
                throw invalid(pointer + "/zero_tool",
                        "a runnable zero_tool class requires the capability project_settings_control");
            }
            if ((writer != null || readOnly != null) && !hasCapability(capabilities, "extension_isolation")) {
                throw invalid(pointer,
                        "a runnable writer or read_only class requires the capability extension_isolation");
            }
        }

        /** @return the tool_set of a runnable class, or {@code null} for a class the harness cannot run */
        private String toolClass(JsonNode declaration, String pointer, String name) {
            if (declaration.isNull()) {
                return null;
            }
            object(declaration, pointer, name, Set.of("args", "tool_set"), Set.of("args", "tool_set"));
            launchArguments(required(declaration, pointer, "args"), pointer + "/args", name + ".args", SLOTS);
            return enumValue(required(declaration, pointer, "tool_set"), pointer + "/tool_set", "tool_set",
                    Set.of("enforced", "advisory"));
        }

        private boolean hasCapability(JsonNode capabilities, String capability) {
            return capabilities.elements().stream().anyMatch(value -> value.text().orElse("").equals(capability));
        }

        private void environment(JsonNode environment, String pointer) {
            object(environment, pointer, "env");
            for (var member : environment.members().entrySet()) {
                var name = member.getKey();
                if (!VARIABLE.matcher(name).matches()) {
                    throw invalid(pointer + "/" + escape(name), "not an environment variable name: '%s'".formatted(name));
                }
                if (jobEnvironment(name)) {
                    throw invalid(pointer + "/" + escape(name),
                            "the launch may not set the job environment variable '%s'".formatted(name));
                }
                if (credentialName(name)) {
                    throw invalid(pointer + "/" + escape(name),
                            "the launch may not set the credential-pattern variable '%s'".formatted(name));
                }
                var value = text(member.getValue(), pointer + "/" + escape(name), name, 1024);
                launchItem(value, pointer + "/" + escape(name), SLOTS);
            }
        }

        private void capabilities(JsonNode capabilities, String pointer) {
            stringArray(capabilities, pointer, "capabilities", 0, 64, true);
            for (var index = 0; index < capabilities.elements().size(); index++) {
                var value = capabilities.elements().get(index).text().orElse("");
                if (!CAPABILITIES.contains(value)) {
                    throw invalid(pointer + "/" + index, "not a capability: '%s'".formatted(value));
                }
            }
        }

        private void modelEfforts(JsonNode efforts, String pointer) {
            object(efforts, pointer, "model_efforts");
            for (var member : efforts.members().entrySet()) {
                stringArray(member.getValue(), pointer + "/" + escape(member.getKey()), "model_efforts",
                        0, 64, true);
            }
        }

        private void output(JsonNode output, String pointer) {
            var members = Set.of("format", "result_pointer");
            object(output, pointer, "output", members, members);
            enumValue(required(output, pointer, "format"), pointer + "/format", "format",
                    Set.of("json", "stream_json", "jsonl"));
            var resultPointer = required(output, pointer, "result_pointer");
            if (!resultPointer.isNull()) {
                var value = text(resultPointer, pointer + "/result_pointer", "result_pointer", 256);
                if (!value.startsWith("/")) {
                    throw invalid(pointer + "/result_pointer", "not a JSON Pointer: '%s'".formatted(value));
                }
            }
        }

        /**
         * The usage block is {@code token-usage}'s, including the rule that {@code input_tokens} and
         * {@code output_tokens} are never {@code not_applicable}; this validator checks only its shape and leaves the
         * meaning of a figure to the stage {@code usage} of the profile verification.
         */
        private void usage(JsonNode usage, String pointer) {
            var members = Set.of("input_tokens", "output_tokens", "cache_read", "cache_creation", "reasoning",
                    "cost_usd", "tool_uses");
            object(usage, pointer, "usage", members, members);
            for (var member : members) {
                var value = required(usage, pointer, member);
                if (!value.isNull()) {
                    text(value, pointer + "/" + member, member, 256);
                }
            }
        }

        private void events(JsonNode events, String pointer) {
            if (events.isNull()) {
                return;
            }
            object(events, pointer, "events", Set.of("kind_pointer", "kinds"), Set.of("kind_pointer", "kinds"));
            var kindPointer = text(required(events, pointer, "kind_pointer"), pointer + "/kind_pointer", "kind_pointer",
                    256);
            if (!kindPointer.startsWith("/")) {
                throw invalid(pointer + "/kind_pointer", "not a JSON Pointer: '%s'".formatted(kindPointer));
            }
            var kinds = required(events, pointer, "kinds");
            var members = Set.of("text", "tool_call", "tool_result", "file_changed", "turn");
            object(kinds, pointer + "/kinds", "kinds", members, members);
            var fields = Map.of(
                    "text", Set.of("match", "text"),
                    "tool_call", Set.of("match", "tool", "target"),
                    "tool_result", Set.of("match", "size", "is_error"),
                    "file_changed", Set.of("match", "path"),
                    "turn", Set.of("match"));
            for (var member : members) {
                var value = required(kinds, pointer + "/kinds", member);
                if (value.isNull()) {
                    continue;
                }
                var kindPointerPath = pointer + "/kinds/" + member;
                var allowed = fields.get(member);
                object(value, kindPointerPath, member, allowed, allowed);
                stringArray(required(value, kindPointerPath, "match"), kindPointerPath + "/match", "match", 0, 64,
                        false);
                for (var field : value.members().keySet()) {
                    var fieldPointer = kindPointerPath + "/" + field;
                    var fieldValue = value.members().get(field);
                    if ("match".equals(field)) {
                        continue;
                    }
                    if ("target".equals(field)) {
                        stringArray(fieldValue, fieldPointer, "target", 0, 4, false);
                    } else if ("is_error".equals(field) && fieldValue.isNull()) {
                        continue;
                    } else {
                        text(fieldValue, fieldPointer, field, 256);
                    }
                }
            }
        }

        private void endpoints(JsonNode endpoints, String pointer) {
            var elements = list(endpoints, pointer, "endpoints", 1, 64);
            for (var index = 0; index < elements.size(); index++) {
                var endpoint = text(elements.get(index), pointer + "/" + index, "endpoints[" + index + "]", 256);
                if (!ENDPOINT.matcher(endpoint).matches()) {
                    throw invalid(pointer + "/" + index, "not a host:port endpoint: '%s'".formatted(endpoint));
                }
            }
        }

        private void writablePaths(JsonNode paths, String pointer) {
            var elements = list(paths, pointer, "writable_paths", 0, 64);
            for (var index = 0; index < elements.size(); index++) {
                var path = text(elements.get(index), pointer + "/" + index, "writable_paths[" + index + "]", 256);
                if (!HOME_PATH.matcher(path).matches()) {
                    throw invalid(pointer + "/" + index, "not a home-relative path: '%s'".formatted(path));
                }
                noTraversal(path, pointer + "/" + index, "writable_paths");
                if (rules.harnessHomes().contains(path)) {
                    throw invalid(pointer + "/" + index,
                            "a harness home is never a writable path as a whole: '%s'".formatted(path));
                }
                for (var managed : rules.managedBundlePaths()) {
                    if (covers(path, managed)) {
                        throw invalid(pointer + "/" + index,
                                "the writable path '%s' covers the managed file '%s'".formatted(path, managed));
                    }
                }
            }
        }

        private void dataHome(JsonNode home, String pointer) {
            if (home.isNull()) {
                return;
            }
            object(home, pointer, "data_home", Set.of("variable", "login_file"), Set.of("variable", "login_file"));
            var variable = text(required(home, pointer, "variable"), pointer + "/variable", "variable", 64);
            if (!VARIABLE.matcher(variable).matches()) {
                throw invalid(pointer + "/variable", "not an environment variable name: '%s'".formatted(variable));
            }
            if (jobEnvironment(variable) || credentialName(variable)) {
                throw invalid(pointer + "/variable",
                        "the data home may not relocate a job or credential variable: '%s'".formatted(variable));
            }
            var loginFile = text(required(home, pointer, "login_file"), pointer + "/login_file", "login_file", 256);
            if (!RELATIVE_PATH.matcher(loginFile).matches()) {
                throw invalid(pointer + "/login_file", "not a relative path: '%s'".formatted(loginFile));
            }
            noTraversal(loginFile, pointer + "/login_file", "data_home.login_file");
        }

        private void resumability(JsonNode resumability, String pointer, String residue) {
            object(resumability, pointer, "resumability", Set.of("hold", "resume"),
                    Set.of("hold", "resume", "max_hold_seconds"));
            var hold = bool(required(resumability, pointer, "hold"), pointer + "/hold", "hold");
            var maxHold = optional(resumability, "max_hold_seconds");
            if (maxHold != null) {
                if (maxHold.isNull()) {
                    if (hold) {
                        throw invalid(pointer + "/max_hold_seconds", "a hold names its max_hold_seconds");
                    }
                } else {
                    integer(maxHold, pointer + "/max_hold_seconds", "max_hold_seconds", 60, 3600);
                }
            }
            var resume = optional(resumability, "resume");
            if (resume == null || resume.isNull()) {
                return;
            }
            if ("ephemeral".equals(residue)) {
                throw invalid(pointer + "/resume",
                        "a resume needs persisted session residue, not ephemeral");
            }
            var resumePointer = pointer + "/resume";
            object(resume, resumePointer, "resume", Set.of("argv", "session_id", "max_age_seconds"),
                    Set.of("argv", "session_id", "max_age_seconds"));
            var argv = required(resume, resumePointer, "argv");
            launchArguments(argv, resumePointer + "/argv", "resume.argv", SESSION_SLOTS);
            var occurrences = argv.elements().stream()
                    .filter(item -> item.text().orElse("").contains("{{session_id}}")).count();
            if (occurrences != 1) {
                throw invalid(resumePointer + "/argv",
                        "the resume argv holds {{session_id}} exactly once, not %d times".formatted(occurrences));
            }
            var sessionId = text(required(resume, resumePointer, "session_id"), resumePointer + "/session_id",
                    "session_id", 256);
            if (!sessionId.startsWith("/")) {
                throw invalid(resumePointer + "/session_id", "not a JSON Pointer: '%s'".formatted(sessionId));
            }
            integer(required(resume, resumePointer, "max_age_seconds"), resumePointer + "/max_age_seconds",
                    "max_age_seconds", 600, 604800);
        }

        private void connection(JsonNode connection, String pointer) {
            var members = Set.of("transport", "idle_timeout_seconds", "bounded_wait_seconds",
                    "progress_resets_timeout");
            object(connection, pointer, "connection", members, members);
            var transport = text(required(connection, pointer, "transport"), pointer + "/transport", "transport", 16);
            if (!"stdio".equals(transport)) {
                throw invalid(pointer + "/transport", "the transport must be 'stdio'");
            }
            var idle = integer(required(connection, pointer, "idle_timeout_seconds"),
                    pointer + "/idle_timeout_seconds", "idle_timeout_seconds", 45, 7200);
            var bounded = integer(required(connection, pointer, "bounded_wait_seconds"),
                    pointer + "/bounded_wait_seconds", "bounded_wait_seconds", 15, 3600);
            var progress = bool(required(connection, pointer, "progress_resets_timeout"),
                    pointer + "/progress_resets_timeout", "progress_resets_timeout");
            if (!progress && bounded >= idle) {
                throw invalid(pointer + "/bounded_wait_seconds",
                        "the bounded wait %d stays below the idle timeout %d without progress frames"
                                .formatted(bounded, idle));
            }
        }

        private void worker(JsonNode worker, String pointer, JsonNode profile) {
            var members = Set.of("eligible", "restricted_models", "ack_deadline_seconds", "ack_informational",
                    "silence_grace_seconds", "idle_wakeups");
            object(worker, pointer, "worker", members, members);
            var eligible = bool(required(worker, pointer, "eligible"), pointer + "/eligible", "eligible");
            var headless = profile.member("headless").orElse(JsonNode.nullValue());
            if (eligible && headless.isNull()) {
                throw invalid(pointer + "/eligible", "an eligible worker host has a headless block");
            }
            var restricted = optional(worker, "restricted_models");
            if (restricted != null && !restricted.isNull()) {
                stringArray(restricted, pointer + "/restricted_models", "restricted_models", 0, 64, true);
            }
            var ackDeadline = optional(worker, "ack_deadline_seconds");
            if (ackDeadline != null && !ackDeadline.isNull()) {
                integer(ackDeadline, pointer + "/ack_deadline_seconds", "ack_deadline_seconds", 1, 600);
            }
            var informational = optional(worker, "ack_informational");
            var informationalValue = informational != null && !informational.isNull()
                    && bool(informational, pointer + "/ack_informational", "ack_informational");
            var grace = optional(worker, "silence_grace_seconds");
            Long graceValue = null;
            if (grace != null && !grace.isNull()) {
                graceValue = integer(grace, pointer + "/silence_grace_seconds", "silence_grace_seconds", 5, 600);
            }
            var wakeups = optional(worker, "idle_wakeups");
            if (wakeups != null && !wakeups.isNull()) {
                integer(wakeups, pointer + "/idle_wakeups", "idle_wakeups", 1, 100);
            }
            if (informationalValue) {
                if (ackDeadline == null || ackDeadline.isNull() || graceValue == null) {
                    throw invalid(pointer + "/ack_deadline_seconds",
                            "an informational acknowledgement names its deadline and silence grace");
                }
                var connection = profile.member("connection").orElseThrow();
                var boundedNode = connection.member("bounded_wait_seconds").orElseThrow();
                var bounded = boundedNode.integerValue().orElseThrow();
                var deadline = ackDeadline.integerValue().orElseThrow();
                if (deadline != bounded + graceValue) {
                    throw invalid(pointer + "/ack_deadline_seconds",
                            "with an informational acknowledgement the deadline is the bounded wait %d plus the grace "
                                    + "%d, not %d".formatted(bounded, graceValue, deadline));
                }
            }
            if (!headless.isNull()) {
                var enforced = enforcedClass(headless);
                if (enforced && (restricted == null || restricted.isNull())) {
                    throw invalid(pointer + "/restricted_models",
                            "an enforced tool set names the models it accepts in restricted_models");
                }
                if (!enforced && restricted != null && !restricted.isNull()) {
                    throw invalid(pointer + "/restricted_models",
                            "restricted_models is non-null exactly when a class is enforced");
                }
                if (enforced) {
                    var tiers = headless.member("model_tiers").orElseThrow();
                    for (var tier : tiers.members().values()) {
                        tier.text().ifPresent(model -> {
                            if (!covered(model, restricted)) {
                                throw invalid(pointer + "/restricted_models",
                                        "the tier model '%s' appears in no entry of restricted_models".formatted(model));
                            }
                        });
                    }
                }
            }
        }

        private boolean enforcedClass(JsonNode headless) {
            var classes = headless.member("classes").orElse(JsonNode.nullValue());
            return classes.members().values().stream()
                    .filter(value -> !value.isNull())
                    .map(value -> value.member("tool_set").flatMap(JsonNode::text).orElse(""))
                    .anyMatch("enforced"::equals);
        }

        private boolean covered(String model, JsonNode restricted) {
            if (restricted == null || restricted.isNull()) {
                return false;
            }
            for (var entry : restricted.elements()) {
                var pattern = entry.text().orElse("");
                if ("*".equals(pattern) || pattern.equals(model)) {
                    return true;
                }
                if (pattern.endsWith("/*") && model.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            }
            return false;
        }

        private void detection(JsonNode detection, String pointer) {
            if (detection.isNull()) {
                return;
            }
            var members = Set.of("precedence", "primary_signals", "secondary_signals", "process_markers",
                    "ai_agent_prefixes", "reaches");
            object(detection, pointer, "detection", members, members);
            integer(required(detection, pointer, "precedence"), pointer + "/precedence", "precedence", 1,
                    Integer.MAX_VALUE);
            signals(required(detection, pointer, "primary_signals"), pointer + "/primary_signals", "primary_signals",
                    1);
            signals(required(detection, pointer, "secondary_signals"), pointer + "/secondary_signals",
                    "secondary_signals", 0);
            var markers = required(detection, pointer, "process_markers");
            stringArray(markers, pointer + "/process_markers", "process_markers", 0, 64, true);
            for (var index = 0; index < markers.elements().size(); index++) {
                var marker = markers.elements().get(index).text().orElse("");
                if (!VARIABLE.matcher(marker).matches()) {
                    throw invalid(pointer + "/process_markers/" + index,
                            "not a process marker variable: '%s'".formatted(marker));
                }
            }
            stringArray(required(detection, pointer, "ai_agent_prefixes"), pointer + "/ai_agent_prefixes",
                    "ai_agent_prefixes", 0, 64, true);
            var reaches = required(detection, pointer, "reaches");
            var values = list(reaches, pointer + "/reaches", "reaches", 1, 64);
            var seen = new HashSet<String>();
            for (var index = 0; index < values.size(); index++) {
                var value = enumValue(values.get(index), pointer + "/reaches/" + index, "reaches",
                        Set.of("command", "serve"));
                if (!seen.add(value)) {
                    throw invalid(pointer + "/reaches/" + index, "the value '%s' occurs twice".formatted(value));
                }
            }
        }

        private void signals(JsonNode signals, String pointer, String name, int min) {
            var elements = list(signals, pointer, name, min, 64);
            for (var index = 0; index < elements.size(); index++) {
                var signalPointer = pointer + "/" + index;
                object(elements.get(index), signalPointer, "signal", Set.of("variable", "equals"),
                        Set.of("variable", "equals"));
                var variable = text(required(elements.get(index), signalPointer, "variable"), signalPointer + "/variable",
                        "variable", 64);
                if (!VARIABLE.matcher(variable).matches()) {
                    throw invalid(signalPointer + "/variable", "not a signal variable: '%s'".formatted(variable));
                }
                var equals = required(elements.get(index), signalPointer, "equals");
                if (!equals.isNull()) {
                    text(equals, signalPointer + "/equals", "equals", 256);
                }
            }
        }

        private void identity(JsonNode identity, String pointer) {
            var members = Set.of("version_probe", "version_pattern");
            object(identity, pointer, "identity", members, members);
            var probe = required(identity, pointer, "version_probe");
            var pattern = required(identity, pointer, "version_pattern");
            if (probe.isNull() != pattern.isNull()) {
                throw invalid(pointer, "version_probe and version_pattern are both null or both present");
            }
            if (!probe.isNull()) {
                launchArguments(probe, pointer + "/version_probe", "version_probe", SLOTS);
            }
            if (!pattern.isNull()) {
                var regex = text(pattern, pointer + "/version_pattern", "version_pattern", 128);
                patternRule(regex, pointer + "/version_pattern");
            }
        }

        // ---- hardening rules ---------------------------------------------------------------------------------

        private void stringRule(String text, String pointer, boolean modelReadable) {
            if (!Normalizer.normalize(text, Normalizer.Form.NFC).equals(text)) {
                throw invalid(pointer, "not in Unicode normal form NFC");
            }
            for (var index = 0; index < text.length(); index++) {
                var character = text.charAt(index);
                if (Character.isISOControl(character)) {
                    throw invalid(pointer, "a control character is not allowed");
                }
                if (character == '\n' || character == '\r') {
                    throw invalid(pointer, "a string is a single line");
                }
                if (modelReadable && !Character.isLetterOrDigit(character)
                        && MODEL_READABLE_CHARACTERS.indexOf(character) < 0) {
                    throw invalid(pointer, "the character '%s' cannot be read by a model".formatted(character));
                }
            }
            for (var placeholder = PLACEHOLDER.matcher(text); placeholder.find(); ) {
                if (!SLOTS.contains(placeholder.group(1)) && !"session_id".equals(placeholder.group(1))
                        && !"uri".equals(placeholder.group(1))) {
                    throw invalid(pointer, "not a closed placeholder: '{{%s}}'".formatted(placeholder.group(1)));
                }
            }
        }

        private void toolName(String name, String pointer) {
            if (!TOOL_NAME.matcher(name).matches()) {
                throw invalid(pointer, "not a tool name: '%s'".formatted(name));
            }
        }

        private void launchArguments(JsonNode arguments, String pointer, String name, Set<String> slots) {
            var elements = list(arguments, pointer, name, 1, 128);
            for (var index = 0; index < elements.size(); index++) {
                var item = text(elements.get(index), pointer + "/" + index, name + "[" + index + "]", 1024);
                if (item.isEmpty()) {
                    throw invalid(pointer + "/" + index, "an argument is not empty");
                }
                launchItem(item, pointer + "/" + index, slots);
            }
        }

        private void launchItem(String item, String pointer, Set<String> slots) {
            if (item.contains("://") || item.startsWith("http:") || item.startsWith("https:")) {
                throw invalid(pointer, "an argument names a URL: '%s'".formatted(item));
            }
            if (item.startsWith("/")) {
                throw invalid(pointer, "an argument names an absolute path: '%s'".formatted(item));
            }
            if (item.startsWith("~/")) {
                throw invalid(pointer, "an argument names a home path: '%s'".formatted(item));
            }
            for (var placeholder = PLACEHOLDER.matcher(item); placeholder.find(); ) {
                if (!slots.contains(placeholder.group(1))) {
                    throw invalid(pointer, "not a closed slot: '{{%s}}'".formatted(placeholder.group(1)));
                }
            }
        }

        private void relativePath(String path, String pointer, String name) {
            if (path.startsWith("/") || path.startsWith("~/")) {
                throw invalid(pointer, "%s is relative: '%s'".formatted(name, path));
            }
            noTraversal(path, pointer, name);
        }

        private void noTraversal(String path, String pointer, String name) {
            if (Set.of(path.split("/")).contains("..")) {
                throw invalid(pointer, "%s does not traverse with '..': '%s'".formatted(name, path));
            }
            if (path.contains("//")) {
                throw invalid(pointer, "%s is normalized: '%s'".formatted(name, path));
            }
        }

        private boolean covers(String path, String managed) {
            return path.equals(managed) || managed.startsWith(path + "/") || path.startsWith(managed + "/");
        }

        private void patternRule(String regex, String pointer) {
            if (regex.length() > 128) {
                throw invalid(pointer, "a pattern is at most 128 characters");
            }
            int groups = 0;
            for (var index = 0; index < regex.length(); index++) {
                var character = regex.charAt(index);
                if (character == '\\') {
                    index++;
                    if (index < regex.length() && Character.isDigit(regex.charAt(index))) {
                        throw invalid(pointer, "a pattern uses no backreference");
                    }
                    continue;
                }
                if (character != '(') {
                    continue;
                }
                var rest = regex.substring(index + 1);
                if (rest.startsWith("?=") || rest.startsWith("?!") || rest.startsWith("?<=")
                        || rest.startsWith("?<!")) {
                    throw invalid(pointer, "a pattern uses no look-around");
                }
                if (!rest.startsWith("?")) {
                    groups++;
                }
            }
            if (groups != 1) {
                throw invalid(pointer, "a pattern has exactly one capture group, not %d".formatted(groups));
            }
        }

        private boolean jobEnvironment(String name) {
            return JOB_ENVIRONMENT.contains(name)
                    || JOB_ENVIRONMENT_PREFIXES.stream().anyMatch(name::startsWith);
        }

        private boolean credentialName(String name) {
            var upper = name.toUpperCase(Locale.ROOT);
            return CREDENTIAL_MARKERS.stream().anyMatch(upper::contains);
        }

        // ---- shape helpers -----------------------------------------------------------------------------------

        private void object(JsonNode node, String pointer, String name, Set<String> required, Set<String> allowed) {
            if (!node.isObject()) {
                throw invalid(pointer, "'%s' must be an object".formatted(name));
            }
            for (var member : node.members().keySet()) {
                if (!allowed.contains(member)) {
                    throw invalid(pointer + "/" + escape(member),
                            "the member '%s' is not declared by '%s'".formatted(member, name));
                }
            }
            for (var member : required) {
                if (!node.members().containsKey(member)) {
                    throw invalid(pointer + "/" + escape(member),
                            "the member '%s' of '%s' is missing".formatted(member, name));
                }
            }
        }

        private void object(JsonNode node, String pointer, String name) {
            if (!node.isObject()) {
                throw invalid(pointer, "'%s' must be an object".formatted(name));
            }
        }

        private JsonNode required(JsonNode node, String pointer, String member) {
            return node.member(member).orElseThrow(() -> invalid(pointer + "/" + escape(member),
                    "the member '%s' is missing".formatted(member)));
        }

        private JsonNode optional(JsonNode node, String member) {
            return node.member(member).orElse(null);
        }

        private String text(JsonNode node, String pointer, String name, int max) {
            var value = string(node, pointer, name, max);
            stringRule(value, pointer, false);
            return value;
        }

        private String string(JsonNode node, String pointer, String name, int max) {
            if (!node.isString()) {
                throw invalid(pointer, "'%s' must be a string".formatted(name));
            }
            var value = node.text().orElseThrow();
            if (value.length() > max) {
                throw invalid(pointer, "'%s' is at most %d characters".formatted(name, max));
            }
            return value;
        }

        private long integer(JsonNode node, String pointer, String name, long min, long max) {
            if (!node.isInteger()) {
                throw invalid(pointer, "'%s' must be an integer".formatted(name));
            }
            var value = node.integerValue().orElseThrow();
            if (value < min || value > max) {
                throw invalid(pointer, "'%s' must be between %d and %d, not %d".formatted(name, min, max, value));
            }
            return value;
        }

        private boolean bool(JsonNode node, String pointer, String name) {
            if (!node.isBoolean()) {
                throw invalid(pointer, "'%s' must be a boolean".formatted(name));
            }
            return node.booleanValue().orElseThrow();
        }

        private String enumValue(JsonNode node, String pointer, String name, Set<String> values) {
            var value = string(node, pointer, name, 256);
            if (!values.contains(value)) {
                throw invalid(pointer, "'%s' has the undeclared value '%s'".formatted(name, value));
            }
            return value;
        }

        private List<JsonNode> list(JsonNode node, String pointer, String name, int min, int max) {
            if (!node.isArray()) {
                throw invalid(pointer, "'%s' must be an array".formatted(name));
            }
            var elements = node.elements();
            if (elements.size() < min || elements.size() > max) {
                throw invalid(pointer, "'%s' has between %d and %d items, not %d".formatted(name, min, max,
                        elements.size()));
            }
            return elements;
        }

        private void stringArray(JsonNode node, String pointer, String name, int min, int max, boolean unique) {
            var elements = list(node, pointer, name, min, max);
            var seen = new HashSet<String>();
            for (var index = 0; index < elements.size(); index++) {
                var value = text(elements.get(index), pointer + "/" + index, name + "[" + index + "]", 1024);
                if (value.isEmpty()) {
                    throw invalid(pointer + "/" + index, "an item of '%s' is not empty".formatted(name));
                }
                if (unique && !seen.add(value)) {
                    throw invalid(pointer + "/" + index, "the item '%s' occurs twice".formatted(value));
                }
            }
        }

        private static String escape(String member) {
            return member.replace("~", "~0").replace("/", "~1");
        }

        private ProfileRefusal invalid(String pointer, String detail) {
            return ProfileRefusal.invalid(document, pointer, detail);
        }
    }
}
