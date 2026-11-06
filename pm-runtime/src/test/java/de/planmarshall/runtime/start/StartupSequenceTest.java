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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.test.TestBases;

/**
 * Startup steps 1 to 5. The modes, file names, token length and log texts the tests read are the specified layout and
 * messages of the startup; the diagnostics, lines and names around them are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Startup steps 1 to 5")
class StartupSequenceTest {

    private static final String USER = System.getProperty("user.name");

    /** Contract literal: the length of a 256-bit token in unpadded base64url. */
    private static final int TOKEN_LENGTH = 43;

    /** Contract literal: the wire name of the reason of a broken link. */
    private static final String LINK_BROKEN_REASON = "link_broken";

    private final List<AuditEvent> records = new CopyOnWriteArrayList<>();

    private Path base;
    private MachinePaths paths;

    @BeforeEach
    void createBase() {
        // The short prefix keeps the socket path below the limit of the OS; a fixture argument, not test data
        base = TestBases.create("pmu");
        paths = new MachinePaths(base, MachinePaths.Os.current());
    }

    @AfterEach
    void deleteBase() {
        TestBases.delete(base);
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private StartupSequence.Outcome run() throws IOException {
        return run(AuditChainVerification.NONE);
    }

    private StartupSequence.Outcome run(AuditChainVerification auditChain) throws IOException {
        // The SecureRandom is the entropy source the startup needs to create its token, not test data
        return new StartupSequence(paths, USER, auditChain, records::add).run(new SecureRandom());
    }

    /** @return a base whose socket path is over the limit of the OS, by a generated name of exactly the limit */
    private Path overLongBase() {
        var limit = MachinePaths.Os.current().sunPathLimit();
        return base.resolve(Generators.letterStrings(limit, limit).next());
    }

    @Nested
    @DisplayName("step 4, bounded verification")
    class BoundedVerification {

        private final String diagnostic = Generators.letterStrings(10, 60).next();

        private final long firstBrokenLine = Generators.longs(1, 100_000).next();

        private final AuditChainVerification.Outcome.Broken linkBroken =
                AuditChainVerification.Outcome.Broken.linkBroken(firstBrokenLine, diagnostic);

        @Test
        @DisplayName("a broken link appends exactly one audit_chain_broken record with reason, line and principal and the start goes on")
        void shouldRecordBrokenChainAndGoOn() throws Exception {
            var outcome = run(() -> linkBroken);

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome, "the start must go on");
            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(linkBroken, ready.verification(), "the outcome must carry the break"),
                        () -> assertEquals(TOKEN_LENGTH, Files.readString(paths.runtimeToken()).length(),
                                "the token must be written"),
                        () -> assertEquals(List.of(new AuditEvent(StartupSequence.AUDIT_CHAIN_BROKEN,
                                        StartupSequence.CATEGORY_SECURITY, StartupSequence.OUTCOME_FAILED,
                                        Map.of(StartupSequence.DETAIL_REASON, LINK_BROKEN_REASON,
                                                StartupSequence.DETAIL_FIRST_BROKEN_LINE, firstBrokenLine,
                                                StartupSequence.DETAIL_OS_PRINCIPAL, USER))), records,
                                "exactly one audit_chain_broken record"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.AUDIT_CHAIN_BROKEN.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "Audit chain broken (reason 'link_broken', first broken line " + firstBrokenLine
                                        + "): " + diagnostic + "; the start goes on"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                INFO.BOUNDED_VERIFICATION_FINISHED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                "Bounded verification finished: audit chain broken (reason 'link_broken', "
                                        + "first broken line " + firstBrokenLine + "): " + diagnostic));
            }
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"logs/audit.jsonl", "logs/audit-index.jsonl",
                "logs/audit-0199e2a4-5b1c-7d3e-8f40-123456789abc.jsonl"})
        @DisplayName("a broken link logs the file its first broken line is counted in, and records no file "
                + "(the three file kinds of the audit layout)")
        void shouldLogFileOfFirstBrokenLine(String file) throws Exception {
            var line = Generators.longs(1, 100_000).next();
            var broken = AuditChainVerification.Outcome.Broken.linkBroken(line,
                    file + " line " + line + " does not parse");

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, run(() -> broken),
                    "the start must go on");

            try (var _ = ready.lock()) {
                assertAll(
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "first broken line " + line + "): " + file + " line " + line
                                        + " does not parse; the start goes on"),
                        () -> assertEquals(Map.of(StartupSequence.DETAIL_REASON, LINK_BROKEN_REASON,
                                        StartupSequence.DETAIL_FIRST_BROKEN_LINE, line,
                                        StartupSequence.DETAIL_OS_PRINCIPAL, USER), records.getFirst().details(),
                                "the record must carry no file"));
            }
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = AuditChainVerification.BreakReason.class, names = "LINK_BROKEN",
                mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("a chain broken for a reason without a line records the reason and no first broken line")
        void shouldRecordReasonWithoutLine(AuditChainVerification.BreakReason reason) throws Exception {
            var broken = AuditChainVerification.Outcome.Broken.withoutLine(reason, diagnostic);

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, run(() -> broken),
                    "the start must go on");

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(broken, ready.verification(), "the outcome must carry the break"),
                        () -> assertEquals(List.of(new AuditEvent(StartupSequence.AUDIT_CHAIN_BROKEN,
                                        StartupSequence.CATEGORY_SECURITY, StartupSequence.OUTCOME_FAILED,
                                        Map.of(StartupSequence.DETAIL_REASON, reason.wireName(),
                                                StartupSequence.DETAIL_OS_PRINCIPAL, USER))), records,
                                "the record must carry no first broken line"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.AUDIT_CHAIN_BROKEN.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "Audit chain broken (reason '" + reason.wireName()
                                        + "', first broken line none): " + diagnostic + "; the start goes on"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                INFO.BOUNDED_VERIFICATION_FINISHED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                "Bounded verification finished: audit chain broken (reason '" + reason.wireName()
                                        + "', first broken line none): " + diagnostic));
            }
        }

        @Test
        @DisplayName("an intact chain appends nothing")
        void shouldAppendNothingForIntactChain() throws Exception {
            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class,
                    run(AuditChainVerification.Outcome.Intact::new), "the start must be ready");

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(new AuditChainVerification.Outcome.Intact(), ready.verification(),
                                "the outcome must report the chain intact"),
                        () -> assertTrue(records.isEmpty(), "an intact chain appends no record"),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, StartupSequence.class),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                INFO.BOUNDED_VERIFICATION_FINISHED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                "Bounded verification finished: audit chain intact"));
            }
        }

        @Test
        @DisplayName("a start without an audit chain appends nothing and reports the chain as not verified")
        void shouldAppendNothingWithoutChain() throws Exception {
            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, run(AuditChainVerification.NONE),
                    "the start must be ready");

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertEquals(new AuditChainVerification.Outcome.NotVerified("no audit chain"),
                                ready.verification(), "the chain must be reported as not verified"),
                        () -> assertTrue(records.isEmpty(), "a start without a chain appends no record"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                                INFO.BOUNDED_VERIFICATION_FINISHED.resolveIdentifierString()),
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
                assertTrue(Files.exists(paths.runtimeToken()), "the token must be written after step 4");
            }
        }

        @Test
        @DisplayName("a refusing audit sink does not stop the start")
        void shouldGoOnWhenSinkRefuses() throws Exception {
            AuditSink refusing = _ -> {
                // Contract literal: the error text of the simulated refusal
                throw new UncheckedIOException(new IOException("audit log not writable"));
            };
            var sequence = new StartupSequence(paths, USER, () -> linkBroken, refusing);

            // The SecureRandom is the entropy source the startup needs to create its token, not test data
            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, sequence.run(new SecureRandom()),
                    "a refusing sink must not stop the start");

            try (var _ = ready.lock()) {
                assertAll(
                        () -> assertTrue(Files.exists(paths.runtimeToken()), "the token must be written"),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                WARN.AUDIT_CHAIN_BROKEN_NOT_RECORDED.resolveIdentifierString()),
                        () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                                "Record of the broken audit chain not handed over to the audit sink"));
            }
        }

        @Test
        @DisplayName("a start refused in step 3 never runs step 4")
        void shouldNotVerifyRefusedStart() throws Exception {
            paths = new MachinePaths(overLongBase(), MachinePaths.Os.current());
            var calls = new AtomicInteger();

            var outcome = run(() -> {
                calls.incrementAndGet();
                return linkBroken;
            });

            assertAll(
                    () -> assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome,
                            "the start must be refused in step 3"),
                    () -> assertEquals(0, calls.get(), "calls of the audit chain verification"),
                    () -> assertTrue(records.isEmpty(), "a refused start appends no record"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, StartupSequence.class));
        }

        @Test
        @DisplayName("reads nothing below enrolments/, projects/ or a plan directory")
        void shouldReadNoStoreOrUnit() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var project = "project-" + name();
            // Contract literals: the directory layout of the base and the minimal JSON of a store
            var enrolment = Files.writeString(base.resolve("enrolments").resolve(project + ".json"), "{}");
            var plan = Files.createDirectories(base.resolve("projects").resolve(project).resolve("plans")
                    .resolve("plan-" + name()));
            Files.writeString(plan.resolve("status.json"), "{}");
            var projects = base.resolve("projects");
            Files.setPosixFilePermissions(enrolment, Set.of());
            Files.setPosixFilePermissions(projects, Set.of());
            try {
                var outcome = run(AuditChainVerification.Outcome.Intact::new);

                // A read of the unreadable enrolment file or a walk of the closed projects directory would throw
                try (var _ = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome,
                             "the start must not read the stores").lock()) {
                    assertAll(
                            () -> assertEquals(Set.of(), Files.getPosixFilePermissions(enrolment),
                                    "the enrolment file must stay closed"),
                            () -> assertEquals(Set.of(), Files.getPosixFilePermissions(projects),
                                    "the projects directory must stay closed"));
                }
            } finally {
                Files.setPosixFilePermissions(projects, PosixModes.DIRECTORY);
                Files.setPosixFilePermissions(enrolment, PosixModes.FILE);
            }
        }
    }

    @Nested
    @DisplayName("on a clean base (the modes and names are the specified layout)")
    class Clean {

        @Test
        @DisplayName("creates the private directories and a fresh 256-bit token")
        void shouldPrepareBase() throws Exception {
            var outcome = run();

            var ready = assertInstanceOf(StartupSequence.Outcome.Ready.class, outcome, "the start must be ready");
            try (var lock = ready.lock()) {
                assertTrue(lock.isHeld(), "the start must hold the lock");
                for (var dir : new Path[]{base, paths.runDir(), paths.stateDir(), base.resolve("enrolments"),
                        paths.locksDir()}) {
                    assertEquals("rwx------", PosixFilePermissions.toString(Files.getPosixFilePermissions(dir)),
                            "every directory must be private to its owner");
                }
                assertEquals("rw-------",
                        PosixFilePermissions.toString(Files.getPosixFilePermissions(paths.runtimeToken())),
                        "the token must be private to its owner");
                assertEquals(TOKEN_LENGTH, Files.readString(paths.runtimeToken()).length(),
                        "the token must carry 256 bits");
                assertFalse(Files.exists(paths.runtimeToken().resolveSibling("runtime.token.tmp")),
                        "no staged token must be left behind");
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
                assertNotEquals(first, Files.readString(paths.runtimeToken()), "every start must write a new token");
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

            assertInstanceOf(StartupSequence.Outcome.LockHeld.class, second, "a second start must find the lock");
            assertEquals(token, Files.readString(paths.runtimeToken()), "the token must stay unchanged");
            assertEquals(modified, Files.getLastModifiedTime(paths.runtimeToken()),
                    "the token file must not be touched");
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
            assertFalse(Files.exists(paths.socket(), LinkOption.NOFOLLOW_LINKS), "the stale socket must be removed");
            assertFalse(Files.exists(paths.runtimeRecord()), "the stale runtime record must be removed");
        }
    }

    @Test
    @DisplayName("refuses an over-long socket path before writing a token")
    void shouldRefuseLongSocketPath() throws Exception {
        paths = new MachinePaths(overLongBase(), MachinePaths.Os.current());

        var outcome = run();

        var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome,
                "an over-long socket path must be refused");
        assertTrue(refused.diagnostic().contains(paths.socket().toString()), "the diagnostic must name the socket");
        // Contract literal: the environment variable the diagnostic points the operator to
        assertTrue(refused.diagnostic().contains("PM_MCP_BASE"), "the diagnostic must name PM_MCP_BASE");
        assertFalse(Files.exists(paths.runtimeToken()), "no token must be written");
        assertFalse(Files.exists(paths.runDir()), "no run directory must be created");
    }

    @Nested
    @DisplayName("refuses a base with broken permissions (the modes and names are the specified layout)")
    class Permissions {

        @ParameterizedTest(name = "{0} with mode 0755")
        @ValueSource(strings = {"", "run", "enrolments"})
        @DisplayName("a directory with a broader mode")
        void shouldRefuseBroadDirectory(String dir) throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var target = dir.isEmpty() ? base : base.resolve(dir);
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rwxr-xr-x"));

            var outcome = run();

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome,
                    "a broader directory must be refused");
            assertTrue(refused.diagnostic().contains("0755"), "the diagnostic must name the mode");
        }

        @Test
        @DisplayName("a state file with a broader mode")
        void shouldRefuseBroadStateFile() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var file = Files.createFile(paths.stateDir().resolve("machine-config.json"));
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, run(),
                    "a broader state file must be refused");
            assertTrue(refused.diagnostic().contains("machine-config.json"), "the diagnostic must name the file");
        }

        @Test
        @DisplayName("a symlinked run directory")
        void shouldRefuseSymlink() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var elsewhere = Files.createDirectory(base.resolve(name()), PosixModes.directoryAttribute());
            TestBases.delete(paths.runDir());
            Files.createSymbolicLink(paths.runDir(), elsewhere);

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, run(),
                    "a symlinked run directory must be refused");
            assertTrue(refused.diagnostic().contains("symbolic link"), "the diagnostic must name the link");
        }

        @Test
        @DisplayName("a directory owned by another user")
        void shouldRefuseForeignOwner() throws Exception {
            ((StartupSequence.Outcome.Ready) run()).lock().close();
            var somebodyElse = "other-" + name();

            // The SecureRandom is the entropy source the startup needs to create its token, not test data
            var outcome = new StartupSequence(paths, somebodyElse, AuditChainVerification.NONE, records::add)
                    .run(new SecureRandom());

            var refused = assertInstanceOf(StartupSequence.Outcome.Refused.class, outcome,
                    "a foreign owner must be refused");
            assertTrue(refused.diagnostic().contains("owned by"), "the diagnostic must name the owner");
        }
    }

    @Test
    @DisplayName("formats permission sets in octal (the modes are the specified layout)")
    void shouldFormatOctal() {
        assertAll(
                () -> assertEquals("0700", PosixModes.octal(PosixModes.DIRECTORY),
                        "the directory mode must be 0700"),
                () -> assertEquals("0600", PosixModes.octal(PosixModes.FILE), "the file mode must be 0600"),
                () -> assertEquals("0755", PosixModes.octal(PosixFilePermissions.fromString("rwxr-xr-x")),
                        "a broader mode must be shown as 0755"));
    }
}
