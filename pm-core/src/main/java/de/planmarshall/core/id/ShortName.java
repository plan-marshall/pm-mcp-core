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

/**
 * The one place a short lower-case name grammar is written: two to 32 characters, the first a lower-case letter, the
 * rest lower-case letters, digits or {@code -}.
 * <p>
 * The registry of the identifiers specifications states the same text under more than one entry, so the text lives
 * here once and every identifier that uses it reads it from this class. The pattern is anchored, so a name matches in
 * full; a caller matches through {@link java.util.regex.Matcher#matches()}, which anchors either way.
 * <p>
 * The test {@code RoleNameSingleDefinitionTest} of the guard package fails the build for a copy of the text in any
 * {@code src/main/java} file of the repository.
 *
 * @since 0.1
 */
final class ShortName {

    /**
     * The grammar of a name of two to 32 characters: a lower-case letter, then lower-case letters, digits or hyphens.
     */
    static final String TEXT = "^[a-z][a-z0-9-]{1,31}$";

    private ShortName() {
    }
}
