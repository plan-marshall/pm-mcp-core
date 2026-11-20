/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.testing;

import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.testing.assertions.AssertionMessages;

/**
 * Guards the rule of the {@code junit-core} skill that every assertion carries a message of 20 to 60 characters, for
 * the test tree of this module.
 * <p>
 * The scan is the shared scanner of the {@code pm-core} {@code tests} JAR (ADR 012, PLAN-V2-103); this module names its
 * own base package and reads its own {@code src/test/java}, so the artifact carries no copy of this tree.
 */
@DisplayName("Every assertion carries a message")
class AssertionMessageScanTest {

    /** The base package this module's tests are written below. */
    private static final String BASE_PACKAGE = "de/planmarshall/provider/git";

    /** Where the offenders of a failing scan are written, so the failure names a readable list. */
    private static final Path VIOLATIONS = Path.of("target/assertion-message-violations.txt");

    @Test
    @DisplayName("every assertion below the base package carries a 20 to 60 character message")
    void everyAssertionOfTheModuleCarriesAMessage() {
        var root = AssertionMessages.testSourceRoot(BASE_PACKAGE);
        var scan = AssertionMessages.scan(root, BASE_PACKAGE, Map.of());
        var violations = scan.violations();
        if (violations.isEmpty()) {
            return;
        }
        AssertionMessages.writeViolations(VIOLATIONS, violations);

        fail(() -> AssertionMessages.failureMessage(violations, VIOLATIONS));
    }
}
