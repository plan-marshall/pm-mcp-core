/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.assertions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The controls of {@link AssertionMessages}: every shape the scan accepts and every shape it refuses, each beside the
 * code that reads it.
 * <p>
 * The controls live in this class rather than in the guard of a module, so the module guards stay thin and a control
 * still sits beside the scanner it proves.
 */
@EnableGeneratorController
@DisplayName("The assertion-message scan")
class AssertionMessagesTest {

    private static final Path VIOLATIONS = Path.of("target/assertion-message-violations.txt");

    /** The indentation the planted fixture lines carry inside the generated method body. */
    private static final String INDENT = "                ";

    @Test
    @DisplayName("a missing message, an out-of-range message, a boundary and a planted shape are measured")
    void missingAndOutOfRangeMessagesAreReported(@TempDir Path temp) throws Exception {
        assertReported(temp.resolve("missing"), "assertTrue: no message", "assertTrue(refusal.isPresent());");
        assertReported(temp.resolve("nested"), "assertTrue: no message",
                "assertAll(\"the refusal\", () -> assertTrue(refusal.isPresent()));");
        assertReported(temp.resolve("short"), "shorter than " + AssertionMessages.MIN_MESSAGE_LENGTH,
                "assertTrue(refusal.isPresent(), \"too short\");");
        assertReported(temp.resolve("long"), "longer than " + AssertionMessages.MAX_MESSAGE_LENGTH,
                "assertTrue(refusal.isPresent(), \"" + "x".repeat(AssertionMessages.MAX_MESSAGE_LENGTH + 1)
                        + "\");");

        assertEveryKnownName(temp);
        assertTolerances(temp);
        assertPlantedShapes(temp);
        assertBoundaries(temp);
    }

    /** The names the rule knows beside the ones PLAN-V2-101 shipped: each is measured with no message. */
    private static void assertEveryKnownName(Path temp) throws IOException {
        assertReported(temp.resolve("fail-missing"), "fail: no message", "fail();");
        assertReported(temp.resolve("fail-empty"), "fail: no message", "fail( );");
        assertReported(temp.resolve("fail-comment"), "fail: no message", "fail(/* the reason is missing */);");
        assertReported(temp.resolve("assert-not-same"), "assertNotSame: no message",
                "assertNotSame(EXPECTED, value);");
        assertReported(temp.resolve("assert-names"), "assertIterableEquals: no message",
                "assertIterableEquals(EXPECTED, value);");
        assertReported(temp.resolve("assert-lines"), "assertLinesMatch: no message",
                "assertLinesMatch(EXPECTED, value);");
        assertReported(temp.resolve("assert-timeout"), "assertTimeout: no message",
                "assertTimeout(TIMEOUT, () -> store.read(file));");
        assertReported(temp.resolve("assert-preemptively"), "assertTimeoutPreemptively: no message",
                "assertTimeoutPreemptively(TIMEOUT, () -> store.read(file));");
        assertReported(temp.resolve("assert-throws-exactly"), "assertThrowsExactly: no message",
                "assertThrowsExactly(IllegalStateException.class, () -> store.read(file));");
    }

    /** A tolerance is a tolerance whether it is a numeric literal, an identifier beside a floating operand, or a
     * declared floating-point value. */
    private static void assertTolerances(Path temp) throws IOException {
        assertReported(temp.resolve("array-tolerance"), "assertArrayEquals: no message",
                "assertArrayEquals(EXPECTED, value, 0.001);");
        assertReported(temp.resolve("array-tolerance-identifier"), "assertArrayEquals: no message",
                "assertArrayEquals(new double[]{1.0}, value, DELTA);");
        assertReported(temp.resolve("tolerance-identifier"), "assertEquals: no message",
                "assertEquals(1.0, value, DELTA);");
        assertReported(temp.resolve("tolerance-declared"), "assertEquals: no message",
                "double expectedD = 0.0; double epsilon = 0.001; assertEquals(expectedD, actual, epsilon);");
        assertReported(temp.resolve("tolerance-scaled"), "assertEquals: no message",
                "double expectedD = 0.0; assertEquals(expectedD, actual, 2 * EPS);");
        assertReported(temp.resolve("tolerance-declared-third"), "assertEquals: no message",
                "double epsilon = 0.001; assertEquals(readExpected(), readActual(), epsilon);");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1e-6", "1_000.0", "0x1.0p-6"})
    @DisplayName("a tolerance literal is not a message")
    void aToleranceLiteralIsNotAMessage(String tolerance, @TempDir Path temp) throws Exception {
        assertReported(temp, "assertEquals: no message", "assertEquals(1.0, value, " + tolerance + ");");
    }

    /** A comma in a type argument, a lower-case qualified type, a multi-line call and a qualified call are measured. */
    private static void assertPlantedShapes(Path temp) throws IOException {
        assertReported(temp.resolve("type-argument"), "assertEquals: no message",
                "assertEquals(Map.of(), new HashMap<String, String>());");
        assertReported(temp.resolve("generic-call"), "assertTrue: no message",
                "assertTrue(Map.<String, String>of().isEmpty());");
        assertReported(temp.resolve("array-type-argument"), "assertEquals: no message",
                "assertEquals(Map.of(), new HashMap<String[], String>());");
        assertReported(temp.resolve("qualified-type-argument"), "assertEquals: no message",
                "assertEquals(Map.of(), new HashMap<java.lang.String, String>());");
        assertReported(temp.resolve("nested-type-argument"), "assertEquals: no message",
                "assertEquals(Map.of(), new HashMap<String, List<Integer>>());");
        assertReported(temp.resolve("multi-line"), "assertTrue: no message", "assertTrue(\n                value);");
        assertReported(temp.resolve("qualified"), "assertTrue: no message", "Assertions.assertTrue(value);");
    }

    /** The two lengths the rule names are accepted, the two beside them are refused. */
    private static void assertBoundaries(Path temp) throws IOException {
        assertNotReported(temp.resolve("twenty"), "assertTrue(value, \"" + "m".repeat(20) + "\");");
        assertNotReported(temp.resolve("sixty"), "assertTrue(value, \"" + "m".repeat(60) + "\");");
        assertReported(temp.resolve("nineteen"), "shorter than " + AssertionMessages.MIN_MESSAGE_LENGTH,
                "assertTrue(value, \"" + "m".repeat(19) + "\");");
        assertReported(temp.resolve("sixty-one"), "longer than " + AssertionMessages.MAX_MESSAGE_LENGTH,
                "assertTrue(value, \"" + "m".repeat(61) + "\");");
    }

    @Test
    @DisplayName("an assertion with a message in range, a dynamic message and a planted accepted shape are not reported")
    void acceptableAssertionsAreNotReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "assertTrue(refusal.isPresent(), \"the refusal must be present\");",
                "assertEquals(\"the refusal\", refusal.orElseThrow().getMessage(), failure::getMessage);",
                "assertThrows(IllegalStateException.class, () -> store.read(file),"
                        + " () -> \"the store refuses the file\");",
                "fail(\"the root of the test sources was not found\");",
                "fail(() -> \"the store refused the file\");",
                "fail(failure.getMessage());");

        assertTrue(scanTree(root).violations().isEmpty(),
                "a message in range and a dynamic message are accepted");
        assertNotReported(temp.resolve("type-argument-message"),
                "assertEquals(Map.of(), new HashMap<String, String>(), \"both maps are equal here\");");
        assertNotReported(temp.resolve("qualified-type-argument-message"),
                "assertEquals(Map.of(), new HashMap<java.lang.String, String>(), \"both maps are equal here\");");
        assertNotReported(temp.resolve("multi-line-message"),
                "assertTrue(value,\n                \"the message spans two lines\");");
        assertNotReported(temp.resolve("qualified-message"),
                "Assertions.assertTrue(value, \"the value is present here\");");
        assertNotReported(temp.resolve("text-block"),
                "assertTrue(value, \"\"\"\n" + INDENT + "the message is a text block\n" + INDENT + "\"\"\");");
        assertNotReported(temp.resolve("text-block-message"),
                "assertEquals(1.0, value, DELTA, \"\"\"\n" + INDENT + "the value stays inside the delta\n"
                        + INDENT + "\"\"\");");
        assertNotReported(temp.resolve("tolerance-message"),
                "assertEquals(1.0, value, DELTA, \"the value stays within the delta\");");
        assertNotReported(temp.resolve("comparison-message"),
                "assertTrue(map.containsKey(key<a), \"the map holds the lower key\");");
        assertNotReported(temp.resolve("two-comparisons"),
                "assertEquals(a<b, c>d, \"the comparisons should agree\");");
        assertNotReported(temp.resolve("upper-case-comparisons"),
                "assertEquals(a<MAX, b>0, \"the bounds are read as comparisons\");");
        assertNotReported(temp.resolve("qualified-comparisons"),
                "assertEquals(actual.min < expected.max, actual.max > expected.min, "
                        + "\"the two ranges overlap at a bound\");");
        assertNotReported(temp.resolve("upper-case-both-comparisons"),
                "assertEquals(actual < MAX, EXPECTED > 0, \"the comparison results must agree\");");
        assertNotReported(temp.resolve("quoted-number"),
                "String MESSAGE = describe(); assertEquals(List.of(\"1.0\"), actual, MESSAGE);");
        assertNotReported(temp.resolve("spaced-type-argument"),
                "assertEquals(Map.of(), new HashMap <String, String>(), \"both maps are equal here\");");
        assertNotReported(temp.resolve("trailing-comment"),
                "assertTrue(value, \"the value is present here\" /* the reviewer's note */);");
        assertNotReported(temp.resolve("leading-comment"),
                "assertTrue(value, /* the reviewer's note */ \"the value is present here\");");
        var message = AssertionMessages.failureMessage(
                List.of("A.java:3 assertTrue: no message", "B.java:4 fail: no message"), VIOLATIONS);

        assertTrue(message.contains("2 assertion-message violation(s)"), "the failure counts the offenders");
        assertTrue(message.contains("A.java:3"), "the failure names the first offender");
        assertTrue(message.contains("B.java:4"), "the failure names the second offender");
        assertTrue(message.contains(VIOLATIONS.toString()), "the failure names the file of the full list");
        var many = IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "A" + index + ".java:1 assertTrue: no message")
                .toList();

        assertTrue(AssertionMessages.failureMessage(many, VIOLATIONS).contains("the first 50 follow"),
                "a list beyond the cut names how many it prints");
    }

    @Test
    @DisplayName("a cause passed to fail is not a message, and a short message beside a comment is measured")
    void causeAndCommentedMessagesAreMeasured(@TempDir Path temp) throws Exception {
        assertReported(temp.resolve("fail-cause"), "a cause is not a message",
                "Throwable cause = new IllegalStateException(); fail(cause);");
        assertReported(temp.resolve("fail-exception"), "a cause is not a message",
                "IOException thrown = new IOException(); fail(thrown);");
        assertReported(temp.resolve("fail-new"), "a cause is not a message",
                "fail(new AssertionError(\"boom\"));");
        assertReported(temp.resolve("commented-short"), "shorter than " + AssertionMessages.MIN_MESSAGE_LENGTH,
                "assertTrue(value, \"too short\" /* the reviewer's note */);");
        assertReported(temp.resolve("commented-long"),
                "longer than " + AssertionMessages.MAX_MESSAGE_LENGTH,
                "assertTrue(value, \"" + "x".repeat(AssertionMessages.MAX_MESSAGE_LENGTH + 1)
                        + "\" /* the reviewer's note */);");
        assertNotReported(temp.resolve("fail-supplier-name"),
                "Supplier<String> failure = () -> \"the store refused\"; fail(failure);");
        assertNotReported(temp.resolve("fail-computed"),
                "fail(new StringBuilder(\"operation failed: \").append(detail).toString());");
        assertNotReported(temp.resolve("item-name"),
                "String MESSAGE = describe(); assertEquals(ITEM_2D, actual, MESSAGE);");
        assertNotReported(temp.resolve("computed-message"),
                "assertEquals(declared.size(), new HashSet<>(declared).size(), declared.toString());");
    }

    @Test
    @DisplayName("a comparison of two upper-case operands is not read as a type argument")
    void aComparisonOfConstantsIsNotATypeArgument(@TempDir Path temp) throws Exception {
        assertNotReported(temp.resolve("bounded-comparison"),
                "assertTrue(v < MAX && v > MIN, \"the value lies between the two bounds\");");
        assertNotReported(temp.resolve("constant-comparisons"),
                "assertEquals(LOW < MAX, MIN > HIGH, \"the two bound checks must agree\");");
    }

    @Test
    @DisplayName("a primitive-array type argument is read as a type")
    void aPrimitiveArrayTypeArgumentIsAType(@TempDir Path temp) throws Exception {
        assertReported(temp.resolve("primitive-array-type-argument"), "assertEquals: no message",
                "assertEquals(Map.of(), new HashMap<String, int[]>());");
    }

    @Test
    @DisplayName("a qualified or statically imported upper-case third argument is a tolerance")
    void aNamedToleranceIsNotAMessage(@TempDir Path temp) throws Exception {
        assertReported(temp.resolve("qualified-tolerance"), "a tolerance is not a message",
                "assertEquals(expected, actual, Fixtures.EPS);");
        assertReported(temp.resolve("imported-tolerance"), "a tolerance is not a message",
                "assertEquals(expected, actual, EPS);");
    }

    @Test
    @DisplayName("a same-file String constant and a supplier over one literal are measured like the literal")
    void aConstantAndALiteralSupplierAreMeasured(@TempDir Path temp) throws Exception {
        assertNotReported(fieldFixture(temp.resolve("constant-in-range"),
                "private static final String MESSAGE = \"the two values must be equal here\";",
                "assertEquals(expected, actual, MESSAGE);"));
        assertReported(fieldFixture(temp.resolve("constant-short"), "static final String SHORT = \"too short\";",
                "assertTrue(value, SHORT);"), "shorter than " + AssertionMessages.MIN_MESSAGE_LENGTH);
        assertReported(fieldFixture(temp.resolve("constant-long"),
                "final static String LONG = \"" + "x".repeat(AssertionMessages.MAX_MESSAGE_LENGTH + 1) + "\";",
                "fail(LONG);"), "longer than " + AssertionMessages.MAX_MESSAGE_LENGTH);
        assertNotReported(temp.resolve("supplier-in-range"), "assertTrue(value, () -> \"the value is present here\");");
        assertReported(temp.resolve("supplier-short"), "shorter than " + AssertionMessages.MIN_MESSAGE_LENGTH,
                "assertTrue(value, () -> \"too short\");");
    }

    @Test
    @DisplayName("a message the scan does not measure is listed with its shape and is no violation")
    void anUnmeasuredMessageIsListed(@TempDir Path temp) throws Exception {
        var scan = scanTree(packageFixture(temp,
                "assertTrue(value, message);",
                "assertTrue(value, \"the value of \" + name);",
                "assertTrue(value, failure.getMessage());"));

        assertTrue(scan.violations().isEmpty(), "an unmeasured message is not a violation");
        assertEquals(3, scan.unmeasured().size(), "each unmeasured message is listed once");
        assertTrue(scan.unmeasured().get(0).endsWith("assertTrue: identifier"), "a bare name is an identifier");
        assertTrue(scan.unmeasured().get(1).endsWith("assertTrue: concatenation"),
                "a joined message is listed as a concatenation");
        assertTrue(scan.unmeasured().get(2).endsWith("assertTrue: method call"),
                "the exception's message is listed as a method call");
    }

    @Test
    @DisplayName("an assertAll heading is not measured while the assertions inside the group are")
    void anAssertAllHeadingIsNotMeasured(@TempDir Path temp) throws Exception {
        assertNotReported(temp.resolve("short-heading"),
                "assertAll(\"short\", () -> assertTrue(value, \"the value is present here\"));");
        assertNotReported(temp.resolve("long-heading"), "assertAll(\""
                + "h".repeat(AssertionMessages.MAX_MESSAGE_LENGTH + 1)
                + "\", () -> assertTrue(value, \"the value is present here\"));");
        assertReported(temp.resolve("short-inside"), "shorter than " + AssertionMessages.MIN_MESSAGE_LENGTH,
                "assertAll(\"short\", () -> assertTrue(value, \"too short\"));");
    }

    @Test
    @DisplayName("an assertion call inside a comment or a string is not reported")
    void assertionTextOutsideCodeIsNotReported(@TempDir Path temp) throws Exception {
        var root = packageFixture(temp,
                "// assertTrue(refusal.isPresent());",
                "/* assertTrue(refusal.isPresent()); */",
                "assertTrue(text.contains(\"assertTrue(value);\"), \"the text carries the assertion text\");",
                "var block = \"\"\"\n                assertTrue(value);\n                \"\"\";",
                "assertFalse(block.isBlank(), \"the planted text block is not blank\");");

        assertTrue(scanTree(root).violations().isEmpty(), "an assertion call in a comment or a string is not code");
    }

    @Test
    @DisplayName("a package with no test source is a violation, not an empty pass")
    void aPackageWithoutSourceIsAViolation(@TempDir Path temp) {
        var scan = AssertionMessages.scan(temp, "de/planmarshall/absent", Map.of());

        assertEquals(0, scan.files(), "no file was read for the absent package");
        assertTrue(scan.violations().getFirst().contains("no test source was scanned"),
                "the absent package is named as a violation");
    }

    @Test
    @DisplayName("an exempt subtree is not scanned and the package beside it is")
    void anExemptSubtreeIsNotScanned(@TempDir Path temp) throws Exception {
        var base = temp.resolve("de/planmarshall/core");
        Files.createDirectories(base.resolve("kept"));
        Files.createDirectories(base.resolve("exempt"));
        Files.writeString(base.resolve("kept/Kept.java"), "class Kept { void planted() { assertTrue(value); } }");
        Files.writeString(base.resolve("exempt/Exempt.java"),
                "class Exempt { void planted() { assertTrue(value); } }");

        var scan = AssertionMessages.scan(temp, "de/planmarshall/core",
                Map.of("de/planmarshall/core/exempt", "the control fixture of this test"));

        assertEquals(1, scan.files(), "only the kept package was read");
        assertEquals(1, scan.violations().size(), "only the kept package carries a violation");
        assertTrue(scan.violations().getFirst().contains("kept/Kept.java"), "the violation names the kept file");
    }

    private static void assertReported(Path root, String reason, String statement) throws IOException {
        var violations = scanTree(packageFixture(root, statement)).violations();

        assertTrue(violations.stream().anyMatch(violation -> violation.contains(reason)),
                "the scan reports the statement for its reason");
    }

    private static void assertReported(Path fixtureRoot, String reason) {
        var violations = scanTree(fixtureRoot).violations();

        assertTrue(violations.stream().anyMatch(violation -> violation.contains(reason)),
                "the scan reports the fixture tree for its reason");
    }

    private static void assertNotReported(Path root, String statement) throws IOException {
        var violations = scanTree(packageFixture(root, statement)).violations();

        assertEquals(List.of(), violations, "the scan reports no violation for the statement");
    }

    private static void assertNotReported(Path fixtureRoot) {
        var violations = scanTree(fixtureRoot).violations();

        assertEquals(List.of(), violations, "the scan reports no violation for the fixture tree");
    }

    private static AssertionMessages.Scan scanTree(Path root) {
        return AssertionMessages.scan(root, "de/planmarshall/fixture", Map.of());
    }

    private static Path packageFixture(Path root, String... statements) throws IOException {
        var directory = root.resolve("de/planmarshall/fixture");
        Files.createDirectories(directory);
        var className = "C" + Generators.letterStrings(3, 10).next();
        var source = "class " + className + " {\n    void planted() {\n        "
                + String.join("\n        ", statements) + "\n    }\n}\n";
        Files.writeString(directory.resolve(className + ".java"), source);
        return root;
    }

    /** @return the root of a fixture whose class declares one field and plants one statement in a method */
    private static Path fieldFixture(Path root, String field, String statement) throws IOException {
        var directory = root.resolve("de/planmarshall/fixture");
        Files.createDirectories(directory);
        var className = "C" + Generators.letterStrings(3, 10).next();
        var source = "class " + className + " {\n    " + field + "\n    void planted() {\n        " + statement
                + "\n    }\n}\n";
        Files.writeString(directory.resolve(className + ".java"), source);
        return root;
    }
}
