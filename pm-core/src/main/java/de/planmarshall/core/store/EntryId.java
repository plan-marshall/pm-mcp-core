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
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The {@code entry_id} of a pending entry (PM-CLIENT-1): {@code ent-} and 8 to 32 lower-case hexadecimal digits, the
 * id a client's entry is named by and the entity id of its bootstrap.
 * <p>
 * The id is the name of its directory {@code pending/<entry_id>/}, which holds the entry's record {@code entry.json},
 * its source directory, its question and decision stores and its chat transcript (Store Schemas § Pending Entry). A
 * name that does not parse as an id is never an entry id ({@link #ofDirectoryName(String)}): a listing of
 * {@code pending/} skips it, and no sweep selects it. A new id is drawn at random with 16 digits; whether it is free
 * is decided by the store under the workspace lock ({@link PendingEntryStore#allocate(ScopeTransaction)}).
 *
 * @param value the id in its one written form
 * @since 0.1
 */
public record EntryId(String value) {

    private static final Pattern FORM = Pattern.compile("ent-[0-9a-f]{8,32}");
    private static final String PREFIX = "ent-";
    private static final int DRAWN_BYTES = 8;
    private static final RandomGenerator RANDOM = new SecureRandom();

    /**
     * @throws NullPointerException     if the value is {@code null}
     * @throws IllegalArgumentException if the value is not {@code ent-} and 8 to 32 lower-case hexadecimal digits
     */
    public EntryId {
        Objects.requireNonNull(value, "value");
        if (!FORM.matcher(value).matches()) {
            throw new IllegalArgumentException("Not an entry id (ent- and 8 to 32 hex digits): " + value);
        }
    }

    /**
     * @param value a string that may be an entry id
     * @return the id; empty if the string is not one
     */
    public static Optional<EntryId> parse(@Nullable String value) {
        return value != null && FORM.matcher(value).matches() ? Optional.of(new EntryId(value)) : Optional.empty();
    }

    /**
     * @param directoryName the name of a directory in {@code pending/}
     * @return the id whose directory the name is; empty if the name is not an entry id
     */
    public static Optional<EntryId> ofDirectoryName(String directoryName) {
        Objects.requireNonNull(directoryName, "directoryName");
        return parse(directoryName);
    }

    /** @return a new id with 16 random digits; it is free only once the store checked it */
    public static EntryId random() {
        return random(RANDOM);
    }

    /**
     * @param random the source of the digits
     * @return a new id with 16 digits drawn from the source
     */
    static EntryId random(RandomGenerator random) {
        var bytes = new byte[DRAWN_BYTES];
        random.nextBytes(bytes);
        return new EntryId(PREFIX + HexFormat.of().formatHex(bytes));
    }

    /** @return the name of the directory of this entry, the id itself */
    public String directoryName() {
        return value;
    }

    /** @return the id in its one written form */
    @Override
    public String toString() {
        return value;
    }
}
