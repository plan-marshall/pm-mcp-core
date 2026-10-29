/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * One script a remembered {@link ConsentKind#RUN_CHECKOUT_SCRIPTS run_checkout_scripts} covers, with the digest it was
 * granted for (Store Schemas § Consent Before Acting): a later run asks again for a script that is not listed or whose
 * digest changed.
 *
 * @param path   the script, relative to the project root
 * @param sha256 the SHA-256 of the script, 64 lower-case hexadecimal digits
 * @since 0.1
 */
public record ConsentScript(String path, String sha256) {

    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    /** @throws IllegalArgumentException if the path is empty or the digest is not a SHA-256 digest */
    public ConsentScript {
        Objects.requireNonNull(path, "path");
        if (path.isEmpty()) {
            throw new IllegalArgumentException("A consent script names its path");
        }
        if (sha256 == null || !DIGEST.matcher(sha256).matches()) {
            throw new IllegalArgumentException("Not a SHA-256 digest of a consent script: " + sha256);
        }
    }
}
