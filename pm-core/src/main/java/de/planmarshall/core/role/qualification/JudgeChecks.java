/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.Optional;

/**
 * The judge check record a grading reads before it offers a rubric item (Model Work § Judge Instrument Check): the
 * project set's {@code qualification/judge-check.json}. A lookup that finds no record answers empty and the grading runs
 * the check first. A lookup does not test the record against the current judge digest or corpus revision; the caller
 * applies {@link JudgeCheck.Verdict#of} and runs the check again when the verdict does not pass.
 *
 * @since 0.1
 */
public interface JudgeChecks {

    /**
     * @return the stored check record; empty if the project set holds none
     */
    Optional<JudgeCheck.Record> lookup();

    /**
     * @param record the check record of the run
     */
    void write(JudgeCheck.Record entry);
}
