/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The identifier types and their validation.
 * <p>
 * {@link de.planmarshall.core.id.RoleName} holds the grammar of a role name and
 * {@link de.planmarshall.core.id.HarnessId} the grammar of a harness id; both read the one text of
 * {@link de.planmarshall.core.id.ShortName}, never a copy. The developer guide is {@code pm-core/doc/developer/core-id.adoc}.
 * <p>
 * Specification: {@code doc/specification/identifiers.adoc}.
 */
@NullMarked
package de.planmarshall.core.id;

import org.jspecify.annotations.NullMarked;
