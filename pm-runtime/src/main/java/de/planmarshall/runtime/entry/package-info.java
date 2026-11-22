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
 * The reader of a pending entry in its directory form ({@code pending/<entry_id>/}) for the engine's
 * {@link de.planmarshall.core.spi.PendingEntryReader} port (PM-ARCH-4; Store Schemas § Pending Entry).
 * <p>
 * The package holds one adapter over {@link de.planmarshall.core.store.PendingEntryStore} of {@code core.store}; it
 * implements the port outside the engine packages, as every engine port is (PM-IMPL-5), and it is the entry's
 * counterpart of {@code runtime.question.QuestionLogRecordReader}.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.runtime.entry;

import org.jspecify.annotations.NullMarked;
