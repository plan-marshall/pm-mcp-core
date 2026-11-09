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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;

/**
 * The class-path location of the coverage fixtures and the parsed report of a result.
 * <p>
 * The fixtures are plain files below {@code ported/coverage}, copied from the original's own fixture directory or written
 * for a deliberate change; {@code PROVENANCE.adoc} records where each comes from. A test names its fixture file and reads
 * it from the test class path, so no test resolves a path relative to the working directory.
 *
 * @since 0.1
 */
@UtilityClass
final class CoverageReportFixtures {

    private static final String DIRECTORY = "ported/coverage";

    /**
     * Resolves a fixture file on the test class path.
     *
     * @param name the file name below {@code ported/coverage}
     * @return the fixture's path
     * @throws IllegalStateException if the file is not on the class path as a file
     */
    static Path path(String name) {
        URL url = CoverageReportFixtures.class.getClassLoader().getResource(DIRECTORY + "/" + name);
        if (url == null || !"file".equals(url.getProtocol())) {
            throw new IllegalStateException("fixture " + name + " is not on the test class path as a file");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("fixture " + name + " has no file path", e);
        }
    }

    /**
     * Returns the parsed report of a parse result that must not have been refused.
     *
     * @param result the parse result
     * @return the parsed report
     */
    static CoverageReport reportOf(CoverageParseResult result) {
        CoverageParseResult.Parsed parsed =
                assertInstanceOf(CoverageParseResult.Parsed.class, result, "The report is parsed, not refused");
        return parsed.report();
    }
}
