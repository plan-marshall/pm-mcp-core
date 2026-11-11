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
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SocketChannel;

/**
 * Control of the outbound-calls guard: a class opening each client channel of NIO, none of which lives in
 * {@code java.net}, on which rule A must fail once per channel type.
 */
public final class ChannelFixture {

    private ChannelFixture() {
    }

    /**
     * @throws IOException if a channel cannot be opened
     */
    public static void open() throws IOException {
        try (var socket = SocketChannel.open(); var datagram = DatagramChannel.open();
             var asyncSocket = AsynchronousSocketChannel.open()) {
            socket.configureBlocking(false);
            datagram.configureBlocking(false);
            asyncSocket.isOpen();
        }
    }
}
