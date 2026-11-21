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
import lombok.experimental.UtilityClass;

/**
 * The sanitising accept step of a candidate harness icon (PM-CLIENT-5, Harness Profiles § Harness Icons; R09): the one
 * place a caller's bytes become an icon the store writes and a client is served.
 * <p>
 * The bytes are untrusted, so nothing of them is trusted after this call: the media type is decided from the bytes and
 * never from a file name, an SVG is rendered through the allow-list of {@link SvgSanitiser}, and a PNG is checked and
 * stripped by {@link PngSanitiser}. A candidate that cannot be sanitised is refused with its reason rather than stored
 * in part, and neither the refusal nor a log line repeats any of its bytes.
 *
 * @since 0.1
 */
@UtilityClass
public class IconSanitiser {

    /** The largest candidate the accept step keeps, {@code 256} KiB. */
    public static final int MAX_BYTES = 256 * 1024;

    /**
     * @param candidate the candidate icon, as a caller hands it over
     * @return the sanitised icon to store
     * @throws IconRefusal if the candidate is above the bound, is neither an SVG nor a PNG, or is refused by its
     *                     sanitiser
     */
    public static SanitisedIcon accept(byte[] candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (candidate.length > MAX_BYTES) {
            throw new IconRefusal(IconRefusal.TOO_LARGE,
                    "A candidate icon is at most %d bytes, not %d".formatted(MAX_BYTES, candidate.length));
        }
        if (candidate.length == 0) {
            throw new IconRefusal(IconRefusal.NOT_SVG_OR_PNG, "An empty candidate is no icon");
        }
        if (PngSanitiser.looksLikePng(candidate)) {
            return new SanitisedIcon(IconMediaType.PNG, PngSanitiser.sanitise(candidate));
        }
        if (!SvgSanitiser.looksLikeXml(candidate)) {
            throw new IconRefusal(IconRefusal.NOT_SVG_OR_PNG,
                    "The bytes are neither an SVG document nor a PNG image");
        }
        return new SanitisedIcon(IconMediaType.SVG, SvgSanitiser.sanitise(candidate));
    }
}
