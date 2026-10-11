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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Control of the analysis boundary guard: a class constructing a {@link PrintWriter} and a {@link PrintStream} on a
 * {@link File} and on a file name, on which the rule must fail once per construction.
 */
public final class PrintWriterFileFixture {

    private PrintWriterFileFixture() {
    }

    /**
     * @param file     the file to print to
     * @param fileName the name of the file to print to
     * @param line     the line to print
     * @throws FileNotFoundException if a file cannot be opened
     */
    public static void print(File file, String fileName, String line) throws FileNotFoundException {
        try (var byFile = new PrintWriter(file); var byName = new PrintWriter(fileName)) {
            byFile.println(line);
            byName.println(line);
        }
        try (var byFile = new PrintStream(file); var byName = new PrintStream(fileName)) {
            byFile.println(line);
            byName.println(line);
        }
    }
}
