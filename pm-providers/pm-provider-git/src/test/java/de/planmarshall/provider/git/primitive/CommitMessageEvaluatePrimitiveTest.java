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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.provider.git.primitive.CommitMessageEvaluatePrimitive.Outcome;
import de.planmarshall.provider.git.primitive.CommitMessageEvaluatePrimitive.Params;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * {@code commit-message.evaluate}: the submitted message is returned as the fact to commit, its free parameter carries
 * the bounds of the message type, and the primitive registers.
 */
@EnableGeneratorController
@DisplayName("CommitMessageEvaluatePrimitive")
class CommitMessageEvaluatePrimitiveTest {

    private final TypedGenerator<String> messages = GitGenerators.commitMessages();

    private final CommitMessageEvaluatePrimitive primitive = new CommitMessageEvaluatePrimitive();

    @RepeatedTest(10)
    @DisplayName("returns the submitted message as the valid fact to commit")
    void returnsTheSubmission() {
        var context = createMock(WorkflowExecutionContext.class);
        replay(context);
        var message = messages.next();

        var result = primitive.execute(context, new Params(message));

        verify(context);
        assertAll("evaluated submission",
                () -> assertEquals(Outcome.VALID, result.outcome(), "A submitted message evaluates as valid"),
                () -> assertEquals(Map.of(CommitMessageEvaluatePrimitive.MESSAGE_FACT, message),
                        result.updatedFacts(), "The submitted message is the one fact"),
                () -> assertEquals(List.of(), CommitMessages.validate(message),
                        "The submitted message is a valid message"));
    }

    @Test
    @DisplayName("the free parameter carries the bounds of commit_message[1..2000]")
    void freeParameterBounds() {
        var declared = Params.class.getRecordComponents()[0].getAnnotation(FreeParam.class);

        assertNotNull(declared, "The message is declared a free parameter");
        // The bounds of the message type commit_message[1..2000] of the specification.
        assertAll("free parameter bounds",
                () -> assertEquals(1, declared.minLength(), "The least message length is one"),
                () -> assertEquals(2000, declared.maxLength(), "The greatest message length is 2000"));
    }

    @Test
    @DisplayName("the primitive passes the registry constructor")
    void registers() {
        var registry = new PrimitiveRegistry(List.of(primitive));

        assertTrue(registry.find(CommitMessageEvaluatePrimitive.ID).isPresent(),
                "The registry holds commit-message.evaluate");
    }

    @Test
    @DisplayName("the one outcome carries the wire name valid")
    void outcomeWireName() {
        // The outcome name is the wire contract the entity kind branches on.
        assertEquals("valid", Outcome.VALID.wireName(), "The one outcome is named valid");
    }

    @Test
    @DisplayName("a submission without a message is refused")
    void messageRequired() {
        assertThrows(NullPointerException.class, () -> new Params(null), "A missing submission is refused");
    }
}
