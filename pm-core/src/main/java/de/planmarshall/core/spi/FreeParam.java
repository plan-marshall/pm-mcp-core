/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a component of the parameter record of a primitive as a free parameter (PM-IMPL-5): a value the caller of
 * the link supplies, in contrast to a component the engine resolves from persisted state.
 * <p>
 * The annotation declares the bounds of a text parameter and nothing more. Reading it, binding the supplied value
 * and refusing a value outside the bounds is the work of the engine.
 *
 * @since 0.1
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.RECORD_COMPONENT)
public @interface FreeParam {

    /**
     * @return the least number of characters of a text value; {@code 0} when there is none
     */
    int minLength() default 0;

    /**
     * @return the greatest number of characters of a text value; {@link Integer#MAX_VALUE} when there is none
     */
    int maxLength() default Integer.MAX_VALUE;
}
