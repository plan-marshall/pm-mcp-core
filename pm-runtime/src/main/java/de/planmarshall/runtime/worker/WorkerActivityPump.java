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
import de.planmarshall.runtime.job.JobLog;

/**
 * Reads a running worker's output stream into its task's activity journal (Model Work § Output Read While the Worker
 * Runs): each complete line the job log gained since the previous pass is mapped through the harness's profile and
 * appended as it arrives, and a line that matches no mapping is counted, never shown.
 * <p>
 * The pump keeps the byte offset of the log it has read and the task the offset belongs to, so a line the warm worker
 * wrote for one task is never appended to the next one: a pump that is asked for another task first closes the attempt
 * it read — its tail and its {@code attempt_end} — and only then reads the new task. That close is also what a task
 * whose lease ended without an end path gets, so every attempt carries its own lines and one end entry.
 *
 * @since 0.1
 */
public final class WorkerActivityPump {

    private final ActivityEventMapper mapper;
    private final ActivityRecorder recorder;
    private @Nullable String task;
    private int attempt;
    private long offset;
    private int unmapped;

    /**
     * @param mapper   the harness's line mapper
     * @param recorder where a mapped entry is written
     */
    public WorkerActivityPump(ActivityEventMapper mapper, ActivityRecorder recorder) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
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
        if (task != null && !task.equals(queueTaskId)) {
            close(generationId, log);
        }
        var appended = pumpTail(queueTaskId, generationId, attempt, log, false);
        task = queueTaskId;
        this.attempt = attempt;
        return appended;
    }

    /**
     * Appends the tail of the attempt the pump read and its end, for a task whose lease ended without an end path (a
     * warm worker whose task closed). The task's facts are gone by then, so the entries carry the attempt the pump
     * read and no off-track signal is evaluated on a closed attempt.
     *
     * @param generationId the worker's generation
     * @param log          the worker's job log
     * @return the written end entry; empty when the pump read no task
     */
    public Optional<ActivityEntry> close(String generationId, JobLog log) {
        Objects.requireNonNull(log, "log");
        var closed = task;
        if (closed == null) {
            return Optional.empty();
        }
        pumpTail(closed, generationId, attempt, log, true);
        var written = recorder.appendClosed(closed, generationId, attempt, ActivityEntry.builder()
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.ATTEMPT_END)
                .end(QueueTaskRecord.AttemptEnd.CLOSED.wireName())
                .unmappedLines(unmapped));
        unmapped = 0;
        task = null;
        recorder.forget(closed);
        return Optional.of(written);
    }

    /**
     * Appends the attempt's end with the count of the lines that matched no mapping.
     *
     * @param queueTaskId  the task the worker held
     * @param generationId the worker's generation
     * @param end          how the attempt ended
     * @return the written entry
     */
    public ActivityEntry finish(String queueTaskId, String generationId, QueueTaskRecord.AttemptEnd end) {
        Objects.requireNonNull(end, "end");
        var written = recorder.append(queueTaskId, generationId, ActivityEntry.builder()
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.ATTEMPT_END)
                .end(end.wireName())
                .unmappedLines(unmapped));
        unmapped = 0;
        task = null;
        recorder.forget(queueTaskId);
        return written;
    }

    /** @return the number of lines that matched no mapping since the last end */
    public int unmappedLines() {
        return unmapped;
    }

    private int pumpTail(String queueTaskId, String generationId, int attempt, JobLog log, boolean closed) {
        if (!(log.readLines(offset) instanceof JobLog.Lines lines)) {
            return 0;
        }
        var appended = 0;
        for (var line : lines.lines()) {
            var mapped = mapper.map(line);
            if (mapped.isEmpty()) {
                unmapped++;
                continue;
            }
            if (closed) {
                recorder.appendClosed(queueTaskId, generationId, attempt, builder(mapped.get()));
            } else {
                recorder.append(queueTaskId, generationId, builder(mapped.get()));
            }
            appended++;
        }
        offset = lines.nextOffset();
        return appended;
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
}
