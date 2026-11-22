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

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/**
 * One entry of the icon store's index {@code icons/index.json} (Store Schemas § Harness Icon Store).
 *
 * @param sourceUrl the URL the icon was fetched from
 * @param sha256    the lowercase-hex SHA-256 of the stored file
 * @param size      the size of the stored file in bytes
 * @param mediaType the media type of the stored file, {@code image/svg+xml} or {@code image/png}
 * @param fetchedAt when the icon was fetched
 * @since 0.1
 */
@Builder
public record IconIndexEntry(String sourceUrl, String sha256, long size, String mediaType, Instant fetchedAt) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the size is negative or the media type is not one the store keeps
     */
    public IconIndexEntry {
        Objects.requireNonNull(sourceUrl, "sourceUrl");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (size < 1) {
            throw new IllegalArgumentException("A stored icon is at least one byte: " + size);
        }
        IconMediaType.fromMediaType(mediaType);
    }

    /** @return the media type of the entry */
    public IconMediaType media() {
        return IconMediaType.fromMediaType(mediaType);
    }
}
