/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The engine half of the machine's automatic onboarding and of its aspects (PM-WF-26, State Catalogue § Machine
 * Onboarding and § The Onboarding's Aspects, CLI &amp; Security § Automatic Onboarding).
 * <p>
 * The units themselves ({@code onboarding.pmwf}, the aspect sub-workflows, {@code auto.consent}, {@code auto.result}) are
 * resources of {@code plan-marshall-mcp}; this package is what the run's engine half is in this repository and is
 * provable over fixtures without a workflow:
 * <ul>
 * <li>the proposal and result vocabulary: {@link de.planmarshall.workflow.onboarding.OnboardingProposal} with its
 * {@link de.planmarshall.workflow.onboarding.ProposalReason} (the closed list of the specification) and
 * {@link de.planmarshall.workflow.onboarding.AutoOnboardingResult}, which answers the decided values and the items the
 * run could not decide;</li>
 * <li>the aspect order of the guided and the automatic run,
 * {@link de.planmarshall.core.config.OnboardingAspect}, kept in {@code core.config} because the machine's settings of
 * {@code pm-runtime} name the same aspects;</li>
 * <li>the routing of {@code onboard.next}, {@link de.planmarshall.workflow.onboarding.OnboardingRouter}: the aspects in
 * their fixed order, the automatic run's result, the settings, and a {@code change} on the result that calls one aspect
 * as a later run and returns to the result.</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.workflow.onboarding;

import org.jspecify.annotations.NullMarked;
