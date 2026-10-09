/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import java.util.List;

/**
 * Checks a submitted text against the rules of its kind (PM-WF-6).
 * <p>
 * A validator is stateless and reads nothing but its argument. A text it rejects is an answer, not a failure: the
 * violations are returned, never thrown.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface ContentValidator {

    /**
     * @param text the submitted text
     * @return the violations in the order of the lines they were found on; empty when the text is accepted
     * @throws NullPointerException if the text is {@code null}
     */
    List<ContentViolation> validate(String text);
}
