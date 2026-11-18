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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The refusal of a change against an older revision (CLI &amp; Security § Concurrent Changes of a Setting): the first
 * change wins, and a later one is refused with {@code conflict}, 409, the current revision and the current value.
 */
@DisplayName("Settings conflict")
class SettingsConflictTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("Should carry the code, the status and both revisions")
    void carriesTheRefusal() {
        var file = directory.resolve("local-config.json");

        var conflict = new SettingsConflict(file, 1L, 4L, "mermaid");

        assertAll(
                () -> assertEquals("conflict", conflict.code(), "The error code is conflict"),
                () -> assertEquals(409, conflict.status(), "The refusal maps to HTTP 409"),
                () -> assertEquals(1L, conflict.expectedRevision(), "The change's revision is kept"),
                () -> assertEquals(4L, conflict.currentRevision(), "The document's revision is kept"),
                () -> assertEquals("mermaid", conflict.currentValue(), "The current value is carried"),
                () -> assertTrue(conflict.getMessage().contains("revision 1"),
                        "The message names the change's revision"),
                () -> assertTrue(conflict.getMessage().contains("holds 4"),
                        "The message names the document's revision"));
    }

    @Test
    @DisplayName("Should refuse a conflict that names the current revision")
    void refusesNoConflict() {
        assertThrows(IllegalArgumentException.class,
                () -> new SettingsConflict(directory.resolve("local-config.json"), 2L, 2L, "text"),
                "A conflict names another revision than the current one");
    }
}
