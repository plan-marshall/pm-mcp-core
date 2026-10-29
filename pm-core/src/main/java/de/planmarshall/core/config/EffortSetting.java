/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

/**
 * What a role assignment store names of the effort (Store Schemas § Role Assignments): the schema makes the member
 * optional and nullable, and the two are different — a member the level does not name keeps the lower level's effort,
 * while a member named {@code null} clears it to the profile's default effort.
 *
 * @since 0.1
 */
public enum EffortSetting {

    /** The level does not name the effort; the value of the lower level stays. */
    INHERIT,
    /** The level names {@code null}: the profile's default effort applies. */
    DEFAULT,
    /** The level names an effort value. */
    VALUE
}
