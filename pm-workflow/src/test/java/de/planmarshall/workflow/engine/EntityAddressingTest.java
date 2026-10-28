/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static de.planmarshall.workflow.engine.EngineFixtures.ENRICH_KIND;
import static de.planmarshall.workflow.engine.EngineFixtures.EXECUTE;
import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.WORKSPACE_ENRICH;
import static de.planmarshall.workflow.engine.EngineFixtures.WORKSPACE_IDLE;
import static de.planmarshall.workflow.engine.EngineFixtures.WORKSPACE_REVIEW;
import static de.planmarshall.workflow.engine.EngineFixtures.queuedTask;
import static de.planmarshall.workflow.engine.EngineFixtures.workspaceStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.PendingEntryReader.EntryRead;
import de.planmarshall.core.store.EntryId;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.engine.AddressResolution.Reason;
import de.planmarshall.workflow.engine.AddressResolution.Refused;
import de.planmarshall.workflow.engine.AddressResolution.Resolved;
import de.planmarshall.workflow.verify.EntityKindRegistry;

/**
 * The addressing of the entities of the workspace scope in its fixture unit (PM-ARCH-4, PM-WF-1): every entity kind
 * the workspace scope hosts is resolved, the grammar of an entity identifier is checked at its bounds, and an address
 * of another scope is refused {@code scope_not_found}, without a read of a pending entry record for a plan or epic
 * address.
 */
@DisplayName("Entity addressing of the workspace scope")
class EntityAddressingTest {

    private static final String PLAN_ENTRY = "ent-0a1b2c3d";
    private static final String EPIC_ENTRY = "ent-0a1b2c3d4e5f60718293a4b5c6d7e8f9";
    private static final String CORE_ENRICHMENT = "enrich-core";
    private static final String WORKFLOW_ENRICHMENT = "enrich-pm-workflow";
    private static final String LESSON_TASK = "qt-00000000000000a1";
    private static final String PLAN_TASK = "qt-00000000000000b2";
    private static final String EPIC_ID = "engine-epic";

    private static final String EPIC_UNIT = """
            ---
            name: epic
            description: A unit written for the tests of pm-workflow.
            type: workflow
            icon: workflow.default
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow epic version 1 format 1
            scope epic
            start epic.work

            state epic.work
                phase epic
                auto test.step
                    done -> epic.done
                    failed -> epic.done

            state epic.done terminal done success
                phase epic
            """;

    private final EngineFixtures fixtures = new EngineFixtures();
    private final EntityAddressResolver resolver = fixtures.resolver();
    private final WorkflowDefinition workspace = EngineFixtures.workspaceUnit();

    /** @return the workspace scope at its fan-out state, one enrichment task per entity, numbered in order */
    private static WorkflowStatus enriching(String... entities) {
        var tasks = new LinkedHashMap<String, WorkflowStatus.TaskEntry>();
        for (var entity : entities) {
            tasks.put(entity, queuedTask("qt-%016x".formatted(tasks.size() + 1), "architect"));
        }
        return workspaceStatus(WORKSPACE_ENRICH, tasks);
    }

    /** @return the workspace scope at its lesson review, with the one task {@link #LESSON_TASK} */
    private static WorkflowStatus reviewing() {
        return workspaceStatus(WORKSPACE_REVIEW, Map.of(LESSON_TASK, queuedTask(LESSON_TASK, "lessons")));
    }

    /** @return the plan scope at its task state {@code execute.review}, with the one task {@link #PLAN_TASK} */
    private static WorkflowStatus planReviewing() {
        return EngineFixtures.planStatus(EXECUTE, "execute.review")
                .withActiveTasks(Map.of(PLAN_TASK, queuedTask(PLAN_TASK, "reviewer")));
    }

    private static WorkflowDefinition planUnit() {
        return EngineFixtures.plan("execute.enqueue", EngineFixtures.WAIT_STATES);
    }

    private AddressResolution inWorkspace(String entity, WorkflowStatus status) {
        return resolver.resolve(EntityAddress.workspace(entity), status, workspace);
    }

    private static Refused refused(Reason reason, AddressResolution resolution) {
        var refusal = assertInstanceOf(Refused.class, resolution, () -> "expected a refusal: " + resolution);
        assertEquals(reason, refusal.reason(), refusal::detail);
        return refusal;
    }

    @Nested
    @DisplayName("Every entity kind of the workspace scope is resolved")
    class EveryKindResolved {

        @Test
        @DisplayName("A pending plan bootstrap by its entry_id")
        void bootstrapPlan() {
            fixtures.entryReader.answer(PLAN_ENTRY, new EntryRead.Live("plan"));
            var address = EntityAddress.workspace(PLAN_ENTRY);

            var resolution = resolver.resolve(address, reviewing(), workspace);

            assertAll(
                    () -> assertEquals(new Resolved(address, AddressResolution.BOOTSTRAP_PLAN, Optional.empty()),
                            resolution),
                    () -> assertEquals(List.of(new EntryId(PLAN_ENTRY)), fixtures.entryReader.reads()));
        }

        @Test
        @DisplayName("A pending epic bootstrap by its entry_id")
        void bootstrapEpic() {
            fixtures.entryReader.answer(EPIC_ENTRY, new EntryRead.Live("epic"));
            var address = EntityAddress.workspace(EPIC_ENTRY);

            var resolution = resolver.resolve(address, enriching(CORE_ENRICHMENT), workspace);

            assertEquals(new Resolved(address, AddressResolution.BOOTSTRAP_EPIC, Optional.empty()), resolution);
        }

        @Test
        @DisplayName("An enrichment task by its entity enrich-<module_id>, without a read of an entry record")
        void enrichment() {
            var address = EntityAddress.workspace(WORKFLOW_ENRICHMENT);

            var resolution = resolver.resolve(address, enriching(CORE_ENRICHMENT, WORKFLOW_ENRICHMENT), workspace);

            assertAll(
                    () -> assertEquals(new Resolved(address, ENRICH_KIND, Optional.of("qt-0000000000000002")),
                            resolution),
                    () -> assertTrue(fixtures.entryReader.reads().isEmpty()));
        }

        @Test
        @DisplayName("The lesson review under the kind lessons-review by its queue_task_id")
        void lessonReview() {
            var address = EntityAddress.workspace(LESSON_TASK);

            var resolution = resolver.resolve(address, reviewing(), workspace);

            assertEquals(new Resolved(address, "lessons-review", Optional.of(LESSON_TASK)), resolution);
        }
    }

    @Nested
    @DisplayName("The grammar of an entity identifier is checked at its bounds")
    class Grammar {

        static Stream<Arguments> acceptedTaskEntities() {
            return Stream.of(
                    Arguments.of("entity of 1 character", "a"),
                    Arguments.of("entity of 64 characters", "a".repeat(64)),
                    Arguments.of("module_id of 57 characters", "enrich-" + "m".repeat(57)));
        }

        static Stream<Arguments> refusedEntities() {
            return Stream.of(
                    Arguments.of("empty entity", ""),
                    Arguments.of("entity of 65 characters", "a".repeat(65)),
                    Arguments.of("entry_id of 7 hex digits", "ent-0a1b2c3"),
                    Arguments.of("entry_id of 33 hex digits", EPIC_ENTRY + "0"),
                    Arguments.of("entry_id of upper-case hex digits", "ent-0A1B2C3D"),
                    Arguments.of("module_id of 58 characters", "enrich-" + "m".repeat(58)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("acceptedTaskEntities")
        @DisplayName("A task entity within its bounds is resolved")
        void acceptedTaskEntity(String bound, String entity) {
            var resolution = inWorkspace(entity, enriching(entity));

            assertEquals(new Resolved(EntityAddress.workspace(entity), ENRICH_KIND,
                    Optional.of("qt-0000000000000001")), resolution, bound);
        }

        @ParameterizedTest
        @ValueSource(strings = {PLAN_ENTRY, EPIC_ENTRY})
        @DisplayName("An entry_id of 8 and of 32 hex digits is resolved, read as the typed EntryId")
        void acceptedEntry(String entryId) {
            fixtures.entryReader.answer(entryId, new EntryRead.Live("plan"));

            var resolution = inWorkspace(entryId, reviewing());

            assertAll(
                    () -> assertEquals(AddressResolution.BOOTSTRAP_PLAN,
                            assertInstanceOf(Resolved.class, resolution).entityKind()),
                    () -> assertEquals(List.of(new EntryId(entryId)), fixtures.entryReader.reads()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedEntities")
        @DisplayName("An identifier outside its grammar is refused invalid_arguments without a read")
        void refusedEntity(String bound, String entity) {
            var resolution = inWorkspace(entity, enriching(CORE_ENRICHMENT));

            assertAll(bound,
                    () -> refused(Reason.INVALID_ARGUMENTS, resolution),
                    () -> assertTrue(fixtures.entryReader.reads().isEmpty()));
        }
    }

    @Nested
    @DisplayName("An address of another scope is refused scope_not_found")
    class OtherScope {

        private final WorkflowStatus epicStatus = EngineFixtures.planStatus("epic", "epic.work").toBuilder()
                .scopeType(JournalScopeType.EPIC)
                .scopeId(EPIC_ID)
                .build();

        @ParameterizedTest
        @ValueSource(strings = {PLAN_ENTRY, EPIC_ENTRY, CORE_ENRICHMENT, LESSON_TASK})
        @DisplayName("A workspace entity under a plan, without a read of an entry record")
        void workspaceEntityUnderPlan(String entity) {
            var resolution = resolver.resolve(EntityAddress.plan(PLAN_ID, entity), planReviewing(), planUnit());

            assertAll(
                    () -> refused(Reason.SCOPE_NOT_FOUND, resolution),
                    () -> assertTrue(fixtures.entryReader.reads().isEmpty()));
        }

        @ParameterizedTest
        @ValueSource(strings = {PLAN_ENTRY, EPIC_ENTRY, CORE_ENRICHMENT, LESSON_TASK})
        @DisplayName("A workspace entity under an epic, without a read of an entry record")
        void workspaceEntityUnderEpic(String entity) {
            var epicUnit = EngineFixtures.parse("epic.pmwf", EPIC_UNIT);

            var resolution = resolver.resolve(EntityAddress.epic(EPIC_ID, entity), epicStatus, epicUnit);

            assertAll(
                    () -> refused(Reason.SCOPE_NOT_FOUND, resolution),
                    () -> assertTrue(fixtures.entryReader.reads().isEmpty()));
        }

        @Test
        @DisplayName("A plan task under NO_PLAN, while the plan resolves it")
        void planTaskUnderNoPlan() {
            var address = EntityAddress.fromArguments(Optional.of(EntityAddress.NO_PLAN), Optional.empty(),
                    PLAN_TASK);

            assertAll(
                    () -> refused(Reason.SCOPE_NOT_FOUND, resolver.resolve(address, reviewing(), workspace)),
                    () -> refused(Reason.SCOPE_NOT_FOUND,
                            resolver.resolve(address, enriching(CORE_ENRICHMENT), workspace)),
                    () -> assertEquals(new Resolved(EntityAddress.plan(PLAN_ID, PLAN_TASK), "change",
                                    Optional.of(PLAN_TASK)),
                            resolver.resolve(EntityAddress.plan(PLAN_ID, PLAN_TASK), planReviewing(), planUnit())));
        }

        @Test
        @DisplayName("A task while the workspace rests in a state that hosts none")
        void stateWithoutTask() {
            var resolution = inWorkspace(CORE_ENRICHMENT, workspaceStatus(WORKSPACE_IDLE, Map.of()));

            assertTrue(refused(Reason.SCOPE_NOT_FOUND, resolution).detail().contains(WORKSPACE_IDLE));
        }
    }

    @Nested
    @DisplayName("The pending entry record decides a bootstrap address")
    class EntryRecord {

        @Test
        @DisplayName("A deleted entry is refused scope_not_found")
        void deleted() {
            var resolution = inWorkspace(PLAN_ENTRY, reviewing());

            assertAll(
                    () -> refused(Reason.SCOPE_NOT_FOUND, resolution),
                    () -> assertEquals(List.of(new EntryId(PLAN_ENTRY)), fixtures.entryReader.reads()));
        }

        @Test
        @DisplayName("An unreadable entry is refused entry_unreadable and never resolved")
        void unreadable() {
            fixtures.entryReader.answer(PLAN_ENTRY, new EntryRead.Unreadable("truncated record"));

            var resolution = inWorkspace(PLAN_ENTRY, reviewing());

            assertTrue(refused(Reason.ENTRY_UNREADABLE, resolution).detail().contains("truncated record"));
        }

        @Test
        @DisplayName("An entry of a kind that is neither plan nor epic is refused entry_unreadable")
        void unknownKind() {
            fixtures.entryReader.answer(PLAN_ENTRY, new EntryRead.Live("lesson"));

            var resolution = inWorkspace(PLAN_ENTRY, reviewing());

            assertTrue(refused(Reason.ENTRY_UNREADABLE, resolution).detail().contains("lesson"));
        }
    }

    @Nested
    @DisplayName("A malformed call is a programming error")
    class Misuse {

        @Test
        @DisplayName("The status of another scope, a state the unit lacks, or an unregistered entity kind")
        void inconsistentArguments() {
            var unregistered = new EntityAddressResolver(fixtures.entryReader, EntityKindRegistry.defaults());
            var lessonAddress = EntityAddress.workspace(LESSON_TASK);
            var otherPlanAddress = EntityAddress.plan("other-plan", PLAN_TASK);
            var coreAddress = EntityAddress.workspace(CORE_ENRICHMENT);
            var planStatus = planReviewing();
            var planDefinition = planUnit();
            var missingState = workspaceStatus("workspace.missing", Map.of());
            var coreEnriching = enriching(CORE_ENRICHMENT);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> resolver.resolve(lessonAddress, planStatus, workspace)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> resolver.resolve(otherPlanAddress, planStatus, planDefinition)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> inWorkspace(LESSON_TASK, missingState)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> unregistered.resolve(coreAddress, coreEnriching, workspace)));
        }

        @Test
        @DisplayName("An address with a scope id where none belongs or none where one does")
        void malformedAddress() {
            var none = Optional.<String>empty();
            var planId = Optional.of(PLAN_ID);
            var epicId = Optional.of(EPIC_ID);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EntityAddress(JournalScopeType.LESSONS, none, LESSON_TASK)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EntityAddress(JournalScopeType.PLAN, none, LESSON_TASK)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EntityAddress(JournalScopeType.WORKSPACE, planId, LESSON_TASK)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> EntityAddress.plan(EntityAddress.NO_PLAN, LESSON_TASK)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> EntityAddress.fromArguments(none, none, LESSON_TASK)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> EntityAddress.fromArguments(planId, epicId, LESSON_TASK)));
        }

        @Test
        @DisplayName("A resolution with a blank entity kind or a blank refusal detail")
        void blankResolution() {
            var address = EntityAddress.workspace(LESSON_TASK);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Resolved(address, " ", Optional.empty())),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Refused(Reason.SCOPE_NOT_FOUND, " ")));
        }
    }

    @Test
    @DisplayName("The tool arguments name the scope: plan_id, epic_id, or plan_id NO_PLAN")
    void addressFromArguments() {
        assertAll(
                () -> assertEquals(EntityAddress.plan(PLAN_ID, PLAN_TASK),
                        EntityAddress.fromArguments(Optional.of(PLAN_ID), Optional.empty(), PLAN_TASK)),
                () -> assertEquals(EntityAddress.epic(EPIC_ID, PLAN_TASK),
                        EntityAddress.fromArguments(Optional.empty(), Optional.of(EPIC_ID), PLAN_TASK)),
                () -> assertTrue(EntityAddress.fromArguments(Optional.of(EntityAddress.NO_PLAN), Optional.empty(),
                        PLAN_TASK).isWorkspace()));
    }

    @Test
    @DisplayName("The refusal reasons carry their wire names")
    void reasonWireNames() {
        assertEquals(List.of("invalid_arguments", "scope_not_found", "entry_unreadable"),
                Stream.of(Reason.values()).map(Reason::wireName).toList());
    }
}
