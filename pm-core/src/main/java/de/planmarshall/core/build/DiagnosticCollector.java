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

/**
 * The deduplication, filing and message truncation the JVM parsers share.
 * <p>
 * The ported parsers deduplicate by category, file, line and the first 100 characters of the message, so the reactor
 * summary and the per-module output of one error produce one diagnostic, and they store at most 500 characters of the
 * message. Both parsers use this one implementation, so the two identities cannot drift apart.
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
     * Truncates a message to the stored length.
     *
     * @param message the message
     * @return the message, or its first {@value #MAX_MESSAGE_LENGTH} characters
     */
    static String stored(String message) {
        return truncate(message, MAX_MESSAGE_LENGTH);
    }

    private static String truncate(String text, int maxLength) {
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
