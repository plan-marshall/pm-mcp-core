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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Duration;
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
    @DisplayName("a credentialed URL followed by an @scope segment masks its host (the stated cost, specified URL)")
    void urlUserInformationLaterAtFalsePositiveCost() {
        assertEquals("https://user:" + SecretRedactor.REDACTED + "@scope/pkg",
                redactValue("https://user:secret@registry.example/@scope/pkg"),
                "the secret runs to the last @ and masks the host");
    }

    @Test
    @DisplayName("a scheme-and-colon run without @ as long as a bounded line is redacted to itself")
    void urlUserInformationLongRunWithoutAt() {
        // x://y: repeated without any @ is the worst case of the pattern: every scheme start scans back to it
        var run = "x://y:".repeat(BuildLogText.MAX_LINE_LENGTH / 6 + 1).substring(0, BuildLogText.MAX_LINE_LENGTH);

        assertEquals(run, redactValue(run), "a long run without any @ stays as it is");
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
    @DisplayName("an empty user in a URL is masked (the Azure DevOps and credential-helper form)")
    void urlUserInformationEmptyUser() {
        assertAll("URL with an empty user",
                () -> assertEquals("clone https://:" + SecretRedactor.REDACTED + "@host/repo",
                        redactValue("clone https://:token-value@host/repo"),
                        "an empty user does not leave the secret readable"),
                () -> assertEquals("https%3A%2F%2F:" + SecretRedactor.REDACTED + "@host",
                        redactValue("https%3A%2F%2F:token-value@host"),
                        "the percent-encoded separator accepts an empty user too"));
    }

    @Test
    @DisplayName("an unquoted assignment shorter than four characters stays readable (the deliberate minimum)")
    void shortUnquotedAssignmentStaysReadable() {
        assertEquals("token =x", redactValue("token =x"),
                "the four-character minimum is an accepted residue");
    }

    @Test
    @DisplayName("a quoted assignment whose first word is short is masked whole")
    void shortQuotedAssignment() {
        assertAll("short quoted assignments",
                () -> assertEquals("token=\"" + SecretRedactor.REDACTED + "\"",
                        redactValue("token=\"ab cd ef\""),
                        "a two-character first word no longer stops the mask"),
                () -> assertEquals("password=\"" + SecretRedactor.REDACTED + "\"",
                        redactValue("password=\"my pass phrase\""),
                        "a three-character first word no longer stops the mask"),
                () -> assertEquals("{\"token\": \"" + SecretRedactor.REDACTED + "\"}",
                        redactValue("{\"token\": \"a b\"}"),
                        "a one-character quoted JSON value is masked"));
    }

    @Test
    @DisplayName("a cross-line quoted value does not swallow the label after it")
    void crossLineQuotedValueDoesNotSwallowTheNextLabel() {
        // the close is refused when a word character follows it, so the unclosed rule stops at the first line end
        var redacted = redactValue("pass=\"abc\ntoken=\"s3cret\"");

        assertAll("cross-line quoted value",
                () -> assertFalse(redacted.contains("s3cret"), "the second credential is still masked"),
                () -> assertEquals("pass=\"" + SecretRedactor.REDACTED + "\ntoken=\""
                        + SecretRedactor.REDACTED + "\"", redacted, "each value is masked on its own line"),
                () -> assertEquals(redacted, redactValue(redacted), "redacting the masked value changes nothing"));
    }

    @Test
    @DisplayName("an escaped quote is no close, so the rest of the line is masked")
    void escapedQuoteIsNoCloseInAnAssignment() {
        var redacted = redactValue("password=\"abc\\\"def");

        assertEquals("password=\"" + SecretRedactor.REDACTED, redacted, "the escaped quote does not close the value");
    }

    @Test
    @DisplayName("a close followed by a word character is read as unclosed (the accepted cost)")
    void closeFollowedByAWordCharacterIsUnclosed() {
        var redacted = redactValue("password=\"my pass\"x");

        assertEquals("password=\"" + SecretRedactor.REDACTED, redacted, "the refused close masks the rest of the line");
    }

    @Test
    @DisplayName("a punctuation-leading value is not swallowed by a quoted assignment")
    void quotedAssignmentDoesNotSwallowAPunctuationLeadingValue() {
        var redacted = redactValue("pass=\"abc token=\"!s3cret-value\"");

        assertFalse(redacted.contains("s3cret-value"), "a refused close masks the rest of the line");
    }

    @Test
    @DisplayName("a no-break space after a credential flag does not hide the value")
    void credentialFlagWithNonAsciiSpace() {
        assertAll("Unicode whitespace after a credential flag",
                () -> assertEquals("--token\u00A0" + SecretRedactor.REDACTED,
                        redactValue("--token\u00A0abcd1234"), "an unquoted value is masked"),
                () -> assertEquals("--token\u00A0\"" + SecretRedactor.REDACTED + "\"",
                        redactValue("--token\u00A0\"abcd1234\""), "a quoted value is masked"));
    }

    @Test
    @DisplayName("a quoted assignment does not swallow a later label on its own line")
    void quotedAssignmentDoesNotSwallowALaterLabelOnItsLine() {
        var redacted = redactValue("pass=\"abc token=\"s3cret\"");

        assertAll("same-line refused close",
                () -> assertFalse(redacted.contains("s3cret"), "the later value is masked with the rest of the line"),
                () -> assertFalse(redacted.contains("abc"), "the first value is masked too"),
                () -> assertEquals(redacted, redactValue(redacted), "redacting the masked value changes nothing"));
    }

    @Test
    @DisplayName("a quoted assignment value that closes on the next line is read as unclosed (the accepted residue)")
    void quotedAssignmentClosingOnTheNextLineIsUnclosed() {
        // the assignment form closes on its own line only: a cross-line close is indistinguishable from the next label's quote
        var redacted = redactValue("password=\"first line\nsecond line\" done");

        assertAll("cross-line quoted assignment",
                () -> assertEquals("password=\"" + SecretRedactor.REDACTED + "\nsecond line\" done", redacted,
                        "the unclosed rule masks the rest of the first line"),
                () -> assertEquals(redacted, redactValue(redacted), "redacting the masked value changes nothing"));
    }

    @Test
    @DisplayName("an unclosed quoted assignment masks the rest of its line")
    void unclosedQuotedAssignmentIsMasked() {
        // `as the flag forms do`: an unclosed quote masks the rest of the line, so a short first word is not left readable
        var doubleQuoted = redactValue("password=\"my pass");

        assertAll("unclosed quoted assignment",
                () -> assertEquals("password=\"" + SecretRedactor.REDACTED, doubleQuoted,
                        "the unclosed quoted value is masked to the line end"),
                () -> assertFalse(doubleQuoted.contains("my pass"), "the value behind the quote is gone"));
    }

    @Test
    @DisplayName("a non-ASCII space in a credential assignment does not hide the value")
    void assignmentWithNonAsciiSpace() {
        // U+00A0 NO-BREAK SPACE and U+202F NARROW NO-BREAK SPACE are not matched by an ASCII-only \s
        assertAll("Unicode whitespace in a credential assignment",
                () -> assertEquals("password\u00A0=" + SecretRedactor.REDACTED,
                        redactValue("password\u00A0=abcd1234"),
                        "a no-break space before the separator is matched"),
                () -> assertEquals("token \u202F= " + SecretRedactor.REDACTED,
                        redactValue("token \u202F= abcd1234"),
                        "a narrow no-break space after the label is matched"));
    }

    @Test
    @DisplayName("invisible formatting characters do not hide a credential")
    void assignmentWithInvisibleCharacters() {
        // U+200B ZERO WIDTH SPACE inside the label; U+FEFF ZERO WIDTH NO-BREAK SPACE in the separator
        var zeroWidthLabel = redactValue("pass\u200Bword=abcd1234");
        var byteOrderMark = redactValue("token\uFEFF=abcd1234");

        assertAll("invisible characters in a credential assignment",
                () -> assertEquals("password=" + SecretRedactor.REDACTED, zeroWidthLabel,
                        "a zero-width space inside the label is removed before the match"),
                () -> assertFalse(zeroWidthLabel.contains("abcd1234"), "the value after a split label is gone"),
                () -> assertEquals("token=" + SecretRedactor.REDACTED, byteOrderMark,
                        "a byte-order mark after the label is removed before the match"),
                () -> assertFalse(byteOrderMark.contains("\uFEFF"), "the stored text carries no invisible character"));
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
    @DisplayName("an unclosed quote holding an escaped quote masks the rest of the line (flag and escape: contract)")
    void unclosedCredentialFlagWithEscapedQuote() {
        var head = Generators.letterStrings(4, 12).next();
        // longer than every letter run of the expected text, so a contains check cannot hit the flag or the mask
        var tail = Generators.letterStrings(12, 20).next();
        var doubleQuoted = redactValue("--password \"" + head + "\\\"" + tail);
        var singleQuoted = redactValue("--password '" + head + "\\'" + tail);

        assertAll("escaped quote inside an unclosed value",
                () -> assertEquals("--password \"" + SecretRedactor.REDACTED, doubleQuoted,
                        "an escaped double quote is no close"),
                () -> assertFalse(doubleQuoted.contains(tail), "the double-quoted tail is gone"),
                () -> assertEquals("--password '" + SecretRedactor.REDACTED, singleQuoted,
                        "an escaped single quote is no close"),
                () -> assertFalse(singleQuoted.contains(tail), "the single-quoted tail is gone"));
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

    @ParameterizedTest(name = "line end #{index}")
    @ValueSource(strings = {"\n", "\r\n", "\r"})
    @DisplayName("a quoted flag value with a backslash before its line end is masked whole (the line ends: contract)")
    void quotedCredentialFlagEscapedLineEnd(String lineEnd) {
        // longer than every letter run of the expected text, so a contains check cannot hit the flag or the mask
        var first = Generators.letterStrings(12, 20).next();
        var second = Generators.letterStrings(12, 20).next();

        var redacted = redactValue("--password \"" + first + "\\" + lineEnd + second + "\" done");

        assertAll("escaped line end inside a quoted value",
                () -> assertEquals("--password \"" + SecretRedactor.REDACTED + "\" done", redacted,
                        "both halves are masked and the flag is kept"),
                () -> assertFalse(redacted.contains(first), "the half before the line end is gone"),
                () -> assertFalse(redacted.contains(second), "the half after the line end is gone"),
                () -> assertEquals(redacted, redactValue(redacted), "redacting the masked value changes nothing"));
    }

    @Test
    @DisplayName("a quoted flag value is never matched past its second escaped line end (backslash and LF: contract)")
    void quotedCredentialFlagTwoEscapedLineEnds() {
        // longer than every letter run of the expected text, so a contains check cannot hit the flag or the mask
        var first = Generators.letterStrings(12, 20).next();
        var second = Generators.letterStrings(12, 20).next();
        var third = Generators.letterStrings(12, 20).next();
        var log = "--password \"" + first + "\\\n" + second + "\\\n" + third + "\"";

        var redacted = redactValue(log);

        assertAll("two escaped line ends inside a quoted value",
                () -> assertEquals("--password \"" + SecretRedactor.REDACTED + "\n" + second + "\\\n" + third + "\"",
                        redacted, "only the first line is masked as unclosed"),
                () -> assertFalse(redacted.contains(first), "the first line of the value is gone"),
                () -> assertEquals(redacted, redactValue(redacted), "redacting the masked value changes nothing"));
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
    @DisplayName("a PGP private-key block is redacted (the OpenPGP armor markers)")
    void pgpPrivateKeyBlock() {
        // the armor header and tail lines are the OpenPGP wire format (RFC 4880 § 6.2)
        var block = String.join("\n", "-----BEGIN PGP PRIVATE KEY BLOCK-----", "",
                Generators.letterStrings(16, 64).next(), "-----END PGP PRIVATE KEY BLOCK-----");

        assertEquals("key:\n" + SecretRedactor.REDACTED + "\ndone", redactValue("key:\n" + block + "\ndone"),
                "a closed PGP key block is replaced whole");
    }

    @Test
    @DisplayName("an unclosed PGP private-key block masks the rest (the OpenPGP armor marker)")
    void unclosedPgpPrivateKeyBlock() {
        // the armor header line is the OpenPGP wire format (RFC 4880 § 6.2)
        var log = "key:\n-----BEGIN PGP PRIVATE KEY BLOCK-----\n\n" + Generators.letterStrings(16, 64).next();

        assertEquals("key:\n" + SecretRedactor.REDACTED, redactValue(log),
                "an unclosed PGP key block is masked to the end");
    }

    /** A wall-clock bound a left-to-right scan meets easily and the former lazy pattern exceeds. */
    private static final Duration REDACTION_BOUND = Duration.ofSeconds(5);

    private static final String PEM_BEGIN_MARKER = "-----BEGIN PRIVATE KEY-----";

    @Test
    @DisplayName("400 000 characters of BEGIN markers without an END are redacted within the bound")
    void pemBeginMarkersWithoutEndAreLinear() {
        // the excerpt maximum of 50 lines of MAX_LINE_LENGTH characters is about 409 600 characters;
        // no END follows any marker, so every marker was scanned to the end of the text before the fix
        var markers = PEM_BEGIN_MARKER.repeat(400_000 / PEM_BEGIN_MARKER.length() + 1).substring(0, 400_000);

        var redacted = assertTimeoutPreemptively(REDACTION_BOUND, () -> redactValue(markers),
                "400 000 characters of BEGIN markers must redact within the bound");

        assertEquals(400_000, markers.length(), "the cost input holds 400 000 characters");
        assertEquals(SecretRedactor.REDACTED, redacted, "an unclosed block masks the whole text");
    }

    @Test
    @DisplayName("3 700 BEGIN markers without an END are redacted within the bound")
    void pemManyBeginMarkersWithoutEndAreLinear() {
        var markers = PEM_BEGIN_MARKER.repeat(3_700);

        var redacted = assertTimeoutPreemptively(REDACTION_BOUND, () -> redactValue(markers),
                "3 700 BEGIN markers must redact within the bound");

        assertEquals(3_700, markers.length() / PEM_BEGIN_MARKER.length(), "the input holds 3 700 markers");
        assertEquals(SecretRedactor.REDACTED, redacted, "an unclosed block masks the whole text");
    }

    @Test
    @DisplayName("a 1 000 000-character quoted flag value is masked without a stack overflow")
    void quotedFlagValueOfOneMillionCharacters() {
        // the quoted value is one plain run: a per-character recursion would overflow the stack here
        var log = "--password \"" + "a".repeat(1_000_000) + "\"";

        var redacted = assertDoesNotThrow(() -> redactValue(log),
                "a million-character quoted value must not overflow the stack");

        assertEquals("--password \"" + SecretRedactor.REDACTED + "\"", redacted,
                "the whole quoted value is masked");
    }

    @Test
    @DisplayName("the npm token is exactly 36 characters after npm_ (the operator's decision)")
    void npmAccessTokenExactLength() {
        // the documented shape: `npm_` plus exactly 36 alphanumeric characters (npm Registry API)
        var token = "npm_" + "A1b2C3d4E5f6G7h8I9j0K1l2M3n4O5p6Q7r8";
        var longer = token + "x";
        var followedByUnderscore = token + "_tail";
        var precededByWordCharacter = "x" + token;

        assertAll("npm token exact length",
                () -> assertEquals("is " + SecretRedactor.REDACTED, redactValue("is " + token),
                        "a 36-character npm token is masked"),
                () -> assertEquals("is " + longer, redactValue("is " + longer),
                        "a 37-character run stays readable"),
                () -> assertEquals(followedByUnderscore, redactValue(followedByUnderscore),
                        "a run followed by an underscore stays"),
                () -> assertEquals(precededByWordCharacter, redactValue(precededByWordCharacter),
                        "a run preceded by a word character stays"));
    }

    @Test
    @DisplayName("an npm package name and prose naming a private key stay readable")
    void npmAndKeyBlockNegativeControls() {
        assertAll("npm token and key block negative controls",
                () -> assertEquals("installed npm_pkg", redactValue("installed npm_pkg"),
                        "a package name starting npm_ stays"),
                () -> assertEquals("rotate the PGP PRIVATE KEY BLOCK monthly",
                        redactValue("rotate the PGP PRIVATE KEY BLOCK monthly"),
                        "prose naming PRIVATE KEY stays readable"),
                () -> assertEquals("the PRIVATE KEY----- line is missing",
                        redactValue("the PRIVATE KEY----- line is missing"),
                        "a marker tail without BEGIN stays readable"));
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

    @Test
    @DisplayName("an escape sequence between a label and its value does not hide the value (SGR codes)")
    void entryTextEscapeSplitLabel() {
        // ESC[1m and ESC[0m are the SGR bold and reset sequences of a colour-coded terminal line
        var value = Generators.letterStrings(8, 16).next();
        var entry = ActivityEntry.builder()
                .seq(Generators.integers(1, 100).next())
                .attempt(Generators.integers(1, 10).next())
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TEXT)
                .text("login \u001B[1mpassword\u001B[0m=" + value)
                .at(Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next()))
                .build();

        var redacted = SecretRedactor.redact(entry);

        assertAll("escape-split label in a text entry",
                () -> assertEquals("login password=" + SecretRedactor.REDACTED, redacted.text(),
                        "the escape-split password value is masked"),
                () -> assertFalse(redacted.text().contains(value), "the secret value is gone from the text"),
                () -> assertFalse(redacted.text().contains("\u001B"), "the stored text carries no escape"));
    }

    @Test
    @DisplayName("an escape sequence inside a tool target does not hide a credential flag (SGR codes)")
    void entryToolTargetEscapeSplitFlag() {
        // ESC[33m and ESC[0m are the SGR yellow and reset sequences of a colour-coded terminal line
        var value = Generators.letterStrings(8, 16).next();
        var entry = ActivityEntry.builder()
                .seq(Generators.integers(1, 100).next())
                .attempt(Generators.integers(1, 10).next())
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_CALL)
                .tool(new ActivityEntry.Tool("bash", "npm login \u001B[33m--password\u001B[0m " + value))
                .at(Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next()))
                .build();

        var target = SecretRedactor.redact(entry).tool().target();

        assertAll("escape-split flag in a tool target",
                () -> assertEquals("npm login --password " + SecretRedactor.REDACTED, target,
                        "the escape-split flag value is masked"),
                () -> assertFalse(target.contains(value), "the secret value is gone from the target"));
    }
}
