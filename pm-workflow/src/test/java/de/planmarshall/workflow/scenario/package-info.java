/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The deterministic scenario replay harness {@code pm-scenario} (PM-TEST-4), a test-scope part of this module:
 * JUnit, in process, against fixture units written for the test, without a live model.
 * <p>
 * A {@link de.planmarshall.workflow.scenario.ScenarioScript} is read from the text of a script in the test: a line
 * {@code scenario <name>}, the declaration of the scripted worker of each role, and one directive per line. The
 * harness runs the directives the engine supports today: {@code start}, {@code complete_job},
 * {@code answer_question}, {@code expire_question}, {@code assert_state}, {@code assert_outcome} and
 * {@code assert_task_outcome}. Every other directive the specification names (the link and worker directives, the
 * worker faults, the runtime restart, the operator verbs) is refused by name with its line and the work package that
 * brings it.
 * <p>
 * The {@link de.planmarshall.workflow.scenario.ScenarioReplayRunner} replays a script against the real
 * {@link de.planmarshall.workflow.engine.WorkflowEngine} over the fixtures of
 * {@link de.planmarshall.workflow.scenario.ScenarioFixtures}, with the status store of the scope in a temporary
 * directory and without git; a task state is settled by the
 * {@link de.planmarshall.workflow.scenario.ScriptedFixtureWorker} of its role, which settles the queued task directly
 * with the outcome the script declares. The runner keeps the stored status bytes after every step; the determinism
 * proof replays a script twice from empty stores and compares them step by step.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/module-structure.adoc} (Test Placement)</li>
 * <li>{@code doc/specification/workflow-dsl/07-loop-back-diagrams-verification.adoc}</li>
 * </ul>
 */
package de.planmarshall.workflow.scenario;
