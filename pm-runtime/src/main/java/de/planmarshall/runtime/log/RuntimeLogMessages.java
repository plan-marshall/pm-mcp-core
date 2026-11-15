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
         * Logged when the {@code hello} MCP tool is called.
         * <p>
         * The component is {@code HelloTool} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord HELLO_TOOL_INVOKED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Hello tool invoked for '%s'")
                .build();

        /**
         * Logged when the Unix socket is bound, restricted to mode {@code 0600} and recorded in
         * {@code state/runtime.json} (startup step 7).
         * <p>
         * The component is {@code SocketBinding} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord RUNTIME_READY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(2)
                .template("Runtime ready on socket '%s' (pid %s)")
                .build();

        /**
         * Logged when the web listener has opened on its address and port.
         * <p>
         * The component is {@code WebListener} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_OPENED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(3)
                .template("Web listener open on %s:%s (lan: %s)")
                .build();

        /**
         * Logged when the web listener has closed (web access disabled, or the runtime stops).
         * <p>
         * The component is {@code WebListener} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_CLOSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(4)
                .template("Web listener on port %s closed")
                .build();

        /**
         * Logged when {@code POST /api/v1/runtime/stop} is accepted; the runtime exits with code {@code 0} shortly
         * after.
         * <p>
         * The component is {@code RuntimeApiResource} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord RUNTIME_STOP_REQUESTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(5)
                .template("Runtime stop requested through the local API")
                .build();

        /**
         * Logged at start with the core tools registered with their flat input schemas.
         * <p>
         * The component is {@code CoreTools} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CORE_TOOLS_REGISTERED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(6)
                .template("Core tools registered: %s")
                .build();

        /**
         * Logged for every request to open the web listener, also one that finds it open, with the address, the
         * requested port ({@code 0} for any free port), the exposure and what asked for it
         * ({@code PUT /api/v1/web}).
         * <p>
         * The component is {@code WebListener} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_OPEN_REQUESTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(7)
                .template("Web listener open requested on %s:%s (lan: %s), cause: %s")
                .build();

        /**
         * Logged before the web listener closes, with what caused the close ({@code PUT /api/v1/web} or
         * {@code runtime shutdown}).
         * <p>
         * The component is {@code WebListener} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_CLOSING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(8)
                .template("Web listener on port %s closing, cause: %s")
                .build();

        /**
         * Logged at start with the credential store the operator recorded in {@code credentials.store} of
         * {@code machine-config.json} ({@code os}, {@code file}, or {@code none} before the choice) and the keyring
         * service name of the instance; the runtime chooses no store by itself.
         * <p>
         * The component is {@code SecretStoreProducer} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CREDENTIAL_STORE_SELECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(9)
                .template("Credential store '%s' recorded, service name '%s'")
                .build();

        /**
         * Logged when a language server of the LSP pool answered {@code initialize}; names the executable, its process
         * id and the negotiated position encoding.
         * <p>
         * The component is {@code LspClient}.
         */
        public static final LogRecord LSP_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(10)
                .template("Language server '%s' started (pid %s, position encoding %s)")
                .build();

        /**
         * Logged when the stale lease sweep of a lease store has finished, with the store and how many leases it kept,
         * marked orphaned and removed.
         * <p>
         * The component is {@code StaleLeaseSweep}.
         */
        public static final LogRecord STALE_LEASE_SWEEP_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(11)
                .template("Stale lease sweep of '%s' finished: %s kept, %s orphaned, %s removed")
                .build();

        /**
         * Logged when {@code pm-operator runtime install} has written the per-user service unit and the service
         * manager has loaded it; names the unit file and the daemon it starts.
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_UNIT_INSTALLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(12)
                .template("Service unit '%s' installed for daemon '%s'")
                .build();

        /**
         * Logged when {@code pm-operator runtime uninstall} has removed the per-user service unit and the service
         * manager has unloaded it, also when no unit was present; names the unit file. Not logged when the unload
         * ended the runtime that the service had started.
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_UNIT_UNINSTALLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(13)
                .template("Service unit '%s' uninstalled")
                .build();

        /**
         * Logged when the bounded verification (startup step 4) has finished; names the outcome of the audit chain
         * verification ({@code intact}, {@code broken (reason '<reason>', first broken line <line>): <diagnostic>} or
         * {@code not verified (<reason>)}); the diagnostic names the file the line is counted in.
         * <p>
         * The component is {@code StartupSequence}.
         */
        public static final LogRecord BOUNDED_VERIFICATION_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(14)
                .template("Bounded verification finished: audit chain %s")
                .build();

        /**
         * Logged when the drain of the runtime starts, before any drain participant is called; names the drain timeout
         * in seconds.
         * <p>
         * The component is {@code RuntimeDrain}.
         */
        public static final LogRecord RUNTIME_DRAIN_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(15)
                .template("Runtime drain started, drain timeout %s seconds")
                .build();

        /**
         * Logged when the drain has removed the runtime record, the token and the socket and released the singleton
         * lock; names the participants that got quiet within the drain timeout, what was terminated after it, and the
         * exit code {@code 0}.
         * <p>
         * The component is {@code RuntimeDrain}.
         */
        public static final LogRecord RUNTIME_DRAINED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(16)
                .template("Runtime drained: %s ended, %s terminated, exit code %s")
                .build();

        /**
         * Logged when the per-project recovery of a project has finished, also a failed one; names the project id and
         * either the number of steps done or the step it failed in.
         * <p>
         * The component is {@code ProjectRecovery}.
         */
        public static final LogRecord PROJECT_RECOVERY_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(17)
                .template("Recovery of project '%s' finished: %s")
                .build();

        /**
         * Logged after each run of the periodic sweep; names how many enrolled projects every step ran for without a
         * failure, how many were skipped because their recovery has not finished or failed, and how many had a failing
         * step.
         * <p>
         * The component is {@code PeriodicSweep}.
         */
        public static final LogRecord PERIODIC_SWEEP_FINISHED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(18)
                .template("Periodic sweep finished: %s projects swept, %s skipped, %s failed")
                .build();

        /**
         * Logged when {@code enrol} has written a project's enrolment file and its sidecar record under the project's
         * enrolment lock; names the project and whether it was enrolled as a pilot of the lifecycle gate. Its
         * {@code enrolment_written} audit record was owed in {@code enrolments/<project_id>.json.audit-intent} before
         * the write as an unconfirmed record, confirmed after the write and handed over, or stays owed there
         * ({@code PM_CORE-100}, {@code PM_RUNTIME-119}).
         * <p>
         * The component is {@code Enrolments}.
         */
        public static final LogRecord ENROLMENT_WRITTEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(19)
                .template("Enrolment of project '%s' written (pilot: %s)")
                .build();

        /**
         * Logged when {@code unenrol} has removed a project's enrolment file and its sidecar; the project's machine
         * store below {@code projects/<project_id>/} is untouched. Not logged when the project had no enrolment. Its
         * {@code enrolment_removed} audit record was owed in {@code enrolments/<project_id>.json.audit-intent} before
         * the removal as an unconfirmed record, confirmed after the removal and handed over, or stays owed there
         * ({@code PM_CORE-100}, {@code PM_RUNTIME-119}).
         * <p>
         * The component is {@code Enrolments}.
         */
        public static final LogRecord ENROLMENT_REMOVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(20)
                .template("Enrolment of project '%s' removed, its project store kept")
                .build();

        /**
         * Logged when the re-baseline of {@code accept-edit enrolment} has rewritten the sidecar record of a project's
         * enrolment file over its current bytes. Its {@code enrolment_rebaselined} audit record was owed in
         * {@code enrolments/<project_id>.json.audit-intent} before the write as an unconfirmed record, confirmed after
         * the write and handed over, or stays owed there ({@code PM_CORE-100}, {@code PM_RUNTIME-119}).
         * <p>
         * The component is {@code Enrolments}.
         */
        public static final LogRecord ENROLMENT_REBASELINED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(21)
                .template("Enrolment of project '%s' re-baselined")
                .build();

        /**
         * Logged when a record that an earlier enrolment write owed (its hand-over was refused or stopped) is handed
         * over by a later enrolment operation or by the recovery sweep ({@code Enrolments.replayOwedAudit()}); names
         * the event, its transaction id and the project. A record handed over again after a stop carries the same
         * transaction id, by which the audit store drops the duplicate. An unconfirmed record (outcome
         * {@code failed}) handed over this way is logged {@code PM_RUNTIME-119} as well. Also logged for the records a
         * recovery sweep hands over before it throws for a project whose intent file was refused or failed on I/O: the
         * sweep continues past that project, so the records of the others are handed over and logged.
         * <p>
         * The component is {@code Enrolments}.
         */
        public static final LogRecord ENROLMENT_AUDIT_REPLAYED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(22)
                .template("Owed audit record '%s' of transaction '%s' for project '%s' handed over after an earlier refusal or stop")
                .build();

        /**
         * Logged when the pending-record step of the periodic sweep has removed the pending entry records of a
         * project created before the pending entry record maximum age, all in one transaction of the workspace scope;
         * names how many records it removed and the project. Each removed record has one
         * {@code pending_entry_removed} audit record, owed by that transaction. Not logged for a run that removed
         * nothing.
         * <p>
         * The component is {@code PendingEntryRecordsSweep}.
         */
        public static final LogRecord PENDING_ENTRY_RECORDS_SWEPT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(23)
                .template("Periodic sweep removed %s pending entry records of project '%s'")
                .build();

        /**
         * Logged when the audit log was rotated under the audit lock: closed by its {@code checkpoint} record, named
         * by an entry of the audit index {@code logs/audit-index.jsonl} and renamed; names the rotated file
         * {@code audit-<first_event_id>.jsonl} and its number of records, the checkpoint included. Also logged when
         * the next append completes a rotation a stop interrupted after its checkpoint.
         * <p>
         * The component is {@code AuditRotation}.
         */
        public static final LogRecord AUDIT_LOG_ROTATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(25)
                .template("Audit log rotated to '%s' with %s records")
                .build();

        /**
         * Logged when {@code credentials.store} of {@code machine-config.json} is written under
         * {@code locks/machine-config.lock}; names the store ({@code os} or {@code file}), the writer
         * ({@code CREDENTIAL_STORE_ASPECT} or {@code STORE_MOVE}) and the new {@code revision}. A writer that does not
         * own the key is refused with {@code aspect_owned} and nothing is logged.
         * <p>
         * The component is {@code CredentialStoreChoice}.
         */
        public static final LogRecord CREDENTIAL_STORE_RECORDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(26)
                .template("Credential store '%s' recorded by %s, machine configuration revision %s")
                .build();

        /**
         * Logged when a move between the credential stores starts, after its record
         * {@code state/credential-store-move.json} was written in phase {@code copying}; names the old and the new
         * store.
         * <p>
         * The component is {@code CredentialStoreMove}.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVE_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(27)
                .template("Credential store move from '%s' to '%s' started")
                .build();

        /**
         * Logged when a move between the credential stores has ended: the new store is recorded, every entry and the
         * integrity key are deleted from the old store, the {@code credential_store_moved} audit record is appended
         * and then the record is deleted; names both stores and the number of Token Lane entries in the new store, the
         * integrity key not counted, never a secret. Where the audit sink refuses the audit record the record of the
         * move is kept and nothing is logged, and the next start finishes the move again. Also logged when the start
         * completes a move a stop cut ({@code PM_RUNTIME-29}).
         * <p>
         * The component is {@code CredentialStoreMove}.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(28)
                .template("Credential store moved from '%s' to '%s', %s entries")
                .build();

        /**
         * Logged after the start completed a move between the credential stores that a stop cut; names both stores and
         * the phase of the record it was resumed from: {@code copying} (with the old store recorded, the copies were
         * deleted from the target and the move ran again from the first entry; with the target recorded, a stop
         * between the switch and the rewrite of the record, the deletion from the old store was finished) or
         * {@code switched} (the deletion from the old store was finished). Not logged when no move was open.
         * <p>
         * The component is {@code CredentialStoreMoveResume}.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVE_RESUMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(29)
                .template("Credential store move from '%s' to '%s' resumed from phase '%s' and completed")
                .build();

        /**
         * Logged when the post-spawn record of a process job is written with the PID and the process start of the
         * launch report; from this record on the job counts as running. Names the job id, its kind, its PID, which is
         * its process-group id, and its budget in seconds, which starts at the spawn. Not logged for a job whose
         * launcher wrote no launch report.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(30)
                .template("Job '%s' of kind '%s' started (pid %s, budget %s seconds)")
                .build();

        /**
         * Logged when the fate of a job is recorded: once in the one write of a job refused by its verification,
         * otherwise by the first step of its settlement; names the job id, the fate and the row of the outcome
         * derivation that decided it. Not logged when the fate step fails ({@code PM_RUNTIME-207}). Logged by the
         * runner only: a job the recovery of a project settles {@code killed} ({@code JobRecoveryStep}) is not logged
         * here.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_SETTLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(31)
                .template("Job '%s' settled with fate '%s' (outcome row %s)")
                .build();

        /**
         * Logged after the start completed the first recording of a credential store that a stop cut; names the store
         * recorded, the store the integrity key is deleted from and the phase of the record it was resumed from:
         * {@code copying} (the copy was removed from the store being recorded and the recording ran again, or, with
         * that store recorded already, the deletion of the key from the other store was finished) or
         * {@code switched} (the deletion of the key from the other store was finished). Not logged when no first
         * recording was open.
         * <p>
         * The component is {@code CredentialStoreMoveResume}.
         */
        public static final LogRecord CREDENTIAL_STORE_FIRST_RECORDING_RESUMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(32)
                .template("First recording of credential store '%s' from '%s' resumed from phase '%s' and completed")
                .build();

        /**
         * Logged when a restore has written the bytes of the unit a reinstall kept back and loaded them: after a
         * failed load of the new unit, or at the restore step of a start after an interrupted reinstall. Names the
         * unit file. Logged after the kept pair was dropped and after the unit was loaded; not logged when the restore
         * or its load fails ({@code PM_RUNTIME-135}).
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_UNIT_RESTORED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(33)
                .template("Service unit '%s' restored and loaded from the copy a reinstall kept")
                .build();

        /**
         * Logged when a restore removes the new unit file because the reinstall kept no previous unit; nothing is
         * unloaded. Names the unit file. Logged after the kept pair was dropped; not logged when the drop fails
         * ({@code PM_RUNTIME-135}).
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(34)
                .template("Service unit '%s' removed, the reinstall kept no previous unit")
                .build();

        /**
         * Logged when the launch accepted a harness's self-update: the update evidence of its harness profile passed
         * and the version probe reported a version inside the profile's supported range. Names the harness id, the
         * recorded and the new version and the new digest; the store and its sidecar were rewritten in the same
         * transaction and the record {@code harness_updated} was audited. Never names a token or a credential.
         * <p>
         * The component is {@code HarnessVerifier}.
         */
        public static final LogRecord HARNESS_SELF_UPDATE_ACCEPTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(35)
                .template("Harness '%s' self-updated from version '%s' to '%s' (digest '%s')")
                .build();

        /**
         * Logged once the job runner reported the started job; names the worker generation, the role and the client id
         * of the harness.
         * <p>
         * The component is {@code WorkerSupervisor}.
         */
        public static final LogRecord WORKER_GENERATION_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(36)
                .template("Worker generation '%s' of role '%s' started on harness '%s'")
                .build();

        /**
         * Logged when a worker generation ended; names the generation, the role and the {@code EndCause} by its wire
         * name ({@code task_done}, {@code recycle}, {@code drain}, {@code pause_at_once}, {@code lost},
         * {@code off_track} or {@code start_failure}).
         * <p>
         * The component is {@code WorkerEnding}.
         */
        public static final LogRecord WORKER_GENERATION_ENDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(37)
                .template("Worker generation '%s' of role '%s' ended with cause '%s'")
                .build();

        /**
         * Logged when a warm worker generation was recycled; names the generation, the role and what recycled it
         * ({@code idle}, {@code token_budget}, {@code tasks_per_generation}, {@code configuration_changed} or
         * {@code idle_yield}).
         * <p>
         * The component is {@code WorkerRecycling}.
         */
        public static final LogRecord WORKER_GENERATION_RECYCLED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(38)
                .template("Worker generation '%s' of role '%s' recycled for '%s'")
                .build();

        /**
         * Logged when {@code store.check} passed: one test entry of the chosen store was written, read back and
         * compared, and deleted. Names the store ({@code os} or {@code file}); never the test value.
         * <p>
         * The component is {@code CredentialStoreAspect}.
         */
        public static final LogRecord CREDENTIAL_STORE_CHECK_PASSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(40)
                .template("The credential store '%s' passed its check (write, read back, delete)")
                .build();

        /**
         * Logged when a provider passed every check of its kind ({@code providers.verify}): names the provider and the
         * number of check lines. Never the credential or the provider's answer.
         * <p>
         * The component is {@code ProviderVerification}.
         */
        public static final LogRecord PROVIDER_VERIFIED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(41)
                .template("Provider '%s' verified (%s check lines)")
                .build();
    }

    /** WARN level messages. */
    @UtilityClass
    public static final class WARN {

        /**
         * Logged when Vert.x created the socket under the process umask; the runtime restricts it right after the
         * bind.
         * <p>
         * The component is {@code SocketBinding} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord SOCKET_MODE_RESTRICTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Socket '%s' had mode %s after the bind and was set to 0600")
                .build();

        /**
         * Logged when another component registered a core tool name first.
         * <p>
         * The component is {@code CoreTools} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CORE_TOOL_SHADOWED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Core tool '%s' not registered: the name is already registered")
                .build();

        /**
         * Logged when the web listener cannot bind its port (answered {@code web_listener_conflict}), for each
         * candidate port it rejects while choosing a port for port {@code 0}, or when it cannot close cleanly.
         * <p>
         * The component is {@code WebListener} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("Web listener could not open on port %s: %s")
                .build();

        /**
         * Logged when a connection to the web listener fails before its first request, a failed TLS handshake
         * included; one line with the class and message of the failure and no stack trace, since a client can cause it
         * at will.
         * <p>
         * The component is {@code WebListener} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord WEB_LISTENER_CONNECTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Web listener on port %s: connection failed before a request (TLS handshake or connection setup): %s")
                .build();

        /**
         * Logged when {@code shutdown}/{@code exit} fails or the process does not end within the request timeout; the
         * process is then terminated.
         * <p>
         * The component is {@code LspClient}.
         */
        public static final LogRecord LSP_SHUTDOWN_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(105)
                .template("Language server (pid %s) did not end cleanly, terminating it: %s")
                .build();

        /**
         * Logged when a project path is refused by the workspace confinement ({@code PM-SEC-2}); names the outcome
         * code {@code path_traversal_rejected} and the reason of the refusal ({@code UNPARSEABLE_PATH},
         * {@code PARENT_NAME_AFTER_MISSING_PART}, {@code CANONICALIZATION_FAILED} or {@code OUTSIDE_WORKSPACE}), never
         * the path.
         * <p>
         * The component is {@code WorkspaceConfinement}.
         */
        public static final LogRecord PROJECT_PATH_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(106)
                .template("Project path refused with outcome '%s': %s")
                .build();

        /**
         * Logged for each lease the stale lease sweep marks orphaned because the runtime that served it is no longer
         * alive; names the lease key, the lease store and the process id of the dead holder.
         * <p>
         * The component is {@code StaleLeaseSweep}.
         */
        public static final LogRecord LEASE_ORPHANED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(107)
                .template("Lease '%s' in '%s' orphaned: its holder (pid %s) is no longer alive")
                .build();

        /**
         * Logged when the bounded verification (startup step 4) finds the audit chain broken; names the reason by its
         * wire name ({@code link_broken}, {@code missing}, {@code reset}, {@code truncated} or {@code head_mismatch}),
         * the first broken line ({@code none} for every reason other than {@code link_broken}) and the diagnostic,
         * which names the file the line is counted in (current log, audit index or a rotated log), never a key or a
         * hash secret. The chain is not repaired, an {@code audit_chain_broken} record is handed to the audit sink, and
         * the start goes on.
         * <p>
         * The component is {@code StartupSequence}.
         */
        public static final LogRecord AUDIT_CHAIN_BROKEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(108)
                .template("Audit chain broken (reason '%s', first broken line %s): %s; the start goes on")
                .build();

        /**
         * Logged when a drain participant has not got quiet within the drain timeout (at once for a timeout of
         * {@code 0}); the drain then terminates what still runs.
         * <p>
         * The component is {@code RuntimeDrain}.
         */
        public static final LogRecord DRAIN_TIMEOUT_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(109)
                .template("Drain timeout of %s seconds expired, terminating what still runs")
                .build();

        /**
         * Logged when {@code drain_timeout_seconds} of {@code machine-config.json} is negative, not an integer or
         * larger than 3153600000 (100 years), or the file cannot be read or does not parse as one JSON object; names
         * the file, the reason and the default of {@code 600} seconds that applies instead.
         * <p>
         * The component is {@code DrainTimeout}.
         */
        public static final LogRecord DRAIN_TIMEOUT_INVALID = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(110)
                .template("drain_timeout_seconds in '%s' is invalid (%s), using the default of %s seconds")
                .build();

        /**
         * Logged when a call waited the project recovery wait (10 seconds) for the recovery of its project in vain;
         * the call is answered {@code runtime_recovering} and the recovery continues.
         * <p>
         * The component is {@code ProjectRecovery}.
         */
        public static final LogRecord PROJECT_RECOVERY_WAIT_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(111)
                .template("Recovery of project '%s' not finished within %s, answering runtime_recovering")
                .build();

        /**
         * Logged once per distinct relay version per runtime instance when the {@code PM-MCP-Relay-Version} header of
         * a relayed request names a version other than the runtime's; a {@code relay_version_mismatch} audit record is
         * handed over with it.
         * <p>
         * The component is {@code RelayVersionCheck}.
         */
        public static final LogRecord RELAY_VERSION_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(112)
                .template("Relay version '%s' differs from runtime version '%s', recommended: pm-operator drain")
                .build();

        /**
         * Logged when the audit sink refuses the {@code audit_chain_broken} record of the bounded verification; names
         * the failure. The start goes on.
         * <p>
         * The component is {@code StartupSequence}.
         */
        public static final LogRecord AUDIT_CHAIN_BROKEN_NOT_RECORDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(113)
                .template("Record of the broken audit chain not handed over to the audit sink: %s")
                .build();

        /**
         * Logged when a drain participant fails in a drain phase ({@code stopAdmitting}, {@code endIdle},
         * {@code awaitQuiet}, {@code terminateRemaining} or {@code close}), with a checked or an unchecked exception;
         * names the participant, the phase and the failure. The drain goes on, records the failure in its outcome, and
         * still removes the runtime files and releases the singleton lock.
         * <p>
         * The component is {@code RuntimeDrain}.
         */
        public static final LogRecord DRAIN_PARTICIPANT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(114)
                .template("Drain participant '%s' failed in %s: %s")
                .build();

        /**
         * Logged when a step of the periodic sweep fails for a project, or throws instead of reporting its failure;
         * names the project id, the step and the reason. The run goes on with the other steps and projects.
         * <p>
         * The component is {@code PeriodicSweep}.
         */
        public static final LogRecord PERIODIC_SWEEP_STEP_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(115)
                .template("Periodic sweep of project '%s' failed in step '%s': %s")
                .build();

        /**
         * Logged when the enrolment check refuses a project's enrolment because it does not match its sidecar record;
         * names the project and the reason ({@code size or SHA-256 differs from the sidecar record},
         * {@code HMAC differs from the sidecar record}, {@code the integrity key does not exist},
         * {@code sidecar without its enrolment file}, {@code enrolment file without its sidecar}), never the key or an
         * HMAC. Only that project is blocked; an {@code enrolment_tampered} audit record follows.
         * <p>
         * The component is {@code EnrolmentCheck}.
         */
        public static final LogRecord ENROLMENT_TAMPERED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(116)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_tampered': %s")
                .build();

        /**
         * Logged when a project's enrolment file or its sidecar fails a per-use check, in the enrolment check or in
         * the re-baseline ({@code accept-edit enrolment}) of the file; names the project and the reason (a symbolic
         * link, a foreign owner, a mode broader than {@code 0600}, a file replaced or changed while it was read). Only
         * that project is blocked, never the runtime start; an {@code enrolment_insecure} audit record follows in both
         * cases.
         * <p>
         * The component is {@code EnrolmentCheck}.
         */
        public static final LogRecord ENROLMENT_INSECURE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(117)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_insecure': %s")
                .build();

        /**
         * Logged when {@code unenrol} is refused because the project has live work; names the project and every item
         * of it ({@code plan <plan_id>}, {@code epic <epic_id>}, {@code job <job_id>}). Nothing is removed; an
         * {@code unenrol_refused} audit record follows. The refusal is thrown even when that audit append fails; the
         * failure is attached to it.
         * <p>
         * The component is {@code Enrolments}.
         */
        public static final LogRecord UNENROL_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(118)
                .template("Unenrol of project '%s' refused with outcome 'scope_active', live work: %s")
                .build();

        /**
         * Logged when the owed audit record of an enrolment write that was started and not confirmed complete is
         * handed over: the mutation failed, or the process stopped before or after it took effect. The record is
         * handed over by a later enrolment operation of the project or {@code Enrolments.replayOwedAudit()} as outcome
         * {@code failed} with the reason {@code not_confirmed}; the message names the event, its transaction id and
         * the project. The record is never handed over as {@code succeeded}. The enrolment file and its sidecar show
         * what became of the write (Concepts § Store Integrity, Audit Records of a Store Change,
         * {@code doc/concepts/store-integrity.adoc#audit-delivery} of {@code plan-marshall-documentation}).
         * <p>
         * The component is {@code Enrolments}.
         */
        public static final LogRecord ENROLMENT_WRITE_NOT_CONFIRMED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(119)
                .template("Audit record '%s' of transaction '%s' for project '%s' handed over as not confirmed: "
                        + "the write did not complete or its completion was not recorded")
                .build();

        /**
         * Logged when the runtime refuses to sign or use an enrolment that violates the enrolment rules; only its
         * project is blocked. Logged for a refusal of {@code enrol} or the re-baseline by the enrolment policy (the
         * reasons name the vector and placeholder, the write path, or the environment name), nothing written, owed or
         * created; and for a refused strict read of the sidecar or the file in the enrolment check, with the reason
         * {@code <outcome code> in <file name>}, the refusal keeping its own outcome. An {@code enrolment_invalid}
         * audit record follows.
         * <p>
         * The component is {@code EnrolmentCheck}.
         */
        public static final LogRecord ENROLMENT_INVALID = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(120)
                .template("Enrolment of project '%s' refused with outcome 'enrolment_invalid': %s")
                .build();

        /**
         * Logged when the background pass of the recovery ({@code OwedEnrolmentAuditStep.unenrolledPass()}), which
         * hands over the audit records of projects that are no longer enrolled, meets an intent it cannot read or hand
         * over; names the failure, with the exception. The intent is kept and the records of the other projects are
         * handed over.
         * <p>
         * The component is {@code OwedEnrolmentAuditStep}.
         */
        public static final LogRecord ENROLMENT_AUDIT_REPLAY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(121)
                .template("Replay of the owed audit records of unenrolled projects failed: %s")
                .build();

        /**
         * Logged once when the verify-not-resolve pipeline refuses a job; names the project, the refusal reason
         * ({@code not_enrolled}, {@code enrolment_invalid}, {@code path_traversal_rejected},
         * {@code executable_not_enrolled}, {@code executable_digest_mismatch}, {@code build_config_changed},
         * {@code argument_rejected} or {@code verb_not_supported}) and its detail with the changed paths of a changed
         * build configuration, never a value of the argument vector or a secret. A refusal of the enrolment that the
         * enrolment check has logged itself ({@code PM_RUNTIME-116}, {@code PM_RUNTIME-117}, {@code PM_RUNTIME-120} for
         * a refused strict read) is not logged again.
         * <p>
         * The component is {@code VerifyNotResolvePipeline}.
         */
        public static final LogRecord JOB_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(122)
                .template("Job of project '%s' refused with reason '%s': %s")
                .build();

        /**
         * Logged when the head {@code logs/audit.head} cannot be written after an append, because the enrolment
         * integrity key cannot be read or the head cannot be written; names the reason code of the key's failure,
         * {@code head_unwritable} (the head cannot be read or written), {@code head_unverifiable} (the head file exists
         * but does not parse or verify, so the damaged head stays and the next verification reports
         * {@code head_mismatch}), or {@code anchored_line_missing} (the log no longer holds the line the previous head
         * anchored, so the previous head stays and the next verification reports the truncation), never the key. The
         * record stays appended, and the next verification reports the head that does not match. Not logged while no
         * key exists (no enrolment yet).
         * <p>
         * The component is {@code AuditHead}.
         */
        public static final LogRecord AUDIT_HEAD_NOT_WRITTEN = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(123)
                .template("Audit head not written, the record stays appended: %s")
                .build();

        /**
         * Logged when a due rotation of the audit log cannot be carried out before its checkpoint is written (the log
         * or the index cannot be read, an intent file is unreadable, the checkpoint cannot be appended), or when a due
         * rotation is refused because the head does not vouch for the current log ({@code anchored_line_missing},
         * {@code head_unverifiable}), or because the due log is already entered in the audit index while records
         * follow its checkpoint (left by a torn tail written behind the checkpoint outside the audit trail's own
         * code); names the failure or the reason. The record is appended to the current log, and the next append
         * retries the rotation; a refused rotation is refused again until the cut or the damaged head is dealt with.
         * <p>
         * The components are {@code AuditHead} and {@code AuditRotation}.
         */
        public static final LogRecord AUDIT_ROTATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(124)
                .template("Audit log rotation not carried out, the log goes on: %s")
                .build();

        /**
         * Logged when a rotation has written its checkpoint but its index entry or the rename fails, or when a pending
         * rotation is not completed because its checkpoint does not verify under the integrity key or the key cannot
         * be read ({@code checkpoint_unverifiable}); names the failure or the reason. The record is not appended (the
         * producer keeps what it owes, as for any refusal of the audit sink), and the next append completes the
         * pending rotation with exactly one index entry once its checkpoint verifies.
         * <p>
         * The components are {@code AuditHead} and {@code AuditRotation}.
         */
        public static final LogRecord AUDIT_ROTATION_PENDING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(125)
                .template("Audit log rotation pending after its checkpoint, the record is not appended: %s")
                .build();

        /**
         * Logged when the Git LFS object of a corpus source is not downloaded: the endpoint did not answer, timed out
         * or answered an error status, refused the object in its batch answer, or does not have it; names the oid, the
         * repository without user information and the detail, never a credential, a header value of the download
         * action or the query of a download URL. Bytes the endpoint delivers are not logged here; the import checks
         * them against the pinned SHA-256 ({@code PM_CORE-200}).
         * <p>
         * The component is {@code LfsBatchClient}.
         */
        public static final LogRecord CORPUS_LFS_OBJECT_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(126)
                .template("LFS object '%s' of '%s' not downloaded: %s")
                .build();

        /**
         * Logged when a step of the HTTPS fetch of a corpus source does not fetch: the dataset API did not answer,
         * timed out or answered an error status, the pinned commit or the file is absent at the pin, or the request
         * was refused; names the repository without user information, query or fragment, and the detail. A repository
         * URL without scheme or host is named {@code <a URL without scheme or host>} and none of its parts is logged,
         * so writing the WARN never fails on what the caller handed in. Bytes the raw endpoint delivers are not logged
         * here; the import checks them against the pinned SHA-256.
         * <p>
         * The component is {@code HuggingFaceSourceRepository}.
         */
        public static final LogRecord CORPUS_HTTPS_SOURCE_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(127)
                .template("HTTPS source '%s' not fetched: %s")
                .build();

        /**
         * Logged when the recorded OS store ({@code keychain} or {@code secret-service}) is unavailable for an
         * operation, at start or later; names the store and the cause. A read fails with
         * {@code credential_store_unavailable}, a write with {@code credential_store_locked}, and nothing is written to
         * the file store in its place. Identifier {@code 104}, the record of the removed file-store fallback, is not
         * reused.
         * <p>
         * The component is {@code CredentialStoreChoice}.
         */
        public static final LogRecord RECORDED_OS_STORE_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(128)
                .template("Recorded credential store '%s' unavailable, no other store serves: %s")
                .build();

        /**
         * Logged when a move between the credential stores fails before the switch: a copy, a read-back or the
         * verification of a sidecar under the moved integrity key failed. The copies are deleted from the target while
         * the old store is recorded, so the clean-up never deletes from the recorded store, and the old store is
         * untouched. Names both stores, what became of the copies and of the record ({@code the copies were removed
         * from '<to>' and '<from>' stays recorded}, the record deleted; {@code the copies could not all be removed from
         * '<to>' and '<from>' stays recorded; the record is kept, so the next start removes them and moves again}, the
         * failure of the removal added to the cause; {@code the recorded store cannot be read, so the copies stay in
         * '<to>'; the record is kept, so the next start resumes the move once it reads again}; or {@code '<to>' is
         * recorded, so the copies stay in '<to>', '<from>' keeps every entry and the record is deleted}) and the cause,
         * with the exception, never a secret. The move then fails with {@code credential_store_failed}.
         * <p>
         * The component is {@code CredentialStoreMove}.
         */
        public static final LogRecord CREDENTIAL_STORE_MOVE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(129)
                .template("Credential store move from '%s' to '%s' failed before the switch, %s: %s")
                .build();

        /**
         * Logged when a member of a job's process group still lived at the end of the termination grace period (5
         * seconds) after {@code SIGTERM}, also when only descendants of the job remained, and the group was sent
         * {@code SIGKILL}; names the job id, the process group and the cause of the termination ({@code ENGINE_CANCEL},
         * {@code PAUSE_NOW}, {@code SUPERVISOR_END}, {@code BUDGET_EXPIRED} or {@code DRAIN}). The fate follows from the
         * cause, not from the signal.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_TERMINATION_ESCALATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(130)
                .template("Job '%s' outlived the grace period after SIGTERM, process group %s sent SIGKILL (cause '%s')")
                .build();

        /**
         * Logged when the launcher {@code pm-exec} of a job cannot be started (missing from {@code <PM_MCP_HOME>/bin},
         * not executable, or its job log cannot be created), with the exception, or when the launch of a verified job
         * cannot be composed (for example an environment the operating system refuses), before the exception is
         * passed on to the caller of the start; names the job id and the reason. The job had no process and is settled
         * by row 10 as the launcher's exit {@code 127} ({@code failed}, {@code failed_unparsed} for a build job), unless
         * a cancel was answered {@code true} before the settlement closed the job's cancel door, whose cause then
         * decides it ({@code cancelled}, row 2; a cancel after the door closed is answered {@code false}).
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_LAUNCHER_NOT_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(131)
                .template("Launcher of job '%s' not started, the job had no process: %s")
                .build();

        /**
         * Logged when the termination protocol fails with an exception (a PID it refuses, or a signal call that
         * failed) for a job whose end the runner has not observed: a job whose post-spawn record could not be written,
         * or whose waiting thread failed before it saw the end of the process; names the job id and the reason, with
         * the exception. The process is destroyed through its process handle, so it is never left running, and the job
         * is settled from what the runner observed; the failure is not passed on, so it does not replace the failure
         * the settlement follows.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_TERMINATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(132)
                .template("Termination of job '%s' failed, its process is destroyed through its handle: %s")
                .build();

        /**
         * Logged when the first recording of a credential store fails before the switch; names the store being
         * recorded, the other store (the one the key is deleted from), what became of the copy and of the record
         * (nothing was recorded and the other store keeps the key; or the copy could not be removed, so the record is
         * kept and the next start removes it and records again), and the reason. Never names a key or a credential
         * value.
         * <p>
         * The component is {@code CredentialStoreChoice}.
         */
        public static final LogRecord CREDENTIAL_STORE_FIRST_RECORDING_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(133)
                .template("First recording of credential store '%s' from '%s' failed, %s: %s")
                .build();

        /**
         * Logged when one of the two stores cannot be read while the first recording reconciles the integrity key, an
         * OS store in a transient outage say; names the store and the reason. The store then counts as holding no
         * key: where it is the store being recorded and the other store holds a key the recording is refused, and
         * where it is the other store the key it holds, if any, stays there.
         * <p>
         * The component is {@code CredentialStoreChoice}.
         */
        public static final LogRecord CREDENTIAL_STORE_UNREADABLE_WHILE_RECORDING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(134)
                .template("Credential store '%s' cannot be read while a store is recorded, taken as holding no integrity key: %s")
                .build();

        /**
         * Logged when a restore fails. A failure before the kept pair is dropped (the unit cannot be read, written
         * back or removed, or the marker cannot be deleted) leaves the pair for the next start, which tries the
         * restore again; when only the copy cannot be dropped, or the restored unit cannot be loaded after the drop,
         * no later start retries it. Names the unit file and the failure; {@code install} reports the restore's failure
         * suppressed under the load's failure.
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_UNIT_RESTORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(135)
                .template("Service unit '%s' could not be restored: %s")
                .build();

        /**
         * Logged when the marker or the kept copy of a reinstall cannot be deleted after the new unit was loaded;
         * names the unit file and the failure. The install stays a success. When the marker stays, the next start
         * treats the reinstall as unfinished: it restores the kept unit, or removes the unit file when the reinstall
         * kept none. When only the copy stays, the next start does nothing and the next install overwrites the copy.
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_UNIT_KEPT_NOT_DROPPED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(136)
                .template("The kept state of service unit '%s' could not be removed after a successful load: %s")
                .build();

        /**
         * Logged when the security audit record of a descendant that outlived the sweep of a probe's process tree
         * ({@code job_descendant_survived}) cannot be written; names the job and the failure. The sweep has sent its
         * signals and the job is settled regardless.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_DESCENDANT_AUDIT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(137)
                .template("The surviving descendant of job '%s' could not be audited: %s")
                .build();

        /**
         * Logged when the sweep of a job's remembered descendants fails (a signal call or a process-table read that
         * throws); names the job and the failure. The sweep is not retried and the job is settled regardless, so a
         * failing sweep never blocks the settlement.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_DESCENDANT_SWEEP_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(138)
                .template("The descendant sweep of job '%s' failed, the job is settled without it: %s")
                .build();

        /**
         * Logged when a worker launch is refused before its spawn: names the harness, the refusal reason
         * ({@code harness_not_enrolled}, {@code harness_update_unverified}, {@code harness_version_unsupported},
         * {@code harness_tampered}, {@code harness_store_unreadable}), the detail and the act that resolves it. The
         * refusal counts as a start failure of the role on that harness; a tampered store also writes the
         * {@code security} record {@code enrolment_tampered}.
         * <p>
         * The component is {@code HarnessVerifier}.
         */
        public static final LogRecord WORKER_LAUNCH_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(139)
                .template("Launch of a worker of harness '%s' refused '%s': %s; resolve it by: %s")
                .build();

        /**
         * Logged when the start-failure guard counted a failed start of a role on a harness: a worker that exited
         * before its first call, or a launch refused before its spawn. Names the role, the harness, the consecutive
         * failure number and the harness's own error text, bounded and redacted like every job log.
         * <p>
         * The component is {@code StartFailureGuard}.
         */
        public static final LogRecord WORKER_START_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(140)
                .template("Start of a worker of role '%s' on harness '%s' failed (consecutive failure %s): %s")
                .build();

        /**
         * Logged when the start-failure guard holds, so the supervisor starts no worker of the role on that harness
         * and the role's queued and new tasks close {@code lost} with the reason {@code role_unavailable}; names the
         * role, the harness and the failures it counted. The guard clears when the role's configuration changes or the
         * harness is enrolled again.
         * <p>
         * The component is {@code StartFailureGuard}.
         */
        public static final LogRecord WORKER_START_GUARD_HELD = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(141)
                .template("Start-failure guard of role '%s' on harness '%s' holds after %s consecutive failures")
                .build();

        /**
         * Logged when a process of a worker's recorded process tree runs an enrolled harness executable or the
         * operator binary {@code pm-operator}, compared by path and digest: names the worker generation, the role, the
         * executable and its digest, never a command line. The fixed action is {@code restart}; the
         * {@code agent_started} security audit record is the task protocol's.
         * <p>
         * The component is {@code AgentStartedDetector}.
         */
        public static final LogRecord AGENT_STARTED_DETECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(142)
                .template("Agent started in the process tree of worker generation '%s' of role '%s': '%s' (%s)")
                .build();

        /**
         * Logged when a task on an endpoint harness ends without a valid answer — an answer that does not validate, a
         * refusal text, an HTTP error or a timeout: names the model, the endpoint and the reason, never the task text.
         * The task's own rules then apply, so a screening leaves its sources unscreened.
         * <p>
         * The component is {@code EndpointHarness}.
         */
        public static final LogRecord ENDPOINT_ATTEMPT_ENDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(143)
                .template("The task of model '%s' on the endpoint '%s' ended: %s")
                .build();

        /**
         * Logged when an endpoint does not answer its readiness check, before it is used: names the endpoint and the
         * readiness path it asked. An endpoint that does not answer it counts as a start failure.
         * <p>
         * The component is {@code EndpointHarness}.
         */
        public static final LogRecord ENDPOINT_NOT_READY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(144)
                .template("The endpoint '%s' did not answer its readiness check at '%s'")
                .build();

        /**
         * Logged when a provider failed at least one check of {@code providers.verify}: names the provider and the
         * message keys of the failed check lines, never the credential. A failed provider is recorded only on the
         * operator's word, and then as not available.
         * <p>
         * The component is {@code ProviderVerification}.
         */
        public static final LogRecord PROVIDER_VERIFICATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(145)
                .template("Provider '%s' failed its verification: %s")
                .build();

        /**
         * Logged when {@code store.move} failed: names the failure, which the aspect reports as
         * {@code credential_store_failed}. A failure after the switch leaves the target store recorded, because an entry
         * cannot be deleted from the old store after the switch, which the next start completes; no entry or secret is
         * named.
         * <p>
         * The component is {@code CredentialStoreAspect}.
         */
        public static final LogRecord CREDENTIAL_STORE_ASPECT_MOVE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(146)
                .template("The credential store move of the aspect failed: %s")
                .build();

        /**
         * Logged when the harness icon store refuses a candidate icon: names the harness, the refusal code and the
         * candidate's length in bytes, never a byte of the candidate itself. Nothing is stored; the caller receives the
         * refusal.
         * <p>
         * The component is {@code HarnessIconStore}.
         */
        public static final LogRecord ICON_REFUSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(147)
                .template("Icon candidate of harness '%s' refused with '%s' at %d bytes")
                .build();
    }

    /** ERROR level messages. */
    @UtilityClass
    public static final class ERROR {

        /**
         * Logged when the bound socket cannot be set to mode {@code 0600} or {@code state/runtime.json} cannot be
         * written; the runtime exits with code {@code 77}.
         * <p>
         * The component is {@code SocketBinding} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord SOCKET_NOT_SECURED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Socket '%s' could not be secured or recorded, exiting with code 77")
                .build();

        /**
         * Logged when a request of {@code /api/v1/credentials/{key}} fails in the backend (locked keyring, insecure
         * or invalid credential file, keyring error); never contains the secret.
         * <p>
         * The component is {@code CredentialsResource} of the assembly {@code pm-mcp-server}.
         */
        public static final LogRecord CREDENTIAL_STORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(201)
                .template("Credential store '%s' failed: %s")
                .build();

        /**
         * Logged when a lock is requested against the total lock order; the request is refused with the internal
         * fault {@code lock_order_violation}, nothing is acquired, and a {@code lock_order_violation} audit record is
         * written.
         * <p>
         * The component is {@code FileLockManager}.
         */
        public static final LogRecord LOCK_ORDER_VIOLATION = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(202)
                .template("Lock order violation: '%s' requested while holding %s")
                .build();

        /**
         * Logged when a step of the service manager fails during install or uninstall of the per-user service unit;
         * names the step ({@code load}, {@code disable} or {@code unload}) and the failure (the command with its exit
         * code and output, or why it could not run), never an environment value; the failure is reported to the
         * caller.
         * <p>
         * The component is {@code ServiceUnitInstaller}.
         */
        public static final LogRecord SERVICE_MANAGER_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(203)
                .template("Service manager command '%s' failed: %s")
                .build();

        /**
         * Logged when a step of the per-project recovery fails, or throws instead of reporting its failure; names the
         * project id, the step and the reason. A recovery the executor rejects is recorded under the first step, a
         * failure after the last step under the last one. The recovery of that project stops there; other projects are
         * unaffected.
         * <p>
         * The component is {@code ProjectRecovery}.
         */
        public static final LogRecord PROJECT_RECOVERY_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(204)
                .template("Recovery of project '%s' failed in step '%s': %s")
                .build();

        /**
         * Logged when a scheduled run of the periodic sweep fails outside its steps (reading the enrolled projects,
         * the recovery state of a project, the clock); names the failure. The schedule stays alive and the next run
         * takes place at the interval.
         * <p>
         * The component is {@code PeriodicSweep}.
         */
        public static final LogRecord PERIODIC_SWEEP_RUN_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(205)
                .template("Periodic sweep run failed, the next run takes place at the interval: %s")
                .build();

        /**
         * Logged when the enrolment integrity key exists and cannot be read, or cannot be created: an insecure or
         * malformed key file, or a failing credential store; names the source (the key file
         * {@code state/enrolment-hmac.key}, or the credential store with the account {@code enrolment-integrity-key})
         * and the reason code, never the key. The read or the write that needed the key fails.
         * <p>
         * The component is {@code EnrolmentKey}.
         */
        public static final LogRecord ENROLMENT_KEY_UNAVAILABLE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(206)
                .template("Enrolment integrity key unavailable from '%s': %s")
                .build();

        /**
         * Logged when a step of the settlement of a job fails (the fate cannot be written, the slot cannot be
         * released); names the job id, the step and the reason. No later step runs, so a released slot never precedes
         * the recorded fate; a job whose fate was not written stays {@code running} in its record and is settled
         * {@code killed} by the recovery. Logged by the runner only, for the settlement of its own jobs: a settlement
         * the recovery of a project runs ({@code JobRecoveryStep}) and that stops fails the recovery step, which is
         * logged as {@code PM_RUNTIME-204}.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_SETTLEMENT_STEP_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(207)
                .template("Settlement of job '%s' stopped at step '%s': %s")
                .build();

        /**
         * Logged by the handler of the virtual thread that waits for a job when that thread ends with an exception or
         * an error (for example of the output parser, of the termination protocol, or of the settlement itself), with
         * the failure; names the job id and the failure. A settlement from what the runner observed may have run
         * before it: after the end of the process, the job is settled without the parser's finding; before it, the
         * process is ended through its handle and the job is settled from what the runner observed then. That
         * settlement is not a guarantee: a fault of the lock manager inside the settlement can be the failure itself
         * or can precede the fallback, and a fallback that fails is followed by a last-resort ended record
         * ({@code PM_RUNTIME-209} if that fails too). The job has left the running jobs.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_SUPERVISION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(208)
                .template("Supervision of job '%s' failed: %s")
                .build();

        /**
         * Logged when the fallback settlement of a job whose supervision failed threw and the last-resort attempt
         * could not write its record either: the record the fallback was about to write is retried once, and when the
         * derivation itself failed, a minimal ended record ({@code indeterminate}, row 5) is written; names the job and
         * the failure. The record stays {@code running} and the recovery of the next runtime start settles it.
         * <p>
         * The component is {@code SubprocessJobRunner}.
         */
        public static final LogRecord JOB_LAST_RESORT_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(209)
                .template("Last-resort settlement of job '%s' failed: %s")
                .build();
    }
}
