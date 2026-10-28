/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.core.role.RoleClass;

/**
 * One entry of the machine's harness enrolment store (PM-SEC-5, Harness Enrolment Store): the absolute executable path,
 * the SHA-256 and the version of the last accepted probe, the instants of the enrolment and of the last accepted
 * self-update, the usage report of the harness and, where its profile declares a weights probe, the weights identity of
 * the models it serves. The store records nothing else: never an environment value, a variable name or a credential.
 * <p>
 * The remote form of a judgment endpoint has no serving program (Client Profiles § Remote Form), so its entry carries a
 * {@link Binding} of the origin and the credential key instead of the program's path, digest and version; the two forms
 * are exclusive (ADR runtime-endpoint/005).
 *
 * @param clientId       the client id of the enrolled harness
 * @param executablePath the resolved absolute path of the executable; {@code null} for a remote endpoint
 * @param sha256         the lowercase-hex SHA-256 of the executable at the last accepted probe; {@code null} for a
 *                       remote endpoint
 * @param version        the version the profile's version probe reported; {@code null} for a remote endpoint
 * @param enrolledAt     when the harness was enrolled
 * @param updatedAt      when the last self-update was accepted; equal to {@code enrolledAt} before the first
 * @param usageReport    what the harness reports of its usage
 * @param events         the per-kind results of the profile's {@code events} stage, {@code reported} or {@code missing}
 * @param weights        the weights identity per model; empty when the profile declares no weights probe
 * @param binding        the origin and credential key of a remote endpoint; {@code null} for a serving program
 * @since 0.1
 */
public record HarnessEnrolment(String clientId, Path executablePath, String sha256, String version, Instant enrolledAt,
Instant updatedAt, UsageReport usageReport, Map<String, String> events, Map<String, Weights> weights,
Binding binding) {

    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ORIGIN = Pattern.compile("^https://[a-z0-9]([a-z0-9.-]*[a-z0-9])?(:[0-9]{1,5})?$");
    private static final Set<String> EVENT_RESULTS = Set.of("reported", "missing");

    /**
     * @param clientId       the client id of the enrolled harness
     * @param executablePath the resolved absolute path of the executable
     * @param sha256         the lowercase-hex SHA-256 of the executable
     * @param version        the version the profile's version probe reported
     * @param enrolledAt     when the harness was enrolled
     * @param updatedAt      when the last self-update was accepted
     * @param usageReport    what the harness reports of its usage
     * @param events         the per-kind results of the profile's {@code events} stage
     * @param weights        the weights identity per model
     */
    public HarnessEnrolment(String clientId, Path executablePath, String sha256, String version, Instant enrolledAt,
            Instant updatedAt, UsageReport usageReport, Map<String, String> events, Map<String, Weights> weights) {
        this(clientId, executablePath, sha256, version, enrolledAt, updatedAt, usageReport, events, weights, null);
    }

    /** Copies the collections and refuses a malformed entry. */
    public HarnessEnrolment {
        Objects.requireNonNull(clientId, "clientId");
        if (!CLIENT_ID.matcher(clientId).matches()) {
            throw new IllegalArgumentException("Not a client id: " + clientId);
        }
        if (binding == null) {
            Objects.requireNonNull(executablePath, "executablePath");
            if (!executablePath.isAbsolute()) {
                throw new IllegalArgumentException("The executable path is not absolute: " + executablePath);
            }
            Objects.requireNonNull(sha256, "sha256");
            if (!DIGEST.matcher(sha256).matches()) {
                throw new IllegalArgumentException("Not a SHA-256: " + sha256);
            }
            Objects.requireNonNull(version, "version");
            if (version.isEmpty()) {
                throw new IllegalArgumentException("An enrolment names its version");
            }
        } else if (executablePath != null || sha256 != null || version != null) {
            throw new IllegalArgumentException("A remote endpoint has no serving program to enrol");
        }
        Objects.requireNonNull(enrolledAt, "enrolledAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(usageReport, "usageReport");
        var eventResults = new LinkedHashMap<String, String>();
        events.forEach((kind, result) -> {
            if (!EVENT_RESULTS.contains(result)) {
                throw new IllegalArgumentException("Not a stage result: " + result);
            }
            eventResults.put(kind, result);
        });
        events = Collections.unmodifiableMap(eventResults);
        weights = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(weights, "weights")));
    }

    /**
     * @param clientId      the client id of the endpoint profile
     * @param origin        the {@code https} origin of the service, the profile's {@code base_url}
     * @param credentialKey the Token Lane credential entry the runtime reads for it
     * @param enrolledAt    when the endpoint was enrolled
     * @param usageReport   what the endpoint reports of its usage
     * @return the enrolment of the remote form, which records the origin and the credential key and no program
     */
    public static HarnessEnrolment endpoint(String clientId, String origin, String credentialKey, Instant enrolledAt,
            UsageReport usageReport) {
        return new HarnessEnrolment(clientId, null, null, null, enrolledAt, enrolledAt,
                Objects.requireNonNull(usageReport, "usageReport"), Map.of(), Map.of(),
                new Binding(origin, credentialKey));
    }

    /** @return whether this entry is the remote form of a judgment endpoint */
    public boolean isEndpoint() {
        return binding != null;
    }

    /**
     * @param weights   the weights identity per model after a read
     * @param updatedAt the instant the enrolment was rewritten
     * @return the entry with the new weights, of either form
     */
    public HarnessEnrolment withWeights(Map<String, Weights> weights, Instant updatedAt) {
        return new HarnessEnrolment(clientId, executablePath, sha256, version, enrolledAt, updatedAt, usageReport,
                events, weights, binding);
    }

    /**
     * @param version  the version probed after an accepted self-update
     * @param sha256   the digest of the changed executable
     * @param updateTo the instant of the accepted self-update
     * @return the entry with the new build of the same harness
     * @throws IllegalStateException if this entry is a remote endpoint, which has no executable to self-update
     */
    public HarnessEnrolment updated(String version, String sha256, Instant updateTo) {
        if (binding != null) {
            throw new IllegalStateException("A remote endpoint has no executable to self-update: " + clientId);
        }
        return new HarnessEnrolment(clientId, executablePath, sha256, version, enrolledAt, updateTo, usageReport,
                events, weights);
    }

    /**
     * The origin and the credential key of a remote endpoint's enrolment (Client Profiles § Remote Form): there is no
     * serving program, so no owner check applies and no program is started, stopped or configured.
     *
     * @param origin        the {@code https} origin of the one service
     * @param credentialKey the Token Lane credential entry the runtime reads at the moment of a request
     * @since 0.1
     */
    public record Binding(String origin, String credentialKey) {

        /**
         * @throws NullPointerException     if a value is {@code null}
         * @throws IllegalArgumentException if the origin is not an {@code https} origin or the key is not a credential
         *                                  key
         */
        public Binding {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(credentialKey, "credentialKey");
            if (!ORIGIN.matcher(origin).matches()) {
                throw new IllegalArgumentException("Not an https origin: " + origin);
            }
            if (!CLIENT_ID.matcher(credentialKey).matches()) {
                throw new IllegalArgumentException("Not a credential key: " + credentialKey);
            }
        }
    }

    /** What the harness reports of its usage (PM-CLIENT-2, Tokens, Not Money). */
    public enum UsageSource {
        /** From the profile's usage pointers. */
        DECLARED("declared"),
        /** From the {@code usage} stage of the profile's verification. */
        VERIFIED("verified");

        private final String wireName;

        UsageSource(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the source in the store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param name the name of a source
         * @return the source of that name
         */
        public static UsageSource fromWireName(String name) {
            for (var value : values()) {
                if (value.wireName.equals(name)) {
                    return value;
                }
            }
            throw new IllegalArgumentException("Not a usage source: " + name);
        }
    }

    /**
     * The usage report kept with an enrolment (Client Profiles § Tokens, Not Money): what the harness reports of its
     * usage, and what a worker costs before it does anything.
     *
     * @param source       where the report came from
     * @param figures      the result per figure, {@code reported}, {@code not_applicable} or {@code missing}
     * @param startContext the first-turn tokens per class and model; empty for {@code source} declared
     * @since 0.1
     */
    public record UsageReport(UsageSource source, Map<String, String> figures, List<StartContext> startContext) {

        private static final Set<String> FIGURE_RESULTS = Set.of("reported", "not_applicable", "missing");
        private static final Set<String> REPORT_FIGURES = ProfileStages.REPORT_FIGURES.stream()
                .map(UsageReport::reportName)
                .collect(Collectors.toUnmodifiableSet());

        /** Copies the figures and the start context and refuses an unknown result or figure. */
        public UsageReport {
            Objects.requireNonNull(source, "source");
            var results = new LinkedHashMap<String, String>();
            figures.forEach((figure, result) -> {
                if (!REPORT_FIGURES.contains(figure)) {
                    throw new IllegalArgumentException("Not a usage report figure: " + figure);
                }
                if (!FIGURE_RESULTS.contains(result)) {
                    throw new IllegalArgumentException("Not a usage result: " + result);
                }
                results.put(figure, result);
            });
            figures = Collections.unmodifiableMap(results);
            startContext = List.copyOf(startContext);
            if (source == UsageSource.DECLARED && !startContext.isEmpty()) {
                throw new IllegalArgumentException("A declared usage report carries no start context");
            }
        }

        /**
         * @param source  where the report came from
         * @param figures the result per figure
         */
        public UsageReport(UsageSource source, Map<String, String> figures) {
            this(source, figures, List.of());
        }

        /**
         * @param source        where the report came from
         * @param stagedFigures the usage stage's result per figure, keyed as {@code headless.usage} names it
         * @param startContext  the start context per class and model
         * @return the report with the figures keyed as the enrolment store names them
         */
        public static UsageReport of(UsageSource source, Map<String, String> stagedFigures,
                List<StartContext> startContext) {
            var report = new LinkedHashMap<String, String>();
            stagedFigures.forEach((figure, result) -> report.put(reportName(figure), result));
            return new UsageReport(source, report, startContext);
        }

        private static String reportName(String figure) {
            return switch (figure) {
                case "input_tokens" -> "input";
                case "output_tokens" -> "output";
                default -> figure;
            };
        }

        /**
         * What a worker costs before it does anything, per class and model (Client Profiles § Tokens, Not Money, Start
         * Context).
         *
         * @param workerClass the class of the worker, {@code writer}, {@code read_only} or {@code zero_tool}
         * @param model       the model the stage ran with
         * @param tokens      the first-turn tokens; empty when the first turn does not report the input figures
         * @since 0.1
         */
        public record StartContext(String workerClass, String model, OptionalLong tokens) {

            /** The classes of the closed list, derived from the role classes. */
            public static final Set<String> CLASSES = Arrays.stream(RoleClass.values())
                    .map(RoleClass::wireName)
                    .collect(Collectors.toUnmodifiableSet());

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
    }

    /**
     * The weights identity of one model.
     *
     * @param weightsId the identity the harness reported
     * @param readAt    when it was read
     * @since 0.1
     */
    public record Weights(String weightsId, Instant readAt) {

        /** @throws IllegalArgumentException if the identity is empty or too long */
        public Weights {
            Objects.requireNonNull(weightsId, "weightsId");
            if (weightsId.isEmpty() || weightsId.length() > 256) {
                throw new IllegalArgumentException("A weights identity is 1 to 256 characters");
            }
            Objects.requireNonNull(readAt, "readAt");
        }
    }
}
