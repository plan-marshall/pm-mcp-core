/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed outcome of a call to a code analysis provider (PM-IMPL-1), for both operations {@code analysis.status}
 * and {@code analysis.issues}. Every outcome other than {@link #OK} is a transport outcome: it carries no value and
 * counts as {@code undecidable} in the wait. An unregistered provider is not an outcome; the registry answers it as
 * empty.
 *
 * @since 0.1
 */
public enum AnalysisOutcome {
    /** The provider answered and the answer was read. */
    OK("ok"),
    /** The provider does not accept the credential. */
    AUTH_FAILED("auth_failed"),
    /** The provider accepts the credential, which lacks the permission for the operation. */
    PERMISSION_DENIED("permission_denied"),
    /** The provider gave no usable answer: transport failure, server error or unexpected status. */
    PROVIDER_UNAVAILABLE("provider_unavailable"),
    /** The provider answered with a response that cannot be read. */
    MALFORMED_RESPONSE("malformed_response");

    private final String wireName;

    AnalysisOutcome(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the value on the wire */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a value on the wire
     * @return the value of that name; empty if no value has it
     */
    public static Optional<AnalysisOutcome> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
    }
}
