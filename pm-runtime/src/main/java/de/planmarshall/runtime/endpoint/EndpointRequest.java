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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;

/**
 * The one HTTP request of a task on an endpoint harness (Client Profiles § The Request): the launch prompt of a
 * {@code zero_tool} worker as the system message, the task input as the user message, the model of the role's effective
 * configuration, streaming explicitly off, the role's result schema as the response format where the endpoint accepts
 * one, and no {@code tools} member and no tool choice.
 * <p>
 * Every request value the endpoint has a default for is sent explicitly, so no default of the serving program decides
 * how a task runs. The request is composed against the harness's context budget, {@code context_tokens} less
 * {@code answer_reserve_tokens}: a launch prompt that alone exceeds it makes the role artifact invalid on that harness,
 * and a task whose messages exceed it cannot be sent. The size is estimated at {@value #CHARS_PER_TOKEN} characters per
 * token, the compiled estimate of this package (ADR runtime-endpoint/004).
 *
 * @param model           the model of the role's effective configuration
 * @param system          the launch prompt of the worker
 * @param user            the task input
 * @param resultSchema    the role's result schema; {@code null} when the role declares none
 * @param schemaName      what the endpoint does with the schema; {@code null} when none is sent
 * @param maxAnswerTokens the answer reserve of the profile
 * @since 0.1
 */
public record EndpointRequest(String model, String system, String user, JsonNode resultSchema, String schemaName,
int maxAnswerTokens) {

    /** The compiled estimate of the requested tokens: one token per this many characters. */
    public static final int CHARS_PER_TOKEN = 4;

    /** Copies the values. */
    public EndpointRequest {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(system, "system");
        Objects.requireNonNull(user, "user");
    }

    /**
     * Composes the request of one task.
     *
     * @param profile      the endpoint profile
     * @param model        the model of the role's effective configuration
     * @param launchPrompt the launch prompt of a {@code zero_tool} worker
     * @param taskInput    the task input
     * @param resultSchema the role's result schema; {@code null} when the role declares none
     * @return the request, or why it cannot be composed
     */
    public static Composed compose(EndpointProfile profile, String model, String launchPrompt, String taskInput,
            JsonNode resultSchema) {
        Objects.requireNonNull(profile, "profile");
        var budget = profile.endpoint().contextBudgetTokens();
        if (estimate(launchPrompt) > budget) {
            return Composed.refused(Refusal.PROMPT_EXCEEDS_BUDGET,
                    "the launch prompt of %d tokens exceeds the context budget of %d on this endpoint"
                            .formatted(estimate(launchPrompt), budget));
        }
        if (estimate(launchPrompt) + estimate(taskInput) > budget) {
            return Composed.refused(Refusal.TASK_EXCEEDS_BUDGET,
                    "the task of %d tokens does not fit the context budget of %d on this endpoint"
                            .formatted(estimate(taskInput), budget));
        }
        var sends = resultSchema != null && profile.endpoint().sendsResultSchema();
        return Composed.composed(new EndpointRequest(model, launchPrompt, taskInput, sends ? resultSchema : null,
                sends ? profile.endpoint().resultSchema() : null, profile.endpoint().answerReserveTokens()));
    }

    /**
     * @param text a text
     * @return its estimated token count, at {@value #CHARS_PER_TOKEN} characters per token, rounded up
     */
    public static int estimate(String text) {
        return text == null ? 0 : (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }

    /**
     * @return the JSON body of the request: explicit values for every default of the serving program, no tool and no
     *         tool choice
     */
    public String body() {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("model", JsonNode.string(model));
        members.put("messages", JsonNode.array(List.of(message("system", system), message("user", user))));
        members.put("stream", JsonNode.bool(false));
        members.put("n", JsonNode.integer(1));
        members.put("temperature", JsonNode.integer(0));
        members.put("top_p", JsonNode.integer(1));
        members.put("presence_penalty", JsonNode.integer(0));
        members.put("frequency_penalty", JsonNode.integer(0));
        members.put("max_tokens", JsonNode.integer(maxAnswerTokens));
        if (resultSchema != null) {
            members.put("response_format", responseFormat());
        }
        return CanonicalJson.write(JsonNode.object(members));
    }

    /**
     * @param toolName the probe tool this stage declares
     * @return the body of the tool-call probe: the request, plus a {@code tools} member that declares one tool
     */
    public String bodyWithTools(String toolName) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("model", JsonNode.string(model));
        members.put("messages", JsonNode.array(List.of(message("system", system), message("user", user))));
        members.put("stream", JsonNode.bool(false));
        members.put("n", JsonNode.integer(1));
        members.put("temperature", JsonNode.integer(0));
        members.put("top_p", JsonNode.integer(1));
        members.put("presence_penalty", JsonNode.integer(0));
        members.put("frequency_penalty", JsonNode.integer(0));
        members.put("max_tokens", JsonNode.integer(maxAnswerTokens));
        var function = Map.of("name", JsonNode.string(toolName), "description",
                JsonNode.string("A probe tool the endpoint is never wired to"),
                "parameters", JsonNode.object(Map.of("type", JsonNode.string("object"), "properties",
                        JsonNode.object(Map.of()))));
        members.put("tools", JsonNode.array(List.of(JsonNode.object(Map.of("type", JsonNode.string("function"),
                "function", JsonNode.object(function))))));
        return CanonicalJson.write(JsonNode.object(members));
    }

    private JsonNode responseFormat() {
        var format = Map.of("name", JsonNode.string("result"), "schema", resultSchema, "strict",
                JsonNode.bool("enforced".equals(schemaName)));
        return JsonNode.object(Map.of("type", JsonNode.string("json_schema"), "json_schema", JsonNode.object(format)));
    }

    private static JsonNode message(String role, String content) {
        return JsonNode.object(Map.of("role", JsonNode.string(role), "content", JsonNode.string(content)));
    }

    /** Why a request cannot be composed. The set is closed. */
    public enum Refusal {
        /** The launch prompt alone exceeds the harness's context budget. */
        PROMPT_EXCEEDS_BUDGET("prompt_exceeds_context_budget"),
        /** The task input pushes the messages past the harness's context budget. */
        TASK_EXCEEDS_BUDGET("task_exceeds_context_budget");

        private final String wireName;

        Refusal(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the refusal in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The composition of one request: the request, or the refusal that ends it before it is sent.
     *
     * @param request the request; {@code null} when {@code refusal} is set
     * @param refusal why no request was composed; {@code null} when one was
     * @param detail  what happened, for a record
     * @since 0.1
     */
    public record Composed(EndpointRequest request, Refusal refusal, String detail) {

        static Composed composed(EndpointRequest request) {
            return new Composed(request, null, "composed");
        }

        static Composed refused(Refusal refusal, String detail) {
            return new Composed(null, refusal, detail);
        }

        /** @return whether a request was composed */
        public boolean isComposed() {
            return request != null;
        }
    }
}
