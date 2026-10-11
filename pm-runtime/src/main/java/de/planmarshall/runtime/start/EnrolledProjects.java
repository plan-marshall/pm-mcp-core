/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import java.util.List;

/**
 * The ids of the enrolled projects, the population of the per-project recovery ({@link ProjectRecovery}) and of the
 * periodic sweep (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Per-Project Recovery). Only
 * enrolled projects are recovered and swept: the machine store an unenrolled project left behind is not touched.
 * <p>
 * The enrolment store fills this seam; until it exists, {@link #NONE} names no project.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface EnrolledProjects {

    /** The seam while no enrolment store exists: no project is enrolled. */
    EnrolledProjects NONE = List::of;

    /**
     * @return the ids of the enrolled projects, each once
     */
    List<String> projectIds();
}
