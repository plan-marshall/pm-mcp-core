/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.sonar;

import java.util.Objects;

/**
 * The key of an issue on Sonar, the address a finding of Sonar is answered on (PM-IMPL-1). The key is assigned by
 * Sonar and opaque here: it is refused only where it could not be one, when it is blank or holds white space or a
 * control character.
 *
 * @param value the key as Sonar reports it
 * @since 0.1
 */
public record SonarIssueKey(String value) {

    /**
     * @throws NullPointerException     for no value
     * @throws IllegalArgumentException for a value that is blank or holds white space or a control character
     */
    public SonarIssueKey {
        Objects.requireNonNull(value, "value");
        if (value.isEmpty()) {
            throw new IllegalArgumentException("A Sonar issue key is not empty");
        }
        if (value.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw new IllegalArgumentException("A Sonar issue key holds no white space and no control character");
        }
    }
}
