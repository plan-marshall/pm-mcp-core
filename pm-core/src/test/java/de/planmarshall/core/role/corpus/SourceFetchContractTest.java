/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical object methods of a fetch outcome (PM-WORK-6, PM-EXEC-5): the outcome, the content bytes and the
 * redacted detail are the value, and the mutable byte array is compared by content rather than by identity.
 */
@EnableGeneratorController
@DisplayName("Contract of a corpus fetch outcome")
class SourceFetchContractTest implements ShouldHandleObjectContracts<SourceFetch> {

    @Override
    public SourceFetch getUnderTest() {
        return SourceFetch.fetched(Generators.letterStrings(1, 40).next().getBytes(UTF_8),
                Generators.letterStrings(1, 40).next());
    }

    /** A fetch outcome is carried in-process; its content bytes are the wire form, not Java serialization. */
    @Override
    @Test
    @DisplayName("a fetch outcome is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(SourceFetch.class),
                "A fetch outcome is not Java-serialized; its content bytes are the wire form");
    }
}
