/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.security.JobTokens;
import de.planmarshall.runtime.security.Secrets;

/** The registry over the job records: a token resolves to its generation, also after its revocation, which {@code revoked} reports. */
@DisplayName("Job token store")
class JobTokenStoreTest {

    private static final String GENERATION = "l-0123456789abcdef";
    private static final String TOKEN = "a-minted-job-token";

    @Test
    @DisplayName("a registered digest resolves to its generation")
    void registerAndResolve() {
        var store = new JobTokenStore();
        store.register(GENERATION, JobTokens.sha256Hex(TOKEN));
        assertEquals(GENERATION, store.resolve(Secrets.sha256(TOKEN)).orElseThrow().jobId());
        assertTrue(store.resolve(Secrets.sha256("another-token")).isEmpty());
    }

    @Test
    @DisplayName("a revoked token still resolves so the handler answers the ended job")
    void revoke() {
        var store = new JobTokenStore();
        store.register(GENERATION, JobTokens.sha256Hex(TOKEN));
        store.register("l-fedcba9876543210", JobTokens.sha256Hex("other"));
        store.revoke(GENERATION);
        assertEquals(GENERATION, store.resolve(Secrets.sha256(TOKEN)).orElseThrow().jobId());
        assertTrue(store.revoked(GENERATION));
        assertEquals("l-fedcba9876543210", store.resolve(Secrets.sha256("other")).orElseThrow().jobId());
        assertFalse(store.revoked("l-fedcba9876543210"));
    }
}
