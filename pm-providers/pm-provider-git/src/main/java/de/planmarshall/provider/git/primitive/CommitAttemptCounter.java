/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.util.Objects;

import de.planmarshall.core.store.CommitAttemptCaps;
import de.planmarshall.core.store.CommitAttemptCaps.ChangeSet;
import de.planmarshall.core.store.CommitAttemptCaps.Frame;

/**
 * Counts the two caps of the {@code git.commit-fix} loop over a {@link CommitAttemptCaps} value (Timeouts &amp;
 * Budgets § Commit Sub-Process Attempt Caps; State Catalogue § {@code git.stage-and-commit}).
 * <p>
 * The class is pure: it holds the persisted caps and answers a new counter from each counted event, so a caller
 * decides the cap before the next attempt and stores the value it read. The change-set counter is bound to the staged
 * tree id and the message digest: counting an attempt of another change set replaces the entry and starts it at one,
 * which is the spec's own reset rule and why a fix that changes the staged tree never keeps the loop alive on its own
 * (PM-WATCH-DSL-28) — the frame counter does, and a new call frame resets that one.
 *
 * @since 0.1
 */
public final class CommitAttemptCounter {

    /** The change-set cap: three failed commit attempts per identical staged change set. */
    public static final int CHANGE_SET_CAP = CommitAttemptCaps.CAP;

    /** The frame cap: three {@code git.commit-fix} tasks per call frame. */
    public static final int FRAME_CAP = CommitAttemptCaps.CAP;

    private final CommitAttemptCaps caps;

    private CommitAttemptCounter(CommitAttemptCaps caps) {
        this.caps = caps;
    }

    /**
     * @param caps the persisted caps
     * @return the counter over those caps
     */
    public static CommitAttemptCounter of(CommitAttemptCaps caps) {
        return new CommitAttemptCounter(Objects.requireNonNull(caps, "caps"));
    }

    /** @return the counter of a state that has neither failed a commit nor run a fix task */
    public static CommitAttemptCounter empty() {
        return new CommitAttemptCounter(CommitAttemptCaps.empty());
    }

    /**
     * Counts one failed commit attempt of a staged change set. The change-set entry keeps its count only while both
     * the staged tree and the message digest are the ones already counted; a different either half starts a new entry
     * at one. A spent cap stays spent: counting above it keeps it at {@link CommitAttemptCaps#CAP}.
     *
     * @param stagedTree    the id of the staged tree the attempt committed
     * @param messageDigest the SHA-256 of the commit message the attempt carried
     * @return the counter with the attempt counted
     */
    public CommitAttemptCounter failedCommitAttempt(String stagedTree, String messageDigest) {
        var sameChangeSet = caps.changeSet() != null
                && caps.changeSet().stagedTree().equals(stagedTree)
                && caps.changeSet().messageDigest().equals(messageDigest);
        var attempts = sameChangeSet ? Math.min(caps.changeSet().attempts() + 1, CHANGE_SET_CAP) : 1;
        return new CommitAttemptCounter(new CommitAttemptCaps(new ChangeSet(stagedTree, messageDigest, attempts),
                caps.frame()));
    }

    /**
     * Counts one {@code git.commit-fix} task of a call frame. The frame entry keeps its count only while the frame is
     * the one already counted; another frame starts a new entry at one. A spent cap stays spent.
     *
     * @param frameId the identity of the call frame the task ran in
     * @return the counter with the task counted
     */
    public CommitAttemptCounter fixTask(String frameId) {
        var sameFrame = caps.frame() != null && caps.frame().frameId().equals(frameId);
        var tasks = sameFrame ? Math.min(caps.frame().tasks() + 1, FRAME_CAP) : 1;
        return new CommitAttemptCounter(new CommitAttemptCaps(caps.changeSet(), new Frame(frameId, tasks)));
    }

    /** @return whether the change-set cap is spent at the counted change set */
    public boolean changeSetCapSpent() {
        return caps.changeSetSpent();
    }

    /** @return whether the frame cap is spent at the counted call frame */
    public boolean frameCapSpent() {
        return caps.frameSpent();
    }

    /**
     * @return whether either cap is spent, so no further commit attempt or fix task is admitted in this frame
     */
    public boolean exhausted() {
        return caps.exhausted();
    }

    /** @return the persisted caps this counter was read from */
    public CommitAttemptCaps caps() {
        return caps;
    }
}
