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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Control of the outbound-calls guard: a class constructing a client socket and connecting it, on which rule A
 * must fail.
 */
public final class ClientSocketFixture {

    private ClientSocketFixture() {
    }

    /**
     * @throws IOException if the connection fails
     */
    public static void connect() throws IOException {
        try (var socket = new Socket()) {
            socket.connect(new InetSocketAddress("192.0.2.1", 443), 1000);
        }
    }
}
