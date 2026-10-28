/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import java.util.Objects;

/**
 * The redaction registry a Token Lane secret is registered with before it is sent (Credentials &amp; Enrolment §
 * Provider Credentials, PM-CRED-5): every read-out of a job log, an activity entry or an excerpt passes it, so no secret
 * a runtime holds reaches a model or the operator's screen.
 * <p>
 * The registry is a seam: the operator channel implements it, and the callers that hold a secret register it. A caller
 * that is handed no registry uses {@link #none()} and never sends a secret it did not register.
 *
 * @since 0.1
 */
public interface SecretRegistry {

    /**
     * @param secret a secret the runtime now holds
     */
    void register(String secret);

    /**
     * @param registry a registry
     * @return the registry, or one that keeps nothing when none was given
     */
    static SecretRegistry orNone(SecretRegistry registry) {
        return registry == null ? none() : registry;
    }

    /** @return a registry that keeps nothing, for a caller that redacts its own text */
    static SecretRegistry none() {
        return secret -> Objects.requireNonNull(secret, "secret");
    }
}
