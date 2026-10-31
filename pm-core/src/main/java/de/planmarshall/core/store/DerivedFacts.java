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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The document of the derived facts store {@code derived-facts.json} (Store Schemas § Derived Facts Store; PM-EXT-6):
 * the entries by their key, in the order of the document, at format version {@value #FORMAT_VERSION}.
 * <p>
 * A fact is a cache: deleting the store only forces a re-derivation. The document holds no other state, so a reader
 * that refuses it never falls back to an empty store.
 * <p>
 * The compact constructor holds the one invariant that spans the key and the entry: the key's kind is the entry's
 * kind, so a document cannot file an entry under a key that names another kind.
 *
 * @param facts the entries by their key, in the order of the document
 * @since 0.1
 */
public record DerivedFacts(Map<DerivedFactKey, DerivedFact> facts) {

    /** The format version of a derived facts store document that is read and written (R13: stays 1). */
    public static final int FORMAT_VERSION = 1;

    /**
     * @throws NullPointerException     if the map, a key or a value is {@code null}
     * @throws IllegalArgumentException if a key's kind is not its entry's kind
     */
    public DerivedFacts {
        Objects.requireNonNull(facts, "facts");
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
        for (var entry : facts.entrySet()) {
            var keyKind = entry.getKey().kind();
            var entryKind = entry.getValue().kind();
            if (keyKind != entryKind) {
                throw new IllegalArgumentException(
                        "The key '%s' names the kind '%s', its entry the kind '%s'"
                                .formatted(entry.getKey().wireName(), keyKind.wireName(), entryKind.wireName()));
            }
        }
    }

    /** @return the empty document */
    public static DerivedFacts empty() {
        return new DerivedFacts(Map.of());
    }

    /**
     * @param key the key of a fact
     * @return the entry of the key; empty if the document holds no entry for it
     */
    public Optional<DerivedFact> fact(DerivedFactKey key) {
        return Optional.ofNullable(facts.get(key));
    }
}
