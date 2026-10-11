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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.test.TestBases;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@EnableTestLogger
@DisplayName("Startup steps 1 to 5")
class StartupSequenceTest {

    private static final String USER = System.getProperty("user.name");

    private final List<AuditEvent> records = new CopyOnWriteArrayList<>();

    private Path base;
    private MachinePaths paths;

    @BeforeEach
    void createBase() {
        base = TestBases.create("pmu");
        paths = new MachinePaths(base, MachinePaths.Os.current());
    }

    @AfterEach
    void deleteBase() {
        TestBases.delete(base);
    }

    private StartupSequence.Outcome run() throws IOException {
        return run(AuditChainVerification.NONE);
    }

    private StartupSequence.Outcome run(AuditChainVerification auditChain) throws IOException {
        return new StartupSequence(paths, USER, auditChain, records::add).run(new SecureRandom());
    }

    @Nested
    @DisplayName("step 4, bounded verification")
    class BoundedVerification {

        private static final String DIAGNOSTIC = "logs/audit.jsonl line 7: prev_hash does not match line 6";

        @Test
        @DisplayName("a broken chain appends exactly one audit_chain_broken record and the start goes on")
        void shouldRecordBrokenChainAndGoOn() throws Exception {
            var outcome = run(() -> new AuditChainVerification.Outcome.Broken(DIAGNOSTIC));

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome);
            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(new AuditChainVerification.Outcome.Broken(DIAGNOSTIC),
                                ready.verification()),
                        () -> assertEquals(43, Files.readString(paths.runtimeToken()).length()),
                        () -> assertEquals(List.of(new AuditEvent(StartupSequence.AUDIT_CHAIN_BROKEN,
                                StartupSequence.CATEGORY_SECURITY, StartupSequence.OUTCOME_FAILED,
                                Map.of(StartupSequence.DETAIL_DIAGNOSTIC, DIAGNOSTIC))), records),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "Audit chain broken, the start goes on: " + DIAGNOSTIC),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                "Bounded verification finished: audit chain broken (" + DIAGNOSTIC + ")"));
            }
        }

        @Test
        @DisplayName("an intact chain appends nothing")
        void shouldAppendNothingForIntactChain() throws Exception {
            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class,
                    run(AuditChainVerification.Outcome.Intact::new));

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(new AuditChainVerification.Outcome.Intact(), ready.verification()),
                        () -> assertTrue(records.isEmpty(), records::toString),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, StartupSequence.class),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                "Bounded verification finished: audit chain intact"));
            }
        }

        @Test
        @DisplayName("a start without an audit chain appends nothing and reports the chain as not verified")
        void shouldAppendNothingWithoutChain() throws Exception {
            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, run(AuditChainVerification.NONE));

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(new AuditChainVerification.Outcome.NotVerified("no audit chain"),
                                ready.verification()),
                        () -> assertTrue(records.isEmpty(), records::toString),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                "Bounded verification finished: audit chain not verified (no audit chain)"));
            }
        }

        @Test
        @DisplayName("runs after the directory check and before the token is written")
        void shouldRunBetweenDirectoryCheckAndToken() throws Exception {
            var seen = new ArrayList<Boolean>();
            AuditChainVerification probe = () -> {
                seen.add(Files.isDirectory(paths.runDir()));
                seen.add(Files.exists(paths.runtimeToken()));
                return new AuditChainVerification.Outcome.Intact();
            };

            try (var _ = ((StartupSequence.Outcome.Ready) run(probe)).lock()) {
                assertEquals(List.of(true, false), seen, "[run/ created, token present] when step 4 ran");
                assertTrue(Files.exists(paths.runtimeToken()));
            }
        }

        @Test
        @DisplayName("a refusing audit sink does not stop the start")
        void shouldGoOnWhenSinkRefuses() throws Exception {
            AuditSink refusing = _ -> {
                throw new UncheckedIOException(new IOException("audit log not writable"));
            };
            var sequence = new StartupSequence(paths, USER,
                    () -> new AuditChainVerification.Outcome.Broken(DIAGNOSTIC), refusing);

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, sequence.run(new SecureRandom()));

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertTrue(Files.exists(paths.runtimeToken())),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "Record of the broken audit chain not handed over to the audit sink"));
            }
        }

        @Test
        @DisplayName("a start refused in step 3 never runs step 4")
        void shouldNotVerifyRefusedStart() throws Exception {
            paths = new MachinePaths(base.resolve("x".repeat(MachinePaths.Os.current().sunPathLimit())),
                    MachinePaths.Os.current());
            var calls = new AtomicInteger();

            var outcome = run(() -> {
                calls.incrementAndGet();
                return new AuditChainVerification.Outcome.Broken(DIAGNOSTIC);
            });

            assertAll(
                    () -> assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome),
                    () -> assertEquals(0, calls.get(), "calls of the audit chain verification"),
                    () -> assertTrue(records.isEmpty(), records::toString),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, StartupSequence.class));
        }

        @Test
        @DisplayName("reads nothing below enrolments/, projects/ or a plan directory")
        void shouldReadNoStoreOrUnit() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var enrolment = Files.writeString(base.resolve("enrolments").resolve("a-project.json"), "{}");
            var plan = Files.createDirectories(base.resolve("projects/a-project/plans/a-plan"));
            Files.writeString(plan.resolve("status.json"), "{}");
            var projects = base.resolve("projects");
            Files.setPosixFilePermissions(enrolment, Set.of());
            Files.setPosixFilePermissions(projects, Set.of());
            try {
                var outcome = run(AuditChainVerification.Outcome.Intact::new);

                // A read of the unreadable enrolment file or a walk of the closed projects directory would throw
                try (var _ = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome).lock()) {
                    assertAll(
                            () -> assertEquals(Set.of(), Files.getPosixFilePermissions(enrolment)),
                            () -> assertEquals(Set.of(), Files.getPosixFilePermissions(projects)));
                }
            } finally {
                Files.setPosixFilePermissions(projects, PosixModes.DIRECTORY);
                Files.setPosixFilePermissions(enrolment, PosixModes.FILE);
            }
        }
    }

    @Nested
    @DisplayName("on a clean base")
    class Clean {

        @Test
        @DisplayName("creates the private directories and a fresh 256-bit token")
        void shouldPrepareBase() throws Exception {
            var outcome = run();

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome);
            try (var lock = ready.lock()) {
                assertTrue(lock.isHeld());
                for (var dir : new Path[]{base, paths.runDir(), paths.stateDir(), base.resolve("enrolments"),
                        paths.locksDir()}) {
                    assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)), dir
                            .toString());
                }
                assertEquals("rw-------",
                        PosixFilePermissions.toString(Files.getPosixFilePermissions(paths.runtimeToken())));
                assertEquals(43, Files.readString(paths.runtimeToken()).length());
                assertFalse(Files.exists(paths.runtimeToken().resolveSibling("runtime.token.tmp")));
            }
        }

        @Test
        @DisplayName("replaces the token at every start")
        void shouldReplaceToken() throws Exception {
            String first;
            try (var _ = ((StartupSequence.Outcome.Ready) run()).lock()) {
                first = Files.readString(paths.runtimeToken());
            }
            try (var _ = ((StartupSequence.Outcome.Ready) run()).lock()) {
                assertNotEquals(first, Files.readString(paths.runtimeToken()));
            }
        }
    }

    @Test
    @DisplayName("a runtime that loses the lock writes nothing")
    void shouldLoseLockWithoutWriting() throws Exception {
        try (var _ = ((StartupSequence.Outcome.Ready) run()).lock()) {
            var token = Files.readString(paths.runtimeToken());
            var modified = Files.getLastModifiedTime(paths.runtimeToken());

            var second = run();

            assertInstanceOf(StartupSequence.Outcome.LockHeld.class, second);
            assertEquals(token, Files.readString(paths.runtimeToken()));
            assertEquals(modified, Files.getLastModifiedTime(paths.runtimeToken()));
        }
    }

    @Test
    @DisplayName("removes a stale socket and runtime record while holding the lock")
    void shouldRemoveStaleFiles() throws Exception {
        Files.createDirectories(paths.runDir(), PosixModes.directoryAttribute());
        Files.createDirectories(paths.stateDir(), PosixModes.directoryAttribute());
        Files.createFile(paths.socket(), PosixModes.fileAttribute());
        Files.createFile(paths.runtimeRecord(), PosixModes.fileAttribute());
        Files.setPosixFilePermissions(base, PosixModes.DIRECTORY);

        try (var _ = ((StartupSequence.Outcome.Ready) run()).lock()) {
            assertFalse(Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS));
            assertFalse(Files.exists(paths.runtimeRecord()));
        }
    }

    @Test
    @DisplayName("refuses an over-long socket path before writing a token")
    void shouldRefuseLongSocketPath() throws Exception {
        var longBase = base.resolve("x".repeat(MachinePaths.Os.current().sunPathLimit()));
        paths = new MachinePaths(longBase, MachinePaths.Os.current());

        var outcome = run();

        var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome);
        assertTrue(refused.diagnostic().contains(paths.socket().toString()), refused.diagnostic());
        assertTrue(refused.diagnostic().contains("PM_MCP_BASE"));
        assertFalse(Files.exists(paths.runtimeToken()));
        assertFalse(Files.exists(paths.runDir()));
    }

    @Nested
    @DisplayName("refuses a base with broken permissions")
    class Permissions {

        @ParameterizedTest(name = "{0} with mode 0755")
        @ValueSource(strings = {"", "run", "enrolments"})
        @DisplayName("a directory with a broader mode")
        void shouldRefuseBroadDirectory(String dir) throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var target = dir.isEmpty() ? base : base.resolve(dir);
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));

            var outcome = run();

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome);
            assertTrue(refused.diagnostic().contains("0755"), refused.diagnostic());
        }

        @Test
        @DisplayName("a state file with a broader mode")
        void shouldRefuseBroadStateFile() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var file = Files.createFile(paths.stateDir().resolve("machine-config.json"));
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, run());
            assertTrue(refused.diagnostic().contains("machine-config.json"), refused.diagnostic());
        }

        @Test
        @DisplayName("a symlinked run directory")
        void shouldRefuseSymlink() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var elsewhere = Files.createDirectory(base.resolve("elsewhere"), PosixModes.directoryAttribute());
            TestBases.delete(paths.runDir());
            Files.createSymbolicLink(paths.runDir(), elsewhere);

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, run());
            assertTrue(refused.diagnostic().contains("symbolic link"), refused.diagnostic());
        }

        @Test
        @DisplayName("a directory owned by another user")
        void shouldRefuseForeignOwner() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();

            var outcome = new StartupSequence(paths, "somebody-else", AuditChainVerification.NONE, records::add)
                    .run(new SecureRandom());

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome);
            assertTrue(refused.diagnostic().contains("owned by"), refused.diagnostic());
        }
    }

    @Test
    @DisplayName("formats permission sets in octal")
    void shouldFormatOctal() {
        assertEquals("0700", PosixModes.octal(PosixModes.DIRECTORY));
        assertEquals("0600", PosixModes.octal(PosixModes.FILE));
        assertEquals("0755", PosixModes.octal(PosixFilePermissions.fromString("rwxr-xr-x")));
    }
}
