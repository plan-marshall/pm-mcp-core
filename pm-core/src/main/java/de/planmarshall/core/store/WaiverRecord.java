/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * One line of the waiver log {@code plans/<plan_id>/waivers.jsonl} of a plan (PM-SEC-3, PM-IMPL-2): an operator's act
 * that declares a waivable gate met, raises a remediation budget ceiling, or revokes such a waiver.
 * <p>
 * The record has a closed schema in {@code format_version} 1 ({@link WaiverRecordCodec}). A waiver is never changed in
 * place: its revocation is a new line of the event {@link Event#REVOKED} with the same {@link #waiverId()}, and the
 * current state of a waiver is the last readable line of its id ({@link WaiverLog}).
 * <p>
 * Two rules tie components together, as the schema of the record states them: a {@link Event#WAIVED} line names its
 * gate, its mode and the class of its reason, and a {@link Mode#RAISE} carries the new ceiling as {@link #value()},
 * which no other mode carries.
 *
 * @param event       whether the line sets or revokes the waiver
 * @param waiverId    the id of the waiver, {@code W-} and digits
 * @param gate        the waivable gate or budget; required on a waived line, {@code null} where absent
 * @param mode        whether the waiver declares the gate met or raises a ceiling; required on a waived line,
 *                    {@code null} where absent
 * @param value       the new absolute ceiling of a raise, at least 0; {@code null} for any other mode
 * @param reason      the operator's reason, 1 to 2000 characters
 * @param reasonClass the operator's class of the reason; required on a waived line, {@code null} where absent
 * @param requestId   the waiver request the act answers, {@code WR-} and digits; {@code null} if it answers none
 * @param operator    who acted, and through which channel
 * @param at          when the waiver was set or revoked
 * @since 0.1
 */
@Builder(toBuilder = true)
public record WaiverRecord(Event event, String waiverId, String gate, Mode mode, Long value, String reason,
ReasonClass reasonClass, String requestId, Operator operator, Instant at) {

    /** The format version of a waiver record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The longest reason a waiver record holds. */
    public static final int MAX_REASON_LENGTH = 2000;

    private static final Pattern WAIVER_ID = Pattern.compile("W-\\d+");
    private static final Pattern REQUEST_ID = Pattern.compile("WR-\\d+");
    private static final Pattern DEVICE_ID = Pattern.compile("dev-[0-9a-f]{12}");

    /**
     * What the line does to the waiver.
     *
     * @since 0.1
     */
    public enum Event {
        /** The waiver is set. */
        WAIVED("waived"),
        /** The waiver is revoked. */
        REVOKED("revoked");

        private final String wireName;

        Event(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Event> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * What the waiver does to its gate or budget.
     *
     * @since 0.1
     */
    public enum Mode {
        /** The gate is declared met. */
        MET("met"),
        /** The ceiling of a remediation budget is raised. */
        RAISE("raise");

        private final String wireName;

        Mode(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Mode> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The operator's class of the reason of a waiver.
     *
     * @since 0.1
     */
    public enum ReasonClass {
        /** The gate failed on something that is no defect. */
        GATE_FALSE_POSITIVE("gate_false_positive"),
        /** The gate does not apply to the change. */
        GATE_NOT_APPLICABLE("gate_not_applicable"),
        /** The tool the gate needs is unavailable. */
        TOOLING_UNAVAILABLE("tooling_unavailable"),
        /** The operator defers the work on purpose. */
        DELIBERATE_DEFERRAL("deliberate_deferral"),
        /** The operator gave no class; the default. */
        UNKNOWN("unknown");

        private final String wireName;

        ReasonClass(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<ReasonClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The client through which the operator acted.
     *
     * @since 0.1
     */
    public enum Channel {
        /** The terminal client. */
        CLI("cli"),
        /** A paired device of the web app. */
        WEB("web");

        private final String wireName;

        Channel(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Channel> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Who acted, and through which channel.
     *
     * @param channel     the client of the act
     * @param deviceId    the paired device, {@code dev-} and 12 hex digits; {@code null} where there is none
     * @param osPrincipal the operating system principal of the operator
     * @since 0.1
     */
    public record Operator(Channel channel, String deviceId, String osPrincipal) {

        /**
         * @throws NullPointerException     if the channel or the principal is {@code null}
         * @throws IllegalArgumentException if the device id breaks its form
         */
        public Operator {
            Objects.requireNonNull(channel, "channel");
            requireDeviceId(deviceId);
            Objects.requireNonNull(osPrincipal, "osPrincipal");
        }
    }

    /**
     * @throws NullPointerException     if the event, the waiver id, the reason, the operator or the instant is
     *                                  {@code null}
     * @throws IllegalArgumentException if a value breaks its form, if a waived line lacks its gate, mode or reason
     *                                  class, or if the value is missing for a raise or present for any other mode
     */
    public WaiverRecord {
        Objects.requireNonNull(event, "event");
        requireMatch(WAIVER_ID, Objects.requireNonNull(waiverId, "waiverId"), "waiver id (W- and digits)");
        if (gate != null && gate.isEmpty()) {
            throw new IllegalArgumentException("The gate of a waiver record is empty");
        }
        Objects.requireNonNull(reason, "reason");
        var reasonLength = reason.codePointCount(0, reason.length());
        if (reasonLength == 0 || reasonLength > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("The reason of a waiver record has 1 to %d characters, not %d"
                    .formatted(MAX_REASON_LENGTH, reasonLength));
        }
        if (requestId != null) {
            requireMatch(REQUEST_ID, requestId, "waiver request id (WR- and digits)");
        }
        Objects.requireNonNull(operator, "operator");
        Objects.requireNonNull(at, "at");
        if (event == Event.WAIVED && (gate == null || mode == null || reasonClass == null)) {
            throw new IllegalArgumentException("A waived line names its gate, its mode and its reason class");
        }
        requireValue(mode, value);
    }

    private static void requireValue(Mode mode, Long value) {
        if (mode == Mode.RAISE) {
            if (value == null) {
                throw new IllegalArgumentException("A raise carries its new ceiling as value");
            }
            if (value < 0) {
                throw new IllegalArgumentException("The ceiling of a raise is at least 0, not " + value);
            }
        } else if (value != null) {
            throw new IllegalArgumentException("Only a raise carries a value: " + value);
        }
    }

    /**
     * @param deviceId a device id; {@code null} where there is none
     * @throws IllegalArgumentException if the device id is present and breaks its form
     */
    static void requireDeviceId(String deviceId) {
        if (deviceId != null) {
            requireMatch(DEVICE_ID, deviceId, "device id (dev- and 12 hex digits)");
        }
    }

    private static void requireMatch(Pattern pattern, String value, String name) {
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a %s: %s".formatted(name, value));
        }
    }
}
