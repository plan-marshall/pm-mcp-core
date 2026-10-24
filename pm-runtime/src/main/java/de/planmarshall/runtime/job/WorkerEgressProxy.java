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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * The worker egress proxy (PM-SEC-5): the runtime's HTTP {@code CONNECT} proxy on a loopback port, one per worker
 * generation, that admits a tunnel only to a host and port of the client profile's {@code headless.endpoints} and
 * refuses every other destination.
 * <p>
 * The proxy reads no content of a tunnel. Every refused destination is an activity entry of the worker and counts
 * toward an audit record {@code egress_refused} per worker generation. The proxy is the worker's only route out where
 * the platform can enforce it (the {@code pm-exec} launcher, the sandbox profile or the worker's network namespace);
 * this class enforces the destination allowlist of the tunnel and records the refusals.
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Worker Egress Proxy).
 *
 * @since 0.1
 */
public final class WorkerEgressProxy implements AutoCloseable {

    /** The request method of a tunnel. */
    private static final String CONNECT = "CONNECT";

    /** The longest request line the proxy reads. */
    private static final int MAX_REQUEST_LINE = 8 * 1024;

    /** How long a tunnel to an admitted destination may take to open. */
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;

    private final String generationId;
    private final Set<String> endpoints;
    private final Consumer<String> activity;
    private final AuditSink audit;
    private final ServerSocket server;
    private final Set<Socket> open = ConcurrentHashMap.newKeySet();
    private final AtomicLong refused = new AtomicLong();
    private volatile boolean closed;

    private WorkerEgressProxy(String generationId, List<String> endpoints, Consumer<String> activity, AuditSink audit,
            ServerSocket server) {
        this.generationId = generationId;
        this.endpoints = endpoints.stream().map(endpoint -> endpoint.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.activity = activity;
        this.audit = audit;
        this.server = server;
    }

    /**
     * Starts a proxy on a loopback port of the machine.
     *
     * @param generationId the worker generation the proxy serves
     * @param endpoints    the {@code host:port} destinations the proxy admits; not empty
     * @param activity     where a refused destination is recorded as an activity entry of the worker
     * @param audit        where the {@code egress_refused} records are handed over
     * @return the started proxy
     * @throws IOException              if the loopback port cannot be bound
     * @throws IllegalArgumentException if the generation id is empty or no endpoint is given
     * @throws NullPointerException     if an argument is {@code null}
     */
    public static WorkerEgressProxy start(String generationId, List<String> endpoints, Consumer<String> activity,
            AuditSink audit) throws IOException {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(endpoints, "endpoints");
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(audit, "audit");
        if (generationId.isEmpty()) {
            throw new IllegalArgumentException("A proxy serves a generation");
        }
        if (endpoints.isEmpty()) {
            throw new IllegalArgumentException("A proxy admits at least one endpoint");
        }
        var server = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        var proxy = new WorkerEgressProxy(generationId, endpoints, activity, audit, server);
        Thread.startVirtualThread(proxy::accept);
        return proxy;
    }

    /** @return the loopback port the proxy listens on */
    public int port() {
        return server.getLocalPort();
    }

    /** @return the proxy's URL for {@code HTTPS_PROXY} and {@code HTTP_PROXY} */
    public String proxyUrl() {
        return "http://127.0.0.1:" + port();
    }

    /** @return the number of destinations refused so far */
    public long refusedCount() {
        return refused.get();
    }

    /** @return the destinations the proxy admits, lower-case */
    public Set<String> endpoints() {
        return endpoints;
    }

    private void accept() {
        while (!closed) {
            try {
                var socket = server.accept();
                open.add(socket);
                Thread.startVirtualThread(() -> handle(socket));
            } catch (IOException e) {
                if (!closed) {
                    // The listener cannot accept any more connections; nothing is left to serve
                    close();
                }
            }
        }
    }

    private void handle(Socket client) {
        try (client) {
            var input = client.getInputStream();
            var requestLine = readLine(input);
            if (requestLine == null) {
                return;
            }
            var parts = requestLine.split(" ");
            if (parts.length < 2 || !CONNECT.equalsIgnoreCase(parts[0])) {
                respond(client.getOutputStream(), "405 Method Not Allowed");
                return;
            }
            var destination = parts[1].toLowerCase(Locale.ROOT);
            if (!isHostPort(destination) || !endpoints.contains(destination)) {
                refuse(client.getOutputStream(), destination);
                return;
            }
            drainHeaders(input);
            tunnel(client, destination);
        } catch (IOException e) {
            // A broken client or destination ends its tunnel only; the proxy stays up
        } finally {
            open.remove(client);
        }
    }

    /** @return whether the text is a host and a decimal port, the shape of an admitted endpoint */
    private static boolean isHostPort(String text) {
        var separator = text.lastIndexOf(':');
        if (separator <= 0 || separator == text.length() - 1) {
            return false;
        }
        var port = text.substring(separator + 1);
        if (port.length() > 5 || port.charAt(0) == '0') {
            return false;
        }
        for (var index = 0; index < port.length(); index++) {
            if (port.charAt(index) < '0' || port.charAt(index) > '9') {
                return false;
            }
        }
        return Integer.parseInt(port) <= 65535;
    }

    private void refuse(OutputStream out, String destination) throws IOException {
        refused.incrementAndGet();
        activity.accept(destination);
        audit.append(AuditEvent.egressRefused(generationId, destination));
        respond(out, "403 Forbidden");
    }

    private void tunnel(Socket client, String destination) throws IOException {
        var separator = destination.lastIndexOf(':');
        var host = destination.substring(0, separator);
        var port = Integer.parseInt(destination.substring(separator + 1));
        try (var remote = new Socket()) {
            open.add(remote);
            try {
                remote.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MILLIS);
                respond(client.getOutputStream(), "200 Connection Established");
                var up = Thread.startVirtualThread(() -> copy(client, remote));
                copy(remote, client);
                up.join();
            } finally {
                open.remove(remote);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            open.remove(client);
        }
    }

    private static void copy(Socket from, Socket to) {
        try {
            from.getInputStream().transferTo(to.getOutputStream());
        } catch (IOException e) {
            // the other end closed the tunnel
        } finally {
            try {
                to.shutdownOutput();
            } catch (IOException e) {
                // already closed
            }
        }
    }

    private static void respond(OutputStream out, String status) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static void drainHeaders(InputStream input) throws IOException {
        String line;
        do {
            line = readLine(input);
        } while (line != null && !line.isEmpty());
    }

    private static String readLine(InputStream input) throws IOException {
        var buffer = new StringBuilder();
        int read;
        while ((read = input.read()) != -1) {
            if (read == '\n') {
                return buffer.toString();
            }
            if (read != '\r') {
                buffer.append((char) read);
            }
            if (buffer.length() > MAX_REQUEST_LINE) {
                throw new IOException("The request line is too long");
            }
        }
        return buffer.isEmpty() ? null : buffer.toString();
    }

    @Override
    public void close() {
        closed = true;
        try {
            server.close();
        } catch (IOException e) {
            // already closed
        }
        for (var socket : open) {
            try {
                socket.close();
            } catch (IOException e) {
                // already closed
            }
        }
        open.clear();
    }
}
