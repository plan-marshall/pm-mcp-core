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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.spi.FileSystemProvider;
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
import de.planmarshall.core.guard.fixtures.FileChannelFixture;
import de.planmarshall.core.guard.fixtures.FileMutatorFixture;
import de.planmarshall.core.guard.fixtures.FileWriteFixture;
import de.planmarshall.core.guard.fixtures.HttpClientFixture;
import de.planmarshall.core.guard.fixtures.InMemoryPrinterFixture;
import de.planmarshall.core.guard.fixtures.NioSocketFixture;
import de.planmarshall.core.guard.fixtures.PrintWriterFileFixture;
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
 *   <li>uses a socket channel of NIO, none of which lives in {@code java.net}: {@link SocketChannel},
 *       {@link ServerSocketChannel}, {@link DatagramChannel}, {@link AsynchronousSocketChannel} or
 *       {@link AsynchronousServerSocketChannel};</li>
 *   <li>calls a writing, creating, moving, copying or deleting method of {@link Files}, {@code newByteChannel}
 *       included;</li>
 *   <li>calls a mutating method of {@link File}, such as {@code delete}, {@code createNewFile}, {@code mkdirs},
 *       {@code renameTo} or one of its setters;</li>
 *   <li>constructs a {@link PrintWriter} or {@link PrintStream} on a {@link File} or a file name;</li>
 *   <li>uses {@link FileOutputStream}, {@link FileWriter}, {@link RandomAccessFile}, {@link FileChannel},
 *       {@link AsynchronousFileChannel} or {@link FileSystemProvider};</li>
 *   <li>depends on {@code core.store..}.</li>
 * </ul>
 * Each control fixture of {@code de.planmarshall.core.guard.fixtures} that this rule is applied to proves that the
 * rule fails where it must.
 */
@DisplayName("Analysis and findings boundary of pm-core")
class AnalysisBoundaryArchTest {

    private static final List<String> NETWORK_PACKAGES = List.of("java.net.http", "javax.net", "de.cuioss.http");

    private static final String STORE_PACKAGE = "de.planmarshall.core.store";

    private static final Set<Class<?>> NETWORK_TYPES = Set.of(SocketChannel.class, ServerSocketChannel.class,
            DatagramChannel.class, AsynchronousSocketChannel.class, AsynchronousServerSocketChannel.class);

    private static final Set<Class<?>> FILE_WRITING_TYPES = Set.of(FileOutputStream.class, FileWriter.class,
            RandomAccessFile.class, FileChannel.class, AsynchronousFileChannel.class, FileSystemProvider.class);

    private static final Set<String> FILES_WRITING_METHODS = Set.of("write", "writeString", "newOutputStream",
            "newBufferedWriter", "newByteChannel", "createFile", "createDirectory", "createDirectories",
            "createTempFile", "createTempDirectory", "createLink", "createSymbolicLink", "move", "copy", "delete",
            "deleteIfExists", "setAttribute", "setLastModifiedTime", "setOwner", "setPosixFilePermissions");

    private static final Set<String> FILE_MUTATING_METHODS = Set.of("delete", "deleteOnExit", "createNewFile",
            "mkdir", "mkdirs", "renameTo", "setWritable", "setReadOnly", "setLastModified", "setExecutable");

    private static final Set<Class<?>> FILE_PRINTING_TYPES = Set.of(PrintWriter.class, PrintStream.class);

    private static final Set<Class<?>> FILE_NAMING_TYPES = Set.of(File.class, String.class);

    private static final ArchCondition<JavaClass> DECLARATIVE =
            new ArchCondition<>("make no network call, write no file and depend on no store") {
                @Override
                public void check(JavaClass javaClass, ConditionEvents events) {
                    javaClass.getDirectDependenciesFromSelf().stream()
                            .filter(dependency -> isForbiddenTarget(dependency.getTargetClass().getBaseComponentType()))
                            .forEach(dependency -> events.add(
                                    SimpleConditionEvent.violated(dependency, dependency.getDescription())));
                    javaClass.getMethodCallsFromSelf().stream()
                            .filter(AnalysisBoundaryArchTest::isWritingCall)
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
                    javaClass.getConstructorCallsFromSelf().stream()
                            .filter(AnalysisBoundaryArchTest::isFilePrinterConstruction)
                            .forEach(call -> events.add(SimpleConditionEvent.violated(call, call.getDescription())));
                }
            };

    private static boolean isForbiddenTarget(JavaClass target) {
        var packageName = target.getPackageName();
        return NETWORK_PACKAGES.stream().anyMatch(network -> isInPackage(packageName, network))
                || "java.net".equals(packageName) && !target.isEquivalentTo(URI.class)
                || NETWORK_TYPES.stream().anyMatch(target::isEquivalentTo)
                || FILE_WRITING_TYPES.stream().anyMatch(target::isEquivalentTo)
                || isInPackage(packageName, STORE_PACKAGE);
    }

    /** A call of a writing method of {@link Files} or of a mutating method of {@link File}. */
    private static boolean isWritingCall(JavaMethodCall call) {
        var owner = call.getTargetOwner();
        return owner.isEquivalentTo(Files.class) && FILES_WRITING_METHODS.contains(call.getName())
                || owner.isEquivalentTo(File.class) && FILE_MUTATING_METHODS.contains(call.getName());
    }

    /** A construction of a {@link PrintWriter} or {@link PrintStream} on a file or a file name. */
    private static boolean isFilePrinterConstruction(JavaConstructorCall call) {
        var parameters = call.getTarget().getRawParameterTypes();
        return FILE_PRINTING_TYPES.stream().anyMatch(call.getTargetOwner()::isEquivalentTo)
                && !parameters.isEmpty()
                && FILE_NAMING_TYPES.stream().anyMatch(parameters.getFirst()::isEquivalentTo);
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
                    Arguments.of(StoreDependencyFixture.class, STORE_PACKAGE + ".ScopeType"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.delete()"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.deleteOnExit()"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.createNewFile()"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.mkdir()"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.mkdirs()"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.renameTo(java.io.File)"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.setWritable(boolean)"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.setReadOnly()"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.setLastModified(long)"),
                    Arguments.of(FileMutatorFixture.class, "java.io.File.setExecutable(boolean)"),
                    Arguments.of(PrintWriterFileFixture.class, "java.io.PrintWriter.<init>(java.io.File)"),
                    Arguments.of(PrintWriterFileFixture.class, "java.io.PrintWriter.<init>(java.lang.String)"),
                    Arguments.of(PrintWriterFileFixture.class, "java.io.PrintStream.<init>(java.io.File)"),
                    Arguments.of(PrintWriterFileFixture.class, "java.io.PrintStream.<init>(java.lang.String)"),
                    Arguments.of(FileChannelFixture.class, "java.nio.file.Files.newByteChannel"),
                    Arguments.of(FileChannelFixture.class, "java.nio.channels.AsynchronousFileChannel"),
                    Arguments.of(FileChannelFixture.class, "java.nio.file.spi.FileSystemProvider"),
                    Arguments.of(NioSocketFixture.class, "java.nio.channels.SocketChannel"),
                    Arguments.of(NioSocketFixture.class, "java.nio.channels.ServerSocketChannel"),
                    Arguments.of(NioSocketFixture.class, "java.nio.channels.DatagramChannel"),
                    Arguments.of(NioSocketFixture.class, "java.nio.channels.AsynchronousSocketChannel"),
                    Arguments.of(NioSocketFixture.class, "java.nio.channels.AsynchronousServerSocketChannel"));
        }

        @ParameterizedTest(name = "{0}: {1}")
        @MethodSource("fixtures")
        @DisplayName("fails on the fixture, naming the forbidden target")
        void failsOnFixture(Class<?> fixture, String forbiddenTarget) {
            var result = evaluate(fixture);

            assertTrue(result.hasViolation(), () -> "The rule must fail on " + fixture.getSimpleName());
            assertTrue(result.getFailureReport().getDetails().stream().anyMatch(detail -> detail.contains(forbiddenTarget)),
                    () -> result.getFailureReport().toString());
        }

        @Test
        @DisplayName("holds on a printer over memory, which writes no file")
        void holdsOnInMemoryPrinter() {
            var result = evaluate(InMemoryPrinterFixture.class);

            assertFalse(result.hasViolation(), () -> result.getFailureReport().toString());
        }
    }
}
