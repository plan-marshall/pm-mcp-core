/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.UnitType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * Reads the fixtures of the verifier tests from the test classpath: {@code verify/passing/*.pmwf}, units every check
 * passes, {@code verify/refused/*.pmwf}, units with one planted defect each, and {@code verify/fuzz/*.pmwf}, the units
 * of the transition fuzzer. Every fixture is written for the tests, never copied from a unit of the product.
 */
final class VerifyFixtures {

    /** The directory of the units every check passes. */
    static final String PASSING = "verify/passing";

    /** The directory of the units the verifier refuses, one planted defect each. */
    static final String REFUSED = "verify/refused";

    /** The line that opens and closes the frontmatter of a fixture. */
    private static final String FRONTMATTER_DELIMITER = "---";

    private static final Pattern EXPECTATION =
            Pattern.compile("^#\\s*expect:\\s*([A-Z_]+)\\s+state\\s+(\\S+)\\s+line\\s+(\\d+)(?:\\s+column\\s+(\\d+))?\\s*$");

    /** The outcome a terminal state declaration reports: {@code state <id> terminal <outcome> <class>}. */
    private static final Pattern TERMINAL_OUTCOME =
            Pattern.compile("^\\s*state\\s+\\S+\\s+terminal\\s+(\\S+)\\s", Pattern.MULTILINE);

    private VerifyFixtures() {
    }

    /**
     * Reads a fixture.
     *
     * @param resource the classpath resource, for example {@code verify/passing/every-construct.pmwf}
     * @return the text of the fixture
     */
    static String read(String resource) {
        try (var in = VerifyFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            Objects.requireNonNull(in, () -> "missing fixture " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Lists the fixtures of a directory.
     *
     * @param directory a directory of fixtures, for example {@link #REFUSED}
     * @return the classpath resource of every {@code .pmwf} file of the directory, sorted by name
     */
    static List<String> list(String directory) {
        var url = Objects.requireNonNull(VerifyFixtures.class.getClassLoader().getResource(directory), directory);
        try (var files = Files.list(Path.of(url.toURI()))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".pmwf"))
                    .sorted()
                    .map(name -> directory + "/" + name)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Reads the expected violations of a refusing fixture: one comment line
     * {@code # expect: <RULE> state <state id> line <line>}, optionally followed by {@code column <column>}, per
     * violation, in the order the verifier reports them. The expectation lines open the body, directly below the
     * closing {@code ---} of the frontmatter, where a comment is admitted; the first line that is not one ends them.
     * The line of an expectation counts the lines of the frontmatter.
     *
     * @param resource the classpath resource of a refusing fixture
     * @return the expected violations, each located in the fixture; empty when the fixture states none
     */
    static List<Expectation> expectations(String resource) {
        var expectations = new ArrayList<Expectation>();
        var lines = read(resource).lines().toList();
        for (var line : lines.subList(bodyStart(lines), lines.size())) {
            var matcher = EXPECTATION.matcher(line);
            if (!matcher.matches()) {
                break;
            }
            var column = matcher.group(4) == null ? Expectation.UNSTATED : Integer.parseInt(matcher.group(4));
            expectations.add(new Expectation(VerificationRule.valueOf(matcher.group(1)), matcher.group(2), resource,
                    Integer.parseInt(matcher.group(3)), column));
        }
        return List.copyOf(expectations);
    }

    /**
     * @param lines the lines of a fixture
     * @return the index of the first line of the body: the line after the closing {@code ---} of the frontmatter, or
     *         the first line when the fixture opens with no frontmatter
     */
    private static int bodyStart(List<String> lines) {
        if (lines.isEmpty() || !FRONTMATTER_DELIMITER.equals(lines.getFirst())) {
            return 0;
        }
        var closing = lines.subList(1, lines.size()).indexOf(FRONTMATTER_DELIMITER);
        return closing < 0 ? lines.size() : closing + 2;
    }

    /**
     * Asserts that a refusing fixture is refused with exactly the violations its {@code # expect:} lines state: the
     * same rules, states, resource and lines, the columns where an expectation states one, in the same order, and no
     * other violation.
     *
     * @param resource the classpath resource of a refusing fixture
     * @param found    the violations the verifier reported for it
     */
    static void assertRefusedExactly(String resource, List<Violation> found) {
        var expected = expectations(resource);
        assertFalse(expected.isEmpty(), () -> resource + " states no expectation");
        var reported = new ArrayList<Expectation>();
        for (var index = 0; index < found.size(); index++) {
            var violation = Expectation.of(found.get(index));
            var columnStated = index < expected.size() && expected.get(index).column() != Expectation.UNSTATED;
            reported.add(columnStated ? violation : violation.withoutColumn());
        }
        assertEquals(expected, reported, () -> resource + " reported " + found);
    }

    /**
     * One expected violation of a refusing fixture: everything of a {@link Violation} but its message.
     *
     * @param rule         the rule that reports it
     * @param stateId      the state it names
     * @param resourceName the fixture it is located in
     * @param line         the line it is located at
     * @param column       the column it is located at, {@link #UNSTATED} when the fixture does not state it
     */
    record Expectation(VerificationRule rule, String stateId, String resourceName, int line, int column) {

        /** The column of an expectation that states none. */
        static final int UNSTATED = 0;

        static Expectation of(Violation violation) {
            return new Expectation(violation.rule(), violation.stateId(), violation.location().resourceName(),
                    violation.location().line(), violation.location().column());
        }

        Expectation withoutColumn() {
            return new Expectation(rule, stateId, resourceName, line, UNSTATED);
        }
    }

    /**
     * The frontmatter of a unit the verifier tests write inline for a scope they choose: a default unit without entries
     * and exits, of the type the Unit Types table assigns to the scope, in eight lines.
     *
     * @param scope the keyword of the unit's scope
     * @return the frontmatter, both {@code ---} lines and the line break after the closing one included
     */
    static String frontmatter(String scope) {
        var type = switch (scope) {
            case "plan", "epic" -> UnitType.WORKFLOW;
            case "subworkflow" -> UnitType.SUBWORKFLOW;
            default -> UnitType.SYSTEM;
        };
        return """
                ---
                name: Inline unit
                description: A unit written for the verifier tests.
                type: %s
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                """.formatted(type.wireName());
    }

    /**
     * A unit the verifier tests write inline, starting at the state {@code verify.entry}. A unit of the scope
     * {@code subworkflow} is a called unit of the type {@code subworkflow}: it declares the one entry {@code main},
     * bound to {@code verify.entry}, and as its exits the outcomes of its terminal states, {@code stepped_out} aside
     * (interface rules 1 to 3). A unit of any other scope declares no entry and no exit and starts at
     * {@code verify.entry}, of the type {@link #frontmatter(String)} assigns to its scope.
     *
     * @param id     the id of the unit
     * @param scope  the keyword of the unit's scope
     * @param states the states of the unit
     * @return the text of the unit
     */
    static String inlineUnit(String id, String scope, String states) {
        if (!"subworkflow".equals(scope)) {
            return frontmatter(scope) + """
                    workflow %s version 1 format 1
                    scope %s
                    start verify.entry

                    %s""".formatted(id, scope, states);
        }
        var exits = new StringBuilder();
        var terminal = TERMINAL_OUTCOME.matcher(states);
        while (terminal.find()) {
            var exit = "exit: " + terminal.group(1) + "\n";
            if (!"stepped_out".equals(terminal.group(1)) && exits.indexOf(exit) < 0) {
                exits.append(exit);
            }
        }
        return """
                ---
                name: Inline unit
                description: A unit written for the verifier tests.
                type: subworkflow
                icon: workflow.default
                entry: main
                %smodification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow %s version 1 format 1
                scope subworkflow
                entry main -> verify.entry

                %s""".formatted(exits, id, states);
    }

    /**
     * Parses a fixture that must follow the grammar.
     *
     * @param resource the classpath resource of the fixture
     * @return the tree of the fixture
     */
    static WorkflowDefinition parse(String resource) {
        return parseSource(resource, read(resource));
    }

    /**
     * Parses a unit written inline in a test; it must follow the grammar.
     *
     * @param resourceName the name its source locations carry
     * @param source       the text of the unit
     * @return the tree of the unit
     */
    static WorkflowDefinition parseSource(String resourceName, String source) {
        var result = WorkflowParser.parse(resourceName, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> resourceName + " was refused: " + result)
                .definition();
    }
}
