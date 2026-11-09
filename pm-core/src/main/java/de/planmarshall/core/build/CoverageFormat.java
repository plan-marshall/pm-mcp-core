/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

/**
 * The format of a coverage report that {@link CoverageReport} describes.
 * <p>
 * Each format names the report file its build tool writes and the identifier a refusal reason carries, so a refused
 * report states which parser refused it.
 *
 * @since 0.1
 */
public enum CoverageFormat {

    /** A JaCoCo XML report, {@code jacoco.xml}. */
    JACOCO("jacoco"),

    /** A Cobertura XML report, {@code coverage.xml} as written by {@code coverage.py}. */
    COBERTURA("cobertura"),

    /** An LCOV text report, {@code lcov.info}. */
    LCOV("lcov"),

    /** An Istanbul JSON summary, {@code coverage-summary.json}. */
    ISTANBUL("istanbul");

    private final String id;

    CoverageFormat(String id) {
        this.id = id;
    }

    /**
     * Returns the identifier of this format.
     *
     * @return the identifier used in a refusal reason and in a rendered result
     */
    public String id() {
        return id;
    }
}
