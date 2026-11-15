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

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One entry of {@code icons/index.json} (Store Schemas § Harness Icon Store): where the icon came from, the digest and size
 * of the file that holds it, its media type, and when it was stored.
 * <p>
 * The source is the URL the profile declared when the icon was fetched; a caller that supplies the bytes itself leaves it
 * empty, which is the state the store is in while the outbound fetch stays deferred. The digest and the size are what the
 * store verifies a file against before it serves it, so a file that differs from its entry is not served.
 *
 * @param source    the URL the icon was fetched from; {@code null} where a caller supplied the bytes
 * @param sha256    the digest of the stored file, as {@code sha256:} and 64 lower-case hexadecimal digits
 * @param size      the length of the stored file in bytes
 * @param mediaType the media type of the stored file, one of the closed set of {@link IconFormat}
 * @param fetchedAt when the icon was stored
 * @since 0.1
 */
@Builder
public record IconIndexEntry(@Nullable String source, String sha256, long size, String mediaType, Instant fetchedAt) {

    /**
     * @throws NullPointerException     if a component that is never {@code null} is {@code null}
     * @throws IllegalArgumentException if the digest is not a {@code sha256:} digest, the size is not positive, the media
     *                                  type is outside the closed set, or the source is not an absolute {@code https}
     *                                  URL
     */
    public IconIndexEntry {
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(fetchedAt, "fetchedAt");
        if (!sha256.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a sha256: digest: " + sha256);
        }
        if (size <= 0) {
            throw new IllegalArgumentException("An icon entry records a positive size: " + size);
        }
        if (IconFormat.ofMediaType(mediaType).isEmpty()) {
            throw new IllegalArgumentException("Not a media type of an icon: " + mediaType);
        }
        if (source != null) {
            requireHttpsSource(source);
        }
    }

    private static void requireHttpsSource(String source) {
        try {
            var uri = new URI(source);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("An icon source is an absolute https URL: " + source);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("An icon source is not a URL: " + source, e);
        }
    }
}
