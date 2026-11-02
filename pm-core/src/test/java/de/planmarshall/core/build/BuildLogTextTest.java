/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reads build-log text and strips the terminal control sequences a colour-coded log carries.
 */
@EnableGeneratorController
@DisplayName("BuildLogText")
class BuildLogTextTest {

    @Test
    @DisplayName("strips SGR colour codes and leaves plain text unchanged")
    void stripsAnsiEscapes() {
        var word = Generators.letterStrings(4, 12).next();
        var coloured = "\u001B[1;31m[ERROR]\u001B[0m " + word;
        var linked = "\u001B]8;;file:///p/Foo.java\u0007[ERROR] /p/Foo.java:[7,3] cannot find symbol\u001B]8;;\u0007";
        var linkedEsc = "\u001B]8;;file:///p/Foo.java\u001B\\[ERROR] /p/Foo.java:[7,3] cannot find symbol"
                + "\u001B]8;;\u001B\\";

        assertAll(
                () -> assertEquals("[ERROR] " + word, BuildLogText.stripAnsi(coloured),
                        "The colour codes are removed"),
                () -> assertEquals(word, BuildLogText.stripAnsi(word), "Plain text is returned unchanged"),
                () -> assertEquals("[WARNING] x", BuildLogText.stripAnsi("[WARNING] x"),
                        "Text without escapes is untouched"),
                () -> assertEquals("[ERROR] /p/Foo.java:[7,3] cannot find symbol",
                        BuildLogText.stripAnsi(linked), "A BEL-terminated hyperlink is removed"),
                () -> assertEquals("[ERROR] /p/Foo.java:[7,3] cannot find symbol",
                        BuildLogText.stripAnsi(linkedEsc), "An ESC-backslash hyperlink is removed"));
    }

    @Test
    @DisplayName("reads a log and replaces undecodable bytes")
    void readsAndReplacesBrokenBytes(@TempDir Path directory) throws Exception {
        Path log = directory.resolve("build.log");
        byte[] content = {'a', (byte) 0xFF, 'b', '\n'};
        Files.write(log, content);

        String text = BuildLogText.read(log);

        assertAll(
                () -> assertTrue(text.startsWith("a"), "The readable prefix is kept"),
                () -> assertTrue(text.endsWith("b\n"), "The readable suffix is kept"),
                () -> assertEquals(4, text.length(), "The broken byte becomes one replacement character"));
    }

    @Test
    @DisplayName("refuses a log file that cannot be read")
    void refusesMissingFile(@TempDir Path directory) {
        var missing = directory.resolve("absent.log");

        var failure = assertThrows(UncheckedIOException.class, () -> BuildLogText.read(missing),
                "A missing log is reported as an unreadable log");

        assertTrue(failure.getMessage().contains("absent.log"), "The refusal names the missing log");
    }
}
