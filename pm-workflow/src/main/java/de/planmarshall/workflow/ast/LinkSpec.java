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

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A declared {@code link} clause of a state (PM-IMPL-5). The lists are copied into immutable lists.
 *
 * @param rel         the link relation
 * @param idPattern   the identifier pattern of the link
 * @param form        the link form; the relation's default form when the clause names none
 * @param freeParams  the free parameters in source order
 * @param guard       the guard of the link, {@code null} when the clause declares none
 * @param action      what the link does
 * @param transitions the transitions of the action's outcomes
 * @param location    where the link was read
 * @since 0.1
 */
public record LinkSpec(
LinkRelation rel,
String idPattern,
LinkForm form,
List<ParamSpec> freeParams,
@Nullable GuardSpec guard,
LinkAction action,
List<OutcomeTransition> transitions,
SourceLocation location) {

    /**
     * Copies the free parameters and the transitions into immutable lists.
     *
     * @param rel         the link relation
     * @param idPattern   the identifier pattern of the link
     * @param form        the link form
     * @param freeParams  the free parameters
     * @param guard       the guard of the link, or {@code null}
     * @param action      what the link does
     * @param transitions the transitions of the action's outcomes
     * @param location    where the link was read
     */
    public LinkSpec {
        freeParams = List.copyOf(freeParams);
        transitions = List.copyOf(transitions);
    }
}
