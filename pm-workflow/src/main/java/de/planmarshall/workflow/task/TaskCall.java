/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import java.util.Objects;

import de.planmarshall.core.queue.QueueTaskId;

/**
 * A call of one worker generation on one task entity (Model Work § Execute &amp; Submit). The generation comes from
 * the worker's relay, never from the model.
 *
 * @param queueTaskId  the task
 * @param generationId the caller's generation, {@code l-} and 16 hex digits
 * @since 0.1
 */
public record TaskCall(String queueTaskId, String generationId) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the task id or the generation breaks its form
     */
    public TaskCall {
        if (QueueTaskId.parse(queueTaskId).isEmpty()) {
            throw new IllegalArgumentException("Not a queue task id: " + queueTaskId);
        }
        Objects.requireNonNull(generationId, "generationId");
        if (generationId.length() != 18 || !generationId.startsWith("l-")
                || !generationId.substring(2).chars().allMatch(character -> Character.digit(character, 16) >= 0)) {
            throw new IllegalArgumentException("Not a generation (l- and 16 hex digits): " + generationId);
        }
    }
}
