/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Controls of the build guards of this repository (PM-IMPL-1): each fixture project below
 * {@code src/guard-controls} breaks one rule of the root POM, and its build must fail in the enforcer
 * execution of that rule.
 * <p>
 * Hazards the guards answer: a framework dependency in the engine ties the plain-Java modules to the
 * assembly; a dependency of the foundation on a module of the product other than {@code pm-api}, or on JGit
 * or {@code cui-http}, turns the dependency direction; so does the engine {@code pm-workflow} when it reaches a
 * provider module, {@code pm-runtime}, JGit or {@code cui-http}; a provider module that reaches the engine or
 * {@code pm-runtime} sees internals it must not; {@code commonmark} or LSP4J outside {@code pm-runtime}
 * spreads its dependencies; model-facing content in a module of this repository is published with its JAR; a
 * public repository that is asked for a coordinate of the product before the organisation registry learns the
 * coordinate and could answer (the fixture {@code central-before-registry} has its own {@code .mvn} with Maven
 * Central listed first). A guard that never fails proves nothing, hence these controls.
 */
@DisplayName("Build guards of the repository")
class BuildGuardsIT {

    @ParameterizedTest(name = "{0} fails in {2}")
    @CsvSource({
            "framework, validate, no-framework-no-later-repository",
            "foundation-product-module, validate, foundation",
            "foundation-jgit, validate, foundation",
            "workflow-product-module, validate, workflow",
            "workflow-cui-http, validate, workflow",
            "runtime-product-module, validate, runtime",
            "runtime-library, validate, runtime-libraries",
            "provider-product-module, validate, provider",
            "model-facing-content, prepare-package, no-model-facing-content",
            "central-before-registry, validate, product-coordinates-from-the-organisation-registry"})
    @DisplayName("a fixture that breaks a rule fails the build")
    void fixtureFails(String fixture, String phase, String execution, @TempDir Path temp) throws Exception {
        var root = Path.of(System.getProperty("pm.root"));
        var pom = root.resolve("src/guard-controls").resolve(fixture).resolve("pom.xml");
        // Started in the fixture's directory, so that Maven takes the .mvn of the fixture where it has one
        // (central-before-registry) and the one of the repository otherwise.
        // The output goes to a file: reading the pipe to its end would wait for a build that hangs, and the
        // time limit below would never apply.
        var log = temp.resolve("fixture.log");
        var builder = new ProcessBuilder(root.resolve("mvnw").toString(), "-B", "--no-transfer-progress", "-f",
                pom.toString(), phase).directory(pom.getParent().toFile()).redirectErrorStream(true)
                .redirectOutput(log.toFile());

        var process = builder.start();
        if (!process.waitFor(5, TimeUnit.MINUTES)) {
            // mvnw is a script and Maven its child: both are ended, and the log is read when they are gone
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
            fail("the build of the fixture did not end:\n" + Files.readString(log));
        }
        var output = Files.readString(log);

        assertNotEquals(0, process.exitValue(), output);
        assertTrue(output.contains("enforce (" + execution + ")"), output);
        assertTrue(output.contains("BUILD FAILURE"), output);
    }
}
