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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ScopeTransaction.PendingTarget;
import de.planmarshall.core.store.TransactionJournal.Mode;

@DisplayName("Scope transaction builder contract")
class ScopeTransactionBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's pending target")
    void builderBuildsPendingTarget() {
        var canonical = new PendingTarget("mcp/status.json", Mode.REPLACE, "{}".getBytes(UTF_8), false);

        var built = PendingTarget.builder().path(canonical.path()).mode(canonical.mode())
                .content(canonical.content()).mustBeAbsent(canonical.mustBeAbsent()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
