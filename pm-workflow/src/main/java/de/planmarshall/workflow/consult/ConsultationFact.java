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

import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.queue.QueueTaskId;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.ConsultationStatus;

/**
 * One consultation as the asking task's facts carry it (Model Work § The Answer through the Next Wait;
 * Store Schemas § Queue Task Record, {@code consultations[]}): the {@code consultation_id}, the consulted role, the
 * status and the answer, which is absent for a consultation that closed {@code lost}.
 *
 * @param consultationId the consultation task, equal to its {@code queue_task_id}
 * @param role           the consulted role
 * @param status         {@code open}, {@code answered} or {@code unavailable}
 * @param answer         the answer; {@code null} while open and for an {@code unavailable} consultation
 * @since 0.1
 */
public record ConsultationFact(String consultationId, String role, ConsultationStatus status, String answer) {

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    /**
     * @throws NullPointerException     if the status is {@code null}
     * @throws IllegalArgumentException if the id or the role is absent or breaks its form, or the answer is too long
     */
    public ConsultationFact {
        if (QueueTaskId.parse(consultationId).isEmpty()) {
            throw new IllegalArgumentException("Not a queue task id: " + consultationId);
        }
        if (role == null || !ROLE.matcher(role).matches()) {
            throw new IllegalArgumentException("Not a role name: " + role);
        }
        Objects.requireNonNull(status, "status");
        if (answer != null && answer.codePointCount(0, answer.length()) > ConsultationService.MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("An answer has at most %d characters"
                    .formatted(ConsultationService.MAX_TEXT_LENGTH));
        }
    }

    /**
     * @param consultation the entry of the asking task
     * @return the fact that entry states
     */
    public static ConsultationFact of(QueueTaskRecord.Consultation consultation) {
        return new ConsultationFact(consultation.consultationId(), consultation.role(), consultation.status(),
                consultation.answer());
    }

    /** @return the status as the representation writes it */
    public String wireStatus() {
        return status.wireName();
    }
}
