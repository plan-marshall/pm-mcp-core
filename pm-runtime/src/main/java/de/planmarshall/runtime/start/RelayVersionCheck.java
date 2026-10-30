/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import de.cuioss.tools.logging.CuiLogger;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * Detects a relay whose version differs from the version of the runtime
 * (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Upgrade). A relay of another version forwards
 * normally; the runtime compares the relay's {@value #HEADER} header with its own version.
 * <p>
 * A mismatch is operator-only ({@code PM-DIST-4}): it is handed to the audit sink as a {@value #RELAY_VERSION_MISMATCH}
 * record with both versions, logged, and kept as {@link Mismatch} for {@code pm-operator status}, with the recommended
 * command {@value #RECOMMENDED_COMMAND}. Nothing of it reaches a representation: {@link #observe(String)} returns
 * nothing, and only the operator status reads {@link #mismatch()}.
 * <p>
 * The record and the log message are written once per distinct relay version per runtime instance, so a busy relay
 * does not flood the audit log. The header comes across a trust boundary, so what it can cost is bounded: a version
 * longer than {@value #MAX_VERSION_LENGTH} characters is truncated to that length, and at most
 * {@value #MAX_RECORDED_VERSIONS} distinct versions are kept and recorded; a further version is still reflected in
 * {@link #mismatch()}, but produces no audit record and no WARN. A record the audit sink refuses is handed over again,
 * and warned about again, by a later request with that version, but at most once per {@link #RETRY_INTERVAL}. The
 * detection state lives in this instance only and is not persisted: a new runtime records a mismatch it observes
 * again. The class is safe for concurrent use by the threads of the relayed requests: the decision to record a version,
 * the hand-over to the audit sink and the outcome are taken atomically per version, so a version is recorded once and a
 * refused record is never lost to a concurrent request.
 *
 * @since 0.1
 */
public final class RelayVersionCheck {

    private static final CuiLogger LOGGER = new CuiLogger(RelayVersionCheck.class);

    /** The request header by which a relay names its own version. */
    public static final String HEADER = "PM-MCP-Relay-Version";

    /** The name of the event that records a relay whose version differs from the runtime's. */
    public static final String RELAY_VERSION_MISMATCH = "relay_version_mismatch";

    /** The category of the {@value #RELAY_VERSION_MISMATCH} record: it concerns the operator alone. */
    public static final String CATEGORY_OPERATOR = "operator";

    /** The outcome of the {@value #RELAY_VERSION_MISMATCH} record: the relay forwards, the operator is warned. */
    public static final String OUTCOME_WARNING = "warning";

    /** The detail of the {@value #RELAY_VERSION_MISMATCH} record that holds the version of the relay. */
    public static final String DETAIL_RELAY_VERSION = "relay_version";

    /** The detail of the {@value #RELAY_VERSION_MISMATCH} record that holds the version of the runtime. */
    public static final String DETAIL_RUNTIME_VERSION = "runtime_version";

    /** The command recommended to the operator on a mismatch: it stops the old runtime. */
    public static final String RECOMMENDED_COMMAND = "pm-operator drain";

    /** The longest relay version taken into account; a longer header value is truncated to this many characters. */
    public static final int MAX_VERSION_LENGTH = 64;

    /** The most distinct relay versions kept and recorded per runtime instance. */
    public static final int MAX_RECORDED_VERSIONS = 32;

    /** The shortest time between two attempts to hand over the record of one version the audit sink refused. */
    public static final Duration RETRY_INTERVAL = Duration.ofMinutes(1);

    /**
     * The latest mismatch, as {@code pm-operator status} shows it.
     *
     * @param relayVersion       the version the relay named
     * @param runtimeVersion     the version of this runtime
     * @param recommendedCommand the command recommended to the operator, {@value #RECOMMENDED_COMMAND}
     */
    public record Mismatch(String relayVersion, String runtimeVersion, String recommendedCommand) {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Mismatch {
            Objects.requireNonNull(relayVersion, "relayVersion");
            Objects.requireNonNull(runtimeVersion, "runtimeVersion");
            Objects.requireNonNull(recommendedCommand, "recommendedCommand");
        }
    }

    /** What became of the record of one version: handed over, or refused by the audit sink at an instant. */
    private sealed interface Handover {

        /** The record was handed over; nothing more is done for the version. */
        record Recorded() implements Handover {
        }

        /**
         * The audit sink refused the record.
         *
         * @param at the instant of the refused attempt
         */
        record Refused(Instant at) implements Handover {
        }
    }

    private static final Handover RECORDED = new Handover.Recorded();

    private final String runtimeVersion;
    private final AuditSink auditSink;
    private final Clock clock;
    private final ConcurrentMap<String, Handover> versions = new ConcurrentHashMap<>();
    private final AtomicInteger slots = new AtomicInteger();
    private final AtomicReference<Mismatch> latest = new AtomicReference<>();

    /**
     * A check on the system clock.
     *
     * @param runtimeVersion the version of this runtime
     * @param auditSink      where a mismatch is handed over as a {@value #RELAY_VERSION_MISMATCH} record
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the runtime version is blank
     */
    public RelayVersionCheck(String runtimeVersion, AuditSink auditSink) {
        this(runtimeVersion, auditSink, Clock.systemUTC());
    }

    /**
     * @param runtimeVersion the version of this runtime
     * @param auditSink      where a mismatch is handed over as a {@value #RELAY_VERSION_MISMATCH} record
     * @param clock          the clock of the {@link #RETRY_INTERVAL}
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the runtime version is blank
     */
    public RelayVersionCheck(String runtimeVersion, AuditSink auditSink, Clock clock) {
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        if (runtimeVersion.isBlank()) {
            throw new IllegalArgumentException("runtimeVersion must not be blank");
        }
        this.runtimeVersion = runtimeVersion.strip();
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Compares the version a relayed request names in its {@value #HEADER} header with the runtime's. The assembly
     * calls it for every relayed request; a request without the header, or with a blank one, is ignored, and so is an
     * equal version.
     * <p>
     * On a mismatch the latest mismatch is updated. The first request that names a given relay version in this
     * instance hands the {@value #RELAY_VERSION_MISMATCH} record to the audit sink and logs the mismatch; every later
     * request with the same version does neither. If the audit sink refuses the record, a later request with the
     * version hands it over anew, once the {@link #RETRY_INTERVAL} since the refused attempt has passed. A version
     * beyond the first {@value #MAX_RECORDED_VERSIONS} distinct ones is not recorded.
     * <p>
     * The hand-over runs while the version's entry is held, so a concurrent request with the same version waits for
     * it; the audit sink is synchronous by contract.
     *
     * @param relayVersion the value of the {@value #HEADER} header; {@code null} if the request carries none
     */
    public void observe(@Nullable String relayVersion) {
        if (relayVersion == null || relayVersion.isBlank()) {
            return;
        }
        var stripped = relayVersion.strip();
        var version = stripped.length() > MAX_VERSION_LENGTH ? stripped.substring(0, MAX_VERSION_LENGTH) : stripped;
        if (version.equals(runtimeVersion)) {
            return;
        }
        latest.set(new Mismatch(version, runtimeVersion, RECOMMENDED_COMMAND));
        versions.compute(version, (key, handover) -> switch (handover) {
            case Handover.Recorded recorded -> recorded;
            case Handover.Refused refused when clock.instant().isBefore(refused.at().plus(RETRY_INTERVAL)) -> refused;
            case Handover.Refused _ -> recordMismatch(key);
            case null -> slots.incrementAndGet() <= MAX_RECORDED_VERSIONS
                    ? recordMismatch(key)
                    : untracked(key).orElse(null);
        });
    }

    /**
     * @return the latest mismatch this instance observed, for {@code pm-operator status}; empty while every relay
     *         named the runtime's own version or none
     */
    public Optional<Mismatch> mismatch() {
        return Optional.ofNullable(latest.get());
    }

    /** Logs the mismatch and hands its record to the audit sink; called while the version's entry is held. */
    private Handover recordMismatch(String version) {
        LOGGER.warn(WARN.RELAY_VERSION_MISMATCH, version, runtimeVersion);
        var event = new AuditEvent(RELAY_VERSION_MISMATCH, CATEGORY_OPERATOR, OUTCOME_WARNING,
                Map.of(DETAIL_RELAY_VERSION, version, DETAIL_RUNTIME_VERSION, runtimeVersion));
        try {
            auditSink.append(event);
            return RECORDED;
        } catch (UncheckedIOException e) {
            LOGGER.debug(e, "Audit sink refused the %s record of relay version '%s'; retried after %s",
                    RELAY_VERSION_MISMATCH, version, RETRY_INTERVAL);
            return new Handover.Refused(clock.instant());
        }
    }

    /** A version beyond the bound: it keeps no entry and is not recorded, so its hand-over is empty. */
    private Optional<Handover> untracked(String version) {
        slots.decrementAndGet();
        LOGGER.debug("Relay version '%s' not recorded: %s distinct relay versions recorded already", version,
                MAX_RECORDED_VERSIONS);
        return Optional.empty();
    }
}
