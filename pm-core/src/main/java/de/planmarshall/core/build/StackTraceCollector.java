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
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Attaches the stack-trace lines of a build log to the diagnostic the ported originals attach them to.
 * <p>
 * A stack line is a line whose stripped form starts with {@code at } or {@code Caused by:}. The ported
 * {@code plan-marshall} parsers run this collection after all issues are filed and always write to the last issue of the
 * list, so a later contiguous block replaces an earlier one and the last block of the log lands on the final diagnostic.
 * That is the original's behaviour and is ported as it stands: the port is not the place to fix it, and a different
 * attachment would make the expected values of the ported tests disagree with the original.
 *
 * @since 0.1
 */
@UtilityClass
class StackTraceCollector {

    /**
     * Returns the diagnostics with the collected stack trace attached to the last of them.
     *
     * @param lines       the log lines, in log order
     * @param diagnostics the filed diagnostics, in log order
     * @return the diagnostics, with at most the last one carrying a stack trace
     */
    static List<Diagnostic> attach(List<String> lines, List<Diagnostic> diagnostics) {
        if (diagnostics.isEmpty()) {
            return List.copyOf(diagnostics);
        }
        var collected = new ArrayList<>(diagnostics);
        var stackLines = new ArrayList<String>();
        for (String line : lines) {
            String stripped = line.strip();
            if (stripped.startsWith("at ") || stripped.startsWith("Caused by:")) {
                stackLines.add(stripped);
                continue;
            }
            if (!stackLines.isEmpty()) {
                collected.set(collected.size() - 1,
                        collected.getLast().withStackTrace(String.join("\n", stackLines)));
                stackLines.clear();
            }
        }
        if (!stackLines.isEmpty()) {
            collected.set(collected.size() - 1, collected.getLast().withStackTrace(String.join("\n", stackLines)));
        }
        return List.copyOf(collected);
    }
}
