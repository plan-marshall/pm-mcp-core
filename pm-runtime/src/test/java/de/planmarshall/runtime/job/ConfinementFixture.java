/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;
import de.planmarshall.core.store.ProjectEnrolment.HostTool;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.core.testing.generators.CoreGenerators;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Enrolments for the confinement tests: the real record of the store, with only the fields the confinement and the
 * environment read filled. The project id, the enrolment instant and the digest of a pinned host tool are drawn per
 * call from the generator artifacts of {@code pm-core} and {@code pm-runtime}; a test that needs the project id reads
 * it from the enrolment. No file is written and no key is created.
 */
final class ConfinementFixture {

    private ConfinementFixture() {
    }

    /**
     * @param root       the project root
     * @param engine     the container-engine opt-in, {@code null} for none
     * @param writePaths the toolchain caches
     * @param variables  the captured enrolment environment
     * @param tools      the pinned host tools
     * @return the enrolment, with a generated project id and enrolment instant
     */
    static ProjectEnrolment enrolment(Path root, ContainerEngine engine, List<String> writePaths,
            Map<String, String> variables, List<HostTool> tools) {
        var generated = CoreGenerators.projectEnrolments().next();
        return new ProjectEnrolment(generated.projectId(), root.toAbsolutePath().normalize().toString(),
                generated.enrolledAt(), false, engine, null, List.of(), tools, Build.defaults(), Map.of(),
                new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), writePaths, new TreeMap<>(variables));
    }

    /**
     * @param executable the absolute path of a host tool
     * @return the pin of the tool, with a generated digest
     */
    static HostTool tool(String executable) {
        return new HostTool(HostToolName.GIT, executable, RuntimeGenerators.sha256Digests().next(), null, null);
    }
}
