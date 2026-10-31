/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.WorkerState;

/**
 * The report of one start failure of a role on a harness (PM-WORK-3, Start-Failure Guard): the role, the client id of
 * the harness and the failed worker's own error text, bounded and redacted.
 * <p>
 * The error text is the last lines of the failed worker's output, cut to its tail of at most {@value #MAX_DETAIL}
 * characters — the bound the job record's {@code harness_error} and the audit record carry — and redacted like every
 * job log: a minted job token or worker secret, the user information of a URI and an e-mail address are replaced before
 * the text leaves the runtime. A start that produced no output reports {@link #NO_OUTPUT}.
 * <p>
 * The value is operator and audit data only. The {@link StartFailureGuard} hands it to the audit log as {@code
 * workerStartFailed} and returns it to the assembly, whose clients show it to the operator; it is never rendered to a
 * model.
 *
 * @param role    the role whose start failed
 * @param harness the client id of the harness the start failed on
 * @param detail  the bounded, redacted error text of the failed worker, never blank
 * @since 0.1
 */
public record StartFailureReport(String role, String harness, String detail) {

    /** The longest error text the job record and the audit record admit, in characters. */
    public static final int MAX_DETAIL = WorkerState.MAX_HARNESS_ERROR;

    /** The text a start that produced no output is reported with. */
    public static final String NO_OUTPUT = "(no output)";

    /** The redirect the redaction writes in place of a hidden value. */
    private static final String REDACTED = "<redacted>";

    /** A minted job token, a secret whose shape the redactor recognises. */
    private static final Pattern JOB_TOKEN = Pattern.compile("\\bpmj_[A-Za-z0-9_-]{43}");

    /** A minted worker secret, a secret whose shape the redactor recognises. */
    private static final Pattern WORKER_SECRET = Pattern.compile("\\bpmw_[A-Za-z0-9_-]{43}");

    /**
     * The user information of a URI, which may carry a credential: everything of the authority up to its last
     * {@code @}, so a password with an unencoded {@code @} is removed whole.
     */
    private static final Pattern USER_INFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/?#\\s]*@");

    /** An e-mail address, which names a person rather than a start failure. */
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");

    /**
     * @throws NullPointerException     if a value is {@code null}
     * @throws IllegalArgumentException if the role or the harness is empty, or the detail is blank or longer than
     *                                  {@value #MAX_DETAIL} characters
     */
    public StartFailureReport {
        requireName(role, "role");
        requireName(harness, "harness");
        Objects.requireNonNull(detail, "detail");
        if (detail.isBlank() || detail.length() > MAX_DETAIL) {
            throw new IllegalArgumentException("'detail' is 1 to %d characters".formatted(MAX_DETAIL));
        }
    }

    /**
     * Reports one start failure over the failed worker's own output: the text is redacted first, so a replaced value is
     * cut by the same bound as the text around it, and then cut to its tail of at most {@value #MAX_DETAIL} characters.
     *
     * @param role    the role whose start failed
     * @param harness the client id of the harness the start failed on
     * @param logTail the failed worker's own error text; {@code null} and blank both report {@link #NO_OUTPUT}
     * @return the bounded, redacted report
     * @throws NullPointerException     if the role or the harness is {@code null}
     * @throws IllegalArgumentException if the role or the harness is empty
     */
    public static StartFailureReport of(String role, String harness, @Nullable String logTail) {
        return new StartFailureReport(role, harness, tail(logTail == null ? "" : redact(logTail)));
    }

    /** @return the text redacted of the credential shapes, the URI user information and the e-mail addresses */
    private static String redact(String text) {
        var redacted = JOB_TOKEN.matcher(text).replaceAll(REDACTED);
        redacted = WORKER_SECRET.matcher(redacted).replaceAll(REDACTED);
        redacted = USER_INFO.matcher(redacted).replaceAll("$1");
        return EMAIL.matcher(redacted).replaceAll(REDACTED);
    }

    /** @return the last {@value #MAX_DETAIL} characters of the text, or {@link #NO_OUTPUT} when it holds none */
    private static String tail(String text) {
        if (text.isBlank()) {
            return NO_OUTPUT;
        }
        return text.length() <= MAX_DETAIL ? text : text.substring(text.length() - MAX_DETAIL);
    }

    private static void requireName(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("'%s' must not be empty".formatted(name));
        }
    }
}
