/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Objects;

/**
 * A skill a role names, as the project resolves it (PM-WORK-1, Role Artifact Schema): the skill URI and the SHA-256 of
 * its files at the read. The digest enters both digests of the role, so an edited extension skill makes the role's
 * qualification stale.
 *
 * @param uri    the skill URI, {@code skill://pm/.../SKILL.md}
 * @param digest the SHA-256 of the skill's files, 64 lower-case hexadecimal digits
 * @since 0.1
 */
public record RoleSkill(String uri, String digest) {

    /**
     * @throws IllegalArgumentException if the URI or the digest is malformed
     */
    public RoleSkill {
        Objects.requireNonNull(uri, "uri");
        if (!uri.startsWith("skill://pm/") || !uri.endsWith("/SKILL.md")) {
            throw new IllegalArgumentException("Not a skill URI: " + uri);
        }
        if (digest == null || !digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a SHA-256 digest: " + digest);
        }
    }
}
