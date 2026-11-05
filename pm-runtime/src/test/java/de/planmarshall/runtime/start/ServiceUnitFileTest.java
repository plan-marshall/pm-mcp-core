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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.runtime.start.ServiceUnitFile.Platform;

/**
 * The per-user service unit of the service-managed start mode (PM-ARCH-7): the unit starts the daemon by its
 * absolute path at login, restarts it on failure, and lies where launchd and systemd look for a user's units. The
 * unit file content is the golden file of its platform and the unit paths are the locations launchd and systemd
 * prescribe; the content of a stale file and the names of the configuration directories are generated.
 */
@EnableGeneratorController
@DisplayName("Service unit file")
class ServiceUnitFileTest {

    /** Golden file path: the daemon path the golden unit files were rendered with. */
    private static final Path DAEMON = Path.of("/opt/plan-marshall-mcp/bin/pm-mcpd");
    /** Contract literals: the file names of the units of the two platforms. */
    private static final String LAUNCH_AGENT = "de.planmarshall.mcpd.plist";
    private static final String SYSTEMD_UNIT = "pm-mcpd.service";

    @TempDir
    Path home;

    private ServiceUnitFile unit(Platform platform) {
        return new ServiceUnitFile(platform, home, null, DAEMON);
    }

    private static String golden(String name) throws IOException {
        try (var in = ServiceUnitFileTest.class.getResourceAsStream("/service-unit/" + name)) {
            if (in == null) {
                throw new IOException("Missing golden file " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String content() {
        return Generators.letterStrings(3, 60).next();
    }

    private static String directory() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @Nested
    @DisplayName("render")
    class Render {

        @Test
        @DisplayName("renders the launchd LaunchAgent byte for byte (golden file)")
        void shouldRenderLaunchd() throws Exception {
            var text = unit(Platform.LAUNCHD).render();

            assertEquals(golden(LAUNCH_AGENT), text, "the LaunchAgent must match its golden file");
        }

        @Test
        @DisplayName("renders the systemd user unit byte for byte (golden file)")
        void shouldRenderSystemd() throws Exception {
            var text = unit(Platform.SYSTEMD).render();

            assertEquals(golden(SYSTEMD_UNIT), text, "the systemd unit must match its golden file");
        }

        @Test
        @DisplayName("escapes XML markup in the daemon path of the LaunchAgent (the markup characters are the case)")
        void shouldEscapeXml() {
            var unit = new ServiceUnitFile(Platform.LAUNCHD, home, null, Path.of("/opt/a&b<c>/pm-mcpd"));

            var text = unit.render();

            assertTrue(text.contains("<string>/opt/a&amp;b&lt;c&gt;/pm-mcpd</string>"),
                    "the markup of the daemon path must be escaped");
        }

        @Test
        @DisplayName("doubles the specifier character in ExecStart (the specifier character is the case)")
        void shouldEscapeSpecifier() {
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, null, Path.of("/opt/100%/pm-mcpd"));

            var text = unit.render();

            assertTrue(text.contains("ExecStart=/opt/100%%/pm-mcpd\n"), "the specifier character must be doubled");
        }

        @Test
        @DisplayName("quotes a daemon path with whitespace, quotes or backslashes in ExecStart (the characters are the case)")
        void shouldQuoteExecStart() {
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, null, Path.of("/opt/my \"pm\\mcp/pm-mcpd"));

            var text = unit.render();

            assertTrue(text.contains("ExecStart=\"/opt/my \\\"pm\\\\mcp/pm-mcpd\"\n"),
                    "the daemon path must be quoted in ExecStart");
        }
    }

    @Nested
    @DisplayName("path")
    class Location {

        @Test
        @DisplayName("places the LaunchAgent under Library/LaunchAgents of the home")
        void shouldLocateLaunchAgent() {
            var path = unit(Platform.LAUNCHD).path();

            assertEquals(home.resolve("Library/LaunchAgents/" + LAUNCH_AGENT), path,
                    "the LaunchAgent must lie in Library/LaunchAgents");
        }

        @Test
        @DisplayName("places the systemd unit under .config of the home without XDG_CONFIG_HOME")
        void shouldLocateSystemdDefault() {
            var path = unit(Platform.SYSTEMD).path();

            assertEquals(home.resolve(".config/systemd/user/" + SYSTEMD_UNIT), path,
                    "the systemd unit must lie below .config");
        }

        @Test
        @DisplayName("places the systemd unit under XDG_CONFIG_HOME when it is set")
        void shouldLocateSystemdUnderXdg() {
            var config = home.resolve(directory());
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, config.toString(), DAEMON);

            var path = unit.path();

            assertEquals(config.resolve("systemd/user/" + SYSTEMD_UNIT), path,
                    "the systemd unit must lie below XDG_CONFIG_HOME");
        }

        @ParameterizedTest(name = "XDG_CONFIG_HOME ''{0}''")
        @ValueSource(strings = {"", "  ", "relative/config"})
        @DisplayName("ignores a blank or relative XDG_CONFIG_HOME (the unusable forms are the cases)")
        void shouldIgnoreUnusableXdg(String xdgConfigHome) {
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, xdgConfigHome, DAEMON);

            var path = unit.path();

            assertEquals(home.resolve(".config/systemd/user/" + SYSTEMD_UNIT), path,
                    "an unusable XDG_CONFIG_HOME must be ignored");
        }

        @Test
        @DisplayName("rejects a relative daemon path")
        void shouldRejectRelativeDaemon() {
            var relative = Path.of(directory(), "pm-mcpd");

            assertThrows(IllegalArgumentException.class,
                    () -> new ServiceUnitFile(Platform.LAUNCHD, home, null, relative),
                    "a relative daemon path must be rejected");
        }

        /**
         * A line break in the daemon path would end the {@code ExecStart} line, and systemd would read the text after
         * it as a directive of its own; the constructor rejects it, so no such unit is ever written. The default file
         * system refuses a NUL character in a path itself, so the NUL branch of the guard has no reachable fixture
         * here.
         */
        @ParameterizedTest(name = "line break {index}")
        @ValueSource(strings = {"\n", "\r", "\r\n"})
        @DisplayName("rejects a daemon path with a line break without echoing it (the line breaks are the cases)")
        void shouldRejectLineBreakInDaemon(String lineBreak) {
            var daemon = Path.of("/opt/pm" + lineBreak + "ExecStartPre=/bin/" + directory() + "/pm-mcpd");

            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> new ServiceUnitFile(Platform.SYSTEMD, home, null, daemon),
                    "a daemon path with a line break must be rejected");

            assertFalse(thrown.getMessage().contains(lineBreak), "the rejection must not echo the line break");
        }
    }

    @Nested
    @DisplayName("write and remove")
    class WriteAndRemove {

        @Test
        @DisplayName("creates the parent directories and writes the rendered unit")
        void shouldWriteUnit() throws Exception {
            var unit = unit(Platform.SYSTEMD);

            unit.write();

            assertAll(
                    () -> assertTrue(unit.exists(), "the written unit must exist"),
                    () -> assertEquals(unit.render(), Files.readString(unit.path()),
                            "the written unit must be the rendered one"),
                    () -> assertFalse(Files.exists(unit.path().resolveSibling(SYSTEMD_UNIT + ".tmp")),
                            "no temporary file must be left behind"));
        }

        @Test
        @DisplayName("replaces an existing unit on a second write")
        void shouldReplaceUnit() throws Exception {
            var unit = unit(Platform.LAUNCHD);
            Files.createDirectories(unit.path().getParent());
            Files.writeString(unit.path(), content());

            unit.write();

            assertEquals(golden(LAUNCH_AGENT), Files.readString(unit.path()), "the stale unit must be replaced");
        }

        @Test
        @DisplayName("reports a removed unit as present")
        void shouldRemovePresentUnit() {
            var unit = unit(Platform.LAUNCHD);
            unit.write();

            var removed = unit.remove();

            assertAll(
                    () -> assertTrue(removed, "a present unit must be reported as removed"),
                    () -> assertFalse(unit.exists(), "the removed unit must no longer exist"));
        }

        @Test
        @DisplayName("reports an absent unit without throwing")
        void shouldRemoveAbsentUnit() {
            var unit = unit(Platform.SYSTEMD);

            var removed = unit.remove();

            assertFalse(removed, "an absent unit must be reported as not removed");
        }

        @Test
        @DisplayName("wraps a write failure into a service unit exception")
        void shouldWrapWriteFailure() throws Exception {
            Files.writeString(home.resolve("Library"), content());
            var unit = unit(Platform.LAUNCHD);

            var thrown = assertThrows(ServiceUnitException.class, unit::write, "a failed write must be wrapped");

            assertTrue(thrown.getMessage().contains(unit.path().toString()), "the failure must name the unit path");
        }

        @Test
        @DisplayName("wraps a remove failure into a service unit exception")
        void shouldWrapRemoveFailure() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            Files.createDirectories(unit.path());
            Files.writeString(unit.path().resolve(directory()), content());

            var thrown = assertThrows(ServiceUnitException.class, unit::remove, "a failed remove must be wrapped");

            assertTrue(thrown.getMessage().contains(unit.path().toString()), "the failure must name the unit path");
        }
    }

    /**
     * Who may modify the unit must not depend on the process umask: the unit and the directories created for it are
     * never group- or world-writable, even under a home whose permissions would allow it.
     */
    @Nested
    @EnabledOnOs({OS.MAC, OS.LINUX})
    @DisplayName("permissions")
    class Permissions {

        private static void assertNotGroupOrWorldWritable(Path path) throws IOException {
            var permissions = Files.getPosixFilePermissions(path);

            assertAll(path.toString(),
                    () -> assertFalse(permissions.contains(PosixFilePermission.GROUP_WRITE),
                            "the path must not be group-writable"),
                    () -> assertFalse(permissions.contains(PosixFilePermission.OTHERS_WRITE),
                            "the path must not be world-writable"));
        }

        @Test
        @DisplayName("writes the unit and its new parent directories without group or world write permission")
        void shouldWriteOwnerOnlyWritable() throws Exception {
            Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwxrwxrwx"));
            var unit = unit(Platform.LAUNCHD);

            unit.write();

            assertNotGroupOrWorldWritable(unit.path());
            assertNotGroupOrWorldWritable(home.resolve("Library/LaunchAgents"));
            assertNotGroupOrWorldWritable(home.resolve("Library"));
        }

        @Test
        @DisplayName("replaces a world-writable stale temporary file instead of reusing it")
        void shouldNotReuseStaleTemporaryFile() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            var stale = unit.path().resolveSibling(SYSTEMD_UNIT + ".tmp");
            Files.createDirectories(stale.getParent());
            Files.writeString(stale, content());
            Files.setPosixFilePermissions(stale, PosixFilePermissions.fromString("rw-rw-rw-"));

            unit.write();

            assertAll(
                    () -> assertEquals(unit.render(), Files.readString(unit.path()),
                            "the written unit must be the rendered one"),
                    () -> assertFalse(Files.exists(stale), "the stale temporary file must be gone"),
                    () -> assertNotGroupOrWorldWritable(unit.path()));
        }

        @Test
        @DisplayName("narrows the permissions of a world-writable unit it replaces")
        void shouldNarrowReplacedUnit() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            Files.createDirectories(unit.path().getParent());
            Files.writeString(unit.path(), content());
            Files.setPosixFilePermissions(unit.path(), PosixFilePermissions.fromString("rw-rw-rw-"));

            unit.write();

            assertNotGroupOrWorldWritable(unit.path());
        }

        @Test
        @DisplayName("writes the unit with mode 0600 and its new parent directories with mode 0700")
        void shouldWriteOwnerOnlyModes() throws Exception {
            Files.setPosixFilePermissions(home, PosixFilePermissions.fromString("rwxrwxrwx"));
            var unit = unit(Platform.SYSTEMD);

            unit.write();

            assertAll(
                    () -> assertEquals(PosixModes.FILE, Files.getPosixFilePermissions(unit.path()),
                            "the unit must be written with mode 0600"),
                    () -> assertEquals(PosixModes.DIRECTORY, Files.getPosixFilePermissions(unit.path().getParent()),
                            "systemd/user must be created with mode 0700"),
                    () -> assertEquals(PosixModes.DIRECTORY,
                            Files.getPosixFilePermissions(home.resolve(".config/systemd")),
                            ".config/systemd must be created with mode 0700"),
                    () -> assertEquals(PosixModes.DIRECTORY, Files.getPosixFilePermissions(home.resolve(".config")),
                            ".config must be created with mode 0700"));
        }

        @Test
        @DisplayName("narrows a world-readable unit it replaces to mode 0600")
        void shouldNarrowReplacedUnitToOwnerOnly() throws Exception {
            var unit = unit(Platform.LAUNCHD);
            Files.createDirectories(unit.path().getParent());
            Files.writeString(unit.path(), content());
            Files.setPosixFilePermissions(unit.path(), PosixFilePermissions.fromString("rw-r--r--"));

            unit.write();

            assertEquals(PosixModes.FILE, Files.getPosixFilePermissions(unit.path()),
                    "the replaced unit must be narrowed to 0600");
        }
    }

    @Nested
    @DisplayName("exists")
    class Exists {

        @Test
        @DisplayName("is true for a present unit")
        void shouldBeTrueForPresentUnit() {
            var unit = unit(Platform.SYSTEMD);
            unit.write();

            assertTrue(unit.exists(), "a written unit must count as present");
        }

        @Test
        @DisplayName("is false for an absent unit")
        void shouldBeFalseForAbsentUnit() {
            var unit = unit(Platform.SYSTEMD);

            assertFalse(unit.exists(), "a unit never written must count as absent");
        }

        /**
         * When the existence of the unit cannot be determined, an uninstall must not take the unit for absent and
         * report success; the unit counts as present, so the removal is tried and its failure reported. A parent
         * directory without search permission makes the existence undeterminable, except for root, who is not
         * subject to it.
         */
        @Test
        @EnabledOnOs({OS.MAC, OS.LINUX})
        @DisplayName("is true when the presence of the unit cannot be determined")
        void shouldBeTrueForUndeterminableUnit() throws Exception {
            assumeFalse("root".equals(System.getProperty("user.name")), "root may search any directory");
            var unit = unit(Platform.LAUNCHD);
            unit.write();
            var agents = unit.path().getParent();
            Files.setPosixFilePermissions(agents, PosixFilePermissions.fromString("rw-------"));
            try {
                assertTrue(unit.exists(), "an undeterminable unit must count as present");
            } finally {
                Files.setPosixFilePermissions(agents, PosixFilePermissions.fromString("rwx------"));
            }
        }
    }

    @Nested
    @DisplayName("platform")
    class PlatformOfOs {

        @ParameterizedTest(name = "''{0}'' is {1}")
        @CsvSource({"Mac OS X, LAUNCHD", "Linux, SYSTEMD", "linux, SYSTEMD"})
        @DisplayName("maps the OS name to its service manager (the os.name values are the cases)")
        void shouldMapOsName(String osName, Platform expected) {
            assertEquals(expected, Platform.of(osName), "the OS name must map to its service manager");
        }

        @Test
        @DisplayName("refuses an OS without a supported per-user service manager")
        void shouldRefuseUnsupportedOs() {
            // Contract prefix: the os.name of Windows, which has no supported per-user service manager
            var osName = "Windows " + Generators.integers(7, 20).next();

            assertThrows(ServiceUnitException.class, () -> Platform.of(osName), "an unsupported OS must be refused");
        }

        @Test
        @EnabledOnOs(OS.MAC)
        @DisplayName("is launchd on macOS")
        void shouldBeLaunchdOnMac() {
            assertEquals(Platform.LAUNCHD, Platform.current(), "macOS must use launchd");
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("is systemd on Linux")
        void shouldBeSystemdOnLinux() {
            assertEquals(Platform.SYSTEMD, Platform.current(), "Linux must use systemd");
        }
    }
}
