/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.SecretRedactor;
import de.planmarshall.core.store.JobId;

/**
 * The unparsed evidence of a build job (Job Runtime § Unparseable Output Fallback): the bounded, redacted tail of the
 * raw log and the resource URI of the full log. The tests fix the bound of 50 lines and 4 KB, the redaction of a
 * secret-shaped value, and the refusal of an absent plan id.
 */
@EnableGeneratorController
@DisplayName("UnparsedEvidence")
class UnparsedEvidenceTest {

    private static final String PLAN = "plan-one";
    private static final JobId JOB = new JobId("b-0123456789abcdef");

    @Test
    @DisplayName("keeps the last fifty lines of a longer log")
    void keepsTheLastFiftyLines() {
        var lines = IntStream.range(0, 200)
                .mapToObj(index -> index + ":" + Generators.letterStrings(1, 12).next())
                .toList();
        var log = String.join("\n", lines) + "\n";

        var evidence = UnparsedEvidence.of(PLAN, JOB, log);

        assertEquals(String.join("\n", lines.subList(150, 200)), evidence.excerpt(), "the excerpt keeps fifty lines");
    }

    @Test
    @DisplayName("cuts a single line longer than four kilobytes at its end")
    void cutsALongLine() {
        var log = "x".repeat(10_000);

        var evidence = UnparsedEvidence.of(PLAN, JOB, log);

        assertAll(
                () -> assertEquals(UnparsedEvidence.MAX_BYTES, evidence.excerpt().getBytes(StandardCharsets.UTF_8).length,
                        "the excerpt carries at most four kilobytes"),
                () -> assertEquals(log.substring(10_000 - UnparsedEvidence.MAX_BYTES), evidence.excerpt(),
                        "the excerpt keeps the end of the line"));
    }

    @Test
    @DisplayName("redacts a secret-shaped value in the excerpt")
    void redactsASecret() {
        var secret = "ghp_" + "a".repeat(32);
        var log = "npm ERR! code E401\nnpm ERR! token=" + secret + "\n";

        var evidence = UnparsedEvidence.of(PLAN, JOB, log);

        assertAll(
                () -> assertFalse(evidence.excerpt().contains(secret), "the secret is not in the excerpt"),
                () -> assertTrue(evidence.excerpt().contains(SecretRedactor.REDACTED), "the excerpt names the mask"),
                () -> assertTrue(evidence.excerpt().startsWith("npm ERR! code E401"),
                        "the redaction keeps the other lines"));
    }

    @Test
    @DisplayName("names the resource URI of the full log by plan and job")
    void namesTheResourceUri() {
        var evidence = UnparsedEvidence.of(PLAN, JOB, "opaque failure\n");

        assertEquals("pm://plan/plan-one/builds/b-0123456789abcdef/log", evidence.excerptUri(),
                "the URI names the plan and the job");
    }

    @Test
    @DisplayName("leaves the excerpt empty for an empty log")
    void emptyLog() {
        var evidence = UnparsedEvidence.of(PLAN, JOB, "");

        assertEquals("", evidence.excerpt(), "an empty log yields an empty excerpt");
    }

    @Test
    @DisplayName("never splits a UTF-8 character at the byte bound")
    void cutsAtACharacterBoundary() {
        var log = "x".repeat(3001) + "é".repeat(3000);

        var evidence = UnparsedEvidence.of(PLAN, JOB, log);

        assertAll(
                () -> assertTrue(evidence.excerpt().getBytes(StandardCharsets.UTF_8).length
                        <= UnparsedEvidence.MAX_BYTES, "the byte bound holds"),
                () -> assertFalse(evidence.excerpt().contains("\uFFFD"), "no character is cut in half"),
                () -> assertTrue(evidence.excerpt().endsWith("é"), "the tail is kept"));
    }

    @Test
    @DisplayName("refuses an absent plan id, job id or log")
    void refusesAbsentInput() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> UnparsedEvidence.of(" ", JOB, "log")),
                () -> assertThrows(NullPointerException.class, () -> UnparsedEvidence.of(null, JOB, "log")),
                () -> assertThrows(NullPointerException.class, () -> UnparsedEvidence.of(PLAN, null, "log")),
                () -> assertThrows(NullPointerException.class, () -> UnparsedEvidence.of(PLAN, JOB, null)),
                () -> assertThrows(IllegalArgumentException.class, () -> new UnparsedEvidence("", " ")));
    }

    @Test
    @DisplayName("refuses a directly constructed excerpt beyond a bound")
    void refusesAnUnboundedExcerpt() {
        var uri = "pm://plan/plan-one/builds/b-0123456789abcdef/log";

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnparsedEvidence("x".repeat(UnparsedEvidence.MAX_BYTES + 1), uri)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new UnparsedEvidence("line\n".repeat(UnparsedEvidence.MAX_LINES + 1), uri)));
    }
}
