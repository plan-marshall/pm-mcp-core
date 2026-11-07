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
import java.util.Locale;
import java.util.Map;

import de.planmarshall.provider.ci.testing.generators.CiGenerators;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The issue and pull-request URL of a source. The owners, group paths, repository names, numbers, caller hosts, query
 * values and fragments that are not the URL grammar under test are generated, and a valid repository that is not the
 * case under test comes from {@link CiGenerators#repositoryRefs()}. What stays literal is the URL grammar and each test
 * says so: the forge hosts and origins ({@code github.com}, {@code gitlab.com}, {@code codeberg.org}), the provider ids,
 * the operation segments ({@code issues}, {@code pull}, {@code pulls}, {@code -/merge_requests}), the hostile parts (user
 * information, dotted, encoded and empty segments, a backslash, a reserved character, cleartext, a trailing-dot or
 * upper-case host, fullwidth digits, padded and zero numbers), the refusal details, and the boundary literals: the
 * 21/22-group nesting limit, the 25-segment limit, the over-long segment, path and number.
 */
@EnableGeneratorController
@DisplayName("ForgeUrl")
class ForgeUrlTest {

    @Test
    @DisplayName("resolves a GitHub issue URL (the github.com issue path is the grammar)")
    void resolvesGitHubIssue() {
        var owner = segment();
        var name = segment();
        var number = number();

        var resolution = ForgeUrl.resolve("https://github.com/%s/%s/issues/%d".formatted(owner, name, number));

        assertEquals(ForgeUrl.Outcome.RESOLVED, resolution.outcome(), "the pull request URL is resolved");
        var url = resolution.url().orElseThrow();
        assertAll("the resolved source",
                () -> assertEquals("github", url.forge(), "the resolved forge is github"),
                () -> assertEquals(owner, url.repository().ownerOrPath(), "the owner is resolved"),
                () -> assertEquals(name, url.repository().name(), "the repository is resolved"),
                () -> assertEquals("https://github.com", url.repository().origin(), "the origin is resolved"),
                () -> assertEquals(number, url.number(), "the number is resolved"),
                () -> assertEquals(ForgeUrl.Kind.ISSUE, url.kind(), "an issue path is an issue"));
    }

    @Test
    @DisplayName("recognises a GitHub pull request URL (the github.com pull path is the grammar)")
    void recognisesPullRequest() {
        var number = number();

        var url = ForgeUrl.resolve("https://github.com/%s/%s/pull/%d".formatted(segment(), segment(), number)).url()
                .orElseThrow();

        assertEquals(ForgeUrl.Kind.PULL_REQUEST, url.kind(), "a pull path is a pull request");
        assertEquals(number, url.number(), "the pull request number is resolved");
    }

    /**
     * The URL templates and hosts are the pull-request path grammar of each forge, so they are contract literals;
     * the owner, the name and the number are generated.
     */
    @ParameterizedTest(name = "{0} pull-request path {2}")
    @CsvSource({
            "github, https://github.com, https://github.com/%s/%s/pull/%d",
            "forgejo, https://codeberg.org, https://codeberg.org/%s/%s/pulls/%d",
            "gitlab, https://gitlab.com, https://gitlab.com/%s/%s/-/merge_requests/%d"
    })
    @DisplayName("names the pull request of each forge's pull-request URL (grammar literals)")
    void namesPullRequestOfEachForge(String forge, String origin, String template) {
        var owner = segment();
        var name = segment();
        var number = number();
        var expected = new PullRequestRef(new RepositoryRef(forge, origin, owner, name), number);

        var pullRequest = ForgeUrl.resolve(template.formatted(owner, name, number)).url().orElseThrow()
                .pullRequest();

        assertEquals(expected, pullRequest.orElseThrow(), "the accessor names repository and number");
    }

    @Test
    @DisplayName("names no pull request for an issue URL (the github.com issue path is the grammar)")
    void namesNoPullRequestForIssue() {
        var url = ForgeUrl.resolve("https://github.com/%s/%s/issues/%d".formatted(segment(), segment(), number()))
                .url().orElseThrow();

        assertTrue(url.pullRequest().isEmpty(), "an issue URL names no pull request");
    }

    @Test
    @DisplayName("resolves a Forgejo and a Codeberg URL against the built-in table (codeberg.org is the contract)")
    void resolvesForgejo() {
        var name = segment();

        var url = ForgeUrl.resolve("https://codeberg.org/%s/%s/issues/%d".formatted(segment(), name, number())).url()
                .orElseThrow();

        assertEquals("forgejo", url.forge(), "the resolved forge is forgejo");
        assertEquals(name, url.repository().name(), "the repository is resolved");
    }

    @Test
    @DisplayName("resolves a GitLab issue and a nested group merge request (the gitlab.com paths are the grammar)")
    void resolvesGitLab() {
        var group = segment();
        var subGroup = segment();
        var project = segment();

        var issue = ForgeUrl.resolve("https://gitlab.com/%s/%s/-/issues/%d".formatted(group, project, number())).url()
                .orElseThrow();
        var merge = ForgeUrl.resolve("https://gitlab.com/%s/%s/%s/-/merge_requests/%d"
                .formatted(group, subGroup, project, number())).url().orElseThrow();

        assertAll("the GitLab grammar",
                () -> assertEquals(group, issue.repository().ownerOrPath(), "the group is resolved"),
                () -> assertEquals(project, issue.repository().name(), "the project is resolved"),
                () -> assertEquals(ForgeUrl.Kind.ISSUE, issue.kind(), "an issue path is an issue"),
                () -> assertEquals(group + "/" + subGroup, merge.repository().ownerOrPath(),
                        "the nested group is resolved"),
                () -> assertEquals(ForgeUrl.Kind.PULL_REQUEST, merge.kind(), "a merge request is a pull request"));
    }

    @Test
    @DisplayName("ignores a trailing slash, a query and a fragment (the slash, ? and # are the grammar)")
    void ignoresTrailingSlashQueryAndFragment() {
        var trailingNumber = number();
        var queryNumber = number();

        var trailing = ForgeUrl.resolve("https://github.com/%s/%s/issues/%d/".formatted(segment(), segment(),
                trailingNumber)).url().orElseThrow();
        var query = ForgeUrl.resolve("https://github.com/%s/%s/issues/%d?%s=%s#%s".formatted(segment(), segment(),
                queryNumber, segment(), segment(), segment())).url().orElseThrow();

        assertEquals(trailingNumber, trailing.number(), "a trailing slash is ignored");
        assertEquals(queryNumber, query.number(), "a query and fragment are ignored");
    }

    @Test
    @DisplayName("uses a host-to-forge table the caller passes (the provider id forgejo is the contract)")
    void usesCallerTable() {
        var host = host();
        var hosts = Map.of(host, "forgejo");

        var url = ForgeUrl.resolve("https://%s/%s/%s/issues/%d".formatted(host, segment(), segment(), number()), hosts)
                .url().orElseThrow();

        assertEquals("forgejo", url.forge(), "the caller's table names the forge");
        assertEquals("https://" + host, url.repository().origin(), "the caller's host is the origin");
    }

    @Test
    @DisplayName("reports an unknown host as the outcome unknown_forge (the outcome token is the contract)")
    void reportsUnknownHost() {
        var resolution = ForgeUrl.resolve("https://%s/%s/%s/issues/%d".formatted(host(), segment(), segment(),
                number()));

        assertAll("the unknown-forge outcome",
                () -> assertEquals(ForgeUrl.Outcome.UNKNOWN_FORGE, resolution.outcome(), "the host is not in the table"),
                () -> assertTrue(resolution.url().isEmpty(), "an unknown host carries no URL"),
                () -> assertTrue(resolution.detail().contains("unknown_forge"), "the detail names the outcome"));
    }

    @Test
    @DisplayName("reports a hostile or malformed URL as invalid (the hostile parts and boundaries are the grammar)")
    void reportsInvalidUrls() {
        var owner = segment();
        var name = segment();
        var number = number();

        assertAll("the invalid URLs",
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://%s:%s@github.com/%s/%s/issues/%d".formatted(segment(), segment(),
                                owner, name, number)).outcome(),
                        "user information is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/../../%s/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a parent segment is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("http://github.com/%s/%s/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a cleartext URL is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s/issues/0".formatted(owner, name)).outcome(),
                        "a zero number is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s/issues/007".formatted(owner, name)).outcome(),
                        "a padded number is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s".formatted(owner, name)).outcome(),
                        "a repository URL is no source URL"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s/releases/%d".formatted(owner, name, number))
                                .outcome(),
                        "an unknown operation is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://gitlab.com/%s/-/issues/%d".formatted(name, number)).outcome(),
                        "GitLab needs a group and a project"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/" + "a".repeat(300) + "/%s/issues/%d".formatted(name,
                                number)).outcome(),
                        "an over-long segment is refused"));
    }

    @Test
    @DisplayName("refuses non-ASCII digits in the issue number (the fullwidth digits are the grammar)")
    void refusesNonAsciiDigits() {
        assertEquals(ForgeUrl.Outcome.INVALID,
                ForgeUrl.resolve("https://github.com/%s/%s/issues/%%EF%%BC%%91%%EF%%BC%%92".formatted(segment(),
                        segment())).outcome(),
                "fullwidth digits are not an issue number");
    }

    @Test
    @DisplayName("refuses a value whose repository names another forge (github against gitlab is the case)")
    void refusesForgeMismatch() {
        var repository = new RepositoryRef("github", "https://github.com", segment(), segment());
        var builder = ForgeUrl.builder().forge("gitlab").repository(repository).number(number())
                .kind(ForgeUrl.Kind.ISSUE);

        assertThrows(IllegalArgumentException.class, builder::build, "a mismatched forge is refused");
    }

    @Test
    @DisplayName("covers the remaining URL-grammar branches (ports, operations and the nesting limit are the grammar)")
    void coversRemainingGrammarBranches() {
        var owner = segment();
        var name = segment();
        var number = number();
        var port = Generators.integers(1024, 65535).next();

        assertAll("the remaining grammar branches",
                () -> assertEquals("https://github.com:" + port,
                        ForgeUrl.resolve("https://github.com:%d/%s/%s/issues/%d".formatted(port, owner, name, number))
                                .url().orElseThrow().repository().origin(),
                        "an explicit port is kept in the origin"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://gitlab.com/%s/%s/-/pipelines/%d".formatted(owner, name, number))
                                .outcome(),
                        "an unknown GitLab operation is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://gitlab.com/%s/%s/-/issues/0".formatted(owner, name)).outcome(),
                        "a GitLab zero number is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s/issues/1234567890".formatted(owner, name))
                                .outcome(),
                        "an over-long number is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s//%s/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "an empty path segment is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://gitlab.com/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/-/issues/1")
                                .outcome(),
                        "a project nested in 22 groups is refused"),
                () -> assertEquals(ForgeUrl.Outcome.RESOLVED,
                        ForgeUrl.resolve("https://gitlab.com/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/-/issues/1")
                                .outcome(),
                        "a project nested in 21 groups is accepted"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://gitlab.com/%s/./%s/-/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a current-directory segment is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https:/github.com/%s/%s/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a URL without a host is refused"),
                () -> assertEquals(number,
                        ForgeUrl.resolve("  https://github.com/%s/%s/issues/%d  ".formatted(owner, name, number))
                                .url().orElseThrow().number(),
                        "surrounding whitespace is stripped"),
                () -> assertEquals(ForgeUrl.Kind.PULL_REQUEST,
                        ForgeUrl.resolve("https://codeberg.org/%s/%s/pulls/%d".formatted(owner, name, number)).url()
                                .orElseThrow().kind(),
                        "a Forgejo pull-request path is a pull request"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s%%3F/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a reserved character in a repository name is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://gitlab.com/%s%%3F/%s/-/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a reserved character in a GitLab path is refused"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/%s/%s/pulls/%d".formatted(owner, name, number))
                                .outcome(),
                        "GitHub has no plural pulls path"));
    }

    @Test
    @DisplayName("covers the hostile path and host normalisation branches (details and boundaries are the contract)")
    void coversHostilePathAndHostBranches() {
        var owner = segment();
        var name = segment();
        var number = number();
        var encodedSlash = ForgeUrl.resolve("https://github.com/%s%%2F%s/issues/%d".formatted(owner, name, number))
                .url().orElseThrow();
        var overLongPath = ForgeUrl.resolve("https://gitlab.com/" + ("a".repeat(100) + "/").repeat(21)
                + "-/issues/1");
        var backslash = ForgeUrl.resolve("https://github.com/%s/%s%%5Cissues/%d".formatted(owner, name, number));
        var encodedParent = ForgeUrl.resolve("https://github.com/%s/%%2e%%2e/%s/issues/%d".formatted(owner, name,
                number));
        var tooManySegments = ForgeUrl.resolve(
                "https://github.com/a/b/c/d/e/f/g/h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z");

        assertAll("the hostile path and host branches",
                () -> assertEquals("the path is not a plain URL path", overLongPath.detail(),
                        "a path over the length bound is refused by the path rule"),
                () -> assertEquals("the path is not a plain URL path", backslash.detail(),
                        "a backslash in the path is refused by the path rule"),
                () -> assertEquals("the path has an empty, dotted or over-long segment", encodedParent.detail(),
                        "an encoded parent segment is refused by the segment rule"),
                () -> assertEquals("the path has an empty, dotted or over-long segment", tooManySegments.detail(),
                        "a path over 25 segments is refused"),
                () -> assertEquals(owner, encodedSlash.repository().ownerOrPath(),
                        "an encoded slash separates owner and name"),
                () -> assertEquals(name, encodedSlash.repository().name(),
                        "an encoded slash separates owner and name"),
                () -> assertEquals("https://github.com",
                        ForgeUrl.resolve("https://GitHub.COM/%s/%s/issues/%d".formatted(owner, name, number)).url()
                                .orElseThrow().repository().origin(),
                        "an uppercase host is lowercased"),
                () -> assertEquals(ForgeUrl.Outcome.UNKNOWN_FORGE,
                        ForgeUrl.resolve("https://github.com./%s/%s/issues/%d".formatted(owner, name, number))
                                .outcome(),
                        "a trailing-dot host is an unknown forge"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                        ForgeUrl.resolve("https://github.com/?%s=%s".formatted(segment(), segment())).outcome(),
                        "a query-only URL has no issue path"),
                () -> assertEquals(ForgeUrl.Outcome.INVALID, ForgeUrl.resolve("").outcome(),
                        "an empty URL is refused"));
    }

    @Test
    @DisplayName("refuses a null URL and a null host table")
    void refusesNullArguments() {
        var url = "https://github.com/%s/%s/issues/%d".formatted(segment(), segment(), number());

        assertAll("the null arguments",
                () -> assertThrows(NullPointerException.class, () -> ForgeUrl.resolve(null),
                        "a null URL is refused"),
                () -> assertThrows(NullPointerException.class, () -> ForgeUrl.resolve(url, null),
                        "a null host table is refused"));
    }

    @Test
    @DisplayName("refuses a forge outside the provider id pattern (the upper-case id Bad is the case)")
    void refusesInvalidForge() {
        var builder = ForgeUrl.builder().forge("Bad").repository(CiGenerators.repositoryRefs().next())
                .number(number()).kind(ForgeUrl.Kind.ISSUE);

        assertThrows(IllegalArgumentException.class, builder::build, "a forge outside the pattern is refused");
    }

    @Test
    @DisplayName("refuses a resolution without a URL and a number below one (0 is the boundary)")
    void refusesMalformedValues() {
        var repository = CiGenerators.repositoryRefs().next();
        var zeroNumber = ForgeUrl.builder().forge(repository.forge()).repository(repository).number(0)
                .kind(ForgeUrl.Kind.ISSUE);
        assertAll("the value invariants",
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new ForgeUrl.Resolution(ForgeUrl.Outcome.RESOLVED, null, ""),
                        "a resolved outcome carries a URL"),
                () -> assertThrows(IllegalArgumentException.class, zeroNumber::build,
                        "a number below one is refused"));
    }

    private static String segment() {
        return Generators.letterStrings(1, 20).next();
    }

    private static int number() {
        return Generators.integers(1, 99_999_999).next();
    }

    private static String host() {
        return segment().toLowerCase(Locale.ROOT) + ".example";
    }
}
