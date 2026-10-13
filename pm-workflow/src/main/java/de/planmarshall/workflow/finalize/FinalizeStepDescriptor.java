/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The facts of one finalize step, a built-in, bundle or project-local one (PM-IMPL-5): where it is ordered in the
 * pipeline, what it changes, what its verdict depends on, what it reads and destroys, and which findings it may file.
 * {@link FinalizePipelineValidator} checks a roster of these descriptors.
 * <p>
 * The descriptor is immutable: the lists and sets are copied into immutable collections, and no component, element or
 * nested value may be {@code null}. This module holds no table of the default steps; the default roster lives in the
 * assembly, and a project's roster is built from the default descriptors of the steps its finalize unit contains plus
 * its project step descriptors.
 *
 * @param name                  the step id, for example {@code default:push}
 * @param order                 the position of the step in the pipeline; unique in a roster
 * @param defaultOn             whether the step is on unless a preset or the operator switches it off
 * @param presets               the presets that switch the step on
 * @param description           what the step does, for the operator
 * @param mutatesSource         whether the step may change tracked source files
 * @param headDependent         whether the step's verdict depends on the commit it ran at
 * @param inputScope            the scope of the verdict's inputs; present exactly when {@code headDependent}
 * @param verdictInputs         the declared verdict inputs; non-empty exactly when {@code inputScope} is
 *                              {@link InputScope#DECLARED}
 * @param advancesMainViaRebase whether the step moves the branch onto a newer main by a rebase
 * @param postRunReview         whether the step reviews the run after the merge
 * @param reads                 the artifact tokens the step reads, for example {@code worktree} or {@code metrics}
 * @param destroys              the artifact tokens the step destroys, for example {@code plan-directory}
 * @param recordsFacts          the facts the step records, for example {@code work_performed}
 * @param lane                  the lane data of a lane-participating step; absent for a step that is always kept
 * @param requires              the steps this step requires
 * @param configurable          the operator-configurable settings of the step
 * @param persona               the persona the step's work runs under
 * @param requiresPromptFields  the prompt fields the step requires
 * @param cannotReach           the defect class a gate step cannot detect
 * @param filesFindings         the finding sources the step may file; empty when it files none
 * @since 0.1
 */
public record FinalizeStepDescriptor(
String name,
int order,
boolean defaultOn,
Set<String> presets,
String description,
boolean mutatesSource,
boolean headDependent,
Optional<InputScope> inputScope,
List<String> verdictInputs,
boolean advancesMainViaRebase,
boolean postRunReview,
List<String> reads,
List<String> destroys,
List<String> recordsFacts,
Optional<Lane> lane,
List<String> requires,
List<ConfigurableKey> configurable,
Optional<String> persona,
List<String> requiresPromptFields,
Optional<String> cannotReach,
List<String> filesFindings) {

    /** The order of {@code default:pre-push-quality-gate}. */
    public static final int PRE_PUSH_QUALITY_GATE_ORDER = 10;

    /** The order of {@code default:push}, directly after the pre-push quality gate. */
    public static final int PUSH_ORDER = 11;

    /** The order of the merge gate {@code default:branch-cleanup}; no step at or after it mutates source. */
    public static final int MERGE_GATE_ORDER = 70;

    /** The lowest order of a post-run review step. */
    public static final int POST_RUN_MIN_ORDER = 900;

    /** The order of {@code default:archive-plan}, the last step, which destroys the plan directory. */
    public static final int TERMINUS_ARCHIVE_ORDER = 1100;

    /**
     * Copies the lists and sets into immutable collections and refuses a missing value.
     *
     * @param name                  the step id
     * @param order                 the position of the step in the pipeline
     * @param defaultOn             whether the step is on by default
     * @param presets               the presets that switch the step on
     * @param description           what the step does
     * @param mutatesSource         whether the step may change tracked source files
     * @param headDependent         whether the step's verdict depends on the commit it ran at
     * @param inputScope            the scope of the verdict's inputs
     * @param verdictInputs         the declared verdict inputs
     * @param advancesMainViaRebase whether the step rebases the branch onto main
     * @param postRunReview         whether the step reviews the run after the merge
     * @param reads                 the artifact tokens the step reads
     * @param destroys              the artifact tokens the step destroys
     * @param recordsFacts          the facts the step records
     * @param lane                  the lane data
     * @param requires              the steps this step requires
     * @param configurable          the operator-configurable settings
     * @param persona               the persona of the step's work
     * @param requiresPromptFields  the prompt fields the step requires
     * @param cannotReach           the defect class a gate step cannot detect
     * @param filesFindings         the finding sources the step may file
     * @throws NullPointerException if a component or an element is {@code null}
     */
    public FinalizeStepDescriptor {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(inputScope, "inputScope");
        Objects.requireNonNull(lane, "lane");
        Objects.requireNonNull(persona, "persona");
        Objects.requireNonNull(cannotReach, "cannotReach");
        presets = Set.copyOf(presets);
        verdictInputs = List.copyOf(verdictInputs);
        reads = List.copyOf(reads);
        destroys = List.copyOf(destroys);
        recordsFacts = List.copyOf(recordsFacts);
        requires = List.copyOf(requires);
        configurable = List.copyOf(configurable);
        requiresPromptFields = List.copyOf(requiresPromptFields);
        filesFindings = List.copyOf(filesFindings);
    }

    /**
     * The lane data of a lane-participating step, as declared by the {@code lane:} block of the step. A step without a
     * lane is not lane-participating and always kept.
     *
     * @param laneClass    the class of the lane
     * @param tier         the tier of the lane
     * @param prunableWhen the condition under which a prunable step is pruned
     * @param costSize     the cost size of the step
     */
    public record Lane(LaneClass laneClass, Optional<String> tier, Optional<String> prunableWhen, String costSize) {

        /**
         * Refuses a missing value.
         *
         * @param laneClass    the class of the lane
         * @param tier         the tier of the lane
         * @param prunableWhen the condition under which a prunable step is pruned
         * @param costSize     the cost size of the step
         * @throws NullPointerException if a component is {@code null}
         */
        public Lane {
            Objects.requireNonNull(laneClass, "laneClass");
            Objects.requireNonNull(tier, "tier");
            Objects.requireNonNull(prunableWhen, "prunableWhen");
            Objects.requireNonNull(costSize, "costSize");
        }
    }

    /** The closed classes of a lane. */
    public enum LaneClass {
        /** A step every lane keeps. */
        CORE,
        /** A step that derives state other steps read. */
        DERIVED_STATE,
        /** A step a lane may prune. */
        PRUNABLE,
        /** An adversarial review step. */
        ADVERSARIAL
    }

    /** The closed scopes of the inputs a head-dependent step's verdict depends on. */
    public enum InputScope {
        /** The verdict depends on remote state, for example the checks of the pull request. */
        REMOTE_STATE,
        /** The verdict depends on inputs that cannot be enumerated. */
        OPEN_ENDED,
        /** The verdict depends on the files the plan changed. */
        PLAN_FOOTPRINT,
        /** The verdict depends on the inputs the step declares in {@code verdictInputs}. */
        DECLARED
    }

    /**
     * An operator-configurable setting of a step, as declared by its {@code configurable:} block.
     *
     * @param key          the configuration key
     * @param defaultValue the value used when the operator sets none
     * @param description  what the setting changes
     */
    public record ConfigurableKey(String key, String defaultValue, String description) {

        /**
         * Refuses a missing value.
         *
         * @param key          the configuration key
         * @param defaultValue the value used when the operator sets none
         * @param description  what the setting changes
         * @throws NullPointerException if a component is {@code null}
         */
        public ConfigurableKey {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(defaultValue, "defaultValue");
            Objects.requireNonNull(description, "description");
        }
    }
}
