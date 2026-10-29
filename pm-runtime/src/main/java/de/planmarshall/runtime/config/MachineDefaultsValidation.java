/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import java.util.Objects;
import java.util.Optional;

/**
 * The machine's own check of its plan defaults (State Catalogue § Plan Defaults, {@code defaults.validate}): only what
 * the machine itself provides is checked, never a repository. A {@code landing.pull-request} needs a forge the machine
 * serves — a pull request is opened on a code-hosting provider, not on the git transport — and {@code landing.main} and
 * {@code landing.files} need none. Whether a base branch is protected, and whether a landing variant fits a project, is
 * the project's own check, made where the project is known; it is never made here, because the machine has no
 * repository.
 *
 * @since 0.1
 */
public final class MachineDefaultsValidation {

    /** The landing variant that opens a pull request. */
    public static final String LANDING_PULL_REQUEST = "landing.pull-request";

    /** The landing variant that commits and pushes on the base branch. */
    public static final String LANDING_MAIN = "landing.main";

    /** The landing variant that writes the changes into the main checkout's working tree. */
    public static final String LANDING_FILES = "landing.files";

    /** The prefix of every message key of a verdict. */
    static final String MESSAGE_KEY_PREFIX = "defaults.validation.";

    private MachineDefaultsValidation() {
    }

    /**
     * One verdict of the machine's check.
     *
     * @param valid      whether the variant fits what the machine provides
     * @param reason     why it does not, or empty
     * @param messageKey the message key of the verdict
     * @since 0.1
     */
    public record Verdict(boolean valid, Optional<String> reason, String messageKey) {

        /** Rejects {@code null} components. */
        public Verdict {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(messageKey, "messageKey");
        }
    }

    /**
     * Checks a landing variant against what the machine provides.
     *
     * @param variant   the landing variant of {@code plan_defaults.landing}
     * @param providers the machine's provider defaults
     * @return the verdict; a pull request without a git hosting provider the machine serves is refused
     */
    public static Verdict landing(String variant, ProviderDefaults providers) {
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(providers, "providers");
        if (LANDING_FILES.equals(variant) || LANDING_MAIN.equals(variant)) {
            return new Verdict(true, Optional.empty(), MESSAGE_KEY_PREFIX + variant + ".ok");
        }
        if (!LANDING_PULL_REQUEST.equals(variant)) {
            return new Verdict(false, Optional.of("not a landing variant: " + variant),
                    MESSAGE_KEY_PREFIX + "unknown");
        }
        if (hostingProvider(providers)) {
            return new Verdict(true, Optional.empty(), MESSAGE_KEY_PREFIX + variant + ".ok");
        }
        return new Verdict(false, Optional.of("no provider the machine serves opens a pull request"),
                MESSAGE_KEY_PREFIX + variant + ".no-provider");
    }

    private static boolean hostingProvider(ProviderDefaults providers) {
        return providers.available(Provider.GITHUB) || providers.available(Provider.GITLAB)
                || providers.available(Provider.FORGEJO);
    }
}
