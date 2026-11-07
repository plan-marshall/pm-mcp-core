/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.test;

import java.nio.charset.StandardCharsets;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * JSON documents for the tests of the launch: a JSON schema a test writes as text, parsed into its node, shared so
 * that no test class copies the parse.
 */
public final class JsonDocuments {

    private JsonDocuments() {
    }

    /**
     * @param document the JSON schema document as text
     * @return the parsed document, reported under the name {@code schema}
     */
    public static JsonNode schema(String document) {
        return JsonDocument.parse(document.getBytes(StandardCharsets.UTF_8), "schema");
    }
}
