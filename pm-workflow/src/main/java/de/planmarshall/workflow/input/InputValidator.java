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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.spi.validation.ConventionalCommitValidator;
import de.planmarshall.workflow.ast.InputType;
import org.jspecify.annotations.Nullable;

/**
 * Validates a value against an {@link InputType}: length in code points, the Conventional Commit and trailer refusal
 * through {@link ConventionalCommitValidator}, the path rules of Decision 3, the entry cap, and the duplicate check.
 * <p>
 * A refusal is returned as an {@link InputRefusal}, never as an exception.
 *
 * @since 0.1
 */
public final class InputValidator {

    private static final String MISSING_RULE = "input.missing";
    private static final String NULL_ECHO = "<null>";

    private InputValidator() {
    }

    /**
     * Validates a single text value against the given text-like input type.
     *
     * @param type  the input type ({@link InputType.Text} or {@link InputType.CommitMessage})
     * @param value the value to validate
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validateText(InputType type, @Nullable String value) {
        Objects.requireNonNull(type, "type");
        if (value == null) {
            return missing();
        }
        var bounds = bounds(type);
        var codePoints = value.codePointCount(0, value.length());
        if (codePoints < bounds[0]) {
            return Optional.of(new InputRefusal("input.too-short", value));
        }
        if (codePoints > bounds[1]) {
            return Optional.of(new InputRefusal("input.too-long", value));
        }
        if (type instanceof InputType.CommitMessage
                && !new ConventionalCommitValidator().validate(value).isEmpty()) {
            return Optional.of(new InputRefusal("input.commit-message", value));
        }
        return Optional.empty();
    }

    /**
     * Validates a single path value against {@link InputType.Path}.
     *
     * @param value the value to validate
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validatePath(@Nullable String value) {
        if (value == null) {
            return missing();
        }
        var refusal = PathRules.check(value);
        return refusal == null ? Optional.empty() : Optional.of(refusal);
    }

    /**
     * Validates a list of paths against {@link InputType.PathList}.
     *
     * @param type   the path list type
     * @param values the values to validate
     * @return empty when the value is accepted, the refusal otherwise
     */
    public static Optional<InputRefusal> validatePathList(InputType type, @Nullable List<String> values) {
        Objects.requireNonNull(type, "type");
        if (values == null) {
            return missing();
        }
        var bounds = bounds(type);
        if (values.size() < bounds[0] || values.size() > bounds[1]) {
            return Optional.of(new InputRefusal("input.count", values.toString()));
        }
        var seen = new HashSet<String>();
        for (var value : values) {
            var refusal = PathRules.check(value);
            if (refusal != null) {
                return Optional.of(refusal);
            }
            if (!seen.add(value)) {
                return Optional.of(new InputRefusal("input.duplicate", value));
            }
        }
        return Optional.empty();
    }

    private static Optional<InputRefusal> missing() {
        return Optional.of(new InputRefusal(MISSING_RULE, NULL_ECHO));
    }

    /**
     * @param type a bounded input type
     * @return the low and high bound, {@code 0} and {@link Integer#MAX_VALUE} when the type declares none
     */
    private static int[] bounds(InputType type) {
        var min = 0;
        var max = Integer.MAX_VALUE;
        var low = switch (type) {
            case InputType.Text(var lo, _) -> lo;
            case InputType.CommitMessage(var lo, _) -> lo;
            case InputType.PathList(var lo, _) -> lo;
            default -> null;
        };
        var high = switch (type) {
            case InputType.Text(_, var hi) -> hi;
            case InputType.CommitMessage(_, var hi) -> hi;
            case InputType.PathList(_, var hi) -> hi;
            default -> null;
        };
        if (low != null) {
            min = low;
        }
        if (high != null) {
            max = high;
        }
        return new int[]{min, max};
    }
}