/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.store.RetryCounters.RetryRule;

/**
 * The two closed lists of the status codec keep parity with the schema of the workflow state store (PM-ARCH-6,
 * PM-WF-7): the members {@link WorkflowStatusCodec#MEMBERS} the codec reads and writes are the schema's members in
 * schema order, and the {@link RetryRule}s are the property names the schema admits for {@code retry_counters}. A
 * member or key added to or removed from the schema without the code fails here instead of being refused or accepted
 * unseen at run time. The schema is read from the documentation checkout named by
 * {@value SpecificationDocument#ROOT_PROPERTY}; without the checkout the tests are skipped with the missing path
 * named.
 */
@DisplayName("Workflow state store schema parity")
class WorkflowStatusSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/02-workflow-state-store.adoc";
    private static final String HEADING = "== Workflow State Store";
    private static final String RETRY_COUNTERS = "retry_counters";

    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        var schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
    }

    @Test
    @DisplayName("the members of the codec are the members of the schema in schema order")
    void membersEqualSchema() {
        var expected = properties.keySet().stream().map(String::valueOf).toList();

        assertFalse(expected.isEmpty(), "the schema declares members");
        assertEquals(WorkflowStatusCodec.MEMBERS, expected);
    }

    @Test
    @DisplayName("the retry rules are the property names the schema admits for retry_counters")
    void retryRulesEqualSchema() {
        var retryCounters = SpecificationDocument.object(properties.get(RETRY_COUNTERS));
        var propertyNames = SpecificationDocument.object(retryCounters.get("propertyNames"));
        var expected = SpecificationDocument.array(propertyNames.get("enum")).stream().map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());

        assertFalse(expected.isEmpty(), "the schema names the retry rules");
        assertEquals(expected, wireNames());
    }

    private static Set<String> wireNames() {
        return Arrays.stream(RetryRule.values()).map(RetryRule::wireName).collect(Collectors.toUnmodifiableSet());
    }
}
