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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One entry of the machine's harness enrolment store (PM-SEC-5, Harness Enrolment Store): the absolute executable path,
 * the SHA-256 and the version of the last accepted probe, the instants of the enrolment and of the last accepted
 * self-update, the usage report of the harness and, where its profile declares a weights probe, the weights identity of
 * the models it serves. The store records nothing else: never an environment value, a variable name or a credential.
 *
 * @param clientId       the client id of the enrolled harness
 * @param executablePath the resolved absolute path of the executable
 * @param sha256         the lowercase-hex SHA-256 of the executable at the last accepted probe
 * @param version        the version the profile's version probe reported
 * @param enrolledAt     when the harness was enrolled
 * @param updatedAt      when the last self-update was accepted; equal to {@code enrolledAt} before the first
 * @param usageReport    what the harness reports of its usage
 * @param events         the per-kind results of the profile's {@code events} stage, {@code reported} or {@code missing}
 * @param weights        the weights identity per model; empty when the profile declares no weights probe
 * @since 0.1
 */
public record HarnessEnrolment(String clientId, Path executablePath, String sha256, String version, Instant enrolledAt,
Instant updatedAt, UsageReport usageReport, Map<String, String> events, Map<String, Weights> weights) {

    private static final Pattern CLIENT_ID = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> EVENT_RESULTS = Set.of("reported", "missing");

    /** Copies the collections and refuses a malformed entry. */
    public HarnessEnrolment {
        Objects.requireNonNull(clientId, "clientId");
        if (!CLIENT_ID.matcher(clientId).matches()) {
            throw new IllegalArgumentException("Not a client id: " + clientId);
        }
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
     * @param version  the version probed after an accepted self-update
     * @param sha256   the digest of the changed executable
     * @param updateTo the instant of the accepted self-update
     * @return the entry with the new build of the same harness
     */
    public HarnessEnrolment updated(String version, String sha256, Instant updateTo) {
        return new HarnessEnrolment(clientId, executablePath, sha256, version, enrolledAt, updateTo, usageReport,
                events, weights);
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
        private static final Set<String> FIGURES = Set.of("input", "output", "cache_read", "cache_creation",
                "reasoning", "tool_uses");

        /** Copies the figures and the start context and refuses an unknown result or figure. */
        public UsageReport {
            Objects.requireNonNull(source, "source");
            var results = new LinkedHashMap<String, String>();
            figures.forEach((figure, result) -> {
                if (!FIGURES.contains(figure)) {
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
         * What a worker costs before it does anything, per class and model (Client Profiles § Tokens, Not Money, Start
         * Context).
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
