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

import java.util.Optional;

/**
 * The pure, syntactic path rules of Decision 3: relative, {@code /} only, no empty segment, no {@code .} or {@code ..}
 * segment, no segment that begins or ends with a space or ends with a dot (a filesystem that folds them would reach
 * another name), no NUL, control or format character, no leading {@code :} (git pathspec magic) or {@code -}, no
 * {@code .git} segment in any case, at most {@value #MAX_PATH_LENGTH} characters, no duplicate in a list.
 * <p>
 * A space is a legal member of a path ({@code docs/my file.md} is bindable); every other whitespace code point is
 * refused, as are control, format and bidi characters. The glob characters {@code *}, {@code ?} and {@code [} are
 * admitted: both staging variants stage a path literally, so a file named {@code pages/[id].tsx} is bindable
 * (link:../adr/workflow-input/006-paths-are-admitted-at-the-edges-and-globs.adoc[ADR workflow-input/006]).
 * <p>
 * Environmental rules (confinement, staging set membership) are checked by the engine at the bind.
 *
 * @since 0.1
 */
public final class PathRules {

    /** The most characters a single path may have. */
    public static final int MAX_PATH_LENGTH = 1024;

    /** The one whitespace code point a path admits: the space. */
    private static final int SPACE = 0x20;

    private PathRules() {
    }

    /**
     * Checks a single path value against the syntactic rules.
     *
     * @param value the path to check, never {@code null}
     * @return empty when the path is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> check(String value) {
        var simple = checkSimple(value);
        if (simple.isPresent()) {
            return simple;
        }
        if (value.codePoints().anyMatch(PathRules::isControl)) {
            return Optional.of(new InputRefusal(InputValidator.RULE_PATH_CONTROL, value));
        }
        if (value.codePoints().anyMatch(PathRules::isRefusedWhitespace)) {
            return Optional.of(new InputRefusal(InputValidator.RULE_PATH_WHITESPACE, value));
        }
        if (value.codePoints().anyMatch(PathRules::isBidi)) {
            return Optional.of(new InputRefusal(InputValidator.RULE_PATH_BIDI, value));
        }
        if (value.codePoints().anyMatch(PathRules::isFormat)) {
            return Optional.of(new InputRefusal(InputValidator.RULE_PATH_FORMAT, value));
        }
        if (value.codePoints().anyMatch(PathRules::isSurrogate)) {
            return Optional.of(new InputRefusal(InputValidator.RULE_PATH_SURROGATE, value));
        }
        return checkSegments(value);
    }

    private static Optional<InputRefusal> checkSimple(String value) {
        if (value.isEmpty()) {
            return refusal(InputValidator.RULE_PATH_EMPTY, value);
        }
        if (value.codePointCount(0, value.length()) > MAX_PATH_LENGTH) {
            return refusal(InputValidator.RULE_PATH_TOO_LONG, value);
        }
        if (value.startsWith("/")) {
            return refusal(InputValidator.RULE_PATH_ABSOLUTE, value);
        }
        if (value.contains("\\")) {
            return refusal(InputValidator.RULE_PATH_BACKSLASH, value);
        }
        if (value.indexOf('\u0000') >= 0) {
            return refusal(InputValidator.RULE_PATH_NUL, value);
        }
        if (value.startsWith(":") || value.startsWith("-")) {
            return refusal(InputValidator.RULE_PATH_MAGIC, value);
        }
        return Optional.empty();
    }

    private static Optional<InputRefusal> checkSegments(String value) {
        for (var segment : value.split("/", -1)) {
            if (segment.isEmpty()) {
                return refusal(InputValidator.RULE_PATH_EMPTY_SEGMENT, value);
            }
            if (".".equals(segment) || "..".equals(segment)) {
                return refusal(InputValidator.RULE_PATH_DOT_SEGMENT, value);
            }
            if (segment.startsWith(" ") || segment.endsWith(" ")) {
                return refusal(InputValidator.RULE_PATH_EDGE_SPACE, value);
            }
            if (segment.endsWith(".")) {
                return refusal(InputValidator.RULE_PATH_TRAILING_DOT, value);
            }
            if (".git".equalsIgnoreCase(segment)) {
                return refusal(InputValidator.RULE_PATH_GIT, value);
            }
        }
        return Optional.empty();
    }

    private static Optional<InputRefusal> refusal(String rule, String value) {
        return Optional.of(new InputRefusal(rule, value));
    }

    private static boolean isControl(int codePoint) {
        return codePoint < 0x20 || (codePoint >= 0x7f && codePoint <= 0x9f);
    }

    /** Whitespace other than the space: a tab, a no-break space or any other separator is refused. */
    private static boolean isRefusedWhitespace(int codePoint) {
        return codePoint != SPACE && (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint));
    }

    private static boolean isBidi(int codePoint) {
        return codePoint == 0x202a || codePoint == 0x202b || codePoint == 0x202c || codePoint == 0x202d
                || codePoint == 0x202e || codePoint == 0x2066 || codePoint == 0x2067 || codePoint == 0x2068
                || codePoint == 0x2069;
    }

    private static boolean isFormat(int codePoint) {
        return Character.getType(codePoint) == Character.FORMAT;
    }

    /** A code point in the surrogate range: {@code codePoints()} yields one only for an unpaired surrogate. */
    private static boolean isSurrogate(int codePoint) {
        return codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE;
    }
}
