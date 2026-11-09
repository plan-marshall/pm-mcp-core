/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The absent case of the git contract components this conversion changed: a {@code null} input is the absence that
 * {@code Optional.empty()} carried before, and the accessor returns it unchanged.
 *
 * @since 0.1
 */
class AbsenceContractsTest {

    @Test
    @DisplayName("a failed git result carries no value")
    void failedGitResultWithoutValue() {
        var result = new GitResult<String>(GitOutcome.WORKTREE_NOT_FOUND, null, "missing");

        assertNull(result.value(), "a failed git result carries no value");
    }

    @Test
    @DisplayName("a local-only remote policy has no origin")
    void localOnlyRemotePolicyHasNoOrigin() {
        assertNull(RemotePolicy.localOnly().origin(), "a local-only policy has no origin");
    }
}
