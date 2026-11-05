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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;

/** Creates the symbolic links of the confinement tests, and skips a test on a platform that cannot create one. */
final class SymbolicLinks {

    private SymbolicLinks() {
    }

    /**
     * Creates a symbolic link and the directories above it; aborts the calling test if the platform or the file
     * system does not allow the link.
     *
     * @param link   the link to create
     * @param target what the link points to
     */
    static void createOrSkip(Path link, Path target) {
        try {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException e) {
            Assumptions.abort("symbolic links cannot be created here: " + e);
        }
    }
}
