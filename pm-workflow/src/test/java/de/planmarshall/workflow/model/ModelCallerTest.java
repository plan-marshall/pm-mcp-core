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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleCatalog;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleResolution;
import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.core.role.RoleValidationReason;
import org.jspecify.annotations.Nullable;

/**
 * The one model caller of {@code model-work/01-roles-and-configuration.adoc#model-caller}: Resolve, Qualify, Available,
 * Queue, Serve in that order; the {@code judge} and the {@code steward} skip Qualify and are queued without a qualification
 * record; a task closed without an attempt carries {@code role_undefined}, {@code class_mismatch},
 * {@code role_unqualified} or {@code role_unavailable}.
 */
@EnableGeneratorController
@DisplayName("The model caller")
class ModelCallerTest {

    // The fixture state id and the fixture role name are the keys the caller is exercised with.
    private static final String STATE = "implement.envelope-3";
    private static final String IMPLEMENT = "implement";
    private static final String JUDGE = RoleCatalog.JUDGE;
    private static final String STEWARD = RoleCatalog.STEWARD;

    @Test
    @DisplayName("a qualified fresh role is queued with its effective configuration")
    void qualifiedRoleIsQueued() {
        var role = ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH);
        var fixtures = new Fixtures(Map.of(IMPLEMENT, role));
        fixtures.qualifies(IMPLEMENT, RoleForm.FRESH);

        var result = fixtures.caller.call(job("task", IMPLEMENT, false));

        var queued = (ModelCallResult.Queued) result;
        var request = fixtures.enqueued().getFirst();
        assertAll(
                () -> assertEquals(ModelFixtures.QUEUE_TASK, queued.queueTaskId()),
                () -> assertEquals(IMPLEMENT, queued.role()),
                () -> assertEquals(RoleForm.FRESH, queued.form()),
                () -> assertFalse(queued.formDowngraded()),
                () -> assertEquals(role.configurationDigest(), request.configurationDigest()),
                () -> assertEquals(role.qualificationDigest(), fixtures.digests().getFirst()),
                () -> assertEquals(ModelFixtures.HARNESS, request.harness()),
                () -> assertEquals(ModelFixtures.MODEL, request.model()),
                () -> assertEquals(STATE, request.stateId()),
                () -> assertFalse(request.consultation()),
                () -> assertEquals(IMPLEMENT, fixtures.looked().getFirst()));
    }

    @Test
    @DisplayName("a qualified warm role is downgraded to fresh when only the fresh form qualifies")
    void warmRoleIsDowngraded() {
        var fixtures = new Fixtures(Map.of(IMPLEMENT,
                ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.WARM)));
        fixtures.qualifies(IMPLEMENT, RoleForm.FRESH);

        var queued = (ModelCallResult.Queued) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals(RoleForm.FRESH, queued.form()),
                () -> assertTrue(queued.formDowngraded()),
                () -> assertEquals(RoleForm.FRESH, fixtures.enqueued().getFirst().form()));
    }

    @Test
    @DisplayName("a role without a qualification record closes role_unqualified")
    void unqualifiedRoleCloses() {
        var fixtures = new Fixtures(Map.of(IMPLEMENT,
                ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH)));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_unqualified", result.reason()),
                () -> assertTrue(fixtures.enqueued().isEmpty()),
                () -> assertEquals("role_unqualified", fixtures.closed().getFirst().reason()));
    }

    @Test
    @DisplayName("an absent role closes role_undefined without a validation reason")
    void absentRoleCloses() {
        var fixtures = new Fixtures(Map.of());

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_undefined", result.reason()),
                () -> assertNull(result.validationReason(), "an absent role carries no validation reason"),
                () -> assertTrue(fixtures.enqueued().isEmpty()));
    }

    @Test
    @DisplayName("an invalid role closes role_undefined naming the reason")
    void invalidRoleClosesWithReason() {
        var detail = Generators.letterStrings(10, 40).next();
        RoleResolver refusing = name -> new RoleResolution.Undefined(name,
                RoleValidationReason.MODEL_NOT_ALLOWED, detail);
        var fixtures = new Fixtures(refusing, ModelFixtures.resolver(Map.of()));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_undefined", result.reason()),
                () -> assertEquals(RoleValidationReason.MODEL_NOT_ALLOWED, result.validationReason(),
                        "the closed result names the validation reason"));
    }

    @Test
    @DisplayName("a read_only role on the task kind closes class_mismatch")
    void classMismatchCloses() {
        var fixtures = new Fixtures(Map.of("review-code",
                ModelFixtures.role("review-code", RoleClass.READ_ONLY, RoleForm.FRESH)));

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", "review-code", false));

        assertAll(
                () -> assertEquals("class_mismatch", result.reason()),
                () -> assertTrue(fixtures.enqueued().isEmpty()));
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
        fixtures.qualifies("architecture", RoleForm.FRESH);

        var result = fixtures.caller.call(job("decision", "architecture", true));

        assertAll(
                () -> assertInstanceOf(ModelCallResult.Queued.class, result),
                () -> assertTrue(fixtures.enqueued().getFirst().consultation()));
    }

    @Test
    @DisplayName("a role whose start-failure guard holds closes role_unavailable")
    void unavailableRoleCloses() {
        var fixtures = new Fixtures(Map.of(IMPLEMENT,
                ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH)));
        fixtures.qualifies(IMPLEMENT, RoleForm.FRESH);
        fixtures.holds(IMPLEMENT);

        var result = (ModelCallResult.Closed) fixtures.caller.call(job("task", IMPLEMENT, false));

        assertAll(
                () -> assertEquals("role_unavailable", result.reason()),
                () -> assertTrue(fixtures.enqueued().isEmpty()));
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
                    () -> assertTrue(fixtures.looked().isEmpty(), "Qualify must be skipped"));
        }

        @Test
        @DisplayName("a steward task is queued from its machine configuration, not from the role resolver")
        void stewardIsQueued() {
            var harness = Generators.letterStrings(5, 12).next();
            var model = Generators.letterStrings(5, 12).next();
            var steward = ModelFixtures.roleIn(STEWARD, RoleClass.READ_ONLY, RoleForm.FRESH, harness, model);
            var artifactSteward = ModelFixtures.role(STEWARD, RoleClass.READ_ONLY, RoleForm.FRESH);
            var fixtures = new Fixtures(ModelFixtures.resolver(Map.of(STEWARD, artifactSteward)),
                    ModelFixtures.resolver(Map.of(STEWARD, steward)));

            var result = fixtures.caller.call(job("decision", STEWARD, false));

            assertAll(
                    () -> assertInstanceOf(ModelCallResult.Queued.class, result),
                    () -> assertTrue(fixtures.looked().isEmpty(), "Qualify must be skipped"),
                    () -> assertEquals(harness, fixtures.enqueued().getFirst().harness()),
                    () -> assertEquals(model, fixtures.enqueued().getFirst().model()));
        }
    }

    @Nested
    @DisplayName("carries the scope")
    class CarriesTheScope {

        @Test
        @DisplayName("a plan task is queued with its plan as the scope")
        void planTaskIsQueuedWithItsScope() {
            var fixtures = new Fixtures(Map.of(IMPLEMENT,
                    ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH)));
            fixtures.qualifies(IMPLEMENT, RoleForm.FRESH);
            var planId = planId();

            var result = fixtures.caller.call(job(planId, "task", IMPLEMENT, false));

            assertAll(
                    () -> assertInstanceOf(ModelCallResult.Queued.class, result, "the plan task must be queued"),
                    () -> assertEquals(planId, fixtures.enqueued().getFirst().scopeId(),
                            "the queued task carries the plan as its scope"));
        }

        @Test
        @DisplayName("a task of the workspace scope is queued without a scope id")
        void workspaceTaskIsQueued() {
            var fixtures = new Fixtures(Map.of(IMPLEMENT,
                    ModelFixtures.role(IMPLEMENT, RoleClass.WRITER, RoleForm.FRESH)));
            fixtures.qualifies(IMPLEMENT, RoleForm.FRESH);

            var result = fixtures.caller.call(job(null, "task", IMPLEMENT, false));

            assertAll(
                    () -> assertInstanceOf(ModelCallResult.Queued.class, result,
                            "the workspace task must be queued"),
                    () -> assertEquals(1, fixtures.enqueued().size(), "exactly one task is queued"),
                    () -> assertNull(fixtures.enqueued().getFirst().scopeId(),
                            "the queued workspace task has no scope id"),
                    () -> assertTrue(fixtures.closed().isEmpty(), "a queued task is not also closed"));
        }

        @Test
        @DisplayName("a task of the workspace scope closes without a scope id")
        void workspaceTaskCloses() {
            var fixtures = new Fixtures(Map.of());

            var result = (ModelCallResult.Closed) fixtures.caller.call(job(null, "task", IMPLEMENT, false));

            assertAll(
                    () -> assertEquals("role_undefined", result.reason(), "the absent role closes role_undefined"),
                    () -> assertNull(result.validationReason(), "an absent role carries no validation reason"),
                    () -> assertEquals(1, fixtures.closed().size(), "exactly one task is closed"),
                    () -> assertNull(fixtures.closed().getFirst().scopeId(),
                            "the closed workspace task has no scope id"),
                    () -> assertEquals("role_undefined", fixtures.closed().getFirst().reason(),
                            "the queue closes the task role_undefined"),
                    () -> assertTrue(fixtures.enqueued().isEmpty(), "a closed task is never queued"));
        }

        @Test
        @DisplayName("a consultation of the workspace scope is queued without a scope id")
        void workspaceConsultationIsQueued() {
            var fixtures = new Fixtures(Map.of("architecture",
                    ModelFixtures.role("architecture", RoleClass.READ_ONLY, RoleForm.FRESH)));
            fixtures.qualifies("architecture", RoleForm.FRESH);

            var result = fixtures.caller.call(job(null, "decision", "architecture", true));

            assertAll(
                    () -> assertInstanceOf(ModelCallResult.Queued.class, result,
                            "the workspace consultation is queued"),
                    () -> assertNull(fixtures.enqueued().getFirst().scopeId(),
                            "the workspace consultation has no scope id"),
                    () -> assertTrue(fixtures.enqueued().getFirst().consultation(),
                            "the queued task keeps its consultation flag"));
        }
    }

    private static ModelJob job(String entityKind, String role, boolean consultation) {
        return job(planId(), entityKind, role, consultation);
    }

    private static ModelJob job(@Nullable String scopeId, String entityKind, String role, boolean consultation) {
        var entity = "entity-" + Generators.letterStrings(3, 12).next();
        return consultation
                ? ModelJob.consultation(scopeId, STATE, entity, role)
                : ModelJob.forModelJob(scopeId, STATE, entity, entityKind, role);
    }

    private static String planId() {
        return "plan-" + Generators.letterStrings(3, 12).next();
    }

    /** One caller with its EasyMock task queue. */
    private static final class Fixtures {

        private final Capture<String> looked = newCapture(CaptureType.ALL);
        private final Capture<String> digests = newCapture(CaptureType.ALL);
        private final Map<String, RoleForm> forms = new LinkedHashMap<>();
        private final Set<String> held = new HashSet<>();
        private final QualificationLookup qualifications = qualifications();
        private final AvailabilityGuard guard = guard();
        private final Capture<ModelQueue.Enqueue> enqueued = newCapture(CaptureType.ALL);
        private final Capture<ModelQueue.Close> closed = newCapture(CaptureType.ALL);
        private final ModelQueue queue;
        private final ModelCaller caller;

        private QualificationLookup qualifications() {
            QualificationLookup lookup = createMock(QualificationLookup.class);
            expect(lookup.qualifiedForm(capture(looked), capture(digests), anyObject(RoleForm.class)))
                    .andAnswer(() -> Optional.ofNullable(forms.get((String) getCurrentArguments()[0]))).anyTimes();
            replay(lookup);
            return lookup;
        }

        private AvailabilityGuard guard() {
            AvailabilityGuard mock = createMock(AvailabilityGuard.class);
            expect(mock.holds(anyObject(String.class), anyObject(String.class)))
                    .andAnswer(() -> held.contains((String) getCurrentArguments()[0])).anyTimes();
            replay(mock);
            return mock;
        }

        private void qualifies(String role, RoleForm form) {
            forms.put(role, form);
        }

        private void holds(String role) {
            held.add(role);
        }

        private List<String> looked() {
            return looked.getValues();
        }

        private List<String> digests() {
            return digests.getValues();
        }

        private Fixtures(Map<String, EffectiveRole> roles) {
            this(ModelFixtures.resolver(roles), ModelFixtures.resolver(Map.of()));
        }

        private Fixtures(RoleResolver roles, RoleResolver steward) {
            queue = createMock(ModelQueue.class);
            expect(queue.enqueue(capture(enqueued)))
                    .andReturn(new ModelQueue.Queued(ModelFixtures.QUEUE_TASK)).anyTimes();
            queue.close(capture(closed));
            expectLastCall().anyTimes();
            replay(queue);
            this.caller = new ModelCaller(roles, steward, qualifications, guard, queue);
        }

        private List<ModelQueue.Enqueue> enqueued() {
            return enqueued.getValues();
        }

        private List<ModelQueue.Close> closed() {
            return closed.getValues();
        }
    }
}
