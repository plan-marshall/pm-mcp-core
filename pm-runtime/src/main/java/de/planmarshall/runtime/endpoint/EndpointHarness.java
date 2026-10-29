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

import de.cuioss.tools.logging.CuiLogger;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.JsonPointer;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.log.RuntimeLogMessages;

/**
 * One task of a {@code zero_tool} role on an endpoint harness (Client Profiles § The Request, § Enrolment): the owner
 * check before the request, the request itself and the acceptance of the answer.
 * <p>
 * The owner check runs before every request: the runtime reads which program owns the listening socket of the profile's
 * {@code base_url} and refuses the task {@code harness_not_enrolled} when that program is not the enrolled executable,
 * so no task text reaches a program the operator did not enrol. The runtime never starts, stops or configures the
 * serving program.
 *
 * @since 0.1
 */
public final class EndpointHarness {

    private static final CuiLogger LOGGER = new CuiLogger(EndpointHarness.class);

    private final EndpointClient client;
    private final SocketOwner owner;

    /**
     * @param client the HTTP client of the endpoint
     * @param owner  the reading of the listening socket's owner
     */
    public EndpointHarness(EndpointClient client, SocketOwner owner) {
        this.client = Objects.requireNonNull(client, "client");
        this.owner = Objects.requireNonNull(owner, "owner");
    }

    /**
     * Runs one task.
     *
     * @param profile  the endpoint profile
     * @param enrolled the enrolment that names the serving program
     * @param request  the composed request
     * @param schema   the role's result schema the answer is validated against; {@code null} when the role declares none
     * @return the outcome of the task
     */
    public EndpointOutcome task(EndpointProfile profile, HarnessEnrolment enrolled, EndpointRequest request,
            JsonNode schema) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(enrolled, "enrolled");
        Objects.requireNonNull(request, "request");
        if (enrolled.isEndpoint()) {
            LOGGER.warn(RuntimeLogMessages.WARN.WORKER_LAUNCH_REFUSED, profile.clientId(),
                    LaunchRefusal.HARNESS_NOT_ENROLLED.code(),
                    "the endpoint '%s' is enrolled as a remote endpoint".formatted(profile.clientId()),
                    LaunchRefusal.HARNESS_NOT_ENROLLED.resolvingAct(profile.clientId()));
            return EndpointOutcome.refused(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED,
                    "the endpoint is enrolled as a remote endpoint");
        }
        var endpoint = URI.create(profile.endpoint().baseUrl());
        var lookup = owner.ownerOf(endpoint.getHost(), endpoint.getPort());
        if (!lookup.runs(enrolled.executablePath().toString())) {
            var detail = lookup.isFound()
                    ? "the program on the port is '%s', the enrolled executable is '%s'"
                    .formatted(lookup.executablePath(), enrolled.executablePath())
                    : lookup.detail();
            LOGGER.warn(RuntimeLogMessages.WARN.WORKER_LAUNCH_REFUSED, profile.clientId(),
                    LaunchRefusal.HARNESS_NOT_ENROLLED.code(), detail,
                    LaunchRefusal.HARNESS_NOT_ENROLLED.resolvingAct(profile.clientId()));
            return EndpointOutcome.refused(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, detail);
        }
        var digest = executableDigest(lookup.executablePath());
        if (digest.isEmpty() || !digest.get().equals(enrolled.sha256())) {
            var detail = "the program on the port is not the build the enrolment recorded";
            LOGGER.warn(RuntimeLogMessages.WARN.WORKER_LAUNCH_REFUSED, profile.clientId(),
                    LaunchRefusal.HARNESS_NOT_ENROLLED.code(), detail,
                    LaunchRefusal.HARNESS_NOT_ENROLLED.resolvingAct(profile.clientId()));
            return EndpointOutcome.refused(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, detail);
        }
        var answer = EndpointOutcome.accept(client.post(profile, EndpointClient.CHAT_PATH, request.body()), schema);
        if (answer.fate() == EndpointOutcome.Fate.ENDED) {
            LOGGER.warn(RuntimeLogMessages.WARN.ENDPOINT_ATTEMPT_ENDED, request.model(),
                    EndpointClient.shown(endpoint), answer.detail());
        }
        return answer;
    }

    private static Optional<String> executableDigest(String executablePath) {
        try {
            return HarnessVerifier.digestExecutable(Path.of(executablePath));
        } catch (UncheckedIOException _) {
            return Optional.empty();
        }
    }

    /**
     * Checks that the endpoint answers its readiness check.     *
     * @param profile the endpoint profile
     * @return whether it is ready
     */
    public boolean ready(EndpointProfile profile) {
        var response = client.get(profile, profile.endpoint().readinessOrModelsPath());
        if (!response.successful()) {
            LOGGER.warn(RuntimeLogMessages.WARN.ENDPOINT_NOT_READY, profile.clientId(),
                    profile.endpoint().readinessOrModelsPath());
            return false;
        }
        return true;
    }

    /**
     * @param profile the endpoint profile
     * @return the model objects of the models listing, or {@code null} when the listing cannot be read
     */
    public JsonNode listing(EndpointProfile profile) {
        var response = client.get(profile, profile.endpoint().modelsPath());
        if (!response.successful()) {
            return null;
        }
        try {
            var document = JsonDocument.parse(response.body().getBytes(StandardCharsets.UTF_8), "models");
            return JsonPointer.resolve(document, profile.endpoint().modelsPointer(), "models listing");
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return null;
        }
    }
}
