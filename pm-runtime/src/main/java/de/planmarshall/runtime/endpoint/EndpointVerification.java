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

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.JsonPointer;
import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.runtime.launch.HarnessVerifier;

/**
 * The four stages that verify an endpoint draft (Client Profiles § Verification of an Endpoint): {@code identity},
 * {@code launch}, {@code classes} and {@code limits}, decided from what the runtime observes and never from the model's
 * statement.
 * <p>
 * The {@code identity} stage runs the version probe, finds the program that owns the listening socket, reads the models
 * listing and, where the profile declares one, the weights identity of every allowed model. The {@code launch} stage
 * checks readiness, sends the base task with streaming off and with the result schema — once with a schema the task text
 * contradicts and once without one — and settles {@code result_schema}. The {@code classes} stage sends a task that asks
 * for a tool call and records whether the endpoint answers with the decoy result the task names
 * ({@code tools_ignored}); the endpoint is never wired to a tool caller on that account. The {@code limits} stage
 * bisects the largest accepted request with the endpoint's own usage figures.
 *
 * @since 0.1
 */
public final class EndpointVerification {

    /** The first request of the {@code limits} bisection, in tokens. */
    public static final int MINIMUM_CONTEXT_TOKENS = 1024;

    /** The upper bound of the {@code limits} bisection, in tokens. */
    public static final int MAXIMUM_CONTEXT_TOKENS = 65_536;

    /** How many requests the {@code limits} bisection sends at most. */
    public static final int MAXIMUM_BISECTION_PROBES = 8;

    /** The name of the probe tool the {@code classes} stage declares. */
    public static final String PROBE_TOOL = "probe_tool";

    private static final String CONTRADICTED_POINTER = "__contradicted__";

    private final EndpointClient client;
    private final SocketOwner owner;
    private final WeightsReader weights;
    private final HarnessVerifier.VersionProbe versionProbe;

    /**
     * @param client       the HTTP client of the endpoint
     * @param owner        the reading of the listening socket's owner
     * @param weights      the reading of a model's weights identity
     * @param versionProbe the version probe of the serving program
     */
    public EndpointVerification(EndpointClient client, SocketOwner owner, WeightsReader weights,
            HarnessVerifier.VersionProbe versionProbe) {
        this.client = Objects.requireNonNull(client, "client");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.weights = Objects.requireNonNull(weights, "weights");
        this.versionProbe = Objects.requireNonNull(versionProbe, "versionProbe");
    }

    /**
     * Runs the four stages on a draft.
     *
     * @param profile     the endpoint profile
     * @param model       the model the launch stages run with
     * @param baseTask    a base task of the role
     * @param toolTask    the task text that asks for a tool call and names its decoy result
     * @param decoyResult the decoy result the tool task names
     * @param schema      the role's result schema; {@code null} when the role declares none
     * @return the result of the four stages
     */
    public ProfileStages.EndpointVerification verify(EndpointProfile profile, String model, String baseTask,
            String toolTask, String decoyResult, JsonNode schema) {
        var identity = identity(profile, model);
        var launch = launch(profile, model, baseTask, schema);
        var classes = classes(profile, model, toolTask, decoyResult);
        var limits = limits(profile, model, baseTask);
        return new ProfileStages.EndpointVerification(identity, launch, classes, limits);
    }

    private ProfileStages.EndpointIdentity identity(EndpointProfile profile, String model) {
        var endpoint = URI.create(profile.endpoint().baseUrl());
        var lookup = owner.ownerOf(endpoint.getHost(), endpoint.getPort());
        var version = lookup.isFound() ? probe(profile, lookup.executablePath()) : Optional.<String>empty();
        var listing = listing(profile);
        var models = modelIds(listing);
        var identities = new LinkedHashMap<String, String>();
        var weightsSettled = true;
        if (profile.weightsProbe() != null) {
            for (var allowed : allowedModels(profile, models)) {
                var reading = weights.read(profile, allowed, listing);
                if (reading.isRead()) {
                    identities.put(allowed, reading.weightsId());
                } else {
                    weightsSettled = false;
                }
            }
        }
        var passed = lookup.isFound() && version.isPresent() && !models.isEmpty() && weightsSettled;
        return new ProfileStages.EndpointIdentity(passed, lookup.isFound() ? lookup.executablePath() : null,
                version.orElse(null), models, identities);
    }

    private ProfileStages.EndpointLaunch launch(EndpointProfile profile, String model, String baseTask,
            JsonNode schema) {
        var ready = ready(profile);
        var composed = EndpointRequest.compose(profile, model, "", baseTask, schema);
        var response = composed.isComposed()
                ? client.post(profile, EndpointClient.CHAT_PATH, composed.request().body())
                : new EndpointClient.Response(0, "", EndpointClient.Failure.UNREACHABLE, composed.detail());
        var outcome = EndpointOutcome.accept(response, schema);
        var streamingOff = response.failure() == EndpointClient.Failure.NONE && !eventStream(response.body());
        var measured = measureSchema(profile, model, baseTask);
        return new ProfileStages.EndpointLaunch(ready && outcome.fate() == EndpointOutcome.Fate.ANSWERED, ready, measured,
                streamingOff);
    }

    private static boolean eventStream(String body) {
        var trimmed = body == null ? "" : body.stripLeading();
        return trimmed.startsWith("data:") || trimmed.contains("\ndata:");
    }

    private ProfileStages.EndpointClasses classes(EndpointProfile profile, String model, String toolTask,
            String decoyResult) {
        var composed = EndpointRequest.compose(profile, model, "", toolTask, null);
        if (!composed.isComposed()) {
            return new ProfileStages.EndpointClasses(false, false);
        }
        var response = client.post(profile, EndpointClient.CHAT_PATH, composed.request().bodyWithTools(PROBE_TOOL));
        var body = response.body();
        var toolCall = body.contains("\"tool_calls\"") || body.contains("\"function_call\"");
        var ignored = decoyResult != null && !decoyResult.isEmpty() && body.contains(decoyResult);
        return new ProfileStages.EndpointClasses(toolCall, ignored);
    }

    private ProfileStages.EndpointLimits limits(EndpointProfile profile, String model, String baseTask) {
        var low = MINIMUM_CONTEXT_TOKENS;
        var high = Math.min(MAXIMUM_CONTEXT_TOKENS, Math.max(profile.endpoint().contextTokens() * 2,
                MINIMUM_CONTEXT_TOKENS));
        var best = 0;
        var probes = 0;
        while (low <= high && probes < MAXIMUM_BISECTION_PROBES) {
            var middle = low + (high - low) / 2;
            probes++;
            if (accepted(profile, model, filler(baseTask, middle), null)) {
                best = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return new ProfileStages.EndpointLimits(best >= MINIMUM_CONTEXT_TOKENS, best);
    }

    private String measureSchema(EndpointProfile profile, String model, String baseTask) {
        var contradicted = JsonNode.object(Map.of("type", JsonNode.string("object"), "required",
                JsonNode.array(List.of(JsonNode.string(CONTRADICTED_POINTER)))));
        var composed = EndpointRequest.compose(profile, model, "", baseTask, contradicted);
        if (!composed.isComposed()) {
            return "none";
        }
        var response = client.post(profile, EndpointClient.CHAT_PATH, composed.request().body());
        if (response.failure() == EndpointClient.Failure.TIMEOUT
                || response.failure() == EndpointClient.Failure.UNREACHABLE || !response.successful()) {
            return "none";
        }
        var content = content(response.body());
        if (content.isEmpty()) {
            return "advisory";
        }
        try {
            var document = JsonDocument.parse(content.get().getBytes(StandardCharsets.UTF_8), "answer");
            return AnswerSchema.validate(contradicted, document).valid() ? "enforced" : "advisory";
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return "advisory";
        }
    }

    private boolean accepted(EndpointProfile profile, String model, String task, JsonNode schema) {
        var composed = EndpointRequest.compose(profile, model, "", task, schema);
        if (!composed.isComposed()) {
            return false;
        }
        return EndpointOutcome.accept(client.post(profile, EndpointClient.CHAT_PATH, composed.request().body()), schema)
                .fate() == EndpointOutcome.Fate.ANSWERED;
    }

    private boolean ready(EndpointProfile profile) {
        return client.get(profile, profile.endpoint().readinessOrModelsPath()).successful();
    }

    private Optional<String> probe(EndpointProfile profile, String executablePath) {
        var identity = profile.identity();
        if (identity == null) {
            return Optional.empty();
        }
        return versionProbe.version(Path.of(executablePath), identity.versionProbe(), identity.versionPattern());
    }

    private JsonNode listing(EndpointProfile profile) {
        var response = client.get(profile, profile.endpoint().modelsPath());
        if (!response.successful()) {
            return JsonNode.nullValue();
        }
        try {
            var document = JsonDocument.parse(response.body().getBytes(StandardCharsets.UTF_8), "models");
            return JsonPointer.resolve(document, profile.endpoint().modelsPointer(), "models listing");
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return JsonNode.nullValue();
        }
    }

    private static List<String> modelIds(JsonNode listing) {
        if (!listing.isArray()) {
            return List.of();
        }
        return listing.elements().stream().map(element -> element.member("id").flatMap(JsonNode::text).orElse(""))
                .filter(id -> !id.isEmpty()).toList();
    }

    private static List<String> allowedModels(EndpointProfile profile, List<String> models) {
        var tiers = new LinkedHashSet<>(profile.modelTiers().values());
        return tiers.isEmpty() ? models : List.copyOf(tiers);
    }

    private static Optional<String> content(String body) {
        try {
            var answer = JsonDocument.parse(body.getBytes(StandardCharsets.UTF_8), "answer");
            return answer.member("choices").filter(JsonNode::isArray)
                    .flatMap(choices -> choices.elements().stream().findFirst())
                    .flatMap(choice -> choice.member("message"))
                    .flatMap(message -> message.member("content"))
                    .flatMap(JsonNode::text);
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return Optional.empty();
        }
    }

    private static String filler(String baseTask, int tokens) {
        var characters = Math.max(0, tokens * EndpointRequest.CHARS_PER_TOKEN - baseTask.length());
        return baseTask + " " + "x".repeat(characters);
    }
}
