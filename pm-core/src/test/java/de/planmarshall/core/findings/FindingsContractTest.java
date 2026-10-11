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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The findings contracts a provider module implements (PM-IMPL-1).
 */
@DisplayName("Findings contract")
class FindingsContractTest {

    private static final String HEAD = "0123456789abcdef0123456789abcdef01234567";
    private static final FindingSourceKey KEY = FindingSourceKey.analysis("analysis-provider");

    static Finding finding(String id, String summary) {
        return Finding.builder()
                .formatVersion(Finding.FORMAT_VERSION)
                .findingId(id)
                .source(FindingSourceKind.ANALYSIS)
                .provider("analysis-provider")
                .severity(Severity.MINOR)
                .blocking(false)
                .status(FindingStatus.OPEN)
                .resolution(Resolution.PENDING)
                .summary(summary)
                .transmitOutcome(TransmitOutcome.PENDING)
                .resolveState(ResolveState.NOT_APPLICABLE)
                .createdAt(Instant.parse("2026-10-11T12:00:00Z"))
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
        var pullRequest = FindingScope.pullRequest(HEAD, 7);
        var commit = FindingScope.commit(HEAD);
        var first = finding("F-1", "first");
        var second = finding("F-2", "second");
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

        responder.respond(finding("F-1", "first"), Disposition.SUPPRESS, "not reachable");

        assertAll(
                () -> assertEquals(KEY, responder.key()),
                () -> assertEquals(List.of("F-1 SUPPRESS not reachable"), answered));
    }
}
