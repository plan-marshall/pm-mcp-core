/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.guard;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.findings.FindingSourceKind;
import de.planmarshall.core.guard.fixtures.ExemptionScopeFixture;
import de.planmarshall.core.guard.fixtures.GitHubNamedFixture;

/**
 * Guards that nothing in {@code pm-core} names a provider (PM-IMPL-1).
 * <p>
 * The hazard: the foundation is provider-neutral, and a code analysis provider, a CI system or a review platform is
 * added as a provider module that implements the contracts of {@code pm-core}. A class of {@code pm-core} that depends
 * on a provider module, or a class, package or member named after a provider, ties the foundation to that provider,
 * and the store vocabulary would start to carry provider identities instead of the provider id apart from it.
 * <p>
 * The rule imports the main classes of {@code de.planmarshall.core} and fails on a dependency on
 * {@code de.planmarshall.provider..} and on a class, package or member name that contains one of
 * {@link #PROVIDER_NAMES}, ignoring case. The only exemption is {@link #EXEMPTION}. Each control fixture of
 * {@code de.planmarshall.core.guard.fixtures} that this rule is applied to proves that the rule fails where it must.
 */
@DisplayName("Provider neutrality of pm-core")
class ProviderNeutralityArchTest {

    /** The provider names no class, package or member of {@code pm-core} may contain, compared in lower case. */
    private static final List<String> PROVIDER_NAMES = List.of("github", "gitlab", "sonar", "codeql");

    /**
     * The one exemption of the rule: the constants of the pull-request and merge-request review sources of
     * {@link FindingSourceKind}. Their names are fixed by the closed wire vocabulary of the findings store
     * ({@code github_pr}, {@code gitlab_mr}), and they name the kind of producer of a finding, not a provider identity.
     * A member of the same name in any other type is not exempt.
     */
    private static final Set<FindingSourceKind> EXEMPTION =
            Set.of(FindingSourceKind.GITHUB_PR, FindingSourceKind.GITLAB_MR);

    private static final String PROVIDER_PACKAGE = "de.planmarshall.provider";

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importMainClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("de.planmarshall.core");
    }

    private static ArchRule rule(Set<FindingSourceKind> exemption) {
        return classes().should(nameNoProvider(exemption));
    }

    private static ArchCondition<JavaClass> nameNoProvider(Set<FindingSourceKind> exemption) {
        return new ArchCondition<>("depend on no provider module and name no provider") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                javaClass.getDirectDependenciesFromSelf().stream()
                        .filter(dependency -> isProviderPackage(dependency.getTargetClass().getPackageName()))
                        .forEach(dependency -> events.add(
                                SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                providerName(javaClass.getName()).ifPresent(name -> events.add(SimpleConditionEvent.violated(
                        javaClass, "Class %s names the provider '%s'".formatted(javaClass.getName(), name))));
                for (JavaMember member : javaClass.getMembers()) {
                    if (!isExempt(member, exemption)) {
                        providerName(member.getName()).ifPresent(name -> events.add(SimpleConditionEvent.violated(
                                member, "Member %s names the provider '%s'".formatted(member.getFullName(), name))));
                    }
                }
            }
        };
    }

    private static boolean isProviderPackage(String packageName) {
        return PROVIDER_PACKAGE.equals(packageName) || packageName.startsWith(PROVIDER_PACKAGE + ".");
    }

    private static Optional<String> providerName(String name) {
        var lowerCase = name.toLowerCase(Locale.ROOT);
        return PROVIDER_NAMES.stream().filter(lowerCase::contains).findFirst();
    }

    private static boolean isExempt(JavaMember member, Set<FindingSourceKind> exemption) {
        return member instanceof JavaField field
                && field.getOwner().isEquivalentTo(FindingSourceKind.class)
                && exemption.stream().anyMatch(constant -> constant.name().equals(field.getName()));
    }

    private static EvaluationResult evaluate(Set<FindingSourceKind> exemption, Class<?>... types) {
        return rule(exemption).evaluate(new ClassFileImporter().importClasses(types));
    }

    @Nested
    @DisplayName("on the main classes of pm-core")
    class MainClasses {

        @Test
        @DisplayName("the rule holds")
        void ruleHolds() {
            assertTrue(mainClasses.contain(FindingSourceKind.class),
                    "The import must contain the main classes of pm-core");

            rule(EXEMPTION).check(mainClasses);
        }

        @Test
        @DisplayName("without the exemption the rule fails on exactly the two exempt constants")
        void exemptionIsLoadBearing() {
            var details = evaluate(Set.of(), FindingSourceKind.class).getFailureReport().getDetails();

            assertAll(
                    () -> assertTrue(details.stream().anyMatch(detail -> detail.contains("FindingSourceKind.GITHUB_PR")),
                            details::toString),
                    () -> assertTrue(details.stream().anyMatch(detail -> detail.contains("FindingSourceKind.GITLAB_MR")),
                            details::toString),
                    () -> assertEquals(2, details.size(), details::toString));
        }
    }

    @Nested
    @DisplayName("on the control fixtures")
    class Controls {

        @Test
        @DisplayName("fails on a class named after a provider")
        void providerNamedClass() {
            var result = evaluate(EXEMPTION, GitHubNamedFixture.class);

            assertTrue(result.hasViolation(), "The rule must fail on GitHubNamedFixture");
            assertTrue(result.getFailureReport().getDetails().stream()
                            .anyMatch(detail -> detail.contains("Class " + GitHubNamedFixture.class.getName())),
                    () -> result.getFailureReport().toString());
        }

        @Test
        @DisplayName("fails on a constant of an exempt name outside FindingSourceKind")
        void exemptionScope() {
            var result = evaluate(EXEMPTION, ExemptionScopeFixture.class);

            assertTrue(result.hasViolation(), "The rule must fail on ExemptionScopeFixture");
            assertTrue(result.getFailureReport().getDetails().stream()
                            .anyMatch(detail -> detail.contains(ExemptionScopeFixture.class.getName() + ".GITHUB_PR")),
                    () -> result.getFailureReport().toString());
        }

        @Test
        @DisplayName("holds on FindingSourceKind with the exemption")
        void exemptConstantsAccepted() {
            assertFalse(evaluate(EXEMPTION, FindingSourceKind.class).hasViolation());
        }
    }
}
