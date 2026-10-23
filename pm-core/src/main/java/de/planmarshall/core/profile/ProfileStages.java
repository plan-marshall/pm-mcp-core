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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** The figures of {@code headless.usage}, in schema order. */
    public static final List<String> USAGE_FIGURES = List.of("input_tokens", "output_tokens", "cache_read",
            "cache_creation", "reasoning", "cost_usd", "tool_uses");

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
        NOT_APPLICABLE("not_applicable"),
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
     * The result of the stage {@code usage}.
     *
     * @param figures the status of each figure, in schema order
     * @param parsed  the parsed figure by name; empty for a figure that is not reported
     * @since 0.1
     */
    public record UsageResult(Map<String, UsageStatus> figures, Map<String, BigDecimal> parsed) {

        /** Copies both maps. */
        public UsageResult {
            figures = Collections.unmodifiableMap(new LinkedHashMap<>(figures));
            parsed = Collections.unmodifiableMap(new LinkedHashMap<>(parsed));
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
                if ("not_applicable".equals(pointer)) {
                    figures.put(figure, UsageStatus.NOT_APPLICABLE);
                } else {
                    var number = parseFigure(turn, pointer, figure, profile, harnessRecord);
                    figures.put(figure, UsageStatus.REPORTED);
                    parsed.put(figure, number);
                }
            }
        }
        return new UsageResult(figures, parsed);
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
