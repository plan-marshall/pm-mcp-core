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
 * The stages that verify a judgment endpoint (Client Profiles § Verification of a Judgment Endpoint): the stages of an
 * endpoint with the differences the protocol {@code system_one} states, decided from what the runtime observes.
 * <p>
 * The {@code identity} stage reads the models listing with the credential and checks that the role's model is listed; a
 * loopback form also runs the version probe and the owner check. The {@code launch} stage sends one typed request of two
 * options in both orders and checks that the answer validates, that its {@code model} equals the requested id and that
 * both orders name the same options; it sends a request whose criteria the instructions contradict and a request with
 * one option only and records the answers, and it refuses a service that answers free text, so the measured
 * {@code result_schema} is {@code enforced}. The {@code classes} stage does not apply: the server never sends tools. The
 * {@code limits} stage bisects between {@value #MINIMUM_CONTEXT_TOKENS} tokens and the service's refusal, counting with
 * {@code usage.input_tokens}; on a remote form only on the operator's explicit choice, because every request is billed.
 *
 * @since 0.1
 */
public final class JudgmentVerification {

    /** The first request of the {@code limits} bisection, in tokens. */
    public static final int MINIMUM_CONTEXT_TOKENS = 1024;

    /** The upper bound of the {@code limits} bisection, in tokens. */
    public static final int MAXIMUM_CONTEXT_TOKENS = 65_536;

    /** How many requests the {@code limits} bisection sends at most. */
    public static final int MAXIMUM_BISECTION_PROBES = 8;

    /** The question the {@code launch} and {@code limits} stages send. */
    public static final String PROBE_QUESTION = "probe";

    private static final String ENFORCED = "enforced";
    private static final String NONE = "none";
    private static final String VALID = "valid";
    private static final String INVALID = "invalid";

    private final JudgmentClient client;
    private final SocketOwner owner;
    private final WeightsReader weights;
    private final HarnessVerifier.VersionProbe versionProbe;

    /**
     * @param client       the one request of the protocol
     * @param owner        the reading of the listening socket's owner; unused for the remote form
     * @param weights      the reading of a model's weights identity
     * @param versionProbe the version probe of the serving program; unused for the remote form
     */
    public JudgmentVerification(JudgmentClient client, SocketOwner owner, WeightsReader weights,
            HarnessVerifier.VersionProbe versionProbe) {
        this.client = Objects.requireNonNull(client, "client");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.weights = Objects.requireNonNull(weights, "weights");
        this.versionProbe = Objects.requireNonNull(versionProbe, "versionProbe");
    }

    /**
     * Runs the stages of a judgment endpoint on a draft.
     *
     * @param profile       the judgment endpoint profile
     * @param model         the versioned model id of the role
     * @param bisectLimits  whether the {@code limits} stage may send its billed requests; the operator's explicit choice
     *                      on a remote form
     * @return the result of the stages
     */
    public ProfileStages.EndpointVerification verify(EndpointProfile profile, String model, boolean bisectLimits) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(model, "model");
        if (profile.endpoint().remote()) {
            return ProfileStages.EndpointVerification.judgment(identity(profile, model, null),
                    launch(profile, model), limits(profile, model, bisectLimits));
        }
        var endpoint = URI.create(profile.endpoint().baseUrl());
        var lookup = owner.ownerOf(endpoint.getHost(), endpoint.getPort());
        if (!ownsTheNamedProgram(profile, lookup)) {
            // No request leaves the runtime and no probe runs: the program on the port is not the one the draft names.
            return ProfileStages.EndpointVerification.judgment(
                    new ProfileStages.EndpointIdentity(false, lookup.isFound() ? lookup.executablePath() : null, null,
                            List.of(), Map.of()),
                    new ProfileStages.EndpointLaunch(false, false, NONE, false), new ProfileStages.EndpointLimits(false,
                            0));
        }
        return ProfileStages.EndpointVerification.judgment(identity(profile, model, lookup), launch(profile, model),
                limits(profile, model, bisectLimits));
    }

    private static boolean ownsTheNamedProgram(EndpointProfile profile, SocketOwner.Lookup lookup) {
        if (!lookup.isFound() || profile.executable() == null) {
            return false;
        }
        return Path.of(lookup.executablePath()).getFileName().toString().equals(profile.executable());
    }

    private ProfileStages.EndpointIdentity identity(EndpointProfile profile, String model,
            SocketOwner.Lookup lookup) {
        var loopback = !profile.endpoint().remote() && lookup != null;
        var executablePath = loopback ? lookup.executablePath() : null;
        var version = loopback ? probe(profile, executablePath) : Optional.<String>empty();
        var listing = listing(profile);
        var models = modelIds(listing);
        var listed = models.contains(model);
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
        var passed = listed && weightsSettled && (!loopback || version.isPresent());
        return new ProfileStages.EndpointIdentity(passed, executablePath, version.orElse(null), models, identities);
    }

    private ProfileStages.EndpointLaunch launch(EndpointProfile profile, String model) {
        var ready = client.get(profile, profile.endpoint().readinessOrModelsPath()).successful();
        var probes = new LinkedHashMap<String, String>();
        var ascending = probe(profile, model, probeState(), twoOptionsQuestion("Which option holds for the state?"),
                true);
        var descending = probe(profile, model, probeState(), twoOptionsQuestion("Which option holds for the state?"),
                false);
        probes.put("two_options_ascending", describe(ascending));
        probes.put("two_options_descending", describe(descending));
        var sameOptions = ascending.valid() && descending.valid()
                && sameOptions(ascending.answer(), descending.answer());
        probes.put("both_orders", sameOptions ? "same_options" : "differ");
        var contradicted = probe(profile, model, probeState(),
                twoOptionsQuestion("Choose the option that the criteria do not offer."), true);
        probes.put("contradicted_instructions", describe(contradicted));
        var oneOption = probe(profile, model, probeState(),
                question("Choose the only option.", Map.of("only", new SystemOneRequest.Criterion("Only",
                        "The only option of the question", null))), true);
        probes.put("one_option", describe(oneOption));
        var passed = ready && sameOptions;
        return new ProfileStages.EndpointLaunch(passed, ready, passed ? ENFORCED : NONE, ascending.valid(),
                probes);
    }

    private ProfileStages.EndpointLimits limits(EndpointProfile profile, String model, boolean bisectLimits) {
        if (!bisectLimits) {
            return new ProfileStages.EndpointLimits(false, 0);
        }
        var low = MINIMUM_CONTEXT_TOKENS;
        var high = MAXIMUM_CONTEXT_TOKENS;
        var best = 0;
        var sent = 0;
        while (low <= high && sent < MAXIMUM_BISECTION_PROBES) {
            var middle = low + (high - low) / 2;
            sent++;
            var read = probe(profile, model, fillerState(middle),
                    twoOptionsQuestion("Which option holds for the state?"), true);
            if (read.valid()) {
                var measured = read.answer().usage().inputTokens();
                var figure = measured.isMeasured() ? measured.value() : 0;
                best = Math.clamp(Math.max(best, Math.max(figure, middle)), 0, MAXIMUM_CONTEXT_TOKENS);
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        return new ProfileStages.EndpointLimits(best >= MINIMUM_CONTEXT_TOKENS, best);
    }

    private Probe probe(EndpointProfile profile, String model, JsonNode state, SystemOneRequest.Question question,
            boolean ascending) {
        var body = SystemOneRequest.body(model, state, List.of(question), ascending);
        var response = client.post(profile, SystemOneRequest.PATH, body);
        if (!response.successful()) {
            return new Probe(false, null);
        }
        var read = SystemOneAnswer.read(response.body(), model,
                Map.of(question.questionId(), question.ascendingKeys()));
        return new Probe(read.isValid(), read.answer());
    }

    private static boolean sameOptions(SystemOneAnswer.Answer left, SystemOneAnswer.Answer right) {
        var leftQuestion = left.answers().get(PROBE_QUESTION);
        var rightQuestion = right.answers().get(PROBE_QUESTION);
        return leftQuestion != null && rightQuestion != null
                && leftQuestion.probabilities().keySet().equals(rightQuestion.probabilities().keySet());
    }

    private static String describe(Probe probe) {
        return probe.valid() ? VALID : INVALID;
    }

    private static SystemOneRequest.Question twoOptionsQuestion(String instructions) {
        var criteria = new LinkedHashMap<String, SystemOneRequest.Criterion>();
        criteria.put("alpha", new SystemOneRequest.Criterion("Alpha", "The state holds the first option", null));
        criteria.put("beta", new SystemOneRequest.Criterion("Beta", "The state holds the second option", null));
        return question(instructions, criteria);
    }

    private static SystemOneRequest.Question question(String instructions,
            Map<String, SystemOneRequest.Criterion> criteria) {
        return new SystemOneRequest.Question(PROBE_QUESTION, instructions, criteria);
    }

    private static JsonNode probeState() {
        return JsonNode.object(Map.of("probe", JsonNode.bool(true)));
    }

    private static JsonNode fillerState(int tokens) {
        var characters = Math.max(0, tokens * EndpointRequest.CHARS_PER_TOKEN - 2_048);
        return JsonNode.object(Map.of("probe", JsonNode.string("x".repeat(characters))));
    }

    private Optional<String> probe(EndpointProfile profile, String executablePath) {
        var identity = profile.identity();
        if (identity == null || executablePath == null) {
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

    /** The reading of one probe request: whether it validated, and its answer. */
    private record Probe(boolean valid, SystemOneAnswer.Answer answer) {

        Probe {
            if (valid && answer == null) {
                throw new IllegalArgumentException("A valid probe carries its answer");
            }
        }
    }
}
