/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.planmarshall.provider.ci.RepositoryRef;

/**
 * Generates a valid {@link RepositoryRef}: a forge and its web origin from the built-in provider table, a generated
 * owner (on GitLab a generated group path of one to three plain segments) and a generated repository name of one
 * plain segment. Every call draws fresh values.
 *
 * @since 0.1
 */
public final class RepositoryRefGenerator implements TypedGenerator<RepositoryRef> {

    /** The provider id of GitLab, the one forge whose owner is a group path (contract: provider id). */
    private static final String GITLAB = "gitlab";

    /** The built-in provider table: provider id and web origin (contract: provider ids and forge origins). */
    private static final List<Forge> FORGES = List.of(
            new Forge("github", "https://github.com"),
            new Forge(GITLAB, "https://gitlab.com"),
            new Forge("forgejo", "https://codeberg.org"));

    @Override
    public RepositoryRef next() {
        var forge = FORGES.get(Generators.integers(0, FORGES.size() - 1).next());
        var owner = GITLAB.equals(forge.id()) ? groupPath() : segment();
        return new RepositoryRef(forge.id(), forge.origin(), owner, segment());
    }

    @Override
    public Class<RepositoryRef> getType() {
        return RepositoryRef.class;
    }

    private static String groupPath() {
        return IntStream.range(0, Generators.integers(1, 3).next())
                .mapToObj(index -> segment())
                .collect(Collectors.joining("/"));
    }

    private static String segment() {
        return Generators.letterStrings(1, 20).next();
    }

    private record Forge(String id, String origin) {
    }
}
