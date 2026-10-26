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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.role.RoleArtifact;

/**
 * Computes the four activity-derived off-track signals of a worker from its task's activity journal and the task's
 * records, never from a model (Model Work § A Worker Off Track):
 * <ul>
 * <li>{@code repeating}: the same tool call with the same target, {@code N} times in a row, where a write of the target
 * or a failed call between two calls breaks the run;</li>
 * <li>{@code no_progress}: {@code N} completed turns without a link of the task in between;</li>
 * <li>{@code over_size}: the tokens of the attempt at {@code N} times the plan task's predicted cost;</li>
 * <li>{@code outside_footprint}: {@code N} unplanned changed files of the plan task.</li>
 * </ul>
 * <p>
 * The detector reads the compiled threshold of each signal and the role artifact's setting, which may only lower it.
 * A signal already raised on the attempt is not raised twice. A signal that cannot be measured — {@code over_size}
 * without a predicted cost, {@code outside_footprint} outside an envelope task — is not evaluated, never reported as
 * holding.
 *
 * @since 0.1
 */
public final class OffTrackDetector {

    /**
     * @param attempt             the attempt of the task
     * @param entries             the task's activity journal
     * @param role                the role of the task, with its off-track settings
     * @param alreadyRaised       the signals already raised on this attempt
     * @param predictedCostTokens the plan task's predicted cost; {@code null} when unknown or not an envelope task
     * @param unplannedChanges    the unplanned changed files of the current plan task
     * @return the signals that hold and were not raised yet, in the order of the closed signal list
     */
    public List<ActivitySignal> detect(int attempt, List<ActivityEntry> entries, RoleArtifact role,
            Set<RoleArtifact.OffTrackSignal> alreadyRaised, Double predictedCostTokens, int unplannedChanges) {
        var ofAttempt = entries.stream().filter(entry -> entry.attempt() == attempt).toList();
        var found = new ArrayList<ActivitySignal>();
        evaluate(RoleArtifact.OffTrackSignal.REPEATING, ofAttempt, role, alreadyRaised, found);
        evaluate(RoleArtifact.OffTrackSignal.NO_PROGRESS, ofAttempt, role, alreadyRaised, found);
        if (predictedCostTokens != null && predictedCostTokens > 0) {
            evaluate(RoleArtifact.OffTrackSignal.OVER_SIZE, role, alreadyRaised, found,
                    spentTokens(ofAttempt) / predictedCostTokens);
        }
        if (unplannedChanges >= 0) {
            evaluate(RoleArtifact.OffTrackSignal.OUTSIDE_FOOTPRINT, role, alreadyRaised, found,
                    unplannedChanges);
        }
        return List.copyOf(found);
    }

    private void evaluate(RoleArtifact.OffTrackSignal signal, List<ActivityEntry> entries, RoleArtifact role,
            Set<RoleArtifact.OffTrackSignal> alreadyRaised, List<ActivitySignal> found) {
        evaluate(signal, role, alreadyRaised, found, measurement(signal, entries));
    }

    private void evaluate(RoleArtifact.OffTrackSignal signal, RoleArtifact role,
            Set<RoleArtifact.OffTrackSignal> alreadyRaised, List<ActivitySignal> found, double measurement) {
        if (alreadyRaised.contains(signal) || measurement < 0) {
            return;
        }
        var setting = role.offTrack(signal);
        var threshold = setting.map(value -> value.effectiveThreshold(signal))
                .orElse(signal.compiledThreshold());
        if (measurement < threshold) {
            return;
        }
        var action = setting.map(RoleArtifact.OffTrackSetting::action).orElse(RoleArtifact.OffTrackAction.SHOW);
        found.add(new ActivitySignal(signal, action, measurement, threshold));
    }

    /**
     * @return the measured value of a signal, or a negative value when the journal cannot measure it
     */
    static double measurement(RoleArtifact.OffTrackSignal signal, List<ActivityEntry> entries) {
        return switch (signal) {
            case REPEATING -> repeats(entries);
            case NO_PROGRESS -> turnsSinceLink(entries);
            default -> -1;
        };
    }

    /**
     * Counts the longest run of a tool call repeated with the same target. A write of the target between two calls and
     * a failed tool result end the run; a different call starts a new one.
     */
    static double repeats(List<ActivityEntry> entries) {
        var longest = 0;
        var current = 0;
        String lastTool = null;
        String lastTarget = null;
        for (var entry : entries) {
            if (entry.kind() == ActivityEntry.Kind.TOOL_CALL) {
                var tool = entry.tool().name();
                var target = entry.tool().target();
                if (tool.equals(lastTool) && Objects.equals(target, lastTarget)) {
                    current++;
                } else {
                    lastTool = tool;
                    lastTarget = target;
                    current = 1;
                }
                longest = Math.max(longest, current);
            } else if (entry.kind() == ActivityEntry.Kind.FILE_CHANGED
                    && lastTarget != null && lastTarget.equals(entry.path())) {
                current = 0;
            } else if (entry.kind() == ActivityEntry.Kind.TOOL_RESULT && entry.result().failed()) {
                current = 0;
            }
        }
        return longest;
    }

    /** Counts the completed turns since the last link of the task, or since the attempt began. */
    static double turnsSinceLink(List<ActivityEntry> entries) {
        var turns = 0;
        for (var entry : entries) {
            if (entry.kind() == ActivityEntry.Kind.TURN) {
                turns++;
            } else if (entry.kind() == ActivityEntry.Kind.LINK) {
                turns = 0;
            }
        }
        return turns;
    }

    /** Sums the {@code total_tokens} field of the usage objects of the attempt's turns. */
    static long spentTokens(List<ActivityEntry> entries) {
        long total = 0;
        for (var entry : entries) {
            if (entry.kind() != ActivityEntry.Kind.TURN || entry.usage() == null) {
                continue;
            }
            total += totalTokens(entry.usage());
        }
        return total;
    }

    private static long totalTokens(String usage) {
        try {
            JsonNode root = JsonDocument.parse(usage.getBytes(StandardCharsets.UTF_8), "usage");
            var total = root.member("total_tokens").orElse(null);
            if (total != null && total.isInteger()) {
                return total.integerValue().orElse(0);
            }
            long sum = 0;
            for (var key : List.of("input_tokens", "output_tokens", "cache_read", "cache_creation", "reasoning")) {
                var value = root.member(key).orElse(null);
                if (value != null && value.isInteger()) {
                    sum += value.integerValue().orElse(0);
                }
            }
            return sum;
        } catch (ProfileRefusal _) {
            return 0;
        }
    }

    /**
     * @param raised the signals a task record already holds for the attempt
     * @return the set as the detector compares it
     */
    public static Set<RoleArtifact.OffTrackSignal> raisedOf(List<ActivityEntry> entries, int attempt) {
        var raised = EnumSet.noneOf(RoleArtifact.OffTrackSignal.class);
        for (var entry : entries) {
            if (entry.kind() == ActivityEntry.Kind.SIGNAL && entry.attempt() == attempt && entry.signal() != null) {
                Optional.ofNullable(switch (entry.signal().signal()) {
                    case REPEATING -> RoleArtifact.OffTrackSignal.REPEATING;
                    case NO_PROGRESS -> RoleArtifact.OffTrackSignal.NO_PROGRESS;
                    case OVER_SIZE -> RoleArtifact.OffTrackSignal.OVER_SIZE;
                    case OUTSIDE_FOOTPRINT -> RoleArtifact.OffTrackSignal.OUTSIDE_FOOTPRINT;
                    case AGENT_STARTED -> null;
                }).ifPresent(raised::add);
            }
        }
        return raised;
    }
}
