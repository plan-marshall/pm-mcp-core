/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.RoleSkill;

/**
 * Composes a worker's launch prompt in the specification's order (PM-SKILL-4, Launch Prompt &amp; Contract Envelope):
 * (1) the shim paragraph, (2) the core conduct skill, (3) the task-protocol skill, (4) the role skill and the role's
 * further skills — byte-identically for every launch of a role on the same skill digests — and then, after the shared
 * prefix, (5) the skills of the task and (6) the contract envelope of a fresh worker.
 * <p>
 * The writing skill {@value #WRITING} is owned by the server and derived from the link schemas alone: an entry of that
 * URI in the role artifact is not delivered as a role skill, so a role that names it neither forces it into a task
 * without free text nor is it counted twice. Above the in-band bound the remaining task skills are rendered as their
 * section index with their URIs. A skill that does not resolve, or renders empty, fails the composition: no worker ever
 * runs with a required skill missing.
 *
 * @since 0.1
 */
public final class PromptComposer {

    /** The core conduct skill every worker receives. */
    public static final String CORE = "skill://pm/core/SKILL.md";

    /** The task-protocol skill every worker receives. */
    public static final String TASK_PROTOCOL = "skill://pm/task-protocol/SKILL.md";

    /** The writing skill a task that writes free text requires. */
    public static final String WRITING = "skill://pm/writing/SKILL.md";

    /**
     * @param request what to compose
     * @return the composed prompt
     * @throws CompositionException if a required skill does not resolve or the prompt cannot be composed
     */
    public LaunchPrompt compose(PromptRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.zeroTool()) {
            if (request.zeroToolPrompt().isEmpty()) {
                throw new CompositionException("the zero_tool prompt of the role is empty");
            }
            return new LaunchPrompt(request.zeroToolPrompt(), ResolvedSkill.sha256(request.zeroToolPrompt()), List.of(),
                    request.zeroToolPrompt().length());
        }
        var prefix = new StringBuilder();
        prefix.append(request.shimParagraph().strip()).append("\n\n");
        var delivered = new ArrayList<ResolvedSkill>();
        appendSkill(prefix, resolve(request, CORE), delivered);
        appendSkill(prefix, resolve(request, TASK_PROTOCOL), delivered);
        appendPrefixSkills(prefix, request, delivered);
        var text = new StringBuilder(prefix);
        var perLaunch = new StringBuilder();
        appendLaunchSkills(perLaunch, request, delivered);
        if (request.fresh() && request.envelope() != null) {
            perLaunch.append(request.envelope().render());
        }
        text.append(perLaunch);
        return new LaunchPrompt(text.toString(), ResolvedSkill.sha256(text.toString()), delivered, prefix.length());
    }

    private static void appendPrefixSkills(StringBuilder out, PromptRequest request, List<ResolvedSkill> delivered) {
        for (var roleSkill : request.roleSkills()) {
            if (WRITING.equals(roleSkill.uri())) {
                continue;
            }
            var resolved = resolve(request, roleSkill.uri());
            if (!resolved.digest().equals(roleSkill.digest())) {
                throw new CompositionException("the skill '%s' changed after the role was read".formatted(
                        roleSkill.uri()));
            }
            appendSkill(out, resolved, delivered);
        }
    }

    private static void appendLaunchSkills(StringBuilder out, PromptRequest request, List<ResolvedSkill> delivered) {
        if (!request.fresh()) {
            return;
        }
        var uris = new LinkedHashSet<String>(request.stateSkills());
        if (request.writesFreeText()) {
            uris.add(WRITING);
        }
        uris.addAll(request.domainSkills());
        var inBand = new StringBuilder();
        var index = new StringBuilder();
        var bound = request.inBandBoundChars();
        for (var uri : uris) {
            if (delivered.stream().noneMatch(skill -> skill.uri().equals(uri))) {
                var resolved = request.skills().resolve(uri).filter(skill -> !skill.text().isEmpty())
                        .orElseThrow(() -> new CompositionException("the skill '%s' does not resolve".formatted(uri)));
                if (bound > 0 && inBand.length() + resolved.text().length() > bound) {
                    index.append("- ").append(uri).append('\n');
                } else {
                    appendSkill(inBand, resolved, delivered);
                }
            }
        }
        out.append(inBand);
        if (!index.isEmpty()) {
            out.append("## Skill index\n\n");
            out.append("The remaining skills are named by their URI and served by `pm_skill`:\n\n");
            out.append(index);
            out.append('\n');
        }
    }

    private static ResolvedSkill resolve(PromptRequest request, String uri) {
        return request.skills().resolve(uri)
                .orElseThrow(() -> new CompositionException("the skill '%s' does not resolve".formatted(uri)));
    }

    private static void appendSkill(StringBuilder out, ResolvedSkill skill, List<ResolvedSkill> delivered) {
        out.append("## ").append(skill.uri()).append("\n\n");
        out.append(skill.text().strip()).append("\n\n");
        delivered.add(skill);
    }

    /**
     * What to compose.
     *
     * @param shimParagraph    the paragraph of the shim skill of the client bundle
     * @param zeroTool         whether the worker is a {@code zero_tool} worker
     * @param zeroToolPrompt   the server-fixed prompt of a {@code zero_tool} role
     * @param skills           where the skill text is read
     * @param roleSkills       the role's resolved skills in delivery order, the role skill first
     * @param fresh            whether the worker is fresh, so that it receives its task's skills and envelope
     * @param writesFreeText   whether the task writes free text, derived from the link schemas
     * @param stateSkills      the skills of the task's state, in delivery order
     * @param domainSkills     the project's domain skills, in delivery order
     * @param envelope         the contract envelope of a fresh worker; {@code null} for a warm worker
     * @param inBandBoundChars the in-band bound; {@code 0} for no bound
     * @since 0.1
     */
    public record PromptRequest(String shimParagraph, boolean zeroTool, String zeroToolPrompt, SkillSource skills,
    List<RoleSkill> roleSkills, boolean fresh, boolean writesFreeText, List<String> stateSkills,
    List<String> domainSkills, @Nullable ContractEnvelope envelope, int inBandBoundChars) {

        /** Copies the lists and refuses a missing source. */
        public PromptRequest {
            Objects.requireNonNull(shimParagraph, "shimParagraph");
            Objects.requireNonNull(zeroToolPrompt, "zeroToolPrompt");
            Objects.requireNonNull(skills, "skills");
            roleSkills = List.copyOf(Objects.requireNonNull(roleSkills, "roleSkills"));
            stateSkills = List.copyOf(Objects.requireNonNull(stateSkills, "stateSkills"));
            domainSkills = List.copyOf(Objects.requireNonNull(domainSkills, "domainSkills"));
            if (inBandBoundChars < 0) {
                throw new IllegalArgumentException("The in-band bound is not negative: " + inBandBoundChars);
            }
        }
    }

    /** A prompt that cannot be composed; the launch is refused {@code prompt_composition_failed}. */
    public static final class CompositionException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        CompositionException(String detail) {
            super(detail);
        }
    }
}
