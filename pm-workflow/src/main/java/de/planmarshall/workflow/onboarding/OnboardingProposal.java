/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.onboarding;

import java.util.Objects;

import de.planmarshall.core.config.OnboardingAspect;
import lombok.Builder;

/**
 * One value the automatic run decided, or the operator was asked for (CLI &amp; Security § Automatic Onboarding, the
 * item schema of {@code auto.result}): the aspect, the key, the value, the reason from the closed list and the check line
 * that confirmed it.
 * <p>
 * An item whose reason is {@link ProposalReason#ASKED} is one the run could not decide by itself: the proposal was
 * incomplete — a login, a secret, no harness found, the model ids of a harness that cannot list them, a failed check —
 * and the operator answered it, after which the run went on.
 *
 * @param aspect the aspect the value belongs to
 * @param key    the key of the value inside the aspect, for example {@code harness.default}
 * @param value  the value the run decided
 * @param reason why the run decided it, or that the operator was asked
 * @param check  the check line that confirmed it, for example one ending in ✓ or ✗
 * @since 0.1
 */
@Builder
public record OnboardingProposal(OnboardingAspect aspect, String key, String value, ProposalReason reason,
String check) {

    /**
     * Refuses a missing component.
     *
     * @param aspect the aspect
     * @param key    the key of the value
     * @param value  the value
     * @param reason the reason
     * @param check  the check line that confirmed it
     * @throws NullPointerException if a component is {@code null}
     */
    public OnboardingProposal {
        Objects.requireNonNull(aspect, "aspect");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(check, "check");
    }

    /**
     * @return whether the run could not decide the item and asked the operator
     */
    public boolean asked() {
        return reason == ProposalReason.ASKED;
    }
}
