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

import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.handler.RedirectNotAllowedException;
import de.cuioss.http.client.handler.RedirectPolicy;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.Set;
import javax.net.ssl.SSLContext;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.runtime.credentials.SecretRegistry;
import de.planmarshall.runtime.credentials.TokenLaneCredential;

/**
 * The one {@code POST} of the protocol {@code system_one}, through {@code cui-http} and never through a raw client
 * (Client Profiles § Judgment Endpoint, Remote Form).
 * <p>
 * The remote form's secret is the Token Lane entry the profile's {@code credential_key} names. It is read at the moment
 * of a request, registered with the redaction registry, and sent only when the profile's {@code base_url} is the origin
 * of the entry's {@code url}; anything else ends the attempt before a request is sent. No redirect is followed: a
 * redirect to another origin or to {@code http} ends the attempt and receives no secret. The runtime retries nothing
 * inside an attempt.
 *
 * @since 0.1
 */
public final class JudgmentClient {

    /** The media type of every request body. */
    public static final String MEDIA_TYPE = "application/json";

    /** The connect timeout of every request, in seconds. */
    public static final int CONNECT_TIMEOUT_SECONDS = EndpointClient.CONNECT_TIMEOUT_SECONDS;

    private final TokenLaneCredential credentials;
    private final SecretRegistry redaction;
    private final @Nullable SSLContext sslContext;

    /**
     * @param credentials the Token Lane entry of a remote endpoint
     * @param redaction   the registry a secret is registered with before it is sent; {@code null} for a caller that
     *                    sends no secret, which is the loopback form's case
     */
    public JudgmentClient(TokenLaneCredential credentials, @Nullable SecretRegistry redaction) {
        this(credentials, redaction, null);
    }

    /**
     * @param credentials the Token Lane entry of a remote endpoint
     * @param redaction   the registry a secret is registered with before it is sent
     * @param sslContext  the TLS context of every request; {@code null} for the JDK's default, the TLS and hostname
     *                    verification of every Token Lane request. Package-private, a seam of this package's tests: the
     *                    production wiring uses the default context
     */
    JudgmentClient(TokenLaneCredential credentials, @Nullable SecretRegistry redaction,
            @Nullable SSLContext sslContext) {
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.redaction = SecretRegistry.orNone(redaction);
        this.sslContext = sslContext;
    }

    /**
     * @param profile the judgment endpoint profile
     * @param path    the path below the profile's {@code base_url}
     * @return the absolute URL of the path
     */
    public static URI uri(EndpointProfile profile, String path) {
        return EndpointClient.uri(profile, path);
    }

    /**
     * Sends one {@code GET} of the models listing or the readiness check.
     *
     * @param profile the judgment endpoint profile
     * @param path    the path below the profile's {@code base_url}
     * @return the answer, or why the attempt ended before or without an answer
     */
    public EndpointClient.Response get(EndpointProfile profile, String path) {
        return send(profile, path, null);
    }

    /**
     * Sends one request of a judgment task.
     *
     * @param profile the judgment endpoint profile
     * @param path    the path of the request, {@link SystemOneRequest#PATH} in production
     * @param body    the JSON body
     * @return the answer, or why the attempt ended before or without an answer
     */
    public EndpointClient.Response post(EndpointProfile profile, String path, String body) {
        return send(profile, Objects.requireNonNull(path, "path"), Objects.requireNonNull(body, "body"));
    }

    private EndpointClient.Response send(EndpointProfile profile, String path, @Nullable String body) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(path, "path");
        if (!profile.endpoint().judgment()) {
            return refusal(profile, path, "not the protocol system_one");
        }
        var credential = credential(profile, path);
        if (credential.refusal() != null) {
            return Objects.requireNonNull(credential.refusal(), "refusal");
        }
        return transport(profile, uri(profile, path), body, credential.authorization());
    }

    /**
     * @return the authorization header the request carries, or the refusal that ends the attempt before it is sent
     */
    private Credential credential(EndpointProfile profile, String path) {
        var endpoint = profile.endpoint();
        if (!endpoint.remote()) {
            if (!EndpointClient.loopback(uri(profile, path))) {
                return new Credential(null, refusal(profile, path, "not a loopback address"));
            }
            return new Credential(null, null);
        }
        var entry = credentials.entry(endpoint.credentialKey()).orElse(null);
        if (entry == null) {
            return new Credential(null, refusal(profile, path, "the credential entry '%s' is missing"
                    .formatted(endpoint.credentialKey())));
        }
        if (!TokenLaneCredential.origin(URI.create(endpoint.baseUrl())).equals(entry.origin())) {
            return new Credential(null,
                    refusal(profile, path, "the credential entry's origin is not the profile's base_url"));
        }
        redaction.register(entry.secret());
        return new Credential("Bearer " + entry.secret(), null);
    }

    private EndpointClient.Response transport(EndpointProfile profile, URI uri, @Nullable String body,
            @Nullable String authorization) {
        var endpoint = profile.endpoint();
        try (var handler = handler(uri, endpoint)) {
            var request = handler.requestBuilder().uri(uri).header("Content-Type", MEDIA_TYPE);
            if (authorization != null) {
                request.header("Authorization", authorization);
            }
            if (body == null) {
                request.GET();
            } else {
                request.POST(BodyPublishers.ofString(body));
            }
            var answer = handler.send(request.build(), BodyHandlers.ofString());
            if (answer.statusCode() >= 300 && answer.statusCode() <= 399) {
                return new EndpointClient.Response(answer.statusCode(), "", EndpointClient.Failure.REDIRECT,
                        "redirect_refused (%s): a redirect ends the attempt".formatted(EndpointClient.shown(uri)));
            }
            return new EndpointClient.Response(answer.statusCode(), answer.body(), EndpointClient.Failure.NONE,
                    "answered");
        } catch (RedirectNotAllowedException e) {
            return new EndpointClient.Response(0, "", EndpointClient.Failure.REDIRECT,
                    "redirect_refused (%s): %s".formatted(EndpointClient.shown(uri), e.getReason()));
        } catch (HttpTimeoutException _) {
            return failure(uri, EndpointClient.Failure.TIMEOUT, "no answer inside %d s"
                    .formatted(endpoint.requestTimeoutSeconds()));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            // The message of such an exception may quote the URL or a header value: name its type only.
            return failure(uri, EndpointClient.Failure.UNREACHABLE, e.getClass().getSimpleName());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return failure(uri, EndpointClient.Failure.UNREACHABLE, "interrupted");
        }
    }

    /** The authorization header of one request, or the refusal that ends the attempt before it is sent. */
    private record Credential(@Nullable String authorization, EndpointClient.@Nullable Response refusal) {
    }

    private HttpHandler handler(URI uri, EndpointProfile.Endpoint endpoint) {
        var builder = HttpHandler.builder().uri(uri).connectionTimeoutSeconds(CONNECT_TIMEOUT_SECONDS)
                .readTimeoutSeconds(endpoint.requestTimeoutSeconds()).allowInsecureHttp(!endpoint.remote())
                .redirectPolicy(RedirectPolicy.builder().allowedHosts(Set.of()).build());
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }

    private static EndpointClient.Response refusal(EndpointProfile profile, String path, String detail) {
        return new EndpointClient.Response(0, "", EndpointClient.Failure.REFUSED,
                "request_refused (%s): %s".formatted(EndpointClient.shown(uri(profile, path)), detail));
    }

    private static EndpointClient.Response failure(URI uri, EndpointClient.Failure failure, String detail) {
        return new EndpointClient.Response(0, "", failure, "%s (%s): %s".formatted(failure.wireName(),
                EndpointClient.shown(uri), detail));
    }
}
