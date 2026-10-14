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
 * The Git LFS part of the fetch of the corpus sources: the batch request and the object download through
 * {@code cui-http} (PM-WORK-6, PM-EXEC-5).
 * <p>
 * {@link de.planmarshall.runtime.corpus.LfsBatchClient} implements
 * {@link de.planmarshall.core.role.corpus.LfsObjectSource} of {@code pm-core}: it asks the LFS batch API of a source's
 * repository for the object a pointer names and downloads it over HTTPS within compiled timeouts, never through a git
 * filter driver, and checks its size and SHA-256 against the pointer.
 */
package de.planmarshall.runtime.corpus;
