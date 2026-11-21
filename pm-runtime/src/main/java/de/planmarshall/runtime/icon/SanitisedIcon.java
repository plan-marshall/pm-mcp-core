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

import java.util.Objects;

/**
 * A candidate icon the accept step kept (Harness Profiles § Harness Icons): its media type and the sanitised bytes that
 * are safe to store and to serve.
 * <p>
 * The content is the sanitised document, never the caller's bytes: an SVG is the allow-listed rendering of what was
 * given, a PNG is the image with its text chunks stripped.
 *
 * @param mediaType the media type the bytes were decided to be
 * @param content   the sanitised bytes to store
 * @since 0.1
 */
public record SanitisedIcon(IconMediaType mediaType, byte[] content) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the content is empty
     */
    public SanitisedIcon {
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(content, "content");
        if (content.length == 0) {
            throw new IllegalArgumentException("A sanitised icon carries its content");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SanitisedIcon icon && mediaType == icon.mediaType
                && java.util.Arrays.equals(content, icon.content);
    }

    @Override
    public int hashCode() {
        return 31 * mediaType.hashCode() + java.util.Arrays.hashCode(content);
    }

    @Override
    public String toString() {
        return "SanitisedIcon[mediaType=%s, content.length=%d]".formatted(mediaType, content.length);
    }
}
