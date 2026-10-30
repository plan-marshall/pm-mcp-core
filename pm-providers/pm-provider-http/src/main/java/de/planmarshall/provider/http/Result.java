/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of a provider operation with its closed outcome.
 *
 * @param outcome  the outcome
 * @param value    the value, present for {@link Outcome#OK}
 * @param complete for reads of a collection: whether every page was read; {@code true} otherwise
 * @param resetAt  for {@link Outcome#RATE_LIMITED}: when the operation may be re-issued
 * @param detail   the detail of a non-OK outcome: the provider's own message where it gave one, otherwise the
 *                 failing step; the client adds no token, no credential and no request body. A provider message
 *                 is remote text, to be passed through the secret redactor where it is emitted ({@code PM-CRED-5})
 * @param <V>      the value type
 * @since 0.1
 */
public record Result<V>(Outcome outcome, Optional<V> value, boolean complete, Optional<Instant> resetAt,
                        String detail) {

    /** The closed outcome of a provider operation. */
    public enum Outcome {
        /** The operation succeeded. */
        OK,
        /** The addressed entity does not exist or is not visible to the credential. */
        NOT_FOUND,
        /**
         * The provider answered and refused the operation itself for a stated reason (e.g. a validation
         * failure, a moved head, a feature that is not enabled); a credential refusal is
         * {@link #AUTH_FAILED} or {@link #PERMISSION_DENIED}.
         */
        REJECTED,
        /** The provider does not accept the credential: missing, invalid, expired or revoked ({@code 401}). */
        AUTH_FAILED,
        /** The provider accepts the credential, which lacks the permission for the operation ({@code 403}). */
        PERMISSION_DENIED,
        /** The provider's rate limit applies until {@code resetAt}; neither a failure nor a timeout. */
        RATE_LIMITED,
        /**
         * No usable answer: transport failure, a request refused before sending, a server error, an unexpected
         * status or an unreadable response.
         */
        FAILED
    }

    /**
     * @param outcome  the outcome
     * @param value    the value
     * @param complete the completeness
     * @param resetAt  the reset instant
     * @param detail   the detail
     */
    public Result {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(resetAt, "resetAt");
        detail = detail == null ? "" : detail;
    }

    /**
     * @param value the value
     * @param <V>   the value type
     * @return a complete OK result
     */
    public static <V> Result<V> ok(V value) {
        return new Result<>(Outcome.OK, Optional.of(value), true, Optional.empty(), "");
    }

    /**
     * @param value    the value
     * @param complete whether every page was read
     * @param <V>      the value type
     * @return an OK result of a collection read
     */
    public static <V> Result<V> ok(V value, boolean complete) {
        return new Result<>(Outcome.OK, Optional.of(value), complete, Optional.empty(), "");
    }

    /**
     * @param outcome the outcome
     * @param detail  the detail
     * @param <V>     the value type
     * @return a result without value
     */
    public static <V> Result<V> of(Outcome outcome, String detail) {
        return new Result<>(outcome, Optional.empty(), true, Optional.empty(), detail);
    }

    /**
     * Maps a response that is not {@link Response.Outcome#OK} to the contract outcome.
     * <p>
     * An HTTP error maps by its status: {@code 401} to {@link Outcome#AUTH_FAILED} and {@code 403} to
     * {@link Outcome#PERMISSION_DENIED} (the credential is refused, never the operation), {@code 404} to
     * {@link Outcome#NOT_FOUND}, and every other
     * {@code 4xx} to {@link Outcome#REJECTED} when the provider refuses the operation itself: always for
     * {@code 405}, {@code 406}, {@code 409} and {@code 422}, and for the remaining ones (such as {@code 400})
     * when the body carries the provider's reason ({@code message} or {@code error}). A {@code 4xx} without a
     * reason, a {@code 5xx}, a transport failure and a request refused before sending map to
     * {@link Outcome#FAILED}.
     *
     * @param response the failed response
     * @param <V>      the value type
     * @return the result
     */
    public static <V> Result<V> failed(Response response) {
        String detail = response.detail().isEmpty() ? "HTTP " + response.status() : response.detail();
        return switch (response.outcome()) {
            case RATE_LIMITED -> new Result<>(Outcome.RATE_LIMITED, Optional.empty(), true, response.resetAt(), detail);
            case HTTP_ERROR -> {
                Optional<String> reason = reasonOf(response.body());
                yield of(byStatus(response.status(), reason.isPresent()), detail + reason.map(r -> ": " + r).orElse(""));
            }
            default -> of(Outcome.FAILED, detail);
        };
    }

    private static Outcome byStatus(int status, boolean reasoned) {
        return switch (status) {
            case 401 -> Outcome.AUTH_FAILED;
            case 403 -> Outcome.PERMISSION_DENIED;
            case 404 -> Outcome.NOT_FOUND;
            case 405, 406, 409, 422 -> Outcome.REJECTED;
            default -> status >= 400 && status < 500 && reasoned ? Outcome.REJECTED : Outcome.FAILED;
        };
    }

    /** The provider's reason in an error body: {@code message} (GitHub, GitLab) or {@code error} (GitLab). */
    private static Optional<String> reasonOf(String body) {
        if (!(Json.parseOrNull(body).orElse(null) instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object reason = map.get("message") != null ? map.get("message") : map.get("error");
        return Optional.ofNullable(reason).map(String::valueOf).filter(r -> !r.isBlank());
    }

    /**
     * Carries a non-OK result over to another value type, keeping outcome, reset instant and detail.
     *
     * @param <W> the other value type
     * @return the result without value
     */
    public <W> Result<W> asFailure() {
        return new Result<>(outcome, Optional.empty(), complete, resetAt, detail);
    }

    /**
     * @return whether the outcome is {@link Outcome#OK}
     */
    public boolean isOk() {
        return outcome == Outcome.OK;
    }
}
