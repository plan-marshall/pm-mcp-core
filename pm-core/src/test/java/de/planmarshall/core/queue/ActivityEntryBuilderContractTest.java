/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link ActivityEntry} builds the value the canonical constructor builds, and a missing source is
 * refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Activity entry builder")
class ActivityEntryBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var seq = Generators.integers(1, 100).next();
        var attempt = Generators.integers(1, 5).next();
        var text = Generators.letterStrings(1, 20).next();
        var at = Instant.parse("2026-01-02T03:04:05Z");

        var canonical = new ActivityEntry(seq, attempt, ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT,
                text, false, null, null, null, null, null, null, null, null, null, null, at);
        var built = ActivityEntry.builder().seq(canonical.seq()).attempt(canonical.attempt())
                .source(canonical.source()).kind(canonical.kind()).text(canonical.text())
                .truncated(canonical.truncated()).tool(canonical.tool()).result(canonical.result())
                .path(canonical.path()).stopReason(canonical.stopReason()).usage(canonical.usage())
                .link(canonical.link()).questionId(canonical.questionId()).signal(canonical.signal())
                .end(canonical.end()).unmappedLines(canonical.unmappedLines()).at(canonical.at()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ActivityEntry(seq, attempt, null, ActivityEntry.Kind.TEXT, text, false, null,
                                    null, null, null, null, null, null, null, null, null, at),
                            "the constructor refuses a null source");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ActivityEntry.builder().seq(seq).attempt(attempt).kind(ActivityEntry.Kind.TEXT)
                                    .text(text).at(at).build(),
                            "the builder refuses a missing source");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent source with the same message");
                });
    }
}
