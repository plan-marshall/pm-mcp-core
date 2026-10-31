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
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.EndpointProfile;

/**
 * The HTTP calls to a loopback endpoint (Client Profiles § The Request): the readiness check, the models listing and the
 * one request of a task, each through {@code cui-http} and never through a raw client.
 * <p>
 * A call answers what happened, never an exception: {@link Failure#TIMEOUT} for a request that did not answer inside the
 * profile's {@code request_timeout_seconds}, {@link Failure#UNREACHABLE} for a target that answered nothing, and
 * {@link Failure#NONE} for an answer whatever its status, so the caller decides on the status and the body. A failure
 * detail names the endpoint and never a task text.
 *
 * @since 0.1
 */
public final class EndpointClient {

    /** The connect timeout of every request, in seconds. */
    public static final int CONNECT_TIMEOUT_SECONDS = 5;

    /** The path of the chat request of the protocol {@code openai_chat}, fixed by the protocol. */
    public static final String CHAT_PATH = "/chat/completions";

    /** The media type of every request body. */
    public static final String MEDIA_TYPE = "application/json";

    /**
     * Sends one {@code GET}.
     *
     * @param profile the endpoint profile
     * @param path    the path below the profile's {@code base_url}
     * @return the answer, or the failure of a request that got none
     */
    public Response get(EndpointProfile profile, String path) {
        return send(profile, path, null);
    }

    /**
     * Sends one {@code POST}.
     *
     * @param profile the endpoint profile
     * @param path    the path below the profile's {@code base_url}
     * @param body    the JSON body
     * @return the answer, or the failure of a request that got none
     */
    public Response post(EndpointProfile profile, String path, String body) {
        return send(profile, Objects.requireNonNull(path, "path"), Objects.requireNonNull(body, "body"));
    }

    /**
     * @param profile the endpoint profile
     * @param path    the path below the profile's {@code base_url}
     * @return the absolute URL of the path
     */
    public static URI uri(EndpointProfile profile, String path) {
        var base = profile.endpoint().baseUrl();
        var separator = path.startsWith("/") ? "" : "/";
        return URI.create(base + separator + path);
    }

    private Response send(EndpointProfile profile, String path, @Nullable String body) {
        Objects.requireNonNull(profile, "profile");
        var uri = uri(profile, path);
        if (!loopback(uri)) {
            return new Response(0, "", Failure.UNREACHABLE,
                    "refused (%s): not a loopback address".formatted(shown(uri)));
        }
        try (var handler = HttpHandler.builder().uri(uri).connectionTimeoutSeconds(CONNECT_TIMEOUT_SECONDS)
                     .readTimeoutSeconds(profile.endpoint().requestTimeoutSeconds()).allowInsecureHttp(true).build()) {
            var request = handler.requestBuilder().uri(uri).header("Content-Type", MEDIA_TYPE);
            if (body == null) {
                request.GET();
            } else {
                request.POST(BodyPublishers.ofString(body));
            }
            var answer = handler.send(request.build(), BodyHandlers.ofString());
            return new Response(answer.statusCode(), answer.body(), Failure.NONE, "answered");
        } catch (HttpTimeoutException _) {
            return failure(uri, Failure.TIMEOUT, "no answer inside %d s"
                    .formatted(profile.endpoint().requestTimeoutSeconds()));
        } catch (IOException | IllegalArgumentException | IllegalStateException e) {
            // The message of such an exception may quote the URL: name its type only.
            return failure(uri, Failure.UNREACHABLE, e.getClass().getSimpleName());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return failure(uri, Failure.UNREACHABLE, "interrupted");
        }
    }

    private static Response failure(URI uri, Failure failure, String detail) {
        return new Response(0, "", failure, "%s (%s): %s".formatted(failure.wireName(), shown(uri), detail));
    }

    /**
     * @param uri a target
     * @return whether the target is a plain HTTP loopback address, the only cleartext the package admits
     */
    static boolean loopback(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        var host = uri.getHost();
        // URI.getHost() keeps the brackets of an IPv6 literal.
        return "127.0.0.1".equals(host) || "::1".equals(host) || "[::1]".equals(host);
    }

    /**
     * @param uri a target
     * @return the URL for a detail: scheme, host, port and path, without user information, query or fragment
     */
    static String shown(URI uri) {
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return scheme + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + (uri.getPath() == null ? "" : uri.getPath());
    }

    /** How a call ended. The set is closed. */
    public enum Failure {
        /** The endpoint answered. */
        NONE("none"),
        /** The target answered nothing. */
        UNREACHABLE("unreachable"),
        /** The target did not answer inside the profile's timeout. */
        TIMEOUT("timeout"),
        /** The request was refused before it was sent. */
        REFUSED("refused"),
        /** The answer was a redirect the package does not follow. */
        REDIRECT("redirect_refused");

        private final String wireName;

        Failure(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the failure in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The answer of one call, or the failure of a call that got none.
     *
     * @param status  the status of the answer; {@code 0} when there was none
     * @param body    the body of the answer; empty when there was none
     * @param failure how the call ended
     * @param detail  what happened, for a record; never a task text
     * @since 0.1
     */
    public record Response(int status, String body, Failure failure, String detail) {

        /** Copies the values and refuses a missing outcome. */
        public Response {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(failure, "failure");
            Objects.requireNonNull(detail, "detail");
        }

        /** @return whether the endpoint answered with a success status */
        public boolean successful() {
            return failure == Failure.NONE && status >= 200 && status <= 299;
        }
    }
}
