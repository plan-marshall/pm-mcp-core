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
 * The owner check runs once, before any request: where the program on the port cannot be read, the stages send nothing
 * and the draft is refused. The {@code identity} stage runs the version probe, reads the models listing and, where the
 * profile declares one, the weights identity of every allowed model. The {@code launch} stage checks readiness, sends the
 * base task with streaming off and with the result schema, and once without a schema. The {@code classes} stage sends a
 * task that asks for a tool call and records whether the endpoint answers with the decoy result the task names
 * ({@code tools_ignored}); the endpoint is never wired to a tool caller on that account. The {@code limits} stage bisects
 * the largest accepted request and reports the endpoint's own usage figure for it.
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
        var endpoint = URI.create(profile.endpoint().baseUrl());
        var lookup = owner.ownerOf(endpoint.getHost(), endpoint.getPort());
        if (!ownsTheNamedProgram(profile, lookup)) {
            // No request leaves the runtime, and no probe runs: the program on the port is not the one the draft names.
            return new ProfileStages.EndpointVerification(
                    new ProfileStages.EndpointIdentity(false, lookup.isFound() ? lookup.executablePath() : null, null,
                            List.of(), Map.of()),
                    new ProfileStages.EndpointLaunch(false, false, "none", false),
                    new ProfileStages.EndpointClasses(false, false), new ProfileStages.EndpointLimits(false, 0));
        }
        return new ProfileStages.EndpointVerification(identity(profile, lookup),
                launch(profile, model, baseTask, schema), classes(profile, model, toolTask, decoyResult),
                limits(profile, model, baseTask));
    }

    /**
     * @return whether the program that owns the socket is the one the draft's {@code executable} names
     */
    private static boolean ownsTheNamedProgram(EndpointProfile profile, SocketOwner.Lookup lookup) {
        if (!lookup.isFound() || profile.executable() == null) {
            return false;
        }
        return Path.of(lookup.executablePath()).getFileName().toString().equals(profile.executable());
    }

    private ProfileStages.EndpointIdentity identity(EndpointProfile profile, SocketOwner.Lookup lookup) {
        if (!lookup.isFound()) {
            return new ProfileStages.EndpointIdentity(false, null, null, List.of(), Map.of());
        }
        var version = probe(profile, lookup.executablePath());
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
        var passed = version.isPresent() && !models.isEmpty() && weightsSettled;
        return new ProfileStages.EndpointIdentity(passed, lookup.executablePath(), version.orElse(null), models,
                identities);
    }

    private ProfileStages.EndpointLaunch launch(EndpointProfile profile, String model, String baseTask,
            JsonNode schema) {
        var ready = ready(profile);
        var answer = content(profile, model, baseTask, schema);
        if (answer.isEmpty()) {
            // An endpoint that refuses a result schema is still a launchable endpoint: result_schema is none.
            answer = content(profile, model, baseTask, null);
        }
        var streamingOff = answer.isPresent();
        var measured = measureSchema(profile, model, baseTask);
        return new ProfileStages.EndpointLaunch(ready && answer.isPresent(), ready, measured, streamingOff);
    }

    private ProfileStages.EndpointClasses classes(EndpointProfile profile, String model, String toolTask,
            String decoyResult) {
        var request = new EndpointRequest(model, "", toolTask, null, null,
                profile.endpoint().answerReserveTokens());
        var response = client.post(profile, EndpointClient.CHAT_PATH, request.bodyWithTools(PROBE_TOOL));
        var body = response.body();
        var toolCall = body.contains("\"tool_calls\"") || body.contains("\"function_call\"");
        var ignored = decoyResult != null && !decoyResult.isEmpty() && body.contains(decoyResult);
        return new ProfileStages.EndpointClasses(toolCall, ignored);
    }

    /**
     * Bisects the largest accepted request past the profile's own budget, and reports the endpoint's own figure for it:
     * the probe is not composed against the budget, because the stage measures that budget.
     */
    private ProfileStages.EndpointLimits limits(EndpointProfile profile, String model, String baseTask) {
        var low = MINIMUM_CONTEXT_TOKENS;
        var high = MAXIMUM_CONTEXT_TOKENS;
        var best = 0;
        var probes = 0;
        while (low <= high && probes < MAXIMUM_BISECTION_PROBES) {
            var middle = low + (high - low) / 2;
            probes++;
            var request = new EndpointRequest(model, "", filler(baseTask, middle), null, null,
                    profile.endpoint().answerReserveTokens());
            var outcome = EndpointOutcome.accept(client.post(profile, EndpointClient.CHAT_PATH, request.body()), null);
            if (outcome.fate() == EndpointOutcome.Fate.ANSWERED) {
                var figure = outcome.usage().totalTokens().orElse(middle);
                best = (int) Math.min(MAXIMUM_CONTEXT_TOKENS, Math.max(best, Math.max(figure, middle)));
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return new ProfileStages.EndpointLimits(best >= MINIMUM_CONTEXT_TOKENS, best);
    }

    /**
     * @return the measurement of {@code result_schema}: {@code none} when the endpoint refuses a request that carries a
     *         schema, {@code enforced} when the contradicted schema still holds the answer, {@code advisory} otherwise
     */
    private String measureSchema(EndpointProfile profile, String model, String baseTask) {
        var contradicted = JsonNode.object(Map.of("type", JsonNode.string("object"), "required",
                JsonNode.array(List.of(JsonNode.string(CONTRADICTED_POINTER)))));
        var request = new EndpointRequest(model, "", baseTask, contradicted, profile.endpoint().resultSchema(),
                profile.endpoint().answerReserveTokens());
        var response = client.post(profile, EndpointClient.CHAT_PATH, request.body());
        if (response.failure() != EndpointClient.Failure.NONE || !response.successful()) {
            return "none";
        }
        var answered = contentOf(response);
        if (answered.isEmpty()) {
            return "advisory";
        }
        try {
            var document = JsonDocument.parse(answered.get().getBytes(StandardCharsets.UTF_8), "answer");
            return AnswerSchema.validate(contradicted, document).valid() ? "enforced" : "advisory";
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return "advisory";
        }
    }

    /**
     * @return the content of one answer, or empty when the endpoint did not answer, did not answer successfully, or
     *         answered with something that is not a JSON document
     */
    private Optional<String> content(EndpointProfile profile, String model, String task, JsonNode schema) {
        var request = new EndpointRequest(model, "", task, schema,
                schema == null ? null : profile.endpoint().resultSchema(), profile.endpoint().answerReserveTokens());
        return contentOf(client.post(profile, EndpointClient.CHAT_PATH, request.body()));
    }

    private static Optional<String> contentOf(EndpointClient.Response response) {
        if (response.failure() != EndpointClient.Failure.NONE || !response.successful()) {
            return Optional.empty();
        }
        try {
            var document = JsonDocument.parse(response.body().getBytes(StandardCharsets.UTF_8), "answer");
            var content = document.member("choices").filter(JsonNode::isArray)
                    .flatMap(choices -> choices.elements().stream().findFirst())
                    .flatMap(choice -> choice.member("message"))
                    .flatMap(message -> message.member("content"))
                    .flatMap(JsonNode::text);
            if (content.isEmpty() || content.get().isEmpty()) {
                return Optional.empty();
            }
            JsonDocument.parse(content.get().getBytes(StandardCharsets.UTF_8), "content");
            return content;
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return Optional.empty();
        }
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

    private static String filler(String baseTask, int tokens) {
        var characters = Math.max(0, tokens * EndpointRequest.CHARS_PER_TOKEN - baseTask.length());
        return baseTask + " " + "x".repeat(characters);
    }
}
