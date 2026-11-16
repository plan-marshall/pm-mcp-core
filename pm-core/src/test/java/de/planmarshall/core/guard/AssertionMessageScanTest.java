/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.testing.assertions.AssertionMessages;

/**
 * Guards the rule of the {@code junit-core} skill that every assertion carries a message of 20 to 60 characters, for
 * the test tree of this module.
 * <p>
 * The scan walks every test source below {@code de/planmarshall/core} and skips the two packages this list exempts, by
 * name and with a reason: a new package is guarded as soon as it is written, because the list is the exemption and not
 * the population. The scan itself is the shared scanner of the {@code tests} JAR
 * ({@link AssertionMessages}), which a module's own thin guard reuses for its own tree.
 */
@DisplayName("Every assertion carries a message")
class AssertionMessageScanTest {

    /** The base package this module's tests are written below. */
    private static final String BASE_PACKAGE = "de/planmarshall/core";

    /** Where the offenders of a failing scan are written, so the failure names a readable list. */
    private static final Path VIOLATIONS = Path.of("target/assertion-message-violations.txt");

    /**
     * The packages the walk leaves out. Both are outside the scope of the assertion-message family and were clean when
     * the walk replaced the list (PLAN-V2-102 landing, L3-8); a plan that takes one of them up removes its entry.
     */
    private static final Map<String, String> EXEMPTIONS = Map.of(
            "de/planmarshall/core/id", "outside the family's scope and clean when the walk replaced the list",
            "de/planmarshall/core/testing", "outside the family's scope and clean when the walk replaced the list");

    @Test
    @DisplayName("every assertion below the base package carries a 20 to 60 character message")
    void everyAssertionOfTheModuleCarriesAMessage() {
        var root = AssertionMessages.testSourceRoot(BASE_PACKAGE);
        var scan = AssertionMessages.scan(root, BASE_PACKAGE, EXEMPTIONS);
        var violations = scan.violations();
        if (violations.isEmpty()) {
            return;
        }
        AssertionMessages.writeViolations(VIOLATIONS, violations);

        assertTrue(violations.isEmpty(), () -> AssertionMessages.failureMessage(violations, VIOLATIONS));
    }
}
