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

/**
 * One {@code consult:<role>} link of a task entity (Model Work § The {@code consult:<role>} Link): the relation
 * {@code consult}, the form {@code submit}, the identifier {@code consult:<role>} and the parameter
 * {@code question: text[1..4000]}.
 * <p>
 * The link is offered only while the task executes, at most one of them at a time and at most the compiled number over
 * all of the task's attempts ({@link ConsultationService#MAX_CONSULT_LINKS}); a consultation task offers none, because a
 * consulted task never consults (Model Work § Serving the Consultation, Depth one).
 *
 * @param role the consulted role, a role name
 * @since 0.1
 */
public record ConsultationLink(String role) {

    /** The relation of every consultation link. */
    public static final String RELATION = "consult";

    /** The form of every consultation link. */
    public static final String FORM = "submit";

    /** The parameter that carries the question. */
    public static final String QUESTION_PARAMETER = "question";

    /** The identifier of the one link a consultation entity offers: its answer. */
    public static final String ANSWER_ID = "answer";

    private static final Pattern ROLE_PATTERN = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    /**
     * @throws NullPointerException     if the role is {@code null}
     * @throws IllegalArgumentException if the role is not a role name
     */
    public ConsultationLink {
        Objects.requireNonNull(role, "role");
        if (!ROLE_PATTERN.matcher(role).matches()) {
            throw new IllegalArgumentException("Not a role name: " + role);
        }
    }

    /** @return the identifier of the link, {@code consult:<role>} */
    public String id() {
        return RELATION + ":" + role;
    }
}
