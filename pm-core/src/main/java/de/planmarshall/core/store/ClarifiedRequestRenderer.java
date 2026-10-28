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

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import de.planmarshall.core.store.IntentRecord.Decider;

/**
 * Renders {@code ## Clarified Request} of {@code request.md} from the accepted intent record (Phase Workflows § The
 * Intent Record, Rendering): the record's members as sections, each item with its identifier, and the model's own calls
 * under their own heading, never mixed with the operator's decisions.
 * <p>
 * The section is derived, never read back: a later phase that names the clarified request means this section. An
 * existing section is replaced in place; a document without one gets it appended, so the original input and the
 * clarifications above it keep their text.
 *
 * @since 0.1
 */
public final class ClarifiedRequestRenderer {

    /** The heading of the rendered section. */
    public static final String HEADING = "## Clarified Request";

    private ClarifiedRequestRenderer() {
    }

    /**
     * @param store the intent store
     * @return the rendered section, ending with a newline
     */
    public static String render(IntentStore store) {
        return render(store, Map.of());
    }

    /**
     * @param store           the intent store
     * @param dimensionScores the six confidence dimensions of the last pass; empty unless the plan stayed below the
     *                        threshold
     * @return the rendered section, ending with a newline
     */
    public static String render(IntentStore store, Map<String, Integer> dimensionScores) {
        Objects.requireNonNull(store, "store");
        Objects.requireNonNull(dimensionScores, "dimensionScores");
        var intentRecord = store.current().intentRecord();
        var out = new StringBuilder();
        out.append(HEADING).append("\n\n");
        section(out, "Why", List.of(intentRecord.why()));
        section(out, "Goal", List.of(intentRecord.goal()));
        criteria(out, intentRecord.criteria());
        if (intentRecord.currentBehaviour() != null) {
            section(out, "Current Behaviour", List.of(intentRecord.currentBehaviour()));
        }
        nonGoals(out, intentRecord.nonGoals());
        constraints(out, intentRecord.constraints());
        decisions(out, "Operator Decisions", intentRecord.decisions().stream()
                .filter(item -> item.decider() == Decider.OPERATOR || item.decider() == Decider.QUORUM).toList());
        decisions(out, "Decisions from Facts", intentRecord.decisions().stream()
                .filter(item -> item.decider() == Decider.FACT).toList());
        decisions(out, "Model's Calls", intentRecord.decisions().stream()
                .filter(item -> item.decider() == Decider.MODEL).toList());
        assumptions(out, intentRecord.assumptions());
        openQuestions(out, intentRecord.openQuestions());
        deferred(out, intentRecord.deferred());
        sourceClaims(out, intentRecord.sourceClaims());
        confidence(out, dimensionScores);
        return out.toString();
    }

    private static void criteria(StringBuilder out, List<IntentRecord.Criterion> criteria) {
        out.append("### Criteria\n\n");
        for (var criterion : criteria) {
            out.append("- `").append(criterion.id()).append("` — ").append(criterion.text())
                    .append(" (`").append(criterion.kind().wireName()).append("`, verified by `")
                    .append(criterion.verifiedBy().wireName()).append("`)");
            if (criterion.capability() != null) {
                out.append(", changes `").append(criterion.capability().capability()).append("/")
                        .append(criterion.capability().requirement()).append("` (")
                        .append(criterion.capability().change().wireName()).append(")");
            }
            out.append('\n');
        }
        out.append('\n');
    }

    private static void nonGoals(StringBuilder out, List<IntentRecord.NonGoal> nonGoals) {
        out.append("### Non-Goals\n\n");
        nonGoals.forEach(item -> out.append("- `").append(item.id()).append("` — ").append(item.text())
                .append('\n'));
        out.append('\n');
    }

    private static void constraints(StringBuilder out, List<IntentRecord.Constraint> constraints) {
        if (constraints.isEmpty()) {
            return;
        }
        out.append("### Constraints\n\n");
        for (var constraint : constraints) {
            out.append("- `").append(constraint.id()).append("` — ").append(constraint.text())
                    .append(" (source: ").append(constraint.source().wireName());
            if (constraint.evidence() != null) {
                out.append("; evidence: `").append(constraint.evidence()).append('`');
            }
            out.append(")\n");
        }
        out.append('\n');
    }

    private static void assumptions(StringBuilder out, List<IntentRecord.Assumption> assumptions) {
        if (assumptions.isEmpty()) {
            return;
        }
        out.append("### Assumptions\n\n");
        for (var assumption : assumptions) {
            out.append("- `").append(assumption.id()).append("` — ").append(assumption.text())
                    .append(" (").append(assumption.status().wireName());
            if (assumption.riskiest()) {
                out.append("; riskiest, test: ").append(assumption.test());
            }
            out.append(")\n");
        }
        out.append('\n');
    }

    private static void openQuestions(StringBuilder out, List<IntentRecord.OpenQuestion> questions) {
        if (questions.isEmpty()) {
            return;
        }
        out.append("### Open Questions\n\n");
        questions.forEach(question -> out.append("- ").append(question.text()).append(" (")
                .append(question.resolver().wireName()).append(")\n"));
        out.append('\n');
    }

    private static void deferred(StringBuilder out, List<IntentRecord.Deferred> deferred) {
        if (deferred.isEmpty()) {
            return;
        }
        out.append("### Deferred\n\n");
        deferred.forEach(item -> out.append("- `").append(item.id()).append("` — ").append(item.text())
                .append(" (reason: ").append(item.reason()).append(")\n"));
        out.append('\n');
    }

    private static void sourceClaims(StringBuilder out, List<IntentRecord.SourceClaim> claims) {
        out.append("### Source Claims\n\n");
        for (var claim : claims) {
            out.append("- \"").append(claim.claim()).append("\" → ").append(claim.landedIn());
            if (claim.reason() != null) {
                out.append(" (").append(claim.reason()).append(')');
            }
            out.append('\n');
        }
        out.append('\n');
    }

    private static void confidence(StringBuilder out, Map<String, Integer> dimensionScores) {
        if (dimensionScores.isEmpty()) {
            return;
        }
        out.append("### Confidence\n\n")
                .append(dimensionScores.entrySet().stream()
                        .map(entry -> "%s: %d".formatted(entry.getKey(), entry.getValue()))
                        .collect(Collectors.joining(", ")))
                .append("\n\n");
    }

    /**
     * @param requestMarkdown the current {@code request.md}
     * @param store           the intent store
     * @return the document with the rendered section in place of an existing one or appended
     */
    public static String applyTo(String requestMarkdown, IntentStore store) {
        Objects.requireNonNull(requestMarkdown, "requestMarkdown");
        var rendered = render(store);
        var lines = requestMarkdown.lines().toList();
        var start = -1;
        for (var index = 0; index < lines.size(); index++) {
            if (HEADING.equals(lines.get(index).strip())) {
                start = index;
                break;
            }
        }
        if (start < 0) {
            return requestMarkdown + (requestMarkdown.endsWith("\n") ? "\n" : "\n\n") + rendered;
        }
        var end = lines.size();
        for (var index = start + 1; index < lines.size(); index++) {
            if (lines.get(index).startsWith("## ")) {
                end = index;
                break;
            }
        }
        var before = String.join("\n", lines.subList(0, start));
        var after = String.join("\n", lines.subList(end, lines.size()));
        var builder = new StringBuilder();
        if (!before.isEmpty()) {
            builder.append(before).append("\n\n");
        }
        builder.append(rendered);
        if (!after.isEmpty()) {
            builder.append(after).append('\n');
        }
        return builder.toString();
    }

    private static void section(StringBuilder out, String heading, List<String> paragraphs) {
        out.append("### ").append(heading).append("\n\n");
        paragraphs.forEach(paragraph -> out.append(paragraph).append('\n'));
        out.append('\n');
    }

    private static void decisions(StringBuilder out, String heading, List<IntentRecord.Decision> decisions) {
        if (decisions.isEmpty()) {
            return;
        }
        out.append("### ").append(heading).append("\n\n");
        for (var decision : decisions) {
            out.append("- `").append(decision.id()).append("` — ").append(decision.text())
                    .append(" (decided by: ").append(decision.decider().wireName()).append(")\n");
        }
        out.append('\n');
    }
}
