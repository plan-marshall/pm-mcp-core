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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.OptionalLong;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Usage aggregate builder contract")
class UsageAggregateBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's usage aggregate")
    void builderBuildsAggregate() {
        var canonical = new UsageAggregate(UsageAggregate.Population.MODEL_JOB, OptionalLong.of(10),
                OptionalLong.of(5), OptionalLong.of(5), 1, 0, Map.of(), 1, new TreeMap<>(), false);

        var built = UsageAggregate.builder().population(canonical.population())
                .totalTokens(canonical.totalTokens()).tokensIn(canonical.tokensIn())
                .tokensOut(canonical.tokensOut()).rows(canonical.rows())
                .rowsUnmeasured(canonical.rowsUnmeasured())
                .unmeasuredReasons(canonical.unmeasuredReasons())
                .populationCount(canonical.populationCount()).excluded(canonical.excluded())
                .totalIsFloor(canonical.totalIsFloor()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new UsageAggregate(null, canonical.totalTokens(), canonical.tokensIn(),
                        canonical.tokensOut(), canonical.rows(), canonical.rowsUnmeasured(),
                        canonical.unmeasuredReasons(), canonical.populationCount(), canonical.excluded(),
                        canonical.totalIsFloor()),
                "the constructor refuses an absent population");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> UsageAggregate.builder().totalTokens(canonical.totalTokens())
                        .tokensIn(canonical.tokensIn()).tokensOut(canonical.tokensOut())
                        .rows(canonical.rows()).rowsUnmeasured(canonical.rowsUnmeasured())
                        .unmeasuredReasons(canonical.unmeasuredReasons())
                        .populationCount(canonical.populationCount()).excluded(canonical.excluded())
                        .totalIsFloor(canonical.totalIsFloor()).build(),
                "the builder refuses an absent population");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent population alike"));
    }
}
