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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The owner lookup of this machine (Client Profiles § Enrolment): it reads the operating system's own table of listening
 * sockets — on Linux {@code /proc/net/tcp}, {@code /proc/net/tcp6} and the file descriptors below {@code /proc/<pid>/fd} —
 * and answers the executable of the process that accepted the port.
 * <p>
 * A platform that keeps no such table answers {@link SocketOwner.Lookup#notFound} with a detail that says so, which
 * refuses the request: the check never passes because it could not look.
 *
 * @since 0.1
 */
final class ProcSocketOwner implements SocketOwner {

    private static final String LISTEN = "0A";
    private static final String SOCKET_PREFIX = "socket:[";
    private static final Path PROCESS_TABLE = Path.of("/proc");

    @Override
    public Lookup ownerOf(String host, int port) {
        Objects.requireNonNull(host, "host");
        if (!Files.isDirectory(PROCESS_TABLE.resolve("net"))) {
            return Lookup.notFound("the owner of a listening socket is not readable on this platform");
        }
        var inode = inode(PROCESS_TABLE.resolve("net/tcp"), port)
                .or(() -> inode(PROCESS_TABLE.resolve("net/tcp6"), port));
        if (inode.isEmpty()) {
            return Lookup.notFound("no program listens on the port %d".formatted(port));
        }
        var owner = processOf(inode.get());
        if (owner.isEmpty()) {
            return Lookup.notFound("the program that listens on the port %d cannot be found".formatted(port));
        }
        var executable = executableOf(owner.get());
        if (executable.isEmpty()) {
            return Lookup.notFound("the executable of the process %d cannot be read".formatted(owner.get()));
        }
        return Lookup.found(executable.get().toString());
    }

    /**
     * @param table the socket table of the kernel
     * @param port  the port
     * @return the inode of the listening socket on the port
     */
    private static Optional<String> inode(Path table, int port) {
        if (!Files.isRegularFile(table)) {
            return Optional.empty();
        }
        var wanted = ":%04X".formatted(port);
        try (Stream<String> lines = Files.lines(table)) {
            return lines.skip(1).map(line -> line.strip().split("\\s+"))
                    .filter(fields -> fields.length > 9 && LISTEN.equals(fields[3]) && fields[1].endsWith(wanted))
                    .map(fields -> fields[9]).findFirst();
        } catch (IOException e) {
            throw new UncheckedIOException("The socket table cannot be read: " + table, e);
        }
    }

    /**
     * @param inode the inode of a listening socket
     * @return the process that holds a file descriptor of it
     */
    private static Optional<Long> processOf(String inode) {
        var wanted = SOCKET_PREFIX + inode + "]";
        try (Stream<Path> processes = Files.list(PROCESS_TABLE)) {
            var candidates = processes.filter(path -> path.getFileName().toString()
                    .chars().allMatch(character -> character >= '0' && character <= '9')).toList();
            for (var process : candidates) {
                var pid = Long.parseLong(process.getFileName().toString());
                if (holds(process.resolve("fd"), wanted)) {
                    return Optional.of(pid);
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("The process table cannot be listed", e);
        }
    }

    private static boolean holds(Path descriptors, String socket) {
        if (!Files.isDirectory(descriptors)) {
            return false;
        }
        try (Stream<Path> entries = Files.list(descriptors)) {
            return entries.anyMatch(descriptor -> socket.equals(link(descriptor)));
        } catch (IOException _) {
            return false;
        }
    }

    private static Optional<Path> executableOf(long pid) {
        try {
            var executable = Files.readSymbolicLink(PROCESS_TABLE.resolve(Long.toString(pid)).resolve("exe"));
            return executable.isAbsolute() ? Optional.of(executable) : Optional.empty();
        } catch (IOException _) {
            return Optional.empty();
        }
    }

    private static String link(Path descriptor) {
        try {
            return Files.readSymbolicLink(descriptor).toString();
        } catch (IOException _) {
            return "";
        }
    }
}
