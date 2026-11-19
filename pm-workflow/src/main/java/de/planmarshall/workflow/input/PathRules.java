/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.input;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The pure, syntactic path rules of Decision 3: relative, {@code /} only, no empty segment, no {@code .} or {@code ..}
 * segment, no NUL, control or format character, no leading {@code :} (git pathspec magic) or {@code -}, no {@code .git}
 * segment in any case, at most 1024 characters, no duplicate in a list.
 * <p>
 * Environmental rules (confinement, staging set membership) are checked by the engine at the bind.
 *
 * @since 0.1
 */
public final class PathRules {

    /** The most characters a single path may have. */
    public static final int MAX_PATH_LENGTH = 1024;

    private PathRules() {
    }

    /**
     * Checks a single path value against the syntactic rules.
     *
     * @param value the path to check
     * @return the refusal, or {@code null} when the path is accepted
     */
    public static @Nullable InputRefusal check(@Nullable String value) {
        Objects.requireNonNull(value, "value");
        var simple = checkSimple(value);
        if (simple != null) {
            return simple;
        }
        if (value.codePoints().anyMatch(PathRules::isControl)) {
            return new InputRefusal("path.control", value);
        }
        if (value.codePoints().anyMatch(Character::isWhitespace)) {
            return new InputRefusal("path.whitespace", value);
        }
        if (value.codePoints().anyMatch(PathRules::isBidi)) {
            return new InputRefusal("path.bidi", value);
        }
        var segment = checkSegments(value);
        if (segment != null) {
            return segment;
        }
        return value.contains("//") ? new InputRefusal("path.double-slash", value) : null;
    }

    private static @Nullable InputRefusal checkSimple(String value) {
        if (value.isEmpty()) {
            return new InputRefusal("path.empty", value);
        }
        if (value.length() > MAX_PATH_LENGTH) {
            return new InputRefusal("path.too-long", value);
        }
        if (value.startsWith("/")) {
            return new InputRefusal("path.absolute", value);
        }
        if (value.contains("\\")) {
            return new InputRefusal("path.backslash", value);
        }
        if (value.indexOf('\u0000') >= 0) {
            return new InputRefusal("path.nul", value);
        }
        if (value.startsWith(":") || value.startsWith("-")) {
            return new InputRefusal("path.magic", value);
        }
        return null;
    }

    private static @Nullable InputRefusal checkSegments(String value) {
        for (var segment : value.split("/", -1)) {
            if (segment.isEmpty()) {
                return new InputRefusal("path.empty-segment", value);
            }
            if (".".equals(segment) || "..".equals(segment)) {
                return new InputRefusal("path.dot-segment", value);
            }
            if (".git".equalsIgnoreCase(segment)) {
                return new InputRefusal("path.git", value);
            }
        }
        return null;
    }

    private static boolean isControl(int codePoint) {
        return codePoint < 0x20 || (codePoint >= 0x7f && codePoint <= 0x9f);
    }

    private static boolean isBidi(int codePoint) {
        return codePoint == 0x202a || codePoint == 0x202b || codePoint == 0x202c || codePoint == 0x202d
                || codePoint == 0x202e || codePoint == 0x2066 || codePoint == 0x2067 || codePoint == 0x2068
                || codePoint == 0x2069;
    }
}