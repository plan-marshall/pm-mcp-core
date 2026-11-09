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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    /** The hash version of the inputs and framing the suite is written for. Contract: hash version 1. */
    static final int VERSION = 1;

    // Contract: the form of a digest, 64 lower-case hexadecimal characters (SHA-256).
    private static final Pattern DIGEST_FORM = Pattern.compile("[0-9a-f]{64}");
    // Contract: the relation keyword unavailable <label> <step> of the fixture grammar.
    private static final String UNAVAILABLE = "unavailable";
    // Contract: the relation keyword same <label> <label> of the fixture grammar.
    private static final String SAME = "same";
    // Contract: the relation keyword different <label> <label> of the fixture grammar.
    private static final String DIFFERENT = "different";
    // Contract: the separator after the failing step in the detail of an unavailable result.
    private static final String STEP_SEPARATOR = ":";

    private DigestRelations() {
    }

    /**
     * Asserts every expectation of a run against its results.
     * <p>
     * Every assertion message states what is checked; the fixture, the line, the directive and the label it concerns
     * are the heading of the {@code assertAll} group that holds the assertion.
     *
     * @param run the run of one fixture
     */
    static void assertRelations(WorktreeShaScenario.Run run) {
        String fixture = run.fixture().name();
        Map<String, GitResult<WorktreeSha>> results = run.results();
        assertAll(fixture, () -> assertFalse(run.expectations().isEmpty(), "the fixture states at least one relation"));
        var checks = new ArrayList<Executable>();
        Set<String> named = new HashSet<>();
        for (var entry : results.entrySet()) {
            GitResult<WorktreeSha> result = entry.getValue();
            if (result.isOk()) {
                WorktreeSha sha = result.value();
                checks.add(() -> assertAll(fixture + ": result " + entry.getKey(),
                        () -> assertEquals(VERSION, sha.version(), "hash version of a successful result"),
                        () -> assertTrue(DIGEST_FORM.matcher(sha.digest()).matches(),
                                "digest is a lower-case hex SHA-256 digest")));
            }
        }
        for (Directive expectation : run.expectations()) {
            List<String> args = expectation.arguments();
            named.addAll(args.subList(0, UNAVAILABLE.equals(expectation.keyword()) ? 1 : 2));
            String where = fixture + " line " + expectation.line() + " (" + expectation.keyword() + " "
                    + String.join(" ", args) + ")";
            checks.add(() -> assertAll(where, () -> check(where, expectation, results)));
        }
        for (String label : results.keySet()) {
            checks.add(() -> assertAll(fixture + ": result " + label,
                    () -> assertTrue(named.contains(label), "every recorded result is named by a relation")));
        }
        assertAll(fixture, checks);
    }

    private static void check(String where, Directive expectation, Map<String, GitResult<WorktreeSha>> results) {
        List<String> args = expectation.arguments();
        GitResult<WorktreeSha> first = result(where, results, args.getFirst());
        switch (expectation.keyword()) {
            case UNAVAILABLE -> assertAll(where + ": detail " + first.detail(),
                    () -> assertEquals(GitOutcome.UNAVAILABLE, first.outcome(), "outcome of an unavailable result"),
                    () -> assertTrue(first.detail().startsWith(args.get(1) + STEP_SEPARATOR),
                            "detail names the expected failing step"));
            case SAME, DIFFERENT -> {
                GitResult<WorktreeSha> second = result(where, results, args.get(1));
                String a = digest(where, args.get(0), first);
                String b = digest(where, args.get(1), second);
                if (SAME.equals(expectation.keyword())) {
                    assertEquals(a, b, "digests of a same relation are equal");
                } else {
                    assertNotEquals(a, b, "digests of a different relation differ");
                }
            }
            default -> fail("the relation keyword is one the grammar declares");
        }
    }

    private static GitResult<WorktreeSha> result(String where, Map<String, GitResult<WorktreeSha>> results,
            String label) {
        GitResult<WorktreeSha> result = results.get(label);
        assertAll(where + ": label " + label,
                () -> assertNotNull(result, "a result is recorded under the named label"));
        return Objects.requireNonNull(result);
    }

    private static String digest(String where, String label, GitResult<WorktreeSha> result) {
        assertAll(where + ": result " + label + " is " + result.outcome() + " (" + result.detail() + ")",
                () -> assertTrue(result.isOk(), "only a successful result enters equality"));
        return result.value().digest();
    }
}
