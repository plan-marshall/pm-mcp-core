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

import java.util.Optional;

/**
 * What the effective role set says about one role name at one read (PM-WORK-1, Composition Check): the effective
 * configuration of a defined role, or the reason the role is undefined.
 * <p>
 * A role that is absent has no validation reason; a role whose artifact or effective values failed validation carries the
 * reason of the table, so the refusing composition can name it.
 *
 * @since 0.1
 */
public sealed interface RoleResolution {

    /** @return whether the role is defined */
    default boolean defined() {
        return this instanceof Defined;
    }

    /**
     * A defined role.
     *
     * @param role the effective configuration
     * @since 0.1
     */
    record Defined(EffectiveRole role) implements RoleResolution {
    }

    /**
     * A role that is absent or invalid.
     *
     * @param roleName the role name
     * @param reason   the reason of the validation table; empty when the role is absent
     * @param detail   what is wrong, naming the value where one exists
     * @since 0.1
     */
    record Undefined(String roleName, Optional<RoleValidationReason> reason, String detail)
            implements RoleResolution {
    }
}
