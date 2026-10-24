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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectEnrolment.ContainerEngine;
import de.planmarshall.core.store.ProjectEnrolment.HostTool;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;

/**
 * Enrolments for the confinement tests: the real record of the store, with only the fields the confinement and the
 * environment read filled. No file is written and no key is created.
 */
final class ConfinementFixture {

    /** A lower-case SHA-256 for the pinned host tools. */
    static final String SHA256 = "a".repeat(64);

    private ConfinementFixture() {
    }

    /**
     * @param root       the project root
     * @param engine     the container-engine opt-in, {@code null} for none
     * @param writePaths the toolchain caches
     * @param variables  the captured enrolment environment
     * @param tools      the pinned host tools
     * @return the enrolment
     */
    static ProjectEnrolment enrolment(Path root, ContainerEngine engine, List<String> writePaths,
            Map<String, String> variables, List<HostTool> tools) {
        return new ProjectEnrolment("job-project", root.toAbsolutePath().normalize().toString(), Instant.EPOCH, false,
                engine, null, List.of(), tools, Build.defaults(), Map.of(), new TreeMap<>(), new TreeMap<>(),
                new TreeMap<>(), writePaths, new TreeMap<>(variables));
    }

    /**
     * @param executable the absolute path of a host tool
     * @return the pin of the tool
     */
    static HostTool tool(String executable) {
        return new HostTool(HostToolName.GIT, executable, SHA256, null, null);
    }
}
