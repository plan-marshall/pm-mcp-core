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

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.qualification.Checkout;
import de.planmarshall.core.role.qualification.JudgeScorer;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The judge side of the grading adapter: it sends a rubric answer to the judge configuration as a task of the shipped
 * {@code zero_tool} judge role, through the task protocol, and answers its structured score (Model Work § Grading).
 * <p>
 * The judge is a zero-tool worker like the screener: it runs in the empty job-private directory, one turn, with the
 * server-fixed judge prompt in-band, and its task closes {@code submitted} only from a structured final result. A judge
 * task that closes without one leaves the answer unscored, which the grading records as an ungraded item.
 *
 * @since 0.1
 */
public final class WorkerJudgeScorer implements JudgeScorer {

    /** The corpus item id by which a judge task is offered; the judge topic grades no role. */
    private static final String JUDGE_ROLE = "judge";

    private final EffectiveRole role;
    private final WorkerSupervisor supervisor;
    private final ItemTaskOffers offers;

    /**
     * @param role       the judge configuration
     * @param supervisor the supervisor that starts its workers
     * @param offers     the port that offers the judge's task to the task protocol
     * @throws NullPointerException if an argument is {@code null}
     */
    public WorkerJudgeScorer(EffectiveRole role, WorkerSupervisor supervisor, ItemTaskOffers offers) {
        this.role = Objects.requireNonNull(role, "role");
        this.supervisor = Objects.requireNonNull(supervisor, "supervisor");
        this.offers = Objects.requireNonNull(offers, "offers");
    }

    @Override
    public Optional<JudgeScore> score(JudgePrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        var request = WorkerStartRequest.builder()
                .role(role)
                .workdirKind(LaunchRequest.WorkdirKind.JOB_PRIVATE)
                .planId(null)
                .workdir(null)
                .envelope(null)
                .writesFreeText(false)
                .stateSkills(List.of())
                .domainSkills(role.skills().stream().map(RoleSkill::uri).toList())
                .zeroToolPrompt(prompt(prompt))
                .build();
        var outcomes = supervisor.start(List.of(request));
        if (outcomes.isEmpty()
                || !(outcomes.getFirst() instanceof StartOutcome.Started(WorkerGeneration generation))) {
            return Optional.empty();
        }
        return offers.offer(new ItemTaskOffers.Task(JUDGE_ROLE, generation.generationId(),
                judgeItem(prompt), role.form(), Checkout.CURRENT, null))
                .flatMap(submitted -> grade(submitted.answer())
                        .map(score -> new JudgeScore(score, submitted.totalTokens(), submitted.durationMs())));
    }

    /**
     * @param prompt the judge prompt
     * @return the server-fixed prompt of the judge role: the question, the facts, the reference where it is given, and the
     *         answer
     */
    static String prompt(JudgePrompt prompt) {
        var text = new StringBuilder();
        text.append("You judge one answer against the reference of its item.\n");
        text.append("Question: ").append(prompt.question()).append('\n');
        if (!prompt.facts().isEmpty()) {
            text.append("Facts: ").append(prompt.facts()).append('\n');
        }
        if (prompt.reference() != null) {
            text.append("Reference: ").append(prompt.reference()).append('\n');
        }
        text.append("Answer: ").append(prompt.answer()).append('\n');
        text.append("Answer with the judge-score object: a score from 0 to 1.\n");
        return text.toString();
    }

    private static ImportedItem judgeItem(JudgePrompt prompt) {
        var input = new ImportedItem.Input(prompt.question(), prompt.facts(), null,
                JsonValue.parse("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8)));
        var target = new ImportedItem.Target(prompt.reference(), "", List.of(), List.of(), false);
        var grading = new ImportedItem.Grading(ImportedItem.GradingMethod.RUBRIC_PROPERTIES, null, null);
        var provenance = new ImportedItem.Provenance(LocalDate.EPOCH,
                new ImportedItem.Origin(URI.create("https://example.org/judge"),
                        "%040x".formatted(0L), "judge/rows.csv", 1),
                List.of("offered to the judge role"));
        var review = new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED, "corpus", null, "");
        return new ImportedItem(JUDGE_ROLE + "/" + slug(prompt.itemId()), JUDGE_ROLE, JUDGE_ROLE, "judge task",
                List.of(), null, input, target, grading, provenance, review, null, "canary");
    }

    /**
     * @param itemId the corpus item the judge prompt names
     * @return the slug of the judge task, aligned with the item id pattern
     */
    private static String slug(String itemId) {
        var slug = itemId.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
        slug = slug.replaceAll("^-", "");
        return slug.matches("[a-z0-9].*") ? slug : "item-" + slug;
    }

    /**
     * @param answer the judge's structured answer
     * @return the score it states, within {@code 0} to {@code 1}; empty when it states none, which leaves the answer
     *         unscored rather than aborting the run on a model's prose
     */
    private static Optional<BigDecimal> grade(String answer) {
        try {
            var reference = JsonValue.parse(answer.getBytes(StandardCharsets.UTF_8));
            if (reference instanceof JsonValue.JsonObject(Map<String, JsonValue> members)
                    && members.get("score") instanceof JsonValue.JsonNumber(BigDecimal value, _)
                    && value.signum() >= 0 && value.compareTo(BigDecimal.ONE) <= 0) {
                return Optional.of(value);
            }
            // An answer that is not the judge-score object is no score: the item stays ungraded, as a lost judge task.
        } catch (IllegalArgumentException _) {
            // MalformedJson is an IllegalArgumentException: a model's prose is a failed judgment, never a failed run.
        }
        return Optional.empty();
    }
}
