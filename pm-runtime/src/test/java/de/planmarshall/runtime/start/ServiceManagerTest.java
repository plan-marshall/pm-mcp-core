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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.runtime.start.ServiceUnitFile.Platform;

/**
 * The service managers of the service-managed start mode (PM-ARCH-7): the exact {@code launchctl} and
 * {@code systemctl --user} commands of load, disable and unload, the exit codes that mean "not loaded", and the
 * failure that names a failing command. Every command goes to a recording fake; no test starts a process. The
 * command words, the job label, the unit name and the "not loaded" exit codes are the contract of the service
 * managers; the user id, the home directory, the other exit codes and the command output are generated.
 */
@EnableGeneratorController
@DisplayName("Service manager")
class ServiceManagerTest {

    /** Contract literals: the launchd job label and the systemd unit name of the daemon. */
    private static final String LABEL = "de.planmarshall.mcpd";
    private static final String UNIT = "pm-mcpd.service";

    private final long uid = Generators.integers(500, 60_000).next();
    private final String user = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    // Fixture paths: where launchd and systemd keep the per-user unit
    private final Path launchAgent = Path.of("/Users/" + user + "/Library/LaunchAgents/" + LABEL + ".plist");
    private final Path systemdUnit = Path.of("/home/" + user + "/.config/systemd/user/" + UNIT);
    private final String output = Generators.letterStrings(3, 40).next();

    private final List<String> bootout = List.of("launchctl", "bootout", "gui/" + uid + "/" + LABEL);
    private final List<String> bootstrap = List.of("launchctl", "bootstrap", "gui/" + uid, launchAgent.toString());
    private static final List<String> DAEMON_RELOAD = List.of("systemctl", "--user", "daemon-reload");
    private static final List<String> ENABLE = List.of("systemctl", "--user", "enable", "--now", UNIT);
    private static final List<String> DISABLE = List.of("systemctl", "--user", "disable", UNIT);
    private static final List<String> STOP = List.of("systemctl", "--user", "stop", UNIT);

    /** @return a generated failing exit code that is none of the given ones */
    private static int failingExitCode(Set<Integer> excluded) {
        int exitCode;
        do {
            exitCode = Generators.integers(1, 255).next();
        } while (excluded.contains(exitCode));
        return exitCode;
    }

    @Nested
    @DisplayName("launchd")
    class Launchd {

        /** Contract literals: the exit codes of bootout that mean the job is not loaded. */
        private static final Set<Integer> NOT_LOADED = Set.of(3, 113);

        private final List<ProcessCallDoubles.Call> calls = new ArrayList<>();

        @Test
        @DisplayName("boots an existing job out and bootstraps the LaunchAgent into the GUI domain on load")
        void shouldLoad() {
            var manager = new LaunchdServiceManager(ProcessCallDoubles.create(null, calls), uid);

            manager.load(launchAgent);

            assertEquals(List.of(bootout, bootstrap), commands(), "the load must boot out, then bootstrap");
        }

        @ParameterizedTest(name = "bootout exit code {0}")
        @ValueSource(ints = {3, 113})
        @DisplayName("treats a job that is not loaded as success on a reinstall (the specified exit codes)")
        void shouldLoadWhenNotLoaded(int exitCode) {
            var manager = new LaunchdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(exitCode, output)), uid);

            manager.load(launchAgent);

            assertEquals(List.of(bootout, bootstrap), commands(), "a job not loaded must still be bootstrapped");
        }

        @Test
        @DisplayName("fails the load when bootstrap exits non-zero, naming the command and its output")
        void shouldFailOnBootstrap() {
            var exitCode = failingExitCode(Set.of());
            var manager = new LaunchdServiceManager(ProcessCallDoubles.create(null, calls,
                    ProcessCallDoubles.answer(0, ""), ProcessCallDoubles.answer(exitCode, output + "\n")), uid);

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.load(launchAgent),
                    "a failing bootstrap must fail the load");

            assertEquals("Command 'launchctl bootstrap gui/" + uid + " " + launchAgent + "' exited with " + exitCode
                    + ": " + output, thrown.getMessage(), "the failure must name the command and output");
        }

        @Test
        @DisplayName("fails the load without bootstrapping when bootout exits with another code")
        void shouldFailOnBootout() {
            var exitCode = failingExitCode(NOT_LOADED);
            var manager = new LaunchdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(exitCode, output)), uid);

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.load(launchAgent),
                    "a failing bootout must fail the load");

            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith(
                                    "Command 'launchctl bootout gui/" + uid + "/" + LABEL + "' exited with " + exitCode),
                            "the failure must name the bootout command"),
                    () -> assertEquals(List.of(bootout), commands(), "no bootstrap after a failing bootout"));
        }

        @Test
        @DisplayName("issues no command on disable, since removing the LaunchAgent disables the job")
        void shouldDisableWithoutCommand() {
            var manager = new LaunchdServiceManager(ProcessCallDoubles.create(null, calls), uid);

            manager.disable(launchAgent);

            assertEquals(List.of(), commands(), "the disable must issue no launchd command");
        }

        @Test
        @DisplayName("boots the job out on unload")
        void shouldUnload() {
            var manager = new LaunchdServiceManager(ProcessCallDoubles.create(null, calls), uid);

            manager.unload(launchAgent);

            assertEquals(List.of(bootout), commands(), "the unload must boot the job out");
        }

        @ParameterizedTest(name = "bootout exit code {0}")
        @ValueSource(ints = {3, 113})
        @DisplayName("treats a job that is not loaded as success on unload (the specified exit codes)")
        void shouldUnloadWhenNotLoaded(int exitCode) {
            var manager = new LaunchdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(exitCode, output)), uid);

            assertDoesNotThrow(() -> manager.unload(launchAgent), "a job not loaded must unload without failure");
        }

        @Test
        @DisplayName("passes on a command that cannot be started")
        void shouldPassOnStartFailure() {
            var failure = new ServiceUnitException(Generators.letterStrings(3, 40).next());
            var manager = new LaunchdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.failure(failure)), uid);

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.unload(launchAgent),
                    "a command that cannot start must fail the unload");

            assertSame(failure, thrown, "the start failure must be passed on unchanged");
        }

        private List<List<String>> commands() {
            return calls.stream().map(ProcessCallDoubles.Call::command).toList();
        }
    }

    @Nested
    @DisplayName("systemd")
    class Systemd {

        /** Contract literal: the exit code of stop that means the unit is not loaded. */
        private static final int NOT_LOADED = 5;

        private final List<ProcessCallDoubles.Call> calls = new ArrayList<>();

        @Test
        @DisplayName("reloads the user manager and enables and starts the unit on load")
        void shouldLoad() {
            var manager = new SystemdServiceManager(ProcessCallDoubles.create(null, calls));

            manager.load(systemdUnit);

            assertEquals(List.of(DAEMON_RELOAD, ENABLE), commands(), "the load must reload, then enable");
        }

        @Test
        @DisplayName("fails the load when enable exits non-zero, naming the command and its output")
        void shouldFailOnEnable() {
            var exitCode = failingExitCode(Set.of());
            var manager = new SystemdServiceManager(ProcessCallDoubles.create(null, calls,
                    ProcessCallDoubles.answer(0, ""), ProcessCallDoubles.answer(exitCode, output)));

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.load(systemdUnit),
                    "a failing enable must fail the load");

            assertEquals("Command 'systemctl --user enable --now " + UNIT + "' exited with " + exitCode + ": "
                    + output, thrown.getMessage(), "the failure must name the command and output");
        }

        @Test
        @DisplayName("fails the load without enabling when daemon-reload exits non-zero")
        void shouldFailOnDaemonReload() {
            var manager = new SystemdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(failingExitCode(Set.of()), output)));

            assertThrows(ServiceUnitException.class, () -> manager.load(systemdUnit),
                    "a failing daemon-reload must fail the load");

            assertEquals(List.of(DAEMON_RELOAD), commands(), "no enable after a failing daemon-reload");
        }

        @Test
        @DisplayName("disables the unit without stopping it")
        void shouldDisable() {
            var manager = new SystemdServiceManager(ProcessCallDoubles.create(null, calls));

            manager.disable(systemdUnit);

            assertEquals(List.of(DISABLE), commands(), "the disable must not stop the unit");
        }

        @Test
        @DisplayName("fails the disable when it exits non-zero")
        void shouldFailOnDisable() {
            var manager = new SystemdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(failingExitCode(Set.of()), output)));

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.disable(systemdUnit),
                    "a failing disable must fail");

            assertTrue(thrown.getMessage().startsWith("Command 'systemctl --user disable " + UNIT + "'"),
                    "the failure must name the disable command");
        }

        @Test
        @DisplayName("stops the unit and reloads the user manager on unload")
        void shouldUnload() {
            var manager = new SystemdServiceManager(ProcessCallDoubles.create(null, calls));

            manager.unload(systemdUnit);

            assertEquals(List.of(STOP, DAEMON_RELOAD), commands(), "the unload must stop, then reload");
        }

        @Test
        @DisplayName("treats a unit that is not loaded as success on unload (the specified exit code 5)")
        void shouldUnloadWhenNotLoaded() {
            var manager = new SystemdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(NOT_LOADED, output)));

            manager.unload(systemdUnit);

            assertEquals(List.of(STOP, DAEMON_RELOAD), commands(), "a unit not loaded must still be reloaded");
        }

        @Test
        @DisplayName("fails the unload when stop exits with another code")
        void shouldFailOnStop() {
            var exitCode = failingExitCode(Set.of(NOT_LOADED));
            var manager = new SystemdServiceManager(
                    ProcessCallDoubles.create(null, calls, ProcessCallDoubles.answer(exitCode, output)));

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.unload(systemdUnit),
                    "a failing stop must fail the unload");

            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith(
                                    "Command 'systemctl --user stop " + UNIT + "' exited with " + exitCode),
                            "the failure must name the stop command"),
                    () -> assertEquals(List.of(STOP), commands(), "no reload after a failing stop"));
        }

        private List<List<String>> commands() {
            return calls.stream().map(ProcessCallDoubles.Call::command).toList();
        }
    }

    @Nested
    @DisplayName("for platform")
    class ForPlatform {

        private final List<ProcessCallDoubles.Call> calls = new ArrayList<>();

        @Test
        @DisplayName("is launchd for the launchd platform")
        void shouldBeLaunchd() {
            var manager = ServiceManager.forPlatform(Platform.LAUNCHD, ProcessCallDoubles.create(null, calls), uid);

            assertInstanceOf(LaunchdServiceManager.class, manager, "launchd must get the launchd manager");
        }

        @Test
        @DisplayName("is systemd for the systemd platform")
        void shouldBeSystemd() {
            var manager = ServiceManager.forPlatform(Platform.SYSTEMD, ProcessCallDoubles.create(null, calls), uid);

            assertInstanceOf(SystemdServiceManager.class, manager, "systemd must get the systemd manager");
        }

        @Test
        @DisplayName("names the user id in the launchd GUI domain")
        void shouldUseUid() {
            var call = ProcessCallDoubles.create(null, calls);
            var otherUid = uid + Generators.integers(1, 1000).next();

            ServiceManager.forPlatform(Platform.LAUNCHD, call, otherUid).unload(launchAgent);

            assertEquals(List.of(List.of("launchctl", "bootout", "gui/" + otherUid + "/" + LABEL)),
                    calls.stream().map(ProcessCallDoubles.Call::command).toList(),
                    "the GUI domain must name the given user id");
        }

        @Test
        @DisplayName("refuses a missing process call")
        void shouldRefuseNullCall() {
            assertThrows(NullPointerException.class, () -> ServiceManager.forPlatform(Platform.SYSTEMD, null, uid),
                    "a missing process call must be refused");
        }
    }
}
