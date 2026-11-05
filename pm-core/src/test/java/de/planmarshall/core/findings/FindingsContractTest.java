/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The findings contracts a provider module implements (PM-IMPL-1).
 */
@EnableGeneratorController
@DisplayName("Findings contract")
class FindingsContractTest {

    private static final String HEAD = "%040x".formatted(Generators.longs().next());
    private static final String PROVIDER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final Instant CREATED = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final FindingSourceKey KEY = FindingSourceKey.analysis(PROVIDER);

    static Finding finding(String id, String summary) {
        return Finding.builder()
                .formatVersion(Finding.FORMAT_VERSION)
                .findingId(id)
                .source(FindingSourceKind.ANALYSIS)
                .provider(PROVIDER)
                .severity(Severity.MINOR)
                .blocking(false)
                .status(FindingStatus.OPEN)
                .resolution(Resolution.PENDING)
                .summary(summary)
                .transmitOutcome(TransmitOutcome.PENDING)
                .resolveState(ResolveState.NOT_APPLICABLE)
                .createdAt(CREATED)
                .build();
    }

    /** A source that answers the findings recorded for each scope. */
    record FixtureSource(FindingSourceKey key, Map<FindingScope, List<Finding>> findings) implements FindingSource {

        @Override
        public List<Finding> fetch(FindingScope scope) {
            return findings.getOrDefault(scope, List.of());
        }
    }

    /** A responder that records every answer it is asked to give. */
    record FixtureResponder(FindingSourceKey key, List<String> answered) implements FindingResponder {

        @Override
        public void respond(Finding finding, Disposition disposition, String reason) {
            answered.add(finding.findingId() + " " + disposition + " " + reason);
        }
    }

    @Test
    @DisplayName("the dispositions are FIX, ACCEPT and SUPPRESS, named as on the wire")
    void dispositions() {
        var names = Arrays.stream(Disposition.values()).map(Enum::name).toList();

        assertEquals(List.of("FIX", "ACCEPT", "SUPPRESS"), names);
    }

    @Test
    @DisplayName("a source returns the findings of the scope it is asked for")
    void source() {
        var pullRequest = FindingScope.pullRequest(HEAD, Generators.integers(1, 99_999).next());
        var commit = FindingScope.commit(HEAD);
        var first = finding("F-" + Generators.integers(1, 999).next(), Generators.letterStrings(3, 12).next());
        var second = finding("F-" + Generators.integers(1_000, 9_999).next(),
                Generators.letterStrings(3, 12).next());
        FindingSource source = new FixtureSource(KEY, Map.of(pullRequest, List.of(first, second)));

        var findings = source.fetch(pullRequest);

        assertAll(
                () -> assertEquals(KEY, source.key()),
                () -> assertEquals(List.of(first, second), findings),
                () -> assertEquals(List.of(), source.fetch(commit)));
    }

    @Test
    @DisplayName("a responder receives the finding, its disposition and the reason")
    void responder() {
        var answered = new ArrayList<String>();
        FindingResponder responder = new FixtureResponder(KEY, answered);
        var id = "F-" + Generators.integers(1, 9_999).next();
        var summary = Generators.letterStrings(3, 12).next();
        var reason = Generators.letterStrings(3, 20).next();

        responder.respond(finding(id, summary), Disposition.SUPPRESS, reason);

        assertAll(
                () -> assertEquals(KEY, responder.key()),
                () -> assertEquals(List.of(id + " SUPPRESS " + reason), answered));
    }
}
