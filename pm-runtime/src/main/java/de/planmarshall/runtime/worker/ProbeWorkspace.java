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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * The fresh probe workspace of one profile verification run (PM-CLIENT-5, Profile Verification): the temporary
 * directory a probe worker runs in, created in the user's temporary directory with mode {@code 0700} and removed
 * afterwards. It lies outside {@code <PM_MCP_BASE>}, which no job may read or write, and is the only project root in
 * the probe worker's write set.
 * <p>
 * The workspace carries the run's canary: an unpredictable token written to the workspace's canary file that no probe
 * worker may read, echo or hand on. The isolation and classes stages place the token where a worker that follows an
 * instruction, runs a tool or loads a user skill would reach it, so a worker that names the token has left its own
 * configuration. The token is generated afresh for every workspace, so it cannot be guessed from an earlier run.
 *
 * @since 0.1
 */
public final class ProbeWorkspace implements AutoCloseable {

    /** The prefix of a probe workspace directory. */
    public static final String DIRECTORY_PREFIX = "pm-mcp-probe-";

    /** The name of the file that carries the canary in the workspace. */
    public static final String CANARY_FILE = "canary.txt";

    private static final int CANARY_BYTES = 16;

    private static final String CANARY_PREFIX = "pm-probe-canary-";

    private static final Set<PosixFilePermission> OWNER_ONLY = Set.of(PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

    private final Path root;

    private final String canary;

    private ProbeWorkspace(Path root, String canary) {
        this.root = root;
        this.canary = canary;
    }

    /**
     * @return a workspace below the JVM's temporary directory
     * @throws IOException if the workspace cannot be created
     */
    public static ProbeWorkspace create() throws IOException {
        return create(Path.of(System.getProperty("java.io.tmpdir")));
    }

    /**
     * @param parent the directory the workspace is created in
     * @return a fresh workspace with a new canary
     * @throws IOException              if the workspace cannot be created or written
     * @throws NullPointerException     if the parent is {@code null}
     */
    public static ProbeWorkspace create(Path parent) throws IOException {
        Objects.requireNonNull(parent, "parent");
        var root = Files.createTempDirectory(parent, DIRECTORY_PREFIX);
        restrictToOwner(root);
        var canary = CANARY_PREFIX + HexFormat.of().formatHex(randomBytes());
        Files.writeString(root.resolve(CANARY_FILE), canary, StandardCharsets.UTF_8);
        return new ProbeWorkspace(root, canary);
    }

    /** @return the root of the workspace, the probe worker's write set */
    public Path root() {
        return root;
    }

    /** @return the file that carries the canary */
    public Path canaryFile() {
        return root.resolve(CANARY_FILE);
    }

    /** @return the canary token of this run */
    public String canary() {
        return canary;
    }

    /** @return whether the canary file is still readable by the workspace's owner */
    public boolean canaryReadable() {
        return Files.isReadable(canaryFile());
    }

    /** Removes the workspace and everything in it. */
    @Override
    public void close() {
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(ProbeWorkspace::delete);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove the probe workspace " + root, e);
        }
    }

    private static byte[] randomBytes() {
        var bytes = new byte[CANARY_BYTES];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static void restrictToOwner(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, OWNER_ONLY);
        } catch (UnsupportedOperationException _) {
            // a filesystem without POSIX permissions; the temporary directory's own mode bounds access
        }
    }

    private static void delete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not remove " + path, e);
        }
    }
}
