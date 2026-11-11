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

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import de.planmarshall.core.queue.SecretRedactor;
import de.planmarshall.core.store.JobId;

/**
 * The evidence a build carries when it ended {@code failed_unparsed} (Job Runtime § Unparseable Output Fallback): a
 * bounded, redacted excerpt of the raw build log together with the resource URI of the full log.
 * <p>
 * The record deliberately carries no diagnostic. A build whose log could not be parsed has no located diagnostic to
 * hand on, and handing the model a made-up one would let it act on a compiler error that never existed. The excerpt is
 * the only content, and it says what the tool actually printed.
 * <p>
 * The excerpt is the tail of the raw log: the ANSI escapes are stripped through {@link BuildLogText#stripAnsi}, each
 * carriage-return frame run is reduced to its last frame, and the last {@value #MAX_LINES} lines are kept, bounded to at
 * most {@value #MAX_BYTES} bytes of UTF-8 counted from its end, so the byte bound can cut the earliest retained line.
 * The tail is taken by scanning back from the log's end, never by listing the log: a log of a million lines costs the
 * tail it keeps, not a list of every line.
 * Every secret-shaped value is replaced by {@link SecretRedactor}; the bound is applied after the redaction, because a
 * replacement can lengthen a line. The full log itself stays beside the job record as {@code <job_id>.log}, which is the
 * resource the URI names.
 *
 * @param excerpt    the bounded, redacted tail of the raw log; empty for an empty log
 * @param excerptUri the resource URI of the full raw log
 * @since 0.1
 */
public record UnparsedEvidence(String excerpt, String excerptUri) {

    /** The number of raw-log lines the excerpt keeps, counted from the end. */
    public static final int MAX_LINES = 50;

    /** The largest excerpt the evidence carries, in bytes of its UTF-8 form. */
    public static final int MAX_BYTES = 4096;

    /** The resource URI of the full log of a job, formatted with the plan id and the job id. */
    public static final String URI_TEMPLATE = "pm://plan/%s/builds/%s/log";

    /**
     * @throws NullPointerException     if the excerpt or the URI is {@code null}
     * @throws IllegalArgumentException if the URI is blank, or the excerpt exceeds the line or byte bound
     */
    public UnparsedEvidence {
        Objects.requireNonNull(excerpt, "excerpt");
        Objects.requireNonNull(excerptUri, "excerptUri");
        if (excerptUri.isBlank()) {
            throw new IllegalArgumentException("The excerpt URI must not be blank");
        }
        if (excerpt.lines().count() > MAX_LINES) {
            throw new IllegalArgumentException("The excerpt exceeds %d lines".formatted(MAX_LINES));
        }
        if (excerpt.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new IllegalArgumentException("The excerpt exceeds %d bytes".formatted(MAX_BYTES));
        }
    }

    /**
     * Builds the evidence of a build job from its raw log.
     *
     * @param planId the plan that owns the build job, never blank
     * @param jobId  the id of the build job
     * @param rawLog the raw job log, the launch report included
     * @return the bounded, redacted evidence and the URI of the full log
     * @throws NullPointerException     if the plan id, job id or raw log is {@code null}
     * @throws IllegalArgumentException if the plan id is blank
     */
    public static UnparsedEvidence of(String planId, JobId jobId, String rawLog) {
        if (Objects.requireNonNull(planId, "planId").isBlank()) {
            throw new IllegalArgumentException("The plan id must not be blank");
        }
        Objects.requireNonNull(jobId, "jobId");
        return new UnparsedEvidence(tail(Objects.requireNonNull(rawLog, "rawLog")),
                URI_TEMPLATE.formatted(planId, jobId.value()));
    }

    private static String tail(String rawLog) {
        String reduced = reduceCarriageReturns(BuildLogText.stripAnsi(rawLog));
        return capped(SecretRedactor.redactText(keptTail(reduced)).orElseThrow());
    }

    /**
     * The last {@value #MAX_LINES} lines of a text whose escapes and carriage-return frames are already reduced, taken
     * by scanning back from its end for line beginnings.
     * <p>
     * A build log is untrusted text of any length, and listing its lines would hold every one of them at once to keep
     * fifty. The scan reads the line ends from the end until it has counted the kept lines, so the memory the tail
     * costs is the tail's own. The line end of the last line is dropped, as joining the listed lines dropped it.
     *
     * @param reduced the log text, its escapes stripped and its carriage-return frames reduced
     * @return the tail, without the line end of its last line
     */
    static String keptTail(String reduced) {
        var tail = reduced.substring(tailStart(reduced));
        return tail.endsWith("\n") ? tail.substring(0, tail.length() - 1) : tail;
    }

    /** The index of the first character of the last {@value #MAX_LINES} lines, or {@code 0} for a shorter text. */
    private static int tailStart(String text) {
        var lines = 1;
        for (var index = text.length() - 1; index >= 0; index--) {
            if (text.charAt(index) == '\n' && index < text.length() - 1) {
                lines++;
                if (lines > MAX_LINES) {
                    return index + 1;
                }
            }
        }
        return 0;
    }

    /**
     * Reduces each carriage-return frame run to its last frame: an npm progress spinner overwrites one line with
     * {@code \r}, and every frame would otherwise count as a line of its own against the line bound, pushing the failure
     * out of the tail. The {@code \r\n} line ending is normalized first, so only a standalone {@code \r} ends a frame.
     *
     * @param text the text, with its ANSI escapes already stripped
     * @return the text with every {@code \r} run reduced to its last frame
     */
    private static String reduceCarriageReturns(String text) {
        String normalized = text.replace("\r\n", "\n");
        var reduced = new StringBuilder(normalized.length());
        for (String line : normalized.split("\n", -1)) {
            if (!reduced.isEmpty()) {
                reduced.append('\n');
            }
            var end = line.length();
            while (end > 0 && line.charAt(end - 1) == '\r') {
                end--;
            }
            var trimmed = line.substring(0, end);
            reduced.append(trimmed.substring(trimmed.lastIndexOf('\r') + 1));
        }
        return reduced.toString();
    }

    /**
     * Keeps the end of the text within the byte bound: an excerpt is the tail of a log, so its most recent part is the
     * part that matters. A cut never splits a UTF-8 character.
     */
    private static String capped(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= MAX_BYTES) {
            return text;
        }
        var from = bytes.length - MAX_BYTES;
        while (from < bytes.length && (bytes[from] & 0xC0) == 0x80) {
            from++;
        }
        return new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
    }
}
