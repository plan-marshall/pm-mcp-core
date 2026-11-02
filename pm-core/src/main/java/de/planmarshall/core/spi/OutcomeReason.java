/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Optional;
import java.util.Set;

/**
 * The closed reason of an outcome that explains why an operation failed, was skipped or stayed indeterminate
 * (PM-IMPL-5). A primitive author declares the reasons of one outcome through
 * {@link PrimitiveOutcome#reasonType()}, and the reason travels in {@link PrimitiveResult#reason()} as data.
 * <p>
 * A reason is data only: no grammar production, guard or verifier reads it. An operation whose callers continue
 * differently per cause gets a named outcome instead; every other failure, skip or indeterminacy is one outcome of
 * its class plus a reason. The rule, the classes that admit a reason and the closed table to conditions are stated in
 * {@code doc/specification/workflow-dsl/05-primitives.adoc#outcomes-and-reasons}.
 *
 * @since 0.1
 */
public interface OutcomeReason {

    /**
     * The condition kinds a reason may map to: {@code footprint_violation} and {@code precondition_unmet}, the two
     * conditions a primitive can originate. The other kinds belong to the engine: {@code guard_indeterminate} arises
     * from its guard evaluation, {@code job_incomplete} from its task attempts, {@code config_drift} from its
     * re-composition, and {@code integrity_breach} names the store it breached, so none of them is a cause a
     * primitive reports. Widening the set is a one-line change with a test (PM-IMPL-5). The closed table is stated in
     * {@code doc/specification/workflow-dsl/05-primitives.adoc#outcomes-and-reasons}.
     */
    Set<String> MAPPABLE_CONDITION_KINDS = Set.of("footprint_violation", "precondition_unmet");

    /**
     * @return the name a log, a diagnostic or a diagram prints the reason by; unique within the reason enum of its
     *         outcome, and a lower-case identifier of the form of an outcome's wire name. The rule is stated in
     *         {@code doc/specification/workflow-dsl/05-primitives.adoc#outcomes-and-reasons}.
     */
    String wireName();

    /**
     * @return the condition kind the reason maps to, or empty when it maps to none. A present kind is a member of
     *         {@link #MAPPABLE_CONDITION_KINDS}, as the closed table in
     *         {@code doc/specification/workflow-dsl/05-primitives.adoc#outcomes-and-reasons} states.
     */
    default Optional<String> conditionKind() {
        return Optional.empty();
    }
}
