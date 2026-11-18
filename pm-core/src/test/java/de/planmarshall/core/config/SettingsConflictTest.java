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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The parts of {@link SettingsConflict} that the stores' own tests do not reach: the guard that a conflict names another
 * revision than the document's, and the message that states both revisions.
 */
@DisplayName("Settings conflict")
class SettingsConflictTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("Should state both revisions in the message")
    void statesBothRevisions() {
        var file = directory.resolve("local-config.json");

        var conflict = new SettingsConflict(file, 1L, 4L, "mermaid");

        assertAll(
                () -> assertTrue(conflict.getMessage().contains("revision 1"),
                        "The message names the change's revision"),
                () -> assertTrue(conflict.getMessage().contains("holds 4"),
                        "The message names the document's revision"),
                () -> assertTrue(conflict.getMessage().contains(file.toString()),
                        "The message names the refused document"));
    }

    @Test
    @DisplayName("Should refuse a conflict that names the current revision")
    void refusesNoConflict() {
        assertThrows(IllegalArgumentException.class,
                () -> new SettingsConflict(directory.resolve("local-config.json"), 2L, 2L, "text"),
                "A conflict names another revision than the current one");
    }
}
