/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

import de.planmarshall.core.role.corpus.SourceFetch;
import de.planmarshall.core.role.corpus.SourceManifest;
import de.planmarshall.core.role.corpus.SourceRepository;
import de.planmarshall.provider.git.GitOperations.FetchCommitInput;
import de.planmarshall.provider.git.GitOperations.ReadFileInput;

/**
 * The git part of the fetch of the corpus sources (PM-WORK-6, PM-EXEC-5) over the cache repository operations of
 * the git contract: {@link SourceRepository} of {@code pm-core}, implemented natively.
 * <p>
 * Each source has its bare cache repository {@code <cache root>/repositories/<name>.git}. The pinned commit is fetched
 * from the source's repository, admitted by a {@link RemotePolicy} of its HTTPS origin alone, without credential.
 * The outcomes map onto those of the fetch: {@link GitOutcome#OK} is {@link SourceFetch.Outcome#FETCHED},
 * {@link GitOutcome#UNKNOWN_REVISION} and {@link GitOutcome#PATH_NOT_FOUND} are {@link SourceFetch.Outcome#PIN_MISSING},
 * and {@link GitOutcome#FAILED}, {@link GitOutcome#REMOTE_ORIGIN_MISMATCH} and every other outcome are
 * {@link SourceFetch.Outcome#UNREACHABLE}, each with its detail.
 *
 * @since 0.1
 */
public final class JGitSourceRepository implements SourceRepository {

    /** The directory of the cache repositories below the cache root. */
    public static final String REPOSITORIES = "repositories";

    private final GitOperations git;
    private final Function<SourceManifest, Remote> remotes;

    /**
     * Where the pinned commit of a source is fetched from, and which remotes the fetch may contact.
     *
     * @param url    the URL of the repository
     * @param policy the remotes the fetch may contact
     * @since 0.1
     */
    public record Remote(String url, RemotePolicy policy) {

        /**
         * Requires both values.
         */
        public Remote {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(policy, "policy");
        }

        /**
         * @param manifest the manifest of a source
         * @return the repository of the manifest, admitted by its HTTPS origin alone
         */
        public static Remote of(SourceManifest manifest) {
            var repository = manifest.repository();
            return new Remote(repository.toString(),
                    RemotePolicy.httpsOrigin(URI.create("https://" + repository.getRawAuthority())));
        }
    }

    /**
     * Fetches every source from the repository its manifest names.
     *
     * @param git the git contract
     */
    public JGitSourceRepository(GitOperations git) {
        this(git, Remote::of);
    }

    /**
     * Fetches every source from the remote the function gives, for a mirror or a local fixture repository.
     *
     * @param git     the git contract
     * @param remotes the remote of the manifest of a source
     */
    public JGitSourceRepository(GitOperations git, Function<SourceManifest, Remote> remotes) {
        this.git = Objects.requireNonNull(git, "git");
        this.remotes = Objects.requireNonNull(remotes, "remotes");
    }

    @Override
    public SourceFetch prepare(Path cacheRoot, SourceManifest manifest) {
        var cache = cache(cacheRoot, manifest);
        var result = git.initCache(cache);
        return result.isOk() ? SourceFetch.done("cache repository " + cache) : failed(result, manifest);
    }

    @Override
    public SourceFetch fetchCommit(Path cacheRoot, SourceManifest manifest) {
        var remote = remotes.apply(manifest);
        var result = git.fetchCommit(new FetchCommitInput(cache(cacheRoot, manifest), remote.url(), manifest.commit(),
                remote.policy()));
        return result.isOk() ? SourceFetch.done("commit %s of %s".formatted(manifest.commit(), remote.url()))
                : failed(result, manifest);
    }

    @Override
    public SourceFetch readFile(Path cacheRoot, SourceManifest manifest, String path) {
        var result = git.readFile(new ReadFileInput(cache(cacheRoot, manifest), manifest.commit(), path));
        return result.isOk()
                ? SourceFetch.fetched(result.value().orElseThrow(), "%s at %s".formatted(path, manifest.commit()))
                : failed(result, manifest);
    }

    /**
     * @param cacheRoot the cache root
     * @param manifest  the manifest of a source
     * @return the git directory of the cache repository of the source
     */
    public static Path cache(Path cacheRoot, SourceManifest manifest) {
        return cacheRoot.resolve(REPOSITORIES).resolve(manifest.name() + ".git");
    }

    private static SourceFetch failed(GitResult<?> result, SourceManifest manifest) {
        var detail = "%s of %s: %s".formatted(result.outcome().name().toLowerCase(Locale.ROOT),
                manifest.repository(), result.detail());
        return switch (result.outcome()) {
            case UNKNOWN_REVISION, PATH_NOT_FOUND -> SourceFetch.pinMissing(detail);
            default -> SourceFetch.unreachable(detail);
        };
    }
}
