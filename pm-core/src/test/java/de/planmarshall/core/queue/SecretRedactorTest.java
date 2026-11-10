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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The redactor removes the value of an explicit credential-shaped assignment and keeps every other text
 * (Store Schemas § Activity Journal: every text and target is redacted; Job Runtime § Pipeline names `SecretRedactor`).
 */
@EnableGeneratorController
@DisplayName("Secret redactor")
class SecretRedactorTest {

    private static String redactValue(String value) {
        return SecretRedactor.redactText(value).orElseThrow();
    }

    @Test
    @DisplayName("an authorization header keeps its label and loses its value")
    void authorizationHeader() {
        assertEquals("Authorization: Bearer " + SecretRedactor.REDACTED,
                redactValue("Authorization: Bearer eyJhbGciOiJIUzI1NiJ9"));
    }

    @Test
    @DisplayName("a bearer value is redacted whatever the header")
    void bearer() {
        assertEquals("sent Bearer " + SecretRedactor.REDACTED, redactValue("sent Bearer abcdefghijkl"));
    }

    @Test
    @DisplayName("a credential-shaped assignment is redacted and keeps the label")
    void assignments() {
        assertAll(
                () -> assertEquals("api_key=" + SecretRedactor.REDACTED,
                        redactValue("api_key=abcd1234")),
                () -> assertEquals("password: " + SecretRedactor.REDACTED,
                        redactValue("password: hunter22")),
                () -> assertEquals("token=\"" + SecretRedactor.REDACTED + "\"",
                        redactValue("token=\"abcdefgh\"")));
    }

    @Test
    @DisplayName("a credential in an environment-variable name is redacted")
    void environmentVariables() {
        assertAll(
                () -> assertEquals("DB_PASSWORD=" + SecretRedactor.REDACTED,
                        redactValue("DB_PASSWORD=hunter22")),
                () -> assertEquals("export GITHUB_TOKEN=" + SecretRedactor.REDACTED,
                        redactValue("export GITHUB_TOKEN=abc12345")),
                () -> assertEquals("MY_API_KEY=" + SecretRedactor.REDACTED,
                        redactValue("MY_API_KEY=abcd1234")),
                () -> assertEquals("AWS_SECRET_ACCESS_KEY=" + SecretRedactor.REDACTED,
                        redactValue("AWS_SECRET_ACCESS_KEY=abcd1234")));
    }

    @Test
    @DisplayName("a quoted key and a camelCase label are redacted")
    void quotedAndCamelCase() {
        assertAll(
                () -> assertEquals("{\"password\": \"" + SecretRedactor.REDACTED + "\"}",
                        redactValue("{\"password\": \"hunter22\"}")),
                () -> assertEquals("authToken=" + SecretRedactor.REDACTED,
                        redactValue("authToken=abcd1234")),
                () -> assertEquals("clientSecret: " + SecretRedactor.REDACTED,
                        redactValue("clientSecret: abcd1234")));
    }

    @Test
    @DisplayName("ordinary build text that starts with pass is not a credential")
    void passIsNarrow() {
        assertAll(
                () -> assertEquals("passed: 7780", redactValue("passed: 7780")),
                () -> assertEquals("passthrough=true", redactValue("passthrough=true")),
                () -> assertEquals("passes=1000", redactValue("passes=1000")));
    }

    @Test
    @DisplayName("a provider token shape is redacted")
    void tokenShape() {
        assertEquals("the key is " + SecretRedactor.REDACTED,
                redactValue("the key is sk-proj-abcdefghijklmnop"));
    }

    @Test
    @DisplayName("an npm access token is redacted")
    void npmAccessToken() {
        var token = "npm_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8";

        assertEquals("the token is " + SecretRedactor.REDACTED, redactValue("the token is " + token));
    }

    @Test
    @DisplayName("user information in a URL keeps the scheme and the user")
    void urlUserInformation() {
        assertEquals("request to https://deploy:" + SecretRedactor.REDACTED + "@registry.example/pkg failed",
                redactValue("request to https://deploy:s3cr3t-value@registry.example/pkg failed"));
    }

    @Test
    @DisplayName("a credential flag with a space is redacted and keeps the flag")
    void spacedCredentialFlags() {
        assertAll(
                () -> assertEquals("npm login --password " + SecretRedactor.REDACTED,
                        redactValue("npm login --password hunter22")),
                () -> assertEquals("publish --token " + SecretRedactor.REDACTED,
                        redactValue("publish --token abc12345")));
    }

    @Test
    @DisplayName("a PEM private key block is redacted")
    void pemPrivateKeyBlock() {
        var block = String.join("\n", "-----BEGIN RSA PRIVATE KEY-----", "MIIEowIBAAKCAQEA7v0x",
                "-----END RSA PRIVATE KEY-----");

        assertEquals("key:\n" + SecretRedactor.REDACTED, redactValue("key:\n" + block));
    }

    @Test
    @DisplayName("shapes that merely resemble a credential stay readable")
    void shapeNegativeControls() {
        assertAll(
                () -> assertEquals("see https://github.com/org/repo",
                        redactValue("see https://github.com/org/repo")),
                () -> assertEquals("--password-file notes.txt", redactValue("--password-file notes.txt")),
                () -> assertEquals("the word token appears here", redactValue("the word token appears here")),
                () -> assertEquals("npm_" + "A".repeat(20), redactValue("npm_" + "A".repeat(20))),
                () -> assertEquals("-----BEGIN RSA PRIVATE KEY----- without an end",
                        redactValue("-----BEGIN RSA PRIVATE KEY----- without an end")));
    }

    @Test
    @DisplayName("prose, a path and a commit id are not secrets")
    void keepsOrdinaryText() {
        assertAll(
                () -> assertEquals("the token budget is 30000",
                        redactValue("the token budget is 30000")),
                () -> assertEquals("src/main/java/A.java", redactValue("src/main/java/A.java")),
                () -> assertEquals("f4a1bd07f480bbf04c18545192e0690fbca03563",
                        redactValue("f4a1bd07f480bbf04c18545192e0690fbca03563")),
                () -> assertEquals("", redactValue("")),
                () -> assertTrue(SecretRedactor.redactText(null).isEmpty(), "Null input yields an empty result"));
    }

    @Test
    @DisplayName("redacting is idempotent")
    void idempotent() {
        var once = redactValue("password=hunter22");

        assertEquals(once, SecretRedactor.redactText(once).orElseThrow());
    }

    @Test
    @DisplayName("an entry's text, tool target and link target are redacted; its path is not")
    void entry() {
        var at = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var seq = Generators.integers(1, 100).next();
        var attempt = Generators.integers(1, 10).next();
        var call = ActivityEntry.builder()
                .seq(seq)
                .attempt(attempt)
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_CALL)
                .tool(new ActivityEntry.Tool("bash", "curl -H 'Authorization: Bearer abcdefghijkl'"))
                .at(at)
                .build();
        var changed = ActivityEntry.builder()
                .seq(seq + 1)
                .attempt(attempt)
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
