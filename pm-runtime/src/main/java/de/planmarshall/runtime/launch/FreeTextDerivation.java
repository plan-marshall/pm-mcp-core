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

import de.planmarshall.core.profile.JsonNode;

/**
 * Derives whether a task writes free text from the schemas of the links its entity offers (PM-SKILL-4; Hypermedia
 * Format, Link Grammar): a task writes free text when a link carries a parameter of type {@code text}, or a named
 * parameter schema with a {@code string} field that has neither {@code enum} nor {@code pattern}. The derivation reads
 * the schemas and never a role artifact, so no role can leave the writing skill out; a {@code string} field bounded by
 * an {@code enum} or a {@code pattern} is a closed value and does not trigger it.
 *
 * @since 0.1
 */
public final class FreeTextDerivation {

    private FreeTextDerivation() {
    }

    /**
     * @param links the links the task's entity offers
     * @return whether the task writes free text
     */
    public static boolean writesFreeText(List<LinkSchema> links) {
        Objects.requireNonNull(links, "links");
        for (var link : links) {
            for (var parameter : link.parameters()) {
                if (parameter.isText() || freeStringField(parameter.schema())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * @param schema a registered named parameter schema; {@code null} for a parameter without one
     * @return whether the schema holds a free {@code string} field
     */
    static boolean freeStringField(JsonNode schema) {
        if (schema == null) {
            return false;
        }
        if (isFreeString(schema)) {
            return true;
        }
        for (var member : schema.members().values()) {
            if (freeStringField(member)) {
                return true;
            }
        }
        for (var element : schema.elements()) {
            if (freeStringField(element)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFreeString(JsonNode schema) {
        if (!isStringType(schema) || schema.member("enum").isPresent() || schema.member("pattern").isPresent()) {
            return false;
        }
        return true;
    }

    private static boolean isStringType(JsonNode schema) {
        var type = schema.member("type").orElse(JsonNode.nullValue());
        if (type.isString()) {
            return "string".equals(type.text().orElse(""));
        }
        for (var element : type.elements()) {
            if ("string".equals(element.text().orElse(""))) {
                return true;
            }
        }
        return false;
    }
}
