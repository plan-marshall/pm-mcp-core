/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.question;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * The decision envelope's schema carries no {@code blocks} member (Model Work Specification, The Decision Structure;
 * Store Schemas Specification, Question Record, which derives what a decision blocks on read). The object stays closed,
 * so a worker payload that carries the removed member fails the schema and is refused {@code invalid_link_args} naming
 * the member; the kept members beside it still declare their shape.
 * <p>
 * The schema file is test input (R10): its members and its closure are the contract this test pins.
 */
// Every datum in this class is a contract: the schema file and its member set.
@DisplayName("Decision envelope schema")
class DecisionEnvelopeSchemaTest {

    private static final String SCHEMA = "de/planmarshall/workflow/question/decision-envelope-v1.schema.json";

    private static Map<?, ?> schema;

    @BeforeAll
    static void readSchema() {
        schema = SchemaResource.load(SCHEMA);
    }

    private static Map<?, ?> properties() {
        return SchemaResource.object(schema.get("properties"));
    }

    private static List<String> strings(Object node, String member) {
        return SchemaResource.array(SchemaResource.object(node).get(member)).stream().map(String::valueOf).toList();
    }

    @Test
    @DisplayName("the removed blocks member is gone and the envelope stays closed")
    void blocksMemberIsGone() {
        assertAll("The stored blocks member is gone",
                () -> assertFalse(properties().containsKey("blocks"),
                        "the blocks member should be absent"),
                () -> assertFalse(strings(schema, "required").contains("blocks"),
                        "the blocks member should not be required"),
                () -> assertEquals(Boolean.FALSE, schema.get("additionalProperties"),
                        "a payload naming blocks must fail the schema"));
    }

    @Test
    @DisplayName("the kept members beside the removed one still declare their shape")
    void keptMembersStayDeclared() {
        assertAll("The kept members stay declared",
                () -> assertTrue(strings(schema, "required").contains("reversibility"),
                        "the reversibility should stay required"),
                () -> assertTrue(properties().containsKey("reversibility"),
                        "the reversibility should stay declared"),
                () -> assertTrue(strings(schema, "required").contains("title"),
                        "the title should stay required"),
                () -> assertTrue(properties().containsKey("parts"),
                        "the parts should stay declared"));
    }
}
