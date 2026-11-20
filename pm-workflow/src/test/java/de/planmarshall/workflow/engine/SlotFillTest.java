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

import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.T0;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.WorkflowCatalog;

/**
 * Slot fills of PLAN-V2-15 as PLAN-V2-157 finishes them: the engine fills the slot a state declares from the task
 * submission it accepted (only the outcome {@code submitted}) and from the operator's answer to the link its act names,
 * validates the value against the slot's closed type, and stores it under {@code <declaring unit id>:<slot name>}. A
 * value that does not fit the type is refused {@code invalid_link_args}, so the submitter or the operator can correct
 * it.
 * <p>
 * The units are written for the test; the operator link binds the free parameter of the fixture primitive
 * {@code test.note}.
 */
@EnableGeneratorController
@DisplayName("Slot fills")
class SlotFillTest {

    /** The slot the fixture units declare and fill. */
    private static final String SLOT = "commit_message";

    /** The key the store holds the slot under: the declaring unit's id and the slot name (Decision 4). */
    private static final String SLOT_KEY = "plan:" + SLOT;

    /** The conventional-commit type of a generated message: the grammar is the contract. */
    private static final String COMMIT_TYPE = "fix(engine): ";

    /** A plan whose task state fills the declared slot from the submission's {@code message}. */
    private static final String TASK_UNIT = """
            ---
            name: plan
            description: A unit written for the tests of pm-workflow.
            type: workflow
            icon: workflow.default
            slot: commit_message
            slot_type: commit_message[1..2000]
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow plan version 1 format 1
            scope plan
            start execute.review

            state execute.review
                fills slot.commit_message from message
                job llm change role reviewer
                    approved -> completed
                    lost -> handed_back

            state completed terminal merged success

            state handed_back terminal handed_back failure
            """;

    /** A plan whose operator state fills the declared slot from the {@code note} of the link's free parameters. */
    private static final String OPERATOR_UNIT = """
            ---
            name: plan
            description: A unit written for the tests of pm-workflow.
            type: workflow
            icon: workflow.default
            slot: commit_message
            slot_type: commit_message[1..2000]
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow plan version 1 format 1
            scope plan
            start execute.answer

            state execute.answer
                fills slot.commit_message from note
                link submit answer (note: text[1..80])
                    do test.note
                        done -> completed
                        failed -> handed_back

            state completed terminal merged success

            state handed_back terminal handed_back failure
            """;

    private final EngineFixtures fixtures = new EngineFixtures();

    /**
     * @param unit the unit of the engine
     * @return an engine whose binder fills the free {@code note} of an operator link from its submission
     */
    private WorkflowEngine operatorEngine(WorkflowDefinition unit) {
        return new WorkflowEngine(unit, WorkflowCatalog.of(), fixtures.validator(), fixtures.registry(),
                noteBinder(), fixtures.ports());
    }

    /**
     * @return an EasyMock binder that fills the free {@code note} of the fixture primitive {@code test.note} from the
     *         operator's submission, and binds the empty record for every other primitive
     */
    @SuppressWarnings("unchecked")
    private static WorkflowEngine.ParameterBinder noteBinder() {
        WorkflowEngine.ParameterBinder binder = createMock(WorkflowEngine.ParameterBinder.class);
        expect(binder.bind(anyObject(), anyObject())).andReturn(new EngineFixtures.NoParams()).anyTimes();
        expect(binder.bind(anyObject(), anyObject(), anyObject())).andAnswer(() -> new EngineFixtures.NoteParams(
                ((Map<String, String>) getCurrentArguments()[2]).get("note"))).anyTimes();
        replay(binder);
        return binder;
    }

    /** @return a generated Conventional Commit message: the type prefix is the grammar, the rest is generated */
    private static String generatedMessage() {
        return COMMIT_TYPE + Generators.letterStrings(1, 40).next();
    }

    @Nested
    @DisplayName("Task submission")
    class TaskSubmission {

        @Test
        @DisplayName("Should fill the declared slot from the submission the settlement carries")
        void shouldFillFromTheSubmission() {
            var message = generatedMessage();
            var engine = fixtures.engine(parse("plan.pmwf", TASK_UNIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var filled = engine.advance(atTask, new EngineEvent.TasksSettled(Map.of("change", "submitted"),
                    Map.of("change", Map.of("message", message)), "approved")).status();

            var slot = filled.slots().get(SLOT_KEY);
            assertNotNull(slot, "the settlement fills the declared slot");
            assertAll(
                    () -> assertEquals("completed", filled.state(), "the approved settlement routes the task"),
                    () -> assertEquals(message, slot.value(), "the slot carries the submitted message"),
                    () -> assertEquals("task_submit", slot.source(), "the slot records a task submission"),
                    () -> assertEquals("change", slot.sourceRef(), "the slot names the submitting entity"),
                    () -> assertEquals(T0, slot.filledAt(), "the slot records the instant it was filled"));
        }

        @Test
        @DisplayName("Should refuse a submitted value that is no commit message")
        void shouldRefuseAnInvalidSubmission() {
            var notACommit = Generators.letterStrings(1, 40).next();
            var engine = fixtures.engine(parse("plan.pmwf", TASK_UNIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var settlement = new EngineEvent.TasksSettled(Map.of("change", "submitted"),
                    Map.of("change", Map.of("message", notACommit)), "approved");

            var refusal = assertThrows(IllegalArgumentException.class, () -> engine.advance(atTask, settlement),
                    "a submitted value that is no commit message is refused");

            assertTrue(refusal.getMessage().startsWith("invalid_link_args:"),
                    "the refusal is the link-argument refusal to correct");
        }

        @Test
        @DisplayName("Should not fill the slot for an outcome that submitted nothing")
        void shouldNotFillForALostOutcome() {
            var message = generatedMessage();
            var engine = fixtures.engine(parse("plan.pmwf", TASK_UNIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var filled = engine.advance(atTask, new EngineEvent.TasksSettled(Map.of("change", "lost"),
                    Map.of("change", Map.of("message", message)), "lost")).status();

            assertAll(
                    () -> assertTrue(filled.slots().isEmpty(), "a lost task fills no slot"),
                    () -> assertEquals("handed_back", filled.state(), "the lost settlement routes the task"));
        }

        @Test
        @DisplayName("Should leave the slot empty when no submission carries its parameter")
        void shouldLeaveTheSlotEmpty() {
            var other = Generators.letterStrings(1, 40).next();
            var engine = fixtures.engine(parse("plan.pmwf", TASK_UNIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var filled = engine.advance(atTask, new EngineEvent.TasksSettled(Map.of("change", "submitted"),
                    Map.of("change", Map.of("other", other)), "approved")).status();

            assertTrue(filled.slots().isEmpty(), "a submission without the parameter fills no slot");
        }

        @Test
        @DisplayName("Should ignore a submission keyed by an entity the state does not await")
        void shouldIgnoreANonAwaitedSubmission() {
            var message = generatedMessage();
            var engine = fixtures.engine(parse("plan.pmwf", TASK_UNIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var filled = engine.advance(atTask, new EngineEvent.TasksSettled(Map.of("change", "submitted"),
                    Map.of("stranger", Map.of("message", message)), "approved")).status();

            assertTrue(filled.slots().isEmpty(), "a non-awaited entity never fills the slot");
        }
    }

    @Nested
    @DisplayName("Operator answer")
    class OperatorAnswer {

        @Test
        @DisplayName("Should fill the declared slot from the answer the operator act carries")
        void shouldFillFromTheOperatorAct() {
            var note = generatedMessage();
            var engine = operatorEngine(parse("plan.pmwf", OPERATOR_UNIT));
            var atLink = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var filled = engine.advance(atLink, new EngineEvent.OperatorAct("execute.answer", "answer",
                    Map.of("note", note))).status();

            var slot = filled.slots().get(SLOT_KEY);
            assertNotNull(slot, "the operator answer fills the declared slot");
            assertAll(
                    () -> assertEquals("completed", filled.state(), "the accepted act routes the link's done"),
                    () -> assertEquals(note, slot.value(), "the slot carries the operator's answer"),
                    () -> assertEquals("operator_answer", slot.source(), "the slot records an operator answer"),
                    () -> assertEquals("answer", slot.sourceRef(), "the slot names the link the act followed"),
                    () -> assertEquals(T0, slot.filledAt(), "the slot records the instant it was filled"));
        }

        @Test
        @DisplayName("Should refuse an answer that does not fit the slot's type")
        void shouldRefuseAnInvalidAnswer() {
            var notACommit = Generators.letterStrings(1, 40).next();
            var engine = operatorEngine(parse("plan.pmwf", OPERATOR_UNIT));
            var atLink = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var act = new EngineEvent.OperatorAct("execute.answer", "answer", Map.of("note", notACommit));

            var refusal = assertThrows(IllegalArgumentException.class, () -> engine.advance(atLink, act),
                    "an answer that is no commit message is refused");

            assertTrue(refusal.getMessage().startsWith("invalid_link_args:"),
                    "the refusal is the link-argument refusal to correct");
        }

        @Test
        @DisplayName("Should leave the slot empty when the act carries no value for its parameter")
        void shouldLeaveTheSlotEmpty() {
            var other = Generators.letterStrings(1, 40).next();
            var engine = operatorEngine(parse("plan.pmwf", OPERATOR_UNIT));
            var atLink = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var filled = engine.advance(atLink, new EngineEvent.OperatorAct("execute.answer", "answer",
                    Map.of("other", other))).status();

            assertTrue(filled.slots().isEmpty(), "an act without the parameter fills no slot");
        }
    }
}
