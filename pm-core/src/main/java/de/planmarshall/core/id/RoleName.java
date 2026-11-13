/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.id;

import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * The role name: the identifier a role, a worker and every record that names a role carries.
 * <p>
 * The grammar is two to 32 characters, a lower-case letter first, then lower-case letters, digits or {@code -}
 * (identifiers registry, entry {@code id-role-name}). A role name carries no underscore: the registry gives
 * {@code _} to names that must be tool names.
 * <p>
 * The one definition of the text lives in {@link ShortName}; this class names it for the role and its callers. A name
 * matches in full, and a {@code null} name raises a {@link NullPointerException}, as
 * {@link Pattern#matcher(CharSequence)} does.
 *
 * @since 0.1
 */
@UtilityClass
public class RoleName {

    /**
     * The compiled grammar of a role name, anchored: a lower-case letter, then one to 31 lower-case letters, digits or
     * hyphens.
     */
    public static final Pattern PATTERN = Pattern.compile(ShortName.TEXT);

    /**
     * @param name a candidate role name
     * @return whether the name is a role name
     * @throws NullPointerException if the name is {@code null}
     */
    public static boolean matches(String name) {
        return PATTERN.matcher(name).matches();
    }
}
