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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.JsonPointer;

/**
 * Reads the weights identity of a model on an endpoint harness (Client Profiles § Weights Identity): from a member of
 * the models listing, or from a probe program run without a shell with the {@code {{model}}} slot.
 * <p>
 * The reader answers one identity or none: a model the listing does not name, a pointer that does not resolve, a probe
 * that fails or reports no identity inside its pattern all leave the identity unread, and the caller records it as
 * unread rather than as an identity. The identity is a string of the listing or the first group of the probe's output,
 * bounded to the 256 characters an enrolment keeps.
 *
 * @since 0.1
 */
public final class WeightsReader {

    /** The longest identity an enrolment keeps. */
    public static final int MAX_IDENTITY_LENGTH = 256;

    /** The longest the reader waits for an argv probe. */
    public static final int PROBE_TIMEOUT_SECONDS = 10;

    /**
     * @param profile       the endpoint profile
     * @param model         the model id
     * @param modelsListing the array of model objects of the listing; {@code null} when it was not read
     * @return the identity of the model's weights, or why none was read
     */
    public Reading read(EndpointProfile profile, String model, JsonNode modelsListing) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(model, "model");
        var probe = profile.weightsProbe();
        if (probe == null) {
            return Reading.unread(model, "the profile declares no weights probe");
        }
        if ("models_listing".equals(probe.source())) {
            return fromListing(model, probe.pointer(), modelsListing);
        }
        return fromProbe(model, probe);
    }

    private Reading fromListing(String model, String pointer, JsonNode listing) {
        if (listing == null || !listing.isArray()) {
            return Reading.unread(model, "the models listing was not read");
        }
        var entry = listing.elements().stream()
                .filter(element -> element.member("id").flatMap(JsonNode::text).filter(model::equals).isPresent())
                .findFirst();
        if (entry.isEmpty()) {
            return Reading.unread(model, "the models listing names no model '%s'".formatted(model));
        }
        try {
            var identity = JsonPointer.resolve(entry.get(), pointer, "weights").text();
            return identity.filter(value -> !value.isEmpty() && value.length() <= MAX_IDENTITY_LENGTH)
                    .map(value -> Reading.read(model, value))
                    .orElseGet(() -> Reading.unread(model, "the listing names no weights identity for '%s'".formatted(model)));
        }
        /*TODO: Catch specific not RuntimeException. Suppress: // cui-rewrite:disable InvalidExceptionUsageRecipe*/
        catch (RuntimeException _) {
            return Reading.unread(model, "the weights pointer '%s' does not resolve for '%s'".formatted(pointer, model));
        }
    }

    private Reading fromProbe(String model, EndpointProfile.WeightsProbe probe) {
        var output = run(probe.argvFor(model));
        if (output.isEmpty()) {
            return Reading.unread(model, "the weights probe of '%s' reported nothing".formatted(model));
        }
        return match(output.get(), probe.pattern())
                .filter(value -> !value.isEmpty() && value.length() <= MAX_IDENTITY_LENGTH)
                .map(value -> Reading.read(model, value))
                .orElseGet(() -> Reading.unread(model, "the weights probe of '%s' reported no identity".formatted(model)));
    }

    /**
     * @param output  the probe's output
     * @param pattern the profile's pattern; {@code null} when the profile declares none
     * @return the first captured group, or the whole match when the pattern captures nothing
     */
    static Optional<String> match(String output, String pattern) {
        if (pattern == null) {
            return Optional.empty();
        }
        try {
            var matcher = Pattern.compile(pattern).matcher(output);
            if (!matcher.find()) {
                return Optional.empty();
            }
            return Optional.of(matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group());
        } catch (PatternSyntaxException _) {
            return Optional.empty();
        }
    }

    /**
     * @param argv the argument vector, run without a shell
     * @return the standard output, or empty when the probe did not finish or failed
     */
    private static Optional<String> run(List<String> argv) {
        if (argv.isEmpty()) {
            return Optional.empty();
        }
        Process process = null;
        try {
            var started = new ProcessBuilder(argv).redirectErrorStream(true).start();
            process = started;
            var output = new ByteArrayOutputStream();
            var reader = Thread.ofVirtual().start(() -> drain(started, output));
            if (!started.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                started.destroyForcibly();
                return Optional.empty();
            }
            reader.join(TimeUnit.SECONDS.toMillis(PROBE_TIMEOUT_SECONDS));
            if (started.exitValue() != 0) {
                return Optional.empty();
            }
            return Optional.of(output.toString(StandardCharsets.UTF_8));
        } catch (IOException _) {
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private static void drain(Process process, ByteArrayOutputStream output) {
        try (var stream = process.getInputStream()) {
            stream.transferTo(output);
        } catch (IOException _) {
            // The probe ended before its output was read: the caller reads an exit status and no output.
        }
    }

    /**
     * One reading of a model's weights identity.
     *
     * @param model     the model id
     * @param weightsId the identity; {@code null} when none was read
     * @param detail    what happened, for a record
     * @since 0.1
     */
    public record Reading(String model, String weightsId, String detail) {

        /** Copies the values. */
        public Reading {
            Objects.requireNonNull(model, "model");
            Objects.requireNonNull(detail, "detail");
        }

        static Reading read(String model, String weightsId) {
            return new Reading(model, weightsId, "read");
        }

        static Reading unread(String model, String detail) {
            return new Reading(model, null, detail);
        }

        /** @return whether an identity was read */
        public boolean isRead() {
            return weightsId != null;
        }
    }
}
