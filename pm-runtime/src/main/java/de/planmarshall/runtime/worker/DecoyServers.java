/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * The loopback-only decoy servers of one profile verification run (PM-CLIENT-5, Profile Verification): a decoy MCP
 * server and a decoy model endpoint, each bound to {@code 127.0.0.1} on an ephemeral port and each recording the
 * requests a worker made of it.
 * <p>
 * The isolation and classes stages enter the MCP decoy in a harness configuration file of the probe workspace and the
 * endpoint decoy in a decoy {@code env} block. A worker that lists or calls a decoy tool, or that sends a request to
 * the decoy endpoint, has left its own configuration and is not isolated. The servers never leave loopback, so a decoy
 * cannot be reached from the network, and the hits of a run are the evidence the stages record.
 *
 * @since 0.1
 */
public final class DecoyServers implements AutoCloseable {

    /**
     * One loopback decoy server and the requests a worker made of it.
     *
     * @since 0.1
     */
    public static final class Server implements AutoCloseable {

        private final HttpServer server;

        private final List<String> hits = new CopyOnWriteArrayList<>();

        private Server(HttpServer server) {
            this.server = server;
        }

        /** @return the loopback base URI of the decoy */
        public URI uri() {
            return URI.create("http://127.0.0.1:" + port() + "/");
        }

        /** @return the ephemeral port the decoy listens on */
        public int port() {
            return server.getAddress().getPort();
        }

        /** @return the requests the decoy received, as {@code METHOD path} */
        public List<String> hits() {
            return List.copyOf(hits);
        }

        /** @return whether a worker reached the decoy */
        public boolean touched() {
            return !hits.isEmpty();
        }

        /** Stops the decoy. */
        @Override
        public void close() {
            server.stop(0);
        }
    }

    private final Server mcp;

    private final Server endpoint;

    private DecoyServers(Server mcp, Server endpoint) {
        this.mcp = mcp;
        this.endpoint = endpoint;
    }

    /**
     * @return the two decoy servers of one probe run
     * @throws IOException if a decoy cannot bind its loopback port
     */
    public static DecoyServers start() throws IOException {
        return new DecoyServers(server("/mcp"), server("/models"));
    }

    /** @return the decoy MCP server */
    public Server mcp() {
        return mcp;
    }

    /** @return the decoy model endpoint */
    public Server endpoint() {
        return endpoint;
    }

    /** Stops both decoys. */
    @Override
    public void close() {
        mcp.close();
        endpoint.close();
    }

    private static Server server(String path) throws IOException {
        var address = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        var decoy = new Server(HttpServer.create(address, 0));
        decoy.server.createContext(path, exchange -> {
            decoy.hits.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            var body = "{\"decoy\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        decoy.server.start();
        return decoy;
    }
}
