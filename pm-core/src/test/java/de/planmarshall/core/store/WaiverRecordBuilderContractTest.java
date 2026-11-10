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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.WaiverRecord.Channel;
import de.planmarshall.core.store.WaiverRecord.Event;
import de.planmarshall.core.store.WaiverRecord.Mode;
import de.planmarshall.core.store.WaiverRecord.Operator;
import de.planmarshall.core.store.WaiverRecord.ReasonClass;

@DisplayName("Waiver record builder contract")
class WaiverRecordBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's waiver record")
    void builderBuildsWaiverRecord() {
        var canonical = new WaiverRecord(Event.WAIVED, "W-1", "build-gate", Mode.MET, null,
                "the gate is a false positive", ReasonClass.GATE_FALSE_POSITIVE, null,
                new Operator(Channel.CLI, null, "oliver"), Instant.parse("2026-10-22T10:00:00Z"));

        var built = WaiverRecord.builder().event(canonical.event()).waiverId(canonical.waiverId())
                .gate(canonical.gate()).mode(canonical.mode()).value(canonical.value())
                .reason(canonical.reason()).reasonClass(canonical.reasonClass())
                .requestId(canonical.requestId()).operator(canonical.operator()).at(canonical.at())
                .build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new WaiverRecord(null, canonical.waiverId(), canonical.gate(), canonical.mode(),
                        canonical.value(), canonical.reason(), canonical.reasonClass(),
                        canonical.requestId(), canonical.operator(), canonical.at()),
                "the constructor refuses an absent event");
        var incomplete = WaiverRecord.builder().waiverId(canonical.waiverId()).gate(canonical.gate())
                .mode(canonical.mode()).value(canonical.value()).reason(canonical.reason())
                .reasonClass(canonical.reasonClass()).requestId(canonical.requestId())
                .operator(canonical.operator()).at(canonical.at());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent event");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent event with the same message"));
    }
}
