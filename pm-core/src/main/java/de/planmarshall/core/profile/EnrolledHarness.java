/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.util.Objects;

/**
 * The harness build an operator enrolled (PM-CLIENT-5, Profile Sets): the executable name, the exact version its version
 * probe reported and the SHA-256 of the executable file. A verification record is bound to the build it was measured
 * on; the loader compares an enrolled build with that record and never runs a profile of a build outside its supported
 * version range.
 *
 * @param executable       the bare executable name, as the profile declares it
 * @param version          the exact version the version probe reported
 * @param executableSha256 the SHA-256 of the executable file, as {@code sha256:} and 64 lower-case hexadecimal digits
 * @since 0.1
 */
public record EnrolledHarness(String executable, String version, String executableSha256) {

    /**
     * @throws IllegalArgumentException if a value is empty or the digest is not a {@code sha256:} digest
     */
    public EnrolledHarness {
        Objects.requireNonNull(executable, "executable");
        Objects.requireNonNull(version, "version");
        if (executable.isEmpty() || version.isEmpty()) {
            throw new IllegalArgumentException("An enrolled harness names its executable and version");
        }
        if (executableSha256 == null || !executableSha256.matches("sha256:[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Not a sha256: digest: " + executableSha256);
        }
    }
}
