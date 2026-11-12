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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.MetricsRow.CauseClass;
import de.planmarshall.core.store.MetricsRow.DurationBasis;
import de.planmarshall.core.store.MetricsRow.Form;
import de.planmarshall.core.store.MetricsRow.Outcome;
import de.planmarshall.core.store.MetricsRow.Phase;
import de.planmarshall.core.store.MetricsRow.Population;
import de.planmarshall.core.store.MetricsRow.SpendClass;

@EnableGeneratorController
@DisplayName("Metrics row builder contract")
class MetricsRowBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's task row")
    void builderBuildsTaskRow() {
        var role = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var harness = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var harnessVersion = Generators.integers(0, 9).next() + "." + Generators.integers(0, 99).next();
        var model = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var usage = new HarnessUsage(Measurement.measured(10), Measurement.measured(20),
                Measurement.measured(0), Measurement.measured(0), Measurement.measured(0),
                Measurement.measured(1), null, null);
        var canonical = new MetricsRow("qt-0123456789abcdef", 1, role, "l-0123456789abcdef",
                "l-0123456789abcdef", harness, harnessVersion, model, null, Form.FRESH,
                Phase.PHASE_5_EXECUTE, null, null, null, null, Outcome.SUBMITTED, null,
                CauseClass.BENIGN, SpendClass.SUBMITTED, Population.MODEL_TASK, usage, null,
                DurationBasis.TASK, null, null, Instant.parse("2026-10-22T10:00:00Z"));

        var built = MetricsRow.builder().queueTaskId(canonical.queueTaskId()).attempt(canonical.attempt())
                .role(canonical.role()).jobId(canonical.jobId()).generationId(canonical.generationId())
                .harness(canonical.harness()).harnessVersion(canonical.harnessVersion()).model(canonical.model())
                .effort(canonical.effort()).form(canonical.form()).phase(canonical.phase())
                .stepId(canonical.stepId()).envelopeId(canonical.envelopeId())
                .predictedCost(canonical.predictedCost()).yieldReason(canonical.yieldReason())
                .outcome(canonical.outcome())
                .continuation(canonical.continuation()).causeClass(canonical.causeClass())
                .spendClass(canonical.spendClass()).population(canonical.population()).usage(canonical.usage())
                .durationMs(canonical.durationMs()).durationBasis(canonical.durationBasis())
                .queuedMs(canonical.queuedMs()).consultationOf(canonical.consultationOf())
                .recordedAt(canonical.recordedAt()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new MetricsRow(canonical.queueTaskId(), canonical.attempt(), canonical.role(),
                        canonical.jobId(), canonical.generationId(), canonical.harness(),
                        canonical.harnessVersion(), canonical.model(), canonical.effort(), canonical.form(),
                        canonical.phase(), canonical.stepId(), canonical.envelopeId(),
                        canonical.predictedCost(), canonical.yieldReason(), null,
                        canonical.continuation(), canonical.causeClass(),
                        canonical.spendClass(), canonical.population(), canonical.usage(),
                        canonical.durationMs(), canonical.durationBasis(), canonical.queuedMs(),
                        canonical.consultationOf(), canonical.recordedAt()),
                "the constructor refuses an absent outcome");
        var incomplete = MetricsRow.builder().queueTaskId(canonical.queueTaskId()).attempt(canonical.attempt())
                .role(canonical.role()).jobId(canonical.jobId())
                .generationId(canonical.generationId()).harness(canonical.harness())
                .harnessVersion(canonical.harnessVersion()).model(canonical.model())
                .effort(canonical.effort()).form(canonical.form()).phase(canonical.phase())
                .stepId(canonical.stepId()).envelopeId(canonical.envelopeId())
                .predictedCost(canonical.predictedCost()).yieldReason(canonical.yieldReason())
                .continuation(canonical.continuation())
                .causeClass(canonical.causeClass()).spendClass(canonical.spendClass())
                .population(canonical.population()).usage(canonical.usage())
                .durationMs(canonical.durationMs()).durationBasis(canonical.durationBasis())
                .queuedMs(canonical.queuedMs()).consultationOf(canonical.consultationOf())
                .recordedAt(canonical.recordedAt());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent outcome");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent outcome with the same message"));
    }
}
