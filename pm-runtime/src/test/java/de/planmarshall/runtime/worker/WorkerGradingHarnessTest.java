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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.qualification.Checkout;
import de.planmarshall.core.role.qualification.GradingHarness;
import de.planmarshall.core.role.qualification.TokenTotal;

/**
 * The grading adapter offers a corpus item as a task of the task protocol and runs it through the supervisor: one worker
 * per fresh item, one supervised worker for a warm form until the contamination rule or a changed directory closes it,
 * and a job-private detached worktree for an item of the project's own repository (Model Work § Grading; § Launch
 * Contract; ADR {@code core-role/004}).
 * <p>
 * The task protocol lives above this module, so the tests stand in for it with the offer port, which records the task and
 * answers a scripted submission; the workers are the landed supervisor over the existing worker fixtures.
 */
@DisplayName("Worker grading harness")
class WorkerGradingHarnessTest {

    private static final String FIRST = "review-code/first";
    private static final String SECOND = "review-code/second";
    private static final String THIRD = "review-code/third";

    @TempDir
    Path base;

    private final List<ItemTaskOffers.Task> tasks = new ArrayList<>();
    private SupervisorTestFixture fixture;
    private ItemTaskOffers offers;
    private Optional<ItemTaskOffers.Submitted> submission;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        submission = Optional.of(new ItemTaskOffers.Submitted("the answer", TokenTotal.measured(120), 40));
        offers = createMock(ItemTaskOffers.class);
        expect(offers.offer(anyObject(ItemTaskOffers.Task.class))).andAnswer(() -> {
            tasks.add((ItemTaskOffers.Task) getCurrentArguments()[0]);
            return submission;
        }).anyTimes();
        replay(offers);
    }

    @Test
    @DisplayName("a fresh form starts one worker per item and offers each as its own task")
    void freshStartsOneWorkerPerItem() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 4);
        var harness = harness(role, ItemWorktrees.none());

        var first = harness.answer(offered(item(FIRST, null), RoleForm.FRESH));
        var second = harness.answer(offered(item(SECOND, null), RoleForm.FRESH));

        assertAll(
                () -> assertTrue(first.isPresent(), "the first item answers"),
                () -> assertTrue(second.isPresent(), "the second item answers"),
                () -> assertEquals(2, fixture.starts.size(), "a fresh worker starts per item"),
                () -> assertEquals(2, tasks.size(), "each item is one task"),
                () -> assertEquals(tasks.get(0).generationId(), fixture.starts.get(0).launch().generationId(),
                        "the first task carries its own generation"),
                () -> assertEquals(tasks.get(1).generationId(), fixture.starts.get(1).launch().generationId(),
                        "the second task carries its own generation"),
                () -> assertNotEquals(tasks.get(0).generationId(), tasks.get(1).generationId(),
                        "two fresh items never share a worker"));
        verify(offers);
    }

    @Test
    @DisplayName("both forms of one role go through the one harness the engine holds")
    void bothFormsThroughOneHarness() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var harness = harness(role, ItemWorktrees.none());

        var fresh = harness.answer(offered(item(FIRST, null), RoleForm.FRESH));
        var warm = harness.answer(offered(item(SECOND, null), RoleForm.WARM));

        assertAll(
                () -> assertTrue(fresh.isPresent(), "the fresh item answers"),
                () -> assertTrue(warm.isPresent(), "the warm item answers through the same harness"),
                () -> assertEquals(2, fixture.starts.size(), "each form starts its worker"),
                () -> assertEquals(RoleForm.WARM, fixture.starts.get(1).launch().form(),
                        "the warm offer starts a warm worker"));
    }

    @Test
    @DisplayName("an unanswered fresh offer leaves the warm worker of the run alone")
    void anUnansweredFreshOfferDoesNotRotateTheWarmWorker() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 3);
        var harness = harness(role, ItemWorktrees.none());
        harness.answer(offered(item(FIRST, null), RoleForm.WARM));
        submission = Optional.empty();

        harness.answer(offered(item(SECOND, null), RoleForm.FRESH));

        assertAll(
                () -> assertEquals(2, fixture.starts.size(), "the fresh item starts its own worker"),
                () -> assertTrue(fixture.cancels.isEmpty(), "the fresh answer's absence cancels nothing"));
    }

    @Test
    @DisplayName("a warm form serves the items in order with one worker")
    void warmServesOneWorker() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var harness = harness(role, ItemWorktrees.none());

        harness.answer(offered(item(FIRST, null), RoleForm.WARM));
        harness.answer(offered(item(SECOND, null), RoleForm.WARM));
        harness.answer(offered(item(THIRD, null), RoleForm.WARM));

        assertAll(
                () -> assertEquals(1, fixture.starts.size(), "the warm form starts one worker"),
                () -> assertEquals(1, tasks.stream().map(ItemTaskOffers.Task::generationId).distinct().count(),
                        "every item is served by that worker"));
    }

    @Test
    @DisplayName("a contaminated item never shares the warm worker and is left ungraded in one directory")
    void contaminationBlocksTheSharedDirectory() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 2);
        var harness = harness(role, worktrees(base.resolve("one-worktree")));

        harness.answer(offered(item(FIRST, null), RoleForm.WARM, Checkout.ITEM_COMMIT));
        var second = harness.answer(offered(item(SECOND, "contaminated by " + FIRST), RoleForm.WARM,
                Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertTrue(second.isEmpty(), "the item may not be served by the worker it names"),
                () -> assertEquals(1, fixture.starts.size(), "one directory holds one warm worker"),
                () -> assertEquals(1, tasks.size(), "no task is offered to the worker it may not share"),
                () -> assertEquals(FIRST, tasks.getFirst().item().id(), "the one task is the uncontaminated item"));
    }

    @Test
    @DisplayName("a zero-tool role creates no item worktree for an origin commit")
    void aZeroToolRoleCreatesNoWorktree() {
        var role = fixture.role(RoleClass.ZERO_TOOL, RoleForm.FRESH, 1);
        ItemWorktrees worktrees = createMock(ItemWorktrees.class);
        replay(worktrees);
        var harness = harness(role, worktrees);

        harness.answer(offered(item(FIRST, null), RoleForm.FRESH, Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertEquals(1, tasks.size(), "the zero-tool item is offered in its own directory"),
                () -> assertNull(tasks.getFirst().workdir(), "a zero-tool task carries no worktree"));
        verify(worktrees);
    }

    @Test
    @DisplayName("a contaminated item starts its own worker in another directory")
    void contaminationStartsAnotherWorker() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 2);
        var first = base.resolve("first-worktree");
        var second = base.resolve("second-worktree");
        ItemWorktrees worktrees = createMock(ItemWorktrees.class);
        expect(worktrees.checkout(anyObject(URI.class), anyString()))
                .andReturn(Optional.of(first))
                .andReturn(Optional.of(second))
                .anyTimes();
        replay(worktrees);
        var harness = harness(role, worktrees);

        harness.answer(offered(item(FIRST, null), RoleForm.WARM, Checkout.ITEM_COMMIT));
        harness.answer(offered(item(SECOND, "contaminated by " + FIRST), RoleForm.WARM, Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertEquals(2, fixture.starts.size(), "the contaminated item starts its own worker"),
                () -> assertEquals(2, tasks.stream().map(ItemTaskOffers.Task::generationId).distinct().count(),
                        "the two items never share a generation"));
        verify(worktrees);
    }

    @Test
    @DisplayName("a warm role whose count is spent asks its worker to end and leaves the item ungraded")
    void contaminationRotatesTheOnlyWorker() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        var harness = harness(role, worktrees(base.resolve("one-worktree")));

        var first = harness.answer(offered(item(FIRST, "contaminated by " + SECOND), RoleForm.WARM,
                Checkout.ITEM_COMMIT));
        var second = harness.answer(offered(item(SECOND, null), RoleForm.WARM, Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertTrue(first.isPresent(), "the first item answers"),
                () -> assertTrue(second.isEmpty(), "the item with no admitted worker is left ungraded"),
                () -> assertEquals(1, fixture.starts.size(), "the spent count admits no second worker"),
                () -> assertEquals(1, fixture.cancels.size(), "the worker the run no longer needs is asked to end"));
    }

    @Test
    @DisplayName("an item without a contamination note keeps the warm worker")
    void sameDirectoryWithoutNoteKeepsTheWorker() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 2);
        var harness = harness(role, worktrees(base.resolve("one-worktree")));

        harness.answer(offered(item(FIRST, null), RoleForm.WARM, Checkout.ITEM_COMMIT));
        harness.answer(offered(item(SECOND, null), RoleForm.WARM, Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertEquals(1, fixture.starts.size(), "the same directory admits no second worker"),
                () -> assertEquals(1, tasks.stream().map(ItemTaskOffers.Task::generationId).distinct().count(),
                        "both items are served by the one worker"));
    }

    @Test
    @DisplayName("an item of the project's repository runs in the detached worktree of its origin commit")
    void itemCommitRunsInItsWorktree() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var worktree = base.resolve("item-commit");
        ItemWorktrees worktrees = createMock(ItemWorktrees.class);
        expect(worktrees.checkout(anyObject(URI.class), anyString())).andReturn(Optional.of(worktree)).anyTimes();
        replay(worktrees);
        var harness = harness(role, worktrees);

        harness.answer(offered(item(FIRST, null), RoleForm.FRESH, Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertEquals(worktree, fixture.starts.getFirst().launch().workingDirectory(),
                        "the worker is fixed to the origin commit's worktree"),
                () -> assertEquals(worktree, tasks.getFirst().workdir(),
                        "the task names the same worktree"));
        verify(worktrees);
    }

    @Test
    @DisplayName("an origin commit that cannot be materialised leaves the item ungraded")
    void unavailableWorktreeLeavesTheItemUngraded() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var harness = harness(role, ItemWorktrees.none());

        var answer = harness.answer(offered(item(FIRST, null), RoleForm.FRESH, Checkout.ITEM_COMMIT));

        assertAll(
                () -> assertTrue(answer.isEmpty(), "the item is ungraded, never run under a false record"),
                () -> assertTrue(fixture.starts.isEmpty(), "no worker starts for it"),
                () -> assertTrue(tasks.isEmpty(), "no task is offered for it"));
    }

    @Test
    @DisplayName("a declined start leaves the item ungraded and offers no task")
    void declinedStartLeavesTheItemUngraded() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        fixture.grant = false;
        var harness = harness(role, ItemWorktrees.none());

        var answer = harness.answer(offered(item(FIRST, null), RoleForm.FRESH));

        assertAll(
                () -> assertTrue(answer.isEmpty(), "a declined worker grades nothing"),
                () -> assertTrue(tasks.isEmpty(), "no task is offered without a worker"));
    }

    @Test
    @DisplayName("a zero-tool role runs each item in the job-private directory with the fixed prompt")
    void zeroToolRunsInThePrivateDirectory() {
        var role = fixture.role(RoleClass.ZERO_TOOL, RoleForm.FRESH, 1);
        var item = item(FIRST, null);
        var harness = harness(role, ItemWorktrees.none());

        harness.answer(offered(item, RoleForm.FRESH));

        assertAll(
                () -> assertTrue(fixture.starts.getFirst().launch().prompt().text().contains(item.input().question()),
                        "the item's server-fixed prompt is carried"),
                () -> assertEquals(base.resolve("run").resolve("jobs")
                                .resolve(fixture.starts.getFirst().launch().generationId()).resolve("cwd"),
                        fixture.starts.getFirst().launch().workingDirectory(),
                        "a zero-tool worker runs in its job-private directory"));
    }

    private ItemWorktrees worktrees(Path worktree) {
        ItemWorktrees worktrees = createMock(ItemWorktrees.class);
        expect(worktrees.checkout(anyObject(URI.class), anyString())).andReturn(Optional.of(worktree)).anyTimes();
        replay(worktrees);
        return worktrees;
    }

    private WorkerGradingHarness harness(EffectiveRole role, ItemWorktrees worktrees) {
        var supervisor = fixture.supervisor();
        return new WorkerGradingHarness(role, supervisor,
                new WorkerEnding(supervisor.environment(), () -> false, () -> {
                }), offers, worktrees, fixture.workdir);
    }

    private static GradingHarness.Offered offered(ImportedItem item, RoleForm form) {
        return offered(item, form, Checkout.CURRENT);
    }

    private static GradingHarness.Offered offered(ImportedItem item, RoleForm form, Checkout checkout) {
        return new GradingHarness.Offered(item, form, checkout, 1);
    }

    private static ImportedItem item(String id, String contamination) {
        var topic = id.substring(0, id.lastIndexOf('/'));
        var input = new ImportedItem.Input("question " + id, "", null,
                JsonValue.parse("{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8)));
        var target = new ImportedItem.Target("right", "", List.of(), List.of(), false);
        var grading = new ImportedItem.Grading(ImportedItem.GradingMethod.EXACT_CHOICE, null, null);
        var provenance = new ImportedItem.Provenance(LocalDate.ofEpochDay(20_000),
                new ImportedItem.Origin(URI.create("https://example.org/corpus"),
                        "%040x".formatted((long) id.hashCode() & 0xffffffffL), "rows.csv", 1),
                List.of("generated"));
        var review = new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED, "corpus", null, "");
        return new ImportedItem(id, topic, "review-code", "an item", List.of(), null, input, target, grading,
                provenance, review, contamination, "canary");
    }
}
