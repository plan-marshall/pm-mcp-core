/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
@NullMarked
package de.planmarshall.workflow.input;

import org.jspecify.annotations.NullMarked;

/**
 * The closed input types a unit declares in its frontmatter, and the value-level validation that rejects a value that
 * does not fit its type (PM-WF-16, Deliverable 1): length in code points, the Conventional Commit and trailer refusal,
 * the path rules of Decision 3, the entry cap, and the duplicate check.
 * <p>
 * The refusal is a value, never an exception (ADR {@code workflow-verify/013}).
 */