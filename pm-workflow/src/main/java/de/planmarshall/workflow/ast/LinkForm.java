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
 * The closed forms of a link (PM-IMPL-5). There is no wait form and no escalation form.
 *
 * @since 0.1
 */
public enum LinkForm {
    /** The link takes parameters. */
    SUBMIT,
    /** The link takes none. */
    NOOP
}
