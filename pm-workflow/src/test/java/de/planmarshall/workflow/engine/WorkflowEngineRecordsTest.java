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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.PlanHistoryLine;

/**
 * Proves that the builders of the records of {@link WorkflowEngine} build the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Workflow engine record builders")
class WorkflowEngineRecordsTest {

    @Test
    @DisplayName("Ports builder builds the canonical constructor's value")
    void portsBuilderEqualsConstructor() {
        var jobs = new EngineFixtures.RecordingJobStarter();
        var tasks = new EngineFixtures.RecordingQueueTaskCreator();
        var questions = new EngineFixtures.FixedQuestionRecordReader();
        var clock = new EngineFixtures.ManualClock(Instant.EPOCH);
        var constructed = new WorkflowEngine.Ports(jobs, tasks, questions, clock);
        var built = WorkflowEngine.Ports.builder()
                .jobs(jobs)
                .tasks(tasks)
                .questions(questions)
                .clock(clock)
                .build();

        assertEquals(constructed, built, "the builder builds the same engine ports");
    }

    @Test
    @DisplayName("Ports builder refuses a missing jobs port")
    void portsBuilderRefusesMissingJobs() {
        var tasks = new EngineFixtures.RecordingQueueTaskCreator();
        var questions = new EngineFixtures.FixedQuestionRecordReader();
        var clock = new EngineFixtures.ManualClock(Instant.EPOCH);
        var builder = WorkflowEngine.Ports.builder()
                .tasks(tasks)
                .questions(questions)
                .clock(clock);
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("jobs", exception.getMessage(), "the builder names the missing jobs port");
    }

    @Test
    @DisplayName("Pending builder builds the canonical constructor's value")
    void pendingBuilderEqualsConstructor() throws Exception {
        var pendingClass = Class.forName("de.planmarshall.workflow.engine.WorkflowEngine$Run$Pending");
        var event = Generators.enumValues(PlanHistoryLine.Event.class).next();
        var node = Generators.letterStrings(1, 8).next();
        var callPath = List.of(Generators.letterStrings(1, 8).next());
        var phase = Generators.letterStrings(1, 8).next();
        var outcome = Generators.letterStrings(1, 8).next();
        var constructor = pendingClass.getDeclaredConstructor(PlanHistoryLine.Event.class, String.class, List.class,
                String.class, String.class);
        constructor.setAccessible(true);
        var constructed = constructor.newInstance(event, node, callPath, phase, outcome);
        var builderFactory = pendingClass.getDeclaredMethod("builder");
        builderFactory.setAccessible(true);
        var builder = builderFactory.invoke(null);
        invokeSetter(builder, "event", event);
        invokeSetter(builder, "node", node);
        invokeSetter(builder, "callPath", callPath);
        invokeSetter(builder, "phase", phase);
        invokeSetter(builder, "outcome", outcome);
        var build = builder.getClass().getDeclaredMethod("build");
        build.setAccessible(true);
        var built = build.invoke(builder);

        assertEquals(constructed, built, "the builder builds the pending history line");
    }

    private static void invokeSetter(Object builder, String name, Object value) throws ReflectiveOperationException {
        var setter = Arrays.stream(builder.getClass().getDeclaredMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == 1)
                .findFirst()
                .orElseThrow();
        setter.setAccessible(true);
        setter.invoke(builder, value);
    }
}
