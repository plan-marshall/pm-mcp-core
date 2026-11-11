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
import de.planmarshall.core.store.WaiverRequest.Answered;

@DisplayName("Waiver request builder contract")
class WaiverRequestBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's waiver request")
    void builderBuildsWaiverRequest() {
        var canonical = new WaiverRequest("WR-1", "build-gate", null,
                "the build is a false positive", null, Instant.parse("2026-10-22T10:00:00Z"), null, null,
                null, null);

        var built = WaiverRequest.builder().requestId(canonical.requestId()).gate(canonical.gate())
                .policy(canonical.policy()).rationale(canonical.rationale())
                .evidenceUri(canonical.evidenceUri()).requestedAt(canonical.requestedAt())
                .outcome(canonical.outcome()).waiverId(canonical.waiverId())
                .answered(canonical.answered()).prCommentRef(canonical.prCommentRef()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new WaiverRequest(null, canonical.gate(), canonical.policy(), canonical.rationale(),
                        canonical.evidenceUri(), canonical.requestedAt(), canonical.outcome(),
                        canonical.waiverId(), canonical.answered(), canonical.prCommentRef()),
                "the constructor refuses an absent request id");
        var incomplete = WaiverRequest.builder().gate(canonical.gate()).policy(canonical.policy())
                .rationale(canonical.rationale()).evidenceUri(canonical.evidenceUri())
                .requestedAt(canonical.requestedAt()).outcome(canonical.outcome())
                .waiverId(canonical.waiverId()).answered(canonical.answered())
                .prCommentRef(canonical.prCommentRef());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent request id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent id with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's answer act")
    void builderBuildsAnswered() {
        var canonical = new Answered(Channel.CLI, null, null, Instant.parse("2026-10-22T10:00:00Z"));

        var built = Answered.builder().channel(canonical.channel()).deviceId(canonical.deviceId())
                .reason(canonical.reason()).at(canonical.at()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Answered(null, canonical.deviceId(), canonical.reason(), canonical.at()),
                "the constructor refuses an absent channel");
        var incomplete = Answered.builder().deviceId(canonical.deviceId()).reason(canonical.reason())
                .at(canonical.at());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent channel");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent channel alike"));
    }
}
