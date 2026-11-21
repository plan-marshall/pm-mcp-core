/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The index of the harness icon store {@code icons/index.json} (Store Schemas § Harness Icon Store): its
 * {@code format_version} and one entry per stored harness, sorted by harness id.
 *
 * @param formatVersion the one format version of the index
 * @param entries       the stored icons by harness id
 * @since 0.1
 */
public record IconIndex(int formatVersion, Map<String, IconIndexEntry> entries) {

    /**
     * @throws NullPointerException if a component is {@code null}
     */
    public IconIndex {
        Objects.requireNonNull(entries, "entries");
        entries = Map.copyOf(new TreeMap<>(entries));
    }

    /**
     * @param entries the stored icons by harness id
     * @return the index of the store's one format version
     */
    public static IconIndex of(Map<String, IconIndexEntry> entries) {
        return new IconIndex(IconStore.FORMAT_VERSION, entries);
    }

    /** @return an index with no stored icon */
    public static IconIndex empty() {
        return new IconIndex(IconStore.FORMAT_VERSION, Map.of());
    }

    /**
     * @param harnessId the harness id
     * @return the entry of that harness
     */
    public Optional<IconIndexEntry> find(String harnessId) {
        return Optional.ofNullable(entries.get(harnessId));
    }
}
