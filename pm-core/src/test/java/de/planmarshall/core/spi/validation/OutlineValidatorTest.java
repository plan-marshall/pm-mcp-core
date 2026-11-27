/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

// Every datum in this class is a contract: the outline fixtures and the rule keys and lines asserted from them.
@DisplayName("Validator of a submitted solution outline")
class OutlineValidatorTest {

    private static final String FILES = "**Affected files:**\n";

    private static final String DEPENDENCIES = "**Added dependencies:**\n";

    private static final String OUTLINE = """
            # Solution Outline

            A sentence outside of a list is not read: - pm-core/A.java

            ## 1. Registry

            **Affected files:**
            - `pm-core/src/main/java/A.java` (write-new)
            - `pm-core/src/main/java/B.java` (write-replace)
              - `pm-core/src/main/java/C.java` (write-conditional)
            - `pm-core/src/main/java/D.java` (delete)

            - `doc/plans/README.adoc` (read)

            **Added dependencies:**
            - `maven:org.example:library`
            - `npm:@scope/package`
            - `npm:left-pad`
            - `pypi:Typing_Extensions`

            **Verification:**
            - the build is green

            ## 2. Tests

            **Affected files:**
            - `pm-core/src/test/java/ATest.java` (write-new)
            ### Notes
            Free text below a heading is not read either.
            """;

    private final OutlineValidator validator = new OutlineValidator();

    private static List<String> keysAndLines(List<ContentViolation> violations) {
        return violations.stream().map(violation -> violation.ruleKey() + "@" + violation.line()).toList();
    }

    static Stream<Arguments> acceptedOutlines() {
        return Stream.of(
                Arguments.of("every intent and two deliverables", OUTLINE),
                Arguments.of("no affected files list", "# Solution Outline\n\nNothing to change."),
                Arguments.of("an empty list", FILES + "\n" + DEPENDENCIES));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedOutlines")
    @DisplayName("accepts an outline with")
    void acceptsOutline(String label, String outline) {
        var violations = validator.validate(outline);

        assertEquals(List.of(), violations, "the outline is accepted without a violation");
    }

    static Stream<Arguments> rejectedOutlines() {
        return Stream.of(
                Arguments.of("no content", "", OutlineValidator.RULE_BLANK, 0),
                Arguments.of("white space only", " \n\t\n", OutlineValidator.RULE_BLANK, 0),
                Arguments.of("an affected file without back quotes", FILES + "- pm-core/A.java (write-new)",
                        OutlineValidator.RULE_AFFECTED_FILE_UNPARSED, 2),
                Arguments.of("a sentence in the affected files list", FILES + "- `pm-core/A.java` (read)\nand one more",
                        OutlineValidator.RULE_AFFECTED_FILE_UNPARSED, 3),
                Arguments.of("an affected file without an intent", FILES + "- `pm-core/A.java`",
                        OutlineValidator.RULE_AFFECTED_FILE_INTENT, 2),
                Arguments.of("an affected file with an unknown intent", FILES + "- `pm-core/A.java` (modify)",
                        OutlineValidator.RULE_AFFECTED_FILE_INTENT, 2),
                Arguments.of("an affected file with text after the intent", FILES + "- `pm-core/A.java` (read) twice",
                        OutlineValidator.RULE_AFFECTED_FILE_INTENT, 2));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedOutlines")
    @DisplayName("rejects an outline with")
    void rejectsOutline(String label, String outline, String ruleKey, int line) {
        var violations = validator.validate(outline);

        assertEquals(List.of(ruleKey + "@" + line), keysAndLines(violations),
                "the outline breaks exactly its rule on its line");
    }

    @Test
    @DisplayName("returns the violations of the affected files lists in the order of the lines")
    void severalViolations() {
        var outline = FILES + "- `pm-core/A.java`\nfree text\n\n## 2. Next\n\n" + FILES + "- `pm-core/B.java` (modify)";

        var violations = validator.validate(outline);

        assertEquals(List.of(OutlineValidator.RULE_AFFECTED_FILE_INTENT + "@2",
                        OutlineValidator.RULE_AFFECTED_FILE_UNPARSED + "@3",
                        OutlineValidator.RULE_AFFECTED_FILE_INTENT + "@8"), keysAndLines(violations),
                "the file rules of both lists keep line order");
    }

    @Test
    @DisplayName("no longer reads an added dependencies list, and still checks an affected files list after it")
    void addedDependenciesListIsNotRead() {
        // Each line broke one of the dependency rules PLAN-V2-189 removed: no bullet, an unknown ecosystem, a
        // versioned coordinate, and a duplicate.
        var outline = DEPENDENCIES + "maven:org.example:library\n- `cargo:serde`\n- `npm:left-pad@1.3.0`\n"
                + "- `npm:left-pad`\n- `npm:left-pad`\n" + FILES + "- `pm-core/A.java` (modify)";

        var violations = validator.validate(outline);

        assertEquals(List.of(OutlineValidator.RULE_AFFECTED_FILE_INTENT + "@8"), keysAndLines(violations),
                "only the affected file after the list is reported");
    }

    @Test
    @DisplayName("refuses null instead of returning a violation")
    void nullText() {
        assertThrows(NullPointerException.class, () -> validator.validate(null),
                "a null outline text is refused, not reported");
    }
}
