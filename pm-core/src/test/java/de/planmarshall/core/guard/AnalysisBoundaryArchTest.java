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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.analysis.AnalysisResult;
import de.planmarshall.core.findings.Finding;
import de.planmarshall.core.guard.fixtures.FileWriteFixture;
import de.planmarshall.core.guard.fixtures.HttpClientFixture;
import de.planmarshall.core.guard.fixtures.StoreDependencyFixture;

/**
 * Guards that {@code core.analysis} and {@code core.findings} stay declarative (PM-IMPL-1).
 * <p>
 * The hazard: the two packages declare the contracts, records and serialization of findings and code analysis, while
 * the HTTP calls to a provider belong to the provider modules and the writes of the findings ledger and the analysis
 * result belong to the store and the workflow. A class of the two packages that opens a network connection, writes a
 * file or depends on the store would carry provider or persistence behaviour into the foundation, beyond what its
 * contracts promise.
 * <p>
 * The rule fails when a class of the two packages
 * <ul>
 *   <li>depends on {@code java.net.http..}, {@code javax.net..}, {@code de.cuioss.http..} or on a class of
 *       {@code java.net} other than {@link URI};</li>
 *   <li>calls a writing, creating, moving, copying or deleting method of {@link Files};</li>
 *   <li>uses {@link FileOutputStream}, {@link FileWriter}, {@link RandomAccessFile} or {@link FileChannel};</li>
 *   <li>depends on {@code core.store..}.</li>
 * </ul>
 * Each control fixture of {@code de.planmarshall.core.guard.fixtures} that this rule is applied to proves that the
 * rule fails where it must.
 */
@DisplayName("Analysis and findings boundary of pm-core")
class AnalysisBoundaryArchTest {

    private static final List<String> NETWORK_PACKAGES = List.of("java.net.http", "javax.net", "de.cuioss.http");

    private static final String STORE_PACKAGE = "de.planmarshall.core.store";

    private static final Set<Class<?>> FILE_WRITING_TYPES =
            Set.of(FileOutputStream.class, FileWriter.class, RandomAccessFile.class, FileChannel.class);

    private static final Set<String> FILES_WRITING_METHODS = Set.of("write", "writeString", "newOutputStream",
            "newBufferedWriter", "createFile", "createDirectory", "createDirectories", "createTempFile",
            "createTempDirectory", "createLink", "createSymbolicLink", "move", "copy", "delete", "deleteIfExists",
            "setAttribute", "setLastModifiedTime", "setOwner", "setPosixFilePermissions");

    private static final ArchCondition<JavaClass> DECLARATIVE =
            new ArchCondition<>("make no network call, write no file and depend on no store") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getDirectDependenciesFromSelf().stream()
                            .filter(dependency -> isForbiddenTarget(dependency.getTargetClass().getBaseComponentType()))
                            .forEach(dependency -> events.add(
                                    SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                    javaClass.getMethodCallsFromSelf().stream()
                            .filter(call -> call.getTargetOwner().isEquivalentTo(Files.class)
                                    && FILES_WRITING_METHODS.contains(call.getName()))
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
                }
            };

    private static boolean isForbiddenTarget(JavaClass target) {
        var packageName = target.getPackageName();
        return NETWORK_PACKAGES.stream().anyMatch(network -> isInPackage(packageName, network))
                || "java.net".equals(packageName) && !target.isEquivalentTo(URI.class)
                || FILE_WRITING_TYPES.stream().anyMatch(target::isEquivalentTo)
                || isInPackage(packageName, STORE_PACKAGE);
    }

    private static boolean isInPackage(String packageName, String root) {
        return packageName.equals(root) || packageName.startsWith(root + ".");
    }

    private static EvaluationResult evaluate(Class<?> fixture) {
        return classes().should(DECLARATIVE).evaluate(new ClassFileImporter().importClasses(fixture));
    }

    @Nested
    @DisplayName("on the main classes of core.analysis and core.findings")
    class MainClasses {

        @Test
        @DisplayName("the rule holds")
        void ruleHolds() {
            JavaClasses mainClasses = new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("de.planmarshall.core.analysis", "de.planmarshall.core.findings");

            assertTrue(mainClasses.contain(AnalysisResult.class) && mainClasses.contain(Finding.class),
                    "The import must contain the main classes of both packages");

            classes().that().resideInAnyPackage("de.planmarshall.core.analysis..", "de.planmarshall.core.findings..")
                    .should(DECLARATIVE)
                    .check(mainClasses);
        }
    }

    @Nested
    @DisplayName("on the control fixtures")
    class Controls {

        static Stream<Arguments> fixtures() {
            return Stream.of(
                    Arguments.of(HttpClientFixture.class, "java.net.http.HttpClient"),
                    Arguments.of(FileWriteFixture.class, "java.nio.file.Files.write"),
                    Arguments.of(StoreDependencyFixture.class, STORE_PACKAGE + ".ScopeType"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("fixtures")
        @DisplayName("fails on the fixture, naming the forbidden target")
        void failsOnFixture(Class<?> fixture, String forbiddenTarget) {
            var result = evaluate(fixture);

            assertTrue(result.hasViolation(), () -> "The rule must fail on " + fixture.getSimpleName());
            assertTrue(result.getFailureReport().getDetails().stream().anyMatch(detail -> detail.contains(forbiddenTarget)),
                    () -> result.getFailureReport().toString());
        }
    }
}
