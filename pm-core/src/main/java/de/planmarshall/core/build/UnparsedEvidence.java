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
import java.util.List;
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
 * The excerpt is the last {@value #MAX_LINES} complete lines of the raw log, bounded to at most {@value #MAX_BYTES}
 * bytes of UTF-8 from its end, with every secret-shaped value replaced by {@link SecretRedactor}. The bound is applied
 * after the redaction, because a replacement can lengthen a line. The full log itself stays beside the job record as
 * {@code <job_id>.log}, which is the resource the URI names.
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
     * @throws IllegalArgumentException if the URI is blank
     */
    public UnparsedEvidence {
        Objects.requireNonNull(excerpt, "excerpt");
        Objects.requireNonNull(excerptUri, "excerptUri");
        if (excerptUri.isBlank()) {
            throw new IllegalArgumentException("The excerpt URI must not be blank");
        }
    }

    /**
     * Builds the evidence of a build job from its raw log.
     *
     * @param planId the plan that owns the build job, never blank
     * @param jobId  the id of the build job
     * @param rawLog the raw job log, the launch report included
     * @return the bounded, redacted evidence and the URI of the full log
     * @throws NullPointerException     if the job, its id or the raw log is {@code null}
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
        List<String> lines = rawLog.lines().toList();
        var from = Math.max(0, lines.size() - MAX_LINES);
        var redacted = SecretRedactor.redactText(String.join("\n", lines.subList(from, lines.size()))).orElse("");
        return capped(redacted);
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
