/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.easymock.EasyMock.createMock;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditSink;

/**
 * Proves that the builder of {@link StartupSequence} builds the value its canonical constructor builds. The user name
 * is generated, the paths are a real machine path, and the verification and the sink are mocks the builder only stores.
 */
@EnableGeneratorController
@DisplayName("StartupSequence builder")
class StartupSequenceRecordsTest {

    @Test
    @DisplayName("the builder builds the startup sequence the constructor builds")
    void startupSequenceBuilderEqualsConstructor() {
        var paths = new MachinePaths(Path.of("/tmp/pm-builder"), MachinePaths.Os.current());
        var user = Generators.letterStrings(3, 20).next();
        AuditChainVerification auditChain = createMock(AuditChainVerification.class);
        AuditSink auditSink = createMock(AuditSink.class);

        var constructed = new StartupSequence(paths, user, auditChain, auditSink);
        var built = StartupSequence.builder()
                .paths(paths)
                .user(user)
                .auditChain(auditChain)
                .auditSink(auditSink)
                .build();

        assertEquals(constructed, built, "the builder builds the same startup sequence");
    }
}
