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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.Checkout;
import de.planmarshall.core.role.qualification.GradingHarness;
import de.planmarshall.runtime.launch.ContractEnvelope;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.launch.LinkParameter;
import de.planmarshall.runtime.launch.LinkSchema;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The grading adapter: it offers a corpus item to the configuration under test as a task of the task protocol, runs it in
 * the granted checkout through the supervisor, and hands the submitted answer back to the grading engine (Model Work
 * § Grading; § Launch Contract; ADR {@code core-role/004}).
 * <p>
 * A fresh form starts one supervisor worker per item. A warm form keeps one supervised worker for the items it serves in
 * order, and the contamination rule ends that worker's service: an item whose {@code contamination} note names an item the
 * worker already served — or one whose note names the candidate — is never offered to it, and neither is one whose working
 * directory differs, because a warm worker is fixed to one directory. Such an item gets a worker of its own where the
 * role's {@code count} still admits one; where it does not, the worker this run no longer needs is asked to end and the
 * item is left ungraded, never offered to the worker it may not share. The workspace of an item of the project's own
 * repository is a job-private detached worktree at its origin commit; a commit the {@link ItemWorktrees} port cannot
 * materialise leaves the item ungraded instead of running it in the checkout under a record that claims otherwise.
 * <p>
 * The adapter carries no queue: {@link ItemTaskOffers} offers the task to the task protocol, which serves it to the
 * generation the adapter started.
 *
 * @since 0.1
 */
public final class WorkerGradingHarness implements GradingHarness {

    private final EffectiveRole role;
    private final WorkerSupervisor supervisor;
    private final WorkerEnding ending;
    private final ItemTaskOffers offers;
    private final ItemWorktrees worktrees;
    private final Path projectCheckout;
    private final List<ImportedItem> warmServed = new ArrayList<>();
    private @Nullable WarmWorker warm;

    /**
     * @param role            the graded configuration
     * @param supervisor      the supervisor that starts its workers
     * @param ending          the one end path a rotated warm worker leaves through
     * @param offers          the port that offers an item's task to the task protocol
     * @param worktrees       the port that materialises an item's origin commit
     * @param projectCheckout the enrolled project checkout
     * @throws NullPointerException if an argument is {@code null}
     */
    public WorkerGradingHarness(EffectiveRole role, WorkerSupervisor supervisor, WorkerEnding ending,
            ItemTaskOffers offers, ItemWorktrees worktrees, Path projectCheckout) {
        this.role = Objects.requireNonNull(role, "role");
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.ending = Objects.requireNonNull(ending, "ending");
        this.offers = Objects.requireNonNull(offers, "offers");
        this.worktrees = Objects.requireNonNull(worktrees, "worktrees");
        this.projectCheckout = Objects.requireNonNull(projectCheckout, "projectCheckout");
    }

    @Override
    public Optional<GradedAnswer> answer(Offered offered) {
        Objects.requireNonNull(offered, "offered");
        var directory = directory(offered.checkout(), offered.item());
        if (directory.isEmpty()) {
            return Optional.empty();
        }
        var generation = offered.form() == RoleForm.FRESH
                ? startFresh(offered.item(), directory.get())
                : startWarm(offered.item(), directory.get());
        if (generation == null) {
            return Optional.empty();
        }
        var answer = offers.offer(new ItemTaskOffers.Task(role.artifact().name(), generation, offered.item(),
                offered.form(), offered.checkout(), role.zeroTool() ? null : directory.get()));
        if (answer.isEmpty() && offered.form() == RoleForm.WARM) {
            // The warm worker that served no answer is no witness for the next item: it is asked to end.
            rotate();
        }
        return answer.map(WorkerGradingHarness::answerOf);
    }

    /**
     * @param checkout the place the engine granted the item
     * @param item     the item
     * @return the directory the worker runs in; empty when a granted origin-commit worktree cannot be materialised
     */
    private Optional<Path> directory(Checkout checkout, ImportedItem item) {
        if (checkout == Checkout.ITEM_COMMIT) {
            return worktrees.checkout(item.provenance().origin().repository(), item.provenance().origin().commit());
        }
        return Optional.of(projectCheckout);
    }

    private @Nullable String startFresh(ImportedItem item, Path directory) {
        return started(List.of(request(item, directory, RoleForm.FRESH)));
    }

    private @Nullable String startWarm(ImportedItem item, Path directory) {
        if (warm != null && warm.generationId() != null && running(warm.generationId())
                && warm.directory().equals(directory)
                && WarmServing.mayShare(item.contamination(), ids(warmServed))
                && !WarmServing.names(warmServed, item)) {
            warmServed.add(item);
            return warm.generationId();
        }
        if (room() < 1) {
            // The role's count holds the workers this run already started: the next item cannot have one, so the item is
            // left ungraded and the worker this run no longer needs is asked to end.
            rotate();
            return null;
        }
        // The run no longer needs the worker it tracked: it is asked to end before its replacement starts, so no worker
        // of this run outlives the harness's reference to it.
        rotate();
        var generation = started(List.of(request(item, directory, RoleForm.WARM)));
        if (generation == null) {
            // The supervisor declined the start: the warm worker still serving the directory stays the one this run
            // knows, and the item is left ungraded rather than offered to it.
            return null;
        }
        warm = new WarmWorker(generation, directory);
        warmServed.clear();
        warmServed.add(item);
        return generation;
    }

    /**
     * @return how many workers the role's {@code count} still admits beside the ones this run started
     */
    private long room() {
        var running = supervisor.generations().stream()
                .filter(generation -> generation.role().equals(role.artifact().name()))
                .filter(generation -> generation.form() == RoleForm.WARM)
                .filter(generation -> !generation.terminal())
                .count();
        return role.artifact().count() - running;
    }

    /**
     * Asks the warm worker the next item may not share to end, so a later item of the run can start its replacement once
     * the worker has exited.
     */
    private void rotate() {
        if (warm == null || warm.generationId() == null) {
            return;
        }
        supervisor.generation(warm.generationId())
                .filter(generation -> !generation.terminal())
                .ifPresent(generation -> ending.forceEnd(generation, EndCause.RECYCLE));
        warm = null;
        warmServed.clear();
    }

    /**
     * @param generationId a generation this harness started
     * @return {@code true} while the supervisor still holds it in a non-terminal state
     */
    private boolean running(String generationId) {
        return supervisor.generation(generationId).filter(generation -> !generation.terminal()).isPresent();
    }

    /**
     * @param item      the item
     * @param directory the working directory of a tool-using worker
     * @param fresh     whether the start is a fresh worker's
     * @return the start request of the item's worker
     */
    private WorkerStartRequest request(ImportedItem item, Path directory, RoleForm form) {
        var graded = roleIn(form);
        var zeroTool = graded.zeroTool();
        return WorkerStartRequest.builder()
                .role(graded)
                .workdirKind(zeroTool ? LaunchRequest.WorkdirKind.JOB_PRIVATE
                        : LaunchRequest.WorkdirKind.PROJECT_CHECKOUT)
                .planId(null)
                .workdir(zeroTool ? null : directory)
                .envelope(form == RoleForm.FRESH && !zeroTool ? envelope(item) : null)
                .writesFreeText(false)
                .stateSkills(List.of())
                .domainSkills(domainSkills())
                .zeroToolPrompt(zeroTool ? zeroToolPrompt(item) : "")
                .build();
    }

    /**
     * The grading engine offers both forms through one harness, while a role artifact declares one. The other form of the
     * same configuration is that artifact in the other form: the launch differs only in the envelope and the supervised
     * lifecycle, and neither is an artifact member.
     *
     * @param form the form the engine offers
     * @return the role as that form
     */
    private EffectiveRole roleIn(RoleForm form) {
        if (role.form() == form) {
            return role;
        }
        return role.toBuilder().artifact(role.artifact().toBuilder().form(form).build()).build();
    }

    /**
     * @param requests the starts of one role
     * @return the generation of the started worker; {@code null} when the supervisor declined the start
     */
    private @Nullable String started(List<WorkerStartRequest> requests) {
        var outcomes = supervisor.start(requests);
        if (outcomes.isEmpty()) {
            return null;
        }
        return outcomes.getFirst() instanceof StartOutcome.Started(WorkerGeneration generation)
                ? generation.generationId() : null;
    }

    private static ContractEnvelope envelope(ImportedItem item) {
        return ContractEnvelope.of("no_plan", null, item.id(),
                List.of(new LinkSchema("submit", List.of(new LinkParameter("answer", "text", null)))),
                JsonNode.object(Map.of()));
    }

    private List<String> domainSkills() {
        return role.skills().stream().map(RoleSkill::uri).toList();
    }

    private static List<String> ids(List<ImportedItem> served) {
        return served.stream().map(ImportedItem::id).toList();
    }

    private static GradedAnswer answerOf(ItemTaskOffers.Submitted submitted) {
        return new GradedAnswer(submitted.answer(), submitted.totalTokens(), submitted.durationMs());
    }

    /**
     * The warm worker that is running, with the directory it is fixed to.
     *
     * @param generationId the generation; {@code null} when the supervisor declined the start
     * @param directory    the working directory of the worker
     */
    private record WarmWorker(@Nullable String generationId, Path directory) {
    }

    /**
     * @param item the item
     * @return the server-fixed prompt of a zero-tool role's task; empty for every other role
     */
    static String zeroToolPrompt(ImportedItem item) {
        var prompt = new StringBuilder();
        prompt.append(item.input().question()).append('\n');
        if (!item.input().facts().isEmpty()) {
            prompt.append(item.input().facts()).append('\n');
        }
        if (item.input().choices() != null) {
            prompt.append("Choices: ").append(String.join(", ", item.input().choices())).append('\n');
        }
        if (item.input().answerSchema() != null) {
            prompt.append("Answer schema: ").append(item.input().answerSchema().toJson()).append('\n');
        }
        return prompt.toString();
    }
}
