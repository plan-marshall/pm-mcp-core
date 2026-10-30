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
 * The corpus sources of the model verification corpus: the formats of its own part, the fetch of public datasets at
 * their pinned commits, the generation of the imported items and the validation of the corpus.
 * <p>
 * The corpus a role configuration is graded against (PM-WORK-6) is its own part plus items generated from public
 * datasets. A source is named by its manifest {@code sources/<name>/manifest.json} ({@link SourceManifest}: the
 * repository, the kind of source it is, the pinned commit, the licence and the files with their SHA-256), and its items
 * by its overlay {@code sources/<name>/overlay.json} ({@link SourceOverlay}: the selected rows of one file with the
 * framing of each item). An item generated from a row is an {@link ImportedItem}, in the format of a corpus item with a
 * fetched provenance. Each of the three formats has a closed JSON schema below this package's resources and a strict
 * codec.
 * <p>
 * Specification: {@code doc/specification/model-work/01-roles-and-configuration.adoc} § Role Qualification.
 */
@NullMarked
package de.planmarshall.core.role.corpus;

import org.jspecify.annotations.NullMarked;
