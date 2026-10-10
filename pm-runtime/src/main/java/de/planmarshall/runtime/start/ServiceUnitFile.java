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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * The unit file of the per-user service that starts {@code pm-mcpd} at login and restarts it on failure, the
 * service-managed start mode of PM-ARCH-7.
 * <p>
 * On macOS the unit is the {@code launchd} LaunchAgent {@code ~/Library/LaunchAgents/de.planmarshall.mcpd.plist},
 * on Linux the {@code systemd --user} unit {@code $XDG_CONFIG_HOME/systemd/user/pm-mcpd.service} (default
 * {@code ~/.config/systemd/user/}). The home directory and the value of {@code XDG_CONFIG_HOME} are inputs, never
 * read from the environment here, so the unit can be rendered and written for any home. The unit names the
 * daemon by its absolute path, never through {@code PATH}.
 *
 * @param platform      the service manager the unit is for
 * @param home          the user's home directory
 * @param xdgConfigHome the value of {@code XDG_CONFIG_HOME}, or {@code null} when it is not set; a blank or
 *                      relative value is ignored, as the XDG Base Directory Specification requires
 * @param daemon        the absolute path of {@code <PM_MCP_HOME>/bin/pm-mcpd}
 * @since 0.1
 */
public record ServiceUnitFile(Platform platform, Path home, String xdgConfigHome, Path daemon) {

    /** The {@code launchd} job label, also the base name of the LaunchAgent file. */
    public static final String LAUNCHD_LABEL = "de.planmarshall.mcpd";

    /** The name of the {@code systemd} user unit. */
    public static final String SYSTEMD_UNIT = "pm-mcpd.service";

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwxr-xr-x");

    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-r--r--");

    private static final String LAUNCHD_TEMPLATE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
            <plist version="1.0">
            <dict>
                <key>Label</key>
                <string>%s</string>
                <key>ProgramArguments</key>
                <array>
                    <string>%s</string>
                </array>
                <key>RunAtLoad</key>
                <true/>
                <key>KeepAlive</key>
                <dict>
                    <key>SuccessfulExit</key>
                    <false/>
                </dict>
            </dict>
            </plist>
            """;

    private static final String SYSTEMD_TEMPLATE = """
            [Unit]
            Description=plan-marshall-mcp runtime (pm-mcpd)

            [Service]
            ExecStart=%s
            Restart=on-failure

            [Install]
            WantedBy=default.target
            """;

    /** The per-user service manager of a platform. */
    public enum Platform {
        /** {@code launchd} on macOS. */
        LAUNCHD,
        /** {@code systemd --user} on Linux. */
        SYSTEMD;

        /**
         * @return the service manager of the running OS
         * @throws ServiceUnitException if the OS has no supported per-user service manager
         */
        public static Platform current() {
            return of(System.getProperty("os.name", ""));
        }

        /**
         * @param osName the value of the system property {@code os.name}
         * @return the service manager of that OS
         * @throws ServiceUnitException if the OS has no supported per-user service manager
         */
        static Platform of(String osName) {
            var name = osName.toLowerCase(Locale.ROOT);
            if (name.startsWith("mac")) {
                return LAUNCHD;
            }
            if (name.startsWith("linux")) {
                return SYSTEMD;
            }
            throw new ServiceUnitException("No per-user service manager is supported on '" + osName + "'");
        }
    }

    /**
     * Validates the inputs. A daemon path with a line break or a NUL character is rejected because no quoting keeps
     * it inside the {@code ExecStart} line or the {@code <string>} element: the text after a line break would be
     * read as a directive of its own, so such a path never reaches a written unit.
     *
     * @throws IllegalArgumentException if {@code daemon} is not absolute or contains a carriage return, a line feed
     *                                  or a NUL character
     */
    public ServiceUnitFile {
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(home, "home");
        Objects.requireNonNull(daemon, "daemon");
        if (daemon.toString().chars().anyMatch(ServiceUnitFile::isForbiddenInDaemonPath)) {
            throw new IllegalArgumentException(
                    "The daemon path must not contain a carriage return, a line feed or a NUL character");
        }
        if (!daemon.isAbsolute()) {
            throw new IllegalArgumentException("The daemon path must be absolute: " + daemon);
        }
    }

    /**
     * @return the location of the unit file
     */
    public Path path() {
        return switch (platform) {
            case LAUNCHD -> home.resolve("Library").resolve("LaunchAgents").resolve(LAUNCHD_LABEL + ".plist");
            case SYSTEMD -> configHome().resolve("systemd").resolve("user").resolve(SYSTEMD_UNIT);
        };
    }

    /**
     * @return the text of the unit, the same for the same inputs
     */
    public String render() {
        var program = daemon.toString();
        return switch (platform) {
            case LAUNCHD -> LAUNCHD_TEMPLATE.formatted(LAUNCHD_LABEL, escapeXml(program));
            case SYSTEMD -> SYSTEMD_TEMPLATE.formatted(escapeSystemd(program));
        };
    }

    /**
     * Writes the unit, creating its parent directories and replacing an existing unit through a temporary sibling
     * and an atomic move.
     * <p>
     * Who may modify the unit does not depend on the process umask: parent directories created here get
     * {@code rwxr-xr-x} and the unit {@code rw-r--r--}, set when they are created. A umask can only narrow these
     * permissions, never widen them, so the unit and the directories created for it are never group- or
     * world-writable. A stale temporary sibling of an interrupted write is deleted first; the move keeps the
     * permissions of the temporary file, so a replaced unit gets them too.
     *
     * @throws ServiceUnitException if the unit cannot be written
     */
    public void write() {
        var file = path();
        var tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent(), PosixFilePermissions.asFileAttribute(DIRECTORY_PERMISSIONS));
            Files.deleteIfExists(tmp);
            Files.createFile(tmp, PosixFilePermissions.asFileAttribute(FILE_PERMISSIONS));
            Files.writeString(tmp, render(), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ServiceUnitException("Service unit '" + file + "' could not be written", e);
        }
    }

    /**
     * Answers whether the unit may be present. Only a unit positively known to be absent counts as absent: when
     * its existence cannot be determined, for example because a parent directory denies access, the unit counts
     * as present, so an uninstall goes on and {@link #remove()} reports the failure instead of skipping it.
     *
     * @return {@code false} if the unit file is known to be absent, {@code true} otherwise
     */
    public boolean exists() {
        return !Files.notExists(path());
    }

    /**
     * Deletes the unit file if it is present.
     *
     * @return {@code true} if a unit file was deleted, {@code false} if there was none
     * @throws ServiceUnitException if the unit cannot be deleted
     */
    public boolean remove() {
        var file = path();
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new ServiceUnitException("Service unit '" + file + "' could not be removed", e);
        }
    }

    private Path configHome() {
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            var configured = Path.of(xdgConfigHome);
            if (configured.isAbsolute()) {
                return configured;
            }
        }
        return home.resolve(".config");
    }

    private static boolean isForbiddenInDaemonPath(int c) {
        return c == '\r' || c == '\n' || c == '\0';
    }

    private static String escapeXml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Escapes a path for {@code ExecStart}: {@code %} starts a specifier and is doubled; a path with whitespace,
     * a quote or a backslash is quoted.
     */
    private static String escapeSystemd(String value) {
        var escaped = value.replace("%", "%%");
        if (escaped.chars().anyMatch(c -> Character.isWhitespace(c) || c == '"' || c == '\\')) {
            return '"' + escaped.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
        }
        return escaped;
    }
}
