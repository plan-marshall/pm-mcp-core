/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static de.planmarshall.runtime.test.JsonDocuments.schema;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The writing skill's need derived from the link schemas.
 *
 * <p>
 * The parameter and link names are generated. The literals that stay are the contract: the parameter type strings
 * ({@code text}, {@code list<text>}, {@code int}, {@code schema[...]}) and the JSON schema documents the derivation
 * reads.
 */
@EnableGeneratorController
@DisplayName("Free-text derivation")
class FreeTextDerivationTest {

    @Test
    @DisplayName("a parameter of type text writes free text")
    void textParameter() {
        assertTrue(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter(name(), "text", null)))),
                "a parameter of type text writes free text");
        assertTrue(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter(name(), "list<text>", null)))),
                "a list of text parameters writes free text");
    }

    @Test
    @DisplayName("a named schema with a free string field writes free text")
    void freeStringField() {
        var schema = schema("{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}");
        assertTrue(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter(name(), "schema[intent-record]",
                schema)))), "a free string field in a schema is free text");
    }

    @Test
    @DisplayName("a string field with an enum or a pattern is a closed value")
    void closedStringField() {
        var withEnum = schema("{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"a\"]}}}");
        var withPattern = schema("{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"string\",\"pattern\":\"^[a-z]+$\"}}}");
        assertFalse(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter(name(), "schema[x]", withEnum)))),
                "a string field with an enum is a closed value");
        assertFalse(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter(name(), "schema[x]",
                withPattern)))), "a string field with a pattern is closed");
    }

    @Test
    @DisplayName("no link and no free text writes no free text")
    void noFreeText() {
        assertFalse(FreeTextDerivation.writesFreeText(List.of()), "no link at all writes no free text");
        assertFalse(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter(name(), "int", null)))),
                "an int parameter writes no free text");
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next();
    }

    private static LinkSchema link(LinkParameter parameter) {
        return new LinkSchema(name() + ":" + name(), List.of(parameter));
    }
}
