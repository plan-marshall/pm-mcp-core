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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The validated values of an endpoint profile (PM-CLIENT-2, Client Profiles § Endpoint Profile Schema): the profile of
 * a model this machine serves on the loopback interface, or of the remote form of the protocol {@code system_one} that
 * one hosted service answers (Client Profiles § Remote Form).
 * <p>
 * The projection reads what the runtime needs and nothing else: the serving program and its identity, the endpoint
 * block with its request settings, the model tiers and the weights probe. It is made from a document
 * {@link ProfileValidator} has accepted, so it never re-states a rule; a document that is not an endpoint profile is
 * refused with {@link IllegalArgumentException}, because the caller selected the wrong kind.
 *
 * @param clientId       the client id, in the namespace of the client profiles
 * @param executable     the bare name of the serving program; {@code null} in the remote form
 * @param identity       the version probe of the serving program; {@code null} in the remote form
 * @param updateEvidence the evidence a changed program must pass; {@code null} when the profile declares none
 * @param endpoint       the endpoint block
 * @param modelTiers     the model of each tier of the default assignment
 * @param weightsProbe   how the identity of the model's weights is read; {@code null} when the harness reports none
 * @since 0.1
 */
public record EndpointProfile(String clientId, String executable, Identity identity, UpdateEvidence updateEvidence,
Endpoint endpoint, Map<String, String> modelTiers, WeightsProbe weightsProbe) {

    /** The member of the document that carries the weights probe, on either kind of profile. */
    private static final String WEIGHTS_PROBE = "weights_probe";

    /** Copies the tiers. */
    public EndpointProfile {
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(endpoint, "endpoint");
        modelTiers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(modelTiers, "modelTiers")));
    }

    /**
     * The version probe of the serving program.
     *
     * @param versionProbe   the argument vector, run without a shell
     * @param versionPattern the pattern whose first group is the version
     * @since 0.1
     */
    public record Identity(List<String> versionProbe, String versionPattern) {

        /** Copies the argument vector. */
        public Identity {
            versionProbe = List.copyOf(Objects.requireNonNull(versionProbe, "versionProbe"));
            Objects.requireNonNull(versionPattern, "versionPattern");
        }
    }

    /**
     * The evidence a changed serving program must pass before it runs.
     *
     * @param method {@code code_signature}, {@code package_provenance} or {@code none}
     * @param signer the signer the method names; {@code null} for {@code none}
     * @since 0.1
     */
    public record UpdateEvidence(String method, String signer) {

        /** @return whether a changed program can pass this evidence at all */
        public boolean verifiable() {
            return !"none".equals(method);
        }
    }

    /**
     * The {@code endpoint} block of the profile.
     *
     * @param protocol              {@code openai_chat} or {@code system_one}
     * @param locality              {@code loopback} or {@code remote}
     * @param baseUrl               the origin of the endpoint
     * @param readinessPath         the readiness path; {@code null} when the models path is the readiness check
     * @param modelsPath            the path of the models listing
     * @param modelsPointer         the JSON Pointer to the model array in the listing
     * @param resultSchema          what the endpoint does with a result schema: {@code enforced}, {@code advisory} or
     *                              {@code none}
     * @param contextTokens         the largest request the endpoint accepts
     * @param answerReserveTokens   the part of {@code contextTokens} kept for the answer
     * @param requestTimeoutSeconds the timeout of one request
     * @param credentialKey         the Token Lane credential entry a {@code remote} endpoint reads; {@code null} in the
     *                              loopback form
     * @since 0.1
     */
    public record Endpoint(String protocol, String locality, String baseUrl, String readinessPath, String modelsPath,
    String modelsPointer, String resultSchema, int contextTokens, int answerReserveTokens,
    int requestTimeoutSeconds, String credentialKey) {

        /**
         * @param protocol              {@code openai_chat} or {@code system_one}
         * @param locality              {@code loopback} or {@code remote}
         * @param baseUrl               the origin of the endpoint
         * @param readinessPath         the readiness path; {@code null} when the models path is the readiness check
         * @param modelsPath            the path of the models listing
         * @param modelsPointer         the JSON Pointer to the model array in the listing
         * @param resultSchema          what the endpoint does with a result schema
         * @param contextTokens         the largest request the endpoint accepts
         * @param answerReserveTokens   the part of {@code contextTokens} kept for the answer
         * @param requestTimeoutSeconds the timeout of one request
         */
        public Endpoint(String protocol, String locality, String baseUrl, String readinessPath, String modelsPath,
                String modelsPointer, String resultSchema, int contextTokens, int answerReserveTokens,
                int requestTimeoutSeconds) {
            this(protocol, locality, baseUrl, readinessPath, modelsPath, modelsPointer, resultSchema, contextTokens,
                    answerReserveTokens, requestTimeoutSeconds, null);
        }

        /** The context budget of a role on the harness: {@code context_tokens} less {@code answer_reserve_tokens}. */
        public int contextBudgetTokens() {
            return contextTokens - answerReserveTokens;
        }

        /** @return whether the endpoint is the remote form of one service, enrolled by the operator only */
        public boolean remote() {
            return "remote".equals(locality);
        }

        /** @return whether the endpoint speaks the protocol {@code system_one}, the judgment endpoint */
        public boolean judgment() {
            return "system_one".equals(protocol);
        }

        /** @return the readiness path, or the models path when the profile declares none */
        public String readinessOrModelsPath() {
            return readinessPath == null ? modelsPath : readinessPath;
        }

        /** @return whether a result schema is sent with the request */
        public boolean sendsResultSchema() {
            return !"none".equals(resultSchema);
        }
    }

    /**
     * How the identity of a model's weights is read (Client Profiles § Weights Identity).
     *
     * @param source  {@code models_listing} or {@code argv}
     * @param pointer the JSON Pointer below a model object; {@code null} for {@code argv}
     * @param argv    the argument vector with the {@code {{model}}} slot; {@code null} for {@code models_listing}
     * @param pattern the pattern whose first group is the identity; {@code null} for {@code models_listing}
     * @since 0.1
     */
    public record WeightsProbe(String source, String pointer, List<String> argv, String pattern) {

        /** Copies the argument vector. */
        public WeightsProbe {
            Objects.requireNonNull(source, "source");
            argv = argv == null ? null : List.copyOf(argv);
        }

        /**
         * @param model a model id
         * @return the argument vector with the {@code {{model}}} slot filled
         */
        public List<String> argvFor(String model) {
            if (argv == null) {
                return List.of();
            }
            return argv.stream().map(item -> item.replace("{{model}}", model)).toList();
        }
    }

    /**
     * @param profile a validated profile document
     * @return the endpoint profile
     * @throws IllegalArgumentException if the document is not an endpoint profile
     */
    public static EndpointProfile of(JsonNode profile) {
        Objects.requireNonNull(profile, "profile");
        if (!ProfileValidator.isEndpoint(profile)) {
            throw new IllegalArgumentException("Not an endpoint profile");
        }
        var block = profile.member("endpoint").orElseThrow();
        return new EndpointProfile(ProfileValidator.clientId(profile),
                member(profile, "executable"),
                identity(profile).orElse(null),
                updateEvidence(profile).orElse(null),
                endpoint(block),
                tiers(profile),
                weightsProbe(profile.member(WEIGHTS_PROBE).orElse(null)).orElse(null));
    }

    /**
     * @param document a validated profile document
     * @return the weights probe of the profile, {@code null} when it declares none: the endpoint profile's own
     *         {@code weights_probe}, or {@code headless.weights_probe} of a client profile
     */
    public static Optional<WeightsProbe> weightsProbeOf(JsonNode document) {
        var declared = ProfileValidator.isEndpoint(document)
                ? document.member(WEIGHTS_PROBE).orElse(null)
                : document.member("headless").filter(headless -> !headless.isNull())
                .flatMap(headless -> headless.member(WEIGHTS_PROBE)).orElse(null);
        return weightsProbe(declared);
    }

    private static Optional<Identity> identity(JsonNode profile) {
        var declared = profile.member("identity").orElse(null);
        if (declared == null || declared.isNull()) {
            return Optional.empty();
        }
        var probe = declared.member("version_probe").orElseThrow();
        return Optional.of(new Identity(probe.elements().stream().map(value -> value.text().orElse("")).toList(),
                declared.member("version_pattern").flatMap(JsonNode::text).orElse("")));
    }

    private static Optional<UpdateEvidence> updateEvidence(JsonNode profile) {
        var declared = profile.member("update_evidence").orElse(null);
        if (declared == null || declared.isNull()) {
            return Optional.empty();
        }
        return Optional.of(new UpdateEvidence(declared.member("method").flatMap(JsonNode::text).orElse("none"),
                member(declared, "signer")));
    }

    private static Endpoint endpoint(JsonNode block) {
        return new Endpoint(block.member("protocol").flatMap(JsonNode::text).orElseThrow(),
                block.member("locality").flatMap(JsonNode::text).orElseThrow(),
                block.member("base_url").flatMap(JsonNode::text).orElseThrow(),
                member(block, "readiness_path"),
                block.member("models_path").flatMap(JsonNode::text).orElseThrow(),
                block.member("models_pointer").flatMap(JsonNode::text).orElseThrow(),
                block.member("result_schema").flatMap(JsonNode::text).orElseThrow(),
                integer(block, "context_tokens"),
                integer(block, "answer_reserve_tokens"),
                integer(block, "request_timeout_seconds"),
                member(block, "credential_key"));
    }

    private static Map<String, String> tiers(JsonNode profile) {
        var tiers = new LinkedHashMap<String, String>();
        profile.member("model_tiers").ifPresent(declared -> declared.members()
                .forEach((tier, value) -> value.text().ifPresent(model -> tiers.put(tier, model))));
        return tiers;
    }

    private static Optional<WeightsProbe> weightsProbe(JsonNode declared) {
        if (declared == null || declared.isNull()) {
            return Optional.empty();
        }
        var argv = declared.member("argv").orElse(null);
        return Optional.of(new WeightsProbe(declared.member("source").flatMap(JsonNode::text).orElseThrow(),
                member(declared, "pointer"),
                argv == null || argv.isNull() ? null
                        : argv.elements().stream().map(value -> value.text().orElse("")).toList(),
                member(declared, "pattern")));
    }

    private static String member(JsonNode node, String name) {
        return node.member(name).filter(value -> !value.isNull()).flatMap(JsonNode::text).orElse(null);
    }

    private static int integer(JsonNode node, String name) {
        var value = node.member(name).orElse(null);
        if (value == null) {
            throw new IllegalArgumentException("The member '%s' is missing".formatted(name));
        }
        return (int) value.integerValue().orElseThrow(
                () -> new IllegalArgumentException("The member '%s' is not an integer".formatted(name)));
    }
}
