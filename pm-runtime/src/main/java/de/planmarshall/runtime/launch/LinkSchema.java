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
 * One link an entity offers, with its named parameter schemas (Hypermedia Format, Link Grammar): the target the link
 * follows and the parameters the server validates. The launch and the offer read the writing skill's need from these
 * schemas, so no role artifact can leave the skill out.
 *
 * @param target     the link target, as the entity offers it
 * @param parameters the parameters in declaration order
 * @since 0.1
 */
public record LinkSchema(String target, List<LinkParameter> parameters) {

    /** Copies the parameters. */
    public LinkSchema {
        Objects.requireNonNull(target, "target");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
}
