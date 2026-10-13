/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

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

/**
 * Reads the DSL fixtures of the tests from the test classpath: {@code dsl/valid/*.pmwf}, units that follow the grammar,
 * and {@code dsl/malformed/*.pmwf}, units the parser refuses. Every fixture is written for the tests, never copied
 * from a unit of the product. A valid fixture is parsed as a default unit unless a test parses it as a project unit.
 */
final class DslFixtures {

    /** The directory of the units that follow the grammar. */
    static final String VALID = "dsl/valid";

    /** The directory of the units the parser refuses. */
    static final String MALFORMED = "dsl/malformed";

    private DslFixtures() {
    }

    /**
     * Reads a fixture.
     *
     * @param resource the classpath resource, for example {@code dsl/valid/plan-unit.pmwf}
     * @return the text of the fixture
     */
    static String read(String resource) {
        try (var in = DslFixtures.class.getClassLoader().getResourceAsStream(resource)) {
            Objects.requireNonNull(in, () -> "missing fixture " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Lists the fixtures of a directory.
     *
     * @param directory {@link #VALID} or {@link #MALFORMED}
     * @return the classpath resource of every {@code .pmwf} file of the directory, sorted by name
     */
    static List<String> list(String directory) {
        var url = Objects.requireNonNull(DslFixtures.class.getClassLoader().getResource(directory), directory);
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
     * Parses a fixture that must follow the grammar as a default unit.
     *
     * @param resource the classpath resource of the fixture
     * @return the tree of the fixture
     */
    static WorkflowDefinition parseValid(String resource) {
        return parseValid(resource, UnitOrigin.DEFAULT);
    }

    /**
     * Parses a fixture that must follow the grammar with the given origin.
     *
     * @param resource the classpath resource of the fixture
     * @param origin   the origin the fixture is parsed with
     * @return the tree of the fixture
     */
    static WorkflowDefinition parseValid(String resource, UnitOrigin origin) {
        var result = WorkflowParser.parse(resource, read(resource), origin);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> resource + " was refused: " + result)
                .definition();
    }
}
