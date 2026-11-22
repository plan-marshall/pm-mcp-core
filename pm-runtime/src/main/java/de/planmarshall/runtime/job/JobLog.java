/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.config.LimitProperties;
import de.planmarshall.core.queue.SecretRedactor;
import de.planmarshall.runtime.job.JobEvidence.LogState;

/**
 * The job log of one job (PM-EXEC-4): the file that receives the job's {@code stdout} and {@code stderr}, its first
 * line the launch report of {@code pm-exec}.
 * <p>
 * The log is readable while the job runs: {@link #readLines(long)} reads the complete lines written after a byte
 * offset and answers the offset to continue from, so a reader follows the log as it grows without reading it twice. The
 * launch report is stripped once: a read from offset {@code 0} takes the first line as the report when it parses as
 * one ({@link LaunchReport#read(String)}), and a read from any later offset never strips a line, so a first output line
 * of the job that looks like a report stays in the output. {@link #readWhole(long)} reads the log once the job has
 * ended, for the outcome derivation.
 * <p>
 * A log that cannot serve a read is one of the states of row 5 of the outcome derivation: {@link LogState#MISSING} when
 * the file does not exist, {@link LogState#TRUNCATED} when it is shorter than what the runtime already read from it,
 * {@link LogState#UNREADABLE} when it exists and cannot be read. Such a read is a value ({@link Unavailable}), never an
 * exception.
 * <p>
 * Every read-out passes the redactor: {@link #readLines(long)} redacts the lines it answers and {@link #readWhole(long)}
 * the reading it hands on — the job's output, the reason it carries no report and an {@link Unavailable} detail — the
 * whole read after its size cap and both before the text leaves this class, so a credential the job printed reaches
 * neither a parser nor a model (Job Runtime § Pipeline and Subprocesses, the redaction of every read-out). A key block a
 * window carries only in part is masked from the window's start through its {@code END} line when that {@code END} is in
 * the window; a window that lies entirely inside the body and carries no marker of its own is the named residue.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process-Group Creation via
 * the {@code pm-exec} Launcher, launch report; Outcome Derivation, row 5).
 *
 * @param path the job log, {@code <job_id>.log} beside the job record
 * @since 0.1
 */
public record JobLog(Path path) {

    /** The classpath location of the limits of the job log. */
    static final String LIMITS = "/de/planmarshall/runtime/job/job-log-limits.properties";

    /** The key of the most bytes of a job log that are read, counted from its end. */
    static final String MAX_BYTES_KEY = "pm.runtime.job.log-max-bytes";

    /**
     * The most bytes a whole read takes from a log; a longer log is read as its first line and its last
     * {@code MAX_BYTES} bytes.
     * <p>
     * Read from {@link #LIMITS} when the class initializes, so the value is changed by editing that resource.
     */
    public static final int MAX_BYTES = Math.toIntExact(LimitProperties.read(LIMITS, MAX_BYTES_KEY));

    /**
     * The most bytes a whole read of an over-cap log searches for its first line, the launch report: a report is one
     * short JSON line, so a first line that does not end inside this head is no report and is not kept.
     */
    static final int FIRST_LINE_MAX_BYTES = 64 * 1024;

    /** @throws NullPointerException if the path is {@code null} */
    public JobLog {
        Objects.requireNonNull(path, "path");
    }

    /**
     * What an incremental read of the log answered.
     *
     * @since 0.1
     */
    public sealed interface Read permits Lines, Unavailable {
    }

    /**
     * What the read of the log of an ended job answered.
     *
     * @since 0.1
     */
    public sealed interface Whole permits Complete, Unavailable {
    }

    /**
     * The complete lines written after the offset a read started at.
     *
     * @param lines      the complete lines, without their line ends; a line still being written is not among them
     * @param nextOffset the byte offset to continue the next read from, just after the last complete line
     * @param report     the launch report, taken from the first line by a read from offset {@code 0}; {@code null} for
     *                   a read from a later offset and for a first line that is no report
     * @since 0.1
     */
    public record Lines(List<String> lines, long nextOffset, @Nullable LaunchReport report) implements Read {

        /**
         * @throws NullPointerException     if the lines are {@code null}
         * @throws IllegalArgumentException if the offset is negative
         */
        public Lines {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (nextOffset < 0) {
                throw new IllegalArgumentException("nextOffset must not be negative: " + nextOffset);
            }
        }
    }

    /**
     * The log of an ended job as the whole read answers it: the launch report and the job's output after it.
     *
     * @param reading the launch report and the job's output after it, or the log when it carries no report; a log over
     *                {@link #MAX_BYTES} is read as its first line and its last {@link #MAX_BYTES} bytes
     * @param length  the length of the log in bytes
     * @since 0.1
     */
    public record Complete(LaunchReport.Reading reading, long length) implements Whole {

        /** @throws NullPointerException if the reading is {@code null} */
        public Complete {
            Objects.requireNonNull(reading, "reading");
        }
    }

    /**
     * The log could not serve the read.
     *
     * @param state  why: {@link LogState#MISSING}, {@link LogState#TRUNCATED} or {@link LogState#UNREADABLE}
     * @param detail what was observed; its credential shapes are redacted here, so a path or an exception message cannot
     *               carry a secret out
     * @since 0.1
     */
    public record Unavailable(LogState state, String detail) implements Read, Whole {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the state is {@link LogState#READABLE}
         */
        public Unavailable {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(detail, "detail");
            if (state == LogState.READABLE) {
                throw new IllegalArgumentException("A readable log is available");
            }
            detail = SecretRedactor.redactText(detail).orElseThrow();
        }
    }

    /**
     * Reads the complete lines written after an offset, while the job may still be writing.
     *
     * @param offset the byte offset to start at: {@code 0}, or the {@link Lines#nextOffset()} of the previous read
     * @return the complete lines after the offset and the offset to continue from, or why the log cannot be read; a log
     *         shorter than the offset is {@link LogState#TRUNCATED}
     * @throws IllegalArgumentException if the offset is negative
     */
    public Read readLines(long offset) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative: " + offset);
        }
        byte[] bytes;
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            var size = channel.size();
            if (size < offset) {
                return truncated(size, offset);
            }
            bytes = read(channel, offset, size);
        } catch (NoSuchFileException _) {
            return missing();
        } catch (IOException e) {
            return unreadable(e);
        }
        var end = lastLineEnd(bytes);
        if (end < 0) {
            return new Lines(List.of(), offset, null);
        }
        var text = new String(bytes, 0, end + 1, StandardCharsets.UTF_8);
        var nextOffset = offset + end + 1;
        if (offset == 0 && LaunchReport.read(text) instanceof LaunchReport.Reported(var report, var output)) {
            return new Lines(redactedLines(output, offset > 0), nextOffset, report);
        }
        return new Lines(redactedLines(text, offset > 0), nextOffset, null);
    }

    /**
     * Reads the log of a job that has ended.
     * <p>
     * A log longer than {@link #MAX_BYTES} is cut: it is read as its first line and its last {@link #MAX_BYTES} bytes,
     * counted from the first line beginning inside them. The first line is kept because the launch report is the first
     * line, and a launcher failure is decided from it: a tail read alone would report {@code Unreported} and lose the
     * launcher-failure decision. The cut is not {@link LogState#TRUNCATED}: that state means the log is shorter than
     * what the runtime already read from it, and an over-cap log is longer, not shorter. The first line is looked for in
     * at most {@link #FIRST_LINE_MAX_BYTES} bytes read from the file start, so a report longer than the overshoot is
     * kept; a first line longer than that head is not kept and the tail alone is answered, so a cut read never takes
     * more than that head and the cap. A log that shrinks between the size read and the read of its content is
     * {@link LogState#TRUNCATED} too: a short read is never reported as a complete log. A log that grows instead loses
     * nothing and is answered as the content read, with the length read before.
     *
     * @param observedLength the number of bytes the runtime already read from the log while the job ran; {@code 0} when
     *                       it read nothing
     * @return the launch report and the job's output, or why the log cannot be read; a log shorter than the observed
     *         length is {@link LogState#TRUNCATED}
     * @throws IllegalArgumentException if the observed length is negative
     */
    public Whole readWhole(long observedLength) {
        if (observedLength < 0) {
            throw new IllegalArgumentException("observedLength must not be negative: " + observedLength);
        }
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            var size = channel.size();
            if (size < observedLength) {
                return truncated(size, observedLength);
            }
            var text = size <= MAX_BYTES ? whole(channel, size) : capped(channel, size);
            var remaining = channel.size();
            if (remaining < size) {
                return truncated(remaining, size);
            }
            // the tail rule of a key block belongs to a read that starts inside the log, never to one from its start
            return new Complete(redacted(LaunchReport.read(text), size > MAX_BYTES), size);
        } catch (NoSuchFileException _) {
            return missing();
        } catch (IOException e) {
            return unreadable(e);
        }
    }

    private static String whole(FileChannel channel, long size) throws IOException {
        return new String(read(channel, 0, size), StandardCharsets.UTF_8);
    }

    /** Redacts a read-out before the text leaves this class; the whole read reaches it after its size cap. */
    private static String redacted(String text, boolean tailWindow) {
        return SecretRedactor.redactText(tailWindow ? SecretRedactor.redactKeyBlockTail(text) : text).orElseThrow();
    }

    /** Redacts the two texts of a whole read: the job's output and the reason it carries no report. */
    private static LaunchReport.Reading redacted(LaunchReport.Reading reading, boolean tailWindow) {
        return switch (reading) {
            case LaunchReport.Reported(var report, var jobOutput) ->
                new LaunchReport.Reported(report, redacted(jobOutput, tailWindow));
            case LaunchReport.Unreported(var detail, var jobOutput) ->
                new LaunchReport.Unreported(redacted(detail, tailWindow), redacted(jobOutput, tailWindow));
        };
    }

    /** The complete lines of a redacted read-out. */
    private static List<String> redactedLines(String text, boolean tailWindow) {
        return redacted(text, tailWindow).lines().toList();
    }

    /**
     * The first line and the last {@link #MAX_BYTES} bytes of a log longer than the cap. The first line is looked for in
     * a head of at most {@link #FIRST_LINE_MAX_BYTES} bytes read from the file start, so a head shorter than the first
     * line does not lose the report: the tail window then starts inside that first line, and the tail is trimmed back to
     * the next line beginning, so the two parts never overlap. A first line longer than
     * {@link #FIRST_LINE_MAX_BYTES} is not kept: the tail alone is answered.
     */
    private static String capped(FileChannel channel, long size) throws IOException {
        var head = read(channel, 0, Math.min(FIRST_LINE_MAX_BYTES, size));
        var tail = new String(tail(channel, size), StandardCharsets.UTF_8);
        var end = firstLineEnd(head);
        if (end < 0) {
            return tail;
        }
        return new String(head, 0, end + 1, StandardCharsets.UTF_8) + tail;
    }

    /**
     * The last {@link #MAX_BYTES} bytes, starting at a line beginning: at the window's own start when the byte before
     * it ends a line, else after the first line end inside it. A window with no line end is one long line and is kept.
     */
    private static byte[] tail(FileChannel channel, long size) throws IOException {
        var from = size - MAX_BYTES;
        var bytes = read(channel, from, size);
        if (bytes.length == 0) {
            return bytes;
        }
        var previous = read(channel, from - 1, from);
        return previous.length > 0 && previous[0] == '\n' ? bytes : fromLineStart(bytes);
    }

    /**
     * The end of the first line of a head, or {@code -1} when the head holds no complete line: the first line is then
     * longer than the head, so the bounded read does not take it and the tail is answered without it.
     */
    private static int firstLineEnd(byte[] head) {
        for (var index = 0; index < head.length; index++) {
            if (head[index] == '\n') {
                return index;
            }
        }
        return -1;
    }

    /**
     * Drops the head of a cut that begins inside a line, so the output after the first line starts at a line beginning.
     * A cut with no line beginning in it is one long line and is kept as it is: emptying it would hide the only
     * content the log has there. The same holds for a cut whose only line end is its last byte, because no line begins
     * after it.
     */
    static byte[] fromLineStart(byte[] bytes) {
        for (var index = 0; index < bytes.length - 1; index++) {
            if (bytes[index] == '\n') {
                return Arrays.copyOfRange(bytes, index + 1, bytes.length);
            }
        }
        return bytes;
    }

    private static byte[] read(FileChannel channel, long offset, long size) throws IOException {
        var length = size - offset;
        if (length > Integer.MAX_VALUE - 8) {
            throw new IOException("The log after offset %d is too long to read at once: %d bytes".formatted(offset,
                    length));
        }
        var buffer = ByteBuffer.allocate((int) length);
        var position = offset;
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

    private static int lastLineEnd(byte[] bytes) {
        for (var index = bytes.length - 1; index >= 0; index--) {
            if (bytes[index] == '\n') {
                return index;
            }
        }
        return -1;
    }

    private Unavailable missing() {
        return new Unavailable(LogState.MISSING, "the job log does not exist: " + path);
    }

    private Unavailable truncated(long length, long expected) {
        return new Unavailable(LogState.TRUNCATED, "the job log %s holds %d bytes, %d were already read from it"
                .formatted(path, length, expected));
    }

    private Unavailable unreadable(IOException e) {
        return new Unavailable(LogState.UNREADABLE, "the job log %s cannot be read: %s".formatted(path,
                e.getMessage()));
    }
}
