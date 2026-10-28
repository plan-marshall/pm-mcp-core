/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * The owner check of the listening socket (PM-SEC-5, Client Profiles § Enrolment): the lookup reads the program that
 * accepted the port, and a port nobody listens on, or a platform without such a table, refuses instead of passing.
 */
@EnabledOnOs(OS.LINUX)
@DisplayName("Socket owner")
class SocketOwnerTest {

    @Test
    @DisplayName("the owner of a listening socket is the executable of the process that accepted it")
    void ownerOfListeningSocket() throws Exception {
        var owner = SocketOwner.ofThisMachine();
        try (var socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            var lookup = owner.ownerOf("127.0.0.1", socket.getLocalPort());

            assertAll(
                    () -> assertTrue(lookup.isFound(), () -> tables(socket.getLocalPort()) + " — " + lookup.detail()),
                    () -> assertTrue(Path.of(lookup.executablePath()).isAbsolute()),
                    () -> assertTrue(Files.isRegularFile(Path.of(lookup.executablePath()))),
                    () -> assertTrue(lookup.runs(lookup.executablePath())),
                    () -> assertFalse(lookup.runs("/nonexistent/program")));
        }
    }

    private static String tables(int port) {
        var wanted = ":%04X".formatted(port);
        var found = new StringBuilder();
        for (var table : List.of("/proc/net/tcp", "/proc/net/tcp6")) {
            try {
                Files.readAllLines(Path.of(table)).stream().filter(line -> line.contains(wanted))
                        .forEach(line -> found.append(table).append(": ").append(line).append('\n'));
            } catch (IOException e) {
                found.append(table).append(": ").append(e.getMessage()).append('\n');
            }
        }
        return found.isEmpty() ? "no entry for " + wanted + " in either table" : found.toString();
    }

    @Test
    @DisplayName("a port nobody listens on has no owner and refuses")
    void noListener() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }

        var lookup = SocketOwner.ofThisMachine().ownerOf("127.0.0.1", port);

        assertAll(
                () -> assertFalse(lookup.isFound()),
                () -> assertFalse(lookup.runs("/nonexistent/program")),
                () -> assertTrue(lookup.detail().contains("no program listens")));
    }

    @Test
    @DisplayName("a lookup that found nothing never runs an enrolled program")
    void notFoundNeverRuns() {
        assertFalse(SocketOwner.Lookup.notFound("the platform keeps no socket table").runs(""));
    }
}
