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

import java.util.List;

/**
 * A {@code decide <decision_kind> -> ask | quorum(<role>, ..., <score>)} line, declared only in the unit
 * {@code adjudicate.decisions} (PM-IMPL-5).
 *
 * @since 0.1
 */
public sealed interface DecisionDeclaration permits DecisionDeclaration.Ask, DecisionDeclaration.Quorum {

    /**
     * {@code ask}: the decision is put to the user.
     */
    record Ask() implements DecisionDeclaration {
    }

    /**
     * {@code quorum(...)}: the decision is taken by a vote of the listed roles.
     *
     * @param roles the voting roles in source order
     * @param score the score a decision needs
     */
    record Quorum(List<String> roles, int score) implements DecisionDeclaration {

        /**
         * Copies the roles into an immutable list.
         *
         * @param roles the voting roles in source order
         * @param score the score a decision needs
         */
        public Quorum {
            roles = List.copyOf(roles);
        }
    }
}
