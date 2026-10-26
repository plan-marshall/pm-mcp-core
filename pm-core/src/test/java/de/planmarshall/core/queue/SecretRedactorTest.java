/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The masker removes the value of an explicit credential-shaped assignment and keeps every other text
 * (Store Schemas § Activity Journal: every text and target is masked).
 */
@DisplayName("Secret masker")
class SecretMaskerTest {

    @Test
    @DisplayName("an authorization header keeps its label and loses its value")
    void authorizationHeader() {
        assertEquals("Authorization: Bearer " + SecretMasker.REDACTED,
                SecretMasker.mask("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"));
    }

    @Test
    @DisplayName("a bearer value is masked whatever the header")
    void bearer() {
        assertEquals("sent Bearer " + SecretMasker.REDACTED, SecretMasker.mask("sent Bearer abcdefghijkl"));
    }

    @Test
    @DisplayName("a credential-shaped assignment is masked and keeps the label")
    void assignments() {
        assertAll(
                () -> assertEquals("api_key=" + SecretMasker.REDACTED,
                        SecretMasker.mask("api_key=abcd1234")),
                () -> assertEquals("password: " + SecretMasker.REDACTED,
                        SecretMasker.mask("password: hunter22")),
                () -> assertEquals("token=\"" + SecretMasker.REDACTED + "\"",
                        SecretMasker.mask("token=\"abcdefgh\"")));
    }

    @Test
    @DisplayName("a provider token shape is masked")
    void tokenShape() {
        assertEquals("the key is " + SecretMasker.REDACTED,
                SecretMasker.mask("the key is sk-proj-abcdefghijklmnop"));
    }

    @Test
    @DisplayName("prose, a path and a commit id are not secrets")
    void keepsOrdinaryText() {
        assertAll(
                () -> assertEquals("the token budget is 30000", SecretMasker.mask("the token budget is 30000")),
                () -> assertEquals("src/main/java/A.java", SecretMasker.mask("src/main/java/A.java")),
                () -> assertEquals("f4a1bd07f480bbf04c18545192e0690fbca03563",
                        SecretMasker.mask("f4a1bd07f480bbf04c18545192e0690fbca03563")),
                () -> assertEquals("", SecretMasker.mask("")),
                () -> assertNull(SecretMasker.mask((String) null)));
    }

    @Test
    @DisplayName("masking is idempotent")
    void idempotent() {
        var once = SecretMasker.mask("password=hunter22");

        assertEquals(once, SecretMasker.mask(once));
    }

    @Test
    @DisplayName("an entry's text, tool target and link target are masked; its path is not")
    void entry() {
        var at = Instant.parse("2026-10-26T00:00:00Z");
        var call = ActivityEntry.builder()
                .seq(1)
                .attempt(1)
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_CALL)
                .tool(new ActivityEntry.Tool("bash", "curl -H 'Authorization: Bearer abcdefghijkl'"))
                .at(at)
                .build();
        var changed = ActivityEntry.builder()
                .seq(2)
                .attempt(1)
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.FILE_CHANGED)
                .path("src/secret/config.properties")
                .at(at)
                .build();

        assertAll(
                () -> assertEquals("curl -H 'Authorization: Bearer " + SecretMasker.REDACTED + "'",
                        SecretMasker.mask(call).tool().target()),
                () -> assertEquals("src/secret/config.properties", SecretMasker.mask(changed).path()));
    }
}
