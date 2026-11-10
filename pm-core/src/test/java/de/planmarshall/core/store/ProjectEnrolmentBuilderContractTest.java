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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectEnrolment.ArgumentSchema;
import de.planmarshall.core.store.ProjectEnrolment.Binding;
import de.planmarshall.core.store.ProjectEnrolment.HostTool;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.store.ProjectEnrolment.Interpreter;
import de.planmarshall.core.store.ProjectEnrolment.ProjectScript;
import de.planmarshall.core.store.ProjectEnrolment.SchemaType;
import de.planmarshall.core.store.ProjectEnrolment.WorkerKind;
import de.planmarshall.core.store.ProjectEnrolment.Wrapper;
import de.planmarshall.core.store.ProjectEnrolment.WrapperName;

@DisplayName("Project enrolment builder contract")
class ProjectEnrolmentBuilderContractTest {

    private static final String SHA_256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's enrolment")
    void builderBuildsEnrolment() {
        var canonical = new ProjectEnrolment("pm-mcp-core", "/home/dev/git/pm-mcp-core", AT, false, null,
                null, List.of(), List.of(), ProjectEnrolment.Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());

        var built = ProjectEnrolment.builder().projectId(canonical.projectId())
                .projectRoot(canonical.projectRoot()).enrolledAt(canonical.enrolledAt()).pilot(canonical.pilot())
                .containerEngine(canonical.containerEngine()).releasedAt(canonical.releasedAt())
                .wrappers(canonical.wrappers()).hostTools(canonical.hostTools()).build(canonical.build())
                .providers(canonical.providers()).projectScripts(canonical.projectScripts())
                .languageServers(canonical.languageServers()).commandBudgets(canonical.commandBudgets())
                .writePaths(canonical.writePaths()).buildEnvironment(canonical.buildEnvironment()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new ProjectEnrolment(canonical.projectId(), canonical.projectRoot(), null,
                        canonical.pilot(), canonical.containerEngine(), canonical.releasedAt(),
                        canonical.wrappers(), canonical.hostTools(), canonical.build(),
                        canonical.providers(), canonical.projectScripts(), canonical.languageServers(),
                        canonical.commandBudgets(), canonical.writePaths(), canonical.buildEnvironment()),
                "the constructor refuses an absent instant");
        var incomplete = ProjectEnrolment.builder().projectId(canonical.projectId())
                .projectRoot(canonical.projectRoot()).pilot(canonical.pilot())
                .containerEngine(canonical.containerEngine()).releasedAt(canonical.releasedAt())
                .wrappers(canonical.wrappers()).hostTools(canonical.hostTools())
                .build(canonical.build()).providers(canonical.providers())
                .projectScripts(canonical.projectScripts())
                .languageServers(canonical.languageServers())
                .commandBudgets(canonical.commandBudgets()).writePaths(canonical.writePaths())
                .buildEnvironment(canonical.buildEnvironment());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent instant");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent instant with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's wrapper")
    void builderBuildsWrapper() {
        var canonical = new Wrapper(WrapperName.MVNW, "mvnw", SHA_256, List.of(), null);

        var built = Wrapper.builder().name(canonical.name()).relativePath(canonical.relativePath())
                .sha256(canonical.sha256()).pinnedFiles(canonical.pinnedFiles())
                .interpreter(canonical.interpreter()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Wrapper(null, canonical.relativePath(), canonical.sha256(),
                        canonical.pinnedFiles(), canonical.interpreter()),
                "the constructor refuses an absent wrapper name");
        var incomplete = Wrapper.builder().relativePath(canonical.relativePath()).sha256(canonical.sha256())
                .pinnedFiles(canonical.pinnedFiles()).interpreter(canonical.interpreter());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent wrapper name");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent name with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's host tool")
    void builderBuildsHostTool() {
        var canonical = new HostTool(HostToolName.GIT, "/usr/bin/git", SHA_256, null, "2.40.0");

        var built = HostTool.builder().name(canonical.name()).executablePath(canonical.executablePath())
                .sha256(canonical.sha256()).interpreter(canonical.interpreter())
                .version(canonical.version()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new HostTool(null, canonical.executablePath(), canonical.sha256(),
                        canonical.interpreter(), canonical.version()),
                "the constructor refuses an absent host tool name");
        var incomplete = HostTool.builder().executablePath(canonical.executablePath())
                .sha256(canonical.sha256()).interpreter(canonical.interpreter())
                .version(canonical.version());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent name");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent name with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's project script")
    void builderBuildsProjectScript() {
        var canonical = new ProjectScript("scripts/parse.py", SHA_256, Interpreter.PYTHON3,
                new Binding(WorkerKind.LOG_PARSER, "build"), ArgumentSchema.any(), 10);

        var built = ProjectScript.builder().scriptPath(canonical.scriptPath()).sha256(canonical.sha256())
                .interpreter(canonical.interpreter()).binding(canonical.binding())
                .argumentSchema(canonical.argumentSchema())
                .maxTimeoutSeconds(canonical.maxTimeoutSeconds()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new ProjectScript(canonical.scriptPath(), canonical.sha256(), null,
                        canonical.binding(), canonical.argumentSchema(), canonical.maxTimeoutSeconds()),
                "the constructor refuses an absent interpreter");
        var incomplete = ProjectScript.builder().scriptPath(canonical.scriptPath()).sha256(canonical.sha256())
                .binding(canonical.binding()).argumentSchema(canonical.argumentSchema())
                .maxTimeoutSeconds(canonical.maxTimeoutSeconds());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent interpreter");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent interpreter alike"));
    }

    @Test
    @DisplayName("the builder builds the constructor's argument schema")
    void builderBuildsArgumentSchema() {
        var canonical = new ArgumentSchema(SchemaType.STRING, null, null, false, null, null, null, null, 10L);

        var built = ArgumentSchema.builder().type(canonical.type()).properties(canonical.properties())
                .required(canonical.required()).closed(canonical.closed())
                .enumValues(canonical.enumValues()).pattern(canonical.pattern())
                .minimum(canonical.minimum()).maximum(canonical.maximum())
                .maxLength(canonical.maxLength()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
