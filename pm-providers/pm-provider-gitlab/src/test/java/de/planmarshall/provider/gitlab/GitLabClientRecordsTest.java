/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.gitlab;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of the two value records of {@link GitLabClient} build the value the canonical constructor builds.
 * Neither record validates its components, so there is no constructor refusal for the builder to mirror. Every value
 * is generated.
 */
@EnableGeneratorController
@DisplayName("GitLabClient value records")
class GitLabClientRecordsTest {

    private static String text() {
        return Generators.letterStrings(1, 20).next();
    }

    @Test
    @DisplayName("the note builder builds the note the constructor builds")
    void noteBuilderEqualsConstructor() {
        var id = text();
        var author = text();
        var body = text();
        var resolvable = Generators.booleans().next();
        var resolved = Generators.booleans().next();

        var built = GitLabClient.Note.builder()
                .id(id)
                .author(author)
                .body(body)
                .resolvable(resolvable)
                .resolved(resolved)
                .build();
        var constructed = new GitLabClient.Note(id, author, body, resolvable, resolved);

        assertEquals(constructed, built, "the builder builds the same note value");
    }

    @Test
    @DisplayName("the token identity builder builds the constructor value")
    void tokenIdentityBuilderEqualsConstructor() {
        var id = text();
        var name = text();
        var userId = text();
        var scopes = List.of(text(), text());
        var active = Generators.booleans().next();
        var expiresAt = text();

        var built = GitLabClient.TokenIdentity.builder()
                .id(id)
                .name(name)
                .userId(userId)
                .scopes(scopes)
                .active(active)
                .expiresAt(expiresAt)
                .build();
        var constructed = new GitLabClient.TokenIdentity(id, name, userId, scopes, active, expiresAt);

        assertEquals(constructed, built, "the builder builds the same identity value");
    }
}
