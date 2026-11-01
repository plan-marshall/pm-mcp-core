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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads build-log text and removes the terminal control sequences a colour-coded log carries.
 * <p>
 * Stripping in the same step as reading is what makes it impossible for a parser to match a colour-coded line against
 * the plain-text patterns of {@link JvmCategoryPatterns}, which is why every parser strips its text through here or
 * through the {@code String} overload of its own {@code parse}.
 * <p>
 * Undecodable bytes are replaced rather than refused: a build log is untrusted text, and one broken byte must not make
 * the whole parse fail.
 *
 * @since 0.1
 */
@UtilityClass
public class BuildLogText {

    /** ANSI escapes: two-character escapes and CSI sequences, covering SGR colour and cursor movement. */
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B(?:[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");

    /**
     * Removes every ANSI escape sequence.
     *
     * @param text the text to clean
     * @return the text without escapes; text carrying none is returned unchanged
     */
    public static String stripAnsi(String text) {
        return ANSI_ESCAPE.matcher(text).replaceAll("");
    }

    /**
     * Reads a build log and strips its ANSI escape sequences.
     *
     * @param logFile the file to read
     * @return the file content without escapes, undecodable bytes replaced
     * @throws UncheckedIOException if the file cannot be read
     */
    public static String read(Path logFile) {
        try {
            return stripAnsi(new String(Files.readAllBytes(logFile), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the build log " + logFile, e);
        }
    }
}
