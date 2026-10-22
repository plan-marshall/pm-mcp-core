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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;

/**
 * Validates one role artifact against the closed schema of {@code model-work/01-roles-and-configuration.adoc#role-artifact}
 * (PM-WORK-1). An unknown member, a missing required member, {@code recycling} missing for {@code warm} or present for
 * {@code fresh}, an {@code off_track} threshold above the compiled one or below the signal's floor, and {@code questions}
 * or {@code off_track} on a {@code zero_tool} role are {@code schema_invalid}.
 * <p>
 * The validator reads the document as bounded, untrusted input through {@link JsonDocument}, so a document that is not
 * JSON, is too large or repeats a member is refused as well. What depends on the harness, the model catalogue or the
 * project's skills is not checked here ({@link RoleValidation}).
 *
 * @since 0.1
 */
public final class RoleArtifactValidator {

    /** The one format version a role artifact declares. */
    public static final int FORMAT_VERSION = 1;

    private static final Set<String> MEMBERS = Set.of("format_version", "name", "description", "class", "tools",
            "harness", "model", "effort", "form", "count", "skills", "recycling", "budget_floor_seconds",
            "context_budget_tokens", "questions", "off_track");

    private static final Set<String> REQUIRED = Set.of("format_version", "name", "description", "class", "tools",
            "harness", "model", "effort", "form", "count", "skills", "budget_floor_seconds");

    private static final Set<String> RECYCLING = Set.of("idle_wakeups", "token_budget", "tasks_per_generation");

    private static final Set<String> QUESTIONS = Set.of("hold_seconds", "resume_within_seconds");

    private static final Set<String> OFF_TRACK_SIGNALS = Set.of("repeating", "no_progress", "over_size",
            "outside_footprint");

    private static final Set<String> OFF_TRACK_MEMBERS = Set.of("threshold", "action");

    private static final Pattern ROLE_NAME = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z][a-z0-9-]{0,31}$");
    private static final Pattern TOOL = Pattern.compile("^[A-Za-z][A-Za-z0-9_.-]{0,63}$");
    private static final Pattern SKILL_URI = Pattern.compile(
            "^skill://pm/[a-z0-9][a-z0-9_-]*(/[a-z0-9][a-z0-9_-]*)*/SKILL\\.md$");

    private static final Map<String, RoleArtifact.OffTrackSignal> SIGNALS = signals();

    private RoleArtifactValidator() {
    }

    /**
     * @param document the artifact document, UTF-8 encoded
     * @param name     the role name, the file name without {@code .json}
     * @return the validated artifact
     * @throws RoleRefusal {@code schema_invalid} if the document breaks a rule of the schema
     */
    public static RoleArtifact validate(byte[] document, String name) {
        JsonNode tree;
        try {
            tree = JsonDocument.parse(document, name + ".json");
        } catch (ProfileRefusal refusal) {
            throw RoleRefusal.schemaInvalid(name, "", "the artifact cannot be read: " + refusal.detail());
        }
        return validate(tree, name);
    }

    /**
     * @param document the artifact tree
     * @param name     the role name, the file name without {@code .json}
     * @return the validated artifact
     * @throws RoleRefusal {@code schema_invalid} if the artifact breaks a rule of the schema
     */
    public static RoleArtifact validate(JsonNode document, String name) {
        return new Checker(name).check(document);
    }

    private static Map<String, RoleArtifact.OffTrackSignal> signals() {
        var signals = new LinkedHashMap<String, RoleArtifact.OffTrackSignal>();
        for (var signal : RoleArtifact.OffTrackSignal.values()) {
            signals.put(signal.wireName(), signal);
        }
        return Map.copyOf(signals);
    }

    /** The checks of one artifact. */
    private static final class Checker {

        private final String role;

        private Checker(String role) {
            this.role = role;
        }

        private RoleArtifact check(JsonNode artifact) {
            object(artifact, "", "role artifact", REQUIRED, MEMBERS);
            var formatVersion = (int) integer(required(artifact, "format_version"), "/format_version",
                    "format_version", FORMAT_VERSION, FORMAT_VERSION);
            var name = text(required(artifact, "name"), "/name", "name", 32);
            if (!ROLE_NAME.matcher(name).matches()) {
                throw invalid("/name", "not a role name: '%s'".formatted(name));
            }
            if (!name.equals(role)) {
                throw invalid("/name", "the artifact declares '%s', its file name is '%s'".formatted(name, role));
            }
            var description = text(required(artifact, "description"), "/description", "description", 240);
            if (description.isEmpty()) {
                throw invalid("/description", "a description is not empty");
            }
            var roleClass = roleClass(required(artifact, "class"));
            var tools = tools(required(artifact, "tools"));
            var harness = string(required(artifact, "harness"), "/harness", "harness", 32);
            if (!CLIENT_ID.matcher(harness).matches()) {
                throw invalid("/harness", "not a client id: '%s'".formatted(harness));
            }
            var model = text(required(artifact, "model"), "/model", "model", 128);
            if (model.isEmpty()) {
                throw invalid("/model", "a model is not empty");
            }
            var effort = effort(required(artifact, "effort"));
            var form = form(required(artifact, "form"));
            var count = (int) integer(required(artifact, "count"), "/count", "count", 1, 16);
            var skills = skills(required(artifact, "skills"));
            var recycling = recycling(optional(artifact, "recycling"), form);
            var budgetFloor = (int) integer(required(artifact, "budget_floor_seconds"), "/budget_floor_seconds",
                    "budget_floor_seconds", 60, 86_400);
            var contextBudget = contextBudget(optional(artifact, "context_budget_tokens"));
            var questions = questions(optional(artifact, "questions"), roleClass);
            var offTrack = offTrack(optional(artifact, "off_track"), roleClass);
            return new RoleArtifact(formatVersion, name, description, roleClass, tools, harness, model, effort, form,
                    count, skills, recycling, budgetFloor, contextBudget, questions, offTrack);
        }

        private RoleClass roleClass(JsonNode node) {
            var value = text(node, "/class", "class", 32);
            return RoleClass.fromWireName(value)
                    .orElseThrow(() -> invalid("/class", "not a role class: '%s'".formatted(value)));
        }

        private List<String> tools(JsonNode node) {
            var elements = list(node, "/tools", "tools", 0, 64);
            var seen = new HashSet<String>();
            var tools = new ArrayList<String>();
            for (var index = 0; index < elements.size(); index++) {
                var tool = string(elements.get(index), "/tools/" + index, "tools[" + index + "]", 64);
                if (!TOOL.matcher(tool).matches()) {
                    throw invalid("/tools/" + index, "not a tool name: '%s'".formatted(tool));
                }
                if (!seen.add(tool)) {
                    throw invalid("/tools/" + index, "the tool '%s' occurs twice".formatted(tool));
                }
                tools.add(tool);
            }
            return tools;
        }

        private EffortValue effort(JsonNode node) {
            if (node.isNull()) {
                return new EffortValue(null);
            }
            if (!node.isString()) {
                throw invalid("/effort", "an effort is a string or null");
            }
            return new EffortValue(string(node, "/effort", "effort", 64));
        }

        private RoleForm form(JsonNode node) {
            var value = text(node, "/form", "form", 16);
            for (var form : RoleForm.values()) {
                if (form.wireName().equals(value)) {
                    return form;
                }
            }
            throw invalid("/form", "not a form: '%s'".formatted(value));
        }

        private List<String> skills(JsonNode node) {
            var elements = list(node, "/skills", "skills", 1, 64);
            var seen = new HashSet<String>();
            var skills = new ArrayList<String>();
            for (var index = 0; index < elements.size(); index++) {
                var skill = string(elements.get(index), "/skills/" + index, "skills[" + index + "]", 256);
                if (!SKILL_URI.matcher(skill).matches()) {
                    throw invalid("/skills/" + index, "not a skill URI: '%s'".formatted(skill));
                }
                if (!seen.add(skill)) {
                    throw invalid("/skills/" + index, "the skill '%s' occurs twice".formatted(skill));
                }
                skills.add(skill);
            }
            return skills;
        }

        private RoleArtifact.Recycling recycling(JsonNode node, RoleForm form) {
            if (form == RoleForm.FRESH) {
                if (node != null) {
                    throw invalid("/recycling", "a fresh role has no recycling");
                }
                return null;
            }
            if (node == null) {
                throw invalid("/recycling", "a warm role names its recycling");
            }
            object(node, "/recycling", "recycling", RECYCLING, RECYCLING);
            var idleWakeups = nullableInteger(optional(node, "idle_wakeups"), "/recycling/idle_wakeups",
                    "idle_wakeups", 1, 100);
            var tokenBudget = (int) integer(required(node, "/recycling", "token_budget"), "/recycling/token_budget",
                    "token_budget", 10_000, Integer.MAX_VALUE);
            var tasksPerGeneration = nullableInteger(optional(node, "tasks_per_generation"),
                    "/recycling/tasks_per_generation", "tasks_per_generation", 1, Integer.MAX_VALUE);
            return new RoleArtifact.Recycling(idleWakeups, tokenBudget, tasksPerGeneration);
        }

        private Integer contextBudget(JsonNode node) {
            if (node == null) {
                return null;
            }
            return (int) integer(node, "/context_budget_tokens", "context_budget_tokens", 10_000, Integer.MAX_VALUE);
        }

        private RoleArtifact.Questions questions(JsonNode node, RoleClass roleClass) {
            if (node == null) {
                return null;
            }
            if (roleClass == RoleClass.ZERO_TOOL) {
                throw invalid("/questions", "a zero_tool role never asks, so it names no questions");
            }
            object(node, "/questions", "questions", QUESTIONS, QUESTIONS);
            var hold = (int) integer(required(node, "/questions", "hold_seconds"), "/questions/hold_seconds",
                    "hold_seconds", 0, 3_600);
            var resume = (int) integer(required(node, "/questions", "resume_within_seconds"),
                    "/questions/resume_within_seconds", "resume_within_seconds", 0, 604_800);
            return new RoleArtifact.Questions(hold, resume);
        }

        private Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack(JsonNode node,
                RoleClass roleClass) {
            if (node == null) {
                return Map.of();
            }
            if (roleClass == RoleClass.ZERO_TOOL) {
                throw invalid("/off_track", "a zero_tool role has no activity to watch, so it names no off_track");
            }
            object(node, "/off_track", "off_track", Set.of(), OFF_TRACK_SIGNALS);
            var settings = new LinkedHashMap<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting>();
            for (var member : node.members().entrySet()) {
                var signal = SIGNALS.get(member.getKey());
                var pointer = "/off_track/" + member.getKey();
                object(member.getValue(), pointer, member.getKey(), Set.of("action"), OFF_TRACK_MEMBERS);
                var action = action(required(member.getValue(), pointer, "action"), pointer + "/action");
                var threshold = threshold(optional(member.getValue(), "threshold"), pointer + "/threshold", signal);
                settings.put(signal, new RoleArtifact.OffTrackSetting(threshold, action));
            }
            return settings;
        }

        private RoleArtifact.OffTrackAction action(JsonNode node, String pointer) {
            var value = text(node, pointer, "action", 16);
            for (var action : RoleArtifact.OffTrackAction.values()) {
                if (action.wireName().equals(value)) {
                    return action;
                }
            }
            throw invalid(pointer, "not an off-track action: '%s'".formatted(value));
        }

        private Double threshold(JsonNode node, String pointer, RoleArtifact.OffTrackSignal signal) {
            if (node == null) {
                return null;
            }
            var value = number(node, pointer, "threshold");
            if (value <= 0) {
                throw invalid(pointer, "a threshold is above zero, not %s".formatted(value));
            }
            if (value > signal.compiledThreshold()) {
                throw invalid(pointer, "the threshold %s raises the compiled %s of '%s'"
                        .formatted(value, signal.compiledThreshold(), signal.wireName()));
            }
            if (value < signal.floor()) {
                throw invalid(pointer, "the threshold %s is below the floor %s of '%s'"
                        .formatted(value, signal.floor(), signal.wireName()));
            }
            return value;
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

        private JsonNode required(JsonNode node, String member) {
            return node.member(member).orElseThrow(() -> invalid("/" + escape(member),
                    "the member '%s' is missing".formatted(member)));
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
            stringRule(value, pointer);
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

        private Integer nullableInteger(JsonNode node, String pointer, String name, long min, long max) {
            if (node == null || node.isNull()) {
                return null;
            }
            return (int) integer(node, pointer, name, min, max);
        }

        private double number(JsonNode node, String pointer, String name) {
            if (!node.isNumber()) {
                throw invalid(pointer, "'%s' must be a number".formatted(name));
            }
            return node.decimalValue().orElseThrow().doubleValue();
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

        private void stringRule(String text, String pointer) {
            if (!Normalizer.normalize(text, Normalizer.Form.NFC).equals(text)) {
                throw invalid(pointer, "not in Unicode normal form NFC");
            }
            for (var index = 0; index < text.length(); index++) {
                var character = text.charAt(index);
                if (Character.isISOControl(character)) {
                    throw invalid(pointer, "a control character is not allowed");
                }
            }
        }

        private static String escape(String member) {
            return member.replace("~", "~0").replace("/", "~1");
        }

        private RoleRefusal invalid(String pointer, String detail) {
            return RoleRefusal.schemaInvalid(role, pointer, detail);
        }
    }
}
