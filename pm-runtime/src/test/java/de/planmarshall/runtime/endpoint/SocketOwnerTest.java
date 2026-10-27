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

import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
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
                    () -> assertTrue(lookup.isFound(), lookup.detail()),
                    () -> assertTrue(Path.of(lookup.executablePath()).isAbsolute()),
                    () -> assertTrue(Files.isRegularFile(Path.of(lookup.executablePath()))),
                    () -> assertTrue(lookup.runs(lookup.executablePath())),
                    () -> assertFalse(lookup.runs("/nonexistent/program")));
        }
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
