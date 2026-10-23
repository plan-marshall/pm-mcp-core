/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleCatalog;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * The one model caller of {@code model-work/01-roles-and-configuration.adoc#model-caller}: Resolve, Qualify, Available,
 * Queue, Serve in that order; the {@code judge} and the {@code steward} skip Qualify and are queued without a qualification
 * record; a task closed without an attempt carries {@code role_undefined}, {@code class_mismatch},
 * {@code role_unqualified} or {@code role_unavailable}.
 */
@DisplayName("The model caller")
class ModelCallerTest {

    private static final String STATE = "implement.envelope-3";
    private static final String IMPLEMENT = "implement";
    private static final String JUDGE = RoleCatalog.JUDGE;
    private static final String STEWARD = RoleCatalog.STEWARD;

    @Test
    @DisplayName("a qualified fresh role is queued with its effective configuration")
    void qualifiedRoleIsQueued() {
        var role = ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH);
        var fixtures = new Fixtures(Map.of(IMPLEMENT, role));
        fixtures.qualifications.qualifies(IMPLEMENT, RoleForm.FRESH);

        var result = fixtures.caller.call(job("task", IMPLEMENT, false));

        var queued = (ModelCallResult.Queued) result;
        var request = fixtures.queue.enqueued().getFirst();
        assertAll(
                () -> assertEquals(ModelFixtures.QUEUE_TASK, queued.queueTaskId()),
                () -> assertEquals(IMPLEMENT, queued.role()),
                () -> assertEquals(RoleForm.FRESH, queued.form()),
                () -> assertFalse(queued.formDowngraded()),
                () -> assertEquals(role.configurationDigest(), request.configurationDigest()),
                () -> assertEquals(role.qualificationDigest(), fixtures.qualifications.digests().getFirst()),
                () -> assertEquals(ModelFixtures.HARNESS, request.harness()),
                () -> assertEquals(ModelFixtures.MODEL, request.model()),
                () -> assertEquals(STATE, request.stateId()),
                () -> assertFalse(request.consultation()),
                () -> assertEquals(IMPLEMENT, fixtures.qualifications.looked().getFirst()));
    }

    @Test
    @DisplayName("a qualified warm role is downgraded to fresh when only the fresh form qualifies")
    void warmRoleIsDowngraded() {
        var fixtures = new Fixtures(Map.of(IMPLEMENT,
                ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.WARM)));
        fixtures.qualifications.qualifies(IMPLEMENT, RoleForm.FRESH);

        var queued = (ModelCallResult.Queued) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals(RoleForm.FRESH, queued.form()),
                () -> assertTrue(queued.formDowngraded()),
                () -> assertEquals(RoleForm.FRESH, fixtures.queue.enqueued().getFirst().form()));
    }

    @Test
    @DisplayName("a role without a qualification record closes role_unqualified")
    void unqualifiedRoleCloses() {
        var fixtures = new Fixtures(Map.of(IMPLEMENT,
                ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH)));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_unqualified", result.reason()),
                () -> assertTrue(fixtures.queue.enqueued().isEmpty()),
                () -> assertEquals("role_unqualified", fixtures.queue.closed().getFirst().reason()));
    }

    @Test
    @DisplayName("an absent role closes role_undefined without a validation reason")
    void absentRoleCloses() {
        var fixtures = new Fixtures(Map.of());

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_undefined", result.reason()),
                () -> assertEquals(Optional.empty(), result.validationReason()),
                () -> assertTrue(fixtures.queue.enqueued().isEmpty()));
    }

    @Test
    @DisplayName("an invalid role closes role_undefined naming the reason")
    void invalidRoleClosesWithReason() {
        RoleResolver refusing = name -> new RoleResolution.Undefined(name,
                Optional.of(RoleValidationReason.MODEL_NOT_ALLOWED), "the catalogue does not allow the model");
        var fixtures = new Fixtures(refusing, ModelFixtures.resolver(Map.of()));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_undefined", result.reason()),
                () -> assertEquals(Optional.of(RoleValidationReason.MODEL_NOT_ALLOWED), result.validationReason()));
    }

    @Test
    @DisplayName("a read_only role on the task kind closes class_mismatch")
    void classMismatchCloses() {
        var fixtures = new Fixtures(Map.of("review-code",
                ModelFixtures.role("review-code", RoleClass.READ_ONLY, RoleForm.FRESH)));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", "review-code", false));

        assertAll(
                () -> assertEquals("class_mismatch", result.reason()),
                () -> assertTrue(fixtures.queue.enqueued().isEmpty()));
    }

    @Test
    @DisplayName("a consulted zero_tool role closes class_mismatch")
    void consultedZeroToolCloses() {
        var fixtures = new Fixtures(Map.of("screener",
                ModelFixtures.role("screener", RoleClass.ZERO_TOOL, RoleForm.FRESH)));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("decision", "screener", true));

        assertEquals("class_mismatch", result.reason());
    }

    @Test
    @DisplayName("a consultation is queued with its flag")
    void consultationIsQueued() {
        var fixtures = new Fixtures(Map.of("architecture",
                ModelFixtures.role("architecture", RoleClass.READ_ONLY, RoleForm.FRESH)));
        fixtures.qualifications.qualifies("architecture", RoleForm.FRESH);

        var result = fixtures.caller.call(job("decision", "architecture", true));

        assertAll(
                () -> assertInstanceOf(ModelCallResult.Queued.class, result),
                () -> assertTrue(fixtures.queue.enqueued().getFirst().consultation()));
    }

    @Test
    @DisplayName("a role whose start-failure guard holds closes role_unavailable")
    void unavailableRoleCloses() {
        var fixtures = new Fixtures(Map.of(IMPLEMENT,
                ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH)));
        fixtures.qualifications.qualifies(IMPLEMENT, RoleForm.FRESH);
        fixtures.guard.holds(IMPLEMENT);

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_unavailable", result.reason()),
                () -> assertTrue(fixtures.queue.enqueued().isEmpty()));
    }

    @Nested
    @DisplayName("skips Qualify")
    class SkipsQualify {

        @Test
        @DisplayName("a judge task is queued without a qualification record")
        void judgeIsQueued() {
            var fixtures = new Fixtures(Map.of(JUDGE,
                    ModelFixtures.role(JUDGE, RoleClass.ZERO_TOOL, RoleForm.FRESH)));

            var result = fixtures.caller.call(job("zero_tool", JUDGE, false));

            assertAll(
                    () -> assertInstanceOf(ModelCallResult.Queued.class, result),
                    () -> assertTrue(fixtures.qualifications.looked().isEmpty(), "Qualify must be skipped"));
        }

        @Test
        @DisplayName("a steward task is queued from its machine configuration, not from the role resolver")
        void stewardIsQueued() {
            var steward = ModelFixtures.roleIn(STEWARD, RoleClass.READ_ONLY, RoleForm.FRESH, "machine-harness",
                    "machine-standard");
            var artifactSteward = ModelFixtures.role(STEWARD, RoleClass.READ_ONLY, RoleForm.FRESH);
            var fixtures = new Fixtures(ModelFixtures.resolver(Map.of(STEWARD, artifactSteward)),
                    ModelFixtures.resolver(Map.of(STEWARD, steward)));

            var result = fixtures.caller.call(job("decision", STEWARD, false));

            assertAll(
                    () -> assertInstanceOf(ModelCallResult.Queued.class, result),
                    () -> assertTrue(fixtures.qualifications.looked().isEmpty(), "Qualify must be skipped"),
                    () -> assertEquals("machine-harness", fixtures.queue.enqueued().getFirst().harness()),
                    () -> assertEquals("machine-standard", fixtures.queue.enqueued().getFirst().model()));
        }
    }

    private static ModelJob job(String entityKind, String role, boolean consultation) {
        return consultation
                ? ModelJob.consultation(Optional.of("plan-1"), STATE, "entity-1", role)
                : ModelJob.forModelJob(Optional.of("plan-1"), STATE, "entity-1", entityKind, role);
    }

    /** One caller with its recorders. */
    private static final class Fixtures {

        private final ModelFixtures.Qualifications qualifications = new ModelFixtures.Qualifications();
        private final ModelFixtures.Guard guard = new ModelFixtures.Guard();
        private final ModelFixtures.RecordingQueue queue = new ModelFixtures.RecordingQueue();
        private final ModelCaller caller;

        private Fixtures(Map<String, EffectiveRole> roles) {
            this(ModelFixtures.resolver(roles), ModelFixtures.resolver(Map.of()));
        }

        private Fixtures(RoleResolver roles, RoleResolver steward) {
            this.caller = new ModelCaller(roles, steward, qualifications, guard, queue);
        }
    }
}
