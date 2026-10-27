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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("The EARS check of a criterion (PM-WATCH-PHASE-106)")
class EarsFormTest {

    @DisplayName("Each EARS pattern is accepted in English")
    @Test
    void acceptsEveryEnglishPattern() {
        assertAccepted("THE system SHALL write the record", false);
        assertAccepted("WHEN a plan is refined THE system SHALL write the record", false);
        assertAccepted("WHERE the plan is deep THE system SHALL write the record", false);
        assertAccepted("WHILE the store is open THE system SHALL write the record", false);
        assertAccepted("WHERE the plan is deep WHILE the store is open WHEN a task submits "
                + "THE system SHALL write the record", false);
        assertAccepted("WHEN a task submits IF the record is new THEN THE system SHALL write the record", false);
        assertAccepted("WHEN a plan is refined THE system SHALL CONTINUE TO keep the record intact", true);
    }

    @DisplayName("A German criterion is accepted: the text between the keywords is not inspected")
    @Test
    void acceptsGermanClauseText() {
        assertAccepted("WHEN ein Plan verfeinert wird THE System SHALL den Datensatz neben der Anfrage schreiben",
                false);
        assertAccepted("WHERE der Plan tief ist THE System SHALL CONTINUE TO den alten Datensatz unverändert lassen",
                true);
    }

    @DisplayName("A lower-case structural word is refused as prose")
    @Test
    void refusesLowerCaseKeyword() {
        assertRefused("when a plan is refined THE system SHALL write the record", "structural keyword");
        assertRefused("WHEN a plan is refined the system shall write the record", "SHALL");
    }

    @DisplayName("A missing SHALL is refused")
    @Test
    void refusesMissingShall() {
        assertRefused("WHEN a plan is refined THE system writes the record", "SHALL");
    }

    @DisplayName("THEN without IF is refused, and IF without THEN")
    @Test
    void refusesConditionalOutOfOrder() {
        assertRefused("WHEN a plan is refined THEN THE system SHALL write the record", "THEN");
        assertRefused("WHEN a task submits IF the record is new THE system SHALL write the record", "THEN");
    }

    @DisplayName("Preconditions out of order are refused")
    @Test
    void refusesPreconditionsOutOfOrder() {
        assertRefused("WHEN a task submits WHERE the plan is deep THE system SHALL write the record", "out of order");
        assertRefused("WHEN a task submits WHILE the store is open THE system SHALL write the record", "out of order");
        assertRefused("WHEN a task submits WHEN again THE system SHALL write the record", "repeated");
    }

    @DisplayName("An unchanged criterion's response begins with CONTINUE TO, and only its own")
    @Test
    void checksTheUnchangedForm() {
        assertRefused("WHEN a plan is refined THE system SHALL write the record", true);
        assertRefused("WHEN a plan is refined THE system SHALL CONTINUE TO keep the record", false);
    }

    @DisplayName("A prose sentence with a lower-case when and shall is not a criterion")
    @Test
    void refusesProse() {
        assertRefused("we write the record when a plan is refined and the system shall keep it", "THE");
        assertRefused("the record is written beside the request", "THE");
    }

    private static void assertAccepted(String text, boolean unchanged) {
        assertTrue(EarsForm.violation(text, unchanged).isEmpty(),
                () -> "expected '%s' to be accepted, got: %s".formatted(text, EarsForm.violation(text, unchanged)));
    }

    private static void assertRefused(String text, String fragment) {
        var violation = EarsForm.violation(text, false);
        assertTrue(violation.isPresent(), () -> "expected '%s' to be refused".formatted(text));
        assertTrue(violation.orElseThrow().contains(fragment),
                () -> "expected the reason to name '%s', got: %s".formatted(fragment, violation.orElseThrow()));
    }

    private static void assertRefused(String text, boolean unchanged) {
        assertTrue(EarsForm.violation(text, unchanged).isPresent(),
                () -> "expected '%s' to be refused".formatted(text));
    }
}
