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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.build.BuildLogText;

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
        // The documented shape: `npm_` plus 36 alphanumeric characters, the last six a Base62 CRC32
        // checksum (npm Registry API, "Delete npm access token"; GitHub changelog 2021-09-23).
        var token = "npm_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8";

        assertEquals("the token is " + SecretRedactor.REDACTED, redactValue("the token is " + token),
                "the documented npm token shape is redacted");
    }

    @Test
    @DisplayName("user information in a URL keeps the scheme and the user")
    void urlUserInformation() {
        assertAll("URL user information",
                () -> assertEquals("request to https://deploy:" + SecretRedactor.REDACTED + "@registry.example/pkg failed",
                        redactValue("request to https://deploy:s3cr3t-value@registry.example/pkg failed"),
                        "a URL's password is masked and its scheme kept"),
                () -> assertEquals("https://user:" + SecretRedactor.REDACTED + "@host/x",
                        redactValue("https://user:p@ss@host/x"),
                        "a password containing @ is masked whole"));
    }

    @Test
    @DisplayName("the remaining URL user-information shapes are masked (specified example URLs)")
    void urlUserInformationShapes() {
        assertAll("URL user-information shapes",
                () -> assertEquals("https://deploy:" + SecretRedactor.REDACTED + "@registry.example/pkg",
                        redactValue("https://deploy:pa/ss@registry.example/pkg"),
                        "a password holding a raw slash is masked"),
                () -> assertEquals("https://deploy:" + SecretRedactor.REDACTED + "@registry.example/pkg",
                        redactValue("https://deploy:pa?s#s@registry.example/pkg"),
                        "a password holding ? and # is masked"),
                () -> assertEquals("https://me@corp.example:" + SecretRedactor.REDACTED + "@host/p",
                        redactValue("https://me@corp.example:s3cret@host/p"),
                        "a user holding a raw @ keeps user and host"),
                () -> assertEquals("https%3A%2F%2Fuser:" + SecretRedactor.REDACTED + "@host",
                        redactValue("https%3A%2F%2Fuser:secret@host"),
                        "a percent-encoded scheme separator is masked"),
                () -> assertEquals("https://user:" + SecretRedactor.REDACTED + "@host",
                        redactValue("https://user:p%40ss%2Fw0rd@host"),
                        "a percent-encoded password stays masked"));
    }

    @Test
    @DisplayName("URL forms without user information stay readable (specified controls)")
    void urlUserInformationNegativeControls() {
        assertAll("URL user-information negative controls",
                () -> assertEquals("https://ghp_token@host/x", redactValue("https://ghp_token@host/x"),
                        "the single-user form stays readable"),
                () -> assertEquals("scheme://host:8080/path", redactValue("scheme://host:8080/path"),
                        "a host and port without @ stay readable"),
                () -> assertEquals("see src/main:java/A.java", redactValue("see src/main:java/A.java"),
                        "a path with a colon stays readable"),
                () -> assertEquals("the build: user said ok @ noon", redactValue("the build: user said ok @ noon"),
                        "a prose sentence stays readable"),
                () -> assertEquals("https://host?next=user:password@other/path",
                        redactValue("https://host?next=user:password@other/path"),
                        "ADR 006's query control stays readable"),
                () -> assertEquals("https://host/p?at=a:b@c", redactValue("https://host/p?at=a:b@c"),
                        "a query with a colon and @ stays readable"));
    }

    @Test
    @DisplayName("a host:port path followed by a raw @ is read as user information (the stated cost)")
    void urlUserInformationFalsePositiveCost() {
        assertEquals("https://host:" + SecretRedactor.REDACTED + "@mention",
                redactValue("https://host:8080/path@mention"),
                "the port and path before a later @ are masked");
    }

    @Test
    @DisplayName("a scheme-like run as long as a bounded line is redacted to itself")
    void urlUserInformationLongSchemeRun() {
        // a.a.a… is a run of scheme characters: every position could start a scheme, the cost case of the pattern
        var run = "a.".repeat(BuildLogText.MAX_LINE_LENGTH / 2 + 1).substring(0, BuildLogText.MAX_LINE_LENGTH);
        var withSeparator = run + "://";

        assertAll("long scheme-like runs",
                () -> assertEquals(run, redactValue(run), "a long scheme-character run stays as it is"),
                () -> assertEquals(withSeparator, redactValue(withSeparator),
                        "the same run followed by :// stays as it is"));
    }

    @Test
    @DisplayName("a credential flag with a space is redacted and keeps the flag")
    void spacedCredentialFlags() {
        assertAll("spaced credential flags",
                () -> assertEquals("npm login --password " + SecretRedactor.REDACTED,
                        redactValue("npm login --password hunter22"), "a spaced password value is masked"),
                () -> assertEquals("publish --token " + SecretRedactor.REDACTED,
                        redactValue("publish --token abc12345"), "a spaced token value is masked"),
                () -> assertEquals("publish --token \"" + SecretRedactor.REDACTED + "\"",
                        redactValue("publish --token \"abcd1234\""), "a double-quoted token value is masked"),
                () -> assertEquals("npm login --password '" + SecretRedactor.REDACTED + "'",
                        redactValue("npm login --password 'hunter22'"), "a single-quoted password is masked"),
                () -> assertEquals("publish --token \"" + SecretRedactor.REDACTED + "\"",
                        redactValue("publish --token \"correct horse battery staple\""),
                        "a quoted value with spaces is masked whole"),
                () -> assertEquals("npm login --password \"" + SecretRedactor.REDACTED,
                        redactValue("npm login --password \"correct horse"),
                        "an unclosed quoted value is masked to the line end"),
                () -> assertEquals("publish --token \"" + SecretRedactor.REDACTED,
                        redactValue("publish --token \"abc'def"),
                        "an unclosed quote masks the rest of the line"),
                () -> assertEquals("publish --token '" + SecretRedactor.REDACTED,
                        redactValue("publish --token 'abc\"def"),
                        "an unclosed single quote masks the rest of the line"),
                () -> assertEquals("publish --token \"" + SecretRedactor.REDACTED + "\"",
                        redactValue("publish --token \"abc\\\"def ghi\""),
                        "an escaped quote inside a value is not its close"),
                () -> assertEquals("npm login --password " + SecretRedactor.REDACTED,
                        redactValue("npm login --password abcdef,secret"),
                        "a comma inside an unquoted value is masked with it"),
                () -> assertEquals("publish --token " + SecretRedactor.REDACTED,
                        redactValue("publish --token abc"), "a short unquoted value is masked"),
                () -> assertEquals("--token \"" + SecretRedactor.REDACTED + "\" --name \"x\"",
                        redactValue("--token \"abc\" --name \"x\""),
                        "only the credential's quoted value is masked"));
    }

    @Test
    @DisplayName("a credential flag is matched whatever its case")
    void credentialFlagCaseInsensitive() {
        assertAll("case-insensitive credential flags",
                () -> assertEquals("--PASSWORD " + SecretRedactor.REDACTED, redactValue("--PASSWORD x"),
                        "an upper-case password flag is masked"),
                () -> assertEquals("publish --Token " + SecretRedactor.REDACTED,
                        redactValue("publish --Token abcd1234"), "a mixed-case token flag is masked"));
    }

    @ParameterizedTest(name = "--{0}")
    @ValueSource(strings = {"auth-token", "api-key", "apikey", "access-token", "client-secret", "otp"})
    @DisplayName("each added credential flag name is masked (the specified name list)")
    void addedCredentialFlagNames(String name) {
        var value = Generators.letterStrings(4, 16).next();

        assertEquals("cli --" + name + " " + SecretRedactor.REDACTED, redactValue("cli --" + name + " " + value),
                "the value of an added credential flag is masked");
    }

    @Test
    @DisplayName("a flag value holding a semicolon, a quote or a line end is masked whole")
    void credentialFlagValueShapes() {
        assertAll("credential flag value shapes",
                () -> assertEquals("--password " + SecretRedactor.REDACTED, redactValue("--password pa;ss"),
                        "a semicolon inside a value is masked with it"),
                () -> assertEquals("--password '" + SecretRedactor.REDACTED + "'",
                        redactValue("--password 'abc\"def'"), "a quoted value holding the other quote is masked"),
                () -> assertEquals("--password " + SecretRedactor.REDACTED, redactValue("--password abc\"def"),
                        "an unquoted value holding a quote is masked"),
                () -> assertEquals("--password " + SecretRedactor.REDACTED, redactValue("--password abc'def"),
                        "an unquoted value holding an apostrophe is masked"),
                () -> assertEquals("--password \"" + SecretRedactor.REDACTED + "\" done",
                        redactValue("--password \"first line\nsecond line\" done"),
                        "a quoted value across one line end is masked"));
    }

    @Test
    @DisplayName("flags and prose outside the credential flag list stay readable")
    void credentialFlagNegativeControls() {
        assertAll("credential flag negative controls",
                () -> assertEquals("--password-file notes.txt", redactValue("--password-file notes.txt"),
                        "a different flag is not a credential flag"),
                () -> assertEquals("publish --token\nnext line", redactValue("publish --token\nnext line"),
                        "a token flag at the end of a line stays"),
                () -> assertEquals("store the password in a vault", redactValue("store the password in a vault"),
                        "prose naming the word password stays"),
                () -> assertEquals("--key value", redactValue("--key value"),
                        "a flag outside the closed list stays"));
    }

    @Test
    @DisplayName("a usage line naming a credential flag masks its placeholder (the stated cost)")
    void credentialFlagFalsePositiveCost() {
        assertEquals("usage: publish --token " + SecretRedactor.REDACTED + " the access token",
                redactValue("usage: publish --token <token> the access token"),
                "the word after a credential flag is masked");
    }

    @Test
    @DisplayName("a PEM private key block is redacted")
    void pemPrivateKeyBlock() {
        var block = String.join("\n", "-----BEGIN RSA PRIVATE KEY-----", "MIIEowIBAAKCAQEA7v0x",
                "-----END RSA PRIVATE KEY-----");

        assertEquals("key:\n" + SecretRedactor.REDACTED, redactValue("key:\n" + block),
                "a PEM private-key block is replaced whole");
    }

    @Test
    @DisplayName("an unclosed PEM private-key block masks the rest")
    void unclosedPemPrivateKeyBlock() {
        var log = "key:\n-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEA7v0x";

        assertEquals("key:\n" + SecretRedactor.REDACTED, redactValue(log),
                "a block without its END line is masked to the end");
    }

    @Test
    @DisplayName("shapes that merely resemble a credential stay readable")
    void shapeNegativeControls() {
        assertAll("shape negative controls",
                () -> assertEquals("see https://github.com/org/repo",
                        redactValue("see https://github.com/org/repo"), "a URL without user information stays"),
                () -> assertEquals("--password-file notes.txt", redactValue("--password-file notes.txt"),
                        "a different flag is not a credential flag"),
                () -> assertEquals("the word token appears here", redactValue("the word token appears here"),
                        "prose carrying the word token stays readable"),
                () -> assertEquals("npm_" + "A".repeat(20), redactValue("npm_" + "A".repeat(20)),
                        "an npm_ string shorter than a token stays"),
                () -> assertEquals("the RSA private key is rotated daily",
                        redactValue("the RSA private key is rotated daily"),
                        "prose naming a key type stays readable"),
                () -> assertEquals("https://host?next=user:password@other/path",
                        redactValue("https://host?next=user:password@other/path"),
                        "a query is not URL user information"),
                () -> assertEquals("https://host/#user:password@other",
                        redactValue("https://host/#user:password@other"),
                        "a fragment is not URL user information"));
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
        var everyShape = redactValue("""
                npm_A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8
                https://deploy:s3cr3t-value@registry.example/pkg --password hunter22
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA7v0x
                -----END RSA PRIVATE KEY-----""");

        assertAll("redaction is idempotent",
                () -> assertEquals(once, SecretRedactor.redactText(once).orElseThrow(),
                        "redacting a redacted assignment changes nothing"),
                () -> assertEquals(everyShape, SecretRedactor.redactText(everyShape).orElseThrow(),
                        "redacting the new shapes twice changes nothing"));
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
