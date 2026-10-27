/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;

/**
 * The profile values the stages {@code isolation}, {@code egress} and {@code usage} of the profile verification settle,
 * and the loader's check of what a record then claims (PM-CLIENT-5, Profile Verification).
 * <p>
 * The stages are the verification's; this type holds their results as the values a profile is loaded with. The
 * {@code isolation} stage settles {@code mcp_config.exclusive} and the capabilities {@code project_settings_control}
 * and {@code extension_isolation} (no decoy tool, no canary file, no request to the decoy endpoint or proxy, the canary
 * variable absent, and the project instruction and skill loaded); the {@code egress} stage settles
 * {@code headless.endpoints}; and the {@code usage} stage records each figure {@code reported},
 * {@code not_applicable} or {@code missing}, compares the figures parsed for the run with the harness's own record
 * field by field, and fails for a declared pointer that does not resolve or a figure that differs.
 *
 * @since 0.1
 */
public final class ProfileStages {

    /** The figure names of {@code headless.usage}, spelled once. */
    private static final String INPUT_TOKENS = "input_tokens";
    private static final String OUTPUT_TOKENS = "output_tokens";
    private static final String CACHE_READ = "cache_read";
    private static final String CACHE_CREATION = "cache_creation";
    private static final String REASONING = "reasoning";
    private static final String COST_USD = "cost_usd";
    private static final String TOOL_USES = "tool_uses";

    /** The declaration of a figure that does not exist for a harness by design, spelled once. */
    private static final class Wire {
        private static final String NOT_APPLICABLE = "not_applicable";
    }

    /** The figures of {@code headless.usage}, in schema order. */
    public static final List<String> USAGE_FIGURES = List.of(INPUT_TOKENS, OUTPUT_TOKENS, CACHE_READ, CACHE_CREATION,
            REASONING, COST_USD, TOOL_USES);

    /** The figures the enrolment's usage report carries, in schema order; the cost is recorded data and never shown. */
    public static final List<String> REPORT_FIGURES = List.of(INPUT_TOKENS, OUTPUT_TOKENS, CACHE_READ, CACHE_CREATION,
            REASONING, TOOL_USES);

    /** The three pointers whose first-turn sum is the start context (Client Profiles § Tokens, Not Money). */
    private static final List<String> START_CONTEXT_FIGURES = List.of(INPUT_TOKENS, CACHE_READ, CACHE_CREATION);

    /** The figures a profile never declares not applicable (Client Profiles § Tokens, Not Money). */
    private static final List<String> NEVER_NOT_APPLICABLE = List.of(INPUT_TOKENS, OUTPUT_TOKENS, TOOL_USES);

    /** The suffix of a profile document, as a refusal names it. */
    private static final String PROFILE_SUFFIX = ".json";

    private ProfileStages() {
    }

    /**
     * The result of the stage {@code isolation}.
     *
     * @param exclusive              {@code mcp_config.exclusive} of the profile
     * @param projectSettingsControl whether the measured launch kept the project settings under control
     * @param extensionIsolation     whether the measured launch showed no decoy tool, no canary file, no request to the
     *                               decoy endpoint or proxy, and no canary variable, and loaded the project instruction
     *                               and skill
     * @since 0.1
     */
    public record IsolationResult(boolean exclusive, boolean projectSettingsControl, boolean extensionIsolation) {
    }

    /**
     * The result of the stage {@code egress}.
     *
     * @param endpoints the {@code headless.endpoints} the worker's egress proxy admits
     * @since 0.1
     */
    public record EgressResult(List<String> endpoints) {

        /** Copies the endpoints. */
        public EgressResult {
            endpoints = List.copyOf(endpoints);
        }
    }

    /** The recording of one usage figure by the stage {@code usage}. The set is closed. */
    public enum UsageStatus {
        /** The declared pointer resolved on the output and the figure was parsed. */
        REPORTED("reported"),
        /** The figure does not exist for this harness by design. */
        NOT_APPLICABLE(Wire.NOT_APPLICABLE),
        /** The harness does not report the figure. */
        MISSING("missing");

        private final String wireName;

        UsageStatus(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the status in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * What a worker costs before it does anything (Client Profiles § Tokens, Not Money, Start Context): the input of
     * the first model turn of one class and model, input plus cache read plus cache creation.
     *
     * @param workerClass the class of the worker, {@code writer}, {@code read_only} or {@code zero_tool}
     * @param model       the model the stage ran with
     * @param tokens      the first-turn tokens; empty when the first turn does not report the input figures
     * @since 0.1
     */
    public record StartContext(String workerClass, String model, OptionalLong tokens) {

        /** The classes of the closed list. */
        public static final Set<String> CLASSES = Set.of("writer", "read_only", "zero_tool");

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the class is not one of the closed list or the model is blank
         */
        public StartContext {
            Objects.requireNonNull(workerClass, "workerClass");
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(tokens, "tokens");
            if (!CLASSES.contains(workerClass)) {
                throw new IllegalArgumentException("Not a worker class: " + workerClass);
            }
            if (model.isBlank()) {
                throw new IllegalArgumentException("'model' must not be blank");
            }
        }
    }

    /**
     * The first turn of one class and model the {@code launch} stage ran, as the usage stage measures the start context
     * on it.
     *
     * @param workerClass the class of the worker
     * @param model       the model the stage ran with
     * @param firstTurn   the first model turn of that worker's output
     * @since 0.1
     */
    public record StartContextInput(String workerClass, String model, JsonNode firstTurn) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the class is not one of the closed list or the model is blank
         */
        public StartContextInput {
            Objects.requireNonNull(firstTurn, "firstTurn");
            new StartContext(workerClass, model, OptionalLong.empty());
        }
    }

    /** The coverage of a harness's usage report. The set is closed. */
    public enum UsageCoverage {
        /** Every report figure is reported or not applicable. */
        COMPLETE("complete"),
        /** At least one report figure is missing and at least one is reported or not applicable. */
        PARTIAL("partial"),
        /** No report figure is reported or not applicable. */
        NOT_REPORTED("not_reported");

        private final String wireName;

        UsageCoverage(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the coverage in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The result of the stage {@code usage}.
     *
     * @param figures      the status of each figure, in schema order
     * @param parsed       the parsed figure by name; empty for a figure that is not reported
     * @param startContext the start context of every class and model the {@code launch} stage ran
     * @since 0.1
     */
    public record UsageResult(Map<String, UsageStatus> figures, Map<String, BigDecimal> parsed,
    List<StartContext> startContext) {

        /** Copies both maps and the start context. */
        public UsageResult {
            figures = Collections.unmodifiableMap(new LinkedHashMap<>(figures));
            parsed = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
            startContext = List.copyOf(startContext);
        }

        /**
         * @param figures the status of each figure
         * @param parsed  the parsed figures
         */
        public UsageResult(Map<String, UsageStatus> figures, Map<String, BigDecimal> parsed) {
            this(figures, parsed, List.of());
        }

        /**
         * @return the coverage of the harness's usage report: complete, partial with the missing figures, or
         *         not_reported; the cost is recorded data and never part of the report
         */
        public UsageCoverage coverage() {
            var reported = REPORT_FIGURES.stream()
                    .filter(figure -> figures.getOrDefault(figure, UsageStatus.MISSING) != UsageStatus.MISSING)
                    .count();
            if (reported == REPORT_FIGURES.size()) {
                return UsageCoverage.COMPLETE;
            }
            return reported == 0 ? UsageCoverage.NOT_REPORTED : UsageCoverage.PARTIAL;
        }

        /** @return the report figures the harness does not report, in schema order */
        public List<String> missingFigures() {
            return REPORT_FIGURES.stream()
                    .filter(figure -> figures.getOrDefault(figure, UsageStatus.MISSING) == UsageStatus.MISSING).toList();
        }
    }

    /**
     * @param profile the effective profile
     * @param projectSettingsControl what the stage measured
     * @param extensionIsolation     what the stage measured
     * @return the record of the isolation result, with the profile's own exclusivity
     */
    public static IsolationResult isolation(LoadedProfile profile, boolean projectSettingsControl,
            boolean extensionIsolation) {
        var exclusive = profile.headless("/mcp_config/exclusive")
                .flatMap(JsonNode::booleanValue)
                .orElse(false);
        return new IsolationResult(exclusive, projectSettingsControl, extensionIsolation);
    }

    /**
     * @param profile the effective profile
     * @return the record of the egress result
     */
    public static EgressResult egress(LoadedProfile profile) {
        var endpoints = profile.headless("/endpoints").orElse(JsonNode.array(List.of()));
        return new EgressResult(endpoints.elements().stream().map(value -> value.text().orElse("")).toList());
    }

    /**
     * Reads each declared usage figure from one turn of the harness output, records it, and compares it field by field
     * with the harness's own record of the run.
     *
     * @param profile       the effective profile
     * @param turn          one line of the harness output, as the stage captured it
     * @param harnessRecord the harness's own figures for the same run; empty when the harness keeps no such record
     * @return the record of the usage stage
     * @throws ProfileRefusal if a declared pointer does not resolve on the output, or a parsed figure differs from the
     *                        harness's own record
     */
    public static UsageResult usage(LoadedProfile profile, JsonNode turn,
            Map<String, BigDecimal> harnessRecord) {
        return usage(profile, turn, harnessRecord, List.of());
    }

    /**
     * Reads each declared usage figure from one turn of the harness output, records it, compares it field by field with
     * the harness's own record of the run, and takes the start context of every class and model the {@code launch}
     * stage ran (Client Profiles § Tokens, Not Money, Start Context).
     *
     * @param profile       the effective profile
     * @param turn          one line of the harness output, as the stage captured it
     * @param harnessRecord the harness's own figures for the same run; empty when the harness keeps no such record
     * @param startContexts the first turn of each class and model the {@code launch} stage ran
     * @return the record of the usage stage
     * @throws ProfileRefusal if a declared pointer does not resolve on the output, or a parsed figure differs from the
     *                        harness's own record
     */
    public static UsageResult usage(LoadedProfile profile, JsonNode turn, Map<String, BigDecimal> harnessRecord,
            List<StartContextInput> startContexts) {
        Objects.requireNonNull(startContexts, "startContexts");
        var usage = profile.headless("/usage").orElseThrow(
                () -> ProfileRefusal.invalid(profile.clientId() + PROFILE_SUFFIX, "/headless/usage",
                        "a profile that is verified for usage names its usage block"));
        var figures = new LinkedHashMap<String, UsageStatus>();
        var parsed = new LinkedHashMap<String, BigDecimal>();
        for (var figure : USAGE_FIGURES) {
            var declaration = usage.member(figure).orElse(JsonNode.nullValue());
            if (declaration.isNull()) {
                figures.put(figure, UsageStatus.MISSING);
            } else {
                var pointer = declaration.text().orElseThrow();
                if (Wire.NOT_APPLICABLE.equals(pointer)) {
                    if (NEVER_NOT_APPLICABLE.contains(figure)) {
                        throw ProfileRefusal.invalid(profile.clientId() + PROFILE_SUFFIX,
                                "/headless/usage/" + figure,
                                "'%s' is never not_applicable".formatted(figure));
                    }
                    figures.put(figure, UsageStatus.NOT_APPLICABLE);
                } else {
                    var number = parseFigure(turn, pointer, figure, profile, harnessRecord);
                    figures.put(figure, UsageStatus.REPORTED);
                    parsed.put(figure, number);
                }
            }
        }
        return new UsageResult(figures, parsed, startContext(profile, usage, startContexts));
    }

    private static List<StartContext> startContext(LoadedProfile profile, JsonNode usage,
            List<StartContextInput> inputs) {
        var contexts = new ArrayList<StartContext>();
        for (var input : inputs) {
            contexts.add(new StartContext(input.workerClass(), input.model(),
                    firstTurnTokens(profile, usage, input.firstTurn())));
        }
        return List.copyOf(contexts);
    }

    private static OptionalLong firstTurnTokens(LoadedProfile profile, JsonNode usage, JsonNode turn) {
        long sum = 0;
        for (var figure : START_CONTEXT_FIGURES) {
            var declaration = usage.member(figure).orElse(JsonNode.nullValue());
            if (declaration.isNull()) {
                return OptionalLong.empty();
            }
            var pointer = declaration.text().orElseThrow();
            if (Wire.NOT_APPLICABLE.equals(pointer)) {
                continue;
            }
            var value = integerAt(profile, turn, pointer);
            if (value.isEmpty()) {
                return OptionalLong.empty();
            }
            sum += value.getAsLong();
        }
        return OptionalLong.of(sum);
    }

    private static OptionalLong integerAt(LoadedProfile profile, JsonNode turn, String pointer) {
        final JsonNode value;
        try {
            value = JsonPointer.resolve(turn, pointer, profile.clientId() + PROFILE_SUFFIX);
        } catch (ProfileRefusal _) {
            return OptionalLong.empty();
        }
        if (!value.isInteger() || value.integerValue().orElseThrow() < 0) {
            return OptionalLong.empty();
        }
        return value.integerValue();
    }

    private static BigDecimal parseFigure(JsonNode turn, String pointer, String figure, LoadedProfile profile,
            Map<String, BigDecimal> harnessRecord) {
        var value = JsonPointer.resolve(turn, pointer, profile.clientId() + PROFILE_SUFFIX);
        var number = value.decimalValue().orElse(null);
        if (number == null) {
            throw ProfileRefusal.invalid(profile.clientId() + PROFILE_SUFFIX, pointer,
                    "the usage pointer of '%s' does not resolve to a figure".formatted(figure));
        }
        var recorded = harnessRecord.get(figure);
        if (recorded != null && recorded.compareTo(number) != 0) {
            throw ProfileRefusal.invalid(profile.clientId() + PROFILE_SUFFIX, pointer,
                    "the parsed figure of '%s' is %s, the harness records %s".formatted(figure, number, recorded));
        }
        return number;
    }
}
