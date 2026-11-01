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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("ForgeUrl")
class ForgeUrlTest {

    @Test
    @DisplayName("resolves a GitHub issue URL")
    void resolvesGitHubIssue() {
        var resolution = ForgeUrl.resolve("https://github.com/cuioss/pm-mcp-core/issues/12");

        assertEquals(ForgeUrl.Outcome.RESOLVED, resolution.outcome(), "the URL is resolved");
        var url = resolution.url().orElseThrow();
        assertAll("the resolved source",
                                () -> assertEquals("github", url.forge(), "the forge is github"),
                                () -> assertEquals("cuioss", url.repository().ownerOrPath(), "the owner is resolved"),
                                () -> assertEquals("pm-mcp-core", url.repository().name(), "the repository is resolved"),
                                () -> assertEquals("https://github.com", url.repository().origin(), "the origin is resolved"),
                                () -> assertEquals(12, url.number(), "the number is resolved"),
                                () -> assertEquals(ForgeUrl.Kind.ISSUE, url.kind(), "an issue path is an issue"));
    }

    @Test
    @DisplayName("recognises a GitHub pull request URL")
    void recognisesPullRequest() {
        var url = ForgeUrl.resolve("https://github.com/cuioss/pm-mcp-core/pull/7").url().orElseThrow();

        assertEquals(ForgeUrl.Kind.PULL_REQUEST, url.kind(), "a pull path is a pull request");
        assertEquals(7, url.number(), "the pull request number is resolved");
    }

    @Test
    @DisplayName("resolves a Forgejo and a Codeberg URL against the built-in table")
    void resolvesForgejo() {
        var url = ForgeUrl.resolve("https://codeberg.org/forgejo/forgejo/issues/3").url().orElseThrow();

        assertEquals("forgejo", url.forge(), "the forge is forgejo");
        assertEquals("forgejo", url.repository().name(), "the repository is resolved");
    }

    @Test
    @DisplayName("resolves a GitLab issue and a nested group merge request")
    void resolvesGitLab() {
        var issue = ForgeUrl.resolve("https://gitlab.com/group/project/-/issues/9").url().orElseThrow();
        var merge = ForgeUrl.resolve("https://gitlab.com/group/sub/project/-/merge_requests/42").url()
                                .orElseThrow();

        assertAll("the GitLab grammar",
                                () -> assertEquals("group", issue.repository().ownerOrPath(), "the group is resolved"),
                                () -> assertEquals("project", issue.repository().name(), "the project is resolved"),
                                () -> assertEquals(ForgeUrl.Kind.ISSUE, issue.kind(), "an issue path is an issue"),
                                () -> assertEquals("group/sub", merge.repository().ownerOrPath(), "the nested group is resolved"),
                                () -> assertEquals(ForgeUrl.Kind.PULL_REQUEST, merge.kind(), "a merge request is a pull request"));
    }

    @Test
    @DisplayName("ignores a trailing slash, a query and a fragment")
    void ignoresTrailingSlashQueryAndFragment() {
        var trailing = ForgeUrl.resolve("https://github.com/cuioss/pm/issues/5/").url().orElseThrow();
        var query = ForgeUrl.resolve("https://github.com/cuioss/pm/issues/6?note=1#comment").url().orElseThrow();

        assertEquals(5, trailing.number(), "a trailing slash is ignored");
        assertEquals(6, query.number(), "a query and fragment are ignored");
    }

    @Test
    @DisplayName("uses a host-to-forge table the caller passes")
    void usesCallerTable() {
        var hosts = Map.of("forge.example", "forgejo");

        var url = ForgeUrl.resolve("https://forge.example/owner/repo/issues/4", hosts).url().orElseThrow();

        assertEquals("forgejo", url.forge(), "the caller's table names the forge");
        assertEquals("https://forge.example", url.repository().origin(), "the caller's host is the origin");
    }

    @Test
    @DisplayName("reports an unknown host as the outcome unknown_forge")
    void reportsUnknownHost() {
        var resolution = ForgeUrl.resolve("https://unknown.example/owner/repo/issues/4");

        assertAll("the unknown-forge outcome",
                                () -> assertEquals(ForgeUrl.Outcome.UNKNOWN_FORGE, resolution.outcome(), "the host is unknown"),
                                () -> assertTrue(resolution.url().isEmpty(), "an unknown host carries no URL"),
                                () -> assertTrue(resolution.detail().contains("unknown_forge"), "the detail names the outcome"));
    }

    @Test
    @DisplayName("reports a hostile or malformed URL as invalid")
    void reportsInvalidUrls() {
        assertAll("the invalid URLs",
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://user:pass@github.com/o/r/issues/1").outcome(),
                                                        "user information is refused"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://github.com/o/../../r/issues/1").outcome(),
                                                        "a parent segment is refused"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("http://github.com/o/r/issues/1").outcome(),
                                                        "a cleartext URL is refused"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://github.com/o/r/issues/0").outcome(),
                                                        "a zero number is refused"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://github.com/o/r/issues/007").outcome(),
                                                        "a padded number is refused"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://github.com/o/r").outcome(),
                                                        "a repository URL is no source URL"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://github.com/o/r/releases/1").outcome(),
                                                        "an unknown operation is refused"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://gitlab.com/project/-/issues/1").outcome(),
                                                        "GitLab needs a group and a project"),
                                () -> assertEquals(ForgeUrl.Outcome.INVALID,
                                                        ForgeUrl.resolve("https://github.com/" + "a".repeat(300) + "/r/issues/1").outcome(),
                                                        "an over-long segment is refused"));
    }

    @Test
    @DisplayName("refuses a value whose repository names another forge")
    void refusesForgeMismatch() {
        var repository = new RepositoryRef("github", "https://github.com", "cuioss", "pm-mcp-core");

        assertThrows(IllegalArgumentException.class,
                                () -> ForgeUrl.builder().forge("gitlab").repository(repository).number(1)
                                                        .kind(ForgeUrl.Kind.ISSUE).build(),
                                "a mismatched forge is refused");
    }

    @Test
    @DisplayName("refuses a resolution without a URL and a number below one")
    void refusesMalformedValues() {
        assertAll("the value invariants",
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new ForgeUrl.Resolution(ForgeUrl.Outcome.RESOLVED, null, ""),
                                                        "a resolved outcome carries a URL"),
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> ForgeUrl.builder().forge("github")
                                                                                .repository(new RepositoryRef("github", "https://github.com", "o", "r"))
                                                                                .number(0).kind(ForgeUrl.Kind.ISSUE).build(),
                                                        "a number below one is refused"));
    }
}
