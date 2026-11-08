/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import lombok.experimental.UtilityClass;

/**
 * The classpath location and the keys of the limits of a project unit file and of a unit frontmatter (PM-IMPL-5).
 * <p>
 * The values lie in {@link #RESOURCE}; {@link de.planmarshall.core.config.LimitProperties} reads them.
 *
 * @since 0.1
 */
@UtilityClass
public class UnitLimits {

    /** The classpath location of the limits of a project unit file and of a unit frontmatter. */
    public static final String RESOURCE = "/de/planmarshall/workflow/dsl/unit-limits.properties";

    /** The key of the most bytes a project unit file may hold. */
    public static final String MAX_UNIT_FILE_BYTES = "pm.workflow.unit.max-file-bytes";

    /** The key of the most lines a frontmatter may hold, its two delimiters included. */
    public static final String MAX_FRONTMATTER_LINES = "pm.workflow.unit.max-frontmatter-lines";

    /** The key of the most characters a refusal echoes from the source. */
    public static final String MAX_ECHO_LENGTH = "pm.workflow.unit.max-echo-length";
}
