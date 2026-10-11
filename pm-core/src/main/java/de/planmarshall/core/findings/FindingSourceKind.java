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

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed kind of producer that filed a finding (member {@code source} of the findings ledger record,
 * PM-IMPL-1), and with the provider id the key a finding source is registered under ({@link FindingSourceKey}).
 * <p>
 * The constants are the closed wire vocabulary of the findings store and name no provider identity: no value names
 * a single code analysis provider, whose id is carried apart in {@code provider}. The two constants of the
 * pull-request and merge-request review sources are the single exemption of the provider-neutrality guard of
 * {@code pm-core}, because their names are fixed by that vocabulary.
 *
 * @since 0.1
 */
public enum FindingSourceKind {
    /** A code analysis provider, named by its provider id. */
    ANALYSIS("analysis"),
    /** A review comment of a pull request. */
    GITHUB_PR("github_pr"),
    /** A review comment of a merge request. */
    GITLAB_MR("gitlab_mr"),
    /** A failed CI check; the finding carries its CI failure. */
    CI("ci"),
    /** The self-review of a change. */
    SELF_REVIEW("self_review"),
    /** An architecture gate. */
    ARCH_GATE("arch_gate"),
    /** A compiler diagnostic. */
    COMPILER("compiler"),
    /** A quality gate of a phase; the finding names its phase. */
    QGATE("qgate"),
    /** A security audit. */
    SECURITY_AUDIT("security_audit"),
    /** A code review. */
    CODE_REVIEW("code_review"),
    /** The server's pre-merge dependency delta check. */
    DEPENDENCY_DELTA("dependency_delta");

    private final String wireName;

    FindingSourceKind(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value in the findings store */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value in the findings store
     * @return the value of that name; empty if no value has it
     */
    public static Optional<FindingSourceKind> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
