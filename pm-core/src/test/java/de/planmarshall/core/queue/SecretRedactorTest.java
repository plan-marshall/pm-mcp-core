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
 * The redactor removes the value of an explicit credential-shaped assignment and keeps every other text
 * (Store Schemas § Activity Journal: every text and target is redacted; Job Runtime § Pipeline names `SecretRedactor`).
 */
@DisplayName("Secret redactor")
class SecretRedactorTest {

    @Test
    @DisplayName("an authorization header keeps its label and loses its value")
    void authorizationHeader() {
        assertEquals("Authorization: Bearer " + SecretRedactor.REDACTED,
                SecretRedactor.redactText("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"));
    }

    @Test
    @DisplayName("a bearer value is redacted whatever the header")
    void bearer() {
        assertEquals("sent Bearer " + SecretRedactor.REDACTED, SecretRedactor.redactText("sent Bearer abcdefghijkl"));
    }

    @Test
    @DisplayName("a credential-shaped assignment is redacted and keeps the label")
    void assignments() {
        assertAll(
                () -> assertEquals("api_key=" + SecretRedactor.REDACTED,
                        SecretRedactor.redactText("api_key=abcd1234")),
                () -> assertEquals("password: " + SecretRedactor.REDACTED,
                        SecretRedactor.redactText("password: hunter22")),
                () -> assertEquals("token=\"" + SecretRedactor.REDACTED + "\"",
                        SecretRedactor.redactText("token=\"abcdefgh\"")));
    }

    @Test
    @DisplayName("a provider token shape is redacted")
    void tokenShape() {
        assertEquals("the key is " + SecretRedactor.REDACTED,
                SecretRedactor.redactText("the key is sk-proj-abcdefghijklmnop"));
    }

    @Test
    @DisplayName("prose, a path and a commit id are not secrets")
    void keepsOrdinaryText() {
        assertAll(
                () -> assertEquals("the token budget is 30000",
                        SecretRedactor.redactText("the token budget is 30000")),
                () -> assertEquals("src/main/java/A.java", SecretRedactor.redactText("src/main/java/A.java")),
                () -> assertEquals("f4a1bd07f480bbf04c18545192e0690fbca03563",
                        SecretRedactor.redactText("f4a1bd07f480bbf04c18545192e0690fbca03563")),
                () -> assertEquals("", SecretRedactor.redactText("")),
                () -> assertNull(SecretRedactor.redactText((String) null)));
    }

    @Test
    @DisplayName("redacting is idempotent")
    void idempotent() {
        var once = SecretRedactor.redactText("password=hunter22");

        assertEquals(once, SecretRedactor.redactText(once));
    }

    @Test
    @DisplayName("an entry's text, tool target and link target are redacted; its path is not")
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
                () -> assertEquals("curl -H 'Authorization: Bearer " + SecretRedactor.REDACTED + "'",
                        SecretRedactor.redact(call).tool().target()),
                () -> assertEquals("src/secret/config.properties", SecretRedactor.redact(changed).path()));
    }
}
