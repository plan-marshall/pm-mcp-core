/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link SourceManifest} builds the value the canonical constructor builds, and a missing name is
 * refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Source manifest builder")
class SourceManifestBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent name alike")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentNameAlike() {
        var canonical = new SourceManifest("corpus", URI.create("https://github.com/example/corpus"),
                "%040x".formatted(Generators.longs().next()), "Apache-2.0", SourceManifest.FetchKind.GIT,
                List.of(new SourceManifest.SourceFile("data/rows.csv",
                        "%064x".formatted(Generators.longs().next()), false)));
        var built = SourceManifest.builder().name(canonical.name()).repository(canonical.repository())
                .commit(canonical.commit()).licence(canonical.licence()).fetch(canonical.fetch())
                .files(canonical.files()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new SourceManifest(null, canonical.repository(), canonical.commit(),
                                    canonical.licence(), canonical.fetch(), canonical.files()),
                            "the constructor refuses a null name");
                    var incomplete = SourceManifest.builder().repository(canonical.repository())
                            .commit(canonical.commit()).licence(canonical.licence())
                            .fetch(canonical.fetch()).files(canonical.files());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }
}
