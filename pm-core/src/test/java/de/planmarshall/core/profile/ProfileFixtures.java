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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The valid profile of the unit tests (test helper): one document that passes every rule, which a test mutates at one
 * JSON Pointer to make the loader refuse exactly that value. The base document is a generic harness, named by its data
 * only; no test fixture of production code names a client.
 */
final class ProfileFixtures {

    private ProfileFixtures() {
    }

    /** @return a profile that passes validation */
    static ProfileFixture valid() {
        return new ProfileFixture(base());
    }

    /** @return the valid profile as a document */
    static byte[] validBytes() {
        return valid().bytes();
    }

    private static Map<String, Object> base() {
        var root = new LinkedHashMap<String, Object>();
        root.put("schema_version", 1);
        root.put("client_id", "acme-cli");
        root.put("display_name", "Acme CLI");
        root.put("icon", null);
        root.put("min_daemon_version", "0.1.0");
        root.put("tools", tools());
        root.put("skill_loading", skillLoading());
        root.put("headless", headless());
        root.put("connection", connection());
        root.put("worker", worker());
        root.put("detection", detection());
        root.put("identity", identity());
        return root;
    }

    private static Map<String, Object> tools() {
        var tools = new LinkedHashMap<String, Object>();
        tools.put("read_file", "read_file");
        tools.put("write_file", "write_file");
        tools.put("edit_file", "edit_file");
        tools.put("search_code", "search_code");
        tools.put("find_files", "find_files");
        tools.put("run_shell", "run_shell");
        tools.put("ask_user", "ask_user");
        tools.put("idioms", new LinkedHashMap<String, Object>());
        return tools;
    }

    private static Map<String, Object> skillLoading() {
        var skill = new LinkedHashMap<String, Object>();
        skill.put("mode", "view_file");
        skill.put("load_directive_template", "pm_skill(uri=\"{{uri}}\")");
        skill.put("schema_residency", "on_demand");
        skill.put("skills_extension", false);
        return skill;
    }

    private static Map<String, Object> headless() {
        var headless = new LinkedHashMap<String, Object>();
        headless.put("executable", "acme");
        headless.put("install_locations", new ArrayList<>(List.of("~/.acme/bin")));
        headless.put("install_hint", "install.acme");
        headless.put("auth_probe", authProbe());
        headless.put("models_probe", null);
        headless.put("weights_probe", null);
        headless.put("model_tiers", modelTiers());
        headless.put("supported_versions", ">=1.0.0 <2.0.0");
        headless.put("update_evidence", updateEvidence());
        headless.put("agent_mode", "flags");
        headless.put("argv", new ArrayList<>(List.of("--task", "{{prompt_file}}")));
        headless.put("mcp_config", mcpConfig());
        headless.put("classes", classes());
        headless.put("env", new LinkedHashMap<String, Object>());
        headless.put("capabilities", new ArrayList<>(List.of("project_settings_control", "extension_isolation",
                "result_schema")));
        headless.put("output", output());
        headless.put("usage", usage());
        headless.put("events", null);
        headless.put("endpoints", new ArrayList<>(List.of("api.acme.test:443")));
        headless.put("writable_paths", new ArrayList<>(List.of("~/.acme/state")));
        headless.put("data_home", null);
        headless.put("tool_result_limit_chars", null);
        headless.put("resource_tools", new ArrayList<>());
        headless.put("session_residue", "persisted");
        headless.put("resumability", resumability());
        return headless;
    }

    private static Map<String, Object> authProbe() {
        var probe = new LinkedHashMap<String, Object>();
        probe.put("argv", new ArrayList<>(List.of("acme", "login", "--status")));
        probe.put("logged_in_exit_codes", new ArrayList<>(List.of(0)));
        probe.put("logged_out_exit_codes", new ArrayList<>(List.of(1)));
        probe.put("login_command", new ArrayList<>(List.of("acme", "login")));
        return probe;
    }

    private static Map<String, Object> modelTiers() {
        var tiers = new LinkedHashMap<String, Object>();
        tiers.put("frontier", "acme-frontier");
        tiers.put("standard", "acme-standard");
        tiers.put("fast", "acme-fast");
        return tiers;
    }

    private static Map<String, Object> updateEvidence() {
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("method", "none");
        evidence.put("signer", null);
        return evidence;
    }

    private static Map<String, Object> mcpConfig() {
        var config = new LinkedHashMap<String, Object>();
        config.put("delivery", "slot");
        config.put("path", null);
        config.put("exclusive", true);
        return config;
    }

    private static Map<String, Object> classes() {
        var classes = new LinkedHashMap<String, Object>();
        classes.put("writer", toolClass("write", "enforced"));
        classes.put("read_only", toolClass("read", "advisory"));
        classes.put("zero_tool", Map.of("args", new ArrayList<>(List.of("--mode", "zero"))));
        return classes;
    }

    private static Map<String, Object> toolClass(String mode, String toolSet) {
        var toolClass = new LinkedHashMap<String, Object>();
        toolClass.put("args", new ArrayList<>(List.of("--mode", mode)));
        toolClass.put("tool_set", toolSet);
        return toolClass;
    }

    private static Map<String, Object> output() {
        var output = new LinkedHashMap<String, Object>();
        output.put("format", "json");
        output.put("result_pointer", "/result");
        return output;
    }

    private static Map<String, Object> usage() {
        var usage = new LinkedHashMap<String, Object>();
        usage.put("input_tokens", "/usage/input_tokens");
        usage.put("output_tokens", "/usage/output_tokens");
        usage.put("cache_read", null);
        usage.put("cache_creation", "not_applicable");
        usage.put("reasoning", null);
        usage.put("cost_usd", null);
        usage.put("tool_uses", null);
        return usage;
    }

    private static Map<String, Object> resumability() {
        var resumability = new LinkedHashMap<String, Object>();
        resumability.put("hold", false);
        resumability.put("resume", null);
        return resumability;
    }

    private static Map<String, Object> connection() {
        var connection = new LinkedHashMap<String, Object>();
        connection.put("transport", "stdio");
        connection.put("idle_timeout_seconds", 1800);
        connection.put("bounded_wait_seconds", 240);
        connection.put("progress_resets_timeout", true);
        return connection;
    }

    private static Map<String, Object> worker() {
        var worker = new LinkedHashMap<String, Object>();
        worker.put("eligible", true);
        worker.put("restricted_models", new ArrayList<>(List.of("*")));
        worker.put("ack_deadline_seconds", null);
        worker.put("ack_informational", false);
        worker.put("silence_grace_seconds", 30);
        worker.put("idle_wakeups", 3);
        return worker;
    }

    private static Map<String, Object> detection() {
        var detection = new LinkedHashMap<String, Object>();
        detection.put("precedence", 9);
        detection.put("primary_signals", new ArrayList<>(List.of(signal("ACME_CLI"))));
        detection.put("secondary_signals", new ArrayList<>());
        detection.put("process_markers", new ArrayList<>());
        detection.put("ai_agent_prefixes", new ArrayList<>());
        detection.put("reaches", new ArrayList<>(List.of("command")));
        return detection;
    }

    private static Map<String, Object> signal(String variable) {
        var signal = new LinkedHashMap<String, Object>();
        signal.put("variable", variable);
        signal.put("equals", null);
        return signal;
    }

    private static Map<String, Object> identity() {
        var identity = new LinkedHashMap<String, Object>();
        identity.put("version_probe", new ArrayList<>(List.of("--version")));
        identity.put("version_pattern", "^acme ([0-9.]+)$");
        return identity;
    }

    /** A profile document a test mutates at one pointer. */
    static final class ProfileFixture {

        private final Map<String, Object> root;

        private ProfileFixture(Map<String, Object> root) {
            this.root = copy(root);
        }

        /**
         * @param pointer a JSON Pointer
         * @param value   the value to set; a {@code null} sets JSON null
         * @return this fixture
         */
        ProfileFixture set(String pointer, Object value) {
            var tokens = tokens(pointer);
            var parent = parent(tokens);
            var last = tokens.get(tokens.size() - 1);
            if (parent instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                var object = (Map<String, Object>) map;
                object.put(last, value);
            } else if (parent instanceof List<?> list) {
                @SuppressWarnings("unchecked")
                var elements = (List<Object>) list;
                elements.set(Integer.parseInt(last), value);
            }
            return this;
        }

        /**
         * @param pointer a JSON Pointer
         * @return this fixture without the value
         */
        ProfileFixture remove(String pointer) {
            var tokens = tokens(pointer);
            var parent = parent(tokens);
            var last = tokens.get(tokens.size() - 1);
            if (parent instanceof Map<?, ?> map) {
                map.remove(last);
            } else if (parent instanceof List<?> list) {
                list.remove(Integer.parseInt(last));
            }
            return this;
        }

        /** @return the document, UTF-8 encoded */
        byte[] bytes() {
            return CanonicalJson.write(tree()).getBytes(UTF_8);
        }

        /** @return the document tree */
        JsonNode tree() {
            return node(root);
        }

        private Object parent(List<String> tokens) {
            Object current = root;
            for (var index = 0; index < tokens.size() - 1; index++) {
                current = child(current, tokens.get(index));
            }
            return current;
        }

        private static Object child(Object current, String token) {
            if (current instanceof Map<?, ?> map) {
                return map.get(token);
            }
            if (current instanceof List<?> list) {
                return list.get(Integer.parseInt(token));
            }
            throw new IllegalArgumentException("No value at '" + token + "'");
        }
    }

    private static List<String> tokens(String pointer) {
        return new ArrayList<>(List.of(pointer.substring(1).split("/", -1)));
    }

    static JsonNode node(Object value) {
        if (value == null) {
            return JsonNode.nullValue();
        }
        if (value instanceof Map<?, ?> map) {
            var members = new LinkedHashMap<String, JsonNode>();
            for (var entry : map.entrySet()) {
                members.put(String.valueOf(entry.getKey()), node(entry.getValue()));
            }
            return JsonNode.object(members);
        }
        if (value instanceof List<?> list) {
            return JsonNode.array(list.stream().map(ProfileFixtures::node).toList());
        }
        if (value instanceof String text) {
            return JsonNode.string(text);
        }
        if (value instanceof Integer number) {
            return JsonNode.integer(number);
        }
        if (value instanceof Long number) {
            return JsonNode.integer(number);
        }
        if (value instanceof Boolean bool) {
            return JsonNode.bool(bool);
        }
        throw new IllegalArgumentException("Not a JSON value: " + value.getClass());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy(Map<String, Object> map) {
        var copy = new LinkedHashMap<String, Object>();
        for (var entry : map.entrySet()) {
            copy.put(entry.getKey(), copyValue(entry.getValue()));
        }
        return copy;
    }

    private static Object copyValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return copy((Map<String, Object>) map);
        }
        if (value instanceof List<?> list) {
            var copy = new ArrayList<Object>();
            for (var element : list) {
                copy.add(copyValue(element));
            }
            return copy;
        }
        return value;
    }
}
