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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;
import de.planmarshall.core.store.MetricsRow.CauseClass;
import de.planmarshall.core.store.MetricsRow.DurationBasis;
import de.planmarshall.core.store.MetricsRow.Form;
import de.planmarshall.core.store.MetricsRow.Outcome;
import de.planmarshall.core.store.MetricsRow.Phase;
import de.planmarshall.core.store.MetricsRow.Population;
import de.planmarshall.core.store.MetricsRow.SpendClass;

/**
 * The metrics store and its task row keep parity with {@code store-schemas/05}: the document's members and required
 * members are the file's, the task row carries every member the file declares for it, and the enumerations of the row
 * are the file's closed sets. The two negative controls prove the comparison reports a member only one side has.
 * <p>
 * The task row deviates from its file in exactly one member: the codec writes {@code attempt_end}, which the file's
 * {@code taskRow} does not declare. The test pins that drift; the landing message reports it.
 */
@EnableGeneratorController
@DisplayName("Metrics store schema parity")
class MetricsSchemaParityTest {

    private static final String RESOURCE = "de/planmarshall/core/store/metrics.schema.json";
    private static final String TASK_ROW = "taskRow";
    private static final String QUEUE_TASK_ID = "qt-0123456789abcdef";
    private static final String ID = "l-0123456789abcdef";

    private static byte[] written() {
        return MetricsCodec.write(new MetricsCodec.Document(List.of(row()), List.of(), List.of()));
    }

    private static MetricsRow row() {
        var usage = new HarnessUsage(Measurement.measured(1), Measurement.measured(2), Measurement.measured(3),
                Measurement.measured(4), Measurement.measured(5), Measurement.measured(6), Optional.empty(),
                Optional.empty());
        return MetricsRow.builder().queueTaskId(QUEUE_TASK_ID).attempt(1).role("implementer").jobId(ID)
                .generationId(ID).harness("opencode").harnessVersion("1").model("model").effort("high")
                .form(Form.FRESH).phase(Phase.PHASE_5_EXECUTE).stepId("step").envelopeId(null).predictedCost(null)
                .yieldReason(null).outcome(Outcome.SUBMITTED).attemptEnd(AttemptEnd.CLOSED).continuation(null)
                .causeClass(CauseClass.BENIGN).spendClass(SpendClass.SUBMITTED).population(Population.MODEL_JOB)
                .usage(usage).durationMs(1L).durationBasis(DurationBasis.PROCESS).queuedMs(1L).consultationOf(null)
                .recordedAt(Instant.parse("2026-11-07T10:00:00Z")).build();
    }

    private static Map<?, ?> taskRow() {
        var document = SchemaParity.object(SchemaParity.array(SchemaParity.document(written()).get("rows")).getFirst());
        return document;
    }

    @Test
    @DisplayName("the members and required members of the document are the file's")
    void documentMembers() {
        var schema = SchemaParity.load(RESOURCE);
        var document = written();

        assertAll(
                () -> assertFalse(SchemaParity.properties(schema).isEmpty(), "the schema declares members"),
                () -> assertEquals(List.of(), SchemaParity.memberDiffs(SchemaParity.members(document), schema),
                        "the document members should equal the schema"),
                () -> assertEquals(Set.copyOf(SchemaParity.required(schema)),
                        SchemaParity.requiredByCodec(document, MetricsCodec::read),
                        "the required document members should match"),
                () -> assertEquals(1,
                        ((Number) SchemaParity.member(schema, "format_version").get("const")).intValue(),
                        "the schema's format version stays 1"),
                () -> assertEquals(MetricsStore.FORMAT_VERSION, 1, "the codec's format version stays 1"));
    }

    @Test
    @DisplayName("the task row deviates from its file only in the extra attempt_end")
    void taskRowMembers() {
        var schema = SchemaParity.object(SchemaParity.object(SchemaParity.load(RESOURCE).get("$defs"))
                .get(TASK_ROW));

        assertAll(
                () -> assertEquals(Set.of("record-only:attempt_end"),
                        Set.copyOf(SchemaParity.memberDiffs(SchemaParity.members(taskRow()), schema)),
                        "the task row's recorded drift should not change silently"),
                () -> assertTrue(Set.copyOf(SchemaParity.members(taskRow()))
                                .containsAll(SchemaParity.required(schema)),
                        "the task row should carry every required member"));
    }

    @Test
    @DisplayName("the row refuses an attempt below the file's minimum and requires its undeclared attempt_end")
    void taskRowLimits() {
        var schema = SchemaParity.object(SchemaParity.object(SchemaParity.load(RESOURCE).get("$defs"))
                .get(TASK_ROW));

        assertAll(
                () -> assertEquals(1, SchemaParity.bound(SchemaParity.member(schema, "attempt"), "minimum"),
                        "the file's attempt minimum stays 1"),
                () -> assertThrows(IllegalArgumentException.class, () -> row().withAttempt(0),
                        "the row should refuse an attempt below its minimum"),
                () -> assertFalse(SchemaParity.required(schema).contains("attempt_end"),
                        "the file does not declare attempt_end"),
                () -> assertThrows(IllegalArgumentException.class, () -> row().withAttemptEnd(null),
                        "the row requires attempt_end when it carries an attempt"));
    }

    @Test
    @DisplayName("the enumerations of the task row are the file's closed sets")
    void taskRowEnumerations() {
        var schema = SchemaParity.object(SchemaParity.object(SchemaParity.load(RESOURCE).get("$defs"))
                .get(TASK_ROW));
        var phase = SchemaParity.object(SchemaParity.object(SchemaParity.load(RESOURCE).get("$defs")).get("phase"));

        assertAll(
                () -> assertEquals(Set.copyOf(SchemaParity.array(phase.get("enum")).stream().map(String::valueOf)
                                .toList()),
                        SchemaParity.wireNames(Phase.values(), Phase::wireName),
                        "the phases should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "form"),
                        SchemaParity.wireNames(Form.values(), Form::wireName),
                        "the forms should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "outcome"),
                        SchemaParity.wireNames(Outcome.values(), Outcome::wireName),
                        "the outcomes should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "cause_class"),
                        SchemaParity.wireNames(CauseClass.values(), CauseClass::wireName),
                        "the cause classes should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "spend_class"),
                        SchemaParity.wireNames(SpendClass.values(), SpendClass::wireName),
                        "the spend classes should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "population"),
                        SchemaParity.wireNames(Population.values(), Population::wireName),
                        "the populations should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "duration_basis"),
                        SchemaParity.wireNames(DurationBasis.values(), DurationBasis::wireName),
                        "the duration bases should equal the schema enum"),
                () -> assertEquals(SchemaParity.enumOf(schema, "unmeasured_reason"),
                        SchemaParity.wireNames(UnmeasuredReason.values(), UnmeasuredReason::wireName),
                        "the unmeasured reasons should equal the schema enum"));
    }

    @Test
    @DisplayName("the comparison reports a member the file has and the document lacks")
    void fileOnlyNegativeControl() {
        var diffs = SchemaParity.withFileOnly(SchemaParity.members(written()), SchemaParity.load(RESOURCE),
                "synthetic_member");

        assertEquals(List.of("file-only:synthetic_member"), diffs, "the extra file member should be reported");
    }

    @Test
    @DisplayName("the comparison reports a member the document has and the file lacks")
    void recordOnlyNegativeControl() {
        var diffs = SchemaParity.withRecordOnly(SchemaParity.members(written()), SchemaParity.load(RESOURCE), "rows");

        assertEquals(List.of("record-only:rows"), diffs, "the missing file member should be reported");
    }
}
