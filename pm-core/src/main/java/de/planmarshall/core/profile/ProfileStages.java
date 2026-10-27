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
import java.util.Objects;
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
 * <p>
 * The four stages of an endpoint harness — {@code identity}, {@code launch}, {@code classes} and {@code limits} — are
 * held here too: what each observed, the values it settled ({@link EndpointVerification#values()}) and the status of
 * each stage ({@link EndpointVerification#stageStatuses()}).
 *
 * @since 0.1
 */
public final class ProfileStages {

    /** The figures of {@code headless.usage}, in schema order. */
    public static final List<String> USAGE_FIGURES = List.of("input_tokens", "output_tokens", "cache_read",
            "cache_creation", "reasoning", "cost_usd", "tool_uses");

    /** The suffix of a profile document, as a refusal names it. */
    private static final String PROFILE_SUFFIX = ".json";
    private static final String MEASURED = "measured";
    private static final String NOT_MEASURED = "not_measured";

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
     * What the stage {@code resumability} measured of one form of a continuation (Client Profiles § Resumability): the
     * form passed, and the longest hold or session age it proved.
     *
     * @param measured      whether the stage ran the form and it passed
     * @param longestSeconds the longest hold or session age the stage proved; {@code null} when the form was not
     *                       measured
     * @since 0.1
     */
    public record FormMeasurement(boolean measured, Integer longestSeconds) {

        /**
         * @throws IllegalArgumentException if a measured form carries no positive limit, or an unmeasured one carries a
         *                                  limit
         */
        public FormMeasurement {
            if (measured && (longestSeconds == null || longestSeconds < 1)) {
                throw new IllegalArgumentException("A measured form carries the limit it proved");
            }
            if (!measured && longestSeconds != null) {
                throw new IllegalArgumentException("A form the stage did not measure carries no limit");
            }
        }

        /** @return a form the stage did not measure, which leaves the profile's value {@code false} and {@code null} */
        public static FormMeasurement notMeasured() {
            return new FormMeasurement(false, null);
        }
    }

    /**
     * The result of the stage {@code resumability}: what the stage measured of the hold and of the resume. A form it
     * did not measure stays {@code false} with no limit, so the profile records no capability the stage never proved.
     *
     * @param hold   what the stage measured of a worker holding its task for the answer
     * @param resume what the stage measured of a generation resumed from a stored session
     * @since 0.1
     */
    public record ResumabilityResult(FormMeasurement hold, FormMeasurement resume) {

        /** The schema's upper bound of {@code headless.resumability.max_hold_seconds}. */
        public static final int MAX_HOLD_SECONDS = 3_600;

        /** The schema's upper bound of {@code headless.resumability.max_age_seconds}. */
        public static final int MAX_RESUME_AGE_SECONDS = 604_800;

        /**
         * @throws NullPointerException     if a measurement is {@code null}
         * @throws IllegalArgumentException if a measured limit lies outside its schema range
         */
        public ResumabilityResult {
            Objects.requireNonNull(hold, "hold");
            Objects.requireNonNull(resume, "resume");
            if (hold.measured() && hold.longestSeconds() > MAX_HOLD_SECONDS) {
                throw new IllegalArgumentException("A measured hold is at most %d seconds"
                        .formatted(MAX_HOLD_SECONDS));
            }
            if (resume.measured() && resume.longestSeconds() > MAX_RESUME_AGE_SECONDS) {
                throw new IllegalArgumentException("A measured resume age is at most %d seconds"
                        .formatted(MAX_RESUME_AGE_SECONDS));
            }
        }

        /** @return whether the harness may hold after a question, with its limit */
        public boolean holdVerified() {
            return hold.measured();
        }

        /** @return the longest hold the stage proved, or {@code null} */
        public Integer maxHoldSeconds() {
            return hold.longestSeconds();
        }

        /** @return whether the harness may resume a stored session, with its limit */
        public boolean resumeVerified() {
            return resume.measured();
        }

        /** @return the longest session age the stage proved, or {@code null} */
        public Integer maxResumeAgeSeconds() {
            return resume.longestSeconds();
        }

        /**
         * @return the record's {@code values[]} entries the stage writes: one per form, {@code measured} where the form
         *         passed and {@code not_measured} where it did not. The verification record admits only the pointer and
         *         the status of a value; the limit it proved is a profile value, carried by {@link #hold()} and
         *         {@link #resume()}, not by the record
         */
        public List<ValueEntry> values() {
            return List.of(
                    new ValueEntry("/headless/resumability/hold",
                            hold.measured() ? MEASURED : NOT_MEASURED),
                    new ValueEntry("/headless/resumability/resume",
                            resume.measured() ? MEASURED : NOT_MEASURED));
        }
    }

    /**
     * One entry of the verification record's {@code values[]} the stage {@code resumability} writes.
     *
     * @param pointer the profile pointer the stage settled
     * @param status  {@code measured} or {@code not_measured}
     * @since 0.1
     */
    public record ValueEntry(String pointer, String status) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the status is not one of the record's
         */
        public ValueEntry {
            Objects.requireNonNull(pointer, "pointer");
            Objects.requireNonNull(status, "status");
            if (!MEASURED.equals(status) && !NOT_MEASURED.equals(status)) {
                throw new IllegalArgumentException("Not a resumability value status: " + status);
            }
        }
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

    // ---- the stages of an endpoint harness ------------------------------------------------------------------

    /** The stages of the verification of an endpoint, in the order they run. */
    public static final List<String> ENDPOINT_STAGES = List.of("identity", "launch", "classes", "limits");

    /** The status of one value the endpoint stages write into the verification record. The set is closed. */
    public enum EndpointValueStatus {
        /** The stage observed the value on the endpoint. */
        MEASURED("measured"),
        /** The stage could not observe the value, so it settles nothing. */
        NOT_MEASURED("not_measured");

        private final String wireName;

        EndpointValueStatus(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the status in a record */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * One entry of the verification record's {@code values[]} an endpoint stage writes.
     *
     * @param pointer the profile pointer the stage settled
     * @param status  {@code measured} or {@code not_measured}
     * @since 0.1
     */
    public record EndpointValue(String pointer, EndpointValueStatus status) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public EndpointValue {
            Objects.requireNonNull(pointer, "pointer");
            Objects.requireNonNull(status, "status");
        }
    }

    /**
     * What the stage {@code identity} observed of the serving program and of the models it lists.
     *
     * @param passed         whether the stage settled the identity values
     * @param executablePath the absolute path of the executable that owns the listening socket; {@code null} when the
     *                       stage did not find it
     * @param version        the version the probe reported; {@code null} when the stage did not run it
     * @param models         the model ids of the listing; empty when the stage did not read it
     * @param weights        the weights identity per model, where the profile declares a probe; empty otherwise
     * @since 0.1
     */
    public record EndpointIdentity(boolean passed, String executablePath, String version, List<String> models,
    Map<String, String> weights) {

        /** Copies the collections and refuses a passed stage that observed nothing. */
        public EndpointIdentity {
            models = List.copyOf(models);
            weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
            if (passed && executablePath == null) {
                throw new IllegalArgumentException("A passed identity stage names the executable it found");
            }
        }

        /** @return whether the stage read a weights identity for a model */
        public boolean probeDeclared() {
            return !weights.isEmpty();
        }
    }

    /**
     * What the stage {@code launch} observed of readiness and of the result schema.
     *
     * @param passed       whether the endpoint answered the base task with a valid document
     * @param readiness    whether the endpoint answered its readiness check
     * @param resultSchema the measured value, {@code enforced}, {@code advisory} or {@code none}
     * @param streamingOff whether the answer was a single document rather than an event stream
     * @since 0.1
     */
    public record EndpointLaunch(boolean passed, boolean readiness, String resultSchema, boolean streamingOff) {

        /** The measured result schema, closed because the record writes it as a value. */
        private static final Set<String> SCHEMAS = Set.of("enforced", "advisory", "none");

        /**
         * @throws NullPointerException     if the schema is {@code null}
         * @throws IllegalArgumentException if the schema is not one the stage measures
         */
        public EndpointLaunch {
            Objects.requireNonNull(resultSchema, "resultSchema");
            if (!SCHEMAS.contains(resultSchema)) {
                throw new IllegalArgumentException("Not a measured result schema: " + resultSchema);
            }
        }
    }

    /**
     * What the stage {@code classes} observed of the tool-call probe.
     *
     * @param toolCallAnswered whether the endpoint answered the probe with a tool call
     * @param toolsIgnored     whether it answered with the decoy result the task text named, which records the endpoint
     *                         as ignoring its tools
     * @since 0.1
     */
    public record EndpointClasses(boolean toolCallAnswered, boolean toolsIgnored) {

        /** @return the per-kind results the record keeps for the stage */
        public Map<String, String> results() {
            var results = new LinkedHashMap<String, String>();
            results.put("tool_call", toolCallAnswered ? "answered" : "none");
            results.put("tools", toolsIgnored ? "tools_ignored" : "not_ignored");
            return Collections.unmodifiableMap(results);
        }
    }

    /**
     * What the stage {@code limits} measured by bisection.
     *
     * @param passed        whether the bisection settled a limit
     * @param contextTokens the largest accepted request; {@code 0} when the stage did not settle one
     * @since 0.1
     */
    public record EndpointLimits(boolean passed, int contextTokens) {

        /**
         * @throws IllegalArgumentException if a passed stage names no positive limit
         */
        public EndpointLimits {
            if (passed && contextTokens < 1) {
                throw new IllegalArgumentException("A passed limits stage names the limit it measured");
            }
        }
    }

    /**
     * The result of the four stages of an endpoint's verification.
     *
     * @param identity the stage {@code identity}
     * @param launch   the stage {@code launch}
     * @param classes  the stage {@code classes}
     * @param limits   the stage {@code limits}
     * @since 0.1
     */
    public record EndpointVerification(EndpointIdentity identity, EndpointLaunch launch, EndpointClasses classes,
    EndpointLimits limits) {

        /**
         * @throws NullPointerException if a stage is {@code null}
         */
        public EndpointVerification {
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(launch, "launch");
            Objects.requireNonNull(classes, "classes");
            Objects.requireNonNull(limits, "limits");
        }

        /** @return whether a failed {@code identity} or {@code launch} refuses the draft */
        public boolean refusesDraft() {
            return !identity.passed() || !launch.passed();
        }

        /**
         * @return the stage status per stage name, as the verification record keeps it
         */
        public Map<String, String> stageStatuses() {
            var statuses = new LinkedHashMap<String, String>();
            statuses.put("identity", identity.passed() ? "passed" : "failed");
            statuses.put("launch", launch.passed() ? "passed" : "failed");
            statuses.put("classes", "passed");
            statuses.put("limits", limits.passed() ? "passed" : "not_run");
            return Collections.unmodifiableMap(statuses);
        }

        /**
         * @return the {@code values[]} entries the stages write; {@code classes} records a result and settles no value
         */
        public List<EndpointValue> values() {
            return List.of(
                    new EndpointValue("/identity", endpointStatus(identity.passed())),
                    new EndpointValue("/executable", endpointStatus(identity.passed())),
                    new EndpointValue("/endpoint/models_path", endpointStatus(identity.passed())),
                    new EndpointValue("/endpoint/models_pointer", endpointStatus(identity.passed())),
                    new EndpointValue("/weights_probe", endpointStatus(identity.probeDeclared())),
                    new EndpointValue("/endpoint/readiness_path", endpointStatus(launch.readiness())),
                    new EndpointValue("/endpoint/result_schema", endpointStatus(launch.passed())),
                    new EndpointValue("/endpoint/context_tokens", endpointStatus(limits.passed())));
        }

        private static EndpointValueStatus endpointStatus(boolean measured) {
            return measured ? EndpointValueStatus.MEASURED : EndpointValueStatus.NOT_MEASURED;
        }
    }
}
