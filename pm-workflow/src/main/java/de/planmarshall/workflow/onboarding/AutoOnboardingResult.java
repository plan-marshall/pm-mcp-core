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

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;

/**
 * The automatic run's result, {@code auto.result} (CLI &amp; Security § Automatic Onboarding): every value the run
 * decided, one item each, and the items it could not decide, which the operator answered in the run.
 * <p>
 * Each item opens its aspect as a later run ({@link OnboardingRouter#change}), so the result is the operator's entry to
 * changing a single aspect without running the whole onboarding again.
 *
 * @param items the values of the run, in the order the aspects and their keys were decided
 * @since 0.1
 */
@Builder
public record AutoOnboardingResult(List<OnboardingProposal> items) {

    /**
     * Keeps the items unmodifiable and refuses a {@code null} list.
     *
     * @param items the values of the run
     * @throws NullPointerException if {@code items} or one of its elements is {@code null}
     */
    public AutoOnboardingResult {
        Objects.requireNonNull(items, "items");
        items.forEach(item -> Objects.requireNonNull(item, "item"));
        items = List.copyOf(items);
    }

    /**
     * @return an empty result, of a run that decided nothing
     */
    public static AutoOnboardingResult empty() {
        return builder().items(List.of()).build();
    }

    /**
     * @return the values the run decided itself
     */
    public List<OnboardingProposal> decided() {
        return items.stream().filter(item -> !item.asked()).toList();
    }

    /**
     * @return the items the run could not decide and asked the operator for
     */
    public List<OnboardingProposal> undecided() {
        return items.stream().filter(OnboardingProposal::asked).toList();
    }

    /**
     * @param aspect the aspect
     * @return the items of one aspect, in the order they were decided
     */
    public List<OnboardingProposal> of(OnboardingAspect aspect) {
        Objects.requireNonNull(aspect, "aspect");
        return items.stream().filter(item -> item.aspect() == aspect).toList();
    }

    /**
     * @param aspect the aspect
     * @param key    the key of the value inside the aspect
     * @return the value the run answered for the key; empty where the run recorded none
     */
    public Optional<String> value(OnboardingAspect aspect, String key) {
        Objects.requireNonNull(key, "key");
        return of(aspect).stream().filter(item -> item.key().equals(key)).map(OnboardingProposal::value).findFirst();
    }
}
