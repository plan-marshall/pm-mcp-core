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

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobLog;

/**
 * Reads a running worker's output stream into its task's activity journal (Model Work § Output Read While the Worker
 * Runs): each complete line the job log gained since the previous pass is mapped through the harness's profile and
 * appended as it arrives, and a line that matches no mapping is counted, never shown.
 * <p>
 * The pump keeps the byte offset of the log it has read and the task the offset belongs to, so a line the warm worker
 * wrote for one task is never appended to the next one: a pump that is asked for another task first closes the attempt
 * it read — its tail and its {@code attempt_end} — and only then reads the new task. That close is also what a task
 * whose lease ended without an end path gets, so every attempt carries its own lines and one end entry. A finish keeps
 * the end the journal committed until the finish completes, so a retry after a later step threw appends no second one.
 *
 * @since 0.1
 */
public final class WorkerActivityPump {

    private final ActivityEventMapper mapper;
    private final ActivityRecorder recorder;
    private final ServedModelWatch watch;
    private @Nullable String task;
    private int attempt;
    private long offset;
    private int unmapped;
    private @Nullable Ended ended;

    /**
     * @param mapper   the harness's line mapper
     * @param recorder where a mapped entry is written
     */
    public WorkerActivityPump(ActivityEventMapper mapper, ActivityRecorder recorder) {
        this(mapper, recorder, ServedModelWatch.recordingNothing());
    }

    /**
     * @param mapper   the harness's line mapper
     * @param recorder where a mapped entry is written
     * @param watch    the attempt's watch of the model that answered its turns
     */
    public WorkerActivityPump(ActivityEventMapper mapper, ActivityRecorder recorder, ServedModelWatch watch) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.watch = Objects.requireNonNull(watch, "watch");
    }

    /**
     * Appends every complete line the log gained since the previous pass, into the task the generation holds. A line
     * of the attempt this pump read before is appended to that attempt first, with its end, so no line is attributed
     * to a task that did not write it.
     *
     * @param queueTaskId  the task the worker holds
     * @param generationId the worker's generation
     * @param attempt      the attempt of that task
     * @param log          the worker's job log
     * @return the number of entries appended
     */
    public int pump(String queueTaskId, String generationId, int attempt, JobLog log) {
        Objects.requireNonNull(queueTaskId, "queueTaskId");
        Objects.requireNonNull(log, "log");
        var switching = task != null && (!task.equals(queueTaskId) || this.attempt != attempt);
        if (switching && !closeTracked(generationId, log).switched()) {
            // The previous attempt's tail cannot be read yet: switching now would append its lines to the next task.
            return 0;
        }
        var read = log.readLines(offset);
        var appended = read instanceof JobLog.Lines lines ? appendLines(queueTaskId, generationId, attempt, lines, false)
                : 0;
        task = queueTaskId;
        this.attempt = attempt;
        return appended;
    }

    /**
     * Appends the tail of the attempt the pump read and its end, for a task whose lease ended without an end path (a
     * warm worker whose task closed). The task's facts are gone by then, so the entries carry the attempt the pump
     * read and no off-track signal is evaluated on a closed attempt. A tail that cannot be read leaves the attempt
     * open, so its lines are never appended to a later task.
     *
     * @param generationId the worker's generation
     * @param log          the worker's job log
     * @return the written end entry; empty when the pump read no task or the tail cannot be read yet
     */
    public Optional<ActivityEntry> close(String generationId, JobLog log) {
        Objects.requireNonNull(log, "log");
        return Optional.ofNullable(closeTracked(generationId, log).entry());
    }

    /**
     * Closes the tracked attempt: its tail, its end and the recorder's state for the task. A read of the tail that can
     * heal ({@code UNREADABLE}) answers no switch, because the lines it would leave behind are the next task's if the
     * pump moved on; a log that does not exist and one that shrank close the attempt with the lines they offered.
     *
     * @param generationId the worker's generation
     * @param log          the worker's job log
     * @return whether the pump may move on, and the entry written when it closed an attempt
     */
    private Closed closeTracked(String generationId, JobLog log) {
        var closed = task;
        if (closed == null) {
            return new Closed(true, null);
        }
        var read = log.readLines(offset);
        if (read instanceof JobLog.Unavailable unavailable && unavailable.state() == LogState.UNREADABLE) {
            return new Closed(false, null);
        }
        if (read instanceof JobLog.Lines lines) {
            appendLines(closed, generationId, attempt, lines, true);
        }
        var written = recorder.appendClosed(closed, attempt, ActivityEntry.builder()
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.ATTEMPT_END)
                .end(QueueTaskRecord.AttemptEnd.CLOSED.wireName())
                .unmappedLines(unmapped));
        unmapped = 0;
        task = null;
        recorder.forget(closed);
        watch.restart();
        return new Closed(true, written);
    }

    /**
     * Appends the attempt's end with the count of the lines that matched no mapping, once per attempt.
     * <p>
     * The pump keeps the end the journal committed before anything later in the call can throw — the report of an
     * off-track signal the end raised is such a step. A retry of a finish that threw after the commit therefore
     * appends no second end: it completes the rest of the finish and answers the end that is in the journal. A finish
     * that threw before the commit kept nothing, so its retry appends the end.
     *
     * @param queueTaskId  the task the worker held
     * @param generationId the worker's generation
     * @param end          how the attempt ended
     * @return the written entry
     */
    public ActivityEntry finish(String queueTaskId, String generationId, QueueTaskRecord.AttemptEnd end) {
        Objects.requireNonNull(queueTaskId, "queueTaskId");
        Objects.requireNonNull(end, "end");
        var kept = ended;
        var written = kept != null && kept.queueTaskId().equals(queueTaskId) ? kept.entry()
                : recorder.append(queueTaskId, generationId, ActivityEntry.builder()
                        .source(ActivityEntry.Source.SERVER)
                        .kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end(end.wireName())
                        .unmappedLines(unmapped), entry -> ended = new Ended(queueTaskId, entry));
        unmapped = 0;
        task = null;
        recorder.forget(queueTaskId);
        watch.restart();
        ended = null;
        return written;
    }

    /** @return the number of lines that matched no mapping since the last end */
    public int unmappedLines() {
        return unmapped;
    }

    private int appendLines(String queueTaskId, String generationId, int attempt, JobLog.Lines lines, boolean closed) {
        var appended = 0;
        for (var line : lines.lines()) {
            if (appendModelChange(queueTaskId, generationId, attempt, line, closed)) {
                appended++;
            }
            var mapped = mapper.map(line);
            if (mapped.isEmpty()) {
                unmapped++;
                continue;
            }
            if (closed) {
                recorder.appendClosed(queueTaskId, attempt, builder(mapped.get()));
            } else {
                recorder.append(queueTaskId, generationId, builder(mapped.get()));
            }
            appended++;
        }
        offset = lines.nextOffset();
        return appended;
    }

    /**
     * Asks the mapper for the model of one line and records a change through the watch, before the line's own entry.
     *
     * @return whether a {@code model_differs} entry was appended
     */
    private boolean appendModelChange(String queueTaskId, String generationId, int attempt, @Nullable String line,
            boolean closed) {
        var change = mapper.servedModel(line).flatMap(watch::see).orElse(null);
        if (change == null) {
            watch.recorded();
            return false;
        }
        var entry = ActivityEntry.builder()
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.MODEL_DIFFERS)
                .launchModel(change.launch())
                .servedModel(change.served());
        if (closed) {
            recorder.appendClosed(queueTaskId, attempt, entry);
        } else {
            recorder.append(queueTaskId, generationId, entry);
        }
        // The watch advances only after the entry is written: a failed write leaves the change pending, and the retry of
        // the same line offers it again instead of losing it.
        watch.recorded();
        return true;
    }

    private static ActivityEntry.ActivityEntryBuilder builder(ActivityEventMapper.Mapped mapped) {
        var builder = ActivityEntry.builder()
                .source(ActivityEntry.Source.HARNESS)
                .kind(mapped.kind());
        return switch (mapped.kind()) {
            case TEXT -> builder.text(mapped.text()).truncated(mapped.truncated());
            case TOOL_CALL -> builder.tool(mapped.tool());
            case TOOL_RESULT -> builder.result(mapped.result());
            case FILE_CHANGED -> builder.path(mapped.path());
            case TURN -> builder.stopReason(mapped.stopReason()).usage(mapped.usage());
            default -> throw new IllegalArgumentException("Not a harness kind: " + mapped.kind());
        };
    }

    /**
     * What a close decided: whether the pump may move on to another task, and the entry it wrote when it closed one.
     *
     * @param switched whether the tracked attempt is closed, so another task may be read
     * @param entry    the written end entry; {@code null} when nothing was closed
     */
    private record Closed(boolean switched, @Nullable ActivityEntry entry) {
    }

    /**
     * The end a finish wrote to the journal before a later step of that finish threw.
     *
     * @param queueTaskId the task whose attempt the end closes
     * @param entry       the end as the journal wrote it
     */
    private record Ended(String queueTaskId, ActivityEntry entry) {
    }
}
