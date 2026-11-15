/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.LongContextThresholds;
import de.planmarshall.workflow.envelope.EnvelopeBudget.Source;

/**
 * The cap of the envelope budget on a threshold read from a validated profile document (Model Work § Cost Sizing,
 * Envelope Budget, item 4): {@link LongContextThresholds} answers the entry of the model, and {@link EnvelopeBudget}
 * caps its fallbacks at it while a role value above it stays as set.
 * <p>
 * The signature here is the shipped one: {@code EnvelopeBudget.resolve} takes {@code @Nullable Long} values, not the
 * {@code OptionalInt} that PLAN-V2-40's outline named (ADR {@code workflow-envelope/003}: an {@code Optional} is a return
 * type only), so the threshold is handed over at the type it has.
 */
// Every datum in this class is a contract: the profile's member names and the schema's budget and threshold bounds.
@DisplayName("Envelope budget from a profile's long-context threshold")
class EnvelopeBudgetProfileThresholdTest {

    private static final String DOCUMENT = "profiles/acme-cli.json";

    /** The one model the document names a threshold for. */
    private static final String MODEL = "acme-frontier";

    /** The document's {@code headless.context_budget_tokens}, a contract value of the schema's range. */
    private static final long PROFILE_BUDGET = 400_000L;

    /** The document's threshold of {@link #MODEL}, so the profile budget lies above it. */
    private static final long THRESHOLD = 200_000L;

    private static final String PROFILE = """
            {
              "client_id": "acme-cli",
              "headless": {
                "context_budget_tokens": 400000,
                "long_context_thresholds": {"acme-frontier": 200000}
              }
            }
            """;

    @Test
    @DisplayName("the profile fallback is capped at the model's threshold")
    void profileFallbackCapped() {
        var threshold = threshold(MODEL);

        var resolution = EnvelopeBudget.resolve(null, null, PROFILE_BUDGET, threshold);

        assertAll(
                () -> assertEquals(THRESHOLD, resolution.tokens(), "The fallback is capped at the threshold"),
                () -> assertEquals(Source.PROFILE, resolution.source(), "The fallback comes from the profile"),
                () -> assertTrue(resolution.capped(), "The lowered fallback is reported as capped"),
                () -> assertFalse(resolution.aboveThreshold(), "A capped fallback is never flagged above"));
    }

    @Test
    @DisplayName("a role budget above the threshold is kept and flagged")
    void roleValueAboveTheThresholdKept() {
        var roleBudget = THRESHOLD + 100_000L;
        var threshold = threshold(MODEL);

        var resolution = EnvelopeBudget.resolve(null, roleBudget, PROFILE_BUDGET, threshold);

        assertAll(
                () -> assertEquals(roleBudget, resolution.tokens(), "The role value is kept as it is set"),
                () -> assertEquals(Source.ROLE, resolution.source(), "The role value wins the precedence"),
                () -> assertFalse(resolution.capped(), "A kept role value is not capped"),
                () -> assertTrue(resolution.aboveThreshold(), "The kept role value is flagged above"));
    }

    @Test
    @DisplayName("a model the profile names no entry for is not capped")
    void modelWithoutEntryNotCapped() {
        var threshold = threshold("acme-unlisted");

        var resolution = EnvelopeBudget.resolve(null, null, PROFILE_BUDGET, threshold);

        assertAll(
                () -> assertEquals(PROFILE_BUDGET, resolution.tokens(), "The fallback stays the profile's value"),
                () -> assertFalse(resolution.capped(), "The fallback is not capped without an entry"),
                () -> assertFalse(resolution.aboveThreshold(), "A fallback is never flagged above"));
    }

    /**
     * @param modelId a model identifier
     * @return the threshold the validated document names for it, or {@code null} where it names none
     */
    private static @Nullable Long threshold(String modelId) {
        var threshold = LongContextThresholds.of(document()).forModel(modelId);
        return threshold.isPresent() ? (long) threshold.getAsInt() : null;
    }

    private static JsonNode document() {
        return JsonDocument.parse(PROFILE.getBytes(UTF_8), DOCUMENT);
    }
}
