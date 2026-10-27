/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;

/**
 * Each off-track signal holds at its threshold and not below it, a lowered threshold of a role artifact applies and a
 * raised one is refused (Model Work § A Worker Off Track).
 */
@DisplayName("Off-track detector")
class OffTrackDetectorTest {

    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    private final OffTrackDetector detector = new OffTrackDetector();

    static RoleArtifact role(Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack) {
        return new RoleArtifact(1, "implementer", "the implementer", RoleClass.WRITER,
                List.of("read_file", "pm_state"), "opencode", "model", new EffortValue("high"), RoleForm.FRESH, 1,
                List.of("skill://pm/implementation/SKILL.md"), null, 60, null, null, offTrack);
    }

    static ActivityEntry toolCall(int seq, String tool, String target) {
        return ActivityEntry.builder().seq(seq).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_CALL).tool(new ActivityEntry.Tool(tool, target)).at(AT).build();
    }

    static ActivityEntry turn(int seq, long totalTokens) {
        return ActivityEntry.builder().seq(seq).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TURN)
                .usage("{\"total_tokens\":" + totalTokens + "}")
                .at(AT).build();
    }

    static ActivityEntry link(int seq) {
        return ActivityEntry.builder().seq(seq).attempt(1).source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.LINK)
                .link(new ActivityEntry.Link(ActivityEntry.LinkRelation.SUBMIT, "TASK-001"))
                .at(AT).build();
    }

    private List<ActivitySignal> detect(List<ActivityEntry> entries, RoleArtifact role) {
        return detector.detect(1, entries, role, Set.of(), null, -1);
    }

    @Test
    @DisplayName("repeating holds at its lowered threshold and not below it")
    void repeating() {
        var role = role(Map.of(RoleArtifact.OffTrackSignal.REPEATING,
                new RoleArtifact.OffTrackSetting(3.0, RoleArtifact.OffTrackAction.NUDGE)));
        var three = List.of(toolCall(1, "read_file", "A.java"), toolCall(2, "read_file", "A.java"),
                toolCall(3, "read_file", "A.java"));
        var two = three.subList(0, 2);

        assertAll(
                () -> assertEquals(1, detect(three, role).size()),
                () -> assertEquals(RoleArtifact.OffTrackSignal.REPEATING, detect(three, role).getFirst().signal()),
                () -> assertEquals(RoleArtifact.OffTrackAction.NUDGE, detect(three, role).getFirst().action()),
                () -> assertEquals(3.0, detect(three, role).getFirst().measurement()),
                () -> assertTrue(detect(two, role).isEmpty()));
    }

    @Test
    @DisplayName("a write of the target or a failed result breaks a repeating run")
    void repeatingBreaks() {
        var role = role(Map.of(RoleArtifact.OffTrackSignal.REPEATING,
                new RoleArtifact.OffTrackSetting(2.0, RoleArtifact.OffTrackAction.SHOW)));
        var written = new ArrayList<>(List.of(toolCall(1, "read_file", "A.java")));
        written.add(ActivityEntry.builder().seq(2).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.FILE_CHANGED).path("A.java").at(AT).build());
        written.add(toolCall(3, "read_file", "A.java"));

        var failed = new ArrayList<>(List.of(toolCall(1, "read_file", "A.java")));
        failed.add(ActivityEntry.builder().seq(2).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_RESULT).result(new ActivityEntry.Result(10, true)).at(AT).build());
        failed.add(toolCall(3, "read_file", "A.java"));

        assertAll(
                () -> assertTrue(detect(written, role).isEmpty()),
                () -> assertTrue(detect(failed, role).isEmpty()));
    }

    @Test
    @DisplayName("no_progress counts the turns since the last link of the task")
    void noProgress() {
        var role = role(Map.of(RoleArtifact.OffTrackSignal.NO_PROGRESS,
                new RoleArtifact.OffTrackSetting(5.0, RoleArtifact.OffTrackAction.SHOW)));
        var five = List.of(turn(1, 10), turn(2, 10), turn(3, 10), turn(4, 10), turn(5, 10));
        var reset = List.of(turn(1, 10), turn(2, 10), link(3), turn(4, 10), turn(5, 10), turn(6, 10),
                turn(7, 10));

        assertAll(
                () -> assertEquals(5.0, detect(five, role).getFirst().measurement()),
                () -> assertTrue(detect(reset, role).isEmpty()));
    }

    @Test
    @DisplayName("over_size compares the attempt's tokens with the predicted cost")
    void overSize() {
        var role = role(Map.of(RoleArtifact.OffTrackSignal.OVER_SIZE,
                new RoleArtifact.OffTrackSetting(2.0, RoleArtifact.OffTrackAction.RESTART)));
        var entries = List.of(turn(1, 60), turn(2, 60));

        var holding = detector.detect(1, entries, role, Set.of(), 50.0, -1);
        var under = detector.detect(1, entries, role, Set.of(), 100.0, -1);

        assertAll(
                () -> assertEquals(2.4, holding.getFirst().measurement()),
                () -> assertEquals(RoleArtifact.OffTrackAction.RESTART, holding.getFirst().action()),
                () -> assertTrue(under.isEmpty()));
    }

    @Test
    @DisplayName("over_size is not evaluated without a predicted cost")
    void overSizeUnmeasured() {
        var role = role(Map.of());
        var entries = List.of(turn(1, 60));

        assertTrue(detector.detect(1, entries, role, Set.of(), null, -1).isEmpty());
        assertTrue(detector.detect(1, entries, role, Set.of(), 0.0, -1).isEmpty());
    }

    @Test
    @DisplayName("outside_footprint holds at its threshold and not below it")
    void outsideFootprint() {
        var role = role(Map.of(RoleArtifact.OffTrackSignal.OUTSIDE_FOOTPRINT,
                new RoleArtifact.OffTrackSetting(2.0, RoleArtifact.OffTrackAction.SHOW)));

        assertAll(
                () -> assertEquals(2.0, detector.detect(1, List.of(turn(1, 1)), role, Set.of(), null, 2)
                        .getFirst().measurement()),
                () -> assertTrue(detector.detect(1, List.of(turn(1, 1)), role, Set.of(), null, 1).isEmpty()),
                () -> assertTrue(detector.detect(1, List.of(turn(1, 1)), role, Set.of(), null, -1).isEmpty()));
    }

    @Test
    @DisplayName("a signal already raised on the attempt is not raised again")
    void oncePerAttempt() {
        var role = role(Map.of(RoleArtifact.OffTrackSignal.NO_PROGRESS,
                new RoleArtifact.OffTrackSetting(5.0, RoleArtifact.OffTrackAction.SHOW)));
        var entries = List.of(turn(1, 10), turn(2, 10), turn(3, 10), turn(4, 10), turn(5, 10));

        assertTrue(detector.detect(1, entries, role, Set.of(RoleArtifact.OffTrackSignal.NO_PROGRESS), null, -1)
                .isEmpty());
        assertTrue(detector.detect(2, entries, role, Set.of(), null, -1).isEmpty());
    }

    @Test
    @DisplayName("the default action is show and the compiled threshold applies without a setting")
    void defaults() {
        var role = role(Map.of());
        var entries = new ArrayList<ActivityEntry>();
        for (var index = 1; index <= RoleArtifact.OffTrackSignal.NO_PROGRESS.compiledThreshold(); index++) {
            entries.add(turn(index, 1));
        }

        var signals = detector.detect(1, entries, role, Set.of(), null, -1);

        assertAll(
                () -> assertEquals(RoleArtifact.OffTrackAction.SHOW, signals.getFirst().action()),
                () -> assertEquals(RoleArtifact.OffTrackSignal.NO_PROGRESS.compiledThreshold(),
                        signals.getFirst().threshold()));
    }

    @Test
    @DisplayName("a raised threshold of a role artifact is refused")
    void raisedThresholdRefused() {
        var raised = new RoleArtifact.OffTrackSetting(
                RoleArtifact.OffTrackSignal.NO_PROGRESS.compiledThreshold() + 1,
                RoleArtifact.OffTrackAction.SHOW);

        assertThrows(IllegalArgumentException.class,
                () -> raised.effectiveThreshold(RoleArtifact.OffTrackSignal.NO_PROGRESS));
    }
}
