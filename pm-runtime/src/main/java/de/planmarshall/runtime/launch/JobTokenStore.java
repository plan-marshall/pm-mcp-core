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

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.planmarshall.runtime.security.JobTokenRegistry;
import de.planmarshall.runtime.security.JobTokens;

/**
 * The registry over the job records that resolves a presented job token's SHA-256 to the generation it was minted for
 * (PM-SEC-5, Job Token). A token is registered at the spawn with the digest the job record already carries and revoked
 * with the job's fate; a revoked token still resolves, so the handler answers {@code job_ended} instead of a transport
 * refusal, and {@link #revoked(String)} says which generation ended. The registry holds no token and nothing a record
 * does not hold.
 *
 * @since 0.1
 */
public final class JobTokenStore implements JobTokenRegistry {

    private static final String GENERATION_ID = "generationId";

    private final Map<String, String> byDigest = new ConcurrentHashMap<>();
    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    /**
     * @param generationId   the worker's generation, equal to its job id
     * @param jobTokenSha256 the lowercase-hex SHA-256 stored in the job record
     */
    public void register(String generationId, String jobTokenSha256) {
        Objects.requireNonNull(generationId, GENERATION_ID);
        Objects.requireNonNull(jobTokenSha256, "jobTokenSha256");
        byDigest.put(jobTokenSha256, generationId);
        revoked.remove(generationId);
    }

    /**
     * Revokes the token of a generation with its fate. The digest stays resolvable, so the handler answers the ended
     * job rather than a transport refusal.
     *
     * @param generationId the worker's generation
     */
    public void revoke(String generationId) {
        Objects.requireNonNull(generationId, GENERATION_ID);
        revoked.add(generationId);
    }

    /**
     * @param generationId the worker's generation
     * @return whether the generation's token was revoked
     */
    public boolean revoked(String generationId) {
        Objects.requireNonNull(generationId, GENERATION_ID);
        return revoked.contains(generationId);
    }

    @Override
    public Optional<JobBinding> resolve(byte[] tokenSha256) {
        Objects.requireNonNull(tokenSha256, "tokenSha256");
        return Optional.ofNullable(byDigest.get(JobTokens.hex(tokenSha256))).map(JobBinding::new);
    }
}
