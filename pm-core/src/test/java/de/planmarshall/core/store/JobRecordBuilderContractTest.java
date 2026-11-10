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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.ClampedValue;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;

/**
 * The builders of {@link JobRecord} and of {@link JobRecord.ClampedValue} build the values the canonical constructors
 * build, and an absent required component is refused with the same refusal.
 */
@DisplayName("Job record builders")
class JobRecordBuilderContractTest {

    private static final String JOB_ID = "b-0123456789abcdef";
    private static final String ARGV_DIGEST = "0123456789abcdef".repeat(4);
    private static final Instant CREATED_AT = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    @DisplayName("the record builder builds the constructor's value and refuses an absent job id")
    void shouldBuildRecordLikeTheConstructorAndRefuseAnAbsentJobId() {
        var scope = new Scope(Scope.Type.PLAN, "plan-one");
        var derivation = new BudgetDerivation(BudgetDerivation.Kind.LEARNED, 3L);
        var canonical = new JobRecord(new JobId(JOB_ID), Execution.PROCESS, scope, null, "build:verify", 600,
                derivation, ARGV_DIGEST, Status.QUEUED, null, null, null, Confinement.UNAVAILABLE, CREATED_AT, null,
                null, null, null, null, null, null, null, false, null, List.of(), null);
        var built = JobRecord.builder().jobId(canonical.jobId()).execution(canonical.execution())
                .scope(canonical.scope()).worker(canonical.worker()).budgetKey(canonical.budgetKey())
                .budgetSeconds(canonical.budgetSeconds()).budgetDerivation(canonical.budgetDerivation())
                .argvDigest(canonical.argvDigest()).status(canonical.status()).fate(canonical.fate())
                .pid(canonical.pid()).processStartedAt(canonical.processStartedAt())
                .confinement(canonical.confinement()).createdAt(canonical.createdAt())
                .spawnedAt(canonical.spawnedAt()).endedAt(canonical.endedAt())
                .childWallMs(canonical.childWallMs()).exitCode(canonical.exitCode()).signal(canonical.signal())
                .landlockAbi(canonical.landlockAbi()).outcomeRow(canonical.outcomeRow())
                .refusalReason(canonical.refusalReason()).cancelBeforeGroup(canonical.cancelBeforeGroup())
                .selfReportContradiction(canonical.selfReportContradiction())
                .clampedValues(canonical.clampedValues()).gateFacts(canonical.gateFacts()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    List<JobRecord.ClampedValue> clampedValues = List.of();
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JobRecord(null, Execution.PROCESS, scope, null, "build:verify", 600,
                                    derivation, ARGV_DIGEST, Status.QUEUED, null, null, null,
                                    Confinement.UNAVAILABLE, CREATED_AT, null, null, null, null, null, null, null,
                                    null, false, null, clampedValues, null),
                            "the constructor refuses a null job id");
                    var incomplete = JobRecord.builder().execution(Execution.PROCESS).scope(scope)
                            .budgetKey("build:verify").budgetSeconds(600).budgetDerivation(derivation)
                            .argvDigest(ARGV_DIGEST).status(Status.QUEUED)
                            .confinement(Confinement.UNAVAILABLE).createdAt(CREATED_AT)
                            .clampedValues(List.of());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing job id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent job id with the same message");
                });
    }

    @Test
    @DisplayName("the clamped value builder builds the constructor's value and refuses an absent key")
    void shouldBuildClampedValueLikeTheConstructorAndRefuseAnAbsentKey() {
        var canonical = new ClampedValue("build.timeout_seconds", "120", new BigDecimal("60"),
                ClampedValue.Source.ENROLMENT);
        var built = ClampedValue.builder().key(canonical.key()).configured(canonical.configured())
                .used(canonical.used()).source(canonical.source()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var used = new BigDecimal("60");
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ClampedValue(null, "120", used, ClampedValue.Source.ENROLMENT),
                            "the constructor refuses a null key");
                    var incomplete = ClampedValue.builder().configured("120").used(new BigDecimal("60"))
                            .source(ClampedValue.Source.ENROLMENT);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing key");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent key with the same message");
                });
    }
}
