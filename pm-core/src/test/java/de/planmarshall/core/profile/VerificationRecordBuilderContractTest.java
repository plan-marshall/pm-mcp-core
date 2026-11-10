/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link VerificationRecord} and of its nested records build the values the canonical constructors
 * build.
 */
@EnableGeneratorController
@DisplayName("Verification record builders")
class VerificationRecordBuilderContractTest {

    private static final String DIGEST = "sha256:" + "0".repeat(64);
    private static final Instant AT = Instant.parse("2026-01-02T03:04:05Z");

    @Test
    @DisplayName("the record builder builds the constructor's value")
    void shouldBuildRecordLikeTheConstructor() {
        var stage = new VerificationRecord.Stage("passed", Map.of(), null, List.of());

        var canonical = new VerificationRecord(VerificationRecord.FORMAT_VERSION,
                Generators.letterStrings(3, 12).next(), DIGEST, AT, new VerificationRecord.Tool("0.1.0", "abc123"),
                new VerificationRecord.HarnessBuild("acme", "1.2.3", DIGEST, List.of("acme-1")),
                new VerificationRecord.Platform("linux", "x86_64", "landlock"), Map.of("identity", stage));
        var built = VerificationRecord.builder().formatVersion(canonical.formatVersion())
                .clientId(canonical.clientId()).profileDigest(canonical.profileDigest())
                .verifiedAt(canonical.verifiedAt()).tool(canonical.tool()).harness(canonical.harness())
                .platform(canonical.platform()).stages(canonical.stages()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }

    @Test
    @DisplayName("the stage builder builds the constructor's value")
    void shouldBuildStageLikeTheConstructor() {
        var canonical = new VerificationRecord.Stage("passed", Map.of("tool_call", "answered"), null, List.of());
        var built = VerificationRecord.Stage.builder().status(canonical.status()).results(canonical.results())
                .trials(canonical.trials()).startContext(canonical.startContext()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }

    @Test
    @DisplayName("the harness build builder builds the constructor's value")
    void shouldBuildHarnessBuildLikeTheConstructor() {
        var canonical = new VerificationRecord.HarnessBuild("acme", "1.2.3", DIGEST, List.of("acme-1"));
        var built = VerificationRecord.HarnessBuild.builder().executable(canonical.executable())
                .version(canonical.version()).executableSha256(canonical.executableSha256())
                .models(canonical.models()).build();

        assertEquals(canonical, built, "the builder builds the constructor's value");
    }
}
