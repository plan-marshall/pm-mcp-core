/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.store.JobId;

@DisplayName("Job id generator")
@EnableGeneratorController
class JobIdGeneratorTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(JobId.Kind.class)
    @DisplayName("Should yield the prefix of the kind, a hyphen and 16 hex digits")
    void shouldYieldTheWrittenFormOfAJobId(JobId.Kind kind) {
        var jobId = RuntimeGenerators.jobIds(kind).next();

        // The pattern is the written form of a job id in pm-core: ^[bcglpv]-[0-9a-f]{16}$
        assertAll("generated job id",
                () -> assertTrue(jobId.matches(kind.prefix() + "-[0-9a-f]{16}"),
                        "The id is the kind prefix and 16 hex digits"),
                () -> assertEquals(kind, new JobId(jobId).kind(), "The id parses as a job id of the kind"));
    }

    @Test
    @DisplayName("Should draw the digits anew for every id")
    void shouldDrawTheDigitsAnew() {
        var generator = RuntimeGenerators.jobIds(JobId.Kind.LLM);

        var ids = Stream.generate(generator::next).limit(8).collect(Collectors.toSet());

        assertTrue(ids.size() > 1, "Eight drawn ids are not all the same id");
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(String.class, RuntimeGenerators.jobIds(JobId.Kind.BUILD).getType(),
                "The generator names the string type");
    }
}
