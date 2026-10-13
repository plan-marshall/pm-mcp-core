/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.workflow.scenario.ScenarioReplayRunner.ScenarioFailure;
import de.planmarshall.workflow.scenario.ScenarioReplayRunner.Snapshot;

/**
 * The determinism of {@code pm-scenario} (PM-TEST-4): a script replayed twice from empty stores writes the same
 * {@code mcp/status.json} bytes after every step, and a unit with one changed transition writes the same bytes up to
 * the step that takes it and then fails at the script's next {@code assert_state}, which names its line.
 */
@DisplayName("Scenario determinism")
class ScenarioDeterminismTest {

    /** The script of the proof; the question expires on line 11 and line 12 asserts where its deadline leads. */
    private static final String SCRIPT = """
            scenario determinism
            worker reviewer submitted
            start plan scenario-plan
            assert_state execute.build
            complete_job b-0000000000000001 {"outcome":"succeeded"}
            assert_state execute.review
            assert_task_outcome reviewer submitted
            assert_state execute.ask
            answer_question Q-001 web {"branch_key":"approve"}
            assert_state execute.confirm
            expire_question Q-002
            assert_state completed
            assert_outcome merged
            """;

    /**
     * The transition the changed unit routes differently: the deadline of the clarification hands the plan back. Every
     * state stays reachable, so the changed unit passes the verifier.
     */
    private static final String TRANSITION = "assumed -> completed";

    private static final String CHANGED_TRANSITION = "assumed -> handed_back";

    @TempDir
    Path first;

    @TempDir
    Path second;

    private static List<Snapshot> replay(String unitText, Path directory) {
        var runner = new ScenarioReplayRunner(ScenarioFixtures.parse("scenario-plan.pmwf", unitText), directory);
        runner.run(ScenarioScript.read(SCRIPT));
        return runner.snapshots();
    }

    @Test
    @DisplayName("Should write the same status bytes after every step in two replays from empty stores")
    void shouldReplayToSameBytes() {
        var once = replay(ScenarioFixtures.PLAN_UNIT, first);
        var twice = replay(ScenarioFixtures.PLAN_UNIT, second);

        assertAll(
                () -> assertEquals(11, once.size()),
                () -> assertTrue(once.stream().allMatch(snapshot -> snapshot.status().length > 0)),
                () -> assertEquals(once, twice));
    }

    @Test
    @DisplayName("Should fail a changed transition at the first assertion after the step that takes it")
    void shouldFailChangedTransition() {
        var changedUnit = ScenarioFixtures.PLAN_UNIT.replace(TRANSITION, CHANGED_TRANSITION);
        var expected = replay(ScenarioFixtures.PLAN_UNIT, first);
        var runner = new ScenarioReplayRunner(ScenarioFixtures.parse("changed.pmwf", changedUnit), second);
        var script = ScenarioScript.read(SCRIPT);

        var failure = assertThrows(ScenarioFailure.class, () -> runner.run(script));

        var changed = runner.snapshots();
        assertAll(
                () -> assertTrue(changedUnit.contains(CHANGED_TRANSITION), "the unit changed"),
                () -> assertEquals(12, failure.line()),
                () -> assertEquals("line 12: assert_state expected 'completed' but the scope is in 'handed_back'",
                        failure.getMessage()),
                () -> assertEquals(List.of(3, 4, 5, 6, 7, 8, 9, 10, 11),
                        changed.stream().map(Snapshot::line).toList()),
                () -> assertEquals(expected.subList(0, 8), changed.subList(0, 8),
                        "every step before the changed transition writes the same bytes"),
                () -> assertFalse(Arrays.equals(expected.get(8).status(), changed.get(8).status()),
                        "expire_question takes the changed transition"));
    }
}
