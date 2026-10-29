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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The consent-before-acting rule of one action (PM-SEC-8, Store Schemas § Consent Before Acting): an action state is
 * reached only through its consent state, answered {@code yes} or {@code yes_remember}, or passed without a question
 * where the kind is remembered in {@link ConsentStore}. A {@code no} records nothing and leaves the scope in the consent
 * state, so only an optional action goes on without it. An act of the operator's own needs no consent.
 * <p>
 * The gate decides; it does not write. {@link #answer(Request, Answer, Consent.Channel, Optional)} returns whether the
 * action runs and, for a {@code yes_remember}, the entry the caller records through
 * {@link ConsentStore#grant(Consent, long)} in the same step as the action's state.
 * <p>
 * One consent state may ask several kinds, each answered on its own: the caller asks the gate once per kind and runs only
 * the actions whose answer allows it. A consent covers its kind and never a wider one, because a remembered kind is
 * looked up by its own kind alone.
 *
 * @since 0.1
 */
public final class ConsentGate {

    private static final Pattern DEVICE_ID = Pattern.compile("dev-[0-9a-f]{12}");

    private final ConsentStore.Snapshot snapshot;
    private final Clock clock;

    /**
     * @param snapshot the consent store as it was read before the question
     * @param clock    the clock of the instant a {@code yes_remember} is recorded at
     */
    public ConsentGate(ConsentStore.Snapshot snapshot, Clock clock) {
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** The answer the operator gave to a consent state. */
    public enum Answer {
        /** The action runs once; nothing is remembered. */
        YES("yes"),
        /** The action runs and its kind is remembered for the scope of the request. */
        YES_REMEMBER("yes_remember"),
        /** The action does not run; nothing is remembered. */
        NO("no");

        private final String wireName;

        Answer(String wireName) {
            this.wireName = wireName;
        }

        /** @return the link the operator answered */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName a value of an answer link
         * @return the answer, or empty for a value outside the closed list
         */
        public static Optional<Answer> of(String wireName) {
            for (var answer : values()) {
                if (answer.wireName.equals(wireName)) {
                    return Optional.of(answer);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * One action's consent request.
     *
     * @param kind      the kind the action needs
     * @param projectId the project, for a kind remembered for a project
     * @param scripts   the scripts the action would run, for {@code run_checkout_scripts}
     * @since 0.1
     */
    public record Request(ConsentKind kind, Optional<String> projectId, List<ConsentScript> scripts) {

        /** Rejects {@code null} components and keeps the scripts unmodifiable. */
        public Request {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(projectId, "projectId");
            scripts = List.copyOf(scripts);
        }

        /**
         * @param kind a kind remembered for the machine
         * @return the request of the kind
         */
        public static Request machine(ConsentKind kind) {
            return new Request(kind, Optional.empty(), List.of());
        }

        /**
         * @param kind      a kind remembered for a project
         * @param projectId the project
         * @return the request of the kind
         */
        public static Request project(ConsentKind kind, String projectId) {
            return new Request(kind, Optional.of(projectId), List.of());
        }

        /**
         * @param projectId the project
         * @param scripts   the scripts the action would run, each with its digest
         * @return the request of {@code run_checkout_scripts}
         */
        public static Request checkoutScripts(String projectId, List<ConsentScript> scripts) {
            return new Request(ConsentKind.RUN_CHECKOUT_SCRIPTS, Optional.of(projectId), scripts);
        }
    }

    /**
     * What the gate decided for one action.
     *
     * @param runs      whether the action may run
     * @param toRecord  the consent to record through {@link ConsentStore#grant(Consent, long)}, for a
     *                  {@code yes_remember}
     * @since 0.1
     */
    public record Decision(boolean runs, Optional<Consent> toRecord) {

        /** Rejects a {@code null} entry. */
        public Decision {
            Objects.requireNonNull(toRecord, "toRecord");
        }

        static Decision pass() {
            return new Decision(true, Optional.empty());
        }

        static Decision refuse() {
            return new Decision(false, Optional.empty());
        }
    }

    /**
     * The decision of one consent state.
     *
     * @param request  the action's request
     * @param answer   the answer the operator gave
     * @param channel  the channel the answer came through
     * @param deviceId the paired device, for an answer through the web app
     * @return whether the action runs, and the consent to record
     * @throws IllegalArgumentException if {@code yes_remember} is answered for a kind that is never remembered, if the
     *                                  request's project does not belong to its kind, or if the device is not a paired
     *                                  device of the web channel
     */
    public Decision answer(Request request, Answer answer, Consent.Channel channel, Optional<String> deviceId) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(answer, "answer");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(deviceId, "deviceId");
        requireScope(request);
        if (snapshot.isRemembered(request.kind(), request.projectId(), request.scripts())) {
            return Decision.pass();
        }
        return switch (answer) {
            case YES -> Decision.pass();
            case NO -> Decision.refuse();
            case YES_REMEMBER -> new Decision(true, Optional.of(remember(request, channel, deviceId)));
        };
    }

    /**
     * An act of the operator's own needs no consent: a secret the operator enters is stored on that act. No consent is
     * asked and nothing is remembered.
     *
     * @return the decision that lets the act run
     */
    public static Decision operatorAct() {
        return Decision.pass();
    }

    /** @return the instant a consent granted now is recorded at */
    public Instant now() {
        return clock.instant();
    }

    private Consent remember(Request request, Consent.Channel channel, Optional<String> deviceId) {
        if (!request.kind().rememberable()) {
            throw new IllegalArgumentException(
                    "The consent kind '%s' is never remembered, so no yes_remember link exists for it"
                            .formatted(request.kind().wireName()));
        }
        requireDevice(channel, deviceId);
        var grantedAt = now();
        if (!request.kind().projectScoped()) {
            return Consent.machine(request.kind(), grantedAt, channel, deviceId);
        }
        var project = request.projectId().orElseThrow(() -> new IllegalArgumentException(
                "The consent kind '%s' is remembered for a project and names none"
                        .formatted(request.kind().wireName())));
        return request.kind().scriptScoped()
                ? Consent.checkoutScripts(project, request.scripts(), grantedAt, channel, deviceId)
                : Consent.project(request.kind(), project, grantedAt, channel, deviceId);
    }

    private static void requireScope(Request request) {
        if (request.kind().projectScoped() != request.projectId().isPresent()) {
            throw new IllegalArgumentException("The consent kind '%s' is %s a project, but the request %s one"
                    .formatted(request.kind().wireName(), request.kind().projectScoped() ? "remembered for" : "not for",
                            request.projectId().isPresent() ? "names" : "names no"));
        }
        if (!request.kind().scriptScoped() && !request.scripts().isEmpty()) {
            throw new IllegalArgumentException(
                    "Only run_checkout_scripts covers scripts, and '%s' requests %d".formatted(
                            request.kind().wireName(), request.scripts().size()));
        }
    }

    private static void requireDevice(Consent.Channel channel, Optional<String> deviceId) {
        deviceId.ifPresent(device -> {
            if (channel != Consent.Channel.WEB) {
                throw new IllegalArgumentException("Only an answer through web names a paired device: " + device);
            }
            if (!DEVICE_ID.matcher(device).matches()) {
                throw new IllegalArgumentException("Not a paired device id of an answer: " + device);
            }
        });
    }
}
