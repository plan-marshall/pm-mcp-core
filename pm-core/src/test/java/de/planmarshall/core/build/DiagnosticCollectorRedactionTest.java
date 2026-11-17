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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Duration;
import java.util.ArrayList;
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
    @DisplayName("a message that is only invisible characters does not fail the parse and keeps the failure")
    void invisibleOnlyMessageDoesNotFailTheParse() {
        var maven = MavenOutputParser.parse("[ERROR] \u200B\u200B\n");
        var mypy = PyprojectOutputParser.parse("src/a.py:3: error: \u200B\n");
        var tap = TapOutputParser.parse("not ok 1 - \u200B\n");
        var jest = JestOutputParser.parse("""
                FAIL  src/a.test.ts
                  ● \u200B

                      at Object.<anonymous> (src/a.test.ts:7:9)

                """);

        assertAll("invisible-only messages",
                () -> assertTrue(maven.diagnostics().isEmpty(), "the Maven line files nothing"),
                () -> assertEquals("error (no visible message)", mypy.diagnostics().getFirst().message(),
                        "the mypy error keeps a fallback message"),
                () -> assertEquals(BuildStatus.FAILURE, mypy.status(), "the mypy build stays failed"),
                () -> assertTrue(tap.diagnostics().isEmpty(), "the TAP line files nothing and does not throw"),
                () -> assertEquals(BuildStatus.FAILURE, tap.status(), "the TAP status stays failed"),
                () -> assertEquals("test failure (no visible name)", jest.diagnostics().getFirst().message(),
                        "the Jest failure keeps a fallback name"),
                () -> assertEquals(BuildStatus.FAILURE, jest.status(), "the Jest build stays failed"));
    }

    @Test
    @DisplayName("a Gradle message is redacted")
    void gradleMessageRedacted() {
        var build = GradleOutputParser.parse("/p/Foo.java:7: error: cannot find symbol " + CREDENTIAL + "\n");

        assertMessagesRedacted(build.diagnostics());
    }

    /** A wall-clock bound a linear scan meets and the former quadratic URL pattern exceeds. */
    private static final Duration FLOOD_BOUND = Duration.ofSeconds(30);

    private static List<String> hostileFlood() {
        // a whitespace-separated flood is many tokens: a per-token lookup that walks back into the previous tokens is
        // quadratic on the whole line, the shape the bounded scan must not have
        var line = "x://y: ".repeat(BuildLogText.MAX_LINE_LENGTH / 7 + 1).substring(0, BuildLogText.MAX_LINE_LENGTH);
        var lines = new ArrayList<String>();
        for (var index = 0; index < 2_000; index++) {
            lines.add(line);
        }
        return List.copyOf(lines);
    }

    @Test
    @DisplayName("a hostile URL flood redacts within the bound through the stored seam")
    void hostileUrlFloodThroughTheStoredSeam() {
        var flood = hostileFlood();

        assertTimeoutPreemptively(FLOOD_BOUND, () -> flood.forEach(DiagnosticCollector::stored),
                "the stored seam redacts the flood within the bound");

        assertEquals(2_000, flood.size(), "the flood holds 2 000 lines");
        assertEquals(BuildLogText.MAX_LINE_LENGTH, flood.getFirst().length(), "each flood line holds 8 192 characters");
    }

    @Test
    @DisplayName("a hostile URL flood redacts within the bound through the Diagnostic constructor")
    void hostileUrlFloodThroughTheDiagnosticConstructor() {
        var stack = String.join("\n", hostileFlood());

        assertTimeoutPreemptively(FLOOD_BOUND,
                () -> new Diagnostic(null, null, null, "text", Category.OTHER, Severity.ERROR, stack),
                "the constructor redacts the flood within the bound");

        assertEquals(2_000 + 2_000 * BuildLogText.MAX_LINE_LENGTH - 1, stack.length(),
                "the flood reaches the constructor whole");
    }
}
