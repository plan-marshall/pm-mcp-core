/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import java.util.Set;

/**
 * The closed link relations, each with the forms it allows and the form of a {@code link} clause that names none
 * (PM-IMPL-5).
 *
 * @since 0.1
 */
public enum LinkRelation {
    /** The regular continuation. */
    NEXT(Set.of(LinkForm.NOOP, LinkForm.SUBMIT), LinkForm.NOOP),
    /** An alternative continuation. */
    ALT(Set.of(LinkForm.NOOP, LinkForm.SUBMIT), LinkForm.NOOP),
    /** The same step again. */
    RETRY(Set.of(LinkForm.NOOP, LinkForm.SUBMIT), LinkForm.NOOP),
    /** A fix in the branch. */
    FIX(Set.of(LinkForm.NOOP, LinkForm.SUBMIT), LinkForm.NOOP),
    /** The return to an earlier phase. */
    LOOP_BACK(Set.of(LinkForm.NOOP, LinkForm.SUBMIT), LinkForm.SUBMIT),
    /** A submission. */
    SUBMIT(Set.of(LinkForm.SUBMIT), LinkForm.SUBMIT),
    /** The request of a waiver alone: it files a request and never awaits an answer. */
    ESCALATE(Set.of(LinkForm.SUBMIT), LinkForm.SUBMIT),
    /** The consultation of a role, rendered from a consult clause and never declared. */
    CONSULT(Set.of(LinkForm.SUBMIT), LinkForm.SUBMIT),
    /** The implicit step out of a session-driven sub-workflow alone. */
    ABORT(Set.of(LinkForm.SUBMIT), LinkForm.SUBMIT);

    private final Set<LinkForm> allowedForms;
    private final LinkForm defaultForm;

    LinkRelation(Set<LinkForm> allowedForms, LinkForm defaultForm) {
        this.allowedForms = allowedForms;
        this.defaultForm = defaultForm;
    }

    /**
     * @return the forms a link of this relation may take
     */
    public Set<LinkForm> allowedForms() {
        return allowedForms;
    }

    /**
     * @return the form of a {@code link} clause that omits its form
     */
    public LinkForm defaultForm() {
        return defaultForm;
    }
}
