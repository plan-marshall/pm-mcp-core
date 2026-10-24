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

import java.time.Instant;
import java.util.List;

import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.queue.QueueTaskRecord.Attempt;
import de.planmarshall.core.queue.QueueTaskRecord.Kind;
import de.planmarshall.core.queue.QueueTaskRecord.Lease;
import de.planmarshall.core.queue.QueueTaskRecord.Scope;
import de.planmarshall.core.queue.QueueTaskRecord.ScopeType;
import de.planmarshall.core.queue.QueueTaskRecord.Status;
import de.planmarshall.core.queue.QueueTaskRecord.TaskClass;
import de.planmarshall.core.role.RoleClass;

/**
 * The records and requests the tests of the consultation service use. They are written for the tests, never taken from
 * the content of the product.
 */
final class ConsultationFixtures {

    static final String ASKER = "qt-0000000000000001";
    static final String ASKER_ROLE = "writer";
    static final String REVIEWER = "reviewer";
    static final String SECOND_REVIEWER = "second-reviewer";
    static final String THIRD_REVIEWER = "third-reviewer";
    static final String ASKING_GENERATION = "l-0000000000000001";
    static final String CONSULTED_GENERATION = "l-0000000000000002";
    static final String SUCCESSOR_GENERATION = "l-0000000000000004";
    static final String DIGEST = "a".repeat(64);
    static final String QUESTION = "Which of the two readings is the one the specification means?";
    static final String ANSWER = "The second: the link is offered only while the task executes.";
    static final Instant AT = Instant.parse("2026-10-24T10:00:00Z");

    private ConsultationFixtures() {
    }

    static Scope planScope() {
        return new Scope(ScopeType.PLAN, "plan-a");
    }

    static QueueTaskRecord executing(String queueTaskId, String role, String generation) {
        var attempt = new Attempt(1, generation, generation, DIGEST, AT, AT, null, null, false);
        return QueueTaskRecord.builder()
                .queueTaskId(queueTaskId)
                .kind(Kind.WORK)
                .role(role)
                .taskClass(TaskClass.WRITER)
                .scope(planScope())
                .entity("envelope-1")
                .stateId("outline.review")
                .formDowngraded(false)
                .status(Status.EXECUTING)
                .continuation(null)
                .lease(new Lease(generation, generation, AT, AT))
                .attempts(List.of(attempt))
                .signals(List.of())
                .consultations(List.of())
                .consultationOf(null)
                .outcome(null)
                .reason(null)
                .questionId(null)
                .createdAt(AT)
                .closedAt(null)
                .build();
    }

    static QueueTaskRecord consultationTask(String consultationId, String askingTask, String role) {
        return QueueTaskRecord.builder()
                .queueTaskId(consultationId)
                .kind(Kind.CONSULTATION)
                .role(role)
                .taskClass(TaskClass.READ_ONLY)
                .scope(planScope())
                .entity(consultationId)
                .stateId("outline.review")
                .formDowngraded(false)
                .status(Status.QUEUED)
                .continuation(null)
                .lease(null)
                .attempts(List.of())
                .signals(List.of())
                .consultations(List.of())
                .consultationOf(askingTask)
                .outcome(null)
                .reason(null)
                .questionId(null)
                .createdAt(AT)
                .closedAt(null)
                .build();
    }

    static QueueTaskRecord askingWithOrphan(String consultationId) {
        var entry = new QueueTaskRecord.Consultation(consultationId, REVIEWER, ASKING_GENERATION, AT,
                QueueTaskRecord.ConsultationStatus.OPEN, null, null, null, null);
        return executing(ASKER, ASKER_ROLE, ASKING_GENERATION).withStatus(Status.CONSULTING)
                .withConsultations(List.of(entry));
    }

    static ConsultationRequest request(String askingTaskId, String generation, String role) {
        return new ConsultationRequest(askingTaskId, generation, role, RoleClass.READ_ONLY, false, List.of(role),
                QUESTION, AT);
    }

    static ConsultationRequest request(String role, RoleClass roleClass) {
        return new ConsultationRequest(ASKER, ASKING_GENERATION, role, roleClass, false, List.of(role), QUESTION, AT);
    }

    static ConsultationRequest request(String role, List<String> declaredRoles) {
        return new ConsultationRequest(ASKER, ASKING_GENERATION, role, RoleClass.READ_ONLY, false, declaredRoles,
                QUESTION, AT);
    }
}
