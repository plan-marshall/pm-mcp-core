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
 * The refusal of one harness icon candidate or of one icon path (Store Schemas § Harness Icon Store; Harness Profiles
 * § Harness Icons): the closed set of reasons the accept step and the store give, each with a stable code a client shows
 * and a test asserts.
 * <p>
 * The {@link #code()} is the machine-readable value; the {@link #reason()} names the offending element, attribute or
 * length, so an operator can act on it. Neither ever carries the candidate's bytes: a refusal reports what was wrong with
 * the input, never the input.
 *
 * @since 0.1
 */
public final class IconRefusal extends RuntimeException {

    /** The candidate is larger than the compiled bound of 256 KiB. */
    public static final String TOO_LARGE = "icon_too_large";

    /** The candidate's bytes are neither an SVG document nor a PNG image. */
    public static final String FORMAT_UNSUPPORTED = "icon_format_unsupported";

    /** The SVG candidate does not parse as well-formed XML. */
    public static final String SVG_MARKUP_INVALID = "icon_svg_markup_invalid";

    /** The SVG candidate declares a DOCTYPE, which no icon needs. */
    public static final String SVG_DOCTYPE_REFUSED = "icon_svg_doctype_refused";

    /** The SVG candidate holds an element outside the allow-list. */
    public static final String SVG_ELEMENT_REFUSED = "icon_svg_element_refused";

    /** The SVG candidate holds an attribute outside the allow-list, or a reference that leaves the document. */
    public static final String SVG_ATTRIBUTE_REFUSED = "icon_svg_attribute_refused";

    /** The PNG candidate's signature or chunk structure is not a PNG image. */
    public static final String PNG_INVALID = "icon_png_invalid";

    /** The PNG candidate holds a chunk outside the allow-list. */
    public static final String PNG_CHUNK_REFUSED = "icon_png_chunk_refused";

    /** The PNG candidate holds a text chunk, which can carry arbitrary text. */
    public static final String PNG_TEXT_CHUNK_REFUSED = "icon_png_text_chunk_refused";

    /** The harness id that would name the file is not a harness id. */
    public static final String HARNESS_ID_INVALID = "icon_harness_id_invalid";

    /** The path an icon would be read from or written to is not a plain file of the icon directory. */
    public static final String PATH_REFUSED = "icon_path_refused";

    private final String code;
    private final String reason;

    /**
     * @param code   the refusal code, a value of this class's constants
     * @param reason what the refusal names
     */
    public IconRefusal(String code, String reason) {
        super(code + ": " + reason);
        this.code = code;
        this.reason = reason;
    }

    /** @return the code of the refusal, a value of this class's constants */
    public String code() {
        return code;
    }

    /** @return the element, attribute or length the refusal names */
    public String reason() {
        return reason;
    }
}
