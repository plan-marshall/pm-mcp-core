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

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;
import de.planmarshall.runtime.job.ProcessTreeTracker.Member;

/**
 * The process tree of a job (termination step 3a): the descendants it recorded while the job ran are swept at its end,
 * a member that left the tree between two refreshes is still ended, a {@code SIGTERM} that does not end a member is
 * escalated to {@code SIGKILL}, and a PID whose process started at another instant was reused and is not signalled.
 */
@DisplayName("Process tree of a job")
class ProcessTreeTrackerTest {

    private static final long ROOT = 4242;
    private static final Instant START = Instant.parse("2026-10-21T10:00:00Z");

    private final Map<Long, Instant> starts = new ConcurrentHashMap<>();
    private final Set<Member> tree = ConcurrentHashMap.newKeySet();
    private final RecordingSignals signals = new RecordingSignals();
    private final FakeTime time = new FakeTime();

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

        private Instant now = START;

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
        return new ProcessTreeTracker(ROOT, root -> Set.copyOf(tree), pid -> Optional.ofNullable(starts.get(pid)), signals,
                time, time);
    }

    private void member(long pid) {
        starts.put(ROOT, START);
        tree.add(new Member(pid, START));
        starts.put(pid, START);
    }

    @Nested
    @DisplayName("records the descendants")
    class Records {

        @Test
        @DisplayName("a refresh remembers every descendant the tree answers")
        void refresh() {
            member(11);
            member(12);

            var tracker = tracker();
            tracker.refresh();

            assertAll(
                    () -> assertEquals(Set.of(11L, 12L), tracker.members()),
                    () -> assertEquals(List.of(), signals.calls, "a refresh signals nothing"));
        }

        @Test
        @DisplayName("a reused root PID records nothing new: the descendants below it are not the job's")
        void reusedRootRecordsNothing() {
            member(11);
            var tracker = tracker();
            tracker.refresh();
            starts.put(ROOT, START.plus(Duration.ofHours(1)));
            tree.add(new Member(12, START));

            tracker.refresh();

            assertAll(
                    () -> assertEquals(Set.of(11L), tracker.members()),
                    () -> assertEquals(List.of(), signals.calls));
        }
    }

    @Nested
    @DisplayName("sweeps the remembered descendants")
    class Sweeps {

        @Test
        @DisplayName("a member that left the tree after a refresh is ended by SIGTERM and is no survivor")
        void detachedMemberIsEnded() {
            member(11);
            var tracker = tracker();
            tracker.refresh();
            tree.clear();

            var survivors = tracker.sweep();

            assertAll(
                    () -> assertEquals(List.of(new Call(11, Signal.SIGTERM)), signals.calls),
                    () -> assertFalse(starts.containsKey(11L), "the member ended"), () -> assertEquals(List.of(), survivors));
        }

        @Test
        @DisplayName("a member that does not end on SIGTERM is sent SIGKILL after the grace period")
        void escalated() {
            member(11);
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
            var tracker = new ProcessTreeTracker(ROOT, root -> Set.copyOf(tree),
                    pid -> Optional.ofNullable(starts.get(pid)), surviving, time, time);
            tracker.refresh();
            tree.clear();

            var survivors = tracker.sweep();

            assertAll(
                    () -> assertEquals(List.of(new Call(11, Signal.SIGTERM), new Call(11, Signal.SIGKILL)),
                            signals.calls),
                    () -> assertEquals(List.of(11L), survivors.stream().map(ProcessTreeTracker.Survivor::pid).toList()),
                    () -> assertEquals(ProcessTreeTracker.SWEEP_GRACE.plus(ProcessTreeTracker.POLL_INTERVAL),
                            Duration.between(START, time.instant())));
        }

        @Test
        @DisplayName("a remembered PID whose process started at another instant was reused and is not signalled")
        void reusedPidIsNotSignalled() {
            member(11);
            var tracker = tracker();
            tracker.refresh();
            starts.put(11L, START.plus(Duration.ofHours(1)));

            tracker.sweep();

            assertAll(
                    () -> assertEquals(List.of(), signals.calls),
                    () -> assertEquals(Set.of(11L), tracker.members(), "the member stays recorded"));
        }

        @Test
        @DisplayName("a remembered member that no longer runs is not signalled")
        void deadMemberIsNotSignalled() {
            member(11);
            var tracker = tracker();
            tracker.refresh();
            starts.remove(11L);

            tracker.sweep();

            assertTrue(signals.calls.isEmpty(), signals.calls::toString);
        }
    }
}
