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

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The child of the killed-runtime tests ({@link KilledRuntimeFixture}): it runs one named lifecycle step against the
 * {@code PM_MCP_BASE} it is given, writes a marker line before the step and one inside it, and then blocks so that
 * it can be killed in the middle of the step.
 * <p>
 * Its arguments are the mode, which names the step, and the base directory. The marker lines are the protocol with
 * the test that started it and not log messages, so they go to the file descriptor itself. A blocked child waits for
 * the end of its standard input, which comes when the test JVM dies; it then halts at once, so it never finishes a
 * step on its own and never outlives the test.
 * <p>
 * Modes:
 * <ul>
 * <li>{@value #STARTUP}: steps 1 to 5 of the {@link StartupSequence}, blocked inside step 4 while the lock is held
 * and before the token is written.</li>
 * <li>{@value #STARTUP_READY}: steps 1 to 5 run to {@link StartupSequence.Outcome.Ready}; blocked after them with the
 * lock held and the token written.</li>
 * <li>{@value #DRAIN}: steps 1 to 5, a socket and a runtime record as steps 6 and 7 leave them, then a
 * {@link RuntimeDrain} blocked in the drain wait on a participant that never gets quiet.</li>
 * <li>{@value #RECOVERY}: a {@link ProjectRecovery} of project {@value #RECOVERED_PROJECT} by the background pass; its
 * first step appends a line to the marker file {@link #recoveryRuns(Path)} and its second step blocks.</li>
 * <li>{@value #LEASES}: a live runtime holding one build-slot lease of each of the projects of
 * {@link RecoveryStepFixtures}, blocked after the claims.</li>
 * <li>{@value #ROLLFORWARD}: blocked inside a scope transaction of the plan {@value #ROLLED_PLAN} of
 * {@link RecoveryStepFixtures#PROJECT_A}, with the scope lock held, after the journal was written and before the
 * rename.</li>
 * <li>{@value #ORDERED_WRITE}: an {@link de.planmarshall.core.store.OrderedWrite} from the plan {@value #ROLLED_PLAN}
 * to the plan {@value #DERIVED_PLAN}, blocked between the authoritative and the derived write.</li>
 * <li>{@value #SWEEP}: a {@link PeriodicSweep} run whose step ({@link #removingStep(Path, Runnable)}) removes the
 * aged records of {@link #pendingRecords(Path)} one at a time, blocked after the first removal.</li>
 * <li>{@value #VERSION}: a {@link RelayVersionCheck} of runtime version {@value #CHILD_RUNTIME_VERSION} that observed
 * the mismatching relay version {@value #MISMATCHING_RELAY} into the file-backed sink {@link #recordingSink(Path)},
 * blocked after the record.</li>
 * </ul>
 */
final class RuntimeChildProcess {

    /** Mode: blocked inside step 4 of the startup sequence. */
    static final String STARTUP = "startup";

    /** Mode: blocked after the startup sequence reached ready. */
    static final String STARTUP_READY = "startup-ready";

    /** Mode: blocked in the drain wait on a participant that never gets quiet. */
    static final String DRAIN = "drain";

    /** Mode: blocked inside a step of the per-project recovery. */
    static final String RECOVERY = "recovery";

    /** The project the mode {@value #RECOVERY} recovers. */
    static final String RECOVERED_PROJECT = "project-a";

    /** Mode: a live runtime holding leases of two projects. */
    static final String LEASES = "leases";

    /** Mode: blocked inside a scope transaction after its journal was written. */
    static final String ROLLFORWARD = "rollforward";

    /** Mode: blocked between the authoritative and the derived write of an ordered write. */
    static final String ORDERED_WRITE = "ordered-write";

    /** The plan whose scope the modes {@value #ROLLFORWARD} and {@value #ORDERED_WRITE} write first. */
    static final String ROLLED_PLAN = "plan-a";

    /** The plan the mode {@value #ORDERED_WRITE} derives. */
    static final String DERIVED_PLAN = "plan-b";

    /** Mode: blocked in a periodic sweep after its first removal. */
    static final String SWEEP = "sweep";

    /** Mode: blocked after a relay version mismatch was recorded. */
    static final String VERSION = "version";

    /** The runtime version of the mode {@value #VERSION}. */
    static final String CHILD_RUNTIME_VERSION = "0.4.0";

    /** The relay version the mode {@value #VERSION} observes, different from {@value #CHILD_RUNTIME_VERSION}. */
    static final String MISMATCHING_RELAY = "0.3.2";

    /** The content the modes {@value #ROLLFORWARD} and {@value #ORDERED_WRITE} write. */
    static final byte[] WRITTEN = "{\"state\":\"written\"}".getBytes(StandardCharsets.UTF_8);

    /** Exit code of a child whose standard input ended while it was blocked. */
    static final int EXIT_ORPHANED = 3;

    private static final String BEFORE = "before";
    private static final String INSIDE = "inside";
    private static final String READY = "ready";

    private RuntimeChildProcess() {
        // entry point only
    }

    /**
     * @param mode the mode
     * @return the line written before the step of the mode
     */
    static String before(String mode) {
        return BEFORE + " " + mode;
    }

    /**
     * @param mode the mode
     * @return the line written inside the step of the mode
     */
    static String inside(String mode) {
        return INSIDE + " " + mode;
    }

    /**
     * @param mode the mode
     * @return the line written when the step of the mode has reached its end state
     */
    static String ready(String mode) {
        return READY + " " + mode;
    }

    /**
     * @param args the mode and the base directory of the runtime
     * @throws IOException if the step fails
     */
    public static void main(String[] args) throws IOException {
        var mode = args[0];
        var paths = new MachinePaths(Path.of(args[1]), MachinePaths.Os.current());
        var markers = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        switch (mode) {
            case STARTUP -> startup(paths, markers, () -> {
                markers.println(inside(STARTUP));
                return awaitKill();
            }, STARTUP);
            case STARTUP_READY -> startup(paths, markers, AuditChainVerification.NONE, STARTUP_READY);
            case DRAIN -> drain(paths, markers);
            case RECOVERY -> recovery(paths, markers);
            case LEASES -> leases(paths.base(), markers);
            case ROLLFORWARD -> rollForward(paths.base(), markers);
            case ORDERED_WRITE -> orderedWrite(paths.base(), markers);
            case SWEEP -> sweep(paths.base(), markers);
            case VERSION -> version(paths.base(), markers);
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    /**
     * @param base the base directory of the runtime
     * @return the directory of the fixture records the mode {@value #SWEEP} sweeps
     */
    static Path pendingRecords(Path base) {
        return base.resolve("pending-records");
    }

    /**
     * The step of the mode {@value #SWEEP}, also used by the run of the next instance: it removes, in the order of
     * their names, the fixture records whose modification time lies before the instant it is given, one at a time,
     * and runs the hook after each removal. A younger record is never removed.
     *
     * @param base         the base directory of the runtime
     * @param afterRemoval runs after each removal
     * @return the removing step
     */
    static SweepStep removingStep(Path base, Runnable afterRemoval) {
        return new SweepStep() {

            @Override
            public Name name() {
                return Name.PENDING_ENTRY_RECORDS;
            }

            @Override
            public RecoveryStep.Outcome run(String projectId, Instant olderThan) {
                try (var listing = Files.list(pendingRecords(base))) {
                    var removed = 0;
                    for (var pendingRecord : listing.sorted().toList()) {
                        if (Files.getLastModifiedTime(pendingRecord).toInstant().isBefore(olderThan)) {
                            Files.delete(pendingRecord);
                            removed++;
                            afterRemoval.run();
                        }
                    }
                    return new RecoveryStep.Outcome.Done(removed + " removed");
                } catch (IOException e) {
                    return new RecoveryStep.Outcome.Failed("records not swept: " + e);
                }
            }
        };
    }

    /**
     * @param base the base directory of the runtime
     * @return the file to which {@link #recordingSink(Path)} appends one line per audit record
     */
    static Path versionRecords(Path base) {
        return base.resolve("version-records");
    }

    /**
     * The audit sink of the mode {@value #VERSION}, also used by the next instance: it appends each record as the line
     * {@code "event relay_version runtime_version"} to {@link #versionRecords(Path)}.
     *
     * @param base the base directory of the runtime
     * @return the recording sink
     */
    static AuditSink recordingSink(Path base) {
        return event -> {
            var line = event.event() + " " + event.details().get(RelayVersionCheck.DETAIL_RELAY_VERSION) + " "
                    + event.details().get(RelayVersionCheck.DETAIL_RUNTIME_VERSION) + System.lineSeparator();
            try {
                Files.writeString(versionRecords(base), line, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
    }

    private static void version(Path base, PrintStream markers) {
        markers.println(before(VERSION));
        new RelayVersionCheck(CHILD_RUNTIME_VERSION, recordingSink(base)).observe(MISMATCHING_RELAY);
        markers.println(inside(VERSION));
        awaitKill();
    }

    private static void sweep(Path base, PrintStream markers) {
        markers.println(before(SWEEP));
        new PeriodicSweep(() -> List.of(RecoveryStepFixtures.PROJECT_A), _ -> true,
                List.of(removingStep(base, () -> {
                    markers.println(inside(SWEEP));
                    awaitKill();
                })), Clock.systemUTC()).runOnce(Instant.now());
        throw new IllegalStateException("the sweep ended although its step blocks");
    }

    private static void leases(Path base, PrintStream markers) throws IOException {
        markers.println(before(LEASES));
        var store = RecoveryStepFixtures.buildSlots(base, new FileLockManager(_ -> {
        }));
        var self = RecoveryStepFixtures.self();
        RecoveryStepFixtures.claim(store, "slot-a", RecoveryStepFixtures.PROJECT_A, self);
        RecoveryStepFixtures.claim(store, "slot-b", RecoveryStepFixtures.PROJECT_B, self);
        markers.println(inside(LEASES));
        awaitKill();
    }

    private static void rollForward(Path base, PrintStream markers) throws IOException {
        markers.println(before(ROLLFORWARD));
        var scope = JournalScope.plan(base, RecoveryStepFixtures.PROJECT_A, ROLLED_PLAN);
        // The transaction stays open: the child dies holding the scope lock, as a runtime killed in a commit does
        var transaction = new FileLockManager(_ -> {
        }).openTransaction();
        transaction.acquire(scope.lockKey());
        RecoveryStepFixtures.interruptedReplace(scope, WRITTEN);
        markers.println(inside(ROLLFORWARD));
        awaitKill();
    }

    private static void orderedWrite(Path base, PrintStream markers) {
        markers.println(before(ORDERED_WRITE));
        var lockManager = new FileLockManager(_ -> {
        });
        RecoveryStepFixtures.orderedWrite(JournalScope.plan(base, RecoveryStepFixtures.PROJECT_A, ROLLED_PLAN),
                JournalScope.plan(base, RecoveryStepFixtures.PROJECT_A, DERIVED_PLAN), WRITTEN, _ -> {
                }, () -> {
                    markers.println(inside(ORDERED_WRITE));
                    awaitKill();
                }).run(lockManager);
        throw new IllegalStateException("the ordered write ended although its derived step blocks");
    }

    /**
     * @param base the base directory of the runtime
     * @return the marker file to which the first recovery step appends one line per run
     */
    static Path recoveryRuns(Path base) {
        return base.resolve("recovery-runs");
    }

    /**
     * The first step of the mode {@value #RECOVERY}, also used by the test that recovers again: it appends the id of
     * the project as a line to the marker file.
     *
     * @param base the base directory of the runtime
     * @return the marking step
     */
    static RecoveryStep markingStep(Path base) {
        return new RecoveryStep() {

            @Override
            public Name name() {
                return Name.ENROLMENT_INTEGRITY;
            }

            @Override
            public Outcome run(String projectId) {
                try {
                    Files.writeString(recoveryRuns(base), projectId + System.lineSeparator(), StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                } catch (IOException e) {
                    return new Outcome.Failed("marker not written: " + e);
                }
                return new Outcome.Done("marked");
            }
        };
    }

    private static void recovery(MachinePaths paths, PrintStream markers) {
        var blocking = new RecoveryStep() {

            @Override
            public Name name() {
                return Name.JOBS_AND_WORKERS;
            }

            @Override
            public Outcome run(String projectId) {
                markers.println(inside(RECOVERY));
                return awaitKill();
            }
        };
        markers.println(before(RECOVERY));
        // The recovery runs on this thread, so the background pass blocks in the second step until the kill
        new ProjectRecovery(() -> List.of(RECOVERED_PROJECT),
                RecoveryStepFixtures.completeSteps(markingStep(paths.base()), blocking), Runnable::run)
                .startBackgroundPass();
        throw new IllegalStateException("the recovery ended although its step blocks");
    }

    private static void drain(MachinePaths paths, PrintStream markers) throws IOException {
        markers.println(before(DRAIN));
        var outcome = new StartupSequence(paths, System.getProperty("user.name"), AuditChainVerification.NONE, _ -> {
        }).run(new SecureRandom());
        if (!(outcome instanceof StartupSequence.Outcome.Ready(var lock, _))) {
            throw new IllegalStateException("startup did not reach ready: " + outcome);
        }
        // Steps 6 and 7 of a real runtime: the socket and the runtime record exist while it drains
        Files.createFile(paths.socket(), PosixModes.fileAttribute());
        Files.writeString(paths.runtimeRecord(), "{}");
        Files.setPosixFilePermissions(paths.runtimeRecord(), PosixModes.FILE);
        new RuntimeDrain(paths, lock, List.of(new NeverQuiet(markers)), Duration.ofSeconds(DrainTimeout.DEFAULT_SECONDS),
                Clock.systemUTC()).drain();
        throw new IllegalStateException("the drain ended although its participant never got quiet");
    }

    /** The participant of mode {@value #DRAIN}: it writes the marker inside the drain wait and blocks there. */
    private record NeverQuiet(PrintStream markers) implements DrainParticipant {

        @Override
        public String name() {
            return "never-quiet";
        }

        @Override
        public void stopAdmitting() {
            // admits nothing
        }

        @Override
        public void endIdle() {
            // nothing idle
        }

        @Override
        public boolean awaitQuiet(Instant deadline) {
            markers.println(inside(DRAIN));
            return awaitKill();
        }

        @Override
        public List<String> terminateRemaining() {
            return List.of();
        }

        @Override
        public void close() {
            // nothing to close
        }
    }

    private static void startup(MachinePaths paths, PrintStream markers, AuditChainVerification auditChain,
            String mode) throws IOException {
        markers.println(before(mode));
        var outcome = new StartupSequence(paths, System.getProperty("user.name"), auditChain, _ -> {
        }).run(new SecureRandom());
        if (outcome instanceof StartupSequence.Outcome.Ready) {
            markers.println(ready(mode));
            awaitKill();
        }
        throw new IllegalStateException("startup did not reach ready: " + outcome);
    }

    /** Blocks until the child is killed; halts at once when standard input ends first. */
    private static <T> T awaitKill() {
        try {
            System.in.transferTo(OutputStream.nullOutputStream());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Runtime.getRuntime().halt(EXIT_ORPHANED);
        throw new IllegalStateException("halt returned");
    }
}
