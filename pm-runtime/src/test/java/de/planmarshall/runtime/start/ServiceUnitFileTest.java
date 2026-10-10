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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;


import de.planmarshall.runtime.start.ServiceUnitFile.Platform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The per-user service unit of the service-managed start mode (PM-ARCH-7): the unit starts the daemon by its
 * absolute path at login, restarts it on failure, and lies where launchd and systemd look for a user's units.
 */
@DisplayName("Service unit file")
class ServiceUnitFileTest {

    private static final Path DAEMON = Path.of("/opt/plan-marshall-mcp/bin/pm-mcpd");

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

    @Nested
    @DisplayName("render")
    class Render {

        @Test
        @DisplayName("renders the launchd LaunchAgent byte for byte")
        void shouldRenderLaunchd() throws Exception {
            var text = unit(Platform.LAUNCHD).render();

            assertEquals(golden("de.planmarshall.mcpd.plist"), text);
        }

        @Test
        @DisplayName("renders the systemd user unit byte for byte")
        void shouldRenderSystemd() throws Exception {
            var text = unit(Platform.SYSTEMD).render();

            assertEquals(golden("pm-mcpd.service"), text);
        }

        @Test
        @DisplayName("escapes XML markup in the daemon path of the LaunchAgent")
        void shouldEscapeXml() {
            var unit = new ServiceUnitFile(Platform.LAUNCHD, home, null, Path.of("/opt/a&b<c>/pm-mcpd"));

            var text = unit.render();

            assertTrue(text.contains("<string>/opt/a&amp;b&lt;c&gt;/pm-mcpd</string>"), text);
        }

        @Test
        @DisplayName("doubles the specifier character in ExecStart")
        void shouldEscapeSpecifier() {
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, null, Path.of("/opt/100%/pm-mcpd"));

            var text = unit.render();

            assertTrue(text.contains("ExecStart=/opt/100%%/pm-mcpd\n"), text);
        }

        @Test
        @DisplayName("quotes a daemon path with whitespace, quotes or backslashes in ExecStart")
        void shouldQuoteExecStart() {
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, null, Path.of("/opt/my \"pm\\mcp/pm-mcpd"));

            var text = unit.render();

            assertTrue(text.contains("ExecStart=\"/opt/my \\\"pm\\\\mcp/pm-mcpd\"\n"), text);
        }
    }

    @Nested
    @DisplayName("path")
    class Location {

        @Test
        @DisplayName("places the LaunchAgent under Library/LaunchAgents of the home")
        void shouldLocateLaunchAgent() {
            var path = unit(Platform.LAUNCHD).path();

            assertEquals(home.resolve("Library/LaunchAgents/de.planmarshall.mcpd.plist"), path);
        }

        @Test
        @DisplayName("places the systemd unit under .config of the home without XDG_CONFIG_HOME")
        void shouldLocateSystemdDefault() {
            var path = unit(Platform.SYSTEMD).path();

            assertEquals(home.resolve(".config/systemd/user/pm-mcpd.service"), path);
        }

        @Test
        @DisplayName("places the systemd unit under XDG_CONFIG_HOME when it is set")
        void shouldLocateSystemdUnderXdg() {
            var config = home.resolve("xdg");
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, config.toString(), DAEMON);

            var path = unit.path();

            assertEquals(config.resolve("systemd/user/pm-mcpd.service"), path);
        }

        @ParameterizedTest(name = "XDG_CONFIG_HOME ''{0}''")
        @ValueSource(strings = {"", "  ", "relative/config"})
        @DisplayName("ignores a blank or relative XDG_CONFIG_HOME")
        void shouldIgnoreUnusableXdg(String xdgConfigHome) {
            var unit = new ServiceUnitFile(Platform.SYSTEMD, home, xdgConfigHome, DAEMON);

            var path = unit.path();

            assertEquals(home.resolve(".config/systemd/user/pm-mcpd.service"), path);
        }

        @Test
        @DisplayName("rejects a relative daemon path")
        void shouldRejectRelativeDaemon() {
            var relative = Path.of("bin/pm-mcpd");

            assertThrows(IllegalArgumentException.class,
                    () -> new ServiceUnitFile(Platform.LAUNCHD, home, null, relative));
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

            assertTrue(unit.exists());
            assertEquals(unit.render(), Files.readString(unit.path()));
            assertFalse(Files.exists(unit.path().resolveSibling("pm-mcpd.service.tmp")));
        }

        @Test
        @DisplayName("replaces an existing unit on a second write")
        void shouldReplaceUnit() throws Exception {
            var unit = unit(Platform.LAUNCHD);
            Files.createDirectories(unit.path().getParent());
            Files.writeString(unit.path(), "stale");

            unit.write();

            assertEquals(golden("de.planmarshall.mcpd.plist"), Files.readString(unit.path()));
        }

        @Test
        @DisplayName("reports a removed unit as present")
        void shouldRemovePresentUnit() {
            var unit = unit(Platform.LAUNCHD);
            unit.write();

            var removed = unit.remove();

            assertTrue(removed);
            assertFalse(unit.exists());
        }

        @Test
        @DisplayName("reports an absent unit without throwing")
        void shouldRemoveAbsentUnit() {
            var unit = unit(Platform.SYSTEMD);

            var removed = unit.remove();

            assertFalse(removed);
        }

        @Test
        @DisplayName("wraps a write failure into a service unit exception")
        void shouldWrapWriteFailure() throws Exception {
            Files.writeString(home.resolve("Library"), "a file where a directory belongs");
            var unit = unit(Platform.LAUNCHD);

            var thrown = assertThrows(ServiceUnitException.class, unit::write);

            assertTrue(thrown.getMessage().contains(unit.path().toString()), thrown.getMessage());
        }

        @Test
        @DisplayName("wraps a remove failure into a service unit exception")
        void shouldWrapRemoveFailure() throws Exception {
            var unit = unit(Platform.SYSTEMD);
            Files.createDirectories(unit.path());
            Files.writeString(unit.path().resolve("child"), "keeps the directory non-empty");

            var thrown = assertThrows(ServiceUnitException.class, unit::remove);

            assertTrue(thrown.getMessage().contains(unit.path().toString()), thrown.getMessage());
        }
    }

    @Nested
    @DisplayName("platform")
    class PlatformOfOs {

        @ParameterizedTest(name = "''{0}'' is {1}")
        @CsvSource({"Mac OS X, LAUNCHD", "Linux, SYSTEMD", "linux, SYSTEMD"})
        @DisplayName("maps the OS name to its service manager")
        void shouldMapOsName(String osName, Platform expected) {
            assertEquals(expected, Platform.of(osName));
        }

        @Test
        @DisplayName("refuses an OS without a supported per-user service manager")
        void shouldRefuseUnsupportedOs() {
            assertThrows(ServiceUnitException.class, () -> Platform.of("Windows 11"));
        }

        @Test
        @EnabledOnOs(OS.MAC)
        @DisplayName("is launchd on macOS")
        void shouldBeLaunchdOnMac() {
            assertEquals(Platform.LAUNCHD, Platform.current());
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("is systemd on Linux")
        void shouldBeSystemdOnLinux() {
            assertEquals(Platform.SYSTEMD, Platform.current());
        }
    }
}
