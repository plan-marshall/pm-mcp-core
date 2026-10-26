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

import java.nio.file.Path;
import java.util.Objects;

/**
 * The executable one process of a worker's recorded process tree runs (PM-WORK-7, {@code agent_started}): its PID, the
 * absolute path of its executable file and the {@code sha256:} digest of that file.
 * <p>
 * The value is what the operating system answers for the process, never what the process says about itself: the path is
 * the one the kernel reports for the running file and the digest is taken over that file, so a binary of the enrolled
 * executable's name with other content is not the enrolled executable.
 *
 * @param pid    the PID of the process, from 1
 * @param path   the absolute path of its executable
 * @param digest the digest of the executable file, prefixed {@value #DIGEST_PREFIX}
 * @since 0.1
 */
public record ProcessExecutable(long pid, Path path, String digest) {

    /** The prefix of the digest, the form the specifications write a SHA-256 in. */
    public static final String DIGEST_PREFIX = "sha256:";

    /**
     * @throws IllegalArgumentException if the PID is below 1, the path is not absolute, or the digest carries no
     *                                  prefixed value
     * @throws NullPointerException     if the path or the digest is {@code null}
     */
    public ProcessExecutable {
        if (pid < 1) {
            throw new IllegalArgumentException("A PID counts from 1: " + pid);
        }
        Objects.requireNonNull(path, "path");
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("The executable path is not absolute: " + path);
        }
        Objects.requireNonNull(digest, "digest");
        if (!digest.startsWith(DIGEST_PREFIX) || digest.length() == DIGEST_PREFIX.length()) {
            throw new IllegalArgumentException("The digest is '%s<hex>': %s".formatted(DIGEST_PREFIX, digest));
        }
    }

    /**
     * @return the digest without its algorithm prefix, the form the harness enrolment stores
     */
    public String sha256() {
        return digest.substring(DIGEST_PREFIX.length());
    }
}
