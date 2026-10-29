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

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.store.HarnessUsage;

/**
 * What a task on an endpoint harness ended with (Client Profiles § The Request): an accepted answer, an attempt that
 * ended like a worker without a valid result, or a request refused before it was sent.
 * <p>
 * An answer that does not validate, a refusal text, an HTTP error and a timeout end the attempt alike; the task's own
 * rules then apply. The token counts of the answer's {@code usage} object are kept per task, and a count the answer
 * lacks is {@code unmeasured}.
 *
 * @param fate    how the task ended
 * @param reason  why; {@link Reason#NONE} for an accepted answer
 * @param detail  what happened, for a record; never the task text
 * @param content the validated answer document, as the endpoint returned it; empty unless the fate is
 *                {@link Fate#ANSWERED}
 * @param usage   the usage of the answer; {@link HarnessUsage#none()} when no answer was measured
 * @since 0.1
 */
public record EndpointOutcome(Fate fate, Reason reason, String detail, String content, HarnessUsage usage) {

    /** Copies the values and refuses an accepted answer with a reason. */
    public EndpointOutcome {
        Objects.requireNonNull(fate, "fate");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(usage, "usage");
        if (fate == Fate.ANSWERED && reason != Reason.NONE) {
            throw new IllegalArgumentException("An accepted answer carries no reason");
        }
    }

    /**
     * @param reason the reason the attempt ended
     * @return an attempt that ended like a worker without a valid result
     */
    public static EndpointOutcome ended(Reason reason) {
        return ended(reason, reason.wireName());
    }

    /**
     * @param reason the reason the attempt ended
     * @param detail what happened
     * @return an attempt that ended like a worker without a valid result
     */
    public static EndpointOutcome ended(Reason reason, String detail) {
        return new EndpointOutcome(Fate.ENDED, reason, detail, "", HarnessUsage.none());
    }

    /**
     * @param reason the reason the request was refused
     * @param detail what happened
     * @return a request refused before it was sent
     */
    public static EndpointOutcome refused(Reason reason, String detail) {
        return new EndpointOutcome(Fate.REFUSED, reason, detail, "", HarnessUsage.none());
    }

    /**
     * @param content the validated answer
     * @param usage   the measured usage
     * @return an accepted answer
     */
    public static EndpointOutcome answered(String content, HarnessUsage usage) {
        return new EndpointOutcome(Fate.ANSWERED, Reason.NONE, "answered", content, usage);
    }

    /** How a task ended. The set is closed. */
    public enum Fate {
        /** The endpoint answered with a valid document. */
        ANSWERED("answered"),
        /** The attempt ended like a worker without a valid result. */
        ENDED("ended"),
        /** The request was refused before it was sent. */
        REFUSED("refused");

        private final String wireName;

        Fate(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the fate in a record */
        public String wireName() {
            return wireName;
        }
    }

    /** Why a task ended as it did. The set is closed. */
    public enum Reason {
        /** The answer was accepted. */
        NONE("none"),
        /** The answer is not one valid JSON document of the result schema. */
        INVALID_ANSWER("invalid_answer"),
        /** The answer is a refusal text. */
        REFUSAL_TEXT("refusal_text"),
        /** The endpoint answered with an error status, or did not answer. */
        HTTP_ERROR("http_error"),
        /** The endpoint did not answer inside the profile's timeout. */
        TIMEOUT("timeout"),
        /** Another program listens on the profile's port. */
        HARNESS_NOT_ENROLLED("harness_not_enrolled"),
        /** The service answered with another model id than the requested one, so a silent re-route ended the attempt. */
        MODEL_ALIAS("model_alias"),
        /** The runtime refused the request before it was sent. */
        REQUEST_REFUSED("request_refused"),
        /** The entity does not fit the endpoint's context budget, so the task closes before a request is sent. */
        INPUT_TOO_LARGE("input_too_large"),
        /** The answer was a redirect to another origin or to http, which ends the attempt. */
        REDIRECT_REFUSED("redirect_refused");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Reads one answer of the protocol {@code openai_chat} and accepts it.
     *
     * @param response     the answer of the request
     * @param resultSchema the role's result schema; {@code null} when the role declares none
     * @return the outcome of the task
     */
    public static EndpointOutcome accept(EndpointClient.Response response, JsonNode resultSchema) {
        Objects.requireNonNull(response, "response");
        if (response.failure() == EndpointClient.Failure.TIMEOUT) {
            return ended(Reason.TIMEOUT, response.detail());
        }
        if (!response.successful()) {
            return ended(Reason.HTTP_ERROR, response.detail());
        }
        if (streaming(response.body())) {
            return ended(Reason.INVALID_ANSWER, "the answer is an event stream, not one JSON document");
        }
        JsonNode answer;
        try {
            answer = JsonDocument.parse(response.body().getBytes(StandardCharsets.UTF_8), "answer");
        } catch (ProfileRefusal | IllegalArgumentException _) {
            return ended(Reason.INVALID_ANSWER, "the answer is no JSON document");
        }
        var usage = usage(answer);
        var refusal = choice(answer).flatMap(choiceText("refusal")).filter(text -> !text.isEmpty());
        if (refusal.isPresent()) {
            return new EndpointOutcome(Fate.ENDED, Reason.REFUSAL_TEXT, "the endpoint returned a refusal text", "",
                    usage);
        }
        var content = choice(answer).flatMap(choiceText("content"));
        if (content.isEmpty() || content.get().isEmpty()) {
            return new EndpointOutcome(Fate.ENDED, Reason.INVALID_ANSWER, "the answer carries no content", "", usage);
        }
        JsonNode document;
        try {
            document = JsonDocument.parse(content.get().getBytes(StandardCharsets.UTF_8), "answer");
        } catch (ProfileRefusal | IllegalArgumentException _) {
            return new EndpointOutcome(Fate.ENDED, Reason.INVALID_ANSWER,
                    "the content is not one JSON document", "", usage);
        }
        if (resultSchema != null) {
            var check = AnswerSchema.validate(resultSchema, document);
            if (!check.valid()) {
                return new EndpointOutcome(Fate.ENDED, Reason.INVALID_ANSWER,
                        "the answer does not validate against the result schema", "", usage);
            }
        }
        return answered(content.get(), usage);
    }

    private static boolean streaming(String body) {
        var trimmed = body == null ? "" : body.stripLeading();
        return trimmed.startsWith("data:") || trimmed.contains("\ndata:");
    }

    private static Optional<JsonNode> choice(JsonNode answer) {
        return answer.member("choices").filter(JsonNode::isArray)
                .flatMap(choices -> choices.elements().stream().findFirst());
    }

    private static Function<JsonNode, Optional<String>> choiceText(String member) {
        return choice -> choice.member("message").flatMap(message -> message.member(member))
                .flatMap(JsonNode::text);
    }

    private static HarnessUsage usage(JsonNode answer) {
        return HarnessUsage.fromEndpoint(figure(answer, "prompt_tokens"), figure(answer, "completion_tokens"));
    }

    private static OptionalLong figure(JsonNode answer, String member) {
        var node = answer.member("usage").flatMap(value -> value.member(member)).orElse(null);
        return node == null ? OptionalLong.empty() : node.integerValue();
    }
}
