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

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.runtime.job.JobLog;

/**
 * Reads a running worker's output stream into its task's activity journal (Model Work § Output Read While the Worker
 * Runs): each complete line the job log gained since the previous pass is mapped through the harness's profile and
 * appended as it arrives, and a line that matches no mapping is counted, never shown.
 * <p>
 * The pump keeps the byte offset of the log it has read, so a caller drives it while the worker runs and every call
 * costs only the new lines. When the attempt ends, the pump appends the {@code attempt_end} entry that carries the
 * count of unmatched lines.
 *
 * @since 0.1
 */
public final class WorkerActivityPump {

    private final ActivityEventMapper mapper;
    private final ActivityRecorder recorder;
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
     * Appends every complete line the log gained since the previous pass.
     *
     * @param queueTaskId  the task the worker holds
     * @param generationId the worker's generation
     * @param log          the worker's job log
     * @return the number of entries appended
     */
    public int pump(String queueTaskId, String generationId, JobLog log) {
        Objects.requireNonNull(log, "log");
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
            recorder.append(queueTaskId, generationId, builder(mapped.get()));
            appended++;
        }
        offset = lines.nextOffset();
        return appended;
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
        recorder.forget(queueTaskId);
        return written;
    }

    /** @return the number of lines that matched no mapping since the last end */
    public int unmappedLines() {
        return unmapped;
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
