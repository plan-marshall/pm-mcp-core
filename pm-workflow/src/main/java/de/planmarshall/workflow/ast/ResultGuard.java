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

/**
 * {@code guard [not] result.<field>} on a call-site transition: a field of the callee's result record, which holds
 * when a boolean field is true or an optional field is present (PM-IMPL-5, PM-WF-16).
 *
 * @param field   the field of the result record
 * @param negated whether the guard is negated with {@code not}
 * @since 0.1
 */
public record ResultGuard(String field, boolean negated) {
}
