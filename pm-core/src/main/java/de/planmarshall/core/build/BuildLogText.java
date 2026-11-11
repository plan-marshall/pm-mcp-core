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
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.config.LimitProperties;

/**
 * Reads build-log text and removes the terminal control sequences a colour-coded log carries.
 * <p>
 * Stripping in the same step as reading is what makes it impossible for a parser to match a colour-coded line against
 * the plain-text patterns of {@link JvmCategoryPatterns}, which is why every parser strips its text through here or
 * through the {@code String} overload of its own {@code parse}.
 * <p>
 * Three forms are removed: an OSC string ({@code ESC ]} … terminated by BEL or {@code ESC \}), whose payload is the hyperlink
 * text an OSC 8 sequence wraps around a path; the two-character escapes such as the SGR colour codes; and the CSI
 * sequences, which also cover cursor movement.
 * <p>
 * Undecodable bytes are replaced rather than refused: a build log is untrusted text, and one broken byte must not make
 * the whole parse fail.
 * <p>
 * <em>A log longer than {@link #MAX_BYTES} is cut.</em> The read of a file takes at most {@link #MAX_BYTES} bytes, counted
 * from the end, and starts at the first line beginning inside them, so a parser sees the log's tail whole-lined: the
 * count lines, the summary and the failure tail are at the end, which is the part a parser needs and the part a build
 * tool cannot be trusted to keep short. A log at or below the cap is read whole, so no parser result changes for it.
 * The cap is data of {@link #LIMITS}, never a literal, and only editing that resource changes it.
 *
 * @since 0.1
 */
@UtilityClass
public class BuildLogText {

    /** The classpath location of the limits of a build log's read. */
    static final String LIMITS = "/de/planmarshall/core/build/build-limits.properties";

    /** The key of the most bytes of a build log that are read, counted from its end. */
    static final String MAX_BYTES_KEY = "pm.core.build.log-max-bytes";

    /**
     * The most bytes a read takes from a log; a longer log is read from its end.
     * <p>
     * Read from {@link #LIMITS} when the class initializes, so the value is changed by editing that resource.
     */
    public static final int MAX_BYTES = Math.toIntExact(LimitProperties.read(LIMITS, MAX_BYTES_KEY));

    /** ANSI escapes: OSC strings, two-character escapes and CSI sequences. */
    private static final Pattern ANSI_ESCAPE =
            Pattern.compile("\\u001B(?:\\][^\\u0007\\u001B]*(?:\\u0007|\\u001B\\\\)|[@-Z\\\\-_]|\\[[0-?]*[ -/]*[@-~])");

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
     * <p>
     * A log longer than {@link #MAX_BYTES} is cut: at most {@link #MAX_BYTES} bytes are taken, counted from its end and
     * from the first line beginning inside them.
     *
     * @param logFile the file to read
     * @return the file content without escapes, undecodable bytes replaced; the tail of a log longer than the cap
     * @throws UncheckedIOException if the file cannot be read
     */
    public static String read(Path logFile) {
        try {
            return stripAnsi(new String(readAtMostCap(logFile), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the build log " + logFile, e);
        }
    }

    private static byte[] readAtMostCap(Path logFile) throws IOException {
        try (var channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
            var size = channel.size();
            if (size <= MAX_BYTES) {
                return Files.readAllBytes(logFile);
            }
            return fromLineStart(read(channel, size - MAX_BYTES, size));
        }
    }

    private static byte[] read(FileChannel channel, long from, long to) throws IOException {
        var buffer = ByteBuffer.allocate((int) (to - from));
        var position = from;
        while (buffer.hasRemaining()) {
            var read = channel.read(buffer, position);
            if (read < 0) {
                break;
            }
            position += read;
        }
        var bytes = new byte[buffer.position()];
        buffer.flip().get(bytes);
        return bytes;
    }

    /**
     * Drops the head of a cut that begins inside a line, so the parsers see whole lines. A cut with no line beginning in
     * it is one long line and is kept as it is: emptying it would hide the only content the log has there.
     */
    private static byte[] fromLineStart(byte[] bytes) {
        for (var index = 0; index < bytes.length; index++) {
            if (bytes[index] == '\n') {
                return Arrays.copyOfRange(bytes, index + 1, bytes.length);
            }
        }
        return bytes;
    }
}
