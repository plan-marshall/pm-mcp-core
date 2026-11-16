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

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The static reads of a unit (PM-WF-16, Rule 1): every {@code reads_config} key exists in the closed configuration,
 * every {@code reads_fact} is a known kind, and every input fallback is a listed key whose domain is assignable to the
 * input type. The registries are arguments of the verifier, so the fixtures of this class supply a small registry of
 * their own; the default validator carries the empty registries and fails closed.
 */
@EnableGeneratorController
@DisplayName("The static reads of a unit")
class UnitInterfaceChecksTest {

    /** A plan unit that declares what it reads and one input with a fallback. */
    private static final String DECLARED_READS = """
            ---
            name: verify.declared-reads
            description: A unit written for the verifier tests.
            type: workflow
            icon: workflow.default
            reads_config: project.review.currency
            reads_fact: push_remote
            input: mode
            input_type: enum[current|stale]
            input_fallback: project.review.currency
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow verify.declared-reads version 1 format 1
            scope plan
            start verify.entry

            state verify.entry terminal done success
            """;

    private static WorkflowDefinition unit(String source) {
        return VerifyFixtures.parseSource("declared-reads.pmwf", source);
    }

    @Test
    @DisplayName("refuses a reads_config key when the validator carries the empty registries")
    void refusesWithTheEmptyRegistries() {
        var found = new WorkflowValidator(TestPrimitives.registry(), List.of()).verify(unit(DECLARED_READS))
                .violations();

        assertEquals(List.of("CONFIG_KEY_BINDING"), found.stream().map(violation -> violation.rule().name()).toList());
    }
}
