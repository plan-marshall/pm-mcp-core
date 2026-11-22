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
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.provider.git.primitive.CommitSite.DiffSummary;
import de.planmarshall.provider.git.primitive.GitTemplateMessagePrimitive.Outcome;
import de.planmarshall.provider.git.primitive.GitTemplateMessagePrimitive.Params;

/**
 * {@code git.template-message}: the type priority over generated sets of suggested types, the module scope and its
 * absence, the validity of every rendered message, and the registration of the primitive.
 */
@EnableGeneratorController
@DisplayName("GitTemplateMessagePrimitive")
class GitTemplateMessagePrimitiveTest {

    /** The type population the generated suggestions draw from: the primitive's own priority list. */
    private static final List<String> PRIORITY = GitTemplateMessagePrimitive.TYPE_PRIORITY;

    private final TypedGenerator<Integer> typeMasks = Generators.integers(0, (1 << PRIORITY.size()) - 1);

    private final TypedGenerator<String> foreignTypes = Generators.strings("ABCDEFGHIJKLMNOPQRSTUVWXYZ", 1, 10);

    private final TypedGenerator<String> scopes = Generators.strings("abcdefghijklmnopqrstuvwxyz-", 1, 20);

    private final TypedGenerator<String> subjects = Generators.letterStrings(1, 40);

    private final TypedGenerator<Integer> counts = Generators.integers(0, 500);

    private CommitSite site;

    private WorkflowExecutionContext context;

    private GitTemplateMessagePrimitive primitive;

    @BeforeEach
    void setUp() {
        site = createMock(CommitSite.class);
        context = createMock(WorkflowExecutionContext.class);
        primitive = new GitTemplateMessagePrimitive(site);
    }

    @Nested
    @DisplayName("execute")
    class Execute {

        @RepeatedTest(30)
        @DisplayName("takes the suggested type of highest priority and the module scope")
        void typePriorityWithScope() {
            var suggested = suggestedTypes();
            var scope = scopes.next();
            var subject = subjects.next();

            var message = render(new DiffSummary(counts.next(), scope, suggested), subject);

            assertEquals("%s(%s): %s".formatted(expectedType(suggested), scope, subject), message,
                    "The message carries the top type and the scope");
        }

        @RepeatedTest(10)
        @DisplayName("leaves the scope out when the changes span several modules")
        void withoutScope() {
            var suggested = suggestedTypes();
            var subject = subjects.next();

            var message = render(new DiffSummary(counts.next(), null, suggested), subject);

            assertEquals("%s: %s".formatted(expectedType(suggested), subject), message,
                    "The message has no scope without a module scope");
        }

        @Test
        @DisplayName("falls back to chore when no type is suggested")
        void fallbackType() {
            var subject = subjects.next();

            var message = render(new DiffSummary(counts.next(), null, Set.of()), subject);

            // The fallback type of the specification.
            assertEquals("chore: " + subject, message, "The fallback type chore is taken");
        }

        @Test
        @DisplayName("ignores a suggested type outside the priority list")
        void foreignTypeOnly() {
            var subject = subjects.next();

            var message = render(new DiffSummary(counts.next(), null, Set.of(foreignTypes.next())), subject);

            assertEquals("chore: " + subject, message, "A type outside the priority is ignored");
        }
    }

    @Nested
    @DisplayName("declarations")
    class Declarations {

        @Test
        @DisplayName("the priority is the order the specification states")
        void priority() {
            // the literal order is the contract: the type priority the specification states (Stage 3)
            var actual = GitTemplateMessagePrimitive.TYPE_PRIORITY;

            assertEquals(List.of("fix", "feat", "perf", "refactor", "docs", "style", "test", "chore", "ci"), actual,
                    "The type priority is the specified order");
        }

        @Test
        @DisplayName("the primitive passes the registry constructor")
        void registers() {
            var registry = new PrimitiveRegistry(List.of(primitive));

            assertTrue(registry.find(GitTemplateMessagePrimitive.PRIMITIVE_ID).isPresent(),
                    "The registry holds git.template-message");
        }

        @Test
        @DisplayName("the one outcome carries the wire name ready")
        void outcomeWireName() {
            // The outcome name is the wire contract the sub-workflow branches on.
            assertEquals("ready", Outcome.READY.wireName(), "The one outcome is named ready");
        }

        @Test
        @DisplayName("parameters without a subject are refused")
        void subjectRequired() {
            assertThrows(NullPointerException.class, () -> new Params(null), "A missing subject is refused");
        }

        @Test
        @DisplayName("a diff summary with a negative count is refused")
        void negativeCount() {
            var negative = -Generators.integers(1, 100).next();

            assertThrows(IllegalArgumentException.class, () -> new DiffSummary(negative, null, Set.of()),
                    "A negative changed-file count is refused");
        }
    }

    /** A random subset of the priority list, with a foreign type mixed in half of the time. */
    private Set<String> suggestedTypes() {
        var mask = typeMasks.next();
        var suggested = new HashSet<String>();
        for (var index = 0; index < PRIORITY.size(); index++) {
            if ((mask & (1 << index)) != 0) {
                suggested.add(PRIORITY.get(index));
            }
        }
        if (Generators.booleans().next()) {
            suggested.add(foreignTypes.next());
        }
        return suggested;
    }

    private static String expectedType(Set<String> suggested) {
        return PRIORITY.stream().filter(suggested::contains).findFirst().orElse("chore");
    }

    /** Executes the primitive over the summary and checks the one outcome and the validity of the message. */
    private String render(DiffSummary summary, String subject) {
        expect(site.diffSummary(context)).andReturn(summary);
        replay(site, context);

        var result = primitive.execute(context, new Params(subject));

        verify(site, context);
        var message = (String) result.updatedFacts().get(GitTemplateMessagePrimitive.MESSAGE_FACT);
        assertAll("rendered message",
                () -> assertEquals(Outcome.READY, result.outcome(), "The template message resolves as ready"),
                () -> assertEquals(Map.of(GitTemplateMessagePrimitive.MESSAGE_FACT, message), result.updatedFacts(),
                        "The message is the one fact of the result"),
                () -> assertEquals(List.of(), CommitMessages.validate(message),
                        "The rendered message is a valid message"));
        return message;
    }
}
