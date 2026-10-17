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

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One line of the waiver consumption log {@code plans/<plan_id>/waiver-consumptions.jsonl} of a plan (PM-SEC-3,
 * PM-IMPL-2): one evaluation of a waivable gate that admitted a failure because a {@code met} waiver is set.
 * <p>
 * The record has a closed schema in {@code format_version} 1 ({@link WaiverConsumptionCodec}): every member is
 * required, and {@link #covered()} names the check names, finding ids or provider conditions the evaluation admitted.
 *
 * @param waiverId the waiver the evaluation relied on, {@code W-} and digits
 * @param gate     the gate that was evaluated
 * @param at       when the gate was evaluated
 * @param stateId  the state whose evaluation relied on the waiver
 * @param headSha  the commit the gate was evaluated on, 40 hex digits
 * @param covered  what the waiver admitted, in the order of the evaluation; each entry not empty
 * @since 0.1
 */
public record WaiverConsumptionRecord(String waiverId, String gate, Instant at, String stateId, String headSha,
List<String> covered) {

    /** The format version of a waiver consumption record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern WAIVER_ID = Pattern.compile("W-[0-9]+");
    private static final Pattern HEAD_SHA = Pattern.compile("[0-9a-f]{40}");

    /**
     * @throws NullPointerException     if a component or an entry of {@code covered} is {@code null}
     * @throws IllegalArgumentException if a value breaks its form, or a text is empty
     */
    public WaiverConsumptionRecord {
        Objects.requireNonNull(waiverId, "waiverId");
        if (!WAIVER_ID.matcher(waiverId).matches()) {
            throw new IllegalArgumentException("Not a waiver id (W- and digits): " + waiverId);
        }
        requireNotEmpty(gate, "gate");
        Objects.requireNonNull(at, "at");
        requireNotEmpty(stateId, "stateId");
        Objects.requireNonNull(headSha, "headSha");
        if (!HEAD_SHA.matcher(headSha).matches()) {
            throw new IllegalArgumentException("Not a commit SHA (40 hex digits): " + headSha);
        }
        covered = List.copyOf(covered);
        covered.forEach(entry -> requireNotEmpty(entry, "covered"));
    }

    private static void requireNotEmpty(String value, String name) {
        if (Objects.requireNonNull(value, name).isEmpty()) {
            throw new IllegalArgumentException("'%s' of a waiver consumption is empty".formatted(name));
        }
    }
}
