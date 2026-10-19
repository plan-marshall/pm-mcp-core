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
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.plan.HeaderJson.ArrayNode;
import de.planmarshall.workflow.plan.HeaderJson.Node;
import de.planmarshall.workflow.plan.HeaderJson.NullNode;
import de.planmarshall.workflow.plan.HeaderJson.ObjectNode;
import de.planmarshall.workflow.plan.HeaderJson.StringNode;
import de.planmarshall.workflow.verify.DeclarationBounds;

/**
 * The facts a composition reads (PM-IMPL-5, PM-EXT-7, PM-WF-25), passed in as a value: the composition reads no store.
 * The facts stay where they are owned; this value is the read of them that one composition rests on, and its digest is
 * the header's {@code plan_facts_digest}.
 * <p>
 * The composition rules address a fact by name:
 * <ul>
 * <li>{@value #LANE_FACT}, {@value #POSTURE_FACT}, {@value #CHANGE_TYPE_FACT}: the plan facts of the same names;</li>
 * <li>{@value #POST_RUN_FACT}: the post-run-only form a fallback or an empty harvest composes ({@code returned},
 * {@code handed_back} or {@code empty_harvest});</li>
 * <li>{@value #FOOTPRINT_FACT}: the declared footprint, a list of paths; absent when it could not be read;</li>
 * <li>{@code axes.<axis>} ({@link Axis#fact()}): the variant bound on the plan behaviour axis {@code landing},
 * {@code method} or {@code asking}, which is always known: the nearest level that sets the axis, else the shipped
 * plan default ({@link #bound(Axis)});</li>
 * <li>{@code axes.<axis>.<declaration>} ({@link Axis#declarationFact(String)}): a declaration of the bound variant,
 * {@code axes.method.order}, {@code axes.asking.wait_after} (a list), {@code axes.asking.outline_review} and
 * {@code axes.asking.self_merge}; known only where the declarations of the bound variant were passed in
 * ({@link #withDeclarations(String, Map)}), which the composer does from the variant's tree;</li>
 * <li>every other name is a key of the effective configuration, among them {@code derived.push_remote} and the
 * provider keys {@code integrations.<provider>}; a key the configuration does not set is absent.</li>
 * </ul>
 * An absent fact is unknown: a rule over it never holds except the one that asks whether it is unset.
 * <p>
 * The levels of an axis are the plan's own value (the {@code axes} member of the plan's {@code overrides.json}), the
 * project's and the machine's; the overrides document itself is a fact as well. Both are counted in the digest. The
 * declarations of the bound variant are not: they are the content of the variant's unit, which the header names by
 * its own digest.
 *
 * @param lane          the lane of the plan, if known
 * @param posture       the posture of the plan, if set
 * @param changeType    the change type of the plan, if known
 * @param postRun       the post-run-only form being composed, if any
 * @param footprint     the declared footprint, if it could be read
 * @param configuration the effective configuration values by key, derived facts included
 * @param steps         the step ids known to the plan
 * @param tasks         the task ids known to the plan
 * @param axes          per plan behaviour axis the variant each level sets, {@link Level#SHIPPED} never among them
 * @param overrides     the text of the plan's {@code overrides.json}, if the plan has one
 * @param declarations  per axis variant whose tree was read its declarations by name
 * @since 0.1
 */
public record PlanFacts(Optional<PlanInstanceHeader.Lane> lane, Optional<PlanInstanceHeader.Posture> posture,
Optional<String> changeType, Optional<String> postRun, Optional<List<String>> footprint,
SortedMap<String, String> configuration, List<String> steps, List<String> tasks, Map<Axis, Map<Level, String>> axes,
Optional<String> overrides, Map<String, Map<String, CallValue>> declarations) {

    /** The fact name of the lane. */
    public static final String LANE_FACT = "lane";
    /** The fact name of the posture. */
    public static final String POSTURE_FACT = "posture";
    /** The fact name of the change type. */
    public static final String CHANGE_TYPE_FACT = "change_type";
    /** The fact name of the post-run-only form. */
    public static final String POST_RUN_FACT = "post_run";
    /** The fact name of the declared footprint. */
    public static final String FOOTPRINT_FACT = "footprint";

    /** The prefix of every fact of a plan behaviour axis. */
    private static final String AXES_PREFIX = "axes.";

    /** The names of the plan facts; every other fact name is an axis fact or a configuration key. */
    static final Set<String> PLAN_FACT_NAMES = Set.of(LANE_FACT, POSTURE_FACT, CHANGE_TYPE_FACT, POST_RUN_FACT,
            FOOTPRINT_FACT);

    /** The names of the axis facts: the bound variant of each axis and the declarations of its variants. */
    static final Set<String> AXIS_FACT_NAMES = axisFactNames();

    /**
     * Refuses a missing component, an axis value that is no variant of its axis or that names the shipped level, and
     * declarations of a unit that is no axis variant, and copies the collections into unmodifiable ones; the
     * configuration is ordered by key.
     *
     * @param lane          the lane of the plan, if known
     * @param posture       the posture of the plan, if set
     * @param changeType    the change type of the plan, if known
     * @param postRun       the post-run-only form being composed, if any
     * @param footprint     the declared footprint, if it could be read
     * @param configuration the effective configuration values by key
     * @param steps         the step ids known to the plan
     * @param tasks         the task ids known to the plan
     * @param axes          per axis the variant each level sets
     * @param overrides     the text of the plan's {@code overrides.json}, if any
     * @param declarations  per axis variant its declarations by name
     * @throws IllegalArgumentException if a level sets a value that is no variant of its axis, a level is
     *                                  {@link Level#SHIPPED}, or declarations name a unit that is no axis variant
     */
    public PlanFacts {
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(posture, POSTURE_FACT);
        Objects.requireNonNull(changeType, "changeType");
        Objects.requireNonNull(postRun, "postRun");
        footprint = Objects.requireNonNull(footprint, FOOTPRINT_FACT).map(List::copyOf);
        configuration = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(configuration,
                "configuration")));
        steps = List.copyOf(steps);
        tasks = List.copyOf(tasks);
        axes = copyAxes(Objects.requireNonNull(axes, "axes"));
        Objects.requireNonNull(overrides, "overrides");
        declarations = copyDeclarations(Objects.requireNonNull(declarations, "declarations"));
    }

    private static Map<Axis, Map<Level, String>> copyAxes(Map<Axis, Map<Level, String>> axes) {
        var copy = new EnumMap<Axis, Map<Level, String>>(Axis.class);
        axes.forEach((axis, levels) -> {
            var values = new EnumMap<Level, String>(Level.class);
            levels.forEach((level, variant) -> {
                if (level == Level.SHIPPED) {
                    throw new IllegalArgumentException("the axis '%s' takes no value of the level '%s'"
                            .formatted(axis.wireName(), level.wireName()));
                }
                if (!axis.variants().contains(variant)) {
                    throw new IllegalArgumentException("'%s' is no variant of the axis '%s', one of %s"
                            .formatted(variant, axis.wireName(), axis.variants()));
                }
                values.put(level, variant);
            });
            if (!values.isEmpty()) {
                copy.put(axis, Collections.unmodifiableMap(values));
            }
        });
        return Collections.unmodifiableMap(copy);
    }

    private static Map<String, Map<String, CallValue>> copyDeclarations(
            Map<String, Map<String, CallValue>> declarations) {
        var copy = new TreeMap<String, Map<String, CallValue>>();
        declarations.forEach((variant, declared) -> {
            if (Axis.ofVariant(variant).isEmpty()) {
                throw new IllegalArgumentException("'%s' is no variant of a plan behaviour axis".formatted(variant));
            }
            copy.put(variant, Collections.unmodifiableSortedMap(new TreeMap<>(declared)));
        });
        return Collections.unmodifiableSortedMap(copy);
    }

    private static Set<String> axisFactNames() {
        var names = new LinkedHashSet<String>();
        for (var axis : Axis.values()) {
            names.add(axis.fact());
            axis.declarationNames().forEach(name -> names.add(axis.declarationFact(name)));
        }
        return Collections.unmodifiableSet(names);
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
            case POST_RUN_FACT -> postRun;
            case FOOTPRINT_FACT -> Optional.empty();
            default -> fact.startsWith(AXES_PREFIX) ? axisValue(fact).flatMap(PlanFacts::textOf)
                    : Optional.ofNullable(configuration.get(fact));
        };
    }

    /**
     * The value of a fact that holds a list.
     *
     * @param fact the fact name
     * @return the list, or empty when the fact is unknown or holds one text
     */
    public Optional<List<String>> list(String fact) {
        if (FOOTPRINT_FACT.equals(fact)) {
            return footprint;
        }
        return axisValue(fact).flatMap(value -> value instanceof CallValue.EnumList(var values)
                ? Optional.of(values) : Optional.empty());
    }

    /**
     * The variant bound on an axis: the plan's own value, else the project's, else the machine's, else the shipped
     * plan default of the axis.
     *
     * @param axis the axis
     * @return the bound variant with the level that set it
     */
    public BoundVariant bound(Axis axis) {
        var levels = axes.getOrDefault(axis, Map.of());
        for (var level : Level.values()) {
            if (levels.containsKey(level)) {
                return new BoundVariant(axis, levels.get(level), level);
            }
        }
        return new BoundVariant(axis, axis.shipped(), Level.SHIPPED);
    }

    /**
     * @param variant  an axis variant
     * @param declared its declarations by name, as its tree declares them
     * @return these facts with the declarations of the variant replaced; every other fact unchanged
     * @throws IllegalArgumentException if the unit is no axis variant
     */
    public PlanFacts withDeclarations(String variant, Map<String, CallValue> declared) {
        var replaced = new TreeMap<>(declarations);
        replaced.put(variant, declared);
        return new PlanFacts(lane, posture, changeType, postRun, footprint, configuration, steps, tasks, axes,
                overrides, replaced);
    }

    /**
     * The source fact of a prune whose rule read a fact: the fact itself, except a declaration of a bound variant,
     * whose source is the bound variant of its axis ({@code axes.asking.self_merge} is sourced {@code axes.asking}).
     *
     * @param fact the fact a rule read
     * @return the source fact of the prunes of the rule
     */
    static String sourceFact(String fact) {
        for (var axis : Axis.values()) {
            if (fact.startsWith(axis.fact() + ".")) {
                return axis.fact();
            }
        }
        return fact;
    }

    /**
     * @return the SHA-256 over the canonical JSON of the facts, 64 lowercase hexadecimal characters; the declarations
     *         of the axis variants are not counted, the header names their units by digest
     */
    public String digest() {
        var members = new LinkedHashMap<String, Node>();
        members.put(LANE_FACT, optional(text(LANE_FACT)));
        members.put(POSTURE_FACT, optional(text(POSTURE_FACT)));
        members.put(CHANGE_TYPE_FACT, optional(changeType));
        members.put(POST_RUN_FACT, optional(postRun));
        members.put(FOOTPRINT_FACT, footprint.<Node>map(PlanFacts::strings).orElse(NullNode.INSTANCE));
        var config = new LinkedHashMap<String, Node>();
        configuration.forEach((key, value) -> config.put(key, new StringNode(value)));
        members.put("configuration", new ObjectNode(config));
        members.put("steps", strings(steps));
        members.put("tasks", strings(tasks));
        var axisLevels = new LinkedHashMap<String, Node>();
        axes.forEach((axis, levels) -> {
            var values = new LinkedHashMap<String, Node>();
            levels.forEach((level, variant) -> values.put(level.wireName(), new StringNode(variant)));
            axisLevels.put(axis.wireName(), new ObjectNode(values));
        });
        members.put("axes", new ObjectNode(axisLevels));
        members.put("overrides", optional(overrides.map(PlanFacts::sha256)));
        return sha256(HeaderJson.canonical(new ObjectNode(members)));
    }

    /**
     * @return the value of an axis fact: the bound variant for {@code axes.<axis>}, the declaration of the bound
     *         variant for {@code axes.<axis>.<declaration>}; empty for every other name and an unknown declaration
     */
    private Optional<CallValue> axisValue(String fact) {
        for (var axis : Axis.values()) {
            if (axis.fact().equals(fact)) {
                return Optional.of(new CallValue.EnumValue(bound(axis).variant()));
            }
            var prefix = axis.fact() + ".";
            if (fact.startsWith(prefix)) {
                return Optional.ofNullable(declarations.getOrDefault(bound(axis).variant(), Map.of())
                        .get(fact.substring(prefix.length())));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> textOf(CallValue value) {
        return switch (value) {
            case CallValue.EnumValue(var text) -> Optional.of(text);
            case CallValue.IntValue(var number) -> Optional.of(Integer.toString(number));
            case CallValue.EnumList _ -> Optional.empty();
        };
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
        private String postRun;
        private List<String> footprint;
        private final Map<String, String> configuration = new TreeMap<>();
        private List<String> steps = List.of();
        private List<String> tasks = List.of();
        private final Map<Axis, Map<Level, String>> axes = new EnumMap<>(Axis.class);
        private String overrides;
        private final Map<String, Map<String, CallValue>> declarations = new TreeMap<>();

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
         * @param axis    a plan behaviour axis
         * @param level   the level that sets it, {@link Level#PLAN}, {@link Level#PROJECT} or {@link Level#MACHINE}
         * @param variant the variant the level sets
         * @return this builder
         */
        public Builder axis(Axis axis, Level level, String variant) {
            axes.computeIfAbsent(axis, key -> new EnumMap<>(Level.class)).put(level, variant);
            return this;
        }

        /**
         * @param value the text of the plan's {@code overrides.json}
         * @return this builder
         */
        public Builder overrides(String value) {
            overrides = value;
            return this;
        }

        /**
         * @param variant  an axis variant
         * @param declared its declarations by name
         * @return this builder
         */
        public Builder declarations(String variant, Map<String, CallValue> declared) {
            declarations.put(variant, declared);
            return this;
        }

        /**
         * @return the facts
         * @throws IllegalArgumentException if an axis value or a declared variant is refused
         */
        public PlanFacts build() {
            return new PlanFacts(Optional.ofNullable(lane), Optional.ofNullable(posture),
                    Optional.ofNullable(changeType), Optional.ofNullable(postRun),
                    Optional.ofNullable(footprint), new TreeMap<>(configuration), steps, tasks, axes,
                    Optional.ofNullable(overrides), declarations);
        }
    }

    /**
     * A plan behaviour axis (PM-WF-25): a call site of the plan's workflow whose variants are sub-workflows named with
     * the axis as prefix, a closed list: a variant is changed, never added.
     *
     * @since 0.1
     */
    public enum Axis {
        /** How the plan lands: through a pull request, onto the base branch, or into the main checkout's files. */
        LANDING("landing", List.of("landing.pull-request", "landing.main", "landing.files"), "landing.pull-request"),
        /** The order of each deliverable's implementing and testing tasks. */
        METHOD("method", List.of(DeclarationBounds.METHOD_TEST_FIRST, DeclarationBounds.METHOD_TESTS_AFTER),
                DeclarationBounds.METHOD_TESTS_AFTER),
        /** Which phase transitions wait for the operator, the outline review and whether the plan merges itself. */
        ASKING("asking", List.of(DeclarationBounds.ASK_EVERY_PHASE, DeclarationBounds.ASK_KEY_POINTS,
                DeclarationBounds.ASK_QUESTIONS_ONLY), DeclarationBounds.ASK_QUESTIONS_ONLY);

        private final String wireName;
        private final List<String> variants;
        private final String shipped;

        Axis(String wireName, List<String> variants, String shipped) {
            this.wireName = wireName;
            this.variants = variants;
            this.shipped = shipped;
        }

        /**
         * @return the name of the axis, the key of {@code axes} in {@code overrides.json}
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @return the closed list of the axis's variants
         */
        public List<String> variants() {
            return variants;
        }

        /**
         * @return the variant of the shipped {@code plan_defaults}, bound where no level sets the axis
         */
        public String shipped() {
            return shipped;
        }

        /**
         * @return the name of the fact that holds the bound variant, {@code axes.<axis>}
         */
        public String fact() {
            return AXES_PREFIX + wireName;
        }

        /**
         * @param declaration the name of a declaration of the axis's variants
         * @return the name of the fact that holds it for the bound variant, {@code axes.<axis>.<declaration>}
         */
        public String declarationFact(String declaration) {
            return fact() + "." + declaration;
        }

        /**
         * @return the names the compiled declaration schemas of the axis's variants declare, in schema order
         */
        public Set<String> declarationNames() {
            var names = new LinkedHashSet<String>();
            variants.forEach(variant -> DeclarationBounds.schema(variant).ifPresent(schema -> schema
                    .forEach(declaration -> names.add(declaration.name()))));
            return Collections.unmodifiableSet(names);
        }

        /**
         * @param unitId the id of a unit
         * @return the axis whose variant the unit is, or empty if it is none
         */
        public static Optional<Axis> ofVariant(String unitId) {
            return Arrays.stream(values()).filter(axis -> axis.variants.contains(unitId)).findFirst();
        }
    }

    /**
     * A level that sets the variant of an axis, from the nearest; {@link #SHIPPED} stands for the shipped
     * {@code plan_defaults} where no level sets it.
     *
     * @since 0.1
     */
    public enum Level {
        /** The plan's own value, the {@code axes} member of its {@code overrides.json}. */
        PLAN("plan"),
        /** The project's value. */
        PROJECT("project"),
        /** The machine's default, {@code plan_defaults} of {@code machine-config.json}. */
        MACHINE("machine"),
        /** The shipped {@code plan_defaults}. */
        SHIPPED("shipped");

        private final String wireName;

        Level(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the name of the level
         */
        public String wireName() {
            return wireName;
        }

        /**
         * @param axis an axis
         * @return the source fact of a variant the level binds on the axis, {@code <level>.axes.<axis>}, for example
         *         {@code plan.axes.asking}
         */
        public String sourceFact(Axis axis) {
            return wireName + "." + axis.fact();
        }
    }

    /**
     * The variant bound on an axis and the level that set it.
     *
     * @param axis    the axis
     * @param variant the bound variant
     * @param level   the level that set it
     * @since 0.1
     */
    public record BoundVariant(Axis axis, String variant, Level level) {

        /**
         * Refuses a missing component.
         *
         * @param axis    the axis
         * @param variant the bound variant
         * @param level   the level that set it
         */
        public BoundVariant {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(variant, "variant");
            Objects.requireNonNull(level, "level");
        }

        /**
         * @return the source fact of the variants the binding prunes, {@code <level>.axes.<axis>}
         */
        public String sourceFact() {
            return level.sourceFact(axis);
        }
    }
}
