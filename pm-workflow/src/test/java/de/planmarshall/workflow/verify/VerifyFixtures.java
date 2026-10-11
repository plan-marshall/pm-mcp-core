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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import de.planmarshall.workflow.ast.UnitOrigin;
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
