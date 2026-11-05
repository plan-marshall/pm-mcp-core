/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("PullRequest and PullRequestRef")
class PullRequestTest {

    private static String text() {
        return Generators.letterStrings(1, 20).next();
    }

    /** The forge and the origin are a provider id and its web origin, so they are contract literals. */
    private static RepositoryRef repository() {
        return new RepositoryRef("github", "https://github.com", text(), text());
    }

    private static PullRequest.PullRequestBuilder validPullRequest() {
        return PullRequest.builder()
                .state(Generators.enumValues(PullRequest.State.class).next())
                .title(text())
                .body(text())
                .baseBranch(text())
                .headBranch(text())
                .headSha(text())
                .headRepositoryRef(repository())
                .headWritable(Generators.booleans().next())
                .changedPaths(List.of(text()))
                .pathCompleteness(Generators.enumValues(PullRequest.PathCompleteness.class).next());
    }

    @Nested
    @DisplayName("PullRequestRef")
    class Ref {

        @Test
        @DisplayName("keeps its repository and number")
        void keepsRepositoryAndNumber() {
            var repository = repository();
            var number = Generators.integers(1, Integer.MAX_VALUE).next();

            var ref = new PullRequestRef(repository, number);

            assertAll("the pull-request reference",
                    () -> assertEquals(repository, ref.repository(), "the repository is kept unchanged"),
                    () -> assertEquals(number, ref.number(), "the pull-request number is kept"));
        }

        @Test
        @DisplayName("refuses a number below 1 (0 is the boundary of the contract)")
        void refusesNumberBelowOne() {
            var repository = repository();
            var negative = Generators.integers(Integer.MIN_VALUE, -1).next();

            assertAll("the refused numbers",
                    () -> assertThrows(IllegalArgumentException.class, () -> new PullRequestRef(repository, 0),
                            "the number zero is refused as a number"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new PullRequestRef(repository, negative),
                            "a negative number is refused as a number"));
        }

        @Test
        @DisplayName("refuses a null repository")
        void refusesNullRepository() {
            var number = Generators.integers(1, Integer.MAX_VALUE).next();

            assertThrows(NullPointerException.class, () -> new PullRequestRef(null, number),
                    "a null repository is refused by the ref");
        }
    }

    @Nested
    @DisplayName("PullRequest")
    class Value {

        @Test
        @DisplayName("copies its changed paths into an immutable list")
        void copiesChangedPathsImmutably() {
            var first = text();
            var paths = new ArrayList<>(List.of(first));
            var pullRequest = validPullRequest().changedPaths(paths).build();
            paths.add(text());
            var returned = pullRequest.changedPaths();
            var added = text();

            assertAll("the changed paths",
                    () -> assertEquals(List.of(first), returned, "the paths are copied at construction"),
                    () -> assertThrows(UnsupportedOperationException.class, () -> returned.add(added),
                            "the returned changed paths are immutable"));
        }

        @Test
        @DisplayName("carries every component it is built with")
        void carriesEveryComponent() {
            var state = Generators.enumValues(PullRequest.State.class).next();
            var title = text();
            var body = text();
            var baseBranch = text();
            var headBranch = text();
            var headSha = text();
            var headRepository = repository();
            var headWritable = Generators.booleans().next();
            var completeness = Generators.enumValues(PullRequest.PathCompleteness.class).next();

            var pullRequest = validPullRequest().state(state).title(title).body(body).baseBranch(baseBranch)
                    .headBranch(headBranch).headSha(headSha).headRepositoryRef(headRepository)
                    .headWritable(headWritable).pathCompleteness(completeness).build();

            assertAll("the pull request",
                    () -> assertEquals(state, pullRequest.state(), "the state is carried unchanged"),
                    () -> assertEquals(title, pullRequest.title(), "the title text is carried unchanged"),
                    () -> assertEquals(body, pullRequest.body(), "the body text is carried unchanged"),
                    () -> assertEquals(baseBranch, pullRequest.baseBranch(), "the base branch is carried as is"),
                    () -> assertEquals(headBranch, pullRequest.headBranch(), "the head branch is carried as is"),
                    () -> assertEquals(headSha, pullRequest.headSha(), "the head commit is carried unchanged"),
                    () -> assertEquals(headRepository, pullRequest.headRepository().orElseThrow(),
                            "the head repository is exposed when known"),
                    () -> assertEquals(headWritable, pullRequest.headWritable(),
                            "the head writability is carried as is"),
                    () -> assertEquals(completeness, pullRequest.pathCompleteness(),
                            "the path completeness is carried as is"));
        }

        @Test
        @DisplayName("keeps an empty body")
        void keepsEmptyBody() {
            var pullRequest = validPullRequest().body("").build();

            assertEquals("", pullRequest.body(), "an empty body is kept as an empty text");
        }

        @Test
        @DisplayName("exposes an absent head repository as an empty Optional")
        void exposesAbsentHeadRepositoryAsEmpty() {
            var pullRequest = validPullRequest().headRepositoryRef(null).build();

            assertTrue(pullRequest.headRepository().isEmpty(), "an unknown head repository is empty");
        }

        @Test
        @DisplayName("refuses a blank title, branch or head commit")
        void refusesBlankRequiredText() {
            var blankTitle = validPullRequest().title(" ");
            var blankBase = validPullRequest().baseBranch(" ");
            var blankHead = validPullRequest().headBranch(" ");
            var blankSha = validPullRequest().headSha(" ");

            assertAll("the blank required text",
                    () -> assertThrows(IllegalArgumentException.class, blankTitle::build,
                            "a blank title is refused by the record"),
                    () -> assertThrows(IllegalArgumentException.class, blankBase::build,
                            "a blank base branch is refused by the record"),
                    () -> assertThrows(IllegalArgumentException.class, blankHead::build,
                            "a blank head branch is refused by the record"),
                    () -> assertThrows(IllegalArgumentException.class, blankSha::build,
                            "a blank head commit is refused by the record"));
        }

        @Test
        @DisplayName("refuses a null required component")
        void refusesNullComponents() {
            var nullState = validPullRequest().state(null);
            var nullTitle = validPullRequest().title(null);
            var nullBody = validPullRequest().body(null);
            var nullBase = validPullRequest().baseBranch(null);
            var nullHead = validPullRequest().headBranch(null);
            var nullSha = validPullRequest().headSha(null);
            var nullPaths = validPullRequest().changedPaths(null);
            var nullCompleteness = validPullRequest().pathCompleteness(null);

            assertAll("the null components",
                    () -> assertThrows(NullPointerException.class, nullState::build,
                            "a null state is refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullTitle::build,
                            "a null title is refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullBody::build,
                            "a null body is refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullBase::build,
                            "a null base branch is refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullHead::build,
                            "a null head branch is refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullSha::build,
                            "a null head commit is refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullPaths::build,
                            "null changed paths are refused by the record"),
                    () -> assertThrows(NullPointerException.class, nullCompleteness::build,
                            "a null completeness is refused by the record"));
        }

        @Test
        @DisplayName("names its states and completeness values as the contract literals")
        void namesContractLiterals() {
            assertAll("the contract names",
                    () -> assertEquals(List.of("OPEN", "CLOSED", "MERGED"),
                            Arrays.stream(PullRequest.State.values()).map(Enum::name).toList(),
                            "the states are the contract literals"),
                    () -> assertEquals(List.of("COMPLETE", "PAGE_CAP", "PROVIDER_RESULT_CAP"),
                            Arrays.stream(PullRequest.PathCompleteness.values()).map(Enum::name).toList(),
                            "the completeness values are the literals"));
        }
    }
}
