/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The absent case of the response components this conversion changed: a {@code null} input is the absence that
 * {@code Optional.empty()} carried before, and the accessor returns it unchanged.
 *
 * @since 0.1
 */
class AbsenceContractsTest {

    @Test
    @DisplayName("a response without etag, next link or reset keeps all three null")
    void responseWithoutOptionalParts() {
        var response = new Response(Response.Outcome.OK, 200, "body", null, null, null, "detail");

        assertAll(
                () -> assertNull(response.etag(), "a response without an etag is absent"),
                () -> assertNull(response.next(), "a response without a next link is absent"),
                () -> assertNull(response.resetAt(), "a response without a reset is absent"));
    }

    @Test
    @DisplayName("a non-ok result without a value or reset keeps both null")
    void resultWithoutValue() {
        var result = new Result<String>(Result.Outcome.NOT_FOUND, null, true, null, "missing");

        assertAll(
                () -> assertNull(result.value(), "a non-ok result carries no value"),
                () -> assertNull(result.resetAt(), "a non-rate-limited result has no reset"));
    }
}
