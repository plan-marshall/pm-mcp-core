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

/**
 * One row of the harness list (PM-CLIENT-5, Harness Profiles § Harness Scan, § Tokens, Not Money): the harness with the
 * path and version it was found with — or the install hint of the profile where nothing was found — whether it is
 * supported and may serve workers, whether it is the default, and what it reports of its token usage for that version.
 * <p>
 * A harness that is not supported, or whose profile may not serve workers, stays in the list and is marked: the operator
 * sees why it is not offered, instead of a harness that silently disappeared. Only a row that is supported and
 * worker-eligible is selectable, which is the same predicate the scan preselects with.
 *
 * @param harnessId      the id of the harness
 * @param displayName    the harness's display name
 * @param version        the version the scan probed; empty where the harness was not found
 * @param executablePath the absolute path the scan found; {@code null} where the harness was not found
 * @param installHint    how to install the harness, shown where it was not found
 * @param supported      whether the version lies in the profile's supported range
 * @param workerEligible whether the profile may serve workers
 * @param defaultHarness whether the row is the default harness of the machine
 * @param enrolled       whether the harness is enrolled in the machine's harness store, so a row the scan found nothing
 *                       for still shows that the machine has an enrolment to remove
 * @param usage          what the harness reports of its token usage for the row's version
 * @since 0.1
 */
@Builder
public record HarnessListEntry(String harnessId, String displayName, String version,
@Nullable String executablePath, String installHint, boolean supported, boolean workerEligible,
boolean defaultHarness, boolean enrolled, HarnessUsageCoverage usage) {

    /**
     * @throws NullPointerException     if a component that is never {@code null} is {@code null}
     * @throws IllegalArgumentException if the id, the display name or the install hint is blank, if a path is recorded
     *                                  for a row without a version or a version without a path, if a row that may serve
     *                                  workers is not supported, or if an unsupported row is the default
     */
    public HarnessListEntry {
        Objects.requireNonNull(harnessId, "harnessId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(installHint, "installHint");
        Objects.requireNonNull(usage, "usage");
        if (harnessId.isBlank() || displayName.isBlank() || installHint.isBlank()) {
            throw new IllegalArgumentException("A harness list entry names its harness and its hint");
        }
        if (version.isEmpty() != (executablePath == null)) {
            throw new IllegalArgumentException("A row carries a path exactly when it carries a version: " + harnessId);
        }
        if (supported && executablePath == null) {
            throw new IllegalArgumentException("A supported harness is one the scan found: " + harnessId);
        }
        if (workerEligible && !supported) {
            throw new IllegalArgumentException("An unsupported harness is not offered for workers: " + harnessId);
        }
        if (defaultHarness && !(supported && workerEligible)) {
            throw new IllegalArgumentException("The default harness is one that may serve workers: " + harnessId);
        }
    }

    /** @return whether the row may be preselected, which is the scan's own predicate */
    public boolean selectable() {
        return supported && workerEligible;
    }
}
