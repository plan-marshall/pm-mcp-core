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
 * The machine's harness icons: the store at {@code <PM_MCP_BASE>/icons/} that keeps one sanitised file per harness
 * ({@link de.planmarshall.runtime.icon.HarnessIconStore}), the closed index document beside them
 * ({@link de.planmarshall.runtime.icon.IconIndexCodec}), and the accept step that turns a candidate's bytes into a
 * storable icon ({@link de.planmarshall.runtime.icon.HarnessIconSanitiser}).
 * <p>
 * The store takes bytes from a caller and opens no connection: the outbound fetch of an icon declared by the profile's
 * {@code source} form is deferred (Later Improvements L-21). What it stores is decided by the bytes alone — an SVG
 * re-written against an allow-list of elements and attributes, or a PNG whose chunk structure is closed — never by a file
 * name, and the harness id that names the file is checked before it reaches a path.
 */
@NullMarked
package de.planmarshall.runtime.icon;

import org.jspecify.annotations.NullMarked;
