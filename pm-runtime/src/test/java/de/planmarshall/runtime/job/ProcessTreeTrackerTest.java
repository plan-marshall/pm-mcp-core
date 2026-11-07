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
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;
import de.planmarshall.runtime.job.ProcessTreeTracker.Member;

/**
 * The process tree of a job (termination step 3a): the descendants it recorded while the job ran are swept at its end,
 * a member that left the tree between two refreshes is still ended, a {@code SIGTERM} that does not end a member is
 * escalated to {@code SIGKILL}, and a PID whose process started at another instant was reused and is not signalled.
 * <p>
 * Contract literals that stay: the signals and the grace period with its poll interval. The root PID, the member PIDs,
 * the start instant and the later instant of a reused PID are generated.
 */
@EnableGeneratorController
@DisplayName("Process tree of a job")
class ProcessTreeTrackerTest {

    private final Map<Long, Instant> starts = new ConcurrentHashMap<>();
    private final Set<Member> tree = ConcurrentHashMap.newKeySet();
    private final RecordingSignals signals = new RecordingSignals();

    private long root;
    private long first;
    private long second;
    private Instant start;
    private Instant later;
    private FakeTime time;

    @BeforeEach
    void drawTree() {
        root = Generators.longs(2, 1_000_000).next();
        first = root + Generators.longs(1, 1_000).next();
        second = first + Generators.longs(1, 1_000).next();
        start = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
        later = start.plus(Duration.ofSeconds(Generators.longs(1, 86_400).next()));
        time = new FakeTime(start);
    }

    private record Call(long pid, Signal signal) {
    }

    private final class RecordingSignals implements ProcessGroupSignals {

        private final List<Call> calls = new CopyOnWriteArrayList<>();

        @Override
        public Answer signalGroup(long pgid, Signal signal) {
            throw new AssertionError("the sweep signals processes, not groups");
        }

        @Override
        public Answer signalProcess(long pid, Signal signal) {
            calls.add(new Call(pid, signal));
            if (signal == Signal.SIGTERM) {
                starts.remove(pid);
            }
            return new Delivered();
        }
    }

    private static final class FakeTime implements InstantSource, TerminationProtocol.Pause {

        private Instant now;

        FakeTime(Instant start) {
            now = start;
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public void sleep(Duration duration) {
            now = now.plus(duration);
        }
    }

    private ProcessTreeTracker tracker() {
        return new ProcessTreeTracker(root, ignoredRoot -> Set.copyOf(tree), pid -> Optional.ofNullable(starts.get(pid)),
                signals, time, time);
    }

    private void member(long pid) {
        starts.put(root, start);
        tree.add(new Member(pid, start));
        starts.put(pid, start);
    }

    @Nested
    @DisplayName("records the descendants")
    class Records {

        @Test
        @DisplayName("a refresh remembers every descendant the tree answers")
        void refresh() {
            member(first);
            member(second);

            var tracker = tracker();
            tracker.refresh();

            assertAll(
                    () -> assertEquals(Set.of(first, second), tracker.members(),
                            "the refresh remembers both descendants"),
                    () -> assertEquals(List.of(), signals.calls, "a refresh signals nothing"));
        }

        @Test
        @DisplayName("a reused root PID records nothing new: the descendants below it are not the job's")
        void reusedRootRecordsNothing() {
            member(first);
            var tracker = tracker();
            tracker.refresh();
            starts.put(root, later);
            tree.add(new Member(second, start));

            tracker.refresh();

            assertAll(
                    () -> assertEquals(Set.of(first), tracker.members(), "no descendant of a reused root is added"),
                    () -> assertEquals(List.of(), signals.calls, "a refresh of a reused root signals nothing"));
        }
    }

    @Nested
    @DisplayName("sweeps the remembered descendants")
    class Sweeps {

        @Test
        @DisplayName("a member that left the tree after a refresh is ended by SIGTERM and is no survivor")
        void detachedMemberIsEnded() {
            member(first);
            var tracker = tracker();
            tracker.refresh();
            tree.clear();

            var survivors = tracker.sweep();

            assertAll(
                    () -> assertEquals(List.of(new Call(first, Signal.SIGTERM)), signals.calls,
                            "the detached member is sent SIGTERM"),
                    () -> assertFalse(starts.containsKey(first), "the ended member is no longer tracked"),
                    () -> assertEquals(List.of(), survivors, "an ended member is no survivor"));
        }

        @Test
        @DisplayName("a member that does not end on SIGTERM is sent SIGKILL after the grace period")
        void escalated() {
            member(first);
            var surviving = new ProcessGroupSignals() {

                @Override
                public Answer signalGroup(long pgid, Signal signal) {
                    throw new AssertionError("the sweep signals processes, not groups");
                }

                @Override
                public Answer signalProcess(long pid, Signal signal) {
                    signals.calls.add(new Call(pid, signal));
                    return new Delivered();
                }
            };
            var tracker = new ProcessTreeTracker(root, ignoredRoot -> Set.copyOf(tree),
                    pid -> Optional.ofNullable(starts.get(pid)), surviving, time, time);
            tracker.refresh();
            tree.clear();

            var survivors = tracker.sweep();

            assertAll(
                    () -> assertEquals(List.of(new Call(first, Signal.SIGTERM), new Call(first, Signal.SIGKILL)),
                            signals.calls, "SIGTERM is escalated to SIGKILL"),
                    () -> assertEquals(List.of(first), survivors.stream().map(ProcessTreeTracker.Survivor::pid)
                            .toList(), "the stubborn member is a survivor"),
                    () -> assertEquals(ProcessTreeTracker.SWEEP_GRACE.plus(ProcessTreeTracker.POLL_INTERVAL),
                            Duration.between(start, time.instant()), "the sweep waited the grace period"));
        }

        @Test
        @DisplayName("a remembered PID whose process started at another instant was reused and is not signalled")
        void reusedPidIsNotSignalled() {
            member(first);
            var tracker = tracker();
            tracker.refresh();
            starts.put(first, later);

            tracker.sweep();

            assertAll(
                    () -> assertEquals(List.of(), signals.calls, "a reused PID is not signalled"),
                    () -> assertEquals(Set.of(first), tracker.members(), "the member stays recorded"));
        }

        @Test
        @DisplayName("a remembered member that no longer runs is not signalled")
        void deadMemberIsNotSignalled() {
            member(first);
            var tracker = tracker();
            tracker.refresh();
            starts.remove(first);

            tracker.sweep();

            assertTrue(signals.calls.isEmpty(), "a dead member is not signalled");
        }
    }
}
