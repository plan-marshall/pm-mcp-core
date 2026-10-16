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

import java.nio.file.Path;

/**
 * The source-fetch part of the fetch of a corpus source (PM-WORK-6, PM-EXEC-5): a cache repository of the source
 * below a cache root, the source's pinned commit made readable there, and one file read at that commit. The part
 * serves a git source and an HTTPS source alike; {@link SourceManifest#fetch()} names which.
 * <p>
 * The interface is implemented natively, outside {@code pm-core}, and composed with {@link CorpusImport} by the
 * daemon. {@link #fetchCommit} makes the pinned commit readable by {@link #readFile} and by {@link #prepare}: once it
 * has answered {@link SourceFetch.Outcome#FETCHED}, every file of the manifest can be read at the pin. Neither
 * {@link #prepare} nor {@link #fetchCommit} needs to create or store anything: a source the daemon fetches over
 * HTTPS may answer both without a cache repository. Every operation answers a {@link SourceFetch}:
 * {@link SourceFetch.Outcome#FETCHED} on success, {@link SourceFetch.Outcome#UNREACHABLE} when the repository did not
 * answer, {@link SourceFetch.Outcome#PIN_MISSING} when the commit or the path is absent at the pin. A file stored in
 * Git LFS is read as its pointer; its object comes from an {@link LfsObjectSource}.
 *
 * @since 0.1
 */
public interface SourceRepository {

    /**
     * Creates the cache repository of the source below the cache root, or opens the one there.
     *
     * @param cacheRoot the cache root, outside every repository
     * @param manifest  the manifest of the source
     * @return {@link SourceFetch.Outcome#FETCHED} with no content once the cache repository exists
     */
    SourceFetch prepare(Path cacheRoot, SourceManifest manifest);

    /**
     * Fetches exactly the pinned commit of the source from its repository into the cache repository.
     *
     * @param cacheRoot the cache root, outside every repository
     * @param manifest  the manifest of the source
     * @return {@link SourceFetch.Outcome#FETCHED} with no content once the commit is in the cache repository
     */
    SourceFetch fetchCommit(Path cacheRoot, SourceManifest manifest);

    /**
     * Reads one file at the pinned commit, as it is stored: a file in Git LFS as its pointer.
     *
     * @param cacheRoot the cache root, outside every repository
     * @param manifest  the manifest of the source
     * @param path      the repository-relative path of the file
     * @return {@link SourceFetch.Outcome#FETCHED} with the bytes of the file
     */
    SourceFetch readFile(Path cacheRoot, SourceManifest manifest, String path);
}
