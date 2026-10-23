/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.runtime.job.JobRefusal.Reason;

/**
 * The refusal of the verify-not-resolve pipeline (PM-EXEC-2, PM-WF-20): a value carrying its reason and details.
 * <p>
 * No refusal names an operator act: an operator command is no part of the value (Hypermedia Format Specification, No
 * Operator Relay), so the record carries exactly the reason, the diagnostic and — for {@code build_config_changed} —
 * the changed repository-relative paths.
 */
@DisplayName("Job refusal")
class JobRefusalTest {

    private static final String DETAIL = "the build configuration changed";

    /** @return a refusal of every reason, with the detail and the changed paths its reason carries */
    static Stream<Arguments> reasons() {
        return Stream.of(Reason.values()).map(reason -> Arguments.of(reason, refusalOf(reason)));
    }

    private static JobRefusal refusalOf(Reason reason) {
        return switch (reason) {
            case Reason.BUILD_CONFIG_CHANGED -> JobRefusal.buildConfigChanged(
                    List.of("pom.xml", ".mvn/maven.config"), DETAIL);
            case Reason.ENROLMENT_TAMPERED -> JobRefusal.enrolmentTampered(DETAIL);
            default -> JobRefusal.of(reason, DETAIL);
        };
    }

    @Test
    @DisplayName("the record carries the reason, the detail and the changed paths, and nothing else")
    void componentsAreReasonDetailAndChangedPaths() {
        var components = Stream.of(JobRefusal.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertEquals(List.of("reason", "detail", "changedPaths"), components);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("reasons")
    @DisplayName("every reason carries its reason and its detail, and only build_config_changed names paths")
    void everyReasonCarriesItsReasonAndDetails(Reason reason, JobRefusal refusal) {
        var expectedPaths = reason == Reason.BUILD_CONFIG_CHANGED
                ? List.of(".mvn/maven.config", "pom.xml")
                : List.<String>of();

        assertAll(
                () -> assertEquals(reason, refusal.reason()),
                () -> assertEquals(DETAIL, refusal.detail()),
                () -> assertEquals(expectedPaths, refusal.changedPaths()),
                () -> assertFalse(refusal.detail().contains("pm-operator"), refusal.detail()));
    }

    @Test
    @DisplayName("build_config_changed names every changed path once, in their natural order")
    void changedPathsAreSortedAndDistinct() {
        var refusal = JobRefusal.buildConfigChanged(
                List.of("pom.xml", ".mvn/maven.config", "pom.xml"), DETAIL);

        assertEquals(List.of(".mvn/maven.config", "pom.xml"), refusal.changedPaths());
    }

    @Test
    @DisplayName("a build_config_changed without a changed path is refused")
    void changedPathsAreRequiredForBuildConfigChanged() {
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> JobRefusal.buildConfigChanged(List.of(), DETAIL));

        assertTrue(refusal.getMessage().contains("build_config_changed"), refusal.getMessage());
    }

    @Test
    @DisplayName("an empty detail and a missing reason are refused")
    void invalidValuesAreRefused() {
        var emptyDetail = assertThrows(IllegalArgumentException.class,
                () -> JobRefusal.of(Reason.ENROLMENT_INSECURE, ""));
        var nullReason = assertThrows(NullPointerException.class, () -> JobRefusal.of(null, DETAIL));

        assertAll(
                () -> assertTrue(emptyDetail.getMessage().contains("detail"), emptyDetail.getMessage()),
                () -> assertTrue(nullReason.getMessage().contains("reason"), nullReason.getMessage()));
    }

    @Test
    @DisplayName("of refuses a reason that names changed paths")
    void ofRefusesBuildConfigChanged() {
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> JobRefusal.of(Reason.BUILD_CONFIG_CHANGED, DETAIL));

        assertTrue(refusal.getMessage().contains("build_config_changed"), refusal.getMessage());
    }
}
