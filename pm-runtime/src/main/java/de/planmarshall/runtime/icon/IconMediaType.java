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

/**
 * The two media types the icon store accepts (Harness Profiles § Harness Icons; Store Schemas § Harness Icon Store):
 * an icon is an SVG or a PNG, and the type is decided by the bytes, never by a file name.
 *
 * @since 0.1
 */
public enum IconMediaType {

    /** A sanitised SVG document. */
    SVG("svg", "image/svg+xml"),

    /** A PNG image with its text chunks stripped. */
    PNG("png", "image/png");

    private final String extension;
    private final String mediaType;

    IconMediaType(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    /** @return the file-name extension of the stored file, {@code svg} or {@code png} */
    public String extension() {
        return extension;
    }

    /** @return the media type of the stored file */
    public String mediaType() {
        return mediaType;
    }

    /**
     * @param name the media type of an index entry
     * @return the media type of that name
     * @throws IllegalArgumentException if the name is not one of the two accepted types
     */
    public static IconMediaType fromMediaType(String name) {
        for (var value : values()) {
            if (value.mediaType.equals(name)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Not an icon media type: " + name);
    }
}
