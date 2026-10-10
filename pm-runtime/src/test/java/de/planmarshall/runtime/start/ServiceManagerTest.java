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

import java.nio.file.Path;
import java.util.List;


import de.planmarshall.runtime.start.ServiceUnitFile.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The service managers of the service-managed start mode (PM-ARCH-7): the exact {@code launchctl} and
 * {@code systemctl --user} commands of load, disable and unload, the exit codes that mean "not loaded", and the
 * failure that names a failing command. Every command goes to a recording fake; no test starts a process.
 */
@DisplayName("Service manager")
class ServiceManagerTest {

    private static final long UID = 501;
    private static final Path LAUNCH_AGENT = Path.of("/Users/someone/Library/LaunchAgents/de.planmarshall.mcpd.plist");
    private static final Path SYSTEMD_UNIT = Path.of("/home/someone/.config/systemd/user/pm-mcpd.service");

    private static final List<String> BOOTOUT = List.of("launchctl", "bootout", "gui/501/de.planmarshall.mcpd");
    private static final List<String> BOOTSTRAP = List.of("launchctl", "bootstrap", "gui/501", LAUNCH_AGENT.toString());
    private static final List<String> DAEMON_RELOAD = List.of("systemctl", "--user", "daemon-reload");
    private static final List<String> ENABLE = List.of("systemctl", "--user", "enable", "--now", "pm-mcpd.service");
    private static final List<String> DISABLE = List.of("systemctl", "--user", "disable", "pm-mcpd.service");
    private static final List<String> STOP = List.of("systemctl", "--user", "stop", "pm-mcpd.service");

    @Nested
    @DisplayName("launchd")
    class Launchd {

        private final RecordingProcessCall call = new RecordingProcessCall();
        private final ServiceManager manager = new LaunchdServiceManager(call, UID);

        @Test
        @DisplayName("boots an existing job out and bootstraps the LaunchAgent into the GUI domain on load")
        void shouldLoad() {
            manager.load(LAUNCH_AGENT);

            assertEquals(List.of(BOOTOUT, BOOTSTRAP), call.commands());
        }

        @ParameterizedTest(name = "bootout exit code {0}")
        @ValueSource(ints = {3, 113})
        @DisplayName("treats a job that is not loaded as success on a reinstall")
        void shouldLoadWhenNotLoaded(int exitCode) {
            call.thenAnswer(exitCode, "Boot-out failed: No such process");

            manager.load(LAUNCH_AGENT);

            assertEquals(List.of(BOOTOUT, BOOTSTRAP), call.commands());
        }

        @Test
        @DisplayName("fails the load when bootstrap exits non-zero, naming the command and its output")
        void shouldFailOnBootstrap() {
            call.thenAnswer(0, "").thenAnswer(5, "Bootstrap failed: 5: Input/output error\n");

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.load(LAUNCH_AGENT));

            assertEquals("Command 'launchctl bootstrap gui/501 " + LAUNCH_AGENT
                    + "' exited with 5: Bootstrap failed: 5: Input/output error", thrown.getMessage());
        }

        @Test
        @DisplayName("fails the load without bootstrapping when bootout exits with another code")
        void shouldFailOnBootout() {
            call.thenAnswer(1, "Boot-out failed: 1: Operation not permitted");

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.load(LAUNCH_AGENT));

            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith(
                                    "Command 'launchctl bootout gui/501/de.planmarshall.mcpd' exited with 1"),
                            thrown.getMessage()),
                    () -> assertEquals(List.of(BOOTOUT), call.commands()));
        }

        @Test
        @DisplayName("issues no command on disable, since removing the LaunchAgent disables the job")
        void shouldDisableWithoutCommand() {
            manager.disable(LAUNCH_AGENT);

            assertEquals(List.of(), call.commands());
        }

        @Test
        @DisplayName("boots the job out on unload")
        void shouldUnload() {
            manager.unload(LAUNCH_AGENT);

            assertEquals(List.of(BOOTOUT), call.commands());
        }

        @ParameterizedTest(name = "bootout exit code {0}")
        @ValueSource(ints = {3, 113})
        @DisplayName("treats a job that is not loaded as success on unload")
        void shouldUnloadWhenNotLoaded(int exitCode) {
            call.thenAnswer(exitCode, "Boot-out failed: No such process");

            assertDoesNotThrow(() -> manager.unload(LAUNCH_AGENT));
        }

        @Test
        @DisplayName("passes on a command that cannot be started")
        void shouldPassOnStartFailure() {
            var failure = new ServiceUnitException("Command 'launchctl bootout' could not run");
            call.thenFail(failure);

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.unload(LAUNCH_AGENT));

            assertSame(failure, thrown);
        }
    }

    @Nested
    @DisplayName("systemd")
    class Systemd {

        private final RecordingProcessCall call = new RecordingProcessCall();
        private final ServiceManager manager = new SystemdServiceManager(call);

        @Test
        @DisplayName("reloads the user manager and enables and starts the unit on load")
        void shouldLoad() {
            manager.load(SYSTEMD_UNIT);

            assertEquals(List.of(DAEMON_RELOAD, ENABLE), call.commands());
        }

        @Test
        @DisplayName("fails the load when enable exits non-zero, naming the command and its output")
        void shouldFailOnEnable() {
            call.thenAnswer(0, "").thenAnswer(1, "Failed to enable unit: Unit file pm-mcpd.service does not exist.");

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.load(SYSTEMD_UNIT));

            assertEquals("Command 'systemctl --user enable --now pm-mcpd.service' exited with 1: "
                    + "Failed to enable unit: Unit file pm-mcpd.service does not exist.", thrown.getMessage());
        }

        @Test
        @DisplayName("fails the load without enabling when daemon-reload exits non-zero")
        void shouldFailOnDaemonReload() {
            call.thenAnswer(1, "Failed to connect to bus");

            assertThrows(ServiceUnitException.class, () -> manager.load(SYSTEMD_UNIT));

            assertEquals(List.of(DAEMON_RELOAD), call.commands());
        }

        @Test
        @DisplayName("disables the unit without stopping it")
        void shouldDisable() {
            manager.disable(SYSTEMD_UNIT);

            assertEquals(List.of(DISABLE), call.commands());
        }

        @Test
        @DisplayName("fails the disable when it exits non-zero")
        void shouldFailOnDisable() {
            call.thenAnswer(1, "Failed to disable unit");

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.disable(SYSTEMD_UNIT));

            assertTrue(thrown.getMessage().startsWith("Command 'systemctl --user disable pm-mcpd.service'"),
                    thrown.getMessage());
        }

        @Test
        @DisplayName("stops the unit and reloads the user manager on unload")
        void shouldUnload() {
            manager.unload(SYSTEMD_UNIT);

            assertEquals(List.of(STOP, DAEMON_RELOAD), call.commands());
        }

        @Test
        @DisplayName("treats a unit that is not loaded as success on unload")
        void shouldUnloadWhenNotLoaded() {
            call.thenAnswer(5, "Failed to stop pm-mcpd.service: Unit pm-mcpd.service not loaded.");

            manager.unload(SYSTEMD_UNIT);

            assertEquals(List.of(STOP, DAEMON_RELOAD), call.commands());
        }

        @Test
        @DisplayName("fails the unload when stop exits with another code")
        void shouldFailOnStop() {
            call.thenAnswer(1, "Job for pm-mcpd.service failed");

            var thrown = assertThrows(ServiceUnitException.class, () -> manager.unload(SYSTEMD_UNIT));

            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith(
                            "Command 'systemctl --user stop pm-mcpd.service' exited with 1"), thrown.getMessage()),
                    () -> assertEquals(List.of(STOP), call.commands()));
        }
    }

    @Nested
    @DisplayName("for platform")
    class ForPlatform {

        @Test
        @DisplayName("is launchd for the launchd platform")
        void shouldBeLaunchd() {
            var manager = ServiceManager.forPlatform(Platform.LAUNCHD, new RecordingProcessCall(), UID);

            assertInstanceOf(LaunchdServiceManager.class, manager);
        }

        @Test
        @DisplayName("is systemd for the systemd platform")
        void shouldBeSystemd() {
            var manager = ServiceManager.forPlatform(Platform.SYSTEMD, new RecordingProcessCall(), UID);

            assertInstanceOf(SystemdServiceManager.class, manager);
        }

        @Test
        @DisplayName("names the user id in the launchd GUI domain")
        void shouldUseUid() {
            var call = new RecordingProcessCall();

            ServiceManager.forPlatform(Platform.LAUNCHD, call, 1234).unload(LAUNCH_AGENT);

            assertEquals(List.of(List.of("launchctl", "bootout", "gui/1234/de.planmarshall.mcpd")), call.commands());
        }

        @Test
        @DisplayName("refuses a missing process call")
        void shouldRefuseNullCall() {
            assertThrows(NullPointerException.class, () -> ServiceManager.forPlatform(Platform.SYSTEMD, null, UID));
        }
    }
}
