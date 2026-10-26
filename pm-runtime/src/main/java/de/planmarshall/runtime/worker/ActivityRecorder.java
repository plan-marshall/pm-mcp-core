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

import java.time.InstantSource;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.SecretRedactor;
import de.planmarshall.core.role.RoleArtifact;

/**
 * Writes a worker's activity into the journal of the task's scope and evaluates the off-track signals at every entry
 * (Model Work § Output Read While the Worker Runs: every appended entry is the evaluation point).
 * <p>
 * The journal hands the attempt's entries, the new one included, to the detector inside its own leaf-lock transaction,
 * which assigns the sequence, redacts the entry and bounds it; each signal that holds is then reported to the task
 * protocol after the lock, and the protocol takes its action and records it. A signal already on the attempt's record
 * is not reported twice, so one cause never takes its action twice.
 * <p>
 * The recorder never evaluates a signal for a task whose facts it cannot read: an unknown task gets no signal, because
 * a signal without an attempt and a role would guess at a threshold.
 *
 * @since 0.1
 */
public final class ActivityRecorder {

    private final ActivityJournal journal;
    private final WorkerTaskActivity tasks;
    private final WorkerProtocol protocol;
    private final OffTrackDetector detector;
    private final InstantSource clock;
    private final Map<String, Set<RoleArtifact.OffTrackSignal>> reported = new ConcurrentHashMap<>();

    /**
     * @param journal  the journal of the task's scope
     * @param tasks    the facts of the served task
     * @param protocol where an off-track signal is reported
     * @param detector the detector of the activity signals
     * @param clock    the clock of the entry's instant
     */
    public ActivityRecorder(ActivityJournal journal, WorkerTaskActivity tasks, WorkerProtocol protocol,
            OffTrackDetector detector, InstantSource clock) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
        this.protocol = Objects.requireNonNull(protocol, "protocol");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Appends one activity entry and evaluates the signals it may raise.
     *
     * @param queueTaskId  the task
     * @param generationId the worker's generation
     * @param builder      the entry, without its sequence, attempt and instant
     * @return the entry as the journal wrote it
     * @throws IllegalStateException if the task's facts cannot be read
     */
    public ActivityEntry append(String queueTaskId, String generationId, ActivityEntry.ActivityEntryBuilder builder) {
        var facts = tasks.facts(queueTaskId).orElseThrow(
                () -> new IllegalStateException("The activity names no open task: " + queueTaskId));
        var at = clock.instant();
        var written = new ActivityEntry[1];
        var detected = new ArrayList<ActivitySignal>();
        var key = queueTaskId + "#" + facts.attempt();
        journal.appendNext(queueTaskId, seq -> {
            written[0] = builder.seq(seq).attempt(facts.attempt()).at(at).build();
            return written[0];
        }, entries -> {
            var raised = EnumSet.noneOf(RoleArtifact.OffTrackSignal.class);
            raised.addAll(facts.alreadyRaised());
            raised.addAll(OffTrackDetector.raisedOf(entries, facts.attempt()));
            raised.addAll(reported.getOrDefault(key, Set.of()));
            detected.addAll(detector.detect(facts.attempt(), entries, facts.role(), raised,
                    facts.predictedCostTokens(), facts.unplannedChanges()));
        });
        for (var signal : detected) {
            protocol.offTrack(new WorkerProtocol.OffTrackReport(generationId, queueTaskId, facts.attempt(),
                    signal.signal().wireName(), signal.action().wireName(), signal.measurement(),
                    signal.threshold(), clock.instant()));
            reported.computeIfAbsent(key, ignored -> EnumSet.noneOf(RoleArtifact.OffTrackSignal.class))
                    .add(signal.signal());
        }
        return SecretRedactor.redact(written[0]);
    }

    /**
     * Forgets the signals reported for a task whose attempt ended, so a successor attempt and a long-lived runtime do
     * not accumulate entries.
     *
     * @param queueTaskId the task
     */
    public void forget(String queueTaskId) {
        reported.keySet().removeIf(key -> key.startsWith(queueTaskId + "#"));
    }
}
