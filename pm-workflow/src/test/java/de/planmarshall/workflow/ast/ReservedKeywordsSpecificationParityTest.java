/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reserved words of the code are the specification's list: {@link ReservedKeywords#ALL} is the code's copy of the
 * reserved-keyword bullet of the Workflow DSL grammar, so a word added to or removed from the specification without the
 * code fails here instead of being refused or admitted unseen at run time.
 * <p>
 * The specification is read from the repository beside the module (the working directory of a module's tests is the
 * module), the same way the schema-parity tests read their files; the bullet and its keywords are contracts.
 */
class ReservedKeywordsSpecificationParityTest {

    /** The specification part whose reserved-keyword bullet is the list. */
    private static final Path SPECIFICATION = Path.of("../doc/specification/workflow-dsl/01-grammar.adoc");

    /** The bullet that opens the list. */
    private static final String BULLET = "* *Reserved keywords*:";

    /** The sentence that ends the list in the bullet. */
    private static final String LIST_END = "are reserved.";

    private static final Pattern KEYWORD = Pattern.compile("`([a-z_]+)`");

    @Test
    @DisplayName("the reserved words are the specification's list")
    void shouldMatchTheSpecification() throws IOException {
        var bullet = Files.readAllLines(SPECIFICATION, UTF_8).stream()
                .filter(line -> line.startsWith(BULLET))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the reserved-keyword bullet is missing from " + SPECIFICATION));
        var list = bullet.substring(BULLET.length(), bullet.indexOf(LIST_END, BULLET.length()));
        var declared = KEYWORD.matcher(list).results().map(result -> result.group(1))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(new TreeSet<>(ReservedKeywords.ALL), declared,
                "the code and the specification name one set");
    }
}
