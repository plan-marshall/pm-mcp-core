/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The server's own reading of an answer of the protocol {@code system_one} (Client Profiles § Judgment Endpoint, The
 * Request and the Answer): the model id, the answer of every question, the choice and the probabilities that sum to
 * {@code 1} within {@code 0.01}, and never the {@code confidence} member.
 */
@DisplayName("An answer of the protocol system_one")
class SystemOneAnswerTest {

    private static final String REQUESTED = "judge-2026-01-01";

    private static final Map<String, List<String>> OPTIONS = Map.of("q-1", List.of("a", "b"));

    private static final String VALID = "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"a\","
            + "\"probabilities\":{\"a\":0.75,\"b\":0.25}}},\"usage\":{\"input_tokens\":120,\"output_tokens\":3}}";

    @Test
    @DisplayName("a valid answer is read with its choice, its probabilities, its model and its usage")
    void validAnswer() {
        var read = SystemOneAnswer.read(VALID, REQUESTED, OPTIONS);

        assertAll(
                () -> assertTrue(read.isValid()),
                () -> assertEquals(REQUESTED, read.answer().model()),
                () -> assertEquals("a", read.answer().answers().get("q-1").choice()),
                () -> assertEquals(0, new BigDecimal("0.75").compareTo(
                        read.answer().answers().get("q-1").probabilities().get("a"))),
                () -> assertEquals(120, read.answer().usage().inputTokens().value()),
                () -> assertEquals(3, read.answer().usage().outputTokens().value()),
                () -> assertTrue(read.answer().document().member("answers").isPresent()));
    }

    @Test
    @DisplayName("the confidence member is never read")
    void confidenceIsNotAScore() {
        var body = VALID.replace("\"choice\":\"a\",", "\"choice\":\"a\",\"confidence\":0.99,");

        var read = SystemOneAnswer.read(body, REQUESTED, OPTIONS);

        assertAll(
                () -> assertTrue(read.isValid()),
                () -> assertFalse(read.answer().document().toString().contains("confidence")));
    }

    @Test
    @DisplayName("an answer of another model id is refused model_alias")
    void modelAlias() {
        var read = SystemOneAnswer.read(VALID.replace(REQUESTED, "judge-2026-02-01"), REQUESTED, OPTIONS);

        assertAll(
                () -> assertFalse(read.isValid()),
                () -> assertEquals(SystemOneAnswer.Refusal.MODEL_ALIAS, read.refusal()),
                () -> assertTrue(read.detail().contains("judge-2026-02-01")),
                () -> assertTrue(read.detail().contains(REQUESTED)));
    }

    /** The bodies of the answers that do not validate, and what each one lacks. */
    static Stream<Arguments> invalidAnswers() {
        return Stream.of(
                Arguments.of("the answer is no JSON document", "not json"),
                Arguments.of("the answer names no model",
                        "{\"answers\":{\"q-1\":{\"choice\":\"a\",\"probabilities\":{\"a\":1,\"b\":0}}}}"),
                Arguments.of("the answer holds no answers", "{\"model\":\"judge-2026-01-01\"}"),
                Arguments.of("a question is not answered",
                        "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-2\":{\"choice\":\"a\","
                                + "\"probabilities\":{\"a\":1,\"b\":0}}}}"),
                Arguments.of("the choice is not an option",
                        "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"z\","
                                + "\"probabilities\":{\"a\":1,\"b\":0}}}}"),
                Arguments.of("the probabilities name an unknown option",
                        "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"a\","
                                + "\"probabilities\":{\"a\":0.75,\"z\":0.25}}}}"),
                Arguments.of("a probability is above 1",
                        "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"a\","
                                + "\"probabilities\":{\"a\":1.2,\"b\":-0.2}}}}"),
                Arguments.of("the probabilities do not sum to 1 within 0.01",
                        "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"a\","
                                + "\"probabilities\":{\"a\":0.9,\"b\":0.12}}}}"),
                Arguments.of("the answer holds no probabilities",
                        "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"a\"}}}"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAnswers")
    @DisplayName("an answer that does not validate is refused invalid_answer")
    void invalidAnswer(String name, String body) {
        var read = SystemOneAnswer.read(body, REQUESTED, OPTIONS);

        assertAll(
                () -> assertFalse(read.isValid()),
                () -> assertEquals(SystemOneAnswer.Refusal.INVALID_ANSWER, read.refusal()));
    }

    @Test
    @DisplayName("the tolerance is exactly 0.01")
    void tolerance() {
        var within = VALID.replace("\"a\":0.75,\"b\":0.25", "\"a\":0.9,\"b\":0.11");
        var outside = VALID.replace("\"a\":0.75,\"b\":0.25", "\"a\":0.9,\"b\":0.12");

        assertAll(
                () -> assertTrue(SystemOneAnswer.read(within, REQUESTED, OPTIONS).isValid()),
                () -> assertFalse(SystemOneAnswer.read(outside, REQUESTED, OPTIONS).isValid()));
    }

    @Test
    @DisplayName("an answer without usage records its counts unmeasured")
    void usageUnmeasured() {
        var body = "{\"model\":\"judge-2026-01-01\",\"answers\":{\"q-1\":{\"choice\":\"a\","
                + "\"probabilities\":{\"a\":1,\"b\":0}}}}";

        var read = SystemOneAnswer.read(body, REQUESTED, OPTIONS);

        assertAll(
                () -> assertTrue(read.isValid()),
                () -> assertFalse(read.answer().usage().inputTokens().isMeasured()),
                () -> assertFalse(read.answer().usage().outputTokens().isMeasured()));
    }
}
