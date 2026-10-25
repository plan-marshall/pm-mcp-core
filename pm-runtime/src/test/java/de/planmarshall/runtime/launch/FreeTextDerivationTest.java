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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/** The writing skill's need derived from the link schemas. */
@DisplayName("Free-text derivation")
class FreeTextDerivationTest {

    @Test
    @DisplayName("a parameter of type text writes free text")
    void textParameter() {
        assertTrue(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter("summary", "text", null)))));
        assertTrue(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter("notes", "list<text>", null)))));
    }

    @Test
    @DisplayName("a named schema with a free string field writes free text")
    void freeStringField() {
        var schema = schema("{\"type\":\"object\",\"properties\":{\"title\":{\"type\":\"string\"}}}");
        assertTrue(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter("intent", "schema[intent-record]",
                schema)))));
    }

    @Test
    @DisplayName("a string field with an enum or a pattern is a closed value")
    void closedStringField() {
        var withEnum = schema("{\"type\":\"object\",\"properties\":{\"status\":{\"type\":\"string\",\"enum\":[\"a\"]}}}");
        var withPattern = schema("{\"type\":\"object\",\"properties\":{\"key\":{\"type\":\"string\",\"pattern\":\"^[a-z]+$\"}}}");
        assertFalse(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter("a", "schema[x]", withEnum)))));
        assertFalse(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter("b", "schema[x]", withPattern)))));
    }

    @Test
    @DisplayName("no link and no free text writes no free text")
    void noFreeText() {
        assertFalse(FreeTextDerivation.writesFreeText(List.of()));
        assertFalse(FreeTextDerivation.writesFreeText(List.of(link(new LinkParameter("count", "int", null)))));
    }

    private static LinkSchema link(LinkParameter parameter) {
        return new LinkSchema("submit:task", List.of(parameter));
    }

    private static JsonNode schema(String document) {
        return JsonDocument.parse(document.getBytes(StandardCharsets.UTF_8), "schema");
    }
}
