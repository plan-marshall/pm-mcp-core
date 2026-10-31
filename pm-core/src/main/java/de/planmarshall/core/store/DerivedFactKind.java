/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed set of the fact kinds of the derived facts store (Store Schemas § Derived Facts Store, {@code kind};
 * PM-EXT-6).
 * <p>
 * The schema enumerates the sixteen kinds, so a kind is a Java constant and never a free-form string: a document that
 * names another kind is unreadable. The set is closed in the code and open for extension only through the
 * specification: a new kind is a specification change, an enum constant and a registration of its value schema in
 * {@link DerivedFactKinds} (ADR {@code core-store/053}).
 * <p>
 * {@link #AGENT_IDENTITY} is operator-only (Store Schemas § Derived Facts Store, bullet <em>Operator-Only Facts</em>):
 * it is shown to the operator and used server-side, and it never appears in a model-facing representation, resource or
 * tool result.
 *
 * @since 0.1
 */
public enum DerivedFactKind {

    /** The default base branch of the project (local, confirmed by the provider). */
    BASE_BRANCH_DEFAULT("base_branch_default", false),
    /** The remote the plan branch is pushed to (local). */
    PUSH_REMOTE("push_remote", false),
    /** The CI provider of the project (local, then network). */
    CI_PROVIDER("ci_provider", false),
    /** The merge methods the repository allows (network). */
    MERGE_METHODS_ALLOWED("merge_methods_allowed", false),
    /** Whether the base branch enforces its merge queue, and whether a queue is available (network). */
    MERGE_QUEUE_ENFORCED("merge_queue_enforced", false),
    /** Whether the merge path requires the base branch to be up to date (network). */
    BRANCH_UP_TO_DATE_REQUIRED("branch_up_to_date_required", false),
    /** The required status check contexts of the base branch (network). */
    REQUIRED_CHECKS("required_checks", false),
    /** Whether the repository requires signed commits (network). */
    SIGNED_COMMITS_REQUIRED("signed_commits_required", false),
    /** Whether a direct push to the base branch is refused (network). */
    BASE_BRANCH_PROTECTED("base_branch_protected", false),
    /** Whether the agent credential can read check runs (network). */
    CHECKS_VISIBLE("checks_visible", false),
    /** The provider's identity of the agent credential; operator-only (network). */
    AGENT_IDENTITY("agent_identity", true),
    /** The repository features the native git variant cannot serve (local). */
    GIT_NATIVE_CAPABILITY("git_native_capability", false),
    /** The active domains of the project (local). */
    DOMAIN_ACTIVATION("domain_activation", false),
    /** The active profiles per module (local). */
    ACTIVE_PROFILES("active_profiles", false),
    /** The structural snapshot of module discovery (local). */
    MODULE_DISCOVERY("module_discovery", false),
    /** The calibrated size-to-token table of the envelope sizing (local). */
    COST_CALIBRATION("cost_calibration", false);

    private final String wireName;
    private final boolean operatorOnly;

    DerivedFactKind(String wireName, boolean operatorOnly) {
        this.wireName = wireName;
        this.operatorOnly = operatorOnly;
    }

    /** @return the name of the kind in a store */
    public String wireName() {
        return wireName;
    }

    /** @return {@code true} if the fact is operator-only and never model-facing */
    public boolean operatorOnly() {
        return operatorOnly;
    }

    /**
     * @param wireName the name of a kind in a store
     * @return the kind of that name; empty if no kind has it
     */
    public static Optional<DerivedFactKind> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(kind -> kind.wireName.equals(wireName)).findFirst();
    }
}
