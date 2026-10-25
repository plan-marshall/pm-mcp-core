/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.util.List;
import java.util.Objects;

/**
 * The launch prompt the server composed, with its digest and the skills it delivered in-band (PM-SKILL-4, Job Record):
 * the digest is the job record's {@code prompt_digest}, and the delivered skills start the generation's delivery
 * record. The shared prefix is the part every launch of a role shares — the shim paragraph, the core conduct skill, the
 * task-protocol skill and the role skill with its further skills — and nothing that varies per launch precedes it.
 *
 * @param text         the prompt file's text
 * @param digest       the SHA-256 of the UTF-8 text
 * @param delivered    the skills delivered in-band, in the order they appear
 * @param prefixLength the length of the shared prefix in characters
 * @since 0.1
 */
public record LaunchPrompt(String text, String digest, List<ResolvedSkill> delivered, int prefixLength) {

    /** Copies the delivered skills and refuses a prefix outside the text. */
    public LaunchPrompt {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(digest, "digest");
        delivered = List.copyOf(Objects.requireNonNull(delivered, "delivered"));
        if (prefixLength < 0 || prefixLength > text.length()) {
            throw new IllegalArgumentException("The shared prefix lies outside the prompt: " + prefixLength);
        }
    }

    /** @return the part every launch of the role shares byte for byte */
    public String sharedPrefix() {
        return text.substring(0, prefixLength);
    }

    /** @return the part that varies per launch, after the shared prefix */
    public String perLaunch() {
        return text.substring(prefixLength);
    }
}
