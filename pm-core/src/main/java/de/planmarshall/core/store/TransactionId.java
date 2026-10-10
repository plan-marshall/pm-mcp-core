/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The {@code transaction_id} of a transaction journal (PM-IMPL-7): a UUID of version 7, written in lower case. Its
 * first 48 bits are the milliseconds of the Unix epoch at which it was generated, so ids sort by the time of their
 * transaction.
 * <p>
 * The staged files of a transaction carry the id as their last name segment ({@code <file>.tmp.<id>},
 * {@code <file>.append.<id>}); {@link #ofSuffix(String)} finds it there, so a recovery recognises the staged files of
 * a transaction and nothing else.
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record TransactionId(String value) {

    /** The form of a {@code transaction_id}, as the schema of the journal states it. */
    private static final Pattern FORM =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private static final int LENGTH = 36;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not a lower-case UUID of version 7
     */
    public TransactionId {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a transaction id (lower-case UUID version 7): " + value);
        }
    }

    /**
     * @param clock the clock whose instant the id carries
     * @return a new id, unique by its 74 random bits
     */
    public static TransactionId generate(Clock clock) {
        var millis = Objects.requireNonNull(clock, "clock").millis();
        var mostSignificant = (millis << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
        var leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new TransactionId(new UUID(mostSignificant, leastSignificant).toString());
    }

    /**
     * @param fileName the name of a file
     * @return the id the name ends with, after a dot; empty if it ends with none
     */
    public static Optional<TransactionId> ofSuffix(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        var dot = fileName.length() - LENGTH - 1;
        if (dot < 0 || fileName.charAt(dot) != '.') {
            return Optional.empty();
        }
        var suffix = fileName.substring(dot + 1);
        return FORM.matcher(suffix).matches() ? Optional.of(new TransactionId(suffix)) : Optional.empty();
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}
