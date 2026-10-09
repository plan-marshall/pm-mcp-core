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
 * The JSON and JSONL state stores: the {@code format_version} envelopes, the digests, the store layout of
 * workspace, project and machine, and the global lock order.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas.adoc}</li>
 * <li>{@code doc/specification/filesystem-layout.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.store;
