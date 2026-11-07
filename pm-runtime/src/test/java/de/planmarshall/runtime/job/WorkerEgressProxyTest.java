/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The worker egress proxy (PM-SEC-5): a tunnel to a destination of {@code headless.endpoints} passes, every other
 * destination is refused with a {@code 403}, an activity entry and an {@code egress_refused} record of the worker
 * generation.
 * <p>
 * Contract literals that stay: the HTTP {@code CONNECT} exchange with its status lines and the loopback address. The
 * generation id, the endpoint hosts and ports and the tunnelled bytes are generated.
 */
@EnableGeneratorController
@DisplayName("Worker egress proxy")
class WorkerEgressProxyTest {

    private final List<String> activity = new CopyOnWriteArrayList<>();
    private final List<AuditEvent> audit = new CopyOnWriteArrayList<>();

    private String generation;

    @BeforeEach
    void drawGeneration() {
        generation = "l-" + RuntimeGenerators.sha256Digests().next().substring(0, 16);
    }

    /** @return a generated endpoint {@code host:port} in lower case below the reserved domain {@code test} */
    private static String endpoint() {
        return Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT) + ".test:"
                + Generators.integers(1, 65535).next();
    }

    @Test
    @DisplayName("a tunnel to an admitted destination passes and carries its bytes")
    void admittedTunnel() throws Exception {
        var payload = Generators.letterStrings(1, 32).next();
        try (var echo = echoServer();
             var proxy = WorkerEgressProxy.start(generation, List.of("127.0.0.1:" + echo.getLocalPort()),
                     activity::add, audit::add);
             var client = new Socket("127.0.0.1", proxy.port())) {
            var status = connect(client.getInputStream(), client, "127.0.0.1:" + echo.getLocalPort());
            client.getOutputStream().write(payload.getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();
            var echoed = client.getInputStream().readNBytes(payload.length());

            assertAll(
                    () -> assertEquals("HTTP/1.1 200 Connection Established", status,
                            "the admitted tunnel is established"),
                    () -> assertEquals(payload, new String(echoed, StandardCharsets.US_ASCII),
                            "the tunnel carries the bytes both ways"),
                    () -> assertEquals(0, proxy.refusedCount(), "the proxy refused no connection"),
                    () -> assertTrue(audit.isEmpty(), "an admitted tunnel is not audited"));
        }
    }

    @Test
    @DisplayName("a destination outside the endpoints is refused, recorded as activity and audited")
    void refusedDestination() throws Exception {
        try (var echo = echoServer();
             var proxy = WorkerEgressProxy.start(generation, List.of(endpoint()), activity::add, audit::add);
             var client = new Socket("127.0.0.1", proxy.port())) {
            var destination = "127.0.0.1:" + echo.getLocalPort();

            var status = connect(client.getInputStream(), client, destination);

            assertAll(
                    () -> assertEquals("HTTP/1.1 403 Forbidden", status, "the destination is forbidden"),
                    () -> assertEquals(1, proxy.refusedCount(), "one refusal is counted"),
                    () -> assertEquals(List.of(destination), activity, "the refusal is recorded as activity"),
                    () -> assertEquals(1, audit.size(), "the refusal is audited once"),
                    () -> assertEquals(AuditEvent.EGRESS_REFUSED, audit.getFirst().event(),
                            "the audit event is egress_refused"),
                    () -> assertEquals(generation, audit.getFirst().details().get(AuditEvent.DETAIL_GENERATION_ID),
                            "the audit names the worker generation"),
                    () -> assertEquals(destination, audit.getFirst().details().get(AuditEvent.DETAIL_DESTINATION),
                            "the audit names the destination"));
        }
    }

    @Test
    @DisplayName("advertises a loopback proxy URL and the admitted endpoints")
    void url() throws Exception {
        var admitted = endpoint();
        try (var proxy = WorkerEgressProxy.start(generation, List.of(admitted.toUpperCase(Locale.ROOT)),
                activity::add, audit::add)) {
            assertAll(
                    () -> assertTrue(proxy.port() > 0, "the proxy listens on a port"),
                    () -> assertEquals("http://127.0.0.1:" + proxy.port(), proxy.proxyUrl(),
                            "the proxy URL is on the loopback"),
                    () -> assertEquals(List.of(admitted), List.copyOf(proxy.endpoints()),
                            "the endpoints are kept in lower case"));
        }
    }

    /** @return a loopback server that echoes every byte it receives */
    private static ServerSocket echoServer() throws IOException {
        var server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread.startVirtualThread(() -> {
            try (var socket = server.accept()) {
                var input = socket.getInputStream();
                var output = socket.getOutputStream();
                int read;
                while ((read = input.read()) != -1) {
                    output.write(read);
                    output.flush();
                }
            } catch (IOException _) {
                // the client closed the connection
            }
        });
        return server;
    }

    private static String connect(InputStream input, Socket client, String destination) throws IOException {
        client.getOutputStream().write(("CONNECT " + destination + " HTTP/1.1\r\nHost: " + destination + "\r\n\r\n")
                .getBytes(StandardCharsets.US_ASCII));
        client.getOutputStream().flush();
        return statusLine(input);
    }

    /** @return the status line, after consuming the blank line that ends the proxy's response head */
    private static String statusLine(InputStream input) throws IOException {
        var bytes = new ByteArrayOutputStream();
        int read;
        while ((read = input.read()) != -1) {
            bytes.write(read);
            if (bytes.size() >= 4 && endsWithHead(bytes)) {
                break;
            }
        }
        var head = bytes.toString(StandardCharsets.US_ASCII);
        var newline = head.indexOf("\r\n");
        return newline < 0 ? head : head.substring(0, newline);
    }

    private static boolean endsWithHead(ByteArrayOutputStream bytes) {
        var value = bytes.toByteArray();
        var length = value.length;
        return length >= 4 && value[length - 4] == '\r' && value[length - 3] == '\n' && value[length - 2] == '\r'
                && value[length - 1] == '\n';
    }
}
