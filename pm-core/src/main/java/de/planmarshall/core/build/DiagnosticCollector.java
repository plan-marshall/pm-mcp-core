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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.queue.SecretRedactor;

/**
 * The deduplication, filing, redaction and message truncation the JVM parsers share.
 * <p>
 * The ported parsers deduplicate by category, file, line and the first 100 characters of the message, so the reactor
 * summary and the per-module output of one error produce one diagnostic. Every message passes through
 * {@link #stored(String)}, which redacts the credential shapes and then stores at most 500 characters of the message:
 * the redaction runs first, so a cut value cannot keep a prefix the redactor would have matched whole. The
 * deduplication key is computed from that redacted message, so two lines that differ only in a redacted value are one
 * diagnostic. Both parsers use this one implementation, so the two identities cannot drift apart, and every parser that
 * files a {@link Diagnostic} passes its message through {@code stored} (the OpenRewrite log parser files its own
 * {@code RewriteFinding} records, which redact their path and message in their constructor); the collected stack trace
 * reaches the same redaction and the same bound in the {@link Diagnostic} constructor, which redacts the file too.
 *
 * @since 0.1
 */
@UtilityClass
class DiagnosticCollector {

    private static final int DEDUP_MESSAGE_LENGTH = 100;

    private static final int MAX_MESSAGE_LENGTH = 500;

    /**
     * Files the diagnostics one line classifier yields, dropping a duplicate of one already filed.
     *
     * @param lines      the log lines, in log order
     * @param classifier the per-line classifier, returning an empty {@link Optional} for a line that is no diagnostic
     * @return the filed diagnostics, in log order
     */
    static List<Diagnostic> collect(List<String> lines, Function<String, Optional<Diagnostic>> classifier) {
        var diagnostics = new ArrayList<Diagnostic>();
        Set<String> seen = new HashSet<>();
        for (String line : lines) {
            Optional<Diagnostic> diagnostic = classifier.apply(line);
            if (diagnostic.isPresent() && seen.add(key(diagnostic.get()))) {
                diagnostics.add(diagnostic.get());
            }
        }
        return List.copyOf(diagnostics);
    }

    /**
     * Returns the deduplication key of a diagnostic.
     *
     * @param diagnostic the diagnostic to key
     * @return the key: category, file, line and the first 100 characters of the message
     */
    static String key(Diagnostic diagnostic) {
        return diagnostic.category().name() + ":" + diagnostic.file() + ":" + diagnostic.line() + ":"
                + truncate(diagnostic.message(), DEDUP_MESSAGE_LENGTH);
    }

    /**
     * Redacts a message and truncates it to the stored length.
     * <p>
     * The redaction runs before the cut, so a value the cut would split cannot keep a prefix the redactor would have
     * matched whole. Every parser files its message through here, so one seam covers all of them.
     *
     * @param message the message
     * @return the redacted message, or its first {@value #MAX_MESSAGE_LENGTH} characters
     */
    static String stored(String message) {
        return truncate(SecretRedactor.redactText(message).orElseThrow(), MAX_MESSAGE_LENGTH);
    }

    /**
     * Redacts and truncates a message, or reports it absent when only whitespace or invisible characters are left after
     * the redaction. A producer skips such a line: {@link Diagnostic} rejects a blank message, so filing it would fail
     * the parse of the whole log.
     *
     * @param message the message
     * @return the redacted stored message, or {@link Optional#empty()} when nothing visible is left
     */
    static Optional<String> storedOrEmpty(String message) {
        String stored = stored(message);
        return stored.isBlank() ? Optional.empty() : Optional.of(stored);
    }

    /**
     * Redacts a collected stack trace and cuts it to the stored length, never splitting a surrogate pair.
     * <p>
     * A stack frame's credential therefore cannot leave a longer residue than a stored message, and the store never
     * holds half of a pair.
     *
     * @param stackTrace the frames collected for a diagnostic, joined by {@code \n}
     * @return the redacted stack trace, or its first {@value #MAX_MESSAGE_LENGTH} characters
     */
    static String storedTrace(String stackTrace) {
        var redacted = SecretRedactor.redactText(stackTrace).orElseThrow();
        if (redacted.length() <= MAX_MESSAGE_LENGTH) {
            return redacted;
        }
        var end = Character.isHighSurrogate(redacted.charAt(MAX_MESSAGE_LENGTH - 1))
                ? MAX_MESSAGE_LENGTH - 1
                : MAX_MESSAGE_LENGTH;
        return redacted.substring(0, end);
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
