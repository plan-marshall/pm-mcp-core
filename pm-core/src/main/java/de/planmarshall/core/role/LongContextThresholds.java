/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.LoadedProfile;

/**
 * The long-context price thresholds of a client profile (PM-CLIENT-5, Harness Profiles § Model Tiers &amp; Models of a
 * Harness): per model id of the harness, the input tokens above which its provider bills a request at the long-context
 * price. A fallback of the context budget is capped at the entry, never a value the role artifact or the operator sets.
 * <p>
 * The thresholds are read beside {@link HarnessProfile}, not as a component of that record: the member is read by the
 * envelope budget alone, and a new component of the record ripples into every constructor of it.
 * <p>
 * {@link #of(JsonNode)} reads the {@code headless.long_context_thresholds} member of a profile document's tree — the
 * tree {@code JsonDocument.parse} returns, or the tree a validated profile carries. A member value that is no integer
 * within the range of a threshold is ignored, so the read never fails; the validator is what refuses such a document
 * before production reads it. {@link #forModel(String)} compares the model id byte-exactly, as
 * {@code identifiers/01-general-rules-and-registry.adoc} general rule 2 requires — no prefix match and no case folding.
 *
 * @param byModel the threshold of each model id the profile declares
 * @since 0.1
 */
public record LongContextThresholds(Map<String, Integer> byModel) {

    private static final String NAME_HEADLESS = "headless";

    private static final String NAME_LONG_CONTEXT_THRESHOLDS = "long_context_thresholds";

    /** The thresholds of a profile that declares none. */
    private static final LongContextThresholds EMPTY = new LongContextThresholds(Map.of());

    /**
     * Copies the map.
     *
     * @param byModel the threshold of each model id the profile declares
     */
    public LongContextThresholds {
        Objects.requireNonNull(byModel, "byModel");
        byModel = Map.copyOf(byModel);
    }

    /**
     * Reads the thresholds of a profile document's tree.
     *
     * @param document the tree of a profile document ({@code JsonDocument.parse} of it, or the tree a validated profile
     *                 carries)
     * @return the thresholds; empty for a document without the member or without a {@code headless} block, which an
     *         endpoint profile is, because its kind declares no {@code headless}
     */
    public static LongContextThresholds of(JsonNode document) {
        var headless = document.member(NAME_HEADLESS).orElse(null);
        if (headless == null || headless.isNull()) {
            return EMPTY;
        }
        var declared = headless.member(NAME_LONG_CONTEXT_THRESHOLDS).orElse(null);
        if (declared == null || declared.isNull()) {
            return EMPTY;
        }
        var byModel = new LinkedHashMap<String, Integer>();
        for (var member : declared.members().entrySet()) {
            var value = member.getValue().integerValue().orElse(0);
            if (value > 0 && value <= Integer.MAX_VALUE) {
                byModel.put(member.getKey(), (int) value);
            }
        }
        return new LongContextThresholds(byModel);
    }

    /**
     * @param profile a validated profile
     * @return the thresholds of its document
     */
    public static LongContextThresholds of(LoadedProfile profile) {
        return of(profile.document());
    }

    /**
     * @param modelId a model identifier
     * @return the long-context threshold of the model, or empty when the profile names none for it
     */
    public OptionalInt forModel(String modelId) {
        var threshold = byModel.get(modelId);
        return threshold == null ? OptionalInt.empty() : OptionalInt.of(threshold);
    }
}
