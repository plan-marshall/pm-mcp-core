/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.SecretRedactor;

/**
 * Every parser files its diagnostic message through the one stored seam, {@link DiagnosticCollector#stored}, which
 * redacts the message before it cuts it to the stored length. One case per parser family proves that a credential the
 * tool printed reaches neither the model nor a store.
 */
@EnableGeneratorController
@DisplayName("Diagnostic message redaction")
class DiagnosticCollectorRedactionTest {

    private static final String SECRET = "abcd1234";

    private static final String CREDENTIAL = "password=" + SECRET;

    private static void assertMessagesRedacted(List<Diagnostic> diagnostics) {
        assertFalse(diagnostics.isEmpty(), "the parser must file a diagnostic");
        assertAll(diagnostics.stream().map(diagnostic -> () -> {
            assertFalse(diagnostic.message().contains(SECRET), "the stored message must not carry the secret");
            assertTrue(diagnostic.message().contains(SecretRedactor.REDACTED),
                    "the stored message keeps the redaction marker");
        }));
    }

    @Test
    @DisplayName("a TAP error message is redacted")
    void tapErrorMessageRedacted() {
        var build = TapOutputParser.parse("not ok 1 - " + CREDENTIAL + "\n");

        assertMessagesRedacted(build.diagnostics());
    }

    @Test
    @DisplayName("a TAP stack line is redacted")
    void tapStackRedacted() {
        var build = TapOutputParser.parse("""
                not ok 1 - fails
                  ---
                  error: "outer failure"
                  stack: |
                          %s
                  ...
                """.formatted(CREDENTIAL));

        var diagnostic = build.diagnostics().getFirst();
        assertAll(
                () -> assertNotNull(diagnostic.stackTrace(), "the parser keeps the stack block"),
                () -> assertFalse(diagnostic.stackTrace().contains(SECRET),
                        "the stored stack must not carry the secret"),
                () -> assertTrue(diagnostic.stackTrace().contains(SecretRedactor.REDACTED),
                        "the stored stack keeps the redaction marker"));
    }

    @Test
    @DisplayName("a Jest test name is redacted")
    void jestMessageRedacted() {
        var build = JestOutputParser.parse("""
                FAIL  src/a.test.ts
                  ● a › %s

                      at Object.<anonymous> (src/a.test.ts:7:9)

                """.formatted(CREDENTIAL));

        assertMessagesRedacted(build.diagnostics());
    }

    @Test
    @DisplayName("an ESLint message is redacted")
    void eslintMessageRedacted() {
        var result = EslintOutputParser.parse("""
                /src/a.ts
                  1:2  error  %s  no-secrets
                """.formatted(CREDENTIAL));

        assertMessagesRedacted(result.build().diagnostics());
    }

    @Test
    @DisplayName("a TypeScript message is redacted")
    void typescriptMessageRedacted() {
        var build = TypeScriptOutputParser.parse("src/a.ts(10,5): error TS2322: " + CREDENTIAL);

        assertMessagesRedacted(build.diagnostics());
    }

    @Test
    @DisplayName("a Maven message is redacted")
    void mavenMessageRedacted() {
        var build = MavenOutputParser.parse("[ERROR] /p/Foo.java:[7,3] cannot find symbol " + CREDENTIAL + "\n");

        assertMessagesRedacted(build.diagnostics());
    }

    @Test
    @DisplayName("a pyproject (mypy) message is redacted")
    void pyprojectMessageRedacted() {
        var build = PyprojectOutputParser.parse("src/a.py:3: error: " + CREDENTIAL + "\n");

        assertMessagesRedacted(build.diagnostics());
    }

    @Test
    @DisplayName("a Gradle message is redacted")
    void gradleMessageRedacted() {
        var build = GradleOutputParser.parse("/p/Foo.java:7: error: cannot find symbol " + CREDENTIAL + "\n");

        assertMessagesRedacted(build.diagnostics());
    }
}
