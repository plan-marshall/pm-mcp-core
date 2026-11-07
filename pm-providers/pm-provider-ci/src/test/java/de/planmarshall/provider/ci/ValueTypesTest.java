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

import de.planmarshall.provider.ci.testing.generators.CiGenerators;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value types of the CI contract. Logins, user ids, permission entries, scopes, instants, titles, bodies, labels,
 * assignees, URLs, comment ids and issue numbers are generated, and a valid repository that is not the case under test
 * comes from {@link CiGenerators#repositoryRefs()}. What stays literal is contract or boundary data and each test says
 * so: the token-kind wire name {@code github_app}, the issue state {@code open} as the provider names it, the blank
 * text {@code " "}, the numbers {@code 0} and {@code -1}, and in the {@link RepositoryRef} normalisation and refusal
 * cases the provider ids, the origins, the owner paths and the segments ({@code o}, {@code r}, the over-long name),
 * which are the reference grammar under test.
 */
@EnableGeneratorController
@DisplayName("The value types of the CI contract")
class ValueTypesTest {

    private static String text() {
        return "x" + Generators.nonEmptyStrings().next();
    }

    private static String word() {
        return Generators.letterStrings(1, 20).next();
    }

    private static String issueUrl() {
        var repository = CiGenerators.repositoryRefs().next();
        return repository.origin() + "/" + repository.ownerOrPath() + "/" + repository.name() + "/issues/"
                + Generators.integers(1, 99_999).next();
    }

    @Test
    @DisplayName("TokenKind round-trips every wire name (github_app is the wire contract)")
    void tokenKindRoundTrips() {
        for (TokenKind kind : TokenKind.values()) {
            assertEquals(kind, TokenKind.fromWireName(kind.wireName()).orElseThrow(),
                    "the wire name names its kind");
        }
        assertTrue(TokenKind.fromWireName(word()).isEmpty(), "an unknown wire name is empty");
        assertEquals("github_app", TokenKind.GITHUB_APP.wireName(), "the App is its own kind");
    }

    @Test
    @DisplayName("RepositoryRef normalises the origin and keeps a group path (origins and paths are the grammar)")
    void repositoryRefNormalisesOrigin() {
        var github = new RepositoryRef("github", "https://GitHub.com/", "cuioss", "pm-mcp-core");
        var gitlab = new RepositoryRef("gitlab", "https://gitlab.com", "group/sub", "project");

        assertAll("the resolved references",
                () -> assertEquals("https://github.com", github.origin(), "the origin is normalised"),
                () -> assertEquals("group/sub", gitlab.ownerOrPath(), "a group path is kept"));
    }

    @Test
    @DisplayName("RepositoryRef refuses an origin with a path and a dotted segment (the values are the grammar)")
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
    @DisplayName("IssueRef refuses a number below one (0 and -1 are the boundary)")
    void issueRefRefusesZero() {
        var repository = CiGenerators.repositoryRefs().next();
        var number = Generators.integers(1, Integer.MAX_VALUE).next();

        assertAll("the issue reference",
                () -> assertEquals(number, new IssueRef(repository, number).number(), "the issue number is kept"),
                () -> assertThrows(IllegalArgumentException.class, () -> new IssueRef(repository, 0),
                        "a number below one is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new IssueRef(repository, -1),
                        "a negative number is refused"));
    }

    @Test
    @DisplayName("Identity copies its collections and carries both App identities")
    void identityCopiesCollections() {
        var permissions = new HashMap<String, String>(Map.of(word(), word()));
        var scopes = new ArrayList<>(List.of(word()));
        var operator = word();
        var expiresAt = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
        var identity = Identity.builder().login(word()).userId(text()).tokenKind(TokenKind.GITHUB_APP)
                .operatorLogin(operator).permissions(permissions).scopes(scopes)
                .expiresAt(expiresAt).build();
        // the "+" prefix keeps the added key apart from the generated letters-only key already present
        permissions.put("+" + word(), word());
        scopes.add(word());
        var returnedPermissions = identity.permissions();
        var returnedScopes = identity.scopes();
        var addedKey = word();
        var addedValue = word();
        var addedScope = word();

        assertAll("the identity",
                () -> assertEquals(1, returnedPermissions.size(), "the permissions are copied"),
                () -> assertEquals(1, returnedScopes.size(), "the scopes are copied"),
                () -> assertEquals(operator, identity.operatorLogin(), "the operator login is carried"),
                () -> assertEquals(expiresAt, identity.expiresAt(), "the expiry is carried"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> returnedPermissions.put(addedKey, addedValue),
                        "the returned permissions are immutable"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> returnedScopes.add(addedScope),
                        "the returned scopes are immutable"));
    }

    @Test
    @DisplayName("Identity refuses a blank login and a missing token kind (the blank login is the boundary)")
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
    @DisplayName("Issue copies its labels and keeps a null milestone (the state open is the provider's name)")
    void issueCopiesLabels() {
        var labels = new ArrayList<>(List.of(word()));
        var title = text();
        var body = text();
        var addedLabel = word();
        var addedAssignee = word();
        var issue = Issue.builder().title(title).body(body).labels(labels).assignees(List.of()).state("open")
                .url(issueUrl()).build();
        labels.add(word());
        var returnedLabels = issue.labels();
        var returnedAssignees = issue.assignees();

        assertAll("the issue",
                () -> assertEquals(1, returnedLabels.size(), "the labels are copied"),
                () -> assertNull(issue.milestone(), "a missing milestone stays null"),
                () -> assertEquals(title, issue.title(), "the title text is carried unchanged"),
                () -> assertEquals(body, issue.body(), "the body text is carried unchanged"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> returnedLabels.add(addedLabel),
                        "the returned labels are immutable"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> returnedAssignees.add(addedAssignee),
                        "the returned assignees are immutable"));
    }

    @Test
    @DisplayName("Issue refuses a blank milestone and a null body (the blank milestone is the boundary)")
    void issueRefusesInvalidValues() {
        var empty = List.<String>of();
        var title = text();
        var url = issueUrl();
        var blankMilestone = Issue.builder().title(title).body("").labels(empty).milestone(" ")
                .assignees(empty).state("open").url(url);
        var missingBody = Issue.builder().title(title).labels(empty).assignees(empty).state("open").url(url);
        assertAll("the refused issues",
                () -> assertThrows(IllegalArgumentException.class, blankMilestone::build,
                        "a blank milestone is refused"),
                () -> assertThrows(NullPointerException.class, missingBody::build, "a missing body is refused"));
    }

    @Test
    @DisplayName("RepositoryRef covers the remaining origin and path branches (the values are the grammar)")
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
    @DisplayName("Identity and Issue refuse a null collection (the state open is the provider's name)")
    void refusesNullCollections() {
        var value = text();
        var empty = List.<String>of();
        var url = issueUrl();
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
    @DisplayName("IssueComment refuses a blank id and a blank URL (the blank text is the boundary)")
    void issueCommentRefusesBlankValues() {
        var id = String.valueOf(Generators.longs(1, Long.MAX_VALUE).next());
        var url = issueUrl();

        assertAll("the refused comments",
                () -> assertThrows(IllegalArgumentException.class, () -> new IssueComment(" ", url),
                        "a blank comment id is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new IssueComment(id, " "),
                        "a blank comment URL is refused"),
                () -> assertEquals(id, new IssueComment(id, url).id(), "the comment id is kept"));
    }
}
