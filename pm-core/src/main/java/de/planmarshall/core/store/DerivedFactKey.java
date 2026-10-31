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

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The key of one entry of the derived facts store (Store Schemas § Derived Facts Store, {@code facts}
 * {@code propertyNames}; PM-EXT-6): a {@link DerivedFactKind} and an optional qualifier.
 * <p>
 * The wire form is {@code <kind>} or {@code <kind>:<qualifier>}, for example {@code module_discovery} or
 * {@code domain_activation:java}, and it matches the schema pattern {@value #FORM_TEXT}. The kind part must name a
 * {@link DerivedFactKind}; a key whose kind part names no kind is refused. The qualifier is opaque to the store: it
 * distinguishes the entries of one kind (a domain, a profile, a module), and whether a kind admits a qualifier is
 * declared by its {@link DerivedFactKindSpec}.
 *
 * @param kind      the kind of the fact
 * @param qualifier the qualifier that distinguishes entries of the kind; {@code null} when the key is the kind alone
 * @since 0.1
 */
public record DerivedFactKey(DerivedFactKind kind, @Nullable String qualifier) {

    /** The schema pattern of {@code facts.propertyNames} (Store Schemas § Derived Facts Store). */
    public static final String FORM_TEXT = "^[a-z][a-z0-9_]{0,47}(:[A-Za-z0-9_.-]{1,128})?$";

    /** The compiled {@link #FORM_TEXT}. */
    public static final Pattern FORM = Pattern.compile(FORM_TEXT);

    /** The grammar of the qualifier alone. */
    private static final Pattern QUALIFIER = Pattern.compile("[A-Za-z0-9_.-]{1,128}");

    /**
     * @throws NullPointerException     if the kind is {@code null}
     * @throws IllegalArgumentException if the qualifier does not match its grammar, or the wire form does not match
     *                                  {@link #FORM_TEXT}
     */
    public DerivedFactKey {
        Objects.requireNonNull(kind, "kind");
        if (qualifier != null && !QUALIFIER.matcher(qualifier).matches()) {
            throw new IllegalArgumentException(
                    "Not a fact-key qualifier ([A-Za-z0-9_.-]{1,128}): " + qualifier);
        }
        var wireName = qualifier == null ? kind.wireName() : kind.wireName() + ":" + qualifier;
        if (!FORM.matcher(wireName).matches()) {
            throw new IllegalArgumentException("Not a fact key (" + FORM_TEXT + "): " + wireName);
        }
    }

    /** @return the wire form of the key: {@code <kind>} or {@code <kind>:<qualifier>} */
    public String wireName() {
        return qualifier == null ? kind.wireName() : kind.wireName() + ":" + qualifier;
    }

    /**
     * @param wireName a string that may be a fact key
     * @return the key; empty if the string does not match the grammar or its kind part names no kind
     */
    public static Optional<DerivedFactKey> fromWireName(String wireName) {
        Objects.requireNonNull(wireName, "wireName");
        if (!FORM.matcher(wireName).matches()) {
            return Optional.empty();
        }
        var separator = wireName.indexOf(':');
        var kindPart = separator < 0 ? wireName : wireName.substring(0, separator);
        var qualifier = separator < 0 ? null : wireName.substring(separator + 1);
        return DerivedFactKind.fromWireName(kindPart).map(kind -> new DerivedFactKey(kind, qualifier));
    }
}
