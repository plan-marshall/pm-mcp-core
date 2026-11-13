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
import java.util.regex.Pattern;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One profile of the effective profile set as the harness scan of the machine onboarding found it (PM-CLIENT-5, Harness
 * Profiles § Harness Scan): the profile's harness id and display name, whether its {@code headless.executable} was found,
 * the version the version probe printed, whether that version lies in {@code headless.supported_versions}, whether the
 * profile may serve workers at all, and the profile's install hint for the case it was not found.
 * <p>
 * The candidate is the scan's own value, not an enrolment: only {@code harness.enrol} records an absolute path and a
 * digest. A profile with {@code headless: null} or {@code worker.eligible: false} is a candidate that is shown and
 * marked, never one that is offered as a worker or as the default, which is why {@code workerEligible} implies
 * {@code found} and {@code supported}.
 *
 * @param harnessId     the id of the profile, the {@code client_id} of the schema until the harness renames land
 * @param displayName   the profile's display name
 * @param found         whether the executable was found in the login {@code PATH} or the profile's install locations
 * @param version       the exact version the version probe printed; {@code null} where nothing was found
 * @param supported     whether the version lies in the profile's supported range; {@code false} where nothing was found
 * @param workerEligible whether the profile may serve workers and may be the default
 * @param installHint   how to install the harness, the profile's {@code headless.install_hint}; required where nothing
 *                      was found
 * @since 0.1
 */
@Builder
public record HarnessCandidate(String harnessId, String displayName, boolean found, @Nullable String version,
boolean supported, boolean workerEligible, String installHint) {

    private static final Pattern HARNESS_ID = Pattern.compile("^[a-z][a-z0-9-]{1,31}$");

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the id does not match the profile schema, if a version is recorded for a
     *                                  harness that was not found or is missing for one that was, or if the candidate
     *                                  claims to be worker-eligible or supported while it was not found
     */
    public HarnessCandidate {
        Objects.requireNonNull(harnessId, "harnessId");
        if (!HARNESS_ID.matcher(harnessId).matches()) {
            throw new IllegalArgumentException("Not a harness id: " + harnessId);
        }
        Objects.requireNonNull(displayName, "displayName");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("A harness candidate names its display name");
        }
        Objects.requireNonNull(installHint, "installHint");
        if (installHint.isBlank()) {
            throw new IllegalArgumentException("A harness candidate carries its install hint: " + harnessId);
        }
        if (found && version == null) {
            throw new IllegalArgumentException("A found harness names the version it probed: " + harnessId);
        }
        if (!found && version != null) {
            throw new IllegalArgumentException("A harness that was not found has no version: " + harnessId);
        }
        if (version != null && version.isBlank()) {
            throw new IllegalArgumentException("A probed version must not be blank: " + harnessId);
        }
        if (!found && supported) {
            throw new IllegalArgumentException("A harness that was not found is not supported: " + harnessId);
        }
        if (workerEligible && !supported) {
            throw new IllegalArgumentException("A harness offered for workers is supported: " + harnessId);
        }
    }

    /**
     * @return whether the candidate may be preselected, the default included: the harness was found, its version is in
     *         the profile's range and the profile may serve workers
     */
    public boolean selectable() {
        return found && supported && workerEligible;
    }
}
