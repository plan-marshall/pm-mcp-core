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
import java.net.Socket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Control of the outbound-calls guard: a class taking a socket from the default SSL socket factory, on which
 * rule A must fail.
 */
public final class SocketFactoryFixture {

    private SocketFactoryFixture() {
    }

    /**
     * @throws IOException if the socket cannot be created
     */
    public static Socket socket(String host, int port) throws IOException {
        return SSLSocketFactory.getDefault().createSocket(host, port);
    }
}
