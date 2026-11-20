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
 * The {@code command_id} of a call frame: {@code cmd-} and 16 random lower-case hexadecimal digits,
 * {@code ^cmd-[0-9a-f]{16}$}.
 * <p>
 * The id names one pushed call frame of a workflow engine advance: one per call, drawn by the engine from the
 * {@code ids} component of its ports when it pushes the frame. It is a reference, not a secret or a capability:
 * unique within the scope that owns the status, never reused.
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record CommandId(String value) {

    /** The grammar of the id: the prefix {@code cmd-} and 16 lower-case hexadecimal digits. */
    public static final Pattern PATTERN = Pattern.compile("^cmd-[0-9a-f]{16}$");

    private static final int DRAWN_BYTES = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not {@code cmd-} and 16 lower-case hexadecimal digits
     */
    public CommandId {
        Objects.requireNonNull(value, "value");
        if (!PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a command id (^cmd-[0-9a-f]{16}$): " + value);
        }
    }

    /**
     * @param value a string that may be a command id
     * @return the id; empty if the string is not one
     */
    public static Optional<CommandId> parse(@Nullable String value) {
        return value != null && PATTERN.matcher(value).matches() ? Optional.of(new CommandId(value))
                : Optional.empty();
    }

    /**
     * @return a new id with 16 random digits
     */
    public static CommandId random() {
        return random(RANDOM);
    }

    /**
     * @param random the source of the digits
     * @return a new id with 16 digits drawn from the source
     */
    public static CommandId random(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        var bytes = new byte[DRAWN_BYTES];
        random.nextBytes(bytes);
        return new CommandId("cmd-" + HexFormat.of().formatHex(bytes));
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}