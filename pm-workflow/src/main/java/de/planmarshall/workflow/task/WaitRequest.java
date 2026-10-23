/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import java.util.Objects;

import de.planmarshall.core.queue.QueueTaskRecord;

/**
 * What a worker's {@code pm_wait} asks the task protocol: a task of the caller's role, for the caller's generation.
 * <p>
 * The generation's digest and the role's current digest are both named. They differ after an artifact edit or an
 * assignment change: the generation is then offered no new task and ends, so a task queued before the edit goes to a
 * generation started with the new configuration.
 *
 * @param generationId     the caller's generation, {@code l-} and 16 hex digits
 * @param role             the role whose task the worker takes
 * @param generationDigest the digest the generation was started with
 * @param roleDigest       the role's current digest at the offer
 * @param reOfferBudget    the re-offer budget of the state whose task the worker takes
 * @since 0.1
 */
public record WaitRequest(String generationId, String role, String generationDigest, String roleDigest,
int reOfferBudget) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the generation, the role or a digest breaks its form, or the budget is
     *                                  negative
     */
    public WaitRequest {
        Objects.requireNonNull(generationId, "generationId");
        if (generationId.length() != 18 || !generationId.startsWith("l-")
                || !generationId.substring(2).chars().allMatch(character -> Character.digit(character, 16) >= 0)) {
            throw new IllegalArgumentException("Not a generation (l- and 16 hex digits): " + generationId);
        }
        Objects.requireNonNull(role, "role");
        if (!role.matches("[a-z][a-z0-9-]{1,31}")) {
            throw new IllegalArgumentException("Not a role name: " + role);
        }
        requireDigest(generationDigest, "generationDigest");
        requireDigest(roleDigest, "roleDigest");
        if (reOfferBudget < 0) {
            throw new IllegalArgumentException("A re-offer budget is not negative: " + reOfferBudget);
        }
    }

    private static void requireDigest(String digest, String name) {
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a SHA-256 digest for " + name + ": " + digest);
        }
    }

    /**
     * @param task a task the protocol considers for an offer
     * @return whether the task is of the caller's role
     */
    boolean matchesRole(QueueTaskRecord task) {
        return role.equals(task.role());
    }
}
