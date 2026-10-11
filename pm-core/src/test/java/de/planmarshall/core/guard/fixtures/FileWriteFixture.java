/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard.fixtures;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Control of the analysis boundary guard: a class calling {@link Files#write(Path, byte[], java.nio.file.OpenOption...)},
 * on which the rule must fail.
 */
public final class FileWriteFixture {

    private FileWriteFixture() {
    }

    /**
     * @param file the file to write
     * @param content the bytes to write to it
     * @throws IOException if the file cannot be written
     */
    public static void write(Path file, byte[] content) throws IOException {
        Files.write(file, content);
    }
}
