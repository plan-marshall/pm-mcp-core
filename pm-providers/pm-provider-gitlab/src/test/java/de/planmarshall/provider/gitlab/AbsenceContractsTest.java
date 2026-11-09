/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.gitlab;

import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The absent case of the invocation component this conversion changed: a {@code null} input is the absence that
 * {@code Optional.empty()} carried before, and the accessor returns it unchanged.
 *
 * @since 0.1
 */
class AbsenceContractsTest {

    @Test
    @DisplayName("an invocation without standard input keeps null")
    void invocationWithoutStdin() {
        assertNull(new GlabCommands.Invocation(List.of("glab"), null).stdin(),
                "an invocation without stdin is absent");
    }
}
