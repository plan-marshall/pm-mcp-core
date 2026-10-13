/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.util.Optional;


import de.planmarshall.core.store.ProjectEnrolment.Argv;
import de.planmarshall.core.store.ProjectEnrolment.BuildSystem;

/**
 * The default templates of the canonical build verbs, which the caller of the pipeline supplies (PM-EXEC-2): the
 * argument check uses the default of a build system and verb only when the project's enrolment holds no template for
 * it, and records the source {@code compiled_default}. No implementation and no table of defaults ship in this
 * package; what the defaults are is decided by the caller.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface DefaultTemplates {

    /**
     * @param buildSystem the build system of the job
     * @param verb        the canonical verb of the job
     * @return the default template of the verb, the arguments after the executable; empty if the build system has no
     *         default for it
     */
    Optional<Argv> template(BuildSystem buildSystem, String verb);
}
