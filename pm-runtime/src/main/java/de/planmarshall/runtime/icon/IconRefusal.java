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

import java.io.Serial;

/**
 * Why a candidate icon was refused before it reached the store (PM-CLIENT-5, Harness Profiles § Harness Icons). A
 * candidate icon is untrusted bytes, so the refusal carries a code and a reason that names the fault; it never repeats
 * the bytes, an element's text or an attribute value.
 *
 * @since 0.1
 */
public final class IconRefusal extends RuntimeException {

    /** The candidate is larger than the compiled bound of {@value IconSanitiser#MAX_BYTES} bytes. */
    public static final String TOO_LARGE = "icon_too_large";

    /** The bytes are neither an SVG document nor a PNG image. */
    public static final String NOT_SVG_OR_PNG = "icon_not_svg_or_png";

    /** The SVG declares a DOCTYPE, so an entity expansion or an external DTD could be reached. */
    public static final String SVG_DOCTYPE = "icon_svg_doctype";

    /** The SVG does not parse as an XML document. */
    public static final String SVG_MALFORMED = "icon_svg_malformed";

    /** The SVG carries a {@code script} element. */
    public static final String SVG_SCRIPT = "icon_svg_script";

    /** The SVG carries a {@code foreignObject} element. */
    public static final String SVG_FOREIGN_OBJECT = "icon_svg_foreign_object";

    /** The SVG carries a {@code use} element. */
    public static final String SVG_USE = "icon_svg_use";

    /** The SVG carries an element outside the allow-list. */
    public static final String SVG_ELEMENT_NOT_ALLOWED = "icon_svg_element_not_allowed";

    /** The SVG carries an event attribute, one whose name begins with {@code on}. */
    public static final String SVG_EVENT_ATTRIBUTE = "icon_svg_event_attribute";

    /** The SVG carries a reference outside the document, a {@code data:} reference, or a style reaching one. */
    public static final String SVG_EXTERNAL_REFERENCE = "icon_svg_external_reference";

    /** The SVG nests elements deeper than the compiled bound. */
    public static final String SVG_TOO_DEEP = "icon_svg_too_deep";

    /** The SVG carries more elements than the compiled bound. */
    public static final String SVG_TOO_COMPLEX = "icon_svg_too_complex";

    /** The PNG does not carry a valid signature, IHDR, chunk CRCs or IEND. */
    public static final String PNG_MALFORMED = "icon_png_malformed";

    /** The harness id that would name the file is not a {@link de.planmarshall.core.id.HarnessId}. */
    public static final String HARNESS_ID_INVALID = "icon_harness_id_invalid";

    /** The source URL the caller names is not an https URL within the bound. */
    public static final String SOURCE_URL_INVALID = "icon_source_url_invalid";

    /** The path the icon would be written to is a symbolic link. */
    public static final String TARGET_IS_LINK = "icon_target_is_link";

    /** The index cannot be read as the icon store's index. */
    public static final String INDEX_UNREADABLE = "icon_index_unreadable";

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient String code;

    /**
     * @param code    the refusal code, a value of this class's constants
     * @param message what the refusal names, never the candidate's bytes
     */
    public IconRefusal(String code, String message) {
        super("%s: %s".formatted(code, message));
        this.code = code;
    }

    /** @return the reason code of the refusal */
    public String code() {
        return code;
    }
}
