/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.Arrays;
import java.util.Optional;

/**
 * The two topics of a role's corpus items (Model Work § Corpus Topics of a Role): the role's own items ({@code own}),
 * whose {@code role} field names the role, and the generic items ({@code generic}), whose {@code role} field is the
 * generic worker.
 *
 * @since 0.1
 */
public enum Topic {

    /** The items whose {@code role} field names the graded role. */
    OWN("own"),

    /** The generic items every role of the classes {@code writer} and {@code read_only} shares. */
    GENERIC("generic");

    private final String wireName;

    Topic(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the topic in a record */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a topic in a document
     * @return the topic; empty if the name is not one
     */
    public static Optional<Topic> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(topic -> topic.wireName.equals(wireName)).findFirst();
    }
}
