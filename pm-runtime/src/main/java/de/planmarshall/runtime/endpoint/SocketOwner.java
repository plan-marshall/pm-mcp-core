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

import java.util.Objects;

/**
 * Which program owns the listening socket of an endpoint (Client Profiles § Enrolment): the process that accepted the
 * port, and the executable it runs.
 * <p>
 * The runtime checks before every request that this program is the enrolled one, so no task text reaches a program the
 * operator did not enrol. The lookup fails closed: where the platform keeps no table of listening sockets, or the owner
 * cannot be read, it answers {@link Lookup#found()} {@code false} and the request is refused.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface SocketOwner {

    /**
     * @param host the host of the endpoint's {@code base_url}
     * @param port the port of the endpoint's {@code base_url}
     * @return the executable of the process that owns the listening socket
     */
    Lookup ownerOf(String host, int port);

    /** @return the lookup of this machine, which reads the operating system's own table of listening sockets */
    static SocketOwner ofThisMachine() {
        return new ProcSocketOwner();
    }

    /**
     * The owner of one listening socket, or why it could not be read.
     *
     * @param found          whether the owner was read
     * @param executablePath the absolute path of the executable that owns the socket; empty when not found
     * @param detail         what happened, for a refusal
     * @since 0.1
     */
    record Lookup(boolean found, String executablePath, String detail) {

        /** Copies the values. */
        public Lookup {
            Objects.requireNonNull(executablePath, "executablePath");
            Objects.requireNonNull(detail, "detail");
        }

        /**
         * @param executablePath the absolute path of the executable that owns the socket
         * @return the lookup of a socket whose owner was read
         */
        public static Lookup found(String executablePath) {
            return new Lookup(true, executablePath, "the socket is owned by " + executablePath);
        }

        /**
         * @param detail why no owner was read
         * @return the lookup of a socket whose owner was not read
         */
        public static Lookup notFound(String detail) {
            return new Lookup(false, "", detail);
        }

        /** @return whether the owner was read */
        public boolean isFound() {
            return found;
        }

        /**
         * @param enrolled the absolute path of the enrolled executable
         * @return whether the owner runs the enrolled program
         */
        public boolean runs(String enrolled) {
            return found && executablePath.equals(enrolled);
        }
    }
}
