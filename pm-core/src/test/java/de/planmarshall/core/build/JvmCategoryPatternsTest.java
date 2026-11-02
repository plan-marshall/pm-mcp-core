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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The ported tests of {@link JvmCategoryPatterns} (PM-TEST-2): the category cases of the Maven suite, carried by the
 * fixture {@code ported/build/jvm-patterns/maven-categories}.
 * <p>
 * The table is one compiled artifact, so its cases live in a fixture directory of their own rather than in the Maven
 * parser's; the roster there maps them. A case of the original's source-level lint (that no Python pattern string carries
 * an unescaped bracket) has no counterpart in a compiled table; the single-letter control of the fixture guards the
 * property it stood for.
 *
 * @since 0.1
 */
@DisplayName("JvmCategoryPatterns")
class JvmCategoryPatternsTest {

    private static final String DIRECTORY = "ported/build/jvm-patterns";

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("categorises each message as the original's category table does")
    void categorisesFixture(BuildLogFixtures.Case fixture) {
        Set<Integer> consumed = new HashSet<>();

        for (int index = 0; index < fixture.expectations().size(); index++) {
            BuildLogFixtures.Expectation expectation = fixture.expectations().get(index);
            List<String> parts = expectation.split(2);
            assertEquals(2, parts.size(), "a categorize expectation names a category and a message");
            Category expected = Category.valueOf(parts.getFirst().toUpperCase(Locale.ROOT));

            Category actual = JvmCategoryPatterns.maven().categorize(parts.get(1));

            assertEquals(expected, actual, "the table categorises the fixture's message");
            consumed.add(index);
        }
        BuildLogFixtures.assertConsumed(fixture, consumed);
    }

    @Test
    @DisplayName("keeps the tool-specific OpenRewrite pattern out of the other tool's table")
    void toolSpecificPatterns() {
        Category mavenOnGradlePlugin = JvmCategoryPatterns.maven().categorize("rewrite-gradle-plugin");
        Category gradleOnGradlePlugin = JvmCategoryPatterns.gradle().categorize("rewrite-gradle-plugin");
        Category gradleOnMavenPlugin = JvmCategoryPatterns.gradle().categorize("rewrite-maven-plugin");

        assertEquals(Category.OTHER, mavenOnGradlePlugin, "the Maven table does not know Gradle's plugin");
        assertEquals(Category.OPENREWRITE_INFO, gradleOnGradlePlugin, "the Gradle table knows its own plugin");
        assertEquals(Category.OTHER, gradleOnMavenPlugin, "the Gradle table does not know Maven's plugin");
    }

    @Test
    @DisplayName("classifies an unmatched message as other, never as a real category")
    void unmatchedIsOther() {
        assertEquals(Category.OTHER, JvmCategoryPatterns.gradle().categorize("an ordinary log line"),
                "an ordinary line carries no category");
        assertSame(Category.COMPILATION_ERROR, JvmCategoryPatterns.maven().categorize("cannot find symbol"),
                "a known compiler message keeps its category");
    }

    @Test
    @DisplayName("closes the corpus of the category cases in both directions")
    void roster() {
        BuildLogFixtures.assertRoster(DIRECTORY, fixtures(), 4);
    }
}
