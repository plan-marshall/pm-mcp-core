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
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (cp < 0x20 || (cp >= 0x7f && cp <= 0x9f)) {
                return new InputRefusal("path.control", value);
            }
            if (Character.isWhitespace(cp)) {
                return new InputRefusal("path.whitespace", value);
            }
            i += Character.charCount(cp);
        }
        // bidi and format characters
        for (int i = 0; i < value.length();) {
            int cp = value.codePointAt(i);
            if (cp == 0x202a || cp == 0x202b || cp == 0x202c || cp == 0x202d || cp == 0x202e
                    || cp == 0x2066 || cp == 0x2067 || cp == 0x2068 || cp == 0x2069) {
                return new InputRefusal("path.bidi", value);
            }
            i += Character.charCount(cp);
        }
        var segments = value.split("/", -1);
        for (var segment : segments) {
            if (segment.isEmpty()) {
                return new InputRefusal("path.empty-segment", value);
            }
            if (segment.equals(".") || segment.equals("..")) {
                return new InputRefusal("path.dot-segment", value);
            }
            if (segment.equalsIgnoreCase(".git")) {
                return new InputRefusal("path.git", value);
            }
        }
        if (value.contains("//")) {
            return new InputRefusal("path.double-slash", value);
        }
        return null;
    }
}