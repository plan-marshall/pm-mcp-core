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
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * The per-project build daemon store (PM-EXEC-4, PM-SEC-5): the directory below the runtime base that holds the
 * registries and logs of the build daemons of one project and one write set, so that no build daemon serves the jobs of
 * two write sets.
 * <p>
 * The store is {@code <PM_MCP_BASE>/projects/<project_id>/daemons/<write_set_digest>/}, where the digest is the first
 * 16 lower-case hex characters of the SHA-256 over the job's sorted, de-duplicated write set. The store is created with
 * mode {@code 0700} when the job's confinement is composed. It holds only the registries and logs of the build daemons;
 * the runtime reads nothing from it and trusts nothing in it. It is readable and writable only for the
 * {@code build} and {@code project_script} jobs of the project and for a {@code git} job that runs repository hooks.
 * <p>
 * The build tools find it through {@code MVND_DAEMON_STORAGE=<store>/mvnd} (an environment value) and through
 * {@code -Dorg.gradle.daemon.registry.base=<store>/gradle}, which {@link #gradleRegistryArgument(Path)} renders for the
 * caller that appends it to the argument vector of a Gradle wrapper; the flag-injecting {@code GRADLE_OPTS} is never
 * exported.
 * <p>
 * Specification: {@code doc/specification/job-runtime/02-confinement-and-environment.adoc} (Build Daemons).
 *
 * @since 0.1
 */
public final class BuildDaemonStore {

    /** The directory of the daemons of one project below {@code projects/<project_id>/}. */
    public static final String DAEMONS = "daemons";

    /** The directory of the {@code mvnd} registry below the daemon store. */
    public static final String MVND = "mvnd";

    /** The directory of the Gradle daemon registry below the daemon store. */
    public static final String GRADLE = "gradle";

    /** The number of hex characters of the write-set digest. */
    public static final int DIGEST_LENGTH = 16;

    /** The system property of the Gradle daemon registry directory. */
    public static final String GRADLE_REGISTRY_PROPERTY = "-Dorg.gradle.daemon.registry.base=";

    private BuildDaemonStore() {
    }

    /**
     * The digest of a write set: the first {@value #DIGEST_LENGTH} hex characters of the SHA-256 over the sorted,
     * newline-separated absolute paths.
     *
     * @param writeSet the write set of the job
     * @return the digest, lower-case hexadecimal
     * @throws NullPointerException if the list or an element is {@code null}
     */
    public static String digest(List<Path> writeSet) {
        var sorted = new ArrayList<String>();
        for (var path : Objects.requireNonNull(writeSet, "writeSet")) {
            var normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize().toString();
            if (!sorted.contains(normalized)) {
                sorted.add(normalized);
            }
        }
        sorted.sort(String::compareTo);
        var joined = String.join("\n", sorted);
        try {
            var hash = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, DIGEST_LENGTH);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    /**
     * @param base      the base directory of the runtime {@code <PM_MCP_BASE>}
     * @param projectId the enrolled project id
     * @param writeSet  the write set of the job
     * @return the daemon store of the project and write set
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the base is not absolute
     */
    public static Path path(Path base, String projectId, List<Path> writeSet) {
        Objects.requireNonNull(base, "base");
        if (!base.isAbsolute()) {
            throw new IllegalArgumentException("The runtime base must be absolute: " + base);
        }
        Objects.requireNonNull(projectId, "projectId");
        return base.resolve("projects").resolve(projectId).resolve(DAEMONS).resolve(digest(writeSet));
    }

    /**
     * Creates the daemon store with mode {@code 0700}.
     *
     * @param store the daemon store of the project and write set
     * @throws java.io.UncheckedIOException if the directory cannot be created
     */
    public static void ensure(Path store) {
        Objects.requireNonNull(store, "store");
        try {
            Files.createDirectories(store);
            Files.setPosixFilePermissions(store, PosixFilePermissions.fromString("rwx------"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the daemon store " + store, e);
        }
    }

    /**
     * @param store the daemon store of the project and write set
     * @return the {@code mvnd} directory of the store
     */
    public static Path mvnd(Path store) {
        return Objects.requireNonNull(store, "store").resolve(MVND);
    }

    /**
     * @param store the daemon store of the project and write set
     * @return the Gradle daemon registry directory of the store
     */
    public static Path gradle(Path store) {
        return Objects.requireNonNull(store, "store").resolve(GRADLE);
    }

    /**
     * @param store the daemon store of the project and write set
     * @return the argument the runtime appends to the argument vector of a Gradle wrapper
     */
    public static String gradleRegistryArgument(Path store) {
        return GRADLE_REGISTRY_PROPERTY + gradle(store);
    }
}
