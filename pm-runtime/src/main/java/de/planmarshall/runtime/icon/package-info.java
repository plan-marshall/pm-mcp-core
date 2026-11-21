/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The harness icon store {@code <PM_MCP_BASE>/icons/} and its sanitising accept step (PM-CLIENT-5, Harness Profiles §
 * Harness Icons; Store Schemas § Harness Icon Store).
 * <ul>
 * <li>{@link de.planmarshall.runtime.icon.IconSanitiser}: the accept step a caller hands candidate bytes to. The bytes
 * are untrusted: at most {@value de.planmarshall.runtime.icon.IconSanitiser#MAX_BYTES} bytes, SVG or PNG decided by
 * the bytes and never by a file name, an SVG filtered through an allow-list of elements and attributes with scripts,
 * event attributes and every reference outside the document refused, and a PNG checked by its signature and its chunk
 * structure with its text chunks stripped. A refusal names its reason and never echoes the bytes.</li>
 * <li>{@link de.planmarshall.runtime.icon.SvgSanitiser}: the allow-list filter of one SVG document.</li>
 * <li>{@link de.planmarshall.runtime.icon.PngSanitiser}: the signature and chunk-structure check of one PNG.</li>
 * <li>{@link de.planmarshall.runtime.icon.IconStore}: one file per harness {@code icons/<harness_id>.<ext>} with mode
 * {@code 0600} and the index {@code icons/index.json}, written under {@code locks/icons.lock} with an atomic write of
 * each. No connection is opened: the store takes the bytes from a caller, and the outbound fetch is deferred to the
 * profile that declares the {@code source} form.</li>
 * <li>{@link de.planmarshall.runtime.icon.IconIndex} and {@link de.planmarshall.runtime.icon.IconIndexEntry}: the
 * index document, its {@code format_version} and one entry per stored harness.</li>
 * </ul>
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/harness-profiles/01-profile-schema.adoc} (Harness Icons)</li>
 * <li>{@code doc/specification/store-schemas/12-machine-stores.adoc} (Harness Icon Store)</li>
 * </ul>
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.runtime.icon;

import org.jspecify.annotations.NullMarked;
