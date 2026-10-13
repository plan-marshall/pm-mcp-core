/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * The {@code generation_id} of an interactive session (PM-WORK-5): {@code sg-} and 16 lower-case hexadecimal digits.
 * A worker generation is its {@code job_id} ({@code l-} form) and is no value of this type.
 * <p>
 * The id is the key of its entry in the session generation record {@code queue/session-generations.json}. A new id is
 * drawn at random; whether it is free is decided by the {@link SessionGenerationStore} under the queue lock, which
 * draws again on a collision.
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record GenerationId(String value) {

    private static final Pattern FORM = Pattern.compile("sg-[0-9a-f]{16}");
    private static final String PREFIX = "sg-";
    private static final int DRAWN_BYTES = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not {@code sg-} and 16 lower-case hexadecimal digits
     */
    public GenerationId {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a session generation id (sg- and 16 hex digits): " + value);
        }
    }

    /**
     * @param value a string that may be a session generation id
     * @return the id; empty if the string is not one
     */
    public static Optional<GenerationId> parse(String value) {
        return value != null && FORM.matcher(value).matches() ? Optional.of(new GenerationId(value))
                : Optional.empty();
    }

    /** @return a new id with 16 random digits; it is free only once the store checked it */
    public static GenerationId random() {
        return random(RANDOM);
    }

    /**
     * @param random the source of the digits
     * @return a new id with 16 digits drawn from the source
     */
    static GenerationId random(RandomGenerator random) {
        var bytes = new byte[DRAWN_BYTES];
        random.nextBytes(bytes);
        return new GenerationId(PREFIX + HexFormat.of().formatHex(bytes));
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}
