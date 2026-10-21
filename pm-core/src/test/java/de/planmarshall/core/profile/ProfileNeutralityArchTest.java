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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.fixtures.ClientNamedFixture;

/**
 * Guards that no class of the profile loader names a harness (PM-CLIENT-5): what a harness is, is
 * the profile document, and the loader implements the mechanisms the schema names. A class, package or member name that
 * contains a shipped client id ties the loader to that client, so a harness could no longer be added as a document.
 * <p>
 * The rule is applied to the main classes of {@code de.planmarshall.core.profile}; the control fixture
 * {@link ClientNamedFixture} proves that the rule fails where it must.
 */
@DisplayName("Harness neutrality of the profile loader")
class ProfileNeutralityArchTest {

    /** The client ids no class, package or member of the loader may contain, compared in lower case. */
    private static final List<String> CLIENT_NAMES = List.of("claude", "opencode", "antigravity", "codex");

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("de.planmarshall.core.profile");
    }

    @Test
    @DisplayName("the loader names no harness")
    void loaderNamesNoHarness() {
        assertFalse(mainClasses.isEmpty(), "The import must contain the classes of the profile package");

        rule().check(mainClasses);
    }

    @Test
    @DisplayName("the rule fails on a class and a member that name a harness")
    void controlFixtureFails() {
        var result = rule().evaluate(new ClassFileImporter().importClasses(ClientNamedFixture.class));

        assertTrue(result.hasViolation(), "The rule must fail on ClientNamedFixture");
        assertTrue(result.getFailureReport().getDetails().stream()
                        .anyMatch(detail -> detail.contains("OPENCODE")),
                () -> result.getFailureReport().toString());
    }

    private static ArchRule rule() {
        return classes().should(nameNoClient());
    }

    private static ArchCondition<JavaClass> nameNoClient() {
        return new ArchCondition<>("name no client") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                clientName(javaClass.getName()).ifPresent(name -> events.add(SimpleConditionEvent.violated(
                        javaClass, "Class %s names the client '%s'".formatted(javaClass.getName(), name))));
                for (JavaMember member : javaClass.getMembers()) {
                    clientName(member.getName()).ifPresent(name -> events.add(SimpleConditionEvent.violated(
                            member, "Member %s names the client '%s'".formatted(member.getFullName(), name))));
                }
            }
        };
    }

    private static Optional<String> clientName(String name) {
        var lowerCase = name.toLowerCase(Locale.ROOT);
        return CLIENT_NAMES.stream().filter(lowerCase::contains).findFirst();
    }
}
