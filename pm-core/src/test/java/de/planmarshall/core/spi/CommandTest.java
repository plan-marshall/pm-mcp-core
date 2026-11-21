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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * A command names one primitive run and never prints the input it carries.
 */
@EnableGeneratorController
@DisplayName("Command of a primitive run")
class CommandTest {

    /** An input record of the test whose component holds secret-looking text. */
    record SecretInput(String token) {
    }

    @Test
    @DisplayName("the builder builds the constructor's value")
    void builderBuildsTheConstructorsValue() {
        var command = CoreGenerators.commands().next();

        var canonical = new Command(command.commandId(), command.correlationId(), command.primitiveId(),
                command.input(), command.provenance(), command.issuedAt());

        assertAll(
                () -> assertEquals(canonical, command, "the builder builds the constructor's value"),
                () -> assertSame(command.input(), canonical.input(), "the command keeps the same input instance"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nullComponents")
    @DisplayName("a null component is refused and named")
    void refusesNullComponent(String component, UnaryOperator<Command.CommandBuilder> nulling) {
        var builder = nulling.apply(builderOf(CoreGenerators.commands().next()));

        var thrown = assertThrows(NullPointerException.class, builder::build, "a null component is refused");

        assertEquals(component, thrown.getMessage(), "the refusal names the null component");
    }

    static Stream<Arguments> nullComponents() {
        return Stream.of(
                Arguments.of("commandId", (UnaryOperator<Command.CommandBuilder>) b -> b.commandId(null)),
                Arguments.of("correlationId", (UnaryOperator<Command.CommandBuilder>) b -> b.correlationId(null)),
                Arguments.of("primitiveId", (UnaryOperator<Command.CommandBuilder>) b -> b.primitiveId(null)),
                Arguments.of("input", (UnaryOperator<Command.CommandBuilder>) b -> b.input(null)),
                Arguments.of("provenance", (UnaryOperator<Command.CommandBuilder>) b -> b.provenance(null)),
                Arguments.of("issuedAt", (UnaryOperator<Command.CommandBuilder>) b -> b.issuedAt(null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    @DisplayName("a blank primitive id is refused without quoting the input")
    void refusesBlankPrimitiveId(String blank) {
        var secret = secretText();
        var builder = builderOf(CoreGenerators.commands().next()).primitiveId(blank).input(new SecretInput(secret));

        var thrown = assertThrows(IllegalArgumentException.class, builder::build, "a blank primitive id is refused");

        assertFalse(thrown.getMessage().contains(secret), "the refusal does not quote the input");
    }

    @Test
    @DisplayName("toString names the class of the input, never its values")
    void toStringOmitsTheInput() {
        var secret = secretText();
        var command = builderOf(CoreGenerators.commands().next()).input(new SecretInput(secret)).build();

        var printed = command.toString();

        assertAll(
                () -> assertFalse(printed.contains(secret), "the printed command omits the input value"),
                () -> assertTrue(printed.contains(SecretInput.class.getName()), "the printed command names the class"),
                () -> assertTrue(printed.contains(command.commandId().value()), "the printed command names its id"),
                () -> assertTrue(printed.contains(command.correlationId().value()),
                        "the printed command names its correlation"),
                () -> assertTrue(printed.contains(command.primitiveId()), "the printed command names the primitive"));
    }

    private static String secretText() {
        return "ghp_" + Generators.letterStrings(36, 36).next();
    }

    private static Command.CommandBuilder builderOf(Command command) {
        return Command.builder().commandId(command.commandId()).correlationId(command.correlationId())
                .primitiveId(command.primitiveId()).input(command.input()).provenance(command.provenance())
                .issuedAt(command.issuedAt());
    }
}
