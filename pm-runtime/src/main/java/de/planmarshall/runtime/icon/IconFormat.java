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

import java.util.Arrays;
import java.util.Optional;

/**
 * The two forms a stored harness icon has (Store Schemas § Harness Icon Store; Harness Profiles § Harness Icons): the
 * extension of its file below {@code icons/} and the media type the index records.
 * <p>
 * The set is closed: a candidate that is neither an SVG nor a PNG is refused, and an index entry whose media type is
 * outside this list is not read. The format is decided by the candidate's bytes, never by a name a caller supplies.
 *
 * @since 0.1
 */
public enum IconFormat {

    /** A vector icon, stored as {@code <harness_id>.svg}. */
    SVG("svg", "image/svg+xml"),

    /** A raster icon, stored as {@code <harness_id>.png}. */
    PNG("png", "image/png");

    private final String extension;
    private final String mediaType;

    IconFormat(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    /** @return the extension of the stored file, without the dot */
    public String extension() {
        return extension;
    }

    /** @return the media type the index records for the format */
    public String mediaType() {
        return mediaType;
    }

    /**
     * @param extension an extension without the dot
     * @return the format of that extension, or empty for one outside the closed list
     */
    public static Optional<IconFormat> ofExtension(String extension) {
        return Arrays.stream(values()).filter(format -> format.extension.equals(extension)).findFirst();
    }

    /**
     * @param mediaType a media type
     * @return the format of that media type, or empty for one outside the closed list
     */
    public static Optional<IconFormat> ofMediaType(String mediaType) {
        return Arrays.stream(values()).filter(format -> format.mediaType.equals(mediaType)).findFirst();
    }
}
