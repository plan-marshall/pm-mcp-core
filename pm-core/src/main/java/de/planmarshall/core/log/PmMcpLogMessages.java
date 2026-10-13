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

        // WS-02, enrolment store: identifiers 60-69

        /** Logged when an enrolment file and its sidecar are written by {@code enrol}. */
        public static final LogRecord ENROLMENT_WRITTEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(60)
                .template("Enrolment of project '%s' written (pilot: %s)")
                .build();

        /** Logged when an enrolment file and its sidecar are removed by {@code unenrol}; the project store stays. */
        public static final LogRecord ENROLMENT_REMOVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(61)
                .template("Enrolment of project '%s' removed, its project store kept")
                .build();

        /** Logged when the sidecar of an enrolment is rewritten over the current file by {@code accept-edit}. */
        public static final LogRecord ENROLMENT_REBASELINED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(62)
                .template("Enrolment of project '%s' re-baselined")
                .build();

        /**
         * Logged when a record that an earlier enrolment write owed (its hand-over was refused or stopped) is handed
         * over by a later enrolment operation or by the recovery sweep.
         */
        public static final LogRecord ENROLMENT_AUDIT_REPLAYED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(63)
                .template("Owed audit record '%s' of transaction '%s' for project '%s' handed over after an earlier refusal or stop")
                .build();

        /**
         * Logged when the recovery of the enrolment pair completes a write that was stopped after the enrolment file
         * and its sidecar were staged completely: the new pair is in place.
         */
        public static final LogRecord ENROLMENT_PAIR_COMPLETED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(64)
                .template("Stopped write of the enrolment of project '%s' completed by the recovery: transaction '%s'")
                .build();

        // WS-02, session log and generations: identifiers 65-69

        /**
         * Logged when the periodic sweep removed the pending entry records of a project older than the pending entry
         * record maximum age, each with its {@code pending_entry_removed} audit record.
         */
        public static final LogRecord PENDING_ENTRY_RECORDS_SWEPT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(65)
                .template("Periodic sweep removed %s pending entry records of project '%s'")
                .build();

        /**
         * Logged when the periodic sweep removed the entries of a project's session generation record fenced before
         * the pending entry record maximum age.
         */
        public static final LogRecord FENCED_SESSION_GENERATIONS_SWEPT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(66)
                .template("Periodic sweep removed %s fenced session generations of project '%s'")
                .build();

        // WS-03, project units and invariants: identifiers 70-79

        /**
         * Logged when every unit of a project has been loaded and verified (PM-IMPL-5), with the project id and the
         * number of verified units.
         */
        public static final LogRecord PROJECT_UNITS_VERIFIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(70)
                .template("Units of project '%s' verified: %s units")
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
         * durable intent and is handed over again by the next recovery or repair. It covers an enrolment write as well,
         * whose record stays owed in the intent {@code <project_id>.json.audit-intent} and is handed over by the next
         * enrolment operation of the project or the recovery sweep.
         */
        public static final LogRecord AUDIT_RECORD_DEFERRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(160)
                .template("Audit record '%s' of transaction '%s' not handed over, kept owed in '%s': %s")
                .build();

        // WS-02, enrolment store: identifiers 161-169

        /** Logged when an enrolment is refused because it does not match its sidecar; only its project is blocked. */
        public static final LogRecord ENROLMENT_TAMPERED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(161)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_tampered': %s")
                .build();

        /** Logged when an enrolment file or its sidecar fails a per-use check; only its project is blocked. */
        public static final LogRecord ENROLMENT_INSECURE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(162)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_insecure': %s")
                .build();

        /** Logged when {@code unenrol} is refused because the project has live work; names the items. */
        public static final LogRecord UNENROL_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(163)
                .template("Unenrol of project '%s' refused with outcome 'scope_active', live work: %s")
                .build();

        /**
         * Logged when the owed audit record of an enrolment write that was started and not confirmed complete (the
         * write failed, or the process stopped before or after it took effect) is handed over, as outcome
         * {@code failed} with reason {@code not_confirmed}; the enrolment file and its sidecar show what became of the
         * write.
         */
        public static final LogRecord ENROLMENT_WRITE_NOT_CONFIRMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(164)
                .template("Audit record '%s' of transaction '%s' for project '%s' handed over as not confirmed: "
                        + "the write did not complete or its completion was not recorded")
                .build();

        /**
         * Logged when the runtime refuses to sign or use an enrolment that violates the enrolment rules; only its
         * project is blocked.
         */
        public static final LogRecord ENROLMENT_INVALID = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(165)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_invalid': %s")
                .build();

        /**
         * Logged when the recovery of the enrolment pair discards a staged write that was stopped before the enrolment
         * file and its sidecar were staged completely, or whose staged sidecar does not match the file; the enrolment
         * is as it was before the write.
         */
        public static final LogRecord ENROLMENT_PAIR_DISCARDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(166)
                .template("Incomplete staged write of the enrolment of project '%s' discarded, "
                        + "the enrolment is as it was before the write: %s")
                .build();

        /**
         * Logged when the background replay of the audit intents of projects that are no longer enrolled meets an
         * intent it cannot read or hand over; the intent is kept.
         */
        public static final LogRecord ENROLMENT_AUDIT_REPLAY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(167)
                .template("Replay of the owed audit records of unenrolled projects failed: %s")
                .build();

        // WS-03, project units and invariants: identifiers 170-179

        /**
         * Logged for every finding that refuses a unit of a project (PM-IMPL-5), with the unit file, the line and the
         * rule or invariant id; the plan lifecycle is refused with {@code config_invalid}, and no default unit stands
         * in for the refused one.
         */
        public static final LogRecord PROJECT_UNIT_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(170)
                .template("Project unit '%s' refused at line %s by '%s', the plan lifecycle is refused with 'config_invalid'")
                .build();

        // WS-04, verify-not-resolve: identifiers from 180

        /**
         * Logged when the verify-not-resolve pipeline refuses a job, with the project id, the reason code and the
         * detail; the detail carries the reason detail and changed paths only, never an argument vector value or a
         * secret.
         */
        public static final LogRecord JOB_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(180)
                .template("Job of project '%s' refused with reason '%s': %s")
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

        /**
         * Logged when a scheduled run of the periodic sweep ends with a failure outside its steps; the schedule stays
         * alive and the next run takes place at the interval.
         */
        public static final LogRecord PERIODIC_SWEEP_RUN_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(253)
                .template("Periodic sweep run failed, the next run takes place at the interval: %s")
                .build();

        // WS-02, enrolment store: identifiers 260-269

        /** Logged when the enrolment integrity key cannot be read or created; never contains the key. */
        public static final LogRecord ENROLMENT_KEY_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(260)
                .template("Enrolment integrity key unavailable from '%s': %s")
                .build();

        // WS-03, project units and invariants: identifiers 270-279

        /**
         * Logged when a default unit read from the classpath fails to parse or verify (PM-IMPL-5), with the resource,
         * the line and the rule; a default unit that does not verify is a defect of the build.
         */
        public static final LogRecord DEFAULT_UNIT_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(270)
                .template("Default unit '%s' refused at line %s by '%s'")
                .build();
    }
}
