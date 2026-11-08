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

import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The readiness check of the endpoint harness: an endpoint that does not answer logs {@code ENDPOINT_NOT_READY} and
 * reports itself not ready.
 * <p>
 * Every datum of this test is a contract: the loopback origin, a port the test bound and closed again, and the
 * readiness path of the fixture profile.
 */
@EnableTestLogger
@DisplayName("Endpoint harness readiness")
class EndpointHarnessReadinessTest {

    @Test
    @DisplayName("an endpoint that does not answer is not ready and logs the record")
    void notReadyEndpointLogs() throws Exception {
        var profile = EndpointFixtures.profile("http://127.0.0.1:" + closedPort());
        var harness = new EndpointHarness(new EndpointClient(), SocketOwner.ofThisMachine());

        var ready = harness.ready(profile);

        assertFalse(ready, "a closed port answers no readiness check");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.ENDPOINT_NOT_READY.resolveIdentifierString());
    }

    private static int closedPort() throws IOException {
        try (var socket = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return socket.getLocalPort();
        }
    }
}
