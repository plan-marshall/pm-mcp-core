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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import java.util.Locale;
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
import de.planmarshall.runtime.start.ProcessCallDoubles.Call;
import de.planmarshall.runtime.start.ServiceUnitFile.Platform;

/**
 * Install and uninstall of the per-user service (PM-ARCH-7) in a temporary home, with the real service managers
 * over a recording fake: no test starts {@code launchctl} or {@code systemctl}, and the real home is never written.
 * <p>
 * Install and uninstall may run inside the runtime the service started, where the command that stops the service
 * ends the caller. The fake notes whether the unit file was present at every command, which proves that install
 * writes the unit before any command and that uninstall removes it before any command that can stop the service.
 * <p>
 * The unit file content is the golden file of its platform, and the command words, the unit paths and the log texts
 * are the contract of the service managers and of the installer; the user id, the content of an older unit, the
 * failure messages and the exit codes are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Service unit installer")
class ServiceUnitInstallerTest {

    /** Golden file path: the daemon path the golden unit files were rendered with. */
    private static final Path DAEMON = Path.of("/opt/plan-marshall-mcp/bin/pm-mcpd");

    private static final List<String> DAEMON_RELOAD = List.of("systemctl", "--user", "daemon-reload");
    private static final List<String> ENABLE = List.of("systemctl", "--user", "enable", "--now", "pm-mcpd.service");
    private static final List<String> DISABLE = List.of("systemctl", "--user", "disable", "pm-mcpd.service");
    private static final List<String> STOP = List.of("systemctl", "--user", "stop", "pm-mcpd.service");

    @TempDir
    Path home;

    private final long uid = Generators.integers(500, 60_000).next();
    private final List<String> bootout = List.of("launchctl", "bootout", "gui/" + uid + "/de.planmarshall.mcpd");
    private final String previousUnit = content();

    /** The calls of the EasyMock process call, in order; a fresh list per test instance. */
    private final List<Call> calls = new ArrayList<>();

    private ProcessCall call(Path watched, ProcessCallDoubles.Answer... answers) {
        return ProcessCallDoubles.create(watched, calls, answers);
    }

    private List<List<String>> commands() {
        return calls.stream().map(Call::command).toList();
    }

    private ServiceUnitFile unit(Platform platform) {
        return new ServiceUnitFile(platform, home, null, DAEMON);
    }

    private ServiceUnitInstaller installer(ServiceUnitFile unit, ProcessCall call) {
        return new ServiceUnitInstaller(unit, ServiceManager.forPlatform(unit.platform(), call, uid));
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

    private List<String> bootstrap(ServiceUnitFile unit) {
        return List.of("launchctl", "bootstrap", "gui/" + uid, unit.path().toString());
    }

    private static String content() {
        return Generators.letterStrings(3, 60).next();
    }

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static ServiceUnitException failure() {
        return new ServiceUnitException(Generators.letterStrings(3, 40).next());
    }

    private static int failingExitCode() {
        return Generators.integers(1, 255).next();
    }

    /** Makes the path a non-empty directory, which cannot be replaced or removed as a file. */
    private static void blockWithDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        Files.writeString(path.resolve(name()), content());
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

    private static void assertInstalledLogged(ServiceUnitFile unit) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.SERVICE_UNIT_INSTALLED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "Service unit '%s' installed for daemon '%s'".formatted(unit.path(), DAEMON));
    }

    private static void assertUninstalledLogged(ServiceUnitFile unit) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.SERVICE_UNIT_UNINSTALLED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "Service unit '%s' uninstalled".formatted(unit.path()));
    }

    private static void assertManagerFailedLogged(String operation) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                ERROR.SERVICE_MANAGER_FAILED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                "Service manager command '%s' failed".formatted(operation));
    }

    private static void assertRestoredLogged(Path unitFile) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.SERVICE_UNIT_RESTORED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "Service unit '%s' restored and loaded from the copy a reinstall kept".formatted(unitFile));
    }

    private static void assertRemovedWithoutPreviousLogged(Path unitFile) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.SERVICE_UNIT_RESTORE_WITHOUT_PREVIOUS.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                "Service unit '%s' removed, the reinstall kept no previous unit".formatted(unitFile));
    }

    private static void assertRestoreFailedLogged(Path unitFile, String reason) {
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.SERVICE_UNIT_RESTORE_FAILED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "Service unit '%s' could not be restored".formatted(unitFile) + reason);
    }

    @Nested
    @DisplayName("on the build platform")
    class CurrentPlatform {

        @Test
        @EnabledOnOs(OS.MAC)
        @DisplayName("installs and uninstalls the LaunchAgent on macOS")
        void shouldInstallAndUninstallOnMac() {
            var unit = unit(Platform.current());
            var call = call(unit.path());
            var installer = installer(unit, call);

            installer.install();

            assertAll(
                    () -> assertEquals(home.resolve("Library/LaunchAgents/de.planmarshall.mcpd.plist"), unit.path(),
                            "the LaunchAgent must lie in Library/LaunchAgents"),
                    () -> assertEquals(golden(Platform.LAUNCHD), Files.readString(unit.path()),
                            "the LaunchAgent must match its golden file"),
                    () -> assertEquals(List.of(bootout, bootstrap(unit)), commands(),
                            "the install must boot out, then bootstrap"));

            installer.uninstall();

            assertAll(
                    () -> assertFalse(Files.exists(unit.path()), "the uninstall must remove the LaunchAgent"),
                    () -> assertEquals(List.of(bootout, bootstrap(unit), bootout), commands(),
                            "the uninstall must boot the job out"),
                    () -> assertInstalledLogged(unit),
                    () -> assertUninstalledLogged(unit));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("installs and uninstalls the systemd user unit on Linux")
        void shouldInstallAndUninstallOnLinux() {
            var unit = unit(Platform.current());
            var call = call(unit.path());
            var installer = installer(unit, call);

            installer.install();

            assertAll(
                    () -> assertEquals(home.resolve(".config/systemd/user/pm-mcpd.service"), unit.path(),
                            "the systemd unit must lie below .config"),
                    () -> assertEquals(golden(Platform.SYSTEMD), Files.readString(unit.path()),
                            "the systemd unit must match its golden file"),
                    () -> assertEquals(List.of(DAEMON_RELOAD, ENABLE), commands(),
                            "the install must reload, then enable"));

            installer.uninstall();

            assertAll(
                    () -> assertFalse(Files.exists(unit.path()), "the uninstall must remove the systemd unit"),
                    () -> assertEquals(List.of(DAEMON_RELOAD, ENABLE, DISABLE, STOP, DAEMON_RELOAD),
                            commands(), "the uninstall must disable, stop and reload"),
                    () -> assertInstalledLogged(unit),
                    () -> assertUninstalledLogged(unit));
        }
    }

    @Nested
    @DisplayName("order of the file work and the commands")
    class Order {

        @Test
        @DisplayName("install writes the LaunchAgent before it boots out and bootstraps the job")
        void shouldWriteBeforeLaunchdLoad() {
            var unit = unit(Platform.LAUNCHD);
            var call = call(unit.path());

            installer(unit, call).install();

            assertEquals(List.of(new Call(bootout, true), new Call(bootstrap(unit), true)), calls,
                    "the LaunchAgent must exist at every command");
        }

        @Test
        @DisplayName("install writes the systemd unit before it reloads and enables it")
        void shouldWriteBeforeSystemdLoad() {
            var unit = unit(Platform.SYSTEMD);
            var call = call(unit.path());

            installer(unit, call).install();

            assertEquals(List.of(new Call(DAEMON_RELOAD, true), new Call(ENABLE, true)), calls,
                    "the systemd unit must exist at every command");
        }

        @Test
        @DisplayName("uninstall removes the LaunchAgent before it boots the job out")
        void shouldRemoveBeforeLaunchdUnload() {
            var unit = unit(Platform.LAUNCHD);
            unit.write();
            var call = call(unit.path());

            installer(unit, call).uninstall();

            assertEquals(List.of(new Call(bootout, false)), calls,
                    "the LaunchAgent must be gone before the bootout");
        }

        @Test
        @DisplayName("uninstall disables the systemd unit while present and removes it before it stops the unit")
        void shouldRemoveBeforeSystemdUnload() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();
            var call = call(unit.path());

            installer(unit, call).uninstall();

            assertEquals(List.of(new Call(DISABLE, true), new Call(STOP, false), new Call(DAEMON_RELOAD, false)),
                    calls, "the unit must be gone before the stop");
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
            Files.writeString(unit.path(), previousUnit);
            var call = call(unit.path());

            installer(unit, call).install();

            assertAll(
                    () -> assertEquals(golden(platform), Files.readString(unit.path()),
                            "the older unit must be replaced by the golden one"),
                    () -> assertEquals(2, calls.size(), "the reinstall must issue the two load commands"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.SERVICE_UNIT_INSTALLED.resolveIdentifierString()),
                    () -> assertInstalledLogged(unit));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("removes the new unit, logs and rethrows when the service manager fails and no unit was installed")
        void shouldRemoveNewUnitOnManagerFailure(Platform platform) {
            var unit = unit(platform);
            var failure = failure();
            var call = call(unit.path(), ProcessCallDoubles.failure(failure));
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install,
                    "a failing service manager must fail the install");

            assertAll(
                    () -> assertSame(failure, thrown, "the failure of the manager must be rethrown"),
                    () -> assertFalse(unit.exists(), "the new unit must be removed again"),
                    () -> assertEquals(1, calls.size(), "no command must follow the failing one"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.SERVICE_MANAGER_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Service manager command 'load' failed: " + failure.getMessage()),
                    () -> assertRemovedWithoutPreviousLogged(unit.path()));
        }

        @Test
        @DisplayName("issues no command when the unit cannot be written")
        void shouldNotLoadUnwrittenUnit() throws Exception {
            Files.writeString(home.resolve("Library"), content());
            var unit = unit(Platform.LAUNCHD);
            var call = call(unit.path());
            var installer = installer(unit, call);

            assertThrows(ServiceUnitException.class, installer::install, "an unwritable unit must fail the install");

            assertAll(
                    () -> assertEquals(List.of(), calls, "no command must be issued for an unwritten unit"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.SERVICE_UNIT_INSTALLED.resolveIdentifierString()));
        }
    }

    @Nested
    @DisplayName("restore of the previous unit")
    class Restore {

        /**
         * @return the commands of a successful load of the platform's unit
         */
        private List<List<String>> load(ServiceUnitFile unit) {
            return switch (unit.platform()) {
                case LAUNCHD -> List.of(bootout, bootstrap(unit));
                case SYSTEMD -> List.of(DAEMON_RELOAD, ENABLE);
            };
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("keeps the previous unit in place and loaded when the load fails")
        void shouldRestorePreviousUnit(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            var loadFailure = failure();
            var call = call(file, ProcessCallDoubles.failure(loadFailure));
            var unit = unit(platform);
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install,
                    "a failing load must fail the install");

            var commands = commands();
            assertAll(
                    () -> assertSame(loadFailure, thrown, "the load failure must be rethrown"),
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be back"),
                    () -> assertEquals(load(unit), commands.subList(1, commands.size()),
                            "the previous unit must be loaded again"),
                    () -> assertTrue(calls.getLast().unitPresent(), "the unit must exist at the reload"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRestoredLogged(file));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("leaves no unit file when the load fails and no unit was installed")
        void shouldLeaveNoUnitWithoutPrevious(Platform platform) {
            var file = unitFile(home, platform);
            var loadFailure = failure();
            var call = call(file, ProcessCallDoubles.failure(loadFailure));
            var installer = installer(unit(platform), call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install,
                    "a failing load must fail the install");

            assertAll(
                    () -> assertSame(loadFailure, thrown, "the load failure must be rethrown"),
                    () -> assertFalse(Files.exists(file), "no unit file must be left behind"),
                    () -> assertEquals(1, calls.size(), "nothing must be loaded again"),
                    () -> assertFalse(Files.exists(previous(file)), "no kept copy must be left behind"),
                    () -> assertFalse(Files.exists(reinstall(file)), "no reinstall marker must be left behind"),
                    () -> assertRemovedWithoutPreviousLogged(file));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("reports a failed restore with both failures, the load first")
        void shouldReportBothFailures(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            var loadFailure = failure();
            var restoreFailure = failure();
            var call = call(file, ProcessCallDoubles.failure(loadFailure),
                    ProcessCallDoubles.failure(restoreFailure));
            var installer = installer(unit(platform), call);

            var thrown = assertThrows(ServiceUnitException.class, installer::install,
                    "a failing load must fail the install");

            assertAll(
                    () -> assertSame(loadFailure, thrown, "the load failure must be rethrown first"),
                    () -> assertArrayEquals(new Throwable[]{restoreFailure}, thrown.getSuppressed(),
                            "the restore failure must be suppressed"),
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be back"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRestoreFailedLogged(file, ": " + restoreFailure.getMessage()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("restores the previous unit when the second command of the load fails")
        void shouldRestoreAfterTheLoadFailsLate(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            var call = call(file, ProcessCallDoubles.answer(0, ""),
                    ProcessCallDoubles.answer(failingExitCode(), content()));
            var unit = unit(platform);

            assertThrows(ServiceUnitException.class, installer(unit, call)::install,
                    "a failing second load command must fail");

            assertAll(
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be back"),
                    () -> assertEquals(4, calls.size(), "the load and the reload issue two commands each"),
                    () -> assertEquals(load(unit), commands().subList(2, 4),
                            "the previous unit must be loaded again"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRestoredLogged(file));
        }

        @Test
        @DisplayName("keeps the previous unit before every command of the load")
        void shouldKeepBeforeLoad() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            var call = call(file);
            var pendingAtCommand = new ArrayList<Boolean>();
            ProcessCall observing = command -> {
                pendingAtCommand.add(Files.exists(reinstall(file)));
                return call.run(command);
            };

            installer(unit(Platform.LAUNCHD), observing).install();

            assertAll(
                    () -> assertEquals(List.of(true, true), pendingAtCommand,
                            "the marker must exist at every load command"),
                    () -> assertEquals(List.of(bootout, bootstrap(unit(Platform.LAUNCHD))), commands(),
                            "the install must boot out, then bootstrap"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the marker must be dropped after the load"));
        }

        @Test
        @DisplayName("keeps the pending previous unit when install runs again before the restore")
        void shouldKeepThePendingUnitOnAnotherInstall() throws Exception {
            var file = unitFile(home, Platform.SYSTEMD);
            Files.createDirectories(file.getParent());
            Files.writeString(previous(file), previousUnit);
            Files.writeString(reinstall(file), "");
            unit(Platform.SYSTEMD).write();
            var call = call(file, ProcessCallDoubles.failure(failure()));

            assertThrows(ServiceUnitException.class, installer(unit(Platform.SYSTEMD), call)::install,
                    "a failing load must fail the install");

            assertAll(
                    () -> assertEquals(previousUnit, Files.readString(file), "the pending previous unit must be back"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRestoredLogged(file));
        }

        @Test
        @DisplayName("keeps the install a success when the kept state cannot be dropped")
        void shouldReportUndroppedKeptState() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(reinstall(file), "");
            blockWithDirectory(previous(file));
            var call = call(file);

            installer(unit(Platform.LAUNCHD), call).install();

            assertAll(
                    () -> assertEquals(golden(Platform.LAUNCHD), Files.readString(file),
                            "the new unit must be installed"),
                    () -> assertTrue(Files.exists(previous(file)), "the undroppable kept copy must stay"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertInstalledLogged(unit(Platform.LAUNCHD)),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.SERVICE_UNIT_KEPT_NOT_DROPPED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("keeps the install a success and restores at the next start when the marker cannot be dropped")
        void shouldRestoreWhenTheMarkerCannotBeDropped() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            Files.writeString(previous(file), previousUnit);
            blockWithDirectory(reinstall(file));
            var call = call(file);

            installer(unit(Platform.LAUNCHD), call).install();

            assertAll(
                    () -> assertEquals(golden(Platform.LAUNCHD), Files.readString(file),
                            "the new unit must be installed"),
                    () -> assertTrue(Files.exists(reinstall(file)), "the undroppable marker must stay"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.SERVICE_UNIT_KEPT_NOT_DROPPED.resolveIdentifierString()));

            var issued = calls.size();
            var restoreCall = call(file);
            var restoreFailure = assertThrows(ServiceUnitException.class,
                    installer(unit(Platform.LAUNCHD), restoreCall)::restore,
                    "the restore must fail on the undroppable marker");

            assertAll(
                    // Contract text: the failure names the kept state that cannot be dropped
                    () -> assertTrue(restoreFailure.getMessage().contains("kept state"),
                            "the failure must name the kept state"),
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be written back"),
                    () -> assertEquals(issued, calls.size(), "the failed restore must issue no command"),
                    () -> assertRestoreFailedLogged(file, ""));
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
                            Files.getPosixFilePermissions(previous(file))), "the kept copy must have mode 0600"),
                    () -> assertEquals("rw-------", PosixFilePermissions.toString(
                            Files.getPosixFilePermissions(reinstall(file))), "the marker must have mode 0600"));
        }

        @Test
        @DisplayName("drops the pair it kept when the unit cannot be written")
        void shouldDropKeptPairOnWriteFailure() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            blockWithDirectory(file.resolveSibling(file.getFileName() + ".tmp"));
            var call = call(file);

            assertThrows(ServiceUnitException.class, installer(unit(Platform.LAUNCHD), call)::install,
                    "an unwritable unit must fail the install");

            assertAll(
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must stay in force"),
                    () -> assertEquals(List.of(), calls, "no command must be issued for an unwritten unit"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"));
        }

        @Test
        @DisplayName("leaves a pending pair when the unit cannot be written")
        void shouldLeavePendingPairOnWriteFailure() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            Files.writeString(previous(file), previousUnit);
            Files.writeString(reinstall(file), "");
            blockWithDirectory(file.resolveSibling(file.getFileName() + ".tmp"));

            assertThrows(ServiceUnitException.class,
                    installer(unit(Platform.LAUNCHD), call(file))::install,
                    "an unwritable unit must fail the install");

            assertAll(
                    () -> assertTrue(Files.exists(previous(file)), "the pending kept copy must stay"),
                    () -> assertTrue(Files.exists(reinstall(file)), "the pending reinstall marker must stay"));
        }

        @Test
        @DisplayName("drops the kept pair when the start's restore load fails, so no later start retries it")
        void shouldNotRetryAFailedRestoreLoad() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(previous(file), previousUnit);
            Files.writeString(reinstall(file), "");
            var restoreFailure = failure();
            var call = call(file, ProcessCallDoubles.failure(restoreFailure));

            var thrown = assertThrows(ServiceUnitException.class,
                    installer(unit(Platform.LAUNCHD), call)::restore, "a failing restore load must fail");

            assertAll(
                    () -> assertSame(restoreFailure, thrown, "the restore failure must be rethrown"),
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be written back"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRestoreFailedLogged(file, ": " + restoreFailure.getMessage()),
                    () -> assertManagerFailedLogged("load"));
        }

        @Test
        @DisplayName("drops the kept unit before the restore loads it, so an ended caller cannot loop")
        void shouldDropBeforeTheRestoreLoad() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(previous(file), previousUnit);
            Files.writeString(reinstall(file), "");
            var call = call(file);
            var pendingAtCommand = new ArrayList<Boolean>();
            ProcessCall observing = command -> {
                pendingAtCommand.add(Files.exists(reinstall(file)));
                return call.run(command);
            };

            var restored = installer(unit(Platform.LAUNCHD), observing).restore();

            assertAll(
                    () -> assertTrue(restored, "the pending reinstall must be restored"),
                    () -> assertEquals(List.of(false, false), pendingAtCommand,
                            "the marker must be gone at every load command"),
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be written back"),
                    () -> assertRestoredLogged(file));
        }

        @Test
        @DisplayName("restores the kept LaunchAgent at the start after an interrupted reinstall on macOS")
        void shouldRestoreAtStart() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            Files.createDirectories(file.getParent());
            Files.writeString(file, previousUnit);
            Files.writeString(previous(file), previousUnit);
            Files.writeString(reinstall(file), "");
            unit(Platform.LAUNCHD).write();
            var call = call(file);
            var installer = installer(unit(Platform.LAUNCHD), call);

            var restored = installer.restore();

            assertAll(
                    () -> assertTrue(restored, "the pending reinstall must be restored"),
                    () -> assertEquals(previousUnit, Files.readString(file), "the previous unit must be written back"),
                    () -> assertEquals(List.of(bootout, bootstrap(unit(Platform.LAUNCHD))), commands(),
                            "the previous unit must be loaded again"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRestoredLogged(file));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("removes the replacement at the start when an interrupted reinstall kept no previous unit")
        void shouldRemoveReplacementAtStartWithoutPrevious(Platform platform) throws Exception {
            var file = unitFile(home, platform);
            unit(platform).write();
            Files.writeString(reinstall(file), "");
            var call = call(file);

            var restored = installer(unit(platform), call).restore();

            assertAll(
                    () -> assertTrue(restored, "the pending reinstall must be resolved"),
                    () -> assertFalse(Files.exists(file), "the replacement unit must be removed"),
                    () -> assertEquals(List.of(), commands(), "nothing must be loaded or unloaded"),
                    () -> assertFalse(Files.exists(previous(file)), "no kept copy must be left behind"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertRemovedWithoutPreviousLogged(file));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(Platform.class)
        @DisplayName("issues no command at a start without a pending reinstall")
        void shouldIgnoreWithoutPendingReinstall(Platform platform) {
            var file = unitFile(home, platform);
            var call = call(file);

            var restored = installer(unit(platform), call).restore();

            assertAll(
                    () -> assertFalse(restored, "nothing must be restored without a reinstall"),
                    () -> assertEquals(List.of(), commands(), "no command must be issued at the start"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, ServiceUnitInstaller.class));
        }

        @Test
        @DisplayName("discards a kept unit on uninstall before the first command, so a later start restores nothing")
        void shouldDiscardOnUninstall() throws Exception {
            var file = unitFile(home, Platform.LAUNCHD);
            unit(Platform.LAUNCHD).write();
            Files.writeString(previous(file), previousUnit);
            Files.writeString(reinstall(file), "");
            var call = call(file);
            var keptAtCommand = new ArrayList<Boolean>();
            ProcessCall observing = command -> {
                keptAtCommand.add(Files.exists(previous(file)) || Files.exists(reinstall(file)));
                return call.run(command);
            };

            installer(unit(Platform.LAUNCHD), observing).uninstall();

            assertAll(
                    () -> assertEquals(List.of(false), keptAtCommand, "the kept pair must be gone at the bootout"),
                    () -> assertFalse(Files.exists(file), "the unit must be removed"),
                    () -> assertFalse(Files.exists(previous(file)), "the kept copy must be dropped"),
                    () -> assertFalse(Files.exists(reinstall(file)), "the reinstall marker must be dropped"),
                    () -> assertUninstalledLogged(unit(Platform.LAUNCHD)));
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
            var call = call(unit.path());

            installer(unit, call).uninstall();

            assertAll(
                    () -> assertEquals(List.of(), calls, "no command must be issued without a unit"),
                    () -> assertFalse(unit.exists(), "no unit must be present afterwards"),
                    () -> assertUninstalledLogged(unit),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.ERROR, ServiceUnitInstaller.class));
        }

        @Test
        @DisplayName("removes the unit, logs and rethrows when unloading fails")
        void shouldRemoveUnitOnUnloadFailure() {
            var unit = unit(Platform.LAUNCHD);
            unit.write();
            var failure = failure();
            var call = call(unit.path(), ProcessCallDoubles.failure(failure));
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall,
                    "a failing unload must fail the uninstall");

            assertAll(
                    () -> assertSame(failure, thrown, "the unload failure must be rethrown"),
                    () -> assertFalse(unit.exists(), "the unit must be removed all the same"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.SERVICE_MANAGER_FAILED.resolveIdentifierString()),
                    () -> assertManagerFailedLogged("unload"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, ServiceUnitInstaller.class));
        }

        @Test
        @DisplayName("removes the unit and still unloads it when disabling fails")
        void shouldRemoveAndUnloadOnDisableFailure() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();
            var exitCode = failingExitCode();
            var call = call(unit.path(), ProcessCallDoubles.answer(exitCode, content()));
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall,
                    "a failing disable must fail the uninstall");

            assertAll(
                    () -> assertTrue(thrown.getMessage().startsWith(
                                    "Command 'systemctl --user disable pm-mcpd.service' exited with " + exitCode),
                            "the failure must name the disable command"),
                    () -> assertFalse(unit.exists(), "the unit must be removed all the same"),
                    () -> assertEquals(List.of(DISABLE, STOP, DAEMON_RELOAD), commands(),
                            "the unit must still be stopped and reloaded"),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            "Service manager command 'disable' failed"),
                    () -> assertManagerFailedLogged("disable"));
        }

        @Test
        @DisplayName("throws the first failure with the later one suppressed when disabling and unloading fail")
        void shouldKeepFirstFailure() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();
            var disableFailure = failure();
            var unloadFailure = failure();
            var call = call(unit.path(), ProcessCallDoubles.failure(disableFailure),
                    ProcessCallDoubles.failure(unloadFailure));
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall,
                    "failing commands must fail the uninstall");

            assertAll(
                    () -> assertSame(disableFailure, thrown, "the disable failure must be thrown first"),
                    () -> assertArrayEquals(new Throwable[]{unloadFailure}, thrown.getSuppressed(),
                            "the unload failure must be suppressed"),
                    () -> assertFalse(unit.exists(), "the unit must be removed all the same"),
                    () -> assertManagerFailedLogged("disable"),
                    () -> assertManagerFailedLogged("unload"));
        }

        @Test
        @DisplayName("does not unload when the unit cannot be removed, so the service keeps its unit")
        void shouldNotUnloadUnremovableUnit() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            blockWithDirectory(unit.path());
            var call = call(unit.path());
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall,
                    "an unremovable unit must fail the uninstall");

            assertAll(
                    // Contract text: the failure says the unit could not be removed
                    () -> assertTrue(thrown.getMessage().contains("could not be removed"),
                            "the failure must say the unit stays"),
                    () -> assertEquals(List.of(DISABLE), commands(), "the unit must not be unloaded"),
                    () -> assertTrue(unit.exists(), "the service must keep its unit"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.SERVICE_UNIT_UNINSTALLED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("suppresses the removal failure under an earlier disable failure")
        void shouldSuppressRemovalFailure() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            blockWithDirectory(unit.path());
            var disableFailure = failure();
            var call = call(unit.path(), ProcessCallDoubles.failure(disableFailure));
            var installer = installer(unit, call);

            var thrown = assertThrows(ServiceUnitException.class, installer::uninstall,
                    "a failing disable must fail the uninstall");

            assertAll(
                    () -> assertSame(disableFailure, thrown, "the disable failure must be thrown first"),
                    () -> assertEquals(1, thrown.getSuppressed().length, "one removal failure must be suppressed"),
                    () -> assertTrue(thrown.getSuppressed()[0].getMessage().contains("could not be removed"),
                            "the suppressed failure is the removal"),
                    () -> assertEquals(List.of(DISABLE), commands(), "the unit must not be unloaded"),
                    () -> assertManagerFailedLogged("disable"));
        }
    }

    @Test
    @DisplayName("refuses a missing unit or service manager")
    void shouldRefuseMissingParts() {
        var unit = unit(Platform.SYSTEMD);
        var manager = ServiceManager.forPlatform(Platform.SYSTEMD, call(null), uid);

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> new ServiceUnitInstaller(null, manager),
                        "a missing unit must be refused"),
                () -> assertThrows(NullPointerException.class, () -> new ServiceUnitInstaller(unit, null),
                        "a missing service manager must be refused"));
    }
}
