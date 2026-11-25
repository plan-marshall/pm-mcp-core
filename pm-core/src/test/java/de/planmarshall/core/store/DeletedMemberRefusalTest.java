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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.MetricsRow.CauseClass;
import de.planmarshall.core.store.MetricsRow.DurationBasis;
import de.planmarshall.core.store.MetricsRow.Form;
import de.planmarshall.core.store.MetricsRow.Outcome;
import de.planmarshall.core.store.MetricsRow.Phase;
import de.planmarshall.core.store.MetricsRow.Population;
import de.planmarshall.core.store.MetricsRow.SpendClass;

/**
 * A record written before PLAN-V2-129 carries a member that plan deleted, and the strict readers refuse it whole,
 * naming the member (ADR {@code core-store/049}): {@code capability} of an intent criterion, {@code interfaces} and
 * {@code key_links} of an outline deliverable, {@code attempt_end} of a metrics task row. No tolerant reader exists.
 * <p>
 * The member names, the anchors they are inserted at and the refusal text are the contract of the stored formats; the
 * documents around them are written by the codecs from the fixtures.
 */
@EnableGeneratorController
@DisplayName("Deleted-member refusal")
class DeletedMemberRefusalTest {

    private static final Path INTENT = Path.of("intent.json");
    private static final Path OUTLINE = Path.of("outline.json");
    private static final Path METRICS = Path.of("metrics.json");

    private static byte[] intent() {
        return IntentCodec.write(IntentStore.first(
                new IntentStore.Revision(1, IntentFixtures.AT, "TASK-001", IntentFixtures.valid())));
    }

    private static byte[] outline() {
        return OutlineCodec.write(OutlineStore.first(
                new OutlineStore.Revision(1, OutlineFixtures.AT, "TASK-001", OutlineFixtures.valid())));
    }

    private static byte[] metrics() {
        var usage = new HarnessUsage(Measurement.measured(1), Measurement.measured(2), Measurement.measured(3),
                Measurement.measured(4), Measurement.measured(5), Measurement.measured(6), null, null);
        var row = MetricsRow.builder().queueTaskId("qt-0123456789abcdef").attempt(1).role("implementer")
                .jobId("l-0123456789abcdef").generationId("l-0123456789abcdef").harness("opencode")
                .harnessVersion("1").model("model").effort("high").servedModels(ServedModels.unmeasured())
                .form(Form.FRESH).phase(Phase.PHASE_5_EXECUTE)
                .stepId("step").envelopeId(null).predictedCost(null).yieldReason(null).outcome(Outcome.SUBMITTED)
                .continuation(null).causeClass(CauseClass.BENIGN).spendClass(SpendClass.SUBMITTED)
                .population(Population.MODEL_JOB).usage(usage).durationMs(1L).durationBasis(DurationBasis.PROCESS)
                .queuedMs(1L).consultationOf(null).recordedAt(Instant.parse("2026-11-07T10:00:00Z")).build();
        return MetricsCodec.write(new MetricsCodec.Document(List.of(row), List.of(), List.of()));
    }

    static Stream<Arguments> deletedMembers() {
        Function<byte[], ?> readIntent = document -> IntentCodec.read(INTENT, document);
        Function<byte[], ?> readOutline = document -> OutlineCodec.read(OUTLINE, document);
        Function<byte[], ?> readMetrics = document -> MetricsCodec.read(METRICS, document);
        return Stream.of(
                Arguments.of("capability", intent(), "\"criteria\":[{", readIntent, INTENT),
                Arguments.of("interfaces", outline(), "\"deliverables\":[{", readOutline, OUTLINE),
                Arguments.of("key_links", outline(), "\"deliverables\":[{", readOutline, OUTLINE),
                Arguments.of("attempt_end", metrics(), "\"rows\":[{", readMetrics, METRICS));
    }

    @ParameterizedTest(name = "a record that still carries {0} is refused whole")
    @MethodSource("deletedMembers")
    @DisplayName("a member PLAN-V2-129 deleted is refused and named")
    void deletedMemberRefused(String member, byte[] written, String anchor, Function<byte[], ?> reader, Path file) {
        var text = new String(written, StandardCharsets.UTF_8);
        assertTrue(text.contains(anchor), "the written document holds the anchor");
        var document = text.replaceFirst(Pattern.quote(anchor),
                Matcher.quoteReplacement(anchor + "\"" + member + "\":null,"));

        var refusal = assertThrows(StoreRefusal.class,
                () -> reader.apply(document.getBytes(StandardCharsets.UTF_8)),
                "a record with a deleted member is refused");

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(), "the refusal is unreadable"),
                () -> assertEquals(file, refusal.file(), "the refusal names the refused file"),
                () -> assertTrue(refusal.getMessage().contains("undeclared field '" + member + "'"),
                        "the refusal names the deleted member"));
    }
}
