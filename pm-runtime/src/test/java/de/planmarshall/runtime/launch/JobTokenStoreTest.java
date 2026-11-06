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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.security.JobTokens;
import de.planmarshall.runtime.security.Secrets;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The registry over the job records: a token resolves to its generation, also after its revocation, which
 * {@code revoked} reports.
 *
 * <p>
 * The generations and the tokens are generated. The literal that stays is the contract: the generation shape
 * {@code l-} followed by sixteen lower-case hex digits.
 */
@EnableGeneratorController
@DisplayName("Job token store")
class JobTokenStoreTest {

    private String generation;
    private String token;

    @BeforeEach
    void setUp() {
        generation = generation();
        token = Generators.letterStrings(8, 32).next();
    }

    @Test
    @DisplayName("a registered digest resolves to its generation")
    void registerAndResolve() {
        var store = new JobTokenStore();
        store.register(generation, JobTokens.sha256Hex(token));
        assertEquals(generation, store.resolve(Secrets.sha256(token)).orElseThrow().jobId(),
                "a registered token resolves to its generation");
        assertTrue(store.resolve(Secrets.sha256(token + "-" + Generators.letterStrings(1, 8).next())).isEmpty(),
                "an unregistered token resolves to nothing");
    }

    @Test
    @DisplayName("a revoked token still resolves so the handler answers the ended job")
    void revoke() {
        var other = token + "-" + Generators.letterStrings(1, 8).next();
        var otherGeneration = generation();
        var store = new JobTokenStore();
        store.register(generation, JobTokens.sha256Hex(token));
        store.register(otherGeneration, JobTokens.sha256Hex(other));
        store.revoke(generation);
        assertEquals(generation, store.resolve(Secrets.sha256(token)).orElseThrow().jobId(),
                "a revoked token still resolves to its generation");
        assertTrue(store.revoked(generation), "the revoked generation is reported revoked");
        assertEquals(otherGeneration, store.resolve(Secrets.sha256(other)).orElseThrow().jobId(),
                "the other token resolves to its own generation");
        assertFalse(store.revoked(otherGeneration), "the other generation is not revoked");
    }

    /** @return a generation id: {@code l-} and sixteen lower-case hex digits */
    private static String generation() {
        return "l-" + RuntimeGenerators.sha256Digests().next().substring(0, 16);
    }
}
