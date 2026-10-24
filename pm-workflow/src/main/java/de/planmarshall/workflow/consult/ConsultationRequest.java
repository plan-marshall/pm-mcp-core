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
import java.util.Objects;
import java.util.regex.Pattern;

import de.planmarshall.core.queue.QueueTaskId;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;

/**
 * The ask of one task follows its {@code consult:<role>} link (Model Work § The {@code consult:<role>} Link): the asking
 * task and generation, the consulted role with the effective configuration its worker is started with, and the question.
 * <p>
 * The harness, model and effort are the consulted role's own effective values, resolved by the caller before the
 * consultation opens; they are never the asker's. The consultation task itself names only the role, because the task
 * protocol offers it to a generation of that role, which the supervisor starts from these values.
 *
 * @param askingTaskId        the asking queue task
 * @param generationId        the generation that follows the link
 * @param role                the consulted role
 * @param roleClass           the class of the consulted role
 * @param form                the form the consulted role's worker runs
 * @param configurationDigest the consulted role's configuration digest
 * @param harness             the consulted role's effective harness
 * @param model               the consulted role's effective model
 * @param effort              the consulted role's effective effort
 * @param question            the question, 1 to 4000 characters
 * @param at                  the instant of the ask
 * @since 0.1
 */
public record ConsultationRequest(String askingTaskId, String generationId, String role, RoleClass roleClass,
RoleForm form, String configurationDigest, String harness, String model, EffortValue effort, String question,
Instant at) {

    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");
    private static final Pattern GENERATION = Pattern.compile("l-[0-9a-f]{16}");
    private static final Pattern DIGEST = Pattern.compile("[0-9a-f]{64}");

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if an id, the role or a digest breaks its form
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
        Objects.requireNonNull(form, "form");
        if (configurationDigest == null || !DIGEST.matcher(configurationDigest).matches()) {
            throw new IllegalArgumentException("Not a SHA-256 configuration digest: " + configurationDigest);
        }
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(question, "question");
        Objects.requireNonNull(at, "at");
    }
}
