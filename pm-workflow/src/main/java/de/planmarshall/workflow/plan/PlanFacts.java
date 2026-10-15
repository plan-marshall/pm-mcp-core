/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.planmarshall.workflow.plan.HeaderJson.ArrayNode;
import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;

/**
 * The facts a composition reads (PM-IMPL-5, PM-EXT-7), passed in as a value: the composition reads no store. The facts
 * stay where they are owned; this value is the read of them that one composition rests on, and its digest is the
 * header's {@code plan_facts_digest}.
 * <p>
 * The composition rules address a fact by name:
 * <ul>
 * <li>{@value #LANE_FACT}, {@value #POSTURE_FACT}, {@value #CHANGE_TYPE_FACT}, {@value #RECIPE_ID_FACT}: the plan
 * facts of the same names;</li>
 * <li>{@value #POST_RUN_FACT}: the post-run-only form a fallback or an empty harvest composes ({@code returned},
 * {@code handed_back} or {@code empty_harvest});</li>
 * <li>{@value #FOOTPRINT_FACT}: the declared footprint, a list of paths; absent when it could not be read;</li>
 * <li>every other name is a key of the effective configuration, among them {@code merge.auto_merge},
 * {@code derived.push_remote} and the provider keys {@code integrations.<provider>}; a key the configuration does
 * not set is absent.</li>
 * </ul>
 * An absent fact is unknown: a rule over it never holds except the one that asks whether it is unset.
 *
 * @param lane          the lane of the plan, if known
 * @param posture       the posture of the plan, if set
 * @param changeType    the change type of the plan, if known
 * @param recipeId      the recipe the plan runs, if any
 * @param postRun       the post-run-only form being composed, if any
 * @param footprint     the declared footprint, if it could be read
 * @param configuration the effective configuration values by key, derived facts included
 * @param steps         the step ids known to the plan
 * @param tasks         the task ids known to the plan
 * @since 0.1
 */
public record PlanFacts(Optional<PlanInstanceHeader.Lane> lane, Optional<PlanInstanceHeader.Posture> posture,
Optional<String> changeType, Optional<String> recipeId, Optional<String> postRun, Optional<List<String>> footprint,
SortedMap<String, String> configuration, List<String> steps, List<String> tasks) {

    /** The fact name of the lane. */
    public static final String LANE_FACT = "lane";
    /** The fact name of the posture. */
    public static final String POSTURE_FACT = "posture";
    /** The fact name of the change type. */
    public static final String CHANGE_TYPE_FACT = "change_type";
    /** The fact name of the recipe id. */
    public static final String RECIPE_ID_FACT = "recipe_id";
    /** The fact name of the post-run-only form. */
    public static final String POST_RUN_FACT = "post_run";
    /** The fact name of the declared footprint. */
    public static final String FOOTPRINT_FACT = "footprint";

    /** The names of the plan facts; every other fact name is a configuration key. */
    static final Set<String> PLAN_FACT_NAMES = Set.of(LANE_FACT, POSTURE_FACT, CHANGE_TYPE_FACT, RECIPE_ID_FACT,
            POST_RUN_FACT, FOOTPRINT_FACT);

    /**
     * Refuses a missing component and copies the collections into unmodifiable ones; the configuration is ordered by
     * key.
     *
     * @param lane          the lane of the plan, if known
     * @param posture       the posture of the plan, if set
     * @param changeType    the change type of the plan, if known
     * @param recipeId      the recipe the plan runs, if any
     * @param postRun       the post-run-only form being composed, if any
     * @param footprint     the declared footprint, if it could be read
     * @param configuration the effective configuration values by key
     * @param steps         the step ids known to the plan
     * @param tasks         the task ids known to the plan
     */
    public PlanFacts {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(posture, POSTURE_FACT);
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(recipeId, "recipeId");
        Objects.requireNonNull(postRun, "postRun");
        footprint = Objects.requireNonNull(footprint, FOOTPRINT_FACT).map(List::copyOf);
        configuration = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(configuration,
                "configuration")));
        steps = List.copyOf(steps);
        tasks = List.copyOf(tasks);
    }

    /**
     * @return a builder that starts with every fact unknown and empty collections
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * The value of a fact that holds one text.
     *
     * @param fact the fact name
     * @return the value, or empty when the fact is unknown or holds a list
     */
    public Optional<String> text(String fact) {
        return switch (fact) {
            case LANE_FACT -> lane.map(PlanInstanceHeader.Lane::wireName);
            case POSTURE_FACT -> posture.map(PlanInstanceHeader.Posture::wireName);
            case CHANGE_TYPE_FACT -> changeType;
            case RECIPE_ID_FACT -> recipeId;
            case POST_RUN_FACT -> postRun;
            case FOOTPRINT_FACT -> Optional.empty();
            default -> Optional.ofNullable(configuration.get(fact));
        };
    }

    /**
     * The value of a fact that holds a list.
     *
     * @param fact the fact name
     * @return the list, or empty when the fact is unknown or holds one text
     */
    public Optional<List<String>> list(String fact) {
        return FOOTPRINT_FACT.equals(fact) ? footprint : Optional.empty();
    }

    /**
     * @return the SHA-256 over the canonical JSON of the facts, 64 lowercase hexadecimal characters
     */
    public String digest() {
        var members = new LinkedHashMap<String, Node>();
        members.put(LANE_FACT, optional(text(LANE_FACT)));
        members.put(POSTURE_FACT, optional(text(POSTURE_FACT)));
        members.put(CHANGE_TYPE_FACT, optional(changeType));
        members.put(RECIPE_ID_FACT, optional(recipeId));
        members.put(POST_RUN_FACT, optional(postRun));
        members.put(FOOTPRINT_FACT, footprint.<Node>map(PlanFacts::strings).orElse(NullNode.INSTANCE));
        var config = new LinkedHashMap<String, Node>();
        configuration.forEach((key, value) -> config.put(key, new StringNode(value)));
        members.put("configuration", new ObjectNode(config));
        members.put("steps", strings(steps));
        members.put("tasks", strings(tasks));
        return sha256(HeaderJson.canonical(new ObjectNode(members)));
    }

    /**
     * @param text a text
     * @return the SHA-256 of its UTF-8 bytes, 64 lowercase hexadecimal characters
     */
    static String sha256(String text) {
        return sha256(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @param bytes the bytes
     * @return their SHA-256, 64 lowercase hexadecimal characters
     */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }

    private static Node optional(Optional<String> value) {
        return value.<Node>map(StringNode::new).orElse(NullNode.INSTANCE);
    }

    private static ArrayNode strings(List<String> values) {
        return new ArrayNode(values.stream().map(value -> (Node) new StringNode(value)).toList());
    }

    /**
     * Builds {@link PlanFacts}; every fact not set stays unknown.
     *
     * @since 0.1
     */
    public static final class Builder {

        private PlanInstanceHeader.Lane lane;
        private PlanInstanceHeader.Posture posture;
        private String changeType;
        private String recipeId;
        private String postRun;
        private List<String> footprint;
        private final Map<String, String> configuration = new TreeMap<>();
        private List<String> steps = List.of();
        private List<String> tasks = List.of();

        private Builder() {
        }

        /**
         * @param value the lane
         * @return this builder
         */
        public Builder lane(PlanInstanceHeader.Lane value) {
            lane = value;
            return this;
        }

        /**
         * @param value the posture
         * @return this builder
         */
        public Builder posture(PlanInstanceHeader.Posture value) {
            posture = value;
            return this;
        }

        /**
         * @param value the change type
         * @return this builder
         */
        public Builder changeType(String value) {
            changeType = value;
            return this;
        }

        /**
         * @param value the recipe id
         * @return this builder
         */
        public Builder recipeId(String value) {
            recipeId = value;
            return this;
        }

        /**
         * @param value the post-run-only form, {@code returned}, {@code handed_back} or {@code empty_harvest}
         * @return this builder
         */
        public Builder postRun(String value) {
            postRun = value;
            return this;
        }

        /**
         * @param value the declared footprint
         * @return this builder
         */
        public Builder footprint(List<String> value) {
            footprint = value;
            return this;
        }

        /**
         * @param key   a key of the effective configuration
         * @param value its value
         * @return this builder
         */
        public Builder configuration(String key, String value) {
            configuration.put(key, value);
            return this;
        }

        /**
         * @param value the step ids known to the plan
         * @return this builder
         */
        public Builder steps(List<String> value) {
            steps = value;
            return this;
        }

        /**
         * @param value the task ids known to the plan
         * @return this builder
         */
        public Builder tasks(List<String> value) {
            tasks = value;
            return this;
        }

        /**
         * @return the facts
         */
        public PlanFacts build() {
            return new PlanFacts(Optional.ofNullable(lane), Optional.ofNullable(posture),
                    Optional.ofNullable(changeType), Optional.ofNullable(recipeId), Optional.ofNullable(postRun),
                    Optional.ofNullable(footprint), new TreeMap<>(configuration), steps, tasks);
        }
    }
}
