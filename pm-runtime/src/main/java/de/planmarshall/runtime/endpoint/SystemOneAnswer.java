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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.store.HarnessUsage;

/**
 * The server's own reading and check of an answer of the protocol {@code system_one} (Client Profiles § Judgment
 * Endpoint, The Request and the Answer): {@code answers.<question_id>} with {@code choice} and {@code probabilities},
 * the member {@code model}, and {@code usage}.
 * <p>
 * An answer is valid when its {@code model} equals the requested id, it holds an answer for every question, each
 * {@code choice} is a {@code branch_key} of the question, and its {@code probabilities} name only the question's
 * options, each between {@code 0} and {@code 1}, summing to {@code 1} within {@code 0.01}. The {@code confidence}
 * member is never read: it describes how concentrated the distribution is, not how likely the option is right. An answer
 * that names another model id is refused {@code model_alias}, so a service that silently re-routes a versioned id to a
 * new release ends the attempt instead of answering under an unchanged qualification record.
 *
 * @since 0.1
 */
public final class SystemOneAnswer {

    /** How far the probabilities of one question may sum from {@code 1}. */
    public static final BigDecimal SUM_TOLERANCE = new BigDecimal("0.01");

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;

    /**
     * One answer of a question: the chosen {@code branch_key} and the probability of each option.
     *
     * @param choice        the chosen {@code branch_key}
     * @param probabilities the probability of each option of the question
     */
    public record QuestionAnswer(String choice, Map<String, BigDecimal> probabilities) {

        /** Copies the probabilities and refuses a missing choice. */
        public QuestionAnswer {
            Objects.requireNonNull(choice, "choice");
            probabilities = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(probabilities, "probabilities")));
        }
    }

    /**
     * A valid answer of one request.
     *
     * @param model   the model id the service answered with; equal to the requested id
     * @param answers the answer of every question, by {@code question_id}
     * @param usage   the usage of the answer
     */
    public record Answer(String model, Map<String, QuestionAnswer> answers, HarnessUsage usage) {

        /** Copies the answers and refuses a missing value. */
        public Answer {
            Objects.requireNonNull(model, "model");
            answers = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(answers, "answers")));
            Objects.requireNonNull(usage, "usage");
        }

        /**
         * @return the validated answer as a document: the model, and the choice and probabilities of every question,
         *         with nothing the service sent beside them
         */
        public JsonNode document() {
            var answersNode = new LinkedHashMap<String, JsonNode>();
            answers.forEach((questionId, answer) -> {
                var probabilities = new LinkedHashMap<String, JsonNode>();
                answer.probabilities().forEach((option, value) -> probabilities.put(option, JsonNode.decimal(value)));
                answersNode.put(questionId, JsonNode.object(Map.of("choice", JsonNode.string(answer.choice()),
                        "probabilities", JsonNode.object(probabilities))));
            });
            return JsonNode.object(Map.of("model", JsonNode.string(model), "answers",
                    JsonNode.object(answersNode)));
        }
    }

    /** Why an answer is refused. The set is closed. */
    public enum Refusal {
        /** The answer is not a valid answer of the protocol. */
        INVALID_ANSWER("invalid_answer"),
        /** The service answered with another model id than the requested one. */
        MODEL_ALIAS("model_alias");

        private final String wireName;

        Refusal(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the refusal in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The reading of one answer: the answer, or why it is refused.
     *
     * @param answer  the answer; {@code null} when {@code refusal} is set
     * @param refusal why the answer is refused; {@code null} when it is valid
     * @param detail  what happened, for a record; never the answer body
     * @since 0.1
     */
    public record Read(Answer answer, Refusal refusal, String detail) {

        /** Copies the values and refuses a reading that is neither. */
        public Read {
            Objects.requireNonNull(detail, "detail");
            if ((answer == null) == (refusal == null)) {
                throw new IllegalArgumentException("A reading is either an answer or a refusal");
            }
        }

        static Read valid(Answer answer) {
            return new Read(answer, null, "answered");
        }

        static Read refused(Refusal refusal, String detail) {
            return new Read(null, refusal, detail);
        }

        /** @return whether the answer is valid */
        public boolean isValid() {
            return answer != null;
        }
    }

    private SystemOneAnswer() {
    }

    /**
     * Reads and checks one answer of a judgment request.
     *
     * @param body           the body of the answer
     * @param requestedModel the versioned model id the request named
     * @param options        the branch keys of every question of the request, by {@code question_id}
     * @return the answer, or why it is refused
     */
    public static Read read(String body, String requestedModel, Map<String, List<String>> options) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(requestedModel, "requestedModel");
        Objects.requireNonNull(options, "options");
        final JsonNode document;
        try {
            document = JsonDocument.parse(body.getBytes(StandardCharsets.UTF_8), "answer");
        } catch (ProfileRefusal | IllegalArgumentException _) {
            return Read.refused(Refusal.INVALID_ANSWER, "the answer is no JSON document");
        }
        if (!document.isObject()) {
            return Read.refused(Refusal.INVALID_ANSWER, "the answer is not a JSON object");
        }
        var model = document.member("model").flatMap(JsonNode::text);
        if (model.isEmpty()) {
            return Read.refused(Refusal.INVALID_ANSWER, "the answer names no model");
        }
        if (!requestedModel.equals(model.get())) {
            return Read.refused(Refusal.MODEL_ALIAS,
                    "the answer names the model '%s', the request the model '%s'".formatted(model.get(),
                            requestedModel));
        }
        var answers = document.member("answers").filter(JsonNode::isObject).orElse(null);
        if (answers == null) {
            return Read.refused(Refusal.INVALID_ANSWER, "the answer holds no answers object");
        }
        var read = new LinkedHashMap<String, QuestionAnswer>();
        for (var entry : options.entrySet()) {
            var questionId = entry.getKey();
            var question = answers.member(questionId).filter(JsonNode::isObject).orElse(null);
            if (question == null) {
                return Read.refused(Refusal.INVALID_ANSWER,
                        "the answer holds no answer to question '%s'".formatted(questionId));
            }
            var choice = question.member("choice").flatMap(JsonNode::text).orElse(null);
            if (choice == null || !entry.getValue().contains(choice)) {
                return Read.refused(Refusal.INVALID_ANSWER,
                        "the choice of question '%s' is not one of its options".formatted(questionId));
            }
            var probabilities = question.member("probabilities").filter(JsonNode::isObject).orElse(null);
            if (probabilities == null) {
                return Read.refused(Refusal.INVALID_ANSWER,
                        "the answer to question '%s' holds no probabilities".formatted(questionId));
            }
            var figures = new LinkedHashMap<String, BigDecimal>();
            var sum = ZERO;
            for (var probability : probabilities.members().entrySet()) {
                if (!entry.getValue().contains(probability.getKey())) {
                    return Read.refused(Refusal.INVALID_ANSWER,
                            "the probabilities of question '%s' name an option it does not have"
                                    .formatted(questionId));
                }
                var value = probability.getValue().decimalValue().orElse(null);
                if (value == null || value.compareTo(ZERO) < 0 || value.compareTo(ONE) > 0) {
                    return Read.refused(Refusal.INVALID_ANSWER,
                            "a probability of question '%s' is not between 0 and 1".formatted(questionId));
                }
                figures.put(probability.getKey(), value);
                sum = sum.add(value);
            }
            if (sum.subtract(ONE).abs().compareTo(SUM_TOLERANCE) > 0) {
                return Read.refused(Refusal.INVALID_ANSWER,
                        "the probabilities of question '%s' do not sum to 1 within %s".formatted(questionId,
                                SUM_TOLERANCE.toPlainString()));
            }
            read.put(questionId, new QuestionAnswer(choice, figures));
        }
        return Read.valid(new Answer(model.get(), read, usage(document)));
    }

    /**
     * The usage of an answer: {@code input_tokens} and {@code output_tokens} of its {@code usage} object, recorded per
     * task like the usage of any endpoint; a count the answer lacks is {@code unmeasured}.
     */
    private static HarnessUsage usage(JsonNode answer) {
        return HarnessUsage.fromEndpoint(figure(answer, "input_tokens"), figure(answer, "output_tokens"));
    }

    private static OptionalLong figure(JsonNode answer, String member) {
        var node = answer.member("usage").flatMap(value -> value.member(member)).orElse(null);
        return node == null ? OptionalLong.empty() : node.integerValue();
    }
}
