/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the watch item that no class but {@link CommitMessages} writes the trailer: the production sources of this
 * module name the trailer key, in any case, only in {@code CommitMessages.java}. The matched control asserts that the
 * scan sees the one string literal of the key there, so an empty scan cannot pass.
 */
@DisplayName("Only CommitMessages writes the trailer")
class CoAuthoredBySourceScanTest {

    /** The production sources of this module, relative to the module directory surefire runs in. */
    private static final Path SOURCES = Path.of("src/main/java");

    /** The one writer of the trailer, relative to {@link #SOURCES}. */
    private static final String WRITER = "de/planmarshall/provider/git/primitive/CommitMessages.java";

    /** The trailer key in lower case, the form every match is made on. */
    private static final String KEY = "co-authored-by";

    /** The key as the string literal of the writer, the occurrence the control counts. */
    private static final Pattern LITERAL = Pattern.compile("\"Co-Authored-By\"");

    @Test
    @DisplayName("the trailer key appears in no production source but CommitMessages.java")
    void onlyTheWriterNamesTheKey() throws IOException {
        var sources = javaSources();

        var naming = sources.stream()
                .filter(source -> read(source).toLowerCase(Locale.ROOT).contains(KEY))
                .map(source -> SOURCES.relativize(source).toString().replace('\\', '/'))
                .toList();

        assertAll("trailer key scan",
                () -> assertTrue(sources.size() > 1, "The scan reads the production sources"),
                () -> assertEquals(List.of(WRITER), naming, "Only CommitMessages names the trailer key"));
    }

    @Test
    @DisplayName("the control sees the one string literal of the key in the writer")
    void controlSeesTheLiteral() {
        var writer = read(SOURCES.resolve(WRITER));

        assertEquals(1, LITERAL.matcher(writer).results().count(), "The writer holds the key literal once");
    }

    private static List<Path> javaSources() throws IOException {
        try (var walk = Files.walk(SOURCES)) {
            return walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static String read(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + source, e);
        }
    }
}
