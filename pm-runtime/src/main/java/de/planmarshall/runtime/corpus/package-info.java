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
 * The fetch of the corpus sources over {@code cui-http} (PM-WORK-6, PM-EXEC-5): the Git LFS object download and the
 * Hugging Face dataset read over HTTPS, each implementing a {@code pm-core} source contract.
 * <p>
 * {@link de.planmarshall.runtime.corpus.LfsBatchClient} implements
 * {@link de.planmarshall.core.role.corpus.LfsObjectSource} of {@code pm-core}: it asks the LFS batch API of a source's
 * repository for the object a pointer names and downloads it over HTTPS within compiled timeouts, never through a git
 * filter driver, reading at most one byte beyond the pointer's size. Delivered bytes are handed over as they are;
 * {@link de.planmarshall.core.role.corpus.CorpusImport} checks them against the pinned SHA-256.
 * <p>
 * {@link de.planmarshall.runtime.corpus.HuggingFaceSourceRepository} implements
 * {@link de.planmarshall.core.role.corpus.SourceRepository} of {@code pm-core}: it confirms the pinned commit of a
 * Hugging Face dataset and reads one of its files at that commit over HTTPS within compiled timeouts, the raw body
 * bounded by a compiled maximum. Delivered bytes are handed over as they are;
 * {@link de.planmarshall.core.role.corpus.CorpusImport} checks them against the pinned SHA-256.
 */
@NullMarked
package de.planmarshall.runtime.corpus;

import org.jspecify.annotations.NullMarked;
