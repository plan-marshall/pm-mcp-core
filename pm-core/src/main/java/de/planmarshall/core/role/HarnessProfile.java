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

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileValidator;

/**
 * The facts of a validated client profile that role validation reads (PM-CLIENT-2, Client Profile Schema): the worker
 * eligibility, the models that accept a restricted tool set, the classes the harness runs, the effort values per model,
 * the model tiers, the context-budget fallback, the idle wakeups, the resumability and the tool that asks the user.
 * <p>
 * The projection keeps {@code core.role} independent of the shape of the profile document: the port of the effective
 * configuration supplies it, and the production wiring projects a {@link LoadedProfile} through {@link #of(LoadedProfile)}.
 *
 * @param clientId                the client id of the harness
 * @param eligible                whether the harness may host workers
 * @param restrictedModelsDeclared whether {@code worker.restricted_models} is non-null
 * @param restrictedModels        the models that accept an enforced tool set; empty when not declared
 * @param classes                 the tool set of each class the harness runs; a class absent is not runnable
 * @param modelEfforts            the effort values per model; the key {@code *} applies to every other model
 * @param modelTiers              the model of each tier of the default assignment
 * @param contextBudgetTokens     the harness fallback of the worker's context budget; {@code null} when absent
 * @param idleWakeups             the host's idle wakeups; {@code null} when absent
 * @param resumability            whether a worker can continue after a question
 * @param askUserTool             the harness tool that asks the user, which no role may name
 * @since 0.1
 */
public record HarnessProfile(String clientId, boolean eligible, boolean restrictedModelsDeclared,
Set<String> restrictedModels, Map<RoleClass, ToolSet> classes, Map<String, List<String>> modelEfforts,
Map<String, String> modelTiers, @Nullable Integer contextBudgetTokens, @Nullable Integer idleWakeups,
Resumability resumability, String askUserTool) {

    /** Copies the collections. */
    public HarnessProfile {
        restrictedModels = Set.copyOf(restrictedModels);
        classes = Collections.unmodifiableMap(new LinkedHashMap<>(classes));
        var efforts = new LinkedHashMap<String, List<String>>();
        modelEfforts.forEach((model, values) -> efforts.put(model, List.copyOf(values)));
        modelEfforts = Collections.unmodifiableMap(efforts);
        modelTiers = Collections.unmodifiableMap(new LinkedHashMap<>(modelTiers));
    }

    /** How far the harness holds a worker to the tools of its role. */
    public enum ToolSet {
        /** The harness limits the worker to the tools of its role. */
        ENFORCED,
        /** The harness cannot limit a worker's tools; the role's tool set only tells it which to use. */
        ADVISORY
    }

    /**
     * Whether a worker of the harness can continue after its question (PM-CLIENT-2, Resumability).
     *
     * @param hold                 whether a worker holds its task in {@code pm_wait} until the answer
     * @param maxHoldSeconds       the longest hold measured on the harness; {@code null} when hold is false
     * @param resume               whether a worker ends with its session stored and is resumed with the answer
     * @param maxResumeAgeSeconds  the oldest stored session measured to resume; {@code null} when resume is false
     * @since 0.1
     */
    public record Resumability(boolean hold, @Nullable Integer maxHoldSeconds, boolean resume,
    @Nullable Integer maxResumeAgeSeconds) {
    }

    /** @return whether the harness runs the class */
    public boolean runs(RoleClass roleClass) {
        return classes.containsKey(roleClass);
    }

    /**
     * @param roleClass a role class
     * @return the tool set of the class, or empty when the harness does not run the class
     */
    public Optional<ToolSet> toolSet(RoleClass roleClass) {
        return Optional.ofNullable(classes.get(roleClass));
    }

    /** @return whether the harness runs no class at all */
    public boolean runsNoClass() {
        return classes.isEmpty();
    }

    /**
     * @param model a model identifier
     * @return whether an entry of the declared restricted models matches the model, exactly or by a {@code *} glob
     */
    public boolean allowsRestrictedModel(String model) {
        for (var pattern : restrictedModels) {
            if ("*".equals(pattern) || pattern.equals(model)) {
                return true;
            }
            if (pattern.endsWith("/*") && model.startsWith(pattern.substring(0, pattern.length() - 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param model a model identifier
     * @return the effort values the harness accepts for the model, in ascending order
     */
    public List<String> efforts(String model) {
        var values = modelEfforts.get(model);
        if (values != null) {
            return values;
        }
        return modelEfforts.getOrDefault("*", List.of());
    }

    /**
     * @param tier a tier of the closed list {@code frontier}, {@code standard}, {@code fast}
     * @return the model the harness maps the tier to
     */
    public Optional<String> tierModel(String tier) {
        return Optional.ofNullable(modelTiers.get(tier));
    }

    /**
     * @param profile a validated profile
     * @return the facts a role reads from it
     */
    public static HarnessProfile of(LoadedProfile profile) {
        var document = profile.document();
        if (ProfileValidator.isEndpoint(document)) {
            return endpoint(profile, document);
        }
        var worker = document.member("worker").orElse(JsonNode.nullValue());
        var headless = document.member("headless").orElse(JsonNode.nullValue());
        var classes = classes(headless);
        var restricted = worker.member("restricted_models").orElse(null);
        var declared = restricted != null && !restricted.isNull();
        var restrictedModels = declared
                ? restricted.elements().stream().map(value -> value.text().orElse("")).filter(text -> !text.isEmpty())
                .collect(Collectors.toUnmodifiableSet())
                : Set.<String>of();
        return new HarnessProfile(profile.clientId(),
                worker.member("eligible").flatMap(JsonNode::booleanValue).orElse(false),
                declared, restrictedModels, classes, efforts(headless), tiers(headless),
                headless.isNull() ? null : integer(headless, "context_budget_tokens"),
                worker.member("idle_wakeups").filter(JsonNode::isInteger).map(value -> (int) value
                        .integerValue().orElse(0)).orElse(null),
                resumability(headless),
                document.member("tools").flatMap(tools -> tools.member("ask_user")).flatMap(JsonNode::text).orElse(""));
    }

    /**
     * The facts of an endpoint profile (Client Profiles § A Model on This Machine): the harness runs the role class
     * {@code zero_tool} alone, has no restricted models and no resumability, and its context budget is the endpoint's
     * {@code context_tokens} less its {@code answer_reserve_tokens}.
     *
     * @param profile  the loaded profile
     * @param document its validated document
     * @return the facts a role reads from it
     */
    private static HarnessProfile endpoint(LoadedProfile profile, JsonNode document) {
        var endpoint = EndpointProfile.of(document);
        return new HarnessProfile(profile.clientId(), true, false, Set.of(),
                Map.of(RoleClass.ZERO_TOOL, ToolSet.ENFORCED), Map.of(), endpoint.modelTiers(),
                endpoint.endpoint().contextBudgetTokens(), null, new Resumability(false, null, false, null), "");
    }

    private static Map<RoleClass, ToolSet> classes(JsonNode headless) {
        if (headless.isNull()) {
            return Map.of();
        }
        var declared = headless.member("classes").orElse(JsonNode.nullValue());
        var classes = new LinkedHashMap<RoleClass, ToolSet>();
        for (var roleClass : List.of(RoleClass.WRITER, RoleClass.READ_ONLY)) {
            declared.member(roleClass.wireName()).filter(value -> !value.isNull()).ifPresent(value -> value
                    .member("tool_set").flatMap(JsonNode::text)
                    .ifPresent(toolSet -> classes.put(roleClass, "enforced".equals(toolSet)
                            ? ToolSet.ENFORCED
                            : ToolSet.ADVISORY)));
        }
        var zeroTool = declared.member("zero_tool").orElse(JsonNode.nullValue());
        if (!zeroTool.isNull()) {
            classes.put(RoleClass.ZERO_TOOL, ToolSet.ENFORCED);
        }
        return classes;
    }

    private static Map<String, List<String>> efforts(JsonNode headless) {
        if (headless.isNull()) {
            return Map.of();
        }
        var declared = headless.member("model_efforts").orElse(null);
        if (declared == null || declared.isNull()) {
            return Map.of();
        }
        var efforts = new LinkedHashMap<String, List<String>>();
        for (var member : declared.members().entrySet()) {
            efforts.put(member.getKey(), member.getValue().elements().stream().map(value -> value.text().orElse(""))
                    .toList());
        }
        return efforts;
    }

    private static Map<String, String> tiers(JsonNode headless) {
        if (headless.isNull()) {
            return Map.of();
        }
        var declared = headless.member("model_tiers").orElse(JsonNode.nullValue());
        var tiers = new LinkedHashMap<String, String>();
        for (var member : declared.members().entrySet()) {
            member.getValue().text().ifPresent(model -> tiers.put(member.getKey(), model));
        }
        return tiers;
    }

    private static Resumability resumability(JsonNode headless) {
        if (headless.isNull()) {
            return new Resumability(false, null, false, null);
        }
        var declared = headless.member("resumability").orElse(JsonNode.nullValue());
        var hold = declared.member("hold").flatMap(JsonNode::booleanValue).orElse(false);
        var maxHold = integer(declared, "max_hold_seconds");
        var resume = declared.member("resume").orElse(JsonNode.nullValue());
        var canResume = !resume.isNull();
        var maxResume = canResume ? integer(resume, "max_age_seconds") : null;
        return new Resumability(hold, maxHold, canResume, maxResume);
    }

    private static Integer integer(JsonNode node, String member) {
        return node.member(member).filter(JsonNode::isInteger).map(value -> (int) value.integerValue()
                .orElse(0)).orElse(null);
    }
}
