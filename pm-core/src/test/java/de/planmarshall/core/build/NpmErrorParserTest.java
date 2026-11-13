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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.SecretRedactor;

/**
 * The ported tests of {@link NpmErrorParser} (PM-TEST-2): one case per plain-file fixture below
 * {@code ported/build/npm-errors}, derived by hand from the original's log and its pytest case.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("NpmErrorParser")
class NpmErrorParserTest extends ParsedBuildPortedTest {

    private static final String DIRECTORY = "ported/build/npm-errors";

    private static final int FIXTURE_FLOOR = 4;

    private static final int ROSTER_CASES = 31;

    static List<BuildLogFixtures.Case> fixtures() {
        return BuildLogFixtures.load(DIRECTORY);
    }

    @Test
    @DisplayName("names the peer dependency of a conflict trailer the original can parse")
    void namesThePeerConflict() {
        String logText = """
                npm ERR! code ERESOLVE
                npm ERR! ERESOLVE unable to resolve dependency tree
                npm ERR! Could not resolve dependency:
                npm ERR! peer react@17.0.2 from react-router-dom@5.3.4
                """;

        ParsedBuild build = NpmErrorParser.parse(logText);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "The whole log is filed as one diagnostic"),
                () -> assertEquals("ERESOLVE: Could not resolve peer dependency react@17.0.2 from react-router-dom@5.3.4",
                        build.diagnostics().getFirst().message(), "The conflict names its peer and its origin"),
                () -> assertEquals(Category.NPM_DEPENDENCY, build.diagnostics().getFirst().category(),
                        "The dependency category is the ERESOLVE one"));
    }

    @Test
    @DisplayName("the install completion line is the success marker")
    void installCompletionLine() {
        ParsedBuild added = NpmErrorParser.parse("npm install\nadded 150 packages in 10s\n");
        ParsedBuild upToDate = NpmErrorParser.parse("npm install\nup to date, audited 5 packages\n");
        ParsedBuild removed = NpmErrorParser.parse("npm uninstall\nremoved 3 packages in 1s\n");

        assertAll(
                () -> assertTrue(added.successMarker(), "the added line is the marker"),
                () -> assertEquals(BuildStatus.SUCCESS, added.status(), "a clean install succeeds"),
                () -> assertTrue(upToDate.successMarker(), "the up-to-date line is the marker"),
                () -> assertTrue(removed.successMarker(), "the removed line is the marker"));
    }

    @Test
    @DisplayName("a prose line that mentions packages is no install summary")
    void installSummaryRejectsProse() {
        ParsedBuild changed = NpmErrorParser.parse("changed the way packages are resolved\n");
        ParsedBuild upToDate = NpmErrorParser.parse("up to date, but packages are stale\n");

        assertAll(
                () -> assertFalse(changed.successMarker(), "a prose sentence is no completion line"),
                () -> assertFalse(upToDate.successMarker(), "a stale-packages sentence is no completion line"));
    }

    @Test
    @DisplayName("an install line beside an error block is no marker")
    void installLineBesideAnError() {
        ParsedBuild build = NpmErrorParser.parse("""
                npm ERR! code E404
                npm ERR! 404 'missing' is not in this registry
                added 150 packages in 10s
                """);

        assertAll(
                () -> assertFalse(build.successMarker(), "an error block keeps the marker false"),
                () -> assertEquals(BuildStatus.FAILURE, build.status(), "the error block is red"));
    }

    @Test
    @DisplayName("a log without a completion line is no marker")
    void noCompletionLine() {
        ParsedBuild build = NpmErrorParser.parse("some unrelated output\n");

        assertFalse(build.successMarker(), "an unrecognised log is no marker");
    }

    @Test
    @DisplayName("a generic message that carries a URL secret is redacted")
    void redactsTheGenericMessage() {
        // The generic message is the code token plus the first reportable line. The code token carries the
        // user-information shape here, so the redaction of the filed message is exercised at its source.
        var log = "npm ERR! code https://deploy:s3cr3t-value@registry.example/pkg\n";

        ParsedBuild build = NpmErrorParser.parse(log);

        assertAll(
                () -> assertEquals(1, build.diagnostics().size(), "the block files one diagnostic"),
                () -> assertFalse(build.diagnostics().getFirst().message().contains("s3cr3t-value"),
                        "the URL secret is not in the message"),
                () -> assertTrue(build.diagnostics().getFirst().message().contains(SecretRedactor.REDACTED),
                        "the message names the mask"));
    }

    @Test
    @DisplayName("an E404 specifier that carries URL user information is redacted (the npm 404 line)")
    void redactsTheE404Specifier() {
        // `npm ERR! 404 '<specifier>' is not in this registry` is the npm wire format of the E404 block
        var secret = Generators.letterStrings(8, 16).next();
        var log = "npm ERR! code E404\nnpm ERR! 404 'https://deploy:" + secret
                + "@registry.example/pkg' is not in this registry\n";

        var message = NpmErrorParser.parse(log).diagnostics().getFirst().message();

        assertAll("E404 specifier redaction",
                () -> assertEquals("E404: Package 'https://deploy:" + SecretRedactor.REDACTED
                        + "@registry.example/pkg' not found in registry", message,
                        "the specifier's URL secret is masked"),
                () -> assertFalse(message.contains(secret), "the URL secret is not in the E404 message"));
    }

    @Test
    @DisplayName("an ERESOLVE peer or from token that carries URL user information is redacted (npm trailer)")
    void redactsTheEresolveTokens() {
        // `peer <dependency> from <package>` after `Could not resolve dependency:` is the npm wire format of ERESOLVE
        var peerSecret = Generators.letterStrings(8, 16).next();
        var fromSecret = Generators.letterStrings(8, 16).next();
        var log = """
                npm ERR! code ERESOLVE
                npm ERR! Could not resolve dependency:
                npm ERR! peer https://deploy:%s@registry.example/react from https://ci:%s@registry.example/app
                """.formatted(peerSecret, fromSecret);

        var message = NpmErrorParser.parse(log).diagnostics().getFirst().message();

        assertAll("ERESOLVE token redaction",
                () -> assertEquals("ERESOLVE: Could not resolve peer dependency https://deploy:" + SecretRedactor.REDACTED
                        + "@registry.example/react from https://ci:" + SecretRedactor.REDACTED
                        + "@registry.example/app", message, "the peer and from URL secrets are masked"),
                () -> assertFalse(message.contains(peerSecret), "the peer URL secret is not in the message"),
                () -> assertFalse(message.contains(fromSecret), "the from URL secret is not in the message"));
    }

    @Test
    @DisplayName("E404 and ERESOLVE messages without a secret stay byte-identical (npm wire format)")
    void namedMessagesWithoutASecretStayUnchanged() {
        // the npm 404 line and the ERESOLVE trailer are the npm wire format
        var e404 = NpmErrorParser.parse("npm ERR! code E404\nnpm ERR! 404 'left-pad' is not in this registry\n");
        var eresolve = NpmErrorParser.parse("npm ERR! code ERESOLVE\nnpm ERR! ERESOLVE unable to resolve\n");

        assertAll("named messages without a secret",
                () -> assertEquals("E404: Package 'left-pad' not found in registry",
                        e404.diagnostics().getFirst().message(), "the E404 message is unchanged"),
                () -> assertEquals("ERESOLVE: Unable to resolve dependency tree",
                        eresolve.diagnostics().getFirst().message(), "the ERESOLVE fallback is unchanged"));
    }

    @Test
    @DisplayName("names the E404 package of npm 11's registry wording under the npm error prefix (npm wire format)")
    void namesTheE404PackageUnderTheCurrentPrefix() {
        // `npm error 404 '<package>' is not in this registry` is the npm 11 wording under the npm 12 prefix
        var name = Generators.letterStrings(4, 16).next();
        var log = "npm error code E404\nnpm error 404 '" + name + "' is not in this registry\n";

        var build = NpmErrorParser.parse(log);

        assertAll("E404 under the npm error prefix",
                () -> assertEquals(1, build.diagnostics().size(), "the E404 block files one diagnostic"),
                () -> assertEquals("E404: Package '" + name + "' not found in registry",
                        build.diagnostics().getFirst().message(), "the package of the 404 line is named"));
    }

    @Test
    @DisplayName("names the ERESOLVE peer conflict under the npm error prefix (npm wire format)")
    void namesThePeerConflictUnderTheCurrentPrefix() {
        // `Could not resolve dependency:` and its `peer <dependency> from <package>` trailer are the npm wire format
        var peer = Generators.letterStrings(4, 16).next();
        var origin = Generators.letterStrings(4, 16).next();
        var log = """
                npm error code ERESOLVE
                npm error Could not resolve dependency:
                npm error peer %s@1.0.0 from %s@2.0.0
                """.formatted(peer, origin);

        var build = NpmErrorParser.parse(log);

        assertAll("ERESOLVE under the npm error prefix",
                () -> assertEquals(Category.NPM_DEPENDENCY, build.diagnostics().getFirst().category(),
                        "the block is the ERESOLVE dependency one"),
                () -> assertEquals("ERESOLVE: Could not resolve peer dependency " + peer + "@1.0.0 from " + origin
                        + "@2.0.0", build.diagnostics().getFirst().message(), "the conflict marker finds the trailer"));
    }

    @Test
    @DisplayName("reads the first line of a generic block under the npm error prefix (npm wire format)")
    void readsTheFirstLineUnderTheCurrentPrefix() {
        // a generic block's message is its code and the first line after the prefix, here the code line itself
        var code = "E" + Generators.letterStrings(4, 10).next().toUpperCase(Locale.ROOT);
        var log = "npm error code " + code + "\nnpm error " + Generators.letterStrings(4, 16).next() + "\n";

        var build = NpmErrorParser.parse(log);

        assertEquals(code + ": code " + code, build.diagnostics().getFirst().message(),
                "the first prefixed line is the message");
    }

    @Override
    ParsedBuild parse(String logText) {
        return NpmErrorParser.parse(logText);
    }

    @Override
    String directory() {
        return DIRECTORY;
    }

    @Override
    int fixtureFloor() {
        return FIXTURE_FLOOR;
    }

    @Override
    int rosterCases() {
        return ROSTER_CASES;
    }
}
