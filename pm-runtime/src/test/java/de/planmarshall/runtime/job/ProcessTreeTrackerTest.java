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

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.createStrictMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.job.ProcessTreeTracker.Member;
import de.planmarshall.runtime.job.ProcessTreeTracker.Survivor;

/**
 * The process tree of a job (termination step 3a): the descendants it recorded while the job ran are swept at its end,
 * a member that left the tree between two refreshes is still ended, a {@code SIGTERM} that does not end a member is
 * escalated to {@code SIGKILL} in that order, a PID whose process started at another instant was reused and is not
 * signalled, every signal goes through the member's own {@link ProcessHandle} (the tracker takes no numeric
 * primitive), and a signal that throws for one member does not keep the others from being signalled.
 * <p>
 * Contract literals that stay: the grace period with its poll interval and the {@code SIGTERM}-then-{@code SIGKILL}
 * order. The root PID, the member PIDs, the start instant and the reported command are generated; the handles are
 * EasyMock strict doubles whose {@code destroy()}, {@code destroyForcibly()} and {@code info()} the test answers in the
 * order the sweep must call them.
 */
@EnableGeneratorController
@DisplayName("Process tree of a job")
class ProcessTreeTrackerTest {

    private final Map<Long, Instant> starts = new ConcurrentHashMap<>();
    private final Set<Member> tree = ConcurrentHashMap.newKeySet();

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
                time, time);
    }

    /** Adds a member whose handle ends the process on {@code SIGTERM}. */
    private Member member(long pid) {
        ProcessHandle handle = createStrictMock(ProcessHandle.class);
        expect(handle.destroy()).andAnswer(() -> starts.remove(pid) != null);
        replay(handle);
        return member(pid, handle);
    }

    /** Adds a member whose handle the test answers; the member is in the tree and its start instant is recorded. */
    private Member member(long pid, ProcessHandle handle) {
        starts.put(root, start);
        var member = new Member(pid, start, handle);
        tree.add(member);
        starts.put(pid, start);
        return member;
    }

    /** @return a handle the sweep must call {@code destroy()}, then {@code destroyForcibly()}, then {@code info()} on */
    private static ProcessHandle stubbornHandle(String command) {
        ProcessHandle.Info info = createMock(ProcessHandle.Info.class);
        expect(info.command()).andReturn(Optional.of(command));
        ProcessHandle handle = createStrictMock(ProcessHandle.class);
        expect(handle.destroy()).andReturn(true);
        expect(handle.destroyForcibly()).andReturn(true);
        expect(handle.info()).andReturn(info);
        replay(info, handle);
        return handle;
    }

    /** @return a handle the sweep must not touch */
    private static ProcessHandle untouchedHandle() {
        ProcessHandle handle = createMock(ProcessHandle.class);
        replay(handle);
        return handle;
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

            assertEquals(Set.of(first, second), tracker.members(), "the refresh remembers both descendants");
        }

        @Test
        @DisplayName("a reused root PID records nothing new: the descendants below it are not the job's")
        void reusedRootRecordsNothing() {
            member(first);
            var tracker = tracker();
            tracker.refresh();
            starts.put(root, later);
            tree.add(new Member(second, start, untouchedHandle()));

            tracker.refresh();

            assertEquals(Set.of(first), tracker.members(), "no descendant of a reused root is added");
        }
    }

    @Nested
    @DisplayName("sweeps the remembered descendants")
    class Sweeps {

        @Test
        @DisplayName("a member that left the tree after a refresh is ended through its handle")
        void detachedMemberIsEnded() {
            var member = member(first);
            var tracker = tracker();
            tracker.refresh();
            tree.clear();

            var survivors = tracker.sweep();

            assertAll(
                    () -> assertDoesNotThrow(() -> verify(member.handle()), "the member is ended through its handle"),
                    () -> assertFalse(starts.containsKey(first), "the ended member is no longer tracked"),
                    () -> assertEquals(List.of(), survivors, "an ended member is no survivor"));
        }

        @Test
        @DisplayName("a member that does not end on SIGTERM is killed through its handle after the grace period")
        void escalated() {
            var command = "/" + Generators.letterStrings(3, 12).next();
            var member = member(first, stubbornHandle(command));
            var tracker = tracker();
            tracker.refresh();
            tree.clear();

            var survivors = tracker.sweep();

            assertAll(
                    () -> assertDoesNotThrow(() -> verify(member.handle()),
                            "SIGTERM before SIGKILL, both through the handle"),
                    () -> assertEquals(List.of(first), survivors.stream().map(Survivor::pid)
                            .toList(), "the stubborn member is a survivor"),
                    () -> assertEquals(command, survivors.getFirst().command(), "the survivor reports its command"),
                    () -> assertEquals(ProcessTreeTracker.SWEEP_GRACE.plus(ProcessTreeTracker.POLL_INTERVAL),
                            Duration.between(start, time.instant()), "the sweep waited the grace period"));
        }

        @Test
        @DisplayName("a member whose signal throws does not keep the other members from being ended")
        void throwingMemberDoesNotStopTheOthers() {
            var command = "/" + Generators.letterStrings(3, 12).next();
            var failureMessage = Generators.nonEmptyStrings().next();
            ProcessHandle.Info info = createMock(ProcessHandle.Info.class);
            expect(info.command()).andReturn(Optional.of(command));
            ProcessHandle throwing = createStrictMock(ProcessHandle.class);
            expect(throwing.destroy()).andThrow(new IllegalStateException(failureMessage));
            expect(throwing.destroyForcibly()).andThrow(new IllegalStateException(failureMessage));
            expect(throwing.info()).andReturn(info);
            replay(info, throwing);
            member(first, throwing);
            var ending = member(second);
            var tracker = tracker();
            tracker.refresh();

            var survivors = assertDoesNotThrow(tracker::sweep, "a throwing signal does not end the sweep");

            assertAll(
                    () -> assertDoesNotThrow(() -> verify(ending.handle()), "the other member is still signalled"),
                    () -> assertFalse(starts.containsKey(second), "the other member is ended"),
                    () -> assertDoesNotThrow(() -> verify(throwing), "the throwing member is escalated too"),
                    () -> assertEquals(List.of(first), survivors.stream().map(Survivor::pid)
                            .toList(), "the throwing member is reported a survivor"),
                    () -> assertEquals(command, survivors.getFirst().command(), "the survivor reports its command"));
        }

        @Test
        @DisplayName("a remembered PID whose process started at another instant was reused and is not signalled")
        void reusedPidIsNotSignalled() {
            ProcessHandle handle = untouchedHandle();
            member(first, handle);
            var tracker = tracker();
            tracker.refresh();
            starts.put(first, later);

            tracker.sweep();

            assertAll(
                    () -> assertDoesNotThrow(() -> verify(handle), "a reused PID is never signalled"),
                    () -> assertEquals(Set.of(first), tracker.members(), "the member stays recorded"));
        }

        @Test
        @DisplayName("a remembered member that no longer runs is not signalled")
        void deadMemberIsNotSignalled() {
            var handle = untouchedHandle();
            member(first, handle);
            var tracker = tracker();
            tracker.refresh();
            starts.remove(first);

            tracker.sweep();

            assertDoesNotThrow(() -> verify(handle), "a dead member is never signalled");
        }
    }

    @Nested
    @DisplayName("sends no numeric signal")

    class Signals {

        @Test
        @DisplayName("the tracker and every type declared in it depend on no group-signal primitive")
        void dependsOnNoGroupSignal() {
            var types = new ArrayList<Class<?>>();
            types.add(ProcessTreeTracker.class);
            types.addAll(List.of(ProcessTreeTracker.class.getDeclaredClasses()));
            var imported = new ClassFileImporter().importClasses(types);

            var sent = noClasses().that(areTheTrackerOrANestedType())
                    .should().dependOnClassesThat().haveFullyQualifiedName(ProcessGroupSignals.class.getName())
                    .evaluate(imported);
            var selected = imported.stream().filter(areTheTrackerOrANestedType()::test).toList();

            assertAll(
                    () -> assertEquals(types.size(), selected.size(),
                            "the subject selects every imported type"),
                    () -> assertFalse(sent.hasViolation(),
                            "no declared type of the tracker signals numerically"));
        }

        /**
         * The subject of the rule: the tracker and every type declared in it, so a nested record, interface or helper
         * that reached for the numeric primitive is a violation too. Both the enclosing name and the nested types come
         * from the class literal, so a renamed class is a compile error here and a newly nested type is imported by the
         * reflection over the declared classes, never by a name list that could go stale.
         *
         * @return the predicate selecting the tracker and its declared types
         */
        private static DescribedPredicate<JavaClass> areTheTrackerOrANestedType() {
            var enclosing = ProcessTreeTracker.class.getName();
            return new DescribedPredicate<>("the tracker and its nested types") {

                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getName().equals(enclosing) || javaClass.getName().startsWith(enclosing + "$");
                }
            };
        }
    }
}
