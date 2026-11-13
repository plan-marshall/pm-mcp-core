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
 * The harness id: the identifier of an enrolled harness, which the role artifacts, the worker state and the metrics
 * rows name beside the role.
 * <p>
 * The grammar is the text of {@link ShortName} — two to 32 characters, a lower-case letter first, then lower-case
 * letters, digits or {@code -} — and the registry states it under its own entry, {@code id-harness-id}, so it may
 * change without the role-name grammar changing. The two names therefore stay apart, though they agree today.
 * <p>
 * A name matches in full, and a {@code null} name raises a {@link NullPointerException}, as
 * {@link Pattern#matcher(CharSequence)} does.
 *
 * @since 0.1
 */
@UtilityClass
public class HarnessId {

    /**
     * The compiled grammar of a harness id, anchored: a lower-case letter, then one to 31 lower-case letters, digits or
     * hyphens.
     */
    public static final Pattern PATTERN = Pattern.compile(ShortName.TEXT);

    /**
     * @param name a candidate harness id
     * @return whether the name is a harness id
     * @throws NullPointerException if the name is {@code null}
     */
    public static boolean matches(String name) {
        return PATTERN.matcher(name).matches();
    }
}
