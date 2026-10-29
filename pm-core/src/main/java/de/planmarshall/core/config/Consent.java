/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * One remembered consent of {@code consents.json} (PM-SEC-8, Store Schemas § Consent Before Acting): the kind the
 * operator allowed, the project it belongs to where the kind is remembered for a project, the scripts it covers where
 * the kind is {@link ConsentKind#RUN_CHECKOUT_SCRIPTS}, and when and through which channel it was granted.
 * <p>
 * The entry is the store's only shape: a machine kind names no project and no scripts, a project kind names its project,
 * and {@code run_checkout_scripts} names the digest of every script it covers. A channel {@code cli} names no paired
 * device; a {@code web} entry may name one.
 *
 * @param kind      the kind the operator allowed
 * @param projectId the project, for a kind remembered for a project
 * @param scripts   the scripts a {@code run_checkout_scripts} entry covers
 * @param grantedAt when the consent was granted
 * @param channel   the channel it was granted through
 * @param deviceId  the paired device, for a {@code web} entry
 * @since 0.1
 */
public record Consent(ConsentKind kind, Optional<String> projectId, List<ConsentScript> scripts, Instant grantedAt,
Channel channel, Optional<String> deviceId) {

    private static final Pattern PROJECT_ID = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

    private static final Pattern DEVICE_ID = Pattern.compile("dev-[0-9a-f]{12}");

    /** The channel a consent was granted through. */
    public enum Channel {
        /** The terminal client. */
        CLI("cli"),
        /** The web app; only there a paired device may be named. */
        WEB("web");

        private final String wireName;

        Channel(String wireName) {
            this.wireName = wireName;
        }

        /** @return the value of {@code channel} in {@code consents.json} */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName a value of {@code channel}
         * @return the channel, or empty for a value outside the closed list
         */
        public static Optional<Channel> of(String wireName) {
            for (var channel : values()) {
                if (channel.wireName.equals(wireName)) {
                    return Optional.of(channel);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * @throws IllegalArgumentException if the kind, its project, its scripts, its channel and its device do not belong
     *                                  together
     */
    public Consent {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(projectId, "projectId");
        scripts = List.copyOf(scripts);
        Objects.requireNonNull(grantedAt, "grantedAt");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(deviceId, "deviceId");
        if (!kind.rememberable()) {
            throw new IllegalArgumentException(
                    "The consent kind '%s' is never remembered, so no entry of it exists".formatted(kind.wireName()));
        }
        requireProject(kind, projectId);
        requireScripts(kind, scripts);
        requireDevice(channel, deviceId);
    }

    private static void requireProject(ConsentKind kind, Optional<String> projectId) {
        if (!kind.projectScoped()) {
            if (projectId.isPresent()) {
                throw new IllegalArgumentException(
                        "The consent kind '%s' is not remembered for a project: %s".formatted(kind.wireName(),
                                projectId.get()));
            }
            return;
        }
        var project = projectId.orElseThrow(() -> new IllegalArgumentException(
                "The consent kind '%s' is remembered for a project and names none".formatted(kind.wireName())));
        if (!PROJECT_ID.matcher(project).matches()) {
            throw new IllegalArgumentException("Not a valid project id of a consent: " + project);
        }
    }

    private static void requireScripts(ConsentKind kind, List<ConsentScript> scripts) {
        if (kind.scriptScoped() || scripts.isEmpty()) {
            return;
        }
        throw new IllegalArgumentException(
                "Only run_checkout_scripts covers scripts, and '%s' names %d".formatted(kind.wireName(),
                        scripts.size()));
    }

    private static void requireDevice(Channel channel, Optional<String> deviceId) {
        deviceId.ifPresent(device -> {
            if (channel != Channel.WEB) {
                throw new IllegalArgumentException("Only a consent granted through web names a paired device: " + device);
            }
            if (!DEVICE_ID.matcher(device).matches()) {
                throw new IllegalArgumentException("Not a paired device id of a consent: " + device);
            }
        });
    }

    /**
     * @param kind      a kind remembered for the machine
     * @param grantedAt when the operator granted it
     * @param channel   the channel it was granted through
     * @param deviceId  the paired device, for a {@code web} entry
     * @return the entry of the machine-wide kind
     */
    public static Consent machine(ConsentKind kind, Instant grantedAt, Channel channel, Optional<String> deviceId) {
        return new Consent(kind, Optional.empty(), List.of(), grantedAt, channel, deviceId);
    }

    /**
     * @param kind      a kind remembered for a project
     * @param projectId the project
     * @param grantedAt when the operator granted it
     * @param channel   the channel it was granted through
     * @param deviceId  the paired device, for a {@code web} entry
     * @return the entry of the project-wide kind
     */
    public static Consent project(ConsentKind kind, String projectId, Instant grantedAt, Channel channel,
            Optional<String> deviceId) {
        return new Consent(kind, Optional.of(projectId), List.of(), grantedAt, channel, deviceId);
    }

    /**
     * @param projectId the project
     * @param scripts   every script the consent covers, with its digest
     * @param grantedAt when the operator granted it
     * @param channel   the channel it was granted through
     * @param deviceId  the paired device, for a {@code web} entry
     * @return the entry of {@code run_checkout_scripts}
     */
    public static Consent checkoutScripts(String projectId, List<ConsentScript> scripts, Instant grantedAt,
            Channel channel, Optional<String> deviceId) {
        return new Consent(ConsentKind.RUN_CHECKOUT_SCRIPTS, Optional.of(projectId), scripts, grantedAt, channel,
                deviceId);
    }

    /**
     * @param projectId the project an entry is scoped to
     * @return whether the entry belongs to the project
     */
    public boolean belongsTo(String projectId) {
        return this.projectId.filter(projectId::equals).isPresent();
    }
}
