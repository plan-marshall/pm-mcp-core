/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Builder;

import de.planmarshall.core.store.WaiverRecord.Channel;

/**
 * One waiver request of a plan, an element of the member {@code waiver_requests[]} of its status document
 * {@code mcp/status.json} (PM-SEC-3, PM-IMPL-2): a worker asks the operator to waive a gate or raise a budget, and the
 * operator answers it by a waiver or declines it.
 * <p>
 * A request is no store of its own and no question record. It has no time limit: it stays pending, without an
 * {@link #outcome()}, until the operator answers it or the plan ends ({@link WaiverRequestOutcome#CANCELLED}).
 *
 * @param requestId    the id of the request, {@code WR-} and digits
 * @param gate         the waivable gate or budget the request names
 * @param policy       the remediation unit that filed it; {@code null} where none is named
 * @param rationale    the worker's rationale, 1 to {@value #MAX_TEXT_LENGTH} characters
 * @param evidenceUri  the evidence of the failure the request is about; {@code null} where none is named
 * @param requestedAt  when the request was filed
 * @param outcome      how the request ended; {@code null} while it is pending
 * @param waiverId     the waiver that answered it, {@code W-} and digits; {@code null} where none did
 * @param answered     the operator's act that answered it; {@code null} where none did
 * @param prCommentRef the provider id of the request's pull request comment; {@code null} where none was posted
 * @since 0.1
 */
@Builder(toBuilder = true)
public record WaiverRequest(String requestId, String gate, @Nullable String policy, String rationale,
@Nullable String evidenceUri, Instant requestedAt, @Nullable WaiverRequestOutcome outcome, @Nullable String waiverId,
@Nullable Answered answered, @Nullable String prCommentRef) {

    /** The longest rationale, and the longest reason of a decline, a request holds. */
    public static final int MAX_TEXT_LENGTH = 2000;

    private static final Pattern REQUEST_ID = Pattern.compile("WR-\\d+");
    private static final Pattern WAIVER_ID = Pattern.compile("W-\\d+");

    /**
     * The operator's act that answered a request: a waiver or a decline.
     *
     * @param channel  the client of the act
     * @param deviceId the paired device, {@code dev-} and 12 hex digits; {@code null} where there is none
     * @param reason   the operator's reason of a decline, at most {@value #MAX_TEXT_LENGTH} characters; {@code null}
     *                 where none was given
     * @param at       when the operator acted
     * @since 0.1
     */
    public record Answered(Channel channel, @Nullable String deviceId, @Nullable String reason, Instant at) {

        /**
         * @throws NullPointerException     if the channel or the instant is {@code null}
         * @throws IllegalArgumentException if the device id breaks its form or the reason is too long
         */
        public Answered {
            Objects.requireNonNull(channel, "channel");
            WaiverRecord.requireDeviceId(deviceId);
            if (reason != null && codePoints(reason) > MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("The reason of an answer has at most %d characters, not %d"
                        .formatted(MAX_TEXT_LENGTH, codePoints(reason)));
            }
            Objects.requireNonNull(at, "at");
        }
    }

    /**
     * @throws NullPointerException     if the request id, the gate, the rationale or the instant of the request is
     *                                  {@code null}
     * @throws IllegalArgumentException if an id breaks its form or the rationale is empty or too long
     */
    public WaiverRequest {
        requireMatch(REQUEST_ID, Objects.requireNonNull(requestId, "requestId"), "waiver request id (WR- and digits)");
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(rationale, "rationale");
        if (rationale.isEmpty() || codePoints(rationale) > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("The rationale of a waiver request has 1 to %d characters, not %d"
                    .formatted(MAX_TEXT_LENGTH, codePoints(rationale)));
        }
        Objects.requireNonNull(requestedAt, "requestedAt");
        if (waiverId != null) {
            requireMatch(WAIVER_ID, waiverId, "waiver id (W- and digits)");
        }
    }

    /**
     * @param text a text
     * @return its length in characters as the schema counts them, Unicode code points, so a character outside the
     *         Basic Multilingual Plane counts once
     */
    private static int codePoints(String text) {
        return text.codePointCount(0, text.length());
    }

    private static void requireMatch(Pattern pattern, String value, String name) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a %s: %s".formatted(name, value));
        }
    }
}
