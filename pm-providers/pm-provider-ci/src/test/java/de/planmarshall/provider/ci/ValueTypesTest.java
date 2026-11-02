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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("The value types of the CI contract")
class ValueTypesTest {

    private static String text() {
        return "x" + Generators.nonEmptyStrings().next();
    }

    @Test
    @DisplayName("TokenKind round-trips every wire name")
    void tokenKindRoundTrips() {
        for (TokenKind kind : TokenKind.values()) {
            assertEquals(kind, TokenKind.fromWireName(kind.wireName()).orElseThrow(),
                                    "the wire name names its kind");
        }
        assertTrue(TokenKind.fromWireName("nonsense").isEmpty(), "an unknown wire name is empty");
        assertEquals("github_app", TokenKind.GITHUB_APP.wireName(), "the App is its own kind");
    }

    @Test
    @DisplayName("RepositoryRef normalises the origin and keeps a group path")
    void repositoryRefNormalisesOrigin() {
        var github = new RepositoryRef("github", "https://GitHub.com/", "cuioss", "pm-mcp-core");
        var gitlab = new RepositoryRef("gitlab", "https://gitlab.com", "group/sub", "project");

        assertAll("the resolved references",
                                () -> assertEquals("https://github.com", github.origin(), "the origin is normalised"),
                                () -> assertEquals("group/sub", gitlab.ownerOrPath(), "a group path is kept"));
    }

    @Test
    @DisplayName("RepositoryRef refuses an origin with a path and a dotted segment")
    void repositoryRefRefusesHostileValues() {
        assertAll("the refused references",
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new RepositoryRef("github", "https://github.com/api", "o", "r"),
                                                        "an origin with a path is refused"),
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new RepositoryRef("github", "https://user@github.com", "o", "r"),
                                                        "an origin with user information is refused"),
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new RepositoryRef("github", "http://github.com", "o", "r"),
                                                        "a cleartext origin is refused"),
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new RepositoryRef("github", "https://github.com", "o/../r", "r"),
                                                        "a dotted path segment is refused"),
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new RepositoryRef("github", "https://github.com", "o", "a name"),
                                                        "a name with a space is refused"),
                                () -> assertThrows(IllegalArgumentException.class,
                                                        () -> new RepositoryRef("Bad", "https://github.com", "o", "r"),
                                                        "a forge outside the pattern is refused"));
    }

    @Test
    @DisplayName("IssueRef refuses a number below one")
    void issueRefRefusesZero() {
        var repository = new RepositoryRef("github", "https://github.com", "o", "r");

        assertAll("the issue reference",
                                () -> assertEquals(3, new IssueRef(repository, 3).number(), "the issue number is kept"),
                                () -> assertThrows(IllegalArgumentException.class, () -> new IssueRef(repository, 0),
                                                        "a number below one is refused"),
                                 () -> assertThrows(IllegalArgumentException.class, () -> new IssueRef(repository, -1),
                                                         "a negative number is refused"));
    }

    @Test
    @DisplayName("Identity copies its collections and carries both App identities")
    void identityCopiesCollections() {
        var permissions = new HashMap<String, String>(Map.of("issues", "write"));
        var scopes = new ArrayList<>(List.of("repo"));
        var identity = Identity.builder().login("bot[bot]").userId(text()).tokenKind(TokenKind.GITHUB_APP)
                                .operatorLogin("operator").permissions(permissions).scopes(scopes)
                                .expiresAt(Instant.EPOCH).build();
        permissions.put("contents", "write");
        scopes.add("admin");

        assertAll("the identity",
                                () -> assertEquals(1, identity.permissions().size(), "the permissions are copied"),
                                () -> assertEquals(1, identity.scopes().size(), "the scopes are copied"),
                                () -> assertEquals("operator", identity.operatorLogin(), "the operator login is carried"),
                                () -> assertEquals(Instant.EPOCH, identity.expiresAt(), "the expiry is carried"),
                                () -> assertThrows(UnsupportedOperationException.class,
                                                        () -> identity.permissions().put("contents", "write"),
                                                        "the returned permissions are immutable"),
                                () -> assertThrows(UnsupportedOperationException.class,
                                                        () -> identity.scopes().add("admin"),
                                                        "the returned scopes are immutable"));
    }

    @Test
    @DisplayName("Identity refuses a blank login and a missing token kind")
    void identityRefusesInvalidValues() {
        var userId = text();
        Map<String, String> permissions = Map.of();
        List<String> scopes = List.of();
        var blankLogin = Identity.builder().login(" ").userId(userId).tokenKind(TokenKind.GITHUB_CLASSIC_PAT)
                .permissions(permissions).scopes(scopes);
        var missingKind = Identity.builder().login(userId).userId(userId).permissions(permissions).scopes(scopes);
        var blankOperator = Identity.builder().login(userId).userId(userId).tokenKind(TokenKind.GITHUB_APP)
                .operatorLogin(" ").permissions(permissions).scopes(scopes);
        assertAll("the refused identities",
                () -> assertThrows(IllegalArgumentException.class, blankLogin::build, "a blank login is refused"),
                () -> assertThrows(NullPointerException.class, missingKind::build,
                        "a missing token kind is refused"),
                () -> assertThrows(IllegalArgumentException.class, blankOperator::build,
                        "a blank operator login is refused"));
    }

    @Test
    @DisplayName("Issue copies its labels and keeps a null milestone")
    void issueCopiesLabels() {
        var labels = new ArrayList<>(List.of("bug"));
        var title = text();
        var body = text();
        var issue = Issue.builder().title(title).body(body).labels(labels).assignees(List.of()).state("open")
                                .url("https://github.com/o/r/issues/1").build();
        labels.add("docs");

        assertAll("the issue",
                                () -> assertEquals(1, issue.labels().size(), "the labels are copied"),
                                () -> assertNull(issue.milestone(), "a missing milestone stays null"),
                                () -> assertEquals(title, issue.title(), "the title text is carried unchanged"),
                                () -> assertEquals(body, issue.body(), "the body text is carried unchanged"),
                                () -> assertThrows(UnsupportedOperationException.class,
                                                        () -> issue.labels().add("docs"),
                                                        "the returned labels are immutable"),
                                () -> assertThrows(UnsupportedOperationException.class,
                                                        () -> issue.assignees().add("alice"),
                                                        "the returned assignees are immutable"));
    }

    @Test
    @DisplayName("Issue refuses a blank milestone and a null body")
    void issueRefusesInvalidValues() {
        var empty = List.<String>of();
        var title = text();
        var url = "https://github.com/o/r/issues/1";
        var blankMilestone = Issue.builder().title(title).body("").labels(empty).milestone(" ")
                .assignees(empty).state("open").url(url);
        var missingBody = Issue.builder().title(title).labels(empty).assignees(empty).state("open").url(url);
        assertAll("the refused issues",
                () -> assertThrows(IllegalArgumentException.class, blankMilestone::build,
                        "a blank milestone is refused"),
                () -> assertThrows(NullPointerException.class, missingBody::build, "a missing body is refused"));
    }

    @Test
    @DisplayName("RepositoryRef covers the remaining origin and path branches")
    void repositoryRefCoversRemainingBranches() {
        var portOrigin = new RepositoryRef("github", "https://github.com:8443", "o", "r");
        var plainOrigin = new RepositoryRef("github", "https://github.com", "o", "r");
        var longName = "a".repeat(256);

        assertAll("the remaining references",
                () -> assertEquals("https://github.com:8443", portOrigin.origin(), "an explicit port is kept"),
                () -> assertEquals("https://github.com",
                        new RepositoryRef("github", "https://github.com:443", "o", "r").origin(),
                        "the default HTTPS port is dropped"),
                () -> assertEquals("https://github.com", plainOrigin.origin(), "a missing port is dropped"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com?x=1", "o", "r"),
                        "an origin with a query is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com#x", "o", "r"),
                        "an origin with a fragment is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https:/github.com", "o", "r"),
                        "an origin without a host is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com", "/o", "r"),
                        "a leading slash is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com", "o/", "r"),
                        "a trailing slash is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com", "", "r"),
                        "an empty path is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com", "o", "."),
                        "a current-directory name is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com", "o/r", "r"),
                        "a GitHub owner with a slash is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new RepositoryRef("github", "https://github.com", "o", longName),
                        "an over-long name is refused"));
    }

    @Test
    @DisplayName("Identity and Issue refuse a null collection")
    void refusesNullCollections() {
        var value = text();
        var empty = List.<String>of();
        var url = "https://github.com/o/r/issues/1";
        var missingPermissions = Identity.builder().login(value).userId(value).tokenKind(TokenKind.GITHUB_APP)
                .scopes(empty);
        var missingLabels = Issue.builder().title(value).body("").assignees(empty).state("open").url(url);
        assertAll("the null collections",
                () -> assertThrows(NullPointerException.class, missingPermissions::build,
                        "missing permissions are refused"),
                () -> assertThrows(NullPointerException.class, missingLabels::build,
                        "missing labels are refused"));
    }

    @Test
    @DisplayName("IssueComment refuses a blank id and a blank URL")
    void issueCommentRefusesBlankValues() {
        assertAll("the refused comments",
                                () -> assertThrows(IllegalArgumentException.class, () -> new IssueComment(" ", "https://x/1"),
                                                        "a blank comment id is refused"),
                                () -> assertThrows(IllegalArgumentException.class, () -> new IssueComment("1", " "),
                                                        "a blank comment URL is refused"),
                                () -> assertEquals("1", new IssueComment("1", "https://x/1").id(), "the comment id is kept"));
    }
}
