/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * A consent the operator gave with {@code yes_remember} in a project's onboarding before the project had its id (Store
 * Schemas § Consent Before Acting; Credentials &amp; Enrolment § Onboarding a Project as One Process): the hold is kept
 * with the onboarding's entry and written to {@code consents.json} under the confirmed {@code project_id} in the
 * transaction of {@code project.enrol}. An onboarding that does not reach its enrolment remembers nothing, because the
 * hold lives and dies with its entry and is never a store entry of its own.
 * <p>
 * Only a project-scoped kind is held: the first checkout consents are {@code inspect_checkout} and
 * {@code run_checkout_scripts}, and a machine kind is asked with the machine's id known.
 *
 * @param kind      the project-scoped kind the operator allowed
 * @param scripts   the scripts a {@code run_checkout_scripts} consent covers, each with its digest
 * @param grantedAt when the operator granted it
 * @param channel   the channel the answer came through
 * @param deviceId  the paired device, for an answer through the web app; may be {@code null}
 * @since 0.1
 */
@Builder
public record PendingConsent(ConsentKind kind, List<ConsentScript> scripts, Instant grantedAt, Consent.Channel channel,
        @Nullable String deviceId) {

    /**
     * @throws NullPointerException     if a non-nullable component is {@code null}
     * @throws IllegalArgumentException if the kind is not a remembered project kind, or its scripts and its kind do not
     *                                  belong together
     */
    public PendingConsent {
        Objects.requireNonNull(kind, "kind");
        scripts = List.copyOf(scripts);
        Objects.requireNonNull(grantedAt, "grantedAt");
        Objects.requireNonNull(channel, "channel");
        if (!kind.rememberable()) {
            throw new IllegalArgumentException(
                    "The consent kind '%s' is never remembered, so no hold exists for it"
                            .formatted(kind.wireName()));
        }
        if (!kind.projectScoped()) {
            throw new IllegalArgumentException(
                    "Only a project kind is held before the project's id: '%s'".formatted(kind.wireName()));
        }
        if (kind.scriptScoped() && scripts.isEmpty()) {
            throw new IllegalArgumentException(
                    "The hold of run_checkout_scripts lists the scripts it covers");
        }
        if (!kind.scriptScoped() && !scripts.isEmpty()) {
            throw new IllegalArgumentException(
                    "Only run_checkout_scripts covers scripts, and '%s' names %d"
                            .formatted(kind.wireName(), scripts.size()));
        }
        if (deviceId != null && channel != Consent.Channel.WEB) {
            throw new IllegalArgumentException(
                    "Only an answer through web names a paired device: " + deviceId);
        }
    }

    /**
     * Materializes the hold under the project id the operator confirmed.
     *
     * @param projectId the confirmed project id
     * @return the consent to record through {@link ConsentStore#grant(Consent, long)}
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public Consent resolvedFor(String projectId) {
        return kind.scriptScoped()
                ? Consent.checkoutScripts(projectId, scripts, grantedAt, channel, deviceId)
                : Consent.project(kind, projectId, grantedAt, channel, deviceId);
    }
}
