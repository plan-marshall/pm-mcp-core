/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;

/**
 * The builder of {@link HarnessVerification.Request} builds the value its canonical constructor builds, so a caller can
 * fill eight components by name without changing the admission.
 */
@DisplayName("Harness verification request builder")
class HarnessVerificationBuilderContractTest {

    @Test
    @DisplayName("the request builder builds the constructor's value")
    void shouldBuildRequestLikeTheConstructor() {
        var canonical = new HarnessVerification.Request(HarnessVerification.VERIFY_STATE,
                ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES, List.of("identity"),
                Map.of("identity", 1L), "{}", "draft/acme-cli.json", ProfileRules.none());

        var built = HarnessVerification.Request.builder()
                .state(canonical.state())
                .kind(canonical.kind())
                .answer(canonical.answer())
                .stages(canonical.stages())
                .stageTokens(canonical.stageTokens())
                .draft(canonical.draft())
                .draftName(canonical.draftName())
                .rules(canonical.rules())
                .build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
        var refusal = assertThrows(NullPointerException.class,
                () -> HarnessVerification.Request.builder().draft("{}").build(),
                "the builder refuses what the constructor refuses");
        assertEquals("state", refusal.getMessage(), "the refusal names the first missing component");
    }
}
