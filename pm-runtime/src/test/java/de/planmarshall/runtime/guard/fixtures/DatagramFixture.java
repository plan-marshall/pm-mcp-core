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

import java.net.DatagramSocket;
import java.net.SocketException;

/**
 * Control of the outbound-calls guard: a class opening a datagram socket, on which rule A must fail.
 */
public final class DatagramFixture {

    private DatagramFixture() {
    }

    /**
     * @throws SocketException if the socket cannot be opened
     */
    public static void open() throws SocketException {
        try (var socket = new DatagramSocket()) {
            socket.isBound();
        }
    }
}
