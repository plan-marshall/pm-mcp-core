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
            return Optional.of(new InputRefusal("input.missing", "<null>"));
        }
        int min = 0;
        int max = Integer.MAX_VALUE;
        if (type instanceof InputType.Text(var lo, var hi)) {
            if (lo != null) {
                min = lo;
            }
            if (hi != null) {
                max = hi;
            }
        } else if (type instanceof InputType.CommitMessage(var lo, var hi)) {
            if (lo != null) {
                min = lo;
            }
            if (hi != null) {
                max = hi;
            }
        }
        var codePoints = value.codePointCount(0, value.length());
        if (codePoints < min) {
            return Optional.of(new InputRefusal("input.too-short", value));
        }
        if (codePoints > max) {
            return Optional.of(new InputRefusal("input.too-long", value));
        }
        if (type instanceof InputType.CommitMessage) {
            var validator = new ConventionalCommitValidator();
            var violations = validator.validate(value);
            if (!violations.isEmpty()) {
                return Optional.of(new InputRefusal("input.commit-message", value));
            }
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
            return Optional.of(new InputRefusal("input.missing", "<null>"));
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
            return Optional.of(new InputRefusal("input.missing", "<null>"));
        }
        int min = 0;
        int max = Integer.MAX_VALUE;
        if (type instanceof InputType.PathList(var lo, var hi)) {
            if (lo != null) {
                min = lo;
            }
            if (hi != null) {
                max = hi;
            }
        }
        if (values.size() < min || values.size() > max) {
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
}