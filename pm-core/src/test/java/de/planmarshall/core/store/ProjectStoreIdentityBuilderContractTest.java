/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("Project store identity builder contract")
class ProjectStoreIdentityBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's store identity")
    void builderBuildsIdentity() {
        var displayName = Generators.letterStrings(3, 20).next();
        var canonical = new ProjectStoreIdentity("pm-mcp-core", "/home/dev/git/pm-mcp-core", displayName,
                Instant.parse("2026-10-17T09:00:00Z"), null);

        var built = ProjectStoreIdentity.builder().projectId(canonical.projectId())
                .projectRoot(canonical.projectRoot()).displayName(canonical.displayName())
                .createdAt(canonical.createdAt()).unenrolledAt(canonical.unenrolledAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new ProjectStoreIdentity(canonical.projectId(), canonical.projectRoot(), null,
                        canonical.createdAt(), canonical.unenrolledAt()),
                "the constructor refuses an absent display name");
        var incomplete = ProjectStoreIdentity.builder().projectId(canonical.projectId())
                .projectRoot(canonical.projectRoot()).createdAt(canonical.createdAt())
                .unenrolledAt(canonical.unenrolledAt());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent display name");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent name with the same message"));
    }
}
