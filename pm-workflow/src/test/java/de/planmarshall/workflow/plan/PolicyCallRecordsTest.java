/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.TreeMap;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.ast.CallValue;

/**
 * Proves that the builder of {@link PolicyCall} builds the value of its canonical constructor.
 */
@EnableGeneratorController
@DisplayName("PolicyCall builder")
class PolicyCallRecordsTest {

    private static TreeMap<String, BoundArgument> args() {
        var args = new TreeMap<String, BoundArgument>();
        args.put("fix_budget", BoundArgument.fromTemplate(new CallValue.IntValue(3), "execute.pmwf"));
        return args;
    }

    @Test
    @DisplayName("the builder builds the plan unit's policy call the constructor builds")
    void builderEqualsConstructor() {
        var callSite = Generators.letterStrings(1, 20).next();
        var unit = Generators.letterStrings(1, 20).next();
        var args = args();

        var constructed = new PolicyCall(callSite, unit, args, null);
        var built = PolicyCall.builder().callSite(callSite).unit(unit).args(args).build();

        assertEquals(constructed, built, "the builder builds the same plan unit call");
    }

    @Test
    @DisplayName("the builder carries the composed unit of a nested policy call")
    void builderCarriesTheUnit() {
        var call = PolicyCall.builder()
                .callSite(Generators.letterStrings(1, 20).next())
                .unit(Generators.letterStrings(1, 20).next())
                .args(args())
                .inUnit("phase-5-execute")
                .build();

        assertEquals("phase-5-execute", call.inUnit(), "the builder carries the unit");
    }

    @Test
    @DisplayName("the builder refuses a missing policy unit")
    void builderRefusesMissingUnit() {
        var builder = PolicyCall.builder().callSite("finalize.remediate").args(args());
        var exception = assertThrows(NullPointerException.class, builder::build,
                "the builder names the missing unit");

        assertEquals("unit", exception.getMessage(), "the builder names the missing unit");
    }
}
