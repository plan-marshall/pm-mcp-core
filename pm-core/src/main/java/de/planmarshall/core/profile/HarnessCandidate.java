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
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.id.HarnessId;

/**
 * One profile of the effective profile set as the harness scan of the machine onboarding found it (PM-CLIENT-5, Harness
 * Profiles § Harness Scan): the profile's harness id and display name, whether its {@code headless.executable} was found
 * and where, the version the version probe printed, whether that version lies in {@code headless.supported_versions},
 * whether the profile may serve workers at all, and the profile's install hint for the case it was not found.
 * <p>
 * The candidate is the scan's own value, not an enrolment: the scan records the absolute path it found and the version
 * probe's answer, and only {@code harness.enrol} records that path and its digest in the harness enrolment. A profile
 * with {@code headless: null} or {@code worker.eligible: false} is a candidate that is shown and marked, never one that is
 * offered as a worker or as the default, which is why {@code workerEligible} implies {@code found} and {@code supported}.
 *
 * @param harnessId      the id of the profile, the {@code client_id} of the schema until the harness renames land
 * @param displayName    the profile's display name
 * @param found          whether the executable was found in the login {@code PATH} or the profile's install locations
 * @param version        the exact version the version probe printed; {@code null} where nothing was found
 * @param executablePath the absolute path the scan found, the one {@code harness.enrol} records; {@code null} where
 *                       nothing was found
 * @param supported      whether the version lies in the profile's supported range; {@code false} where nothing was found
 * @param workerEligible whether the profile may serve workers and may be the default
 * @param installHint    how to install the harness, the profile's {@code headless.install_hint}; shown where nothing was
 *                       found
 * @since 0.1
 */
@Builder
public record HarnessCandidate(String harnessId, String displayName, boolean found, @Nullable String version,
@Nullable String executablePath, boolean supported, boolean workerEligible, String installHint) {

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the id does not match the profile schema, if a version or a path is recorded
     *                                  for a harness that was not found or is missing for one that was, if the version or
     *                                  the path is blank, or if the candidate claims to be worker-eligible or
     *                                  supported while it was not found
     */
    public HarnessCandidate {
        requireNames(harnessId, displayName, installHint);
        requireFoundShape(harnessId, found, version, executablePath);
        requireSelectability(harnessId, found, supported, workerEligible);
    }

    /**
     * @return whether the candidate may be preselected, the default included: the harness was found, its version is in
     *         the profile's range and the profile may serve workers
     */
    public boolean selectable() {
        return found && supported && workerEligible;
    }

    private static void requireNames(String harnessId, String displayName, String installHint) {
        Objects.requireNonNull(harnessId, "harnessId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(installHint, "installHint");
        if (!HarnessId.matches(harnessId)) {
            throw new IllegalArgumentException("Not a harness id: " + harnessId);
        }
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("A harness candidate names its display name");
        }
        if (installHint.isBlank()) {
            throw new IllegalArgumentException("A harness candidate carries its install hint: " + harnessId);
        }
    }

    private static void requireFoundShape(String harnessId, boolean found, @Nullable String version,
            @Nullable String executablePath) {
        if (found != (version != null)) {
            throw new IllegalArgumentException(
                    "A harness carries a version exactly when it was found: " + harnessId);
        }
        if (found != (executablePath != null)) {
            throw new IllegalArgumentException(
                    "A harness carries a path exactly when it was found: " + harnessId);
        }
        if (version != null && version.isBlank()) {
            throw new IllegalArgumentException("A probed version must not be blank: " + harnessId);
        }
        if (executablePath != null && executablePath.isBlank()) {
            throw new IllegalArgumentException("A found executable path must not be blank: " + harnessId);
        }
    }

    private static void requireSelectability(String harnessId, boolean found, boolean supported,
            boolean workerEligible) {
        if (supported && !found) {
            throw new IllegalArgumentException("A harness that was not found is not supported: " + harnessId);
        }
        if (workerEligible && !supported) {
            throw new IllegalArgumentException("A harness offered for workers is supported: " + harnessId);
        }
    }
}
