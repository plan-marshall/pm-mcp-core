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

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Waiver consumption record builder contract")
class WaiverConsumptionRecordBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's waiver consumption")
    void builderBuildsConsumption() {
        var canonical = new WaiverConsumptionRecord("W-1", "build-gate",
                Instant.parse("2026-10-22T10:00:00Z"), "execute.step",
                "0123456789abcdef0123456789abcdef01234567", List.of("check-a"));

        var built = WaiverConsumptionRecord.builder().waiverId(canonical.waiverId())
                .gate(canonical.gate()).at(canonical.at()).stateId(canonical.stateId())
                .headSha(canonical.headSha()).covered(canonical.covered()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new WaiverConsumptionRecord(null, canonical.gate(), canonical.at(),
                        canonical.stateId(), canonical.headSha(), canonical.covered()),
                "the constructor refuses an absent waiver id");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> WaiverConsumptionRecord.builder().gate(canonical.gate()).at(canonical.at())
                        .stateId(canonical.stateId()).headSha(canonical.headSha())
                        .covered(canonical.covered()).build(),
                "the builder refuses an absent waiver id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent id with the same message"));
    }
}
