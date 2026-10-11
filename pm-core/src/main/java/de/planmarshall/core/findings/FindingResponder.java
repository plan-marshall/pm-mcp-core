/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

/**
 * Answers a finding on the provider it came from, with the disposition triage gave it (PM-IMPL-1). Implemented by
 * the provider modules and registered in a {@link FindingRegistry} under its {@link #key()}, the same key as the
 * {@link FindingSource} whose findings it answers.
 *
 * @since 0.1
 */
public interface FindingResponder {

    /**
     * @return the key this responder is registered under
     */
    FindingSourceKey key();

    /**
     * @param finding     the finding to answer
     * @param disposition the disposition of triage
     * @param reason      why the finding got this disposition
     */
    void respond(Finding finding, Disposition disposition, String reason);
}
