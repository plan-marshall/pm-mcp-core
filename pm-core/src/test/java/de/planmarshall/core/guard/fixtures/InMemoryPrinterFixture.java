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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * Counter-control of the analysis boundary guard: a class printing to memory through a {@link PrintWriter} and a
 * {@link PrintStream}, which writes no file and on which the rule must hold.
 */
public final class InMemoryPrinterFixture {

    private InMemoryPrinterFixture() {
    }

    /**
     * @param line the line to print
     * @return the line printed by the writer, followed by the line printed by the stream
     */
    public static String print(String line) {
        var text = new StringWriter();
        try (var writer = new PrintWriter(text)) {
            writer.print(line);
        }
        var bytes = new ByteArrayOutputStream();
        try (var stream = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            stream.print(line);
        }
        return text + bytes.toString(StandardCharsets.UTF_8);
    }
}
