/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.consult;

import java.util.List;
import java.util.Objects;

import de.planmarshall.core.queue.QueueTaskRecord;
import org.jspecify.annotations.Nullable;

/**
 * What following a consultation link, or recording the answer of a consultation, did: it opened the consultation and
 * moved the asking task to {@code consulting}, it recorded the answer and delivered it, or it was refused with the
 * reason.
 *
 * @since 0.1
 */
public sealed interface ConsultationOutcome
        permits ConsultationOutcome.Opened, ConsultationOutcome.Answered, ConsultationOutcome.Refused {

    /**
     * The consultation opened; the asking task is in {@code consulting}.
     *
     * @param consultationId the consultation task, equal to its {@code queue_task_id}
     * @param askingTask     the asking task as it stands after the call
     * @since 0.1
     */
    record Opened(String consultationId, QueueTaskRecord askingTask) implements ConsultationOutcome {

        /** @throws NullPointerException if a component is {@code null} */
        public Opened {
            Objects.requireNonNull(consultationId, "consultationId");
            Objects.requireNonNull(askingTask, "askingTask");
        }
    }

    /**
     * The consultation closed; the asking task carries the consultation's fact.
     *
     * @param askingTask the asking task as it stands after the call, or {@code null} when the queue holds none
     * @param facts      the consultations the asking task currently carries, in the order they were asked
     * @since 0.1
     */
    record Answered(@Nullable QueueTaskRecord askingTask, List<ConsultationFact> facts)
            implements ConsultationOutcome {

        /** @throws NullPointerException if the list is {@code null} */
        public Answered {
            facts = List.copyOf(facts);
        }
    }

    /**
     * The call was refused.
     *
     * @param refusal why the call was refused
     * @param detail  what is wrong, naming the value where one exists
     * @since 0.1
     */
    record Refused(ConsultationRefusal refusal, String detail) implements ConsultationOutcome {

        /** @throws NullPointerException if a component is {@code null} */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
