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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Map;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * Steps 1 to 5 of the runtime's startup sequence, run before the HTTP server binds the socket
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Startup Sequence).
 * <ol>
 * <li>Acquire the singleton lock; a runtime that loses writes nothing.</li>
 * <li>Remove a stale {@code run/runtime.sock} and {@code state/runtime.json}, only while holding the lock.</li>
 * <li>Check the socket path length, create and verify the directories ({@link BaseDirectoryCheck}).</li>
 * <li>Bounded verification: work whose cost does not grow with the number of projects, plans or jobs. The audit
 * chain is verified through {@link AuditChainVerification}; a broken chain is logged, handed to the audit sink as an
 * {@value #AUDIT_CHAIN_BROKEN} record and carried in {@link Outcome.Ready}, never repaired, and the start goes on. No
 * store or unit work is done: no enrolment, plan or job file is read.</li>
 * <li>Write a fresh runtime token.</li>
 * </ol>
 * Steps 2 to 5 run only while the singleton lock is held. Steps 6 (bind, runtime record) and 7 (ready) run inside
 * Quarkus ({@link SocketBinding}).
 *
 * @param paths      the machine paths
 * @param user       the name of the running user, the expected owner of the base
 * @param auditChain the audit chain verification of step 4; {@link AuditChainVerification#NONE} while no chain exists
 * @param auditSink  where step 4 hands over the record of a broken audit chain
 * @since 0.1
 */
public record StartupSequence(MachinePaths paths, String user, AuditChainVerification auditChain,
AuditSink auditSink) {

    private static final CuiLogger LOGGER = new CuiLogger(StartupSequence.class);

    /** The name of the event that records a broken audit chain found by the bounded verification. */
    public static final String AUDIT_CHAIN_BROKEN = "audit_chain_broken";

    /** The category of the {@value #AUDIT_CHAIN_BROKEN} record. */
    public static final String CATEGORY_SECURITY = "security";

    /** The outcome of the {@value #AUDIT_CHAIN_BROKEN} record: the verification failed. */
    public static final String OUTCOME_FAILED = "failed";

    /** The detail of the {@value #AUDIT_CHAIN_BROKEN} record that holds the diagnostic of the verification. */
    public static final String DETAIL_DIAGNOSTIC = "diagnostic";

    /**
     * The result of the steps before the bind.
     */
    public sealed interface Outcome {

        /**
         * The runtime holds the lock, the base is verified and the token written.
         *
         * @param lock         the held singleton lock
         * @param verification the outcome of the audit chain verification of step 4, for the status to show
         */
        record Ready(RuntimeLock lock, AuditChainVerification.Outcome verification) implements Outcome {
        }

        /** Another runtime holds the singleton lock; nothing was written. */
        record LockHeld() implements Outcome {
        }

        /**
         * A permission violation or an over-long socket path refuses the start (exit code 77).
         *
         * @param diagnostic the diagnostic naming the path
         */
        record Refused(String diagnostic) implements Outcome {
        }
    }

    /** Exit code of a refused start ({@code EX_NOPERM}). */
    public static final int EXIT_REFUSED = 77;

    /**
     * Runs steps 1 to 5.
     *
     * @param random the source of the runtime token
     * @return the outcome; on {@link Outcome.Ready} the caller keeps the lock for the process lifetime
     * @throws IOException if a file operation fails
     */
    public Outcome run(SecureRandom random) throws IOException {
        var acquired = RuntimeLock.tryAcquire(paths.runtimeLock());
        if (acquired.isEmpty()) {
            return new Outcome.LockHeld();
        }
        var lock = acquired.get();
        try {
            Files.deleteIfExists(paths.socket());
            Files.deleteIfExists(paths.runtimeRecord());
            var violation = new BaseDirectoryCheck(paths, user).run();
            if (violation.isPresent()) {
                lock.close();
                return new Outcome.Refused(violation.get());
            }
            var verification = boundedVerification();
            new RuntimeTokenFile(paths.runtimeToken()).writeFresh(random);
            return new Outcome.Ready(lock, verification);
        } catch (IOException e) {
            lock.closeAfterFailure(e);
            throw e;
        }
    }

    /**
     * Step 4: verifies the audit chain, records a broken chain and goes on whatever the outcome.
     */
    private AuditChainVerification.Outcome boundedVerification() {
        var verification = auditChain.verify();
        if (verification instanceof AuditChainVerification.Outcome.Broken(var diagnostic)) {
            LOGGER.warn(WARN.AUDIT_CHAIN_BROKEN, diagnostic);
            recordBrokenChain(diagnostic);
        }
        LOGGER.info(INFO.BOUNDED_VERIFICATION_FINISHED, describe(verification));
        return verification;
    }

    private void recordBrokenChain(String diagnostic) {
        var event = new AuditEvent(AUDIT_CHAIN_BROKEN, CATEGORY_SECURITY, OUTCOME_FAILED,
                Map.of(DETAIL_DIAGNOSTIC, diagnostic));
        try {
            auditSink.append(event);
        } catch (UncheckedIOException e) {
            LOGGER.warn(e, WARN.AUDIT_CHAIN_BROKEN_NOT_RECORDED, e.getMessage());
        }
    }

    private static String describe(AuditChainVerification.Outcome verification) {
        return switch (verification) {
            case AuditChainVerification.Outcome.Intact() -> "intact";
            case AuditChainVerification.Outcome.Broken(var diagnostic) -> "broken (" + diagnostic + ")";
            case AuditChainVerification.Outcome.NotVerified(var reason) -> "not verified (" + reason + ")";
        };
    }
}
