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

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * What findings are fetched for and what code analysis is asked about (PM-IMPL-1): a commit, or a pull request at
 * a commit. It is the scope of {@link FindingSource#fetch} and the target of the analysis result and of both
 * analysis operations, so {@code core.analysis} depends on {@code core.findings} and never the reverse.
 * <p>
 * The commit is 40 or 64 digits because the scope is the {@code target} of the schema {@code AnalysisResult}, whose
 * {@code commit_sha} admits the SHA-1 and the SHA-256 object name of a repository alike. A {@link Finding} records
 * its commits ({@code raised_at_head}, {@code reviewed_commit_sha}) with 40 digits only, as the schema
 * {@code FindingRecord} states; a scope with a 64-digit commit therefore does not carry over into those members.
 *
 * @param kind      whether the scope is a commit or a pull request
 * @param commitSha the commit, as 40 or 64 lower-case hexadecimal digits
 * @param prNumber  the number of the pull request, at least 1, if {@code kind} is {@link Kind#PULL_REQUEST};
 *                  {@code null} for a commit
 * @since 0.1
 */
public record FindingScope(Kind kind, String commitSha, Integer prNumber) {

    private static final Pattern COMMIT_SHA = Pattern.compile("^(?:[0-9a-f]{40}|[0-9a-f]{64})$");

    /**
     * @throws NullPointerException     if the kind or the commit is {@code null}
     * @throws IllegalArgumentException if the commit is not 40 or 64 lower-case hexadecimal digits, or the
     *                                  pull-request number is missing for a pull request, present for a commit or
     *                                  less than 1
     */
    public FindingScope {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(commitSha, "commit_sha");
        if (!COMMIT_SHA.matcher(commitSha).matches()) {
            throw new IllegalArgumentException("commit_sha must be 40 or 64 lower-case hexadecimal digits");
        }
        if (kind == Kind.PULL_REQUEST) {
            if (prNumber == null) {
                throw new IllegalArgumentException("pr_number is required iff kind is pull_request");
            }
            if (prNumber < 1) {
                throw new IllegalArgumentException("pr_number must be at least 1");
            }
        } else if (prNumber != null) {
            throw new IllegalArgumentException("pr_number is set only for kind pull_request");
        }
    }

    /**
     * @param commitSha the commit, as 40 or 64 lower-case hexadecimal digits
     * @return the scope of that commit
     */
    public static FindingScope commit(String commitSha) {
        return new FindingScope(Kind.COMMIT, commitSha, null);
    }

    /**
     * @param commitSha the commit the pull request is at, as 40 or 64 lower-case hexadecimal digits
     * @param prNumber  the number of the pull request, at least 1
     * @return the scope of that pull request
     */
    public static FindingScope pullRequest(String commitSha, int prNumber) {
        return new FindingScope(Kind.PULL_REQUEST, commitSha, prNumber);
    }

    /**
     * The closed kind of a scope.
     */
    public enum Kind {
        /** A commit. */
        COMMIT("commit"),
        /** A pull request at a commit. */
        PULL_REQUEST("pull_request");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value on the wire and in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value on the wire or in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }
}
