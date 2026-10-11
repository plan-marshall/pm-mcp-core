/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.log;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages of the PM-MCP server.
 * <p>
 * Identifier ranges: INFO 001-099, WARN 100-199, ERROR 200-299.
 *
 * @since 0.1
 */
@UtilityClass
public final class PmMcpLogMessages {

    /** Prefix of all PM-MCP log messages. */
    public static final String PREFIX = "PM_MCP";

    /** INFO level messages. */
    @UtilityClass
    public static final class INFO {

        /** Logged when the {@code hello} tool is invoked. */
        public static final LogRecord HELLO_TOOL_INVOKED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Hello tool invoked for '%s'")
                .build();

        // Local adapter and MCP surface: identifiers 10-29

        /** Logged when the socket is bound, secured and recorded (startup step 7). */
        public static final LogRecord RUNTIME_READY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(10)
                .template("Runtime ready on socket '%s' (pid %s)")
                .build();

        /** Logged when the web listener has opened. */
        public static final LogRecord WEB_LISTENER_OPENED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(11)
                .template("Web listener open on %s:%s (lan: %s)")
                .build();

        /** Logged when the web listener has closed. */
        public static final LogRecord WEB_LISTENER_CLOSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(12)
                .template("Web listener on port %s closed")
                .build();

        /** Logged when a local client asks the runtime to stop. */
        public static final LogRecord RUNTIME_STOP_REQUESTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(13)
                .template("Runtime stop requested through the local API")
                .build();

        /** Logged when the core tool surface is registered. */
        public static final LogRecord CORE_TOOLS_REGISTERED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(14)
                .template("Core tools registered: %s")
                .build();

        /** Logged for every attempt to open the web listener, with the request and what caused it. */
        public static final LogRecord WEB_LISTENER_OPEN_REQUESTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(15)
                .template("Web listener open requested on %s:%s (lan: %s), cause: %s")
                .build();

        /** Logged before the web listener closes, with what caused the close. */
        public static final LogRecord WEB_LISTENER_CLOSING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(17)
                .template("Web listener on port %s closing, cause: %s")
                .build();

        /** Logged at start with the one active credential backend and the keyring service name. */
        public static final LogRecord CREDENTIAL_STORE_SELECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(30)
                .template("Credential store '%s' active, service name '%s'")
                .build();

        /** Logged when a language server of the LSP pool answered {@code initialize}. */
        public static final LogRecord LSP_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(40)
                .template("Language server '%s' started (pid %s, position encoding %s)")
                .build();

        // WS-01, locks and leases: identifiers from 50

        /** Logged when the stale lease sweep of a lease store has finished, with what it did to the leases. */
        public static final LogRecord STALE_LEASE_SWEEP_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(50)
                .template("Stale lease sweep of '%s' finished: %s kept, %s orphaned, %s removed")
                .build();

        // WS-01, per-user service unit: identifiers from 51

        /** Logged when the per-user service unit is written and loaded, with the unit path and the daemon path. */
        public static final LogRecord SERVICE_UNIT_INSTALLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(51)
                .template("Service unit '%s' installed for daemon '%s'")
                .build();

        /** Logged when the per-user service unit is removed and unloaded, also when no unit was present. */
        public static final LogRecord SERVICE_UNIT_UNINSTALLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(52)
                .template("Service unit '%s' uninstalled")
                .build();

        // WS-01, runtime lifecycle: identifiers from 53

        /** Logged when the bounded verification (startup step 4) has finished, with the audit chain outcome. */
        public static final LogRecord BOUNDED_VERIFICATION_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(53)
                .template("Bounded verification finished: audit chain %s")
                .build();

        /** Logged when the runtime drain starts, with the drain timeout in seconds. */
        public static final LogRecord RUNTIME_DRAIN_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(54)
                .template("Runtime drain started, drain timeout %s seconds")
                .build();

        /** Logged when the runtime drain has ended, with what was ended and what was terminated. */
        public static final LogRecord RUNTIME_DRAINED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(55)
                .template("Runtime drained: %s ended, %s terminated, exit code %s")
                .build();

        /** Logged when the recovery of a project has finished, with the project id and the step reports. */
        public static final LogRecord PROJECT_RECOVERY_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(56)
                .template("Recovery of project '%s' finished: %s")
                .build();

        /** Logged when a run of the periodic sweep has finished, with the projects swept, skipped and failed. */
        public static final LogRecord PERIODIC_SWEEP_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(57)
                .template("Periodic sweep finished: %s projects swept, %s skipped, %s failed")
                .build();
    }

    /** WARN level messages. */
    @UtilityClass
    public static final class WARN {

        // Local adapter and MCP surface: identifiers 110-129

        /** Logged when the socket did not have mode 0600 after the bind and was restricted. */
        public static final LogRecord SOCKET_MODE_RESTRICTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(110)
                .template("Socket '%s' had mode %s after the bind and was set to 0600")
                .build();

        /** Logged when a core tool is not registered because another component registered the name. */
        public static final LogRecord CORE_TOOL_SHADOWED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(111)
                .template("Core tool '%s' not registered: the name is already registered")
                .build();

        /** Logged when the web listener cannot open. */
        public static final LogRecord WEB_LISTENER_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(112)
                .template("Web listener could not open on port %s: %s")
                .build();

        /** Logged when a connection to the web listener fails before its first request, a failed TLS handshake included. */
        public static final LogRecord WEB_LISTENER_CONNECTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(113)
                .template("Web listener on port %s: connection failed before a request (TLS handshake or connection setup): %s")
                .build();

        /** Logged at start when no OS keyring is usable and the file credential store serves. */
        public static final LogRecord KEYRING_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(130)
                .template("No OS keyring usable, the file credential store serves: %s")
                .build();

        /** Logged when a language server does not end cleanly on {@code shutdown} / {@code exit}. */
        public static final LogRecord LSP_SHUTDOWN_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(140)
                .template("Language server (pid %s) did not end cleanly, terminating it: %s")
                .build();

        // Workspace and enrolment (WS-01): identifiers 150-159

        /** Logged when a project path is refused by the workspace confinement; names the outcome code and the reason, never the path. */
        public static final LogRecord PROJECT_PATH_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(150)
                .template("Project path refused with outcome '%s': %s")
                .build();

        /** Logged when a lease is marked orphaned because the runtime that served it is no longer alive. */
        public static final LogRecord LEASE_ORPHANED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(151)
                .template("Lease '%s' in '%s' orphaned: its holder (pid %s) is no longer alive")
                .build();

        // WS-01, runtime lifecycle: identifiers from 152

        /** Logged when the bounded verification finds the audit chain broken; the start goes on. */
        public static final LogRecord AUDIT_CHAIN_BROKEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(152)
                .template("Audit chain broken, the start goes on: %s")
                .build();

        /** Logged when the drain timeout expires and the runtime terminates what still runs. */
        public static final LogRecord DRAIN_TIMEOUT_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(153)
                .template("Drain timeout of %s seconds expired, terminating what still runs")
                .build();

        /** Logged when {@code drain_timeout_seconds} is negative or not an integer, and the default is used. */
        public static final LogRecord DRAIN_TIMEOUT_INVALID = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(154)
                .template("drain_timeout_seconds in '%s' is invalid (%s), using the default of %s seconds")
                .build();

        /** Logged when a call waited the project recovery wait in vain; the recovery continues. */
        public static final LogRecord PROJECT_RECOVERY_WAIT_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(155)
                .template("Recovery of project '%s' not finished within %s, answering runtime_recovering")
                .build();

        /** Logged once per distinct relay version that differs from the runtime version. */
        public static final LogRecord RELAY_VERSION_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(156)
                .template("Relay version '%s' differs from runtime version '%s', recommended: pm-operator drain")
                .build();

        /** Logged when the audit sink refused the record of a broken audit chain; the start goes on. */
        public static final LogRecord AUDIT_CHAIN_BROKEN_NOT_RECORDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(157)
                .template("Record of the broken audit chain not handed over to the audit sink: %s")
                .build();

        /** Logged when a drain participant fails in a drain phase; the drain goes on and records the failure. */
        public static final LogRecord DRAIN_PARTICIPANT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(158)
                .template("Drain participant '%s' failed in %s: %s")
                .build();

        /** Logged when a step of the periodic sweep fails for a project; the run goes on with the other steps. */
        public static final LogRecord PERIODIC_SWEEP_STEP_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(159)
                .template("Periodic sweep of project '%s' failed in step '%s': %s")
                .build();

        // WS-02, store substrate: identifiers 160-169

        /**
         * Logged when the audit sink refused the record of a completed store mutation; the record stays owed in its
         * durable intent and is handed over again by the next recovery or repair.
         */
        public static final LogRecord AUDIT_RECORD_DEFERRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(160)
                .template("Audit record '%s' of transaction '%s' not handed over, kept owed in '%s': %s")
                .build();
    }

    /** ERROR level messages. */
    @UtilityClass
    public static final class ERROR {

        // Local adapter and MCP surface: identifiers 210-229

        /** Logged when the bound socket cannot be secured or recorded; the runtime exits with code 77. */
        public static final LogRecord SOCKET_NOT_SECURED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(210)
                .template("Socket '%s' could not be secured or recorded, exiting with code 77")
                .build();

        /** Logged when a credential store operation of the local API fails; never contains the secret. */
        public static final LogRecord CREDENTIAL_STORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(230)
                .template("Credential store '%s' failed: %s")
                .build();

        // WS-01, locks and leases: identifiers from 250

        /** Logged when a lock acquisition against the total lock order is refused; nothing was acquired. */
        public static final LogRecord LOCK_ORDER_VIOLATION = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(250)
                .template("Lock order violation: '%s' requested while holding %s")
                .build();

        // WS-01, per-user service unit: identifiers from 251

        /** Logged when a service-manager command fails; names the command and the exit code or failure reason, never an environment value. */
        public static final LogRecord SERVICE_MANAGER_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(251)
                .template("Service manager command '%s' failed: %s")
                .build();

        // WS-01, runtime lifecycle: identifiers from 252

        /** Logged when a recovery step of a project fails; the recovery of that project stops, other projects go on. */
        public static final LogRecord PROJECT_RECOVERY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(252)
                .template("Recovery of project '%s' failed in step '%s': %s")
                .build();
    }
}
