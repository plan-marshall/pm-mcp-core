/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of the supported-versions {@code Version} builds the value the canonical constructor builds.
 */
@DisplayName("Supported version builder")
class SupportedVersionsBuilderContractTest {

    @Test
    @DisplayName("the version builder builds the constructor's value")
    void shouldBuildVersionLikeTheConstructor() {
        var canonical = new SupportedVersions.Version(2, 1, 0, "rc1");
        var built = SupportedVersions.Version.builder().major(canonical.major()).minor(canonical.minor())
                .patch(canonical.patch()).suffix(canonical.suffix()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
