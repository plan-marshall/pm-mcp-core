/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * One input a unit declares in its frontmatter (PM-WF-16): its name, its closed type, whether its absence is legal, the
 * compiled default it falls back to, the configuration key that supplies it, and the description shown to a caller.
 * <p>
 * An input is addressed inside the unit as {@code input.<name>}; the owner of an input is the unit whose frontmatter
 * declares it. A call binds it explicitly, {@code callee_input = caller_value}, checked by Rule 6 at build time. The
 * value is resolved in the order the call site bound it, the value of the {@link #fallback()} configuration key, the
 * {@link #defaultValue()}, and absence when {@link #optional()} is set; an input for which none of the four applies is
 * required and a call site that omits it is refused.
 *
 * @param name         the name of the input, a {@code param_name} and never a reserved keyword
 * @param type         the closed type of the input
 * @param optional     whether the source form carried the trailing {@code ?}, so an absent input is legal
 * @param defaultValue the compiled default, {@code null} when the declaration carries none
 * @param fallback     the configuration key that supplies the value when the call site binds none, {@code null} when
 *                     the declaration carries none
 * @param description  what an absent input means and what a caller may assume, 1 to 500 characters; {@code null} when
 *                     the declaration carries none
 * @param location     where the {@code input} line was read
 * @since 0.1
 */
@Builder
public record InputDecl(String name, InputType type, boolean optional, @Nullable CallValue defaultValue,
        @Nullable String fallback, @Nullable String description, SourceLocation location) {

    /**
     * @return whether a call site must bind the input: it carries neither the optional form nor a default nor a
     *         fallback
     */
    public boolean required() {
        return !optional && defaultValue == null && fallback == null;
    }
}
