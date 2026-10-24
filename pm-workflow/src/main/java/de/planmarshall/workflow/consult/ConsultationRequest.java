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
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.queue.QueueTaskId;
import de.planmarshall.core.role.RoleClass;

/**
 * The ask of one task follows its {@code consult:<role>} link (Model Work § The {@code consult:<role>} Link): the asking
 * task and generation, the consulted role and its class, the roles the state declares, and the question.
 * <p>
 * The consulted role's harness, model and effort are resolved by the caller — the worker of that role is started from
 * the role's own effective configuration — and are not a member here; the consultation task names the role, and the task
 * protocol offers it to a generation of that role. The question travels with the ask; the server composes it into the
 * consultation entity's representation, and this package does not persist it.
 *
 * @param askingTaskId    the asking queue task
 * @param generationId    the generation that follows the link
 * @param role            the consulted role
 * @param roleClass       the class of the consulted role
 * @param formDowngraded  whether the consulted role's warm artifact runs fresh because only the fresh form qualifies
 * @param declaredRoles   the roles the state declares with {@code consult role}
 * @param question        the question, 1 to 4000 characters
 * @param at              the instant of the ask
 * @since 0.1
 */
public record ConsultationRequest(String askingTaskId, String generationId, String role, RoleClass roleClass,
boolean formDowngraded, List<String> declaredRoles, String question, Instant at) {

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern GENERATION = Pattern.compile("l-[0-9a-f]{16}");

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if an id, the role or the generation breaks its form, or a declared role is not
     *                                  a role name
     */
    public ConsultationRequest {
        if (QueueTaskId.parse(askingTaskId).isEmpty()) {
            throw new IllegalArgumentException("Not a queue task id: " + askingTaskId);
        }
        if (generationId == null || !GENERATION.matcher(generationId).matches()) {
            throw new IllegalArgumentException("Not a generation (l- and 16 hex digits): " + generationId);
        }
        if (role == null || !ROLE.matcher(role).matches()) {
            throw new IllegalArgumentException("Not a role name: " + role);
        }
        Objects.requireNonNull(roleClass, "roleClass");
        declaredRoles = List.copyOf(Objects.requireNonNull(declaredRoles, "declaredRoles"));
        for (var declared : declaredRoles) {
            if (!ROLE.matcher(declared).matches()) {
                throw new IllegalArgumentException("Not a role name: " + declared);
            }
        }
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(at, "at");
    }
}
