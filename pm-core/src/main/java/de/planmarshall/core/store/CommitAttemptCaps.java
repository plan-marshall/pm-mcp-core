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

import java.util.Objects;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The attempts of the two caps of the {@code git.commit-fix} loop, the member {@code commit_attempt_caps} of the
 * workflow state store (Timeouts &amp; Budgets § Commit Sub-Process Attempt Caps; State Catalogue §
 * {@code git.stage-and-commit}).
 * <p>
 * Two caps bound the loop and are counted independently: the {@link ChangeSet change-set cap} counts the failed
 * commit attempts of one identical staged change set — the staged tree id and the message — and resets when either
 * half changes; the {@link Frame frame cap} counts the {@code git.commit-fix} tasks of one call frame and resets by a
 * new call. Whichever is spent first ends the loop, so a fix that changes the staged tree resets the change-set
 * counter without keeping the loop alive (PM-WATCH-DSL-28).
 * <p>
 * The value is immutable and holds data only; the transitions live in the pure class {@code CommitAttemptCounter} of
 * this package, so both the engine and the assembly may count through it.
 *
 * @param changeSet the change-set cap counted at the current staged change set, or {@code null} before the first
 *                  failed attempt
 * @param frame     the frame cap counted at the current call frame, or {@code null} before the first fix task
 * @since 0.1
 */
public record CommitAttemptCaps(@Nullable ChangeSet changeSet, @Nullable Frame frame) {

    /** The cap of both counters: three attempts of the change set and three fix tasks per call frame. */
    public static final int CAP = 3;

    /**
     * The failed commit attempts of one identical staged change set.
     *
     * @param stagedTree    the id of the staged tree the attempt committed
     * @param messageDigest the SHA-256 of the commit message the attempt carried
     * @param attempts      the failed attempts of that change set, {@code 0} to {@link #CAP}
     * @since 0.1
     */
    public record ChangeSet(String stagedTree, String messageDigest, int attempts) {

        private static final Pattern TREE = Pattern.compile("[0-9a-f]{40}");

        private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

        /**
         * @throws NullPointerException     if a digest is {@code null}
         * @throws IllegalArgumentException if the staged tree or the message digest breaks its grammar, or the
         *                                  attempts lie outside {@code 0} to {@link #CAP}
         */
        public ChangeSet {
            requirePattern(stagedTree, TREE, "change_set.staged_tree");
            requirePattern(messageDigest, DIGEST, "change_set.message_digest");
            requireAttempts(attempts, "change_set.attempts");
        }
    }

    /**
     * The {@code git.commit-fix} tasks of one call frame.
     *
     * @param frameId the identity of the call frame the tasks belong to
     * @param tasks   the fix tasks taken in that frame, {@code 0} to {@link #CAP}
     * @since 0.1
     */
    public record Frame(String frameId, int tasks) {

        /**
         * @throws NullPointerException     if the frame id is {@code null}
         * @throws IllegalArgumentException if the frame id is blank, or the tasks lie outside {@code 0} to
         *                                  {@link #CAP}
         */
        public Frame {
            Objects.requireNonNull(frameId, "frame_id");
            if (frameId.isBlank()) {
                throw new IllegalArgumentException("'commit_attempt_caps.frame.frame_id' must not be blank");
            }
            requireAttempts(tasks, "commit_attempt_caps.frame.tasks");
        }
    }

    /** @return the caps of a state that has neither failed a commit nor run a fix task */
    public static CommitAttemptCaps empty() {
        return new CommitAttemptCaps(null, null);
    }

    /**
     * @return whether either cap is spent, which ends the {@code git.commit-fix} loop
     */
    public boolean exhausted() {
        return changeSetSpent() || frameSpent();
    }

    /**
     * @return whether the change-set cap is spent at the counted change set
     */
    public boolean changeSetSpent() {
        return changeSet != null && changeSet.attempts() >= CAP;
    }

    /**
     * @return whether the frame cap is spent at the counted call frame
     */
    public boolean frameSpent() {
        return frame != null && frame.tasks() >= CAP;
    }

    private static void requireAttempts(int attempts, String name) {
        if (attempts < 0 || attempts > CAP) {
            throw new IllegalArgumentException("'%s' must lie between 0 and %d: %d".formatted(name, CAP, attempts));
        }
    }

    private static void requirePattern(String value, Pattern pattern, String name) {
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not a digest: %s".formatted(name, value));
        }
    }
}
