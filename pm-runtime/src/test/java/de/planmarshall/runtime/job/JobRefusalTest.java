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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.runtime.job.JobRefusal.Reason;

/**
 * The refusal of the verify-not-resolve pipeline (PM-EXEC-2, PM-WF-20): a value carrying its reason and details.
 * <p>
 * No refusal names an operator act: an operator command is no part of the value (Hypermedia Format Specification, No
 * Operator Relay), so the record carries exactly the reason, the diagnostic and — for {@code build_config_changed} —
 * the changed repository-relative paths.
 * <p>
 * Contract literals that stay: the record component names, the reason {@code build_config_changed} and the words
 * {@code detail} and {@code reason} the validation names, and {@code pm-operator}, the operator command no detail may
 * name. The detail and the changed paths are generated; their prefixes {@code a-} and {@code b-} fix the natural
 * order the test expects, and the input lists them unsorted on purpose.
 */
@EnableGeneratorController
@DisplayName("Job refusal")
class JobRefusalTest {

    private String detail;
    private String first;
    private String second;

    @BeforeEach
    void draw() {
        detail = Generators.letterStrings(1, 64).next();
        first = "a-" + word() + "/" + word();
        second = "b-" + word();
    }

    private static String word() {
        return Generators.letterStrings(1, 16).next().toLowerCase(Locale.ROOT);
    }

    /** @return a refusal of the reason, with the detail and the changed paths its reason carries */
    private JobRefusal refusalOf(Reason reason) {
        return switch (reason) {
            case Reason.BUILD_CONFIG_CHANGED -> JobRefusal.buildConfigChanged(List.of(second, first), detail);
            case Reason.ENROLMENT_TAMPERED -> JobRefusal.enrolmentTampered(detail);
            default -> JobRefusal.of(reason, detail);
        };
    }

    @Test
    @DisplayName("the record carries the reason, the detail and the changed paths, and nothing else")
    void componentsAreReasonDetailAndChangedPaths() {
        var components = Stream.of(JobRefusal.class.getRecordComponents())
                .map(RecordComponent::getName).toList();

        assertEquals(List.of("reason", "detail", "changedPaths"), components,
                "the refusal has exactly its three components");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Reason.class)
    @DisplayName("every reason carries its reason and its detail, and only build_config_changed names paths")
    void everyReasonCarriesItsReasonAndDetails(Reason reason) {
        var refusal = refusalOf(reason);
        var expectedPaths = reason == Reason.BUILD_CONFIG_CHANGED ? List.of(first, second) : List.<String>of();

        assertAll(
                () -> assertEquals(reason, refusal.reason(), "the refusal carries its reason"),
                () -> assertEquals(detail, refusal.detail(), "the refusal carries its detail"),
                () -> assertEquals(expectedPaths, refusal.changedPaths(),
                        "only build_config_changed names paths"),
                () -> assertFalse(refusal.detail().contains("pm-operator"),
                        "no detail names the operator command"));
    }

    @Test
    @DisplayName("build_config_changed names every changed path once, in their natural order")
    void changedPathsAreSortedAndDistinct() {
        var refusal = JobRefusal.buildConfigChanged(List.of(second, first, second), detail);

        assertEquals(List.of(first, second), refusal.changedPaths(),
                "the changed paths are sorted and distinct");
    }

    @Test
    @DisplayName("a build_config_changed without a changed path is refused")
    void changedPathsAreRequiredForBuildConfigChanged() {
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> JobRefusal.buildConfigChanged(List.of(), detail), "an empty path list is refused");

        assertTrue(refusal.getMessage().contains("build_config_changed"),
                "the message names build_config_changed");
    }

    @Test
    @DisplayName("an empty detail and a missing reason are refused")
    void invalidValuesAreRefused() {
        var emptyDetail = assertThrows(IllegalArgumentException.class,
                () -> JobRefusal.of(Reason.ENROLMENT_INSECURE, ""), "an empty detail is refused");
        var nullReason = assertThrows(NullPointerException.class, () -> JobRefusal.of(null, detail),
                "a missing reason is refused");

        assertAll(
                () -> assertTrue(emptyDetail.getMessage().contains("detail"), "the message names the detail"),
                () -> assertTrue(nullReason.getMessage().contains("reason"), "the message names the reason"));
    }

    @Test
    @DisplayName("of refuses a reason that names changed paths")
    void ofRefusesBuildConfigChanged() {
        var refusal = assertThrows(IllegalArgumentException.class,
                () -> JobRefusal.of(Reason.BUILD_CONFIG_CHANGED, detail), "of refuses build_config_changed");

        assertTrue(refusal.getMessage().contains("build_config_changed"),
                "the message names build_config_changed");
    }
}
