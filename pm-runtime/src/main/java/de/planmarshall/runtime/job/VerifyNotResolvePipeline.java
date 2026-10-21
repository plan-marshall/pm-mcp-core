/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import de.cuioss.tools.logging.CuiLogger;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.ConfinementOutcome.Confined;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.WorkspaceRoots;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentCheck;
import de.planmarshall.runtime.job.JobRefusal.Reason;
import de.planmarshall.runtime.job.JobRequest.ProjectScriptCall;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.workspace.WorkspaceConfinement;

/**
 * The verify-not-resolve pipeline of a job (PM-EXEC-1, PM-EXEC-2, PM-EXEC-3, PM-SEC-2): turns a {@link JobRequest}
 * into a {@link JobVerification}, deterministically and without resolving anything at launch.
 * <p>
 * The stages run in this order, and the first stage that refuses decides; a later stage never runs:
 * <ol>
 * <li>stage 0, the enrolment check: {@link EnrolmentCheck#verify(LockTransaction, String)} under the caller's
 * transaction; its refusal is mapped to {@code enrolment_tampered}, {@code enrolment_insecure},
 * {@code enrolment_invalid} (a refused strict read of the file or the sidecar, or a violated enrolment rule) or
 * {@code not_enrolled} (neither the file nor its sidecar exists);</li>
 * <li>stage 1, the working directory: confined by {@link WorkspaceConfinement} to the enrolled {@code project_root}
 * and the live linked worktrees the caller verified, refused as {@code path_traversal_rejected}; the verified job runs
 * in the canonical path of the outcome;</li>
 * <li>stage 2, the executable and its digest: {@link ExecutableCheck}, for a wrapper job with
 * {@link WrapperVerification} over the wrapper and its pinned files ({@code executable_not_enrolled},
 * {@code executable_digest_mismatch}, {@code build_config_changed}); it answers the verified absolute start of the
 * argument vector;</li>
 * <li>stage 3, the argument check, last: {@link ArgumentCheck} with the caller's {@link DefaultTemplates}
 * ({@code verb_not_supported}, {@code argument_rejected}); it answers the arguments after the executable and, for a
 * build job, the source of its template; the validated arguments of a project script job are carried by the verified
 * job as {@link VerifiedJob#projectScriptArguments()}, not in its argument vector.</li>
 * </ol>
 * <p>
 * The lock scope belongs to the caller. Stage 0 acquires the project's enrolment lock in the caller's
 * {@link LockTransaction}, and a transaction releases its locks only all together, when the caller closes it; this
 * pipeline cannot release the enrolment lock between stage 0 and stage 3 without closing the caller's transaction.
 * Stages 1 to 3 therefore run under the enrolment lock whenever the caller's transaction is still open. Stage 3 matches
 * an enrolled {@code pattern} only against a string that the {@code maxLength} of the same schema has bounded, and
 * refuses a {@code pattern} without {@code maxLength} as {@code argument_rejected} before any regular expression is
 * evaluated, so the time stage 3 holds the lock is bounded by the enrolment. A caller closes its transaction as soon as
 * this method returns: it must not keep the enrolment lock while it launches or waits for the verified job.
 * <p>
 * A refusal is a value. Each refusal is logged once at WARN ({@code PM_RUNTIME-122}); a refusal of the enrolment that
 * the enrolment check has logged and audited itself is not logged again. A fault is no refusal and passes through:
 * {@link SecretStoreException} when the integrity key cannot be read, {@link InternalFaultException} when the
 * enrolment lock must not be acquired after the locks the transaction holds, {@link UncheckedIOException} when a file
 * cannot be read.
 *
 * @since 0.1
 */
public final class VerifyNotResolvePipeline {

    private static final CuiLogger LOGGER = new CuiLogger(VerifyNotResolvePipeline.class);

    /** The refusals of the enrolment check that it logs and audits itself. */
    private static final Set<StoreRefusal.Outcome> LOGGED_BY_ENROLMENT_CHECK = EnumSet.of(
            StoreRefusal.Outcome.ENROLMENT_TAMPERED, StoreRefusal.Outcome.ENROLMENT_INSECURE,
            StoreRefusal.Outcome.UNREADABLE, StoreRefusal.Outcome.FORMAT_OUTDATED, StoreRefusal.Outcome.FORMAT_NEWER);

    private final EnrolmentCheck enrolmentCheck;
    private final DefaultTemplates defaults;

    /**
     * @param enrolmentCheck the enrolment check of stage 0
     * @param defaults       the default templates of the build verbs, used by stage 3 when the enrolment holds none
     */
    public VerifyNotResolvePipeline(EnrolmentCheck enrolmentCheck, DefaultTemplates defaults) {
        this.enrolmentCheck = Objects.requireNonNull(enrolmentCheck, "enrolmentCheck");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    /**
     * Verifies a job. The project's enrolment lock is acquired in the transaction if it does not hold it yet.
     *
     * @param transaction the transaction of the caller
     * @param request     the job as the caller asks for it
     * @return the verified job, or the refusal of the first stage that refused it
     * @throws SecretStoreException     if the integrity key exists and cannot be read
     * @throws InternalFaultException   if the enrolment lock must not be acquired after the locks the transaction
     *                                  holds
     * @throws UncheckedIOException     if a file cannot be read
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public JobVerification verify(LockTransaction transaction, JobRequest request) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(request, "request");
        var projectId = request.projectId();

        ProjectEnrolment enrolment;
        try {
            enrolment = enrolmentCheck.verify(transaction, projectId);
        } catch (StoreRefusal e) {
            var refusal = enrolmentRefusal(projectId, e);
            return LOGGED_BY_ENROLMENT_CHECK.contains(e.outcome()) ? new JobVerification.Refused(refusal)
                    : refused(projectId, refusal);
        }

        var roots = new WorkspaceRoots(Path.of(enrolment.projectRoot()), request.liveWorktrees());
        var confinement = new WorkspaceConfinement(roots).confine(request.workingDirectory());
        if (confinement instanceof Rejected(var rejection)) {
            return refused(projectId, JobRefusal.of(Reason.PATH_TRAVERSAL_REJECTED,
                    "the working directory lies outside the workspace of the project: "
                            + rejection.name().toLowerCase(Locale.ROOT)));
        }
        var workingDirectory = ((Confined) confinement).path();

        List<String> executable;
        switch (ExecutableCheck.check(projectId, enrolment, workingDirectory, request.target())) {
            case Stage.Failed<List<String>>(var refusal) -> {
                return refused(projectId, refusal);
            }
            case Stage.Passed<List<String>>(var start) -> executable = start;
        }

        ArgumentCheck.Arguments arguments;
        switch (ArgumentCheck.check(enrolment, defaults, request)) {
            case Stage.Failed<ArgumentCheck.Arguments>(var refusal) -> {
                return refused(projectId, refusal);
            }
            case Stage.Passed<ArgumentCheck.Arguments>(var checked) -> arguments = checked;
        }

        var argv = new ArrayList<>(executable);
        argv.addAll(arguments.arguments());
        return new JobVerification.Verified(new VerifiedJob(workingDirectory, argv, arguments.source(),
                projectScriptArguments(request.target()), enrolment));
    }

    /** The arguments of a project script job, which stage 3 validated; empty for any other job. */
    private static Optional<JobRequest.Value> projectScriptArguments(JobRequest.Target target) {
        return target instanceof ProjectScriptCall call ? Optional.of(call.arguments()) : Optional.empty();
    }

    /**
     * The outcome of a stage after stage 1: the value the stage verified, or its refusal.
     *
     * @param <T> what the stage verifies
     * @since 0.1
     */
    // T is a phantom type parameter: it types Passed<T>.value() for the pattern matches on a Stage<T>, and the
    // interface itself needs no member of type T.
    @SuppressWarnings("java:S2326")
    sealed interface Stage<T> permits Stage.Passed, Stage.Failed {

        /**
         * @param <T>   what the stage verifies
         * @param value what the stage verified
         * @return the outcome of a stage that passed
         */
        static <T> Stage<T> passed(T value) {
            return new Passed<>(value);
        }

        /**
         * @param <T>     what the stage verifies
         * @param refusal why the stage refused the job
         * @return the outcome of a stage that refused
         */
        static <T> Stage<T> failed(JobRefusal refusal) {
            return new Failed<>(refusal);
        }

        /**
         * The stage passed.
         *
         * @param <T>   what the stage verifies
         * @param value what it verified
         */
        record Passed<T>(T value) implements Stage<T> {

            public Passed {
                Objects.requireNonNull(value, "value");
            }
        }

        /**
         * The stage refused the job.
         *
         * @param <T>     what the stage verifies
         * @param refusal why
         */
        record Failed<T>(JobRefusal refusal) implements Stage<T> {

            public Failed {
                Objects.requireNonNull(refusal, "refusal");
            }
        }
    }

    /** Maps a refusal of the enrolment check to the refusal of the job; the detail never names an operator act. */
    private static JobRefusal enrolmentRefusal(String projectId, StoreRefusal refusal) {
        var detail = "the enrolment of project '%s' was refused (%s)".formatted(projectId,
                refusal.outcome().code());
        return switch (refusal.outcome()) {
            case ABSENT -> JobRefusal.of(Reason.NOT_ENROLLED, "the project has no enrolment");
            case ENROLMENT_TAMPERED -> JobRefusal.enrolmentTampered(detail);
            case ENROLMENT_INSECURE -> JobRefusal.of(Reason.ENROLMENT_INSECURE, detail);
            case UNREADABLE, FORMAT_OUTDATED, FORMAT_NEWER, ENROLMENT_INVALID ->
                JobRefusal.of(Reason.ENROLMENT_INVALID, detail);
        };
    }

    /** Logs the refusal once and answers it. */
    private static JobVerification refused(String projectId, JobRefusal refusal) {
        var detail = refusal.changedPaths().isEmpty() ? refusal.detail()
                : "%s; changed: %s".formatted(refusal.detail(), String.join(", ", refusal.changedPaths()));
        LOGGER.warn(WARN.JOB_REFUSED, projectId, refusal.reason().code(), detail);
        return new JobVerification.Refused(refusal);
    }
}
