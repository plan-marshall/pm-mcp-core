/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import java.net.URI;

/**
 * The Git LFS part of the fetch of a corpus source (PM-WORK-6, PM-EXEC-5): the object an LFS pointer names, from the
 * LFS endpoint of the source's repository.
 * <p>
 * The interface is implemented over HTTP outside {@code pm-core}, never through a git filter driver, and composed with
 * {@link CorpusImport} by the daemon. {@link CorpusImport} checks the downloaded object against the hash of the
 * manifest itself.
 *
 * @since 0.1
 */
public interface LfsObjectSource {

    /**
     * @param repository the repository of the source, whose LFS endpoint serves the object
     * @param pointer    the pointer naming the object
     * @return {@link SourceFetch.Outcome#FETCHED} with the bytes of the object;
     *         {@link SourceFetch.Outcome#UNREACHABLE} if the endpoint did not answer;
     *         {@link SourceFetch.Outcome#PIN_MISSING} if the endpoint does not have the object
     */
    SourceFetch object(URI repository, LfsPointer pointer);
}
