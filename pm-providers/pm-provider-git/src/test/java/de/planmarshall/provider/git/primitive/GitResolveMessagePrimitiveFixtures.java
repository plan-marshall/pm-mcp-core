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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitResolveMessagePrimitive.Params;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The fixture the {@code git.resolve-message} tests share: an EasyMock {@link CommitSite}, the generated inputs of the
 * message modes, and the ready-message assertions.
 */
abstract class GitResolveMessagePrimitiveFixtures {

    final TypedGenerator<String> messages = GitGenerators.commitMessages();

    final TypedGenerator<String> modules = Generators.strings("abcdefghijklmnopqrstuvwxyz-", 1, 20);

    final TypedGenerator<String> titles = Generators.letterStrings(1, 40);

    final TypedGenerator<String> stepIds = Generators.strings("abcdefghijklmnopqrstuvwxyz-", 1, 30);

    final TypedGenerator<Integer> deliverables = Generators.integers(1, 99);

    final TypedGenerator<String> ids = Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789-", 1, 24);

    CommitSite site;

    WorkflowExecutionContext context;

    GitResolveMessagePrimitive primitive;

    @BeforeEach
    void setUp() {
        site = createMock(CommitSite.class);
        context = createMock(WorkflowExecutionContext.class);
        primitive = new GitResolveMessagePrimitive(site);
    }

    PrimitiveResult<Outcome> run(Params parameters) {
        replay(site, context);
        var result = primitive.execute(context, parameters);
        verify(site, context);
        return result;
    }

    static String readyMessage(PrimitiveResult<Outcome> result) {
        assertEquals(Outcome.READY, result.outcome(), "The message resolves as ready");
        var message = (String) result.updatedFacts().get(GitResolveMessagePrimitive.MESSAGE_FACT);
        assertEquals(List.of(), CommitMessages.validate(message), "The resolved message is a valid message");
        return message;
    }

    static void assertNeedsInteractive(PrimitiveResult<Outcome> result) {
        assertAll("needs interactive",
                () -> assertEquals(Outcome.NEEDS_INTERACTIVE, result.outcome(),
                        "The message goes to the composing task"),
                () -> assertEquals(Map.of(), result.updatedFacts(), "No message is resolved for the task"));
    }
}
