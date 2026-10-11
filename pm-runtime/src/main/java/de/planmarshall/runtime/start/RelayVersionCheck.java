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

import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * Detects a relay whose version differs from the version of the runtime
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Upgrade). A relay of another version forwards
 * normally; the runtime compares the relay's {@value #HEADER} header with its own version.
 * <p>
 * A mismatch is operator-only ({@code PM-DIST-4}): it is handed to the audit sink as a {@value #RELAY_VERSION_MISMATCH}
 * record with both versions, logged, and kept as {@link Mismatch} for {@code pm-operator status}, with the recommended
 * command {@value #RECOMMENDED_COMMAND}. Nothing of it reaches a representation: {@link #observe(String)} returns
 * nothing, and only the operator status reads {@link #mismatch()}.
 * <p>
 * The record and the log message are written once per distinct relay version per runtime instance, so a busy relay
 * does not flood the audit log. The detection state lives in this instance only and is not persisted: a new runtime
 * records a mismatch it observes again. The class is safe for concurrent use by the threads of the relayed requests.
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

    private final String runtimeVersion;
    private final AuditSink auditSink;
    private final Set<String> recorded = ConcurrentHashMap.newKeySet();
    private final AtomicReference<Mismatch> latest = new AtomicReference<>();

    /**
     * @param runtimeVersion the version of this runtime
     * @param auditSink      where a mismatch is handed over as a {@value #RELAY_VERSION_MISMATCH} record
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the runtime version is blank
     */
    public RelayVersionCheck(String runtimeVersion, AuditSink auditSink) {
        Objects.requireNonNull(runtimeVersion, "runtimeVersion");
        if (runtimeVersion.isBlank()) {
            throw new IllegalArgumentException("runtimeVersion must not be blank");
        }
        this.runtimeVersion = runtimeVersion.strip();
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    /**
     * Compares the version a relayed request names in its {@value #HEADER} header with the runtime's. The assembly
     * calls it for every relayed request; a request without the header, or with a blank one, is ignored, and so is an
     * equal version.
     * <p>
     * On a mismatch the latest mismatch is updated. The first request that names a given relay version in this
     * instance hands the {@value #RELAY_VERSION_MISMATCH} record to the audit sink and logs the mismatch; every later
     * request with the same version does neither. If the audit sink refuses the record, the version is forgotten
     * again, so the next request with it hands the record over anew.
     *
     * @param relayVersion the value of the {@value #HEADER} header; {@code null} if the request carries none
     */
    public void observe(String relayVersion) {
        if (relayVersion == null || relayVersion.isBlank()) {
            return;
        }
        var version = relayVersion.strip();
        if (version.equals(runtimeVersion)) {
            return;
        }
        latest.set(new Mismatch(version, runtimeVersion, RECOMMENDED_COMMAND));
        if (recorded.add(version)) {
            LOGGER.warn(WARN.RELAY_VERSION_MISMATCH, version, runtimeVersion);
            record(version);
        }
    }

    /**
     * @return the latest mismatch this instance observed, for {@code pm-operator status}; empty while every relay
     *         named the runtime's own version or none
     */
    public Optional<Mismatch> mismatch() {
        return Optional.ofNullable(latest.get());
    }

    private void record(String version) {
        var event = new AuditEvent(RELAY_VERSION_MISMATCH, CATEGORY_OPERATOR, OUTCOME_WARNING,
                Map.of(DETAIL_RELAY_VERSION, version, DETAIL_RUNTIME_VERSION, runtimeVersion));
        try {
            auditSink.append(event);
        } catch (UncheckedIOException e) {
            recorded.remove(version);
            LOGGER.debug(e, "Audit sink refused the %s record of relay version '%s'; the next request retries",
                    RELAY_VERSION_MISMATCH, version);
        }
    }
}
