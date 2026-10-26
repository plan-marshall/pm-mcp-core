/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.core.store.WorkerState.DeliveredSkill;
import de.planmarshall.core.store.WorkerState.EndReason;
import de.planmarshall.core.store.WorkerState.Form;
import de.planmarshall.core.store.WorkerState.RoleClass;
import de.planmarshall.core.store.WorkerState.State;
import de.planmarshall.core.store.WorkerState.Workdir;
import de.planmarshall.core.store.WorkerState.WorkerStateBuilder;
import de.planmarshall.core.store.WorkerState.WorktreeMark;

/**
 * The worker block of a job record (PM-WORK-2, PM-WORK-3, PM-IMPL-2): every field and every value of the closed
 * enumerations round-trips byte for byte through the canonical writer, and every document outside the one shape of the
 * block is refused at its pointer. The combination rules of the state — a task without its lease, a state before the
 * first call, a terminal state without its end reason, the start-failure pair — are refused at construction.
 */
@DisplayName("Worker state codec")
class WorkerStateCodecTest {

    static final JobId GENERATION = new JobId("l-00000000000000ff");
    static final Instant CONNECTED_AT = Instant.parse("2026-10-20T10:00:00Z");

    private static final Path FILE = Path.of("/pm-base/projects/sample-project/jobs/l-00000000000000ff.json");
    private static final String DIGEST = "ab".repeat(32);

    /** The builder of a worker that connected and waits for its first offer, every optional member absent. */
    static WorkerStateBuilder base() {
        return WorkerState.builder().role("implementer").configurationDigest(DIGEST).roleClass(RoleClass.WRITER)
                .form(Form.FRESH).harness("claude").harnessVersion("2.1.0").model("claude-sonnet-4-6")
                .generationId(GENERATION).workdir(new Workdir(Workdir.Kind.PLAN_WORKTREE, "plan-one"))
                .promptDigest(DIGEST).jobTokenSha256(DIGEST).connectedAt(CONNECTED_AT).state(State.WAITING)
                .stateAt(CONNECTED_AT.plusSeconds(1)).servedTasks(List.of()).idleWakeups(0)
                .contextTokens(ContextTokens.NOT_MEASURED).deliveredSkills(List.of()).startFailure(false);
    }

    /** The worker that connected and waits for its first offer. */
    static WorkerState waiting() {
        return base().build();
    }

    /** The worker with every optional member present, at its terminal state. */
    static WorkerState full() {
        return base().effort("high").effortNarrowedFrom("xhigh").state(State.ENDED)
                .stateAt(CONNECTED_AT.plusSeconds(30))
                .servedTasks(List.of("qt-0000000000000001", "qt-0000000000000002"))
                .worktreeMark(new WorktreeMark("0123456789abcdef0123456789abcdef01234567",
                        List.of("pm-core/src/main/java/de/planmarshall/core/store/WorkerState.java"),
                        CONNECTED_AT.plusSeconds(20)))
                .idleWakeups(2).contextTokens(ContextTokens.measured(48_000)).endReason(EndReason.TASK_DONE)
                .deliveredSkills(List.of(new DeliveredSkill("skill://pm/plan-marshall:ref-code-quality", DIGEST,
                        CONNECTED_AT.plusSeconds(2))))
                .build();
    }

    /** A worker that exited before its first call. */
    static WorkerState startFailed() {
        return base().connectedAt(null).jobTokenSha256(null).state(State.ENDED)
                .stateAt(CONNECTED_AT.plusSeconds(30)).endReason(EndReason.START_FAILURE).startFailure(true)
                .harnessError("You have hit your usage limit").build();
    }

    /** One state per value of every closed enumeration, so the round trip covers each of them. */
    static List<Arguments> variants() {
        var variants = new ArrayList<Arguments>();
        variants.add(Arguments.of("waiting", waiting()));
        variants.add(Arguments.of("full", full()));
        variants.add(Arguments.of("start failure", startFailed()));
        for (var state : State.values()) {
            variants.add(Arguments.of("state " + state.wireName(), inState(state)));
        }
        for (var reason : EndReason.values()) {
            variants.add(Arguments.of("end reason " + reason.wireName(), inEndReason(reason)));
        }
        for (var roleClass : RoleClass.values()) {
            variants.add(Arguments.of("class " + roleClass.wireName(), inClass(roleClass)));
        }
        for (var form : Form.values()) {
            variants.add(Arguments.of("form " + form.wireName(), base().form(form).build()));
        }
        for (var kind : Workdir.Kind.values()) {
            variants.add(Arguments.of("workdir " + kind.wireName(), base()
                    .workdir(new Workdir(kind, kind == Workdir.Kind.PLAN_WORKTREE ? "plan-one" : null)).build()));
        }
        return variants;
    }

    private static WorkerState inState(State state) {
        var builder = base().state(state).stateAt(CONNECTED_AT.plusSeconds(2));
        if (state.isTerminal()) {
            builder.endReason(state == State.LOST ? EndReason.SILENCE : EndReason.TASK_DONE);
        } else if (state != State.WAITING) {
            builder.task("qt-0000000000000003");
        }
        return builder.build();
    }

    private static WorkerState inEndReason(EndReason reason) {
        var builder = base().state(State.ENDED).stateAt(CONNECTED_AT.plusSeconds(30)).endReason(reason);
        if (reason == EndReason.START_FAILURE) {
            builder.connectedAt(null).jobTokenSha256(null).startFailure(true).harnessError("account limit");
        }
        return builder.build();
    }

    private static WorkerState inClass(RoleClass roleClass) {
        var builder = base().roleClass(roleClass);
        if (roleClass == RoleClass.ZERO_TOOL) {
            builder.jobTokenSha256(null);
        }
        return builder.build();
    }

    private static String replace(String document, String fragment, String replacement) {
        assertTrue(document.contains(fragment), "the fixture must contain " + fragment);
        return document.replace(fragment, replacement);
    }

    private static StoreRefusal refusalOf(String document) {
        return assertThrows(StoreRefusal.class, () -> WorkerStateCodec.read(FILE, document));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.WorkerStateCodecTest#variants")
        @DisplayName("a worker state is read back equal and written again byte for byte")
        void roundTrip(String name, WorkerState state) {
            var text = WorkerStateCodec.write(state);

            var read = WorkerStateCodec.read(FILE, text);

            assertAll(
                    () -> assertEquals(state, read),
                    () -> assertEquals(text, WorkerStateCodec.write(read)));
        }

        @Test
        @DisplayName("the members are written through the canonical writer, sorted by their names")
        void canonicalOrder() {
            var text = WorkerStateCodec.write(full());
            var positions = WorkerStateCodec.MEMBERS.stream().sorted()
                    .map(member -> text.indexOf("\"" + member + "\":")).toList();

            assertAll(
                    () -> assertTrue(positions.stream().allMatch(position -> position >= 0), positions.toString()),
                    () -> assertEquals(positions.stream().sorted().toList(), positions),
                    () -> assertTrue(text.startsWith("{") && text.endsWith("}"), text),
                    () -> assertTrue(!text.contains("\n") && !text.contains(": "), text));
        }

        @Test
        @DisplayName("the optional effort_narrowed_from may be omitted, every other member may not")
        void optionalMember() {
            var document = replace(WorkerStateCodec.write(waiting()), ",\"effort_narrowed_from\":null", "");

            var read = WorkerStateCodec.read(FILE, document);

            assertAll(
                    () -> assertNull(read.effortNarrowedFrom()),
                    () -> assertEquals(waiting(), read));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("an unknown member is refused at its pointer")
        void unknownMember() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()), "\"role\":", "\"extra\":1,\"role\":"));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(FILE, refusal.file()),
                    () -> assertTrue(refusal.getMessage().contains("'extra'"), refusal.getMessage()));
        }

        @Test
        @DisplayName("a missing required member is refused at its pointer")
        void missingMember() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()), ",\"harness_error\":null", ""));

            assertTrue(refusal.getMessage().contains("'harness_error'"), refusal.getMessage());
        }

        @Test
        @DisplayName("a member twice is refused")
        void memberTwice() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()), "\"role\":\"implementer\",",
                    "\"role\":\"implementer\",\"role\":\"implementer\","));

            assertTrue(refusal.getMessage().contains("'role' twice"), refusal.getMessage());
        }

        @Test
        @DisplayName("an undeclared value of a closed enumeration is refused at its pointer")
        void undeclaredValue() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()), "\"class\":\"writer\"",
                    "\"class\":\"owner\""));

            assertTrue(refusal.getMessage().contains("'class'"), refusal.getMessage());
        }

        @Test
        @DisplayName("a negative context size is refused at its pointer")
        void negativeContext() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()), "\"context_tokens\":\"unmeasured\"",
                    "\"context_tokens\":-1"));

            assertTrue(refusal.getMessage().contains("context_tokens"), refusal.getMessage());
        }

        @Test
        @DisplayName("a working directory of another kind with a plan id is refused")
        void workdirWithPlanId() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()), "\"kind\":\"plan_worktree\"",
                    "\"kind\":\"project_checkout\""));

            assertTrue(refusal.getMessage().contains("project_checkout"), refusal.getMessage());
        }

        @Test
        @DisplayName("a generation that is not a worker's job id is refused")
        void generationOfAnotherKind() {
            var refusal = refusalOf(replace(WorkerStateCodec.write(waiting()),
                    "\"generation_id\":\"l-00000000000000ff\"", "\"generation_id\":\"x-00000000000000ff\""));

            var foreignGeneration = base().generationId(new JobId("b-00000000000000ff"));

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, foreignGeneration::build),
                    () -> assertTrue(refusal.getMessage().contains("generation_id"), refusal.getMessage()));
        }
    }

    @Nested
    @DisplayName("combination rules")
    class Combinations {

        @Test
        @DisplayName("a terminal state without an end reason, and an end reason before the end, are refused")
        void terminalState() {
            var terminalWithoutReason = base().state(State.ENDED);
            var reasonBeforeEnd = base().state(State.WAITING).endReason(EndReason.TASK_DONE);

            assertAll(
                    () -> assertTrue(assertThrows(IllegalArgumentException.class, terminalWithoutReason::build)
                            .getMessage().contains("end_reason")),
                    () -> assertTrue(assertThrows(IllegalArgumentException.class, reasonBeforeEnd::build)
                            .getMessage().contains("end_reason")));
        }

        @Test
        @DisplayName("a task in a state that holds none is refused")
        void taskWithoutLease() {
            var stateWithoutLease = base().task("qt-0000000000000003");

            var failure = assertThrows(IllegalArgumentException.class, stateWithoutLease::build);

            assertTrue(failure.getMessage().contains("leased"), failure.getMessage());
        }

        @Test
        @DisplayName("a state timestamp before the first call is refused")
        void beforeConnection() {
            var stateBeforeConnection = base().stateAt(CONNECTED_AT.minusSeconds(1));

            var failure = assertThrows(IllegalArgumentException.class, stateBeforeConnection::build);

            assertTrue(failure.getMessage().contains("state_at"), failure.getMessage());
        }

        @Test
        @DisplayName("the start failure and its error text are one pair")
        void startFailurePair() {
            var errorWithoutFailure = base().harnessError("boom");
            var failureWithoutError = base().state(State.ENDED).endReason(EndReason.START_FAILURE)
                    .startFailure(true);

            assertAll(
                    () -> assertTrue(assertThrows(IllegalArgumentException.class, errorWithoutFailure::build)
                            .getMessage().contains("harness_error")),
                    () -> assertTrue(assertThrows(IllegalArgumentException.class, failureWithoutError::build)
                            .getMessage().contains("first call")));
        }

        @Test
        @DisplayName("a narrowed-from effort without the narrowed effort is refused")
        void narrowWithoutEffort() {
            var narrowedWithoutEffort = base().effortNarrowedFrom("xhigh");

            assertThrows(IllegalArgumentException.class, narrowedWithoutEffort::build);
        }

        @Test
        @DisplayName("a zero-tool worker holds no job token")
        void zeroToolToken() {
            var zeroToolWithoutToken = base().roleClass(RoleClass.ZERO_TOOL);

            assertThrows(IllegalArgumentException.class, zeroToolWithoutToken::build);
        }
    }
}
