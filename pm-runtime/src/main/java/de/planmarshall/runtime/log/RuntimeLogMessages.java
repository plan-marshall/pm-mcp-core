/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.log;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages of the module {@code pm-runtime}.
 * <p>
 * Identifier ranges: INFO 001-099, WARN 100-199, ERROR 200-299. A new message takes the next free identifier of
 * its level.
 *
 * @since 0.1
 */
@UtilityClass
public final class RuntimeLogMessages {

    /** Prefix of all log messages of {@code pm-runtime}. */
    public static final String PREFIX = "PM_RUNTIME";

    /** INFO level messages. */
    @UtilityClass
    public static final class INFO {

        /**
         * Logged when the {@code hello} tool is invoked.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord HELLO_TOOL_INVOKED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Hello tool invoked for '%s'")
                .build();

        /**
         * Logged when the socket is bound, secured and recorded (startup step 7).
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord RUNTIME_READY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(2)
                .template("Runtime ready on socket '%s' (pid %s)")
                .build();

        /**
         * Logged when the web listener has opened.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_OPENED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(3)
                .template("Web listener open on %s:%s (lan: %s)")
                .build();

        /**
         * Logged when the web listener has closed.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_CLOSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(4)
                .template("Web listener on port %s closed")
                .build();

        /**
         * Logged when a local client asks the runtime to stop.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord RUNTIME_STOP_REQUESTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(5)
                .template("Runtime stop requested through the local API")
                .build();

        /**
         * Logged when the core tool surface is registered.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CORE_TOOLS_REGISTERED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(6)
                .template("Core tools registered: %s")
                .build();

        /**
         * Logged for every attempt to open the web listener, with the request and what caused it.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_OPEN_REQUESTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(7)
                .template("Web listener open requested on %s:%s (lan: %s), cause: %s")
                .build();

        /**
         * Logged before the web listener closes, with what caused the close.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_CLOSING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(8)
                .template("Web listener on port %s closing, cause: %s")
                .build();

        /**
         * Logged at start with the credential store the operator recorded ({@code os}, {@code file}, or
         * {@code none} before the choice) and the keyring service name.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CREDENTIAL_STORE_SELECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(9)
                .template("Credential store '%s' recorded, service name '%s'")
                .build();

        /** Logged when a language server of the LSP pool answered {@code initialize}. */
        public static final LogRecord LSP_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(10)
                .template("Language server '%s' started (pid %s, position encoding %s)")
                .build();

        /** Logged when the stale lease sweep of a lease store has finished, with what it did to the leases. */
        public static final LogRecord STALE_LEASE_SWEEP_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(11)
                .template("Stale lease sweep of '%s' finished: %s kept, %s orphaned, %s removed")
                .build();

        /** Logged when the per-user service unit is written and loaded, with the unit path and the daemon path. */
        public static final LogRecord SERVICE_UNIT_INSTALLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(12)
                .template("Service unit '%s' installed for daemon '%s'")
                .build();

        /** Logged when the per-user service unit is removed and unloaded, also when no unit was present. */
        public static final LogRecord SERVICE_UNIT_UNINSTALLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(13)
                .template("Service unit '%s' uninstalled")
                .build();

        /** Logged when the bounded verification (startup step 4) has finished, with the audit chain outcome. */
        public static final LogRecord BOUNDED_VERIFICATION_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(14)
                .template("Bounded verification finished: audit chain %s")
                .build();

        /** Logged when the runtime drain starts, with the drain timeout in seconds. */
        public static final LogRecord RUNTIME_DRAIN_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(15)
                .template("Runtime drain started, drain timeout %s seconds")
                .build();

        /** Logged when the runtime drain has ended, with what was ended and what was terminated. */
        public static final LogRecord RUNTIME_DRAINED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(16)
                .template("Runtime drained: %s ended, %s terminated, exit code %s")
                .build();

        /** Logged when the recovery of a project has finished, with the project id and the step reports. */
        public static final LogRecord PROJECT_RECOVERY_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(17)
                .template("Recovery of project '%s' finished: %s")
                .build();

        /** Logged when a run of the periodic sweep has finished, with the projects swept, skipped and failed. */
        public static final LogRecord PERIODIC_SWEEP_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(18)
                .template("Periodic sweep finished: %s projects swept, %s skipped, %s failed")
                .build();

        /** Logged when an enrolment file and its sidecar are written by {@code enrol}. */
        public static final LogRecord ENROLMENT_WRITTEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(19)
                .template("Enrolment of project '%s' written (pilot: %s)")
                .build();

        /** Logged when an enrolment file and its sidecar are removed by {@code unenrol}; the project store stays. */
        public static final LogRecord ENROLMENT_REMOVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(20)
                .template("Enrolment of project '%s' removed, its project store kept")
                .build();

        /** Logged when the sidecar of an enrolment is rewritten over the current file by {@code accept-edit}. */
        public static final LogRecord ENROLMENT_REBASELINED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(21)
                .template("Enrolment of project '%s' re-baselined")
                .build();

        /**
         * Logged when a record that an earlier enrolment write owed (its hand-over was refused or stopped) is handed
         * over by a later enrolment operation or by the recovery sweep.
         */
        public static final LogRecord ENROLMENT_AUDIT_REPLAYED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(22)
                .template("Owed audit record '%s' of transaction '%s' for project '%s' handed over after an earlier refusal or stop")
                .build();

        /**
         * Logged when the periodic sweep removed the pending entry records of a project older than the pending entry
         * record maximum age, each with its {@code pending_entry_removed} audit record.
         */
        public static final LogRecord PENDING_ENTRY_RECORDS_SWEPT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(23)
                .template("Periodic sweep removed %s pending entry records of project '%s'")
                .build();

        /**
         * Logged when the audit log was rotated: closed by its checkpoint record, named by an entry of the audit
         * index and renamed, with the name of the rotated file and its number of records, the checkpoint included.
         */
        public static final LogRecord AUDIT_LOG_ROTATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(25)
                .template("Audit log rotated to '%s' with %s records")
                .build();

        /**
         * Logged by {@code CredentialStoreChoice} when {@code credentials.store} of {@code machine-config.json} is
         * written, with the store, the writer (the credential-store aspect or the store move) and the new revision.
         */
        public static final LogRecord CREDENTIAL_STORE_RECORDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(26)
                .template("Credential store '%s' recorded by %s, machine configuration revision %s")
                .build();

        /**
         * Logged by {@code CredentialStoreMove} when a move between the credential stores starts, after its record
         * {@code state/credential-store-move.json} was written, with the old and the new store.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVE_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(27)
                .template("Credential store move from '%s' to '%s' started")
                .build();

        /**
         * Logged by {@code CredentialStoreMove} when a move between the credential stores has ended: the new store is
         * recorded, the old one is empty and the record is deleted; with the old and the new store and the number of
         * Token Lane entries moved (the integrity key not counted). Never names an entry's secret.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(28)
                .template("Credential store moved from '%s' to '%s', %s entries")
                .build();

        /**
         * Logged by {@code CredentialStoreMoveResume} after the start completed a move between the credential stores
         * that a stop cut, with the old and the new store and the phase of the record it was resumed from
         * ({@code copying}: started again from the first entry; {@code switched}: the deletion from the old store
         * finished).
         */
        public static final LogRecord CREDENTIAL_STORE_MOVE_RESUMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(29)
                .template("Credential store move from '%s' to '%s' resumed from phase '%s' and completed")
                .build();

        /**
         * Logged when the record of a job carries its PID and process start (the post-spawn replace), from which on the
         * job counts as running; names the job, its kind, its PID and its budget in seconds.
         */
        public static final LogRecord JOB_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(30)
                .template("Job '%s' of kind '%s' started (pid %s, budget %s seconds)")
                .build();

        /** Logged when the fate of a job is recorded; names the job, its fate and the row of the outcome derivation. */
        public static final LogRecord JOB_SETTLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(31)
                .template("Job '%s' settled with fate '%s' (outcome row %s)")
                .build();

        /**
         * Logged by {@code CredentialStoreMoveResume} after the start completed the first recording of a credential
         * store that a stop cut, with the store recorded, the store the key is deleted from and the phase of the record
         * it was resumed from ({@code copying}: recorded again; {@code switched}: the deletion of the key finished).
         */
        public static final LogRecord CREDENTIAL_STORE_FIRST_RECORDING_RESUMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(32)
                .template("First recording of credential store '%s' from '%s' resumed from phase '%s' and completed")
                .build();

        /**
         * Logged when a restore wrote the kept unit of a reinstall back and loaded it, or when the restore of a start
         * did so after an interrupted reinstall; names the unit file.
         */
        public static final LogRecord SERVICE_UNIT_RESTORED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(33)
                .template("Service unit '%s' restored and loaded from the copy a reinstall kept")
                .build();

        /**
         * Logged when a restore removes the new unit file instead of writing a kept one back, because the reinstall
         * found no unit installed; nothing is unloaded.
         */
        public static final LogRecord SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(34)
                .template("Service unit '%s' removed, the reinstall kept no previous unit")
                .build();
    }

    /** WARN level messages. */
    @UtilityClass
    public static final class WARN {

        /**
         * Logged when the socket did not have mode 0600 after the bind and was restricted.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord SOCKET_MODE_RESTRICTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Socket '%s' had mode %s after the bind and was set to 0600")
                .build();

        /**
         * Logged when a core tool is not registered because another component registered the name.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CORE_TOOL_SHADOWED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Core tool '%s' not registered: the name is already registered")
                .build();

        /**
         * Logged when the web listener cannot open.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("Web listener could not open on port %s: %s")
                .build();

        /**
         * Logged when a connection to the web listener fails before its first request, a failed TLS handshake included.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_CONNECTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Web listener on port %s: connection failed before a request (TLS handshake or connection setup): %s")
                .build();

        /** Logged when a language server does not end cleanly on {@code shutdown} / {@code exit}. */
        public static final LogRecord LSP_SHUTDOWN_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(105)
                .template("Language server (pid %s) did not end cleanly, terminating it: %s")
                .build();

        /** Logged when a project path is refused by the workspace confinement; names the outcome code and the reason, never the path. */
        public static final LogRecord PROJECT_PATH_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(106)
                .template("Project path refused with outcome '%s': %s")
                .build();

        /** Logged when a lease is marked orphaned because the runtime that served it is no longer alive. */
        public static final LogRecord LEASE_ORPHANED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(107)
                .template("Lease '%s' in '%s' orphaned: its holder (pid %s) is no longer alive")
                .build();

        /**
         * Logged when the bounded verification finds the audit chain broken, with the reason by its wire name, the
         * first broken line, {@code none} for every reason other than {@code link_broken}, and the diagnostic, which
         * names the file the line is counted in and never a key or a hash secret; the start goes on.
         */
        public static final LogRecord AUDIT_CHAIN_BROKEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(108)
                .template("Audit chain broken (reason '%s', first broken line %s): %s; the start goes on")
                .build();

        /** Logged when the drain timeout expires and the runtime terminates what still runs. */
        public static final LogRecord DRAIN_TIMEOUT_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(109)
                .template("Drain timeout of %s seconds expired, terminating what still runs")
                .build();

        /** Logged when {@code drain_timeout_seconds} is negative or not an integer, and the default is used. */
        public static final LogRecord DRAIN_TIMEOUT_INVALID = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(110)
                .template("drain_timeout_seconds in '%s' is invalid (%s), using the default of %s seconds")
                .build();

        /** Logged when a call waited the project recovery wait in vain; the recovery continues. */
        public static final LogRecord PROJECT_RECOVERY_WAIT_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(111)
                .template("Recovery of project '%s' not finished within %s, answering runtime_recovering")
                .build();

        /** Logged once per distinct relay version that differs from the runtime version. */
        public static final LogRecord RELAY_VERSION_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(112)
                .template("Relay version '%s' differs from runtime version '%s', recommended: pm-operator drain")
                .build();

        /** Logged when the audit sink refused the record of a broken audit chain; the start goes on. */
        public static final LogRecord AUDIT_CHAIN_BROKEN_NOT_RECORDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(113)
                .template("Record of the broken audit chain not handed over to the audit sink: %s")
                .build();

        /** Logged when a drain participant fails in a drain phase; the drain goes on and records the failure. */
        public static final LogRecord DRAIN_PARTICIPANT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(114)
                .template("Drain participant '%s' failed in %s: %s")
                .build();

        /** Logged when a step of the periodic sweep fails for a project; the run goes on with the other steps. */
        public static final LogRecord PERIODIC_SWEEP_STEP_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(115)
                .template("Periodic sweep of project '%s' failed in step '%s': %s")
                .build();

        /** Logged when an enrolment is refused because it does not match its sidecar; only its project is blocked. */
        public static final LogRecord ENROLMENT_TAMPERED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(116)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_tampered': %s")
                .build();

        /** Logged when an enrolment file or its sidecar fails a per-use check; only its project is blocked. */
        public static final LogRecord ENROLMENT_INSECURE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(117)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_insecure': %s")
                .build();

        /** Logged when {@code unenrol} is refused because the project has live work; names the items. */
        public static final LogRecord UNENROL_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(118)
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
                .identifier(119)
                .template("Audit record '%s' of transaction '%s' for project '%s' handed over as not confirmed: "
                        + "the write did not complete or its completion was not recorded")
                .build();

        /**
         * Logged when the runtime refuses to sign or use an enrolment that violates the enrolment rules; only its
         * project is blocked.
         */
        public static final LogRecord ENROLMENT_INVALID = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(120)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_invalid': %s")
                .build();

        /**
         * Logged when the background replay of the audit intents of projects that are no longer enrolled meets an
         * intent it cannot read or hand over; the intent is kept.
         */
        public static final LogRecord ENROLMENT_AUDIT_REPLAY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(121)
                .template("Replay of the owed audit records of unenrolled projects failed: %s")
                .build();

        /**
         * Logged when the verify-not-resolve pipeline refuses a job, with the project id, the reason code and the
         * detail; the detail carries the reason detail and changed paths only, never an argument vector value or a
         * secret.
         */
        public static final LogRecord JOB_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(122)
                .template("Job of project '%s' refused with reason '%s': %s")
                .build();

        /**
         * Logged when the head of the audit log cannot be written after an append, because the enrolment integrity
         * key cannot be read, the head cannot be read or written ({@code head_unwritable}), the head file exists and
         * does not parse or does not verify ({@code head_unverifiable}, the damaged head stays), or the log no longer
         * holds the line the previous head anchored ({@code anchored_line_missing}, the previous head stays); names
         * the reason code, never the key. The record stays appended, and the next verification reports the head that
         * does not match.
         */
        public static final LogRecord AUDIT_HEAD_NOT_WRITTEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(123)
                .template("Audit head not written, the record stays appended: %s")
                .build();

        /**
         * Logged when a due rotation of the audit log cannot be carried out before its checkpoint was written, or is
         * refused because the log no longer holds the line the audit head anchors ({@code anchored_line_missing}) or
         * the head file does not parse or does not verify ({@code head_unverifiable}), so that no checkpoint signs a
         * cut log; the record is appended to the current log, and the next append retries the rotation.
         */
        public static final LogRecord AUDIT_ROTATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(124)
                .template("Audit log rotation not carried out, the log goes on: %s")
                .build();

        /**
         * Logged when a rotation of the audit log stays pending after its checkpoint, because the index entry or the
         * rename failed, or because the checkpoint does not verify under the enrolment integrity key or cannot be
         * verified while the key is absent or unreadable ({@code checkpoint_unverifiable}, the rotation is not
         * completed); the record is not appended, since no record may follow the checkpoint, and the next append with
         * the key completes a rotation whose checkpoint verifies.
         */
        public static final LogRecord AUDIT_ROTATION_PENDING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(125)
                .template("Audit log rotation pending after its checkpoint, the record is not appended: %s")
                .build();

        /**
         * Logged when the Git LFS object of a corpus source is not downloaded: the endpoint did not answer or failed,
         * or does not have the object. Bytes the endpoint delivers are handed to the import's hash check instead. The
         * detail names the step and never carries a credential or a header value of the download action.
         */
        public static final LogRecord CORPUS_LFS_OBJECT_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(126)
                .template("LFS object '%s' of '%s' not downloaded: %s")
                .build();

        /**
         * Logged when a step of the HTTPS fetch of a corpus source does not fetch: the dataset API did not answer or
         * failed, the pinned commit or the file is absent, or the request was refused. Bytes the raw endpoint delivers
         * are handed to the import's hash check instead. The detail names the step and never carries user information.
         */
        public static final LogRecord CORPUS_HTTPS_SOURCE_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(127)
                .template("HTTPS source '%s' not fetched: %s")
                .build();

        /**
         * Logged by {@code CredentialStoreChoice} when the recorded OS store (Keychain or Secret Service) is
         * unavailable for an operation, with the store and the cause; the operation fails and no other store serves
         * in its place. Identifier 104, the removed record of the former file-store fallback, is not reused.
         */
        public static final LogRecord RECORDED_OS_STORE_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(128)
                .template("Recorded credential store '%s' unavailable, no other store serves: %s")
                .build();

        /**
         * Logged by {@code CredentialStoreMove} when a move fails before the switch (a copy, a read-back or the
         * verification of a sidecar under the moved key failed); with the old and the new store, what became of the
         * copies and of the record (copies removed from the target while the old store stays recorded, record
         * deleted; copies not all removed, record kept for the next start; copies kept in the target because the
         * recorded store cannot be read, record kept, or because the target is recorded, record deleted) and the
         * cause, never a secret.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(129)
                .template("Credential store move from '%s' to '%s' failed before the switch, %s: %s")
                .build();

        /**
         * Logged when a member of a job's process group still lived at the end of the termination grace period after
         * {@code SIGTERM}, and the group was sent {@code SIGKILL}; names the job, the process group and the cause of
         * the termination.
         */
        public static final LogRecord JOB_TERMINATION_ESCALATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(130)
                .template("Job '%s' outlived the grace period after SIGTERM, process group %s sent SIGKILL (cause '%s')")
                .build();

        /**
         * Logged when the launcher of a job cannot be started, or the launch of a job cannot be composed, so the job
         * had no process and is settled as the launcher's exit 127, unless a cancel was answered true before the
         * settlement closed the job's cancel door, whose cause then decides it; names the job and why.
         */
        public static final LogRecord JOB_LAUNCHER_NOT_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(131)
                .template("Launcher of job '%s' not started, the job had no process: %s")
                .build();

        /**
         * Logged when the termination protocol fails with an exception for a job whose end the runner has not observed
         * (a refused PID or a failed signal call), before its process is destroyed through its handle and the job is
         * settled; names the job and the reason.
         */
        public static final LogRecord JOB_TERMINATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(132)
                .template("Termination of job '%s' failed, its process is destroyed through its handle: %s")
                .build();

        /**
         * Logged by {@code CredentialStoreChoice} when the first recording of a credential store fails before the
         * switch: names the store being recorded, the store the key is deleted from, what became of the copy and of
         * the record of the recording, and the reason. Never names a key.
         */
        public static final LogRecord CREDENTIAL_STORE_FIRST_RECORDING_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(133)
                .template("First recording of credential store '%s' from '%s' failed, %s: %s")
                .build();

        /**
         * Logged by {@code CredentialStoreChoice} when the credential store that is not being recorded cannot be read
         * while the first recording reconciles the integrity key, so it is taken as holding no key; names the store and
         * the reason.
         */
        public static final LogRecord CREDENTIAL_STORE_UNREADABLE_WHILE_RECORDING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(134)
                .template("Credential store '%s' cannot be read while a store is recorded, taken as holding no integrity key: %s")
                .build();

        /**
         * Logged when the marker or the kept copy of a reinstall cannot be deleted before a restore's load, or when the
         * kept unit cannot be read, written back or removed; names the unit file and the failure. A marker that stayed,
         * and any failure before the drop, leave the pair for the next start, which tries the restore again; a copy
         * that alone stayed, and a load that failed after the drop, are not retried. `install` reports the restore's
         * failure suppressed under the load's failure.
         */
        public static final LogRecord SERVICE_UNIT_RESTORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(135)
                .template("Service unit '%s' could not be restored: %s")
                .build();

        /**
         * Logged when the kept pair of a reinstall whose load succeeded cannot be dropped; names the unit file and the
         * failure. The install stays a success. When the marker stays, the next start treats the reinstall as
         * unfinished and restores the kept unit, or removes the unit file when none was kept; when only the copy
         * stays, the next start does nothing and the next install overwrites the copy.
         */
        public static final LogRecord SERVICE_UNIT_KEPT_NOT_DROPPED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(136)
                .template("The kept state of service unit '%s' could not be removed after a successful load: %s")
                .build();

        /**
         * Logged when the security audit record {@code job_descendant_survived} of a descendant that outlived the
         * sweep of a job's process tree cannot be written; names the job and the failure. The sweep has sent its
         * signals; the job is settled regardless.
         */
        public static final LogRecord JOB_DESCENDANT_AUDIT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(137)
                .template("The surviving descendant of job '%s' could not be audited: %s")
                .build();
    }

    /** ERROR level messages. */
    @UtilityClass
    public static final class ERROR {

        /**
         * Logged when the bound socket cannot be secured or recorded; the runtime exits with code 77.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord SOCKET_NOT_SECURED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Socket '%s' could not be secured or recorded, exiting with code 77")
                .build();

        /**
         * Logged when a credential store operation of the local API fails; never contains the secret.
         * <p>
         * Used by the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CREDENTIAL_STORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(201)
                .template("Credential store '%s' failed: %s")
                .build();

        /** Logged when a lock acquisition against the total lock order is refused; nothing was acquired. */
        public static final LogRecord LOCK_ORDER_VIOLATION = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(202)
                .template("Lock order violation: '%s' requested while holding %s")
                .build();

        /** Logged when a service-manager command fails; names the command and the exit code or failure reason, never an environment value. */
        public static final LogRecord SERVICE_MANAGER_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(203)
                .template("Service manager command '%s' failed: %s")
                .build();

        /** Logged when a recovery step of a project fails; the recovery of that project stops, other projects go on. */
        public static final LogRecord PROJECT_RECOVERY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(204)
                .template("Recovery of project '%s' failed in step '%s': %s")
                .build();

        /**
         * Logged when a scheduled run of the periodic sweep ends with a failure outside its steps; the schedule stays
         * alive and the next run takes place at the interval.
         */
        public static final LogRecord PERIODIC_SWEEP_RUN_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(205)
                .template("Periodic sweep run failed, the next run takes place at the interval: %s")
                .build();

        /** Logged when the enrolment integrity key cannot be read or created; never contains the key. */
        public static final LogRecord ENROLMENT_KEY_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(206)
                .template("Enrolment integrity key unavailable from '%s': %s")
                .build();

        /**
         * Logged when a step of the settlement of a job fails and the settlement stops there, so no later step runs;
         * names the job, the step and the reason.
         */
        public static final LogRecord JOB_SETTLEMENT_STEP_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(207)
                .template("Settlement of job '%s' stopped at step '%s': %s")
                .build();

        /**
         * Logged when the virtual thread that waits for a job fails with an exception or an error; names the job and the
         * failure. A settlement from what the runner observed may have run before it, but it need not have: a fault of
         * the lock manager can be the failure itself or precede the fallback, and a job whose fate was not recorded is
         * settled by the recovery of the next runtime start.
         */
        public static final LogRecord JOB_SUPERVISION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(208)
                .template("Supervision of job '%s' failed: %s")
                .build();

        /**
         * Logged when the last-resort settlement of a job fails as well: the fallback settlement had thrown and the
         * retry of the record it was about to write, or the minimal ended record for a failed derivation, could not be
         * written either; names the job and the failure. The record stays {@code running} and the recovery of the next
         * runtime start settles it.
         */
        public static final LogRecord JOB_LAST_RESORT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(209)
                .template("Last-resort settlement of job '%s' failed: %s")
                .build();
    }
}
