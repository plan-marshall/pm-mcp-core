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
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@EnableGeneratorController
@DisplayName("Qualification settings builder contract")
class QualificationSettingsBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's qualification settings")
    void builderBuildsSettings() {
        var skipRole = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var canonical = new QualificationSettings(80, 5, List.of(skipRole), false);

        var built = QualificationSettings.builder().agreementLevel(canonical.agreementLevel())
                .allowedGap(canonical.allowedGap()).skipRoles(canonical.skipRoles())
                .skipDecisions(canonical.skipDecisions()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new QualificationSettings(canonical.agreementLevel(), canonical.allowedGap(), null,
                        canonical.skipDecisions()),
                "the constructor refuses an absent role list");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> QualificationSettings.builder().agreementLevel(canonical.agreementLevel())
                        .allowedGap(canonical.allowedGap()).skipDecisions(canonical.skipDecisions()).build(),
                "the builder refuses an absent role list");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent role list with the same message"));
    }
}
