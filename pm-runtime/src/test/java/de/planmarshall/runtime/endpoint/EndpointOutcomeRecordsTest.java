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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.HarnessUsage;

/**
 * Proves that the builder of {@link EndpointOutcome} builds the value its canonical constructor builds, including its
 * null refusal for the fate. Every value is generated; the attempt ended on a timeout, carries no content and measured
 * no usage.
 */
@EnableGeneratorController
@DisplayName("EndpointOutcome builder")
class EndpointOutcomeRecordsTest {

    @Test
    @DisplayName("the builder builds the endpoint outcome the constructor builds")
    void endpointOutcomeBuilderEqualsConstructor() {
        var fate = EndpointOutcome.Fate.ENDED;
        var reason = EndpointOutcome.Reason.TIMEOUT;
        var detail = Generators.letterStrings(1, 40).next();
        var content = "";
        var usage = HarnessUsage.none();

        var constructed = new EndpointOutcome(fate, reason, detail, content, usage);
        var built = EndpointOutcome.builder()
                .fate(fate)
                .reason(reason)
                .detail(detail)
                .content(content)
                .usage(usage)
                .build();

        assertEquals(constructed, built, "the builder builds the same endpoint outcome");
    }

    @Test
    @DisplayName("the builder refuses a missing fate like the constructor")
    void endpointOutcomeBuilderRefusesMissingFate() {
        var builder = EndpointOutcome.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("fate", refusal.getMessage(), "the refusal names the fate");
    }
}
