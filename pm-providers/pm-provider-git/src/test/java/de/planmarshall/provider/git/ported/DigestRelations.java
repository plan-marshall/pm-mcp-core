/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.ported;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.function.Executable;

import de.planmarshall.provider.git.GitOperations.WorktreeSha;
import de.planmarshall.provider.git.GitOutcome;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.ported.PortedFixtures.Directive;

/**
 * Asserts the equality behaviour of {@code git.worktree-sha} results, never their bytes (PM-TEST-2).
 * <p>
 * The digest is a PM-MCP-native value that is never exchanged with the original, so no expected value is a digest:
 * a fixture states relations between its labelled results instead.
 * <ul>
 * <li>{@code same a b}: both results succeeded and their digests are equal.</li>
 * <li>{@code different a b}: both results succeeded and their digests differ.</li>
 * <li>{@code unavailable a step}: the result is {@link GitOutcome#UNAVAILABLE} and its detail names the failing
 * step.</li>
 * </ul>
 * Every successful result carries hash version {@code 1} and a digest of 64 lower-case hexadecimal characters (its
 * form, never its value). An unavailable result never enters an equality assertion: {@code same} or {@code different}
 * over it fails, because an unavailable value equals nothing, another unavailable value included. Every recorded
 * label must be named by an expectation, so a result that no relation checks fails the fixture.
 *
 * @since 0.1
 */
final class DigestRelations {

    /** The hash version of the inputs and framing the suite is written for. */
    static final int VERSION = 1;

    private static final Pattern DIGEST_FORM = Pattern.compile("[0-9a-f]{64}");

    private DigestRelations() {
    }

    /**
     * Asserts every expectation of a run against its results.
     *
     * @param run the run of one fixture
     */
    static void assertRelations(WorktreeShaScenario.Run run) {
        String fixture = run.fixture().name();
        Map<String, GitResult<WorktreeSha>> results = run.results();
        if (run.expectations().isEmpty()) {
            fail("fixture " + fixture + " states no relation");
        }
        var checks = new ArrayList<Executable>();
        Set<String> named = new HashSet<>();
        for (var entry : results.entrySet()) {
            GitResult<WorktreeSha> result = entry.getValue();
            if (result.isOk()) {
                WorktreeSha sha = result.value().orElseThrow();
                checks.add(() -> assertEquals(VERSION, sha.version(),
                        fixture + ": result " + entry.getKey() + " has hash version " + sha.version()));
                checks.add(() -> assertTrue(DIGEST_FORM.matcher(sha.digest()).matches(),
                        fixture + ": result " + entry.getKey() + " is no lower-case hex SHA-256 digest"));
            }
        }
        for (Directive expectation : run.expectations()) {
            List<String> args = expectation.arguments();
            named.addAll(args.subList(0, "unavailable".equals(expectation.keyword()) ? 1 : 2));
            checks.add(() -> check(fixture, expectation, results));
        }
        for (String label : results.keySet()) {
            checks.add(() -> assertTrue(named.contains(label),
                    fixture + ": result " + label + " is named by no relation"));
        }
        assertAll(fixture, checks);
    }

    private static void check(String fixture, Directive expectation, Map<String, GitResult<WorktreeSha>> results) {
        List<String> args = expectation.arguments();
        String where = fixture + " line " + expectation.line() + " (" + expectation.keyword() + " "
                + String.join(" ", args) + ")";
        GitResult<WorktreeSha> first = result(where, results, args.getFirst());
        switch (expectation.keyword()) {
            case "unavailable" -> {
                assertEquals(GitOutcome.UNAVAILABLE, first.outcome(), where + ": outcome");
                assertTrue(first.detail().startsWith(args.get(1) + ":"),
                        where + ": detail " + first.detail() + " does not name the step " + args.get(1));
            }
            case "same", "different" -> {
                GitResult<WorktreeSha> second = result(where, results, args.get(1));
                String a = digest(where, args.get(0), first);
                String b = digest(where, args.get(1), second);
                if ("same".equals(expectation.keyword())) {
                    assertEquals(a, b, where + ": digests differ");
                } else {
                    assertNotEquals(a, b, where + ": digests are equal");
                }
            }
            default -> fail(where + ": no relation " + expectation.keyword());
        }
    }

    private static GitResult<WorktreeSha> result(String where, Map<String, GitResult<WorktreeSha>> results,
            String label) {
        GitResult<WorktreeSha> result = results.get(label);
        if (result == null) {
            fail(where + ": no result is recorded under " + label);
        }
        return result;
    }

    private static String digest(String where, String label, GitResult<WorktreeSha> result) {
        if (!result.isOk()) {
            fail(where + ": result " + label + " is " + result.outcome() + " (" + result.detail()
                    + "); an unavailable result never enters an equality assertion");
        }
        return result.value().orElseThrow().digest();
    }
}
