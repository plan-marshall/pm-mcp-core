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

import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.JsonNode;

/**
 * One named parameter of a hypermedia link (Hypermedia Format, Link Grammar): its name, its declared type and, for the
 * object-valued type {@code schema[<schema_name>]}, the registered JSON Schema the value is validated against. The
 * composer derives the writing skill from the schemas, never from a role artifact.
 *
 * @param name   the parameter name
 * @param type   the declared type, for example {@code text}, {@code list<text>} or {@code schema[outline-record]}
 * @param schema the registered schema of a {@code schema[…]} parameter; {@code null} for every other type
 * @since 0.1
 */
public record LinkParameter(String name, String type, @Nullable JsonNode schema) {

    /** @throws NullPointerException if the name or the type is {@code null} */
    public LinkParameter {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
    }

    /** @return whether the parameter takes free text */
    public boolean isText() {
        return "text".equals(type) || "list<text>".equals(type);
    }

    /** @return whether the parameter takes a named parameter schema */
    public boolean isSchema() {
        return schema != null;
    }
}
