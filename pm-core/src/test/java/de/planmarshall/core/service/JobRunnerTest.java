/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.JobRunner.CancelCause;
import de.planmarshall.core.service.JobRunner.ProbeRequest;
import de.planmarshall.core.service.JobRunner.StartRequest;
import de.planmarshall.core.service.JobRunner.StartResult;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;

/**
 * The service interface of the job runner (PM-EXEC-4): its value types validate their components, the closed cancel
 * causes map to the outcome rows 2 to 4, and the runtime, not the foundation, implements it.
 */
@DisplayName("Job runner")
class JobRunnerTest {

    private static final Scope PLAN = new Scope(Scope.Type.PLAN, "plan-one");
    private static final BudgetDerivation FLOOR = new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null);

    /** A runner that is no part of the foundation, so the guard below has an implementation it must find. */
    private static final class TestRunner implements JobRunner<String, String> {

        @Override
        public StartResult start(StartRequest<String> request) {
            return new StartResult.Refused("not_started");
        }

        @Override
        public StartResult startProbe(ProbeRequest<String> request) {
            return new StartResult.Refused("not_started");
        }

        @Override
        public boolean cancel(JobId jobId, CancelCause cause) {
            return false;
        }

        @Override
        public Optional<JobRecord> record(JobId jobId) {
            return Optional.empty();
        }
    }

    private static StartRequest<String> request(JobId.Kind kind, String budgetKey, long budgetSeconds) {
        return new StartRequest<>(kind, PLAN, budgetKey, budgetSeconds, FLOOR, "verify", Optional.empty());
    }

    private static List<String> implementations(JavaClasses classes) {
        return classes.stream().filter(type -> !type.isInterface() && type.isAssignableTo(JobRunner.class))
                .map(JavaClass::getName).toList();
    }

    @Nested
    @DisplayName("start request")
    class StartRequests {

        @Test
        @DisplayName("a complete request keeps its components")
        void complete() {
            var request = new StartRequest<>(JobId.Kind.BUILD, PLAN, "build:verify", 600, FLOOR, "verify",
                    Optional.of("payload"));

            assertAll(
                    () -> assertEquals(JobId.Kind.BUILD, request.kind()),
                    () -> assertEquals(600, request.budgetSeconds()),
                    () -> assertEquals(Optional.of("payload"), request.stdin()));
        }

        @Test
        @DisplayName("a missing component is refused")
        void missing() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> new StartRequest<>(null, PLAN, "build:verify", 1, FLOOR, "r", Optional.empty())),
                    () -> assertThrows(NullPointerException.class, () -> new StartRequest<>(JobId.Kind.BUILD, null,
                            "build:verify", 1, FLOOR, "r", Optional.empty())),
                    () -> assertThrows(NullPointerException.class,
                            () -> new StartRequest<>(JobId.Kind.BUILD, PLAN, null, 1, FLOOR, "r", Optional.empty())),
                    () -> assertThrows(NullPointerException.class, () -> new StartRequest<>(JobId.Kind.BUILD, PLAN,
                            "build:verify", 1, null, "r", Optional.empty())),
                    () -> assertThrows(NullPointerException.class, () -> new StartRequest<String>(JobId.Kind.BUILD,
                            PLAN, "build:verify", 1, FLOOR, null, Optional.empty())),
                    () -> assertThrows(NullPointerException.class,
                            () -> new StartRequest<>(JobId.Kind.BUILD, PLAN, "build:verify", 1, FLOOR, "r", null)));
        }

        @Test
        @DisplayName("a probe, a blank budget key and a budget under one second are refused")
        void invalid() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> request(JobId.Kind.PROBE, "probe:version", 15)),
                    () -> assertThrows(IllegalArgumentException.class, () -> request(JobId.Kind.BUILD, " ", 15)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> request(JobId.Kind.BUILD, "build:verify", 0)),
                    () -> assertEquals(1, request(JobId.Kind.BUILD, "build:verify", 1).budgetSeconds()));
        }

        @Test
        @DisplayName("a probe request requires its candidate and carries the consent")
        void probeRequest() {
            var consented = new ProbeRequest<>("candidate", true);

            assertAll(
                    () -> assertTrue(consented.consented()),
                    () -> assertThrows(NullPointerException.class, () -> new ProbeRequest<String>(null, true)));
        }
    }

    @Nested
    @DisplayName("start result")
    class StartResults {

        @Test
        @DisplayName("a started job names its id")
        void started() {
            var jobId = new JobId("b-0123456789abcdef");

            assertAll(
                    () -> assertEquals(jobId, new StartResult.Started(jobId).jobId()),
                    () -> assertThrows(NullPointerException.class, () -> new StartResult.Started(null)));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", " ", "Digest_mismatch", "has space", "1st", "_leading", "argument-rejected"})
        @DisplayName("a refusal reason outside the lower-case snake-case form is refused")
        void invalidReason(String reason) {
            assertThrows(IllegalArgumentException.class, () -> new StartResult.Refused(reason));
        }

        @Test
        @DisplayName("a refusal carries its reason, and a missing reason is refused")
        void refused() {
            assertAll(
                    () -> assertEquals("executable_digest_mismatch",
                            new StartResult.Refused("executable_digest_mismatch").reason()),
                    () -> assertThrows(NullPointerException.class, () -> new StartResult.Refused(null)));
        }
    }

    @Nested
    @DisplayName("cancel causes")
    class CancelCauses {

        @ParameterizedTest(name = "{0} -> row {1}, {2}")
        @CsvSource({
                "ENGINE_CANCEL, 2, CANCELLED",
                "PLAN_PAUSED_NOW, 2, CANCELLED",
                "SUPERVISOR_END, 2, CANCELLED",
                "BUDGET_EXPIRED, 3, TIMED_OUT",
                "DRAIN, 4, KILLED"})
        @DisplayName("each cause settles the job by its row of the outcome derivation")
        void mapping(CancelCause cause, int row, Fate fate) {
            assertAll(
                    () -> assertEquals(row, cause.outcomeRow()),
                    () -> assertEquals(fate, cause.fate()));
        }

        @Test
        @DisplayName("the causes cover exactly the rows 2 to 4")
        void rows() {
            var rows = Arrays.stream(CancelCause.values()).map(CancelCause::outcomeRow)
                    .collect(Collectors.toUnmodifiableSet());

            assertAll(
                    () -> assertEquals(Set.of(2, 3, 4), rows),
                    () -> assertEquals(5, CancelCause.values().length));
        }
    }

    @Nested
    @DisplayName("implementation")
    class Implementation {

        @Test
        @DisplayName("no class of pm-core implements the job runner")
        void noImplementationInTheFoundation() {
            var production = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("de.planmarshall.core");

            assertEquals(List.of(), implementations(production));
        }

        @Test
        @DisplayName("the guard finds an implementation where there is one")
        void guardFindsAnImplementation() {
            var withRunner = new ClassFileImporter().importClasses(TestRunner.class, JobRunner.class);

            assertEquals(List.of(TestRunner.class.getName()), implementations(withRunner));
        }
    }
}
