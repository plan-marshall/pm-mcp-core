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
 * The integration name: the name of a git or provider integration ({@code git}, {@code github}, {@code gitlab},
 * {@code forgejo}, {@code sonar}) that a plan's overrides key its variant by (Store Schemas § Plan Overrides, the
 * {@code propertyNames} of {@code integrations}).
 * <p>
 * The grammar is the text of {@link ShortName} — two to 32 characters, a lower-case letter first, then lower-case
 * letters, digits or {@code -} — and it is held apart from {@link RoleName} and {@link HarnessId}, so an integration
 * is never validated as a role or a harness, though the three agree today.
 * <p>
 * A name matches in full, and a {@code null} name raises a {@link NullPointerException}, as
 * {@link Pattern#matcher(CharSequence)} does.
 *
 * @since 0.1
 */
@UtilityClass
public class IntegrationName {

    /**
     * The compiled grammar of an integration name, anchored: a lower-case letter, then one to 31 lower-case letters,
     * digits or hyphens.
     */
    public static final Pattern PATTERN = Pattern.compile(ShortName.TEXT);

    /**
     * @param name a candidate integration name
     * @return whether the name is an integration name
     * @throws NullPointerException if the name is {@code null}
     */
    public static boolean matches(String name) {
        return PATTERN.matcher(name).matches();
    }
}
