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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The extraction of a build line's location, including the numbers too large for an {@code int}: a hostile log must not
 * make the parse throw, and a dropped line drops its column with it.
 */
@DisplayName("JvmFileLocation")
class JvmFileLocationTest {

    @Test
    @DisplayName("reads the four location forms of the ported originals")
    void readsLocationForms() {
        assertAll(
                () -> assertEquals(new JvmFileLocation.Location("/p/Foo.java", 12, 5),
                        JvmFileLocation.parse("/p/Foo.java:[12,5] cannot find symbol"),
                        "The Maven style carries file, line and column"),
                () -> assertEquals(new JvmFileLocation.Location("/p/Foo.kt", 7, 3),
                        JvmFileLocation.parse("/p/Foo.kt:7:3 error: unresolved reference"),
                        "The Gradle style carries an optional column"),
                () -> assertEquals(new JvmFileLocation.Location("/p/Foo.groovy", 7, null),
                        JvmFileLocation.parse("/p/Foo.groovy:7: did not compile"),
                        "A line-only reference has no column"),
                () -> assertEquals(new JvmFileLocation.Location("FooTest.java", 42, null),
                        JvmFileLocation.parse("FooTest.rejects:42 expected: <a> but was: <b>"),
                        "A test reference names the test's source file"));
    }

    @Test
    @DisplayName("drops a location whose number does not fit an int")
    void dropsOverflowingNumbers() {
        assertAll(
                () -> assertEquals(new JvmFileLocation.Location("/p/Foo.java", null, null),
                        JvmFileLocation.parse("/p/Foo.java:99999999999999999999: broken"),
                        "An overflowing line leaves no line and no column"),
                () -> assertEquals(JvmFileLocation.Location.NONE,
                        JvmFileLocation.parse("a line with no location at all"),
                        "A line without a location carries no component"));
    }
}
