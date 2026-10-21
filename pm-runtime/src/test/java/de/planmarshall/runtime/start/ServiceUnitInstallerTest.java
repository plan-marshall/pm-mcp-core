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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.RecordingProcessCall.Call;
import de.planmarshall.runtime.start.ServiceUnitFile.Platform;

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

    /**
     * The unit file of a platform under the temporary home, derived from the documented layout rather than from
     * {@link ServiceUnitFile#path()}.
     */
    private static Path unitFile(Path home, Platform platform) {
        return switch (platform) {
            case LAUNCHD -> home.resolve("Library/LaunchAgents/" + ServiceUnitFile.LAUNCHD_LABEL + ".plist");
            case SYSTEMD -> home.resolve(".config/systemd/user/" + ServiceUnitFile.SYSTEMD_UNIT);
        };
    }

    private static Path previous(Path unitFile) {
        return unitFile.resolveSibling(unitFile.getFileName() + ".previous");
    }

    private static Path reinstall(Path unitFile) {
        return unitFile.resolveSibling(unitFile.getFileName() + ".reinstall");
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
        @DisplayName("removes the new unit, logs and rethrows when the service manager fails and no unit was installed")
        void shouldRemoveNewUnitOnManagerFailure(Platform platform) {
            var unit = unit(platform);
            var failure = new ServiceUnitException("Command could not run");
            var call = new RecordingProcessCall(unit.path()).thenFail(failure);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install);

            assertAll(
                    () -> assertSame(failure, thrown),
                    () -> assertFalse(unit.exists()),
                    () -> assertEquals(1, call.calls().size()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.SERVICE_MANAGER_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                            managerFailed("load") + ": Command could not run"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS.resolveIdentifierString()));
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
    @DisplayName("restore of the previous unit")
    class Restore {

        private static final String PREVIOUS_UNIT = "a unit of an older version";

        private static String restored(Path unitFile) {
            return "Service unit '%s' restored and loaded from the copy a reinstall kept".formatted(unitFile);
        }

        private static String removedWithoutPrevious(Path unitFile) {
            return "Service unit '%s' removed, the reinstall kept no previous unit".formatted(unitFile);
        }

        private static String restoreFailed(Path unitFile) {
            return "Service unit '%s' could not be restored".formatted(unitFile);
        }

        /**
         * @return the commands of a successful load of the platform's unit
         */
        private static List<List<String>> load(ServiceUnitFile unit) {
            return switch (unit.platform()) {
                case LAUNCHD -> List.of(BOOTOUT, bootstrap(unit));
                case SYSTEMD -> List.of(DAEMON_RELOAD, ENABLE);
            };
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("keeps the previous unit in place and loaded when the load fails")
        void shouldRestorePreviousUnit(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            var loadFailure = new ServiceUnitException("load could not run");
            var call = new RecordingProcessCall(file).thenFail(loadFailure);
            var unit = unit(platform);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install);

            var commands = call.commands();
            assertAll(
                    () -> assertSame(loadFailure, thrown),
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertEquals(load(unit), commands.subList(1, commands.size())),
                    () -> assertTrue(call.calls().getLast().unitPresent()),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, restored(file)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("leaves no unit file when the load fails and no unit was installed")
        void shouldLeaveNoUnitWithoutPrevious(Platform platform) {
            var file = unitFile(home, platform);
            var loadFailure = new ServiceUnitException("load could not run");
            var call = new RecordingProcessCall(file).thenFail(loadFailure);
            var installer = installer(unit(platform), call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install);

            assertAll(
                    () -> assertSame(loadFailure, thrown),
                    () -> assertFalse(Files.exists(file)),
                    () -> assertEquals(1, call.calls().size()),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            removedWithoutPrevious(file)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("reports a failed restore with both failures, the load first")
        void shouldReportBothFailures(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            var loadFailure = new ServiceUnitException("load could not run");
            var restoreFailure = new ServiceUnitException("restore could not run");
            var call = new RecordingProcessCall(file).thenFail(loadFailure).thenFail(restoreFailure);
            var installer = installer(unit(platform), call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install);

            assertAll(
                    () -> assertSame(loadFailure, thrown),
                    () -> assertArrayEquals(new Throwable[]{restoreFailure}, thrown.getSuppressed()),
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            restoreFailed(file) + ": " + restoreFailure.getMessage()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("restores the previous unit when the second command of the load fails")
        void shouldRestoreAfterTheLoadFailsLate(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            var call = new RecordingProcessCall(file).thenAnswer(0, "").thenAnswer(1, "load failed");
            var unit = unit(platform);

            assertThrows(ServiceUnitException.class, installer(unit, call)::install);

            assertAll(
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertEquals(4, call.calls().size()),
                    () -> assertEquals(load(unit), call.commands().subList(2, 4)),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))));
        }

        @Test
        @DisplayName("keeps the previous unit before every command of the load")
        void shouldKeepBeforeLoad() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            var call = new RecordingProcessCall(file);
            var pendingAtCommand = new ArrayList<Boolean>();
            ProcessCall observing = command -> {
                pendingAtCommand.add(Files.exists(reinstall(file)));
                return call.run(command);
            };

            new ServiceUnitInstaller(unit(Platform.LAUNCHD),
                    ServiceManager.forPlatform(Platform.LAUNCHD, observing, UID)).install();

            assertAll(
                    () -> assertEquals(List.of(true, true), pendingAtCommand),
                    () -> assertEquals(List.of(BOOTOUT, bootstrap(unit(Platform.LAUNCHD))), call.commands()),
                    () -> assertFalse(Files.exists(reinstall(file))));
        }

        @Test
        @DisplayName("keeps the pending previous unit when install runs again before the restore")
        void shouldKeepThePendingUnitOnAnotherInstall() throws Exception {
            var file = unitFile(home, Platform.SYSTEMD);
            Files.createDirectories(file.getParent());
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.writeString(reinstall(file), "");
            unit(Platform.SYSTEMD).write();
            var call = new RecordingProcessCall(file).thenFail(new ServiceUnitException("load could not run"));

            assertThrows(ServiceUnitException.class, installer(unit(Platform.SYSTEMD), call)::install);

            assertAll(
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))));
        }

        @Test
        @DisplayName("keeps the install a success when the kept state cannot be dropped")
        void shouldReportUndroppedKeptState() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(reinstall(file), "");
            Files.createDirectories(previous(file));
            Files.writeString(previous(file).resolve("child"), "keeps the directory non-empty");
            var call = new RecordingProcessCall(file);

            installer(unit(Platform.LAUNCHD), call).install();

            assertAll(
                    () -> assertEquals(golden(Platform.LAUNCHD), Files.readString(file)),
                    () -> assertTrue(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            installed(unit(Platform.LAUNCHD))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.SERVICE_UNIT_KEPT_NOT_DROPPED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("keeps the install a success and restores at the next start when the marker cannot be dropped")
        void shouldRestoreWhenTheMarkerCannotBeDropped() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.createDirectories(reinstall(file));
            Files.writeString(reinstall(file).resolve("child"), "keeps the directory non-empty");
            var call = new RecordingProcessCall(file);

            installer(unit(Platform.LAUNCHD), call).install();

            assertAll(
                    () -> assertEquals(golden(Platform.LAUNCHD), Files.readString(file)),
                    () -> assertTrue(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.SERVICE_UNIT_KEPT_NOT_DROPPED.resolveIdentifierString()));

            var restoreCall = new RecordingProcessCall(file);
            var restoreFailure = assertThrows(ServiceUnitException.class,
                    installer(unit(Platform.LAUNCHD), restoreCall)::restore);

            assertAll(
                    () -> assertTrue(restoreFailure.getMessage().contains("kept state"), restoreFailure.getMessage()),
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertEquals(List.of(), restoreCall.calls()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, restoreFailed(file)));
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("keeps the unit in force and its marker with mode rw-------")
        void shouldKeepWithRestrictedModes() {
            var file = unitFile(home, Platform.SYSTEMD);
            unit(Platform.SYSTEMD).write();
            unit(Platform.SYSTEMD).keepForRestore();

            assertAll(
                    () -> assertEquals("rw-------", PosixFilePermissions.toString(
                            Files.getPosixFilePermissions(previous(file)))),
                    () -> assertEquals("rw-------", PosixFilePermissions.toString(
                            Files.getPosixFilePermissions(reinstall(file)))));
        }

        @Test
        @DisplayName("drops the pair it kept when the unit cannot be written")
        void shouldDropKeptPairOnWriteFailure() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(tmp);
            Files.writeString(tmp.resolve("child"), "keeps the directory non-empty");
            var call = new RecordingProcessCall(file);

            assertThrows(ServiceUnitException.class, installer(unit(Platform.LAUNCHD), call)::install);

            assertAll(
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertEquals(List.of(), call.calls()),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))));
        }

        @Test
        @DisplayName("leaves a pending pair when the unit cannot be written")
        void shouldLeavePendingPairOnWriteFailure() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.writeString(reinstall(file), "");
            var tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.createDirectories(tmp);
            Files.writeString(tmp.resolve("child"), "keeps the directory non-empty");

            assertThrows(ServiceUnitException.class,
                    installer(unit(Platform.LAUNCHD), new RecordingProcessCall(file))::install);

            assertAll(
                    () -> assertTrue(Files.exists(previous(file))),
                    () -> assertTrue(Files.exists(reinstall(file))));
        }

        @Test
        @DisplayName("drops the kept pair when the start's restore load fails, so no later start retries it")
        void shouldNotRetryAFailedRestoreLoad() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.writeString(reinstall(file), "");
            var restoreFailure = new ServiceUnitException("load could not run");
            var call = new RecordingProcessCall(file).thenFail(restoreFailure);

            var thrown = assertThrows(ServiceUnitException.class,
                    installer(unit(Platform.LAUNCHD), call)::restore);

            assertAll(
                    () -> assertSame(restoreFailure, thrown),
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, restoreFailed(file)));
        }

        @Test
        @DisplayName("drops the kept unit before the restore loads it, so an ended caller cannot loop")
        void shouldDropBeforeTheRestoreLoad() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.writeString(reinstall(file), "");
            var call = new RecordingProcessCall(file);
            var pendingAtCommand = new ArrayList<Boolean>();
            ProcessCall observing = command -> {
                pendingAtCommand.add(Files.exists(reinstall(file)));
                return call.run(command);
            };
            var installer = new ServiceUnitInstaller(unit(Platform.LAUNCHD),
                    ServiceManager.forPlatform(Platform.LAUNCHD, observing, UID));

            var restored = installer.restore();

            assertAll(
                    () -> assertTrue(restored),
                    () -> assertEquals(List.of(false, false), pendingAtCommand),
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)));
        }

        @Test
        @DisplayName("restores the kept LaunchAgent at the start after an interrupted reinstall on macOS")
        void shouldRestoreAtStart() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, PREVIOUS_UNIT);
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.writeString(reinstall(file), "");
            unit(Platform.LAUNCHD).write();
            var call = new RecordingProcessCall(file);
            var installer = installer(unit(Platform.LAUNCHD), call);

            var restored = installer.restore();

            assertAll(
                    () -> assertTrue(restored),
                    () -> assertEquals(PREVIOUS_UNIT, Files.readString(file)),
                    () -> assertEquals(List.of(BOOTOUT, bootstrap(unit(Platform.LAUNCHD))), call.commands()),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO, restored(file)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("removes the replacement at the start when an interrupted reinstall kept no previous unit")
        void shouldRemoveReplacementAtStartWithoutPrevious(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            unit(platform).write();
            Files.writeString(reinstall(file), "");
            var call = new RecordingProcessCall(file);

            var restored = installer(unit(platform), call).restore();

            assertAll(
                    () -> assertTrue(restored),
                    () -> assertFalse(Files.exists(file)),
                    () -> assertEquals(List.of(), call.commands()),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            removedWithoutPrevious(file)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("issues no command at a start without a pending reinstall")
        void shouldIgnoreWithoutPendingReinstall(Platform platform) {
            var file = unitFile(home, platform);
            var call = new RecordingProcessCall(file);

            var restored = installer(unit(platform), call).restore();

            assertAll(
                    () -> assertFalse(restored),
                    () -> assertEquals(List.of(), call.commands()));
        }

        @Test
        @DisplayName("discards a kept unit on uninstall before the first command, so a later start restores nothing")
        void shouldDiscardOnUninstall() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            unit(Platform.LAUNCHD).write();
            Files.writeString(previous(file), PREVIOUS_UNIT);
            Files.writeString(reinstall(file), "");
            var call = new RecordingProcessCall(file);
            var keptAtCommand = new ArrayList<Boolean>();
            ProcessCall observing = command -> {
                keptAtCommand.add(Files.exists(previous(file)) || Files.exists(reinstall(file)));
                return call.run(command);
            };

            new ServiceUnitInstaller(unit(Platform.LAUNCHD),
                    ServiceManager.forPlatform(Platform.LAUNCHD, observing, UID)).uninstall();

            assertAll(
                    () -> assertEquals(List.of(false), keptAtCommand),
                    () -> assertFalse(Files.exists(file)),
                    () -> assertFalse(Files.exists(previous(file))),
                    () -> assertFalse(Files.exists(reinstall(file))));
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
