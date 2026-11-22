/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.easymock.EasyMock.expect;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.MessageMode;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.Params;

/**
 * {@code git.resolve-message}: one case per message mode and per presence of a bound message, the refusal of a message
 * {@link CommitMessages#validate(String)} rejects, the clean-tree route and the registration of the primitive.
 */
@EnableGeneratorController
@DisplayName("GitResolveMessagePrimitive")
class GitResolveMessagePrimitiveTest extends GitResolveMessagePrimitiveFixtures {

    @RepeatedTest(10)
    @DisplayName("formats the active deliverable and ignores a bound message")
    void formatsTheDeliverable() {
        var deliverable = deliverables.next();
        var module = modules.next();
        var title = titles.next();
        var parameters = Params.builder().messageMode(MessageMode.AUTO_DELIVERABLE).message(messages.next())
                .deliverable(deliverable).module(module).title(title).build();

        var message = readyMessage(run(parameters));

        assertEquals("feat(%s): complete deliverable %d - %s".formatted(module, deliverable, title), message,
                "The message is formatted from the deliverable");
    }

    @ParameterizedTest(name = "without {0}")
    @MethodSource("missingDeliverableFacts")
    @DisplayName("a call outside a complete deliverable goes to the composing task")
    void incompleteDeliverable(String missing) {
        var builder = Params.builder().messageMode(MessageMode.AUTO_DELIVERABLE).deliverable(deliverables.next())
                .module(modules.next()).title(titles.next());
        switch (missing) {
            case "deliverable" -> builder.deliverable(null);
            case "module" -> builder.module(null);
            default -> builder.title(null);
        }

        assertNeedsInteractive(run(builder.build()));
    }

    @Test
    @DisplayName("a module that breaks the header form goes to the composing task")
    void refusedHeader() {
        var parameters = Params.builder().messageMode(MessageMode.AUTO_DELIVERABLE)
                .deliverable(deliverables.next()).module(modules.next() + ")(").title(titles.next()).build();

        assertNeedsInteractive(run(parameters));
    }

    @RepeatedTest(10)
    @DisplayName("takes the bound message when it is valid")
    void boundMessage() {
        var bound = messages.next();
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK).message(bound)
                .stepId(stepIds.next()).build();

        assertEquals(bound, readyMessage(run(parameters)), "The bound message is the resolved message");
    }

    @Test
    @DisplayName("refuses a bound message without type and hands it to the task")
    void refusedBoundMessage() {
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK)
                .message(": " + titles.next()).stepId(stepIds.next()).build();

        assertNeedsInteractive(run(parameters));
    }

    @Test
    @DisplayName("falls back to the fixed message of the step")
    void stepFallback() {
        var stepId = stepIds.next();
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK).stepId(stepId).build();

        assertEquals("chore(finalize): apply %s changes".formatted(stepId), readyMessage(run(parameters)),
                "The step's fixed message is resolved");
    }

    @Test
    @DisplayName("falls back to the settle message for a caller that is no step")
    void settleFallback() {
        var parameters = Params.builder().messageMode(MessageMode.STEP_OR_FALLBACK).build();

        // The fixed message of the specification (Bound Parameter Contract, Stage 3).
        assertEquals("chore(finalize): settle changes", readyMessage(run(parameters)),
                "The settle message is resolved without a step");
    }

    @Test
    @DisplayName("always goes to the composing task, even with a bound message")
    void alwaysInteractive() {
        var parameters = Params.builder().messageMode(MessageMode.INTERACTIVE).message(messages.next())
                .stepId(stepIds.next()).build();

        assertNeedsInteractive(run(parameters));
    }

    @Test
    @DisplayName("a clean checkout is ready with no message to commit")
    void cleanCheckout() {
        expect(site.dirtyPaths(context)).andReturn(List.of());
        var parameters = Params.builder().messageMode(MessageMode.NONE_ASSERT_CLEAN).message(messages.next())
                .build();

        var result = run(parameters);

        assertAll("clean checkout",
                () -> assertEquals(Outcome.READY, result.outcome(), "A clean checkout resolves as ready"),
                () -> assertEquals(Map.of(), result.updatedFacts(), "A clean checkout carries no message"));
    }

    @Test
    @DisplayName("a dirty checkout goes to the composing task")
    void dirtyCheckout() {
        expect(site.dirtyPaths(context)).andReturn(List.of(Generators.letterStrings(1, 20).next()));
        var parameters = Params.builder().messageMode(MessageMode.NONE_ASSERT_CLEAN).build();

        assertNeedsInteractive(run(parameters));
    }

    @Test
    @DisplayName("the primitive passes the registry constructor")
    void registers() {
        var registry = new PrimitiveRegistry(List.of(primitive));

        assertTrue(registry.find(GitResolveMessagePrimitive.PRIMITIVE_ID).isPresent(),
                "The registry holds git.resolve-message");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(MessageMode.class)
    @DisplayName("each message mode carries the wire name of its input value")
    void modeWireNames(MessageMode mode) {
        // The values of the input message_mode are the wire contract of the specification.
        var expected = switch (mode) {
            case AUTO_DELIVERABLE -> "auto_deliverable";
            case STEP_OR_FALLBACK -> "step_or_fallback";
            case INTERACTIVE -> "interactive";
            case NONE_ASSERT_CLEAN -> "none_assert_clean";
        };

        assertEquals(expected, mode.wireName(), "The mode carries the wire name of its value");
    }

    @Test
    @DisplayName("the outcomes carry the wire names ready and needs_interactive")
    void outcomeWireNames() {
        // The outcome names are the wire contract the sub-workflow branches on.
        assertAll("outcome wire names",
                () -> assertEquals("ready", Outcome.READY.wireName(), "The ready outcome is named ready"),
                () -> assertEquals("needs_interactive", Outcome.NEEDS_INTERACTIVE.wireName(),
                        "The interactive outcome is needs_interactive"));
    }

    @Test
    @DisplayName("parameters without a message mode are refused")
    void modeRequired() {
        var builder = Params.builder();

        assertThrows(NullPointerException.class, builder::build, "A missing message mode is refused");
    }

    static Stream<String> missingDeliverableFacts() {
        return Stream.of("deliverable", "module", "title");
    }
}
