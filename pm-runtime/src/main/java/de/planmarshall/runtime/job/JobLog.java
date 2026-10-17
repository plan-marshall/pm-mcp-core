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
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process-Group Creation via
 * the {@code pm-exec} Launcher, launch report; Outcome Derivation, row 5).
 *
 * @since 0.1
 */
public final class JobLog {

    private final Path path;

    /**
     * @param path the job log, {@code <job_id>.log} beside the job record
     * @throws NullPointerException if the path is {@code null}
     */
    public JobLog(Path path) {
        this.path = Objects.requireNonNull(path, "path");
    }

    /** @return the path of the job log */
    public Path path() {
        return path;
    }

    /**
     * What an incremental read of the log answered.
     *
     * @since 0.1
     */
    public sealed interface Read permits Lines, Unavailable {
    }

    /**
     * What the read of the whole log of an ended job answered.
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
     * @param report     the launch report, taken from the first line by a read from offset {@code 0}; empty for a read
     *                   from a later offset and for a first line that is no report
     * @since 0.1
     */
    public record Lines(List<String> lines, long nextOffset, Optional<LaunchReport> report) implements Read {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the offset is negative
         */
        public Lines {
            lines = List.copyOf(Objects.requireNonNull(lines, "lines"));
            if (nextOffset < 0) {
                throw new IllegalArgumentException("nextOffset must not be negative: " + nextOffset);
            }
            Objects.requireNonNull(report, "report");
        }
    }

    /**
     * The whole log of an ended job, read to its end.
     *
     * @param reading the launch report and the job's output after it, or the whole log when it carries no report
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
     * @param detail what was observed
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
            return new Lines(List.of(), offset, Optional.empty());
        }
        var text = new String(bytes, 0, end + 1, StandardCharsets.UTF_8);
        var nextOffset = offset + end + 1;
        if (offset == 0 && LaunchReport.read(text) instanceof LaunchReport.Reported(var report, var output)) {
            return new Lines(output.lines().toList(), nextOffset, Optional.of(report));
        }
        return new Lines(text.lines().toList(), nextOffset, Optional.empty());
    }

    /**
     * Reads the whole log of a job that has ended.
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
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(path);
        } catch (NoSuchFileException _) {
            return missing();
        } catch (IOException e) {
            return unreadable(e);
        }
        if (bytes.length < observedLength) {
            return truncated(bytes.length, observedLength);
        }
        return new Complete(LaunchReport.read(new String(bytes, StandardCharsets.UTF_8)), bytes.length);
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
