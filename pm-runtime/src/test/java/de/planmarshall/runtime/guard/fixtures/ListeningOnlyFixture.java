/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.guard.fixtures;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;

/**
 * Positive control of the outbound-calls guard: a class that listens only, on a server socket and on an HTTP server
 * bound to a loopback address. A listening socket accepts connections and opens none, so rule A must hold on it.
 */
public final class ListeningOnlyFixture {

    private ListeningOnlyFixture() {
    }

    /**
     * @throws IOException if a listener cannot be bound
     */
    public static void listen() throws IOException {
        var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        try (var server = new ServerSocket()) {
            server.bind(address, 1);
            server.getLocalPort();
        }
        var http = HttpServer.create(address, 0);
        http.stop(0);
    }
}
