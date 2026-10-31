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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;

/**
 * The two requests of one judgment task (Client Profiles § Judgment Endpoint, The Request and the Answer): the model of the
 * role, the facts of the vote entity as {@code state}, and one question of the type {@code choice} per question of the
 * entity, keyed by its {@code question_id}, with its options as {@code criteria} keyed by {@code branch_key}.
 * <p>
 * The first request lists the options sorted by {@code branch_key}, the second in the opposite order, so the two answers
 * of a task are independent of the order the service saw. Nothing else is sent: no recommendation, no answer of another
 * role, no tool, and no header that opts into a slower or less accurate mode. The entity is packed to the profile's
 * context budget, {@code context_tokens} less {@code answer_reserve_tokens}; a task that does not fit is refused before
 * it is sent.
 * <p>
 * The body is written with its members in the order this class builds them: {@link CanonicalJson} sorts an object's
 * members, which would erase the option order the protocol depends on, so only the escaping and the number spelling of
 * the canonical writer are used here.
 *
 * @since 0.1
 */
public final class SystemOneRequest {

    /** The path of the protocol's request, fixed by the protocol. */
    public static final String PATH = "/v1/systemone";

    /** The only question type the protocol serves. */
    public static final String CHOICE = "choice";

    /**
     * One option of a question (Client Profiles § Judgment Endpoint): the label the answer is chosen by, and what the
     * option means for the state.
     *
     * @param label        the short name of the option; never empty
     * @param description  what the option says of the state; {@code null} when the question gives none
     * @param consequences what follows from the option; {@code null} when the question gives none
     */
    public record Criterion(String label, @Nullable String description, @Nullable String consequences) {

        /** Refuses an empty label. */
        public Criterion {
            Objects.requireNonNull(label, "label");
            if (label.isEmpty()) {
                throw new IllegalArgumentException("A criterion names its label");
            }
        }
    }

    /**
     * One question of the entity, of the type {@code choice}.
     *
     * @param questionId   the id of the question of the entity
     * @param instructions the question text
     * @param criteria     the options by {@code branch_key}; empty is refused, because a question has options
     */
    public record Question(String questionId, String instructions, Map<String, Criterion> criteria) {

        /** Copies the options and refuses a question without an id, a text or an option. */
        public Question {
            Objects.requireNonNull(questionId, "questionId");
            Objects.requireNonNull(instructions, "instructions");
            criteria = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(criteria, "criteria")));
            if (questionId.isEmpty()) {
                throw new IllegalArgumentException("A question names its question_id");
            }
            if (criteria.isEmpty()) {
                throw new IllegalArgumentException("The question '%s' names no option".formatted(questionId));
            }
        }

        /** @return the branch keys of the question, sorted by {@code branch_key} */
        public List<String> ascendingKeys() {
            var keys = new ArrayList<>(criteria.keySet());
            keys.sort(Comparator.naturalOrder());
            return List.copyOf(keys);
        }

        /**
         * @param ascending whether the options are listed sorted by {@code branch_key} or in the opposite order
         * @return the branch keys in the order the request lists them
         */
        public List<String> keys(boolean ascending) {
            var keys = ascendingKeys();
            return ascending ? keys : keys.reversed();
        }
    }

    /**
     * The two bodies of one task, and the entity's estimated size.
     *
     * @param ascending  the body whose options are sorted by {@code branch_key}
     * @param descending the body whose options are listed in the opposite order
     * @param estimatedTokens the estimated size of the entity, at {@value EndpointRequest#CHARS_PER_TOKEN} characters per
     *                        token
     * @since 0.1
     */
    public record Composed(String ascending, String descending, int estimatedTokens) {

        /** Copies the bodies and refuses a missing one. */
        public Composed {
            Objects.requireNonNull(ascending, "ascending");
            Objects.requireNonNull(descending, "descending");
        }
    }

    /** Why a task cannot be composed. The set is closed. */
    public enum Refusal {
        /** The entity alone exceeds the harness's context budget. */
        ENTITY_EXCEEDS_BUDGET("input_too_large"),
        /** The task asks no question, or names one twice. */
        TASK_INVALID("task_invalid");

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
     * The composition of one task: the two bodies, or the refusal that ends it before it is sent.
     *
     * @param request the two bodies; {@code null} when {@code refusal} is set
     * @param refusal why no request was composed; {@code null} when one was
     * @param detail  what happened, for a record
     * @since 0.1
     */
    public record Result(@Nullable Composed request, @Nullable Refusal refusal, String detail) {

        /** Copies the values and refuses an outcome that is neither. */
        public Result {
            Objects.requireNonNull(detail, "detail");
            if ((request == null) == (refusal == null)) {
                throw new IllegalArgumentException("A composition is either composed or refused");
            }
        }

        static Result composed(Composed request) {
            return new Result(request, null, "composed");
        }

        static Result refused(Refusal refusal, String detail) {
            return new Result(null, refusal, detail);
        }

        /** @return whether the two bodies were composed */
        public boolean isComposed() {
            return request != null;
        }
    }

    private SystemOneRequest() {
    }

    /**
     * Composes the two requests of one task.
     *
     * @param profile   the judgment endpoint profile
     * @param model     the versioned model id of the role
     * @param state     the facts of the vote entity
     * @param questions the questions of the entity, in their order
     * @return the two bodies, or why they cannot be composed
     */
    public static Result compose(EndpointProfile profile, String model, JsonNode state, List<Question> questions) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(questions, "questions");
        if (questions.isEmpty()) {
            return Result.refused(Refusal.TASK_INVALID, "the task asks no question");
        }
        var questionIds = new LinkedHashSet<String>();
        for (var question : questions) {
            if (!questionIds.add(question.questionId())) {
                return Result.refused(Refusal.TASK_INVALID,
                        "the task names the question '%s' twice".formatted(question.questionId()));
            }
        }
        var ascending = body(model, state, questions, true);
        var tokens = EndpointRequest.estimate(ascending);
        var budget = profile.endpoint().contextBudgetTokens();
        if (tokens > budget) {
            return Result.refused(Refusal.ENTITY_EXCEEDS_BUDGET,
                    "the entity of %d tokens exceeds the context budget of %d on this endpoint"
                            .formatted(tokens, budget));
        }
        return Result.composed(new Composed(ascending, body(model, state, questions, false), tokens));
    }

    /**
     * @param model     the versioned model id of the role
     * @param state     the facts of the vote entity
     * @param questions the questions of the entity
     * @param ascending whether the options are listed sorted by {@code branch_key}
     * @return the JSON body of one of the task's two requests
     */
    public static String body(String model, JsonNode state, List<Question> questions, boolean ascending) {
        var questionsNode = new LinkedHashMap<String, JsonNode>();
        for (var question : questions) {
            var criteria = new LinkedHashMap<String, JsonNode>();
            for (var key : question.keys(ascending)) {
                criteria.put(key, criterion(question.criteria().get(key)));
            }
            var members = new LinkedHashMap<String, JsonNode>();
            members.put("type", JsonNode.string(CHOICE));
            members.put("instructions", JsonNode.string(question.instructions()));
            members.put("criteria", JsonNode.object(criteria));
            questionsNode.put(question.questionId(), JsonNode.object(members));
        }
        var body = new LinkedHashMap<String, JsonNode>();
        body.put("model", JsonNode.string(model));
        body.put("state", state);
        body.put("questions", JsonNode.object(questionsNode));
        var out = new StringBuilder();
        write(JsonNode.object(body), out);
        return out.toString();
    }

    /**
     * @param questions the questions of the task
     * @return the branch keys of every question by its {@code question_id}, as the answer is validated against them
     */
    public static Map<String, List<String>> options(List<Question> questions) {
        var options = new LinkedHashMap<String, List<String>>();
        questions.forEach(question -> options.put(question.questionId(), question.ascendingKeys()));
        return Collections.unmodifiableMap(options);
    }

    private static JsonNode criterion(Criterion criterion) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("label", JsonNode.string(criterion.label()));
        if (criterion.description() != null) {
            members.put("description", JsonNode.string(criterion.description()));
        }
        if (criterion.consequences() != null) {
            members.put("consequences", JsonNode.string(criterion.consequences()));
        }
        return JsonNode.object(members);
    }

    /**
     * Writes the value with an object's members in their own order, which the canonical writer would sort away; strings
     * and numbers are written by {@link CanonicalJson}, so the escaping is the canonical one.
     */
    private static void write(JsonNode value, StringBuilder out) {
        switch (value.kind()) {
            case OBJECT -> {
                out.append('{');
                var first = true;
                for (var member : value.members().entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append(CanonicalJson.write(JsonNode.string(member.getKey())));
                    out.append(':');
                    write(member.getValue(), out);
                }
                out.append('}');
            }
            case ARRAY -> {
                out.append('[');
                var first = true;
                for (var element : value.elements()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    write(element, out);
                }
                out.append(']');
            }
            default -> out.append(CanonicalJson.write(value));
        }
    }
}
