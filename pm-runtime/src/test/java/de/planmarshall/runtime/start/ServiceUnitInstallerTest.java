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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.start.RecordingProcessCall.Call;
import de.planmarshall.runtime.start.ServiceUnitFile.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Install and uninstall of the per-user service (PM-ARCH-7) in a temporary home, with the real service managers
 * over a recording fake: no test starts {@code launchctl} or {@code systemctl}, and the real home is never written.
 * <p>
 * Install and uninstall may run inside the runtime the service started, where the command that stops the service
 * ends the caller. The fake notes whether the unit file was present at every command, which proves that install
 * writes the unit before any command and that uninstall removes it before any command that can stop the service.
 */
@EnableTestLogger
@DisplayName("Service unit installer")
class ServiceUnitInstallerTest {

    private static final long UID = 501;
    private static final Path DAEMON = Path.of("/opt/plan-marshall-mcp/bin/pm-mcpd");

    private static final List<String> DAEMON_RELOAD = List.of("systemctl", "--user", "daemon-reload");
    private static final List<String> ENABLE = List.of("systemctl", "--user", "enable", "--now", "pm-mcpd.service");
    private static final List<String> DISABLE = List.of("systemctl", "--user", "disable", "pm-mcpd.service");
    private static final List<String> STOP = List.of("systemctl", "--user", "stop", "pm-mcpd.service");
    private static final List<String> BOOTOUT = List.of("launchctl", "bootout", "gui/501/de.planmarshall.mcpd");

    @TempDir
    Path home;

    private ServiceUnitFile unit(Platform platform) {
        return new ServiceUnitFile(platform, home, null, DAEMON);
    }

    private static ServiceUnitInstaller installer(ServiceUnitFile unit, RecordingProcessCall call) {
        return new ServiceUnitInstaller(unit, ServiceManager.forPlatform(unit.platform(), call, UID));
    }

    private static List<String> bootstrap(ServiceUnitFile unit) {
        return List.of("launchctl", "bootstrap", "gui/501", unit.path().toString());
    }

    private static String golden(Platform platform) throws IOException {
        var name = switch (platform) {
            case LAUNCHD -> "de.planmarshall.mcpd.plist";
            case SYSTEMD -> "pm-mcpd.service";
        };
        try (var in = ServiceUnitInstallerTest.class.getResourceAsStream("/service-unit/" + name)) {
            if (in == null) {
                throw new IOException("Missing golden file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String installed(ServiceUnitFile unit) {
        return "Service unit '%s' installed for daemon '%s'".formatted(unit.path(), DAEMON);
    }

    private static String uninstalled(ServiceUnitFile unit) {
        return "Service unit '%s' uninstalled".formatted(unit.path());
    }

    private static String managerFailed(String operation) {
        return "Service manager command '%s' failed".formatted(operation);
    }

    @Nested
    @DisplayName("on the build platform")
    class CurrentPlatform {

        @Test
        @EnabledOnOs(OS.MAC)
        @DisplayName("installs and uninstalls the LaunchAgent on macOS")
        void shouldInstallAndUninstallOnMac() {
            var unit = unit(Platform.current());
            var call = new RecordingProcessCall(unit.path());
            var installer = installer(unit, call);

            installer.install();

            assertAll(
                    () -> assertEquals(home.resolve("Library/LaunchAgents/de.planmarshall.mcpd.plist"), unit.path()),
                    () -> assertEquals(golden(Platform.LAUNCHD), Files.readString(unit.path())),
                    () -> assertEquals(List.of(BOOTOUT, bootstrap(unit)), call.commands()));

            installer.uninstall();

            assertAll(
                    () -> assertFalse(Files.exists(unit.path())),
                    () -> assertEquals(List.of(BOOTOUT, bootstrap(unit), BOOTOUT), call.commands()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, installed(unit)),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, uninstalled(unit)));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("installs and uninstalls the systemd user unit on Linux")
        void shouldInstallAndUninstallOnLinux() {
            var unit = unit(Platform.current());
            var call = new RecordingProcessCall(unit.path());
            var installer = installer(unit, call);

            installer.install();

            assertAll(
                    () -> assertEquals(home.resolve(".config/systemd/user/pm-mcpd.service"), unit.path()),
                    () -> assertEquals(golden(Platform.SYSTEMD), Files.readString(unit.path())),
                    () -> assertEquals(List.of(DAEMON_RELOAD, ENABLE), call.commands()));

            installer.uninstall();

            assertAll(
                    () -> assertFalse(Files.exists(unit.path())),
                    () -> assertEquals(List.of(DAEMON_RELOAD, ENABLE, DISABLE, STOP, DAEMON_RELOAD),
                            call.commands()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, installed(unit)),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, uninstalled(unit)));
        }
    }

    @Nested
    @DisplayName("order of the file work and the commands")
    class Order {

        @Test
        @DisplayName("install writes the LaunchAgent before it boots out and bootstraps the job")
        void shouldWriteBeforeLaunchdLoad() {
            var unit = unit(Platform.LAUNCHD);
            var call = new RecordingProcessCall(unit.path());

            installer(unit, call).install();

            assertEquals(List.of(new Call(BOOTOUT, true), new Call(bootstrap(unit), true)), call.calls());
        }

        @Test
        @DisplayName("install writes the systemd unit before it reloads and enables it")
        void shouldWriteBeforeSystemdLoad() {
            var unit = unit(Platform.SYSTEMD);
            var call = new RecordingProcessCall(unit.path());

            installer(unit, call).install();

            assertEquals(List.of(new Call(DAEMON_RELOAD, true), new Call(ENABLE, true)), call.calls());
        }

        @Test
        @DisplayName("uninstall removes the LaunchAgent before it boots the job out")
        void shouldRemoveBeforeLaunchdUnload() {
            var unit = unit(Platform.LAUNCHD);
            unit.write();
            var call = new RecordingProcessCall(unit.path());

            installer(unit, call).uninstall();

            assertEquals(List.of(new Call(BOOTOUT, false)), call.calls());
        }

        @Test
        @DisplayName("uninstall disables the systemd unit while present and removes it before it stops the unit")
        void shouldRemoveBeforeSystemdUnload() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();
            var call = new RecordingProcessCall(unit.path());

            installer(unit, call).uninstall();

            assertEquals(List.of(new Call(DISABLE, true), new Call(STOP, false), new Call(DAEMON_RELOAD, false)),
                    call.calls());
        }
    }

    @Nested
    @DisplayName("install")
    class Install {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("replaces an existing unit on a reinstall")
        void shouldReplaceOnReinstall(Platform platform) throws Exception {
            var unit = unit(platform);
            Files.createDirectories(unit.path().getParent());
            Files.writeString(unit.path(), "a unit of an older version");
            var call = new RecordingProcessCall(unit.path());

            installer(unit, call).install();

            assertAll(
                    () -> assertEquals(golden(platform), Files.readString(unit.path())),
                    () -> assertEquals(2, call.calls().size()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.SERVICE_UNIT_INSTALLED.resolveIdentifierString()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("keeps the unit, logs and rethrows when the service manager fails")
        void shouldKeepUnitOnManagerFailure(Platform platform) {
            var unit = unit(platform);
            var failure = new ServiceUnitException("Command could not run");
            var call = new RecordingProcessCall(unit.path()).thenFail(failure);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install);

            assertAll(
                    () -> assertSame(failure, thrown),
                    () -> assertEquals(golden(platform), Files.readString(unit.path())),
                    () -> assertEquals(1, call.calls().size()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.SERVICE_MANAGER_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                            managerFailed("load") + ": Command could not run"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, ServiceUnitInstaller.class));
        }

        @Test
        @DisplayName("issues no command when the unit cannot be written")
        void shouldNotLoadUnwrittenUnit() throws Exception {
            Files.writeString(home.resolve("Library"), "a file where a directory belongs");
            var unit = unit(Platform.LAUNCHD);
            var call = new RecordingProcessCall(unit.path());
            var installer = installer(unit, call);

            assertThrows(ServiceUnitException.class, installer::install);

            assertEquals(List.of(), call.calls());
        }
    }

    @Nested
    @DisplayName("uninstall")
    class Uninstall {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("issues no command and is no failure without a unit")
        void shouldIgnoreMissingUnit(Platform platform) {
            var unit = unit(platform);
            var call = new RecordingProcessCall(unit.path());

            installer(unit, call).uninstall();

            assertAll(
                    () -> assertEquals(List.of(), call.calls()),
                    () -> assertFalse(unit.exists()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, uninstalled(unit)),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, ServiceUnitInstaller.class));
        }

        @Test
        @DisplayName("removes the unit, logs and rethrows when unloading fails")
        void shouldRemoveUnitOnUnloadFailure() {
            var unit = unit(Platform.LAUNCHD);
            unit.write();
            var failure = new ServiceUnitException("Command could not run");
            var call = new RecordingProcessCall(unit.path()).thenFail(failure);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall);

            assertAll(
                    () -> assertSame(failure, thrown),
                    () -> assertFalse(unit.exists()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.SERVICE_MANAGER_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, managerFailed("unload")),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, ServiceUnitInstaller.class));
        }

        @Test
        @DisplayName("removes the unit and still unloads it when disabling fails")
        void shouldRemoveAndUnloadOnDisableFailure() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();
            var call = new RecordingProcessCall(unit.path()).thenAnswer(1, "Failed to disable unit");
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall);

            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith(
                            "Command 'systemctl --user disable pm-mcpd.service' exited with 1"), thrown.getMessage()),
                    () -> assertFalse(unit.exists()),
                    () -> assertEquals(List.of(DISABLE, STOP, DAEMON_RELOAD), call.commands()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            managerFailed("disable")));
        }

        @Test
        @DisplayName("throws the first failure with the later one suppressed when disabling and unloading fail")
        void shouldKeepFirstFailure() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();
            var disableFailure = new ServiceUnitException("disable could not run");
            var unloadFailure = new ServiceUnitException("stop could not run");
            var call = new RecordingProcessCall(unit.path()).thenFail(disableFailure).thenFail(unloadFailure);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall);

            assertAll(
                    () -> assertSame(disableFailure, thrown),
                    () -> assertArrayEquals(new Throwable[]{unloadFailure}, thrown.getSuppressed()),
                    () -> assertFalse(unit.exists()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, managerFailed("disable")),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR, managerFailed("unload")));
        }

        @Test
        @DisplayName("does not unload when the unit cannot be removed, so the service keeps its unit")
        void shouldNotUnloadUnremovableUnit() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            Files.createDirectories(unit.path());
            Files.writeString(unit.path().resolve("child"), "keeps the directory non-empty");
            var call = new RecordingProcessCall(unit.path());
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall);

            assertAll(
                    () -> assertTrue(thrown.getMessage().contains("could not be removed"), thrown.getMessage()),
                    () -> assertEquals(List.of(DISABLE), call.commands()),
                    () -> assertTrue(unit.exists()));
        }

        @Test
        @DisplayName("suppresses the removal failure under an earlier disable failure")
        void shouldSuppressRemovalFailure() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            Files.createDirectories(unit.path());
            Files.writeString(unit.path().resolve("child"), "keeps the directory non-empty");
            var disableFailure = new ServiceUnitException("disable could not run");
            var call = new RecordingProcessCall(unit.path()).thenFail(disableFailure);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall);

            assertAll(
                    () -> assertSame(disableFailure, thrown),
                    () -> assertEquals(1, thrown.getSuppressed().length),
                    () -> assertTrue(thrown.getSuppressed()[0].getMessage().contains("could not be removed")),
                    () -> assertEquals(List.of(DISABLE), call.commands()));
        }
    }

    @Test
    @DisplayName("refuses a missing unit or service manager")
    void shouldRefuseMissingParts() {
        var unit = unit(Platform.SYSTEMD);
        var manager = ServiceManager.forPlatform(Platform.SYSTEMD, new RecordingProcessCall(), UID);

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new ServiceUnitInstaller(null, manager)),
                () -> assertThrows(NullPointerException.class, () -> new ServiceUnitInstaller(unit, null)));
    }
}
