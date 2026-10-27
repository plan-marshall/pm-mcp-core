/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.web;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * The links from a plan's process to the activity of its tasks (Store Schemas § Activity Journal: the plan's process
 * names per leaf its tasks with a link to their activity).
 * <p>
 * The process resource is built elsewhere; this type is the one place the activity URI of a task is spelled, so the
 * process and the task resource name the same target.
 *
 * @param queueTaskId the task
 * @param link        the URI of its activity
 * @since 0.1
 */
public record ActivityLinks(String queueTaskId, String link) {

    /** The URI template of a task's activity, one {@code %s} for the queue task id. */
    public static final String URI_TEMPLATE = "pm://task/%s/activity";

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the queue task id is blank
     */
    public ActivityLinks {
        Objects.requireNonNull(queueTaskId, "queueTaskId");
        Objects.requireNonNull(link, "link");
        if (queueTaskId.isBlank()) {
            throw new IllegalArgumentException("An activity link names its task");
        }
    }

    /**
     * @param queueTaskId the task
     * @return the activity link of the task
     */
    public static ActivityLinks of(String queueTaskId) {
        return new ActivityLinks(queueTaskId, URI_TEMPLATE.formatted(queueTaskId));
    }

    /**
     * @param queueTaskIds the tasks of one leaf of the process
     * @return one link per task, in the order the leaf names them
     */
    public static List<ActivityLinks> ofLeaf(Collection<String> queueTaskIds) {
        return Objects.requireNonNull(queueTaskIds, "queueTaskIds").stream().map(ActivityLinks::of).toList();
    }
}
