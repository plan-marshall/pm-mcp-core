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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The parsed {@code instance} header line of a plan instance version (PM-IMPL-5). The input digests are copied into an
 * unmodifiable map that keeps their order.
 *
 * @param planId       the identifier of the plan
 * @param version      the instance version
 * @param event        the composition event that produced the version
 * @param inputDigests the digest of every composition input by name
 * @param json         the header JSON as read
 * @since 0.1
 */
public record InstanceHeader(String planId, int version, String event, Map<String, String> inputDigests, String json) {

    /**
     * Copies the input digests into an unmodifiable map that keeps their order.
     *
     * @param planId       the identifier of the plan
     * @param version      the instance version
     * @param event        the composition event
     * @param inputDigests the digest of every composition input by name
     * @param json         the header JSON as read
     */
    public InstanceHeader {
        inputDigests = Collections.unmodifiableMap(new LinkedHashMap<>(inputDigests));
    }
}
