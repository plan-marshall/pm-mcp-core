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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.LimitProperties;

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

    @Test
    @DisplayName("the cap is read from the module's limits resource")
    void capIsReadFromTheLimitsResource() {
        assertEquals(BuildLogText.MAX_BYTES, LimitProperties.read(BuildLogText.LIMITS, BuildLogText.MAX_BYTES_KEY),
                "the cap must come from the resource");
    }

    @Test
    @DisplayName("an alternate resource yields its own cap, never a fixed one")
    void capOfAnAlternateResource() {
        var alternate = "/de/planmarshall/core/build/alternate-build-limits.properties";

        assertEquals(42, LimitProperties.read(alternate, BuildLogText.MAX_BYTES_KEY),
                "the reader must return the value of the resource");
    }

    @Test
    @DisplayName("a log one byte under the cap is read whole")
    void readsALogUnderTheCapWhole(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES - 2);
        Path log = directory.resolve("under.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertEquals(BuildLogText.MAX_BYTES - 1, text.length(), "the whole log is read"),
                () -> assertTrue(text.startsWith("h"), "the head of the log is kept"),
                () -> assertTrue(text.endsWith("a"), "the end of the log is kept"));
    }

    @Test
    @DisplayName("a log one byte over the cap is read from its end")
    void readsALogOverTheCapFromItsEnd(@TempDir Path directory) throws Exception {
        var content = "h" + "a".repeat(BuildLogText.MAX_BYTES);
        Path log = directory.resolve("over.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertEquals(BuildLogText.MAX_BYTES, text.length(), "at most the cap is read"),
                () -> assertFalse(text.startsWith("h"), "the head of the log is cut"),
                () -> assertTrue(text.endsWith("a"), "the end of the log is kept"));
    }

    @Test
    @DisplayName("a cut log starts at a line beginning")
    void cutLogStartsAtALineBeginning(@TempDir Path directory) throws Exception {
        var line = "line " + Generators.letterStrings(1, 8).next() + "\n";
        var content = "head\n" + line.repeat(BuildLogText.MAX_BYTES / line.length() + 2);
        Path log = directory.resolve("lines.log");
        Files.writeString(log, content, StandardCharsets.UTF_8);

        var text = BuildLogText.read(log);

        assertAll(
                () -> assertTrue(text.getBytes(StandardCharsets.UTF_8).length <= BuildLogText.MAX_BYTES,
                        "at most the cap is read"),
                () -> assertTrue(text.startsWith("line "), "the cut starts at a line beginning"),
                () -> assertFalse(text.contains("head"), "the head of the log is cut"),
                () -> assertTrue(text.endsWith(line), "the last line is kept whole"));
    }
}
