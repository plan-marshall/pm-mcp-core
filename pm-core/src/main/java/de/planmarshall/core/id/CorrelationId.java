/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.id;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The {@code correlation_id} of a workflow engine advance: {@code cor-} and 16 random lower-case hexadecimal digits,
 * {@code ^cor-[0-9a-f]{16}$}.
 * <p>
 * The id names one engine advance: drawn once per {@code Run} (that is, per advance) and shared by every frame the
 * advance pushes and, in the later plans of the command-model groundwork, by every primitive command it runs. It is a
 * reference, not a secret or a capability: unique within the scope that owns the status, never reused.
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record CorrelationId(String value) {

    private static final Pattern FORM = Pattern.compile("cor-[0-9a-f]{16}");
    private static final int DRAWN_BYTES = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not {@code cor-} and 16 lower-case hexadecimal digits
     */
    public CorrelationId {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a correlation id (^cor-[0-9a-f]{16}$): " + value);
        }
    }

    /**
     * @param value a string that may be a correlation id
     * @return the id; empty if the string is not one
     */
    public static Optional<CorrelationId> parse(@Nullable String value) {
        return value != null && FORM.matcher(value).matches() ? Optional.of(new CorrelationId(value))
                : Optional.empty();
    }

    /**
     * @return a new id with 16 random digits
     */
    public static CorrelationId random() {
        return random(RANDOM);
    }

    /**
     * @param random the source of the digits
     * @return a new id with 16 digits drawn from the source
     */
    public static CorrelationId random(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        var bytes = new byte[DRAWN_BYTES];
        random.nextBytes(bytes);
        return new CorrelationId("cor-" + HexFormat.of().formatHex(bytes));
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}