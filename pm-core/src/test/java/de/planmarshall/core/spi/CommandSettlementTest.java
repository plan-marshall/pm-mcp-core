/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.testing.generators.CommandGenerator;
import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * A settlement carries the result of one primitive run as flat plain values, checks the wire-name grammars of its
 * outcome and reason, and never prints a fact value.
 */
@EnableGeneratorController
@DisplayName("Settlement of a command")
class CommandSettlementTest {

    @Test
    @DisplayName("the builder builds the constructor's value")
    void builderBuildsTheConstructorsValue() {
        var settlement = settlement().build();

        var canonical = new CommandSettlement(settlement.command(), settlement.outcome(), settlement.outcomeClass(),
                settlement.reason(), settlement.facts(), settlement.spawnedJobId(), settlement.raisedCondition(),
                settlement.settledAt());

        assertEquals(canonical, settlement, "the builder builds the constructor's value");
    }

    @Test
    @DisplayName("the optional components default to null")
    void optionalComponentsDefaultToNull() {
        var settlement = settlement().build();

        assertAll(
                () -> assertNull(settlement.reason(), "the settlement carries no reason"),
                () -> assertNull(settlement.spawnedJobId(), "the settlement carries no spawned job"),
                () -> assertNull(settlement.raisedCondition(), "the settlement raises no condition"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullComponents")
    @DisplayName("a null required component is refused and named")
    void refusesNullComponent(String component, UnaryOperator<CommandSettlement.CommandSettlementBuilder> nulling) {
        var builder = nulling.apply(settlement());

        var thrown = assertThrows(NullPointerException.class, builder::build, "a null required component is refused");

        assertEquals(component, thrown.getMessage(), "the refusal names the null component");
    }

    static Stream<Arguments> nullComponents() {
        return Stream.of(
                Arguments.of("command", nulling(b -> b.command(null))),
                Arguments.of("outcome", nulling(b -> b.outcome(null))),
                Arguments.of("outcomeClass", nulling(b -> b.outcomeClass(null))),
                Arguments.of("facts", nulling(b -> b.facts(null))),
                Arguments.of("settledAt", nulling(b -> b.settledAt(null))));
    }

    private static UnaryOperator<CommandSettlement.CommandSettlementBuilder> nulling(
            UnaryOperator<CommandSettlement.CommandSettlementBuilder> operator) {
        return operator;
    }

    @Nested
    @DisplayName("Wire names of the outcome and the reason")
    class WireNames {

        // The values are grammar literals of the contract: [a-z][a-z0-9_-]{0,31} for an outcome.
        @ParameterizedTest
        @ValueSource(strings = {"done", "not-found", "commit_failed", "a", "a0123456789012345678901234567890"})
        @DisplayName("an outcome of the outcome grammar is accepted, hyphen included (contract literal)")
        void acceptsOutcomeOfTheGrammar(String outcome) {
            var settlement = settlement().outcome(outcome).build();

            assertEquals(outcome, settlement.outcome(), "the settlement keeps the outcome");
        }

        // The values are grammar literals of the contract: each breaks [a-z][a-z0-9_-]{0,31}.
        @ParameterizedTest
        @ValueSource(strings = {"", "Done", "0done", "-done", "with space", "a01234567890123456789012345678901"})
        @DisplayName("an outcome outside the outcome grammar is refused (contract literal)")
        void refusesOutcomeOutsideTheGrammar(String outcome) {
            var builder = settlement().outcome(outcome);

            assertThrows(IllegalArgumentException.class, builder::build, "an outcome outside the grammar is refused");
        }

        // The values are grammar literals of the contract: [a-z][a-z0-9_]{0,31} for a reason.
        @ParameterizedTest
        @ValueSource(strings = {"timeout", "commit_failed", "a", "a0123456789012345678901234567890"})
        @DisplayName("a reason of the reason grammar is accepted (contract literal)")
        void acceptsReasonOfTheGrammar(String reason) {
            var settlement = settlement().reason(reason).build();

            assertEquals(reason, settlement.reason(), "the settlement keeps the reason");
        }

        // The values are grammar literals of the contract: each breaks [a-z][a-z0-9_]{0,31}.
        @ParameterizedTest
        @ValueSource(strings = {"not-found", "", "Timeout", "0timeout", "a01234567890123456789012345678901"})
        @DisplayName("a reason outside the reason grammar is refused, hyphen included (contract literal)")
        void refusesReasonOutsideTheGrammar(String reason) {
            var builder = settlement().reason(reason);

            assertThrows(IllegalArgumentException.class, builder::build, "a reason outside the grammar is refused");
        }
    }

    @Nested
    @DisplayName("Raised condition")
    class RaisedCondition {

        @ParameterizedTest
        @MethodSource("de.planmarshall.core.spi.CommandSettlementTest#mappableKinds")
        @DisplayName("a mappable condition raised for a reason is accepted")
        void acceptsMappableCondition(String kind) {
            var settlement = settlement().reason(lowerCaseWord()).raisedCondition(kind).build();

            assertEquals(kind, settlement.raisedCondition(), "the settlement keeps the raised condition");
        }

        @Test
        @DisplayName("a condition no primitive can originate is refused (contract literal)")
        void refusesUnmappableCondition() {
            // guard_indeterminate is a condition kind of the engine, a contract literal
            var builder = settlement().reason(lowerCaseWord()).raisedCondition("guard_indeterminate");

            assertThrows(IllegalArgumentException.class, builder::build, "an unmappable condition is refused");
        }

        @ParameterizedTest
        @MethodSource("de.planmarshall.core.spi.CommandSettlementTest#mappableKinds")
        @DisplayName("a condition raised without a reason is refused")
        void refusesConditionWithoutReason(String kind) {
            var builder = settlement().raisedCondition(kind);

            assertThrows(IllegalArgumentException.class, builder::build, "a condition without a reason is refused");
        }
    }

    static List<String> mappableKinds() {
        return List.copyOf(OutcomeReason.MAPPABLE_CONDITION_KINDS);
    }

    @Nested
    @DisplayName("Facts")
    class Facts {

        @Test
        @DisplayName("the facts are an unmodifiable copy in the order of the argument")
        void factsAreAnOrderedUnmodifiableCopy() {
            var facts = new LinkedHashMap<String, Object>();
            var names = Stream.generate(() -> Generators.letterStrings(4, 12).next()).distinct().limit(5).toList();
            names.forEach(name -> facts.put(name, Generators.nonBlankStrings().next()));
            var settlement = settlement().facts(facts).build();

            facts.clear();

            assertAll(
                    () -> assertEquals(names, List.copyOf(settlement.facts().keySet()), "the facts keep their order"),
                    () -> assertEquals(5, settlement.facts().size(), "a change of the argument does not reach it"),
                    () -> assertThrows(UnsupportedOperationException.class, () -> settlement.facts().clear(),
                            "the facts of a settlement are unmodifiable"));
        }

        @Test
        @DisplayName("a fact with a null name is refused")
        void refusesNullFactName() {
            var facts = new HashMap<String, Object>();
            facts.put(null, Generators.nonBlankStrings().next());
            var builder = settlement().facts(facts);

            assertThrows(NullPointerException.class, builder::build, "a fact with a null name is refused");
        }

        @Test
        @DisplayName("a fact with a null value is refused without quoting a value")
        void refusesNullFactValue() {
            var secret = secretText();
            var facts = new LinkedHashMap<String, Object>();
            facts.put(Generators.letterStrings(4, 12).next(), secret);
            facts.put(Generators.letterStrings(13, 20).next(), null);
            var builder = settlement().facts(facts);

            var thrown = assertThrows(NullPointerException.class, builder::build, "a fact with a null value is refused");

            assertFalse(thrown.getMessage().contains(secret), "the refusal does not quote a fact value");
        }
    }

    @Test
    @DisplayName("toString prints the fact names, never their values nor the input")
    void toStringOmitsFactValuesAndInput() {
        var secret = secretText();
        var inputSecret = secretText();
        var name = Generators.letterStrings(4, 12).next();
        var command = CoreGenerators.commands().next();
        var secretCommand = Command.builder().commandId(command.commandId()).correlationId(command.correlationId())
                .primitiveId(command.primitiveId()).input(new CommandGenerator.Input(inputSecret))
                .provenance(command.provenance()).issuedAt(command.issuedAt()).build();
        var settlement = settlement().command(secretCommand).facts(Map.of(name, secret)).build();

        var printed = settlement.toString();

        assertAll(
                () -> assertFalse(printed.contains(secret), "the printed settlement omits the fact value"),
                () -> assertFalse(printed.contains(inputSecret), "the printed settlement omits the input"),
                () -> assertTrue(printed.contains(name), "the printed settlement names the fact"),
                () -> assertTrue(printed.contains(settlement.outcome()), "the printed settlement names the outcome"));
    }

    private static CommandSettlement.CommandSettlementBuilder settlement() {
        return CommandSettlement.builder()
                .command(CoreGenerators.commands().next())
                .outcome(lowerCaseWord())
                .outcomeClass(Generators.enumValues(OutcomeClass.class).next())
                .facts(Map.of())
                .settledAt(Instant.ofEpochMilli(Generators.integers(0, Integer.MAX_VALUE).next().longValue()));
    }

    private static String lowerCaseWord() {
        return Generators.letterStrings(1, 20).next().toLowerCase(Locale.ROOT);
    }

    private static String secretText() {
        return "ghp_" + Generators.letterStrings(36, 36).next();
    }
}
