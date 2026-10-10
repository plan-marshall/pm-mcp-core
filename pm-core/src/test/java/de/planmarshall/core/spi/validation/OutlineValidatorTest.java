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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
                Arguments.of("every intent, the three ecosystems and two deliverables", OUTLINE),
                Arguments.of("neither list", "# Solution Outline\n\nNothing to change."),
                Arguments.of("an empty list", FILES + "\n" + DEPENDENCIES),
                Arguments.of("the same name in two ecosystems", DEPENDENCIES + "- `npm:requests`\n- `pypi:requests`"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedOutlines")
    @DisplayName("accepts an outline with")
    void acceptsOutline(String label, String outline) {
        var violations = validator.validate(outline);

        assertTrue(violations.isEmpty(), () -> label + " must be accepted, but: " + violations);
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
                        OutlineValidator.RULE_AFFECTED_FILE_INTENT, 2),
                Arguments.of("a dependency without back quotes", DEPENDENCIES + "- maven:org.example:library",
                        OutlineValidator.RULE_DEPENDENCY_UNPARSED, 2),
                Arguments.of("a dependency without an ecosystem prefix", DEPENDENCIES + "- `library`",
                        OutlineValidator.RULE_DEPENDENCY_UNPARSED, 2),
                Arguments.of("a dependency with text after the coordinate", DEPENDENCIES + "- `npm:left-pad` for padding",
                        OutlineValidator.RULE_DEPENDENCY_UNPARSED, 2),
                Arguments.of("an unknown ecosystem", DEPENDENCIES + "- `cargo:serde`",
                        OutlineValidator.RULE_DEPENDENCY_ECOSYSTEM, 2),
                Arguments.of("an empty ecosystem", DEPENDENCIES + "- `:serde`",
                        OutlineValidator.RULE_DEPENDENCY_ECOSYSTEM, 2),
                Arguments.of("a Maven coordinate with a version", DEPENDENCIES + "- `maven:org.example:library:1.0`",
                        OutlineValidator.RULE_DEPENDENCY_COORDINATE, 2),
                Arguments.of("a Maven coordinate without an artifact", DEPENDENCIES + "- `maven:org.example`",
                        OutlineValidator.RULE_DEPENDENCY_COORDINATE, 2),
                Arguments.of("an npm coordinate with a version", DEPENDENCIES + "- `npm:left-pad@1.3.0`",
                        OutlineValidator.RULE_DEPENDENCY_COORDINATE, 2),
                Arguments.of("an npm coordinate in upper case", DEPENDENCIES + "- `npm:LeftPad`",
                        OutlineValidator.RULE_DEPENDENCY_COORDINATE, 2),
                Arguments.of("a Python coordinate with a version", DEPENDENCIES + "- `pypi:requests==2.32`",
                        OutlineValidator.RULE_DEPENDENCY_COORDINATE, 2),
                Arguments.of("a Python coordinate that ends with a separator", DEPENDENCIES + "- `pypi:requests-`",
                        OutlineValidator.RULE_DEPENDENCY_COORDINATE, 2),
                Arguments.of("a coordinate named twice in one list",
                        DEPENDENCIES + "- `maven:org.example:library`\n- `maven:org.example:library`",
                        OutlineValidator.RULE_DEPENDENCY_DUPLICATE, 3),
                Arguments.of("a coordinate named in two lists",
                        DEPENDENCIES + "- `npm:left-pad`\n\n## 2. Next\n\n" + DEPENDENCIES + "- `npm:left-pad`",
                        OutlineValidator.RULE_DEPENDENCY_DUPLICATE, 7),
                Arguments.of("a Python coordinate named twice in two spellings",
                        DEPENDENCIES + "- `pypi:Typing_Extensions`\n- `pypi:typing-extensions`",
                        OutlineValidator.RULE_DEPENDENCY_DUPLICATE, 3));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rejectedOutlines")
    @DisplayName("rejects an outline with")
    void rejectsOutline(String label, String outline, String ruleKey, int line) {
        var violations = validator.validate(outline);

        assertEquals(List.of(ruleKey + "@" + line), keysAndLines(violations), label);
    }

    @Test
    @DisplayName("returns the violations of both lists in the order of the lines")
    void severalViolations() {
        var outline = FILES + "- `pm-core/A.java`\nfree text\n" + DEPENDENCIES + "- `cargo:serde`\n- `npm:left-pad@1`";

        var violations = validator.validate(outline);

        assertEquals(List.of(OutlineValidator.RULE_AFFECTED_FILE_INTENT + "@2",
                OutlineValidator.RULE_AFFECTED_FILE_UNPARSED + "@3",
                OutlineValidator.RULE_DEPENDENCY_ECOSYSTEM + "@5",
                OutlineValidator.RULE_DEPENDENCY_COORDINATE + "@6"), keysAndLines(violations));
    }

    @Test
    @DisplayName("names the line of the first occurrence in the message of a duplicate")
    void duplicateNamesFirstLine() {
        var outline = DEPENDENCIES + "- `npm:left-pad`\n- `npm:lodash`\n- `npm:left-pad`";

        var violations = validator.validate(outline);

        assertTrue(violations.getFirst().message().endsWith("line 2"), violations.getFirst().message());
    }

    @Test
    @DisplayName("refuses null instead of returning a violation")
    void nullText() {
        assertThrows(NullPointerException.class, () -> validator.validate(null));
    }
}
