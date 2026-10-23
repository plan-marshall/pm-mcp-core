/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

/**
 * The effort a level of the effective configuration names (PM-WORK-1, Role Assignment): a value the harness takes, or
 * {@code null} because the level names the absence of an effort flag. The two are different: a level that does not name
 * the member at all is {@code null} <em>outside</em> this type (an {@code Optional} that is empty), while a level that
 * names {@code null} keeps the earlier levels' effort cleared.
 *
 * @param value the effort value, or {@code null} for no effort flag
 * @since 0.1
 */
public record EffortValue(String value) {
}
