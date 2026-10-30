/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.corpus;

import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.tools.logging.CuiLogger;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.role.corpus.SourceFetch;
import de.planmarshall.core.role.corpus.SourceManifest;
import de.planmarshall.core.role.corpus.SourceRepository;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * Reads a corpus source that is a Hugging Face dataset at its pinned commit (PM-WORK-6, PM-EXEC-5), through
 * {@code cui-http} and never through a git client: the {@link SourceRepository} implementation for a manifest whose
 * fetch is the HTTPS one, beside {@link LfsBatchClient}.
 * <p>
 * {@link #prepare} answers without a request and creates nothing: the files are read over HTTPS, so there is no cache
 * repository to prepare. {@link #fetchCommit(Path, SourceManifest)} confirms the pin with one {@code GET} of the
 * dataset's revision, {@code <host>/api/datasets/<owner>/<name>/revision/<commit>}, and stores nothing.
 * {@link #readFile(Path, SourceManifest, String)} reads one file at the pinned commit with one {@code GET} of
 * {@code <repository>/raw/<commit>/<path>}, which serves the file as it is stored, a file in Git LFS as its pointer;
 * the pointer's object comes from an {@link de.planmarshall.core.role.corpus.LfsObjectSource}.
 * <p>
 * Every request goes to an {@code https} URL without user information; a repository URL without scheme or host, or a
 * repository that is not {@code https}, is refused before anything is sent, and so is a repository that is not a
 * Hugging Face dataset URL. Every request is bounded by the compiled connect timeout of
 * {@value #CONNECT_TIMEOUT_SECONDS} s and read timeout of {@value #READ_TIMEOUT_SECONDS} s. An endpoint that does not
 * answer, answers with a server error or refuses the request is {@link SourceFetch.Outcome#UNREACHABLE}; a pin the
 * dataset does not have (404) and a file absent at the pin (404) are {@link SourceFetch.Outcome#PIN_MISSING}. Each
 * such outcome is logged as a warning through {@link WARN#CORPUS_HTTPS_SOURCE_UNAVAILABLE}, which names the repository
 * without user information, query or fragment and never a credential. Bytes the raw endpoint delivers are
 * {@link SourceFetch.Outcome#FETCHED} whatever they are, up to the raw-file maximum of {@value #MAX_RAW_FILE_SIZE}
 * bytes; a longer body is {@link SourceFetch.Outcome#UNREACHABLE} rather than held in memory. The import's hash check
 * of {@link de.planmarshall.core.role.corpus.CorpusImport} refuses bytes that are not the file's SHA-256.
 *
 * @since 0.1
 */
public final class HuggingFaceSourceRepository implements SourceRepository {

    /** The connect timeout of every request, in seconds. */
    public static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** The read timeout of every request, in seconds. */
    public static final int READ_TIMEOUT_SECONDS = 60;

    /**
     * The most bytes one raw file may hold. A raw body longer than this is refused rather than held in memory, so a
     * changed or hostile dataset file cannot exhaust the heap before the import's hash check runs.
     */
    private static final int MAX_RAW_FILE_SIZE = 64 * 1024 * 1024;

    /**
     * The path of the datasets of a Hugging Face repository: a dataset repository is {@code <host>/datasets/<owner>/
     * <name>}, so the dataset is the path below it.
     */
    static final String DATASETS = "/datasets/";

    /** The path of the dataset API below the host, which the revision of a pin is read from. */
    static final String DATASETS_API = "/api/datasets/";

    private static final CuiLogger LOGGER = new CuiLogger(HuggingFaceSourceRepository.class);
    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");

    /** The name of the {@code manifest} parameter, the message of the null check of the manifest-based methods. */
    private static final String MANIFEST = "manifest";

    private final boolean cleartextLoopback;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int maxRawFileSize;

    /**
     * A repository that sends every request over {@code https}, within the compiled timeouts and the compiled raw-file
     * maximum.
     */
    public HuggingFaceSourceRepository() {
        this(false, CONNECT_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS, MAX_RAW_FILE_SIZE);
    }

    /**
     * @param cleartextLoopback     {@code true} admits plain {@code http} to a loopback address too, for a local test
     *                              server
     * @param connectTimeoutSeconds the connect timeout of every request
     * @param readTimeoutSeconds    the read timeout of every request
     */
    HuggingFaceSourceRepository(boolean cleartextLoopback, int connectTimeoutSeconds, int readTimeoutSeconds) {
        this(cleartextLoopback, connectTimeoutSeconds, readTimeoutSeconds, MAX_RAW_FILE_SIZE);
    }

    /**
     * @param cleartextLoopback     {@code true} admits plain {@code http} to a loopback address too, for a local test
     *                              server
     * @param connectTimeoutSeconds the connect timeout of every request
     * @param readTimeoutSeconds    the read timeout of every request
     * @param maxRawFileSize        the most bytes one raw file may hold
     */
    HuggingFaceSourceRepository(boolean cleartextLoopback, int connectTimeoutSeconds, int readTimeoutSeconds,
            int maxRawFileSize) {
        this.cleartextLoopback = cleartextLoopback;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.maxRawFileSize = maxRawFileSize;
    }

    @Override
    public SourceFetch prepare(Path cacheRoot, SourceManifest manifest) {
        Objects.requireNonNull(manifest, MANIFEST);
        return SourceFetch.done("prepared");
    }

    @Override
    public SourceFetch fetchCommit(Path cacheRoot, SourceManifest manifest) {
        Objects.requireNonNull(manifest, MANIFEST);
        return fetchCommit(manifest.repository(), manifest.commit());
    }

    @Override
    public SourceFetch readFile(Path cacheRoot, SourceManifest manifest, String path) {
        Objects.requireNonNull(manifest, MANIFEST);
        return readFile(manifest.repository(), manifest.commit(), path);
    }

    /**
     * Confirms the pin of a repository, the entry the manifest-based method delegates to.
     *
     * @param repository the repository of a source
     * @param commit     the pinned commit
     * @return {@link SourceFetch.Outcome#FETCHED} once the revision answers 200; {@link SourceFetch.Outcome#PIN_MISSING}
     *         on 404; {@link SourceFetch.Outcome#UNREACHABLE} otherwise or for a repository the client refuses
     */
    SourceFetch fetchCommit(URI repository, String commit) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        var refused = refuse(repository);
        if (refused.isPresent()) {
            return log(repository, refused.get());
        }
        var revision = endpoint(repository, DATASETS_API, "/revision/" + commit);
        if (revision.isEmpty()) {
            return log(repository, notADataset(repository));
        }
        return log(repository, confirm(revision.get(), commit));
    }

    /**
     * Reads one file at the pinned commit of a repository, the entry the manifest-based method delegates to.
     *
     * @param repository the repository of a source
     * @param commit     the pinned commit
     * @param path       the repository-relative path of the file
     * @return {@link SourceFetch.Outcome#FETCHED} with the bytes as stored, up to the raw-file maximum;
     *         {@link SourceFetch.Outcome#PIN_MISSING} on 404; {@link SourceFetch.Outcome#UNREACHABLE} otherwise, for a
     *         repository the client refuses, or for a body over the raw-file maximum
     */
    SourceFetch readFile(URI repository, String commit, String path) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(commit, "commit");
        Objects.requireNonNull(path, "path");
        var refused = refuse(repository);
        if (refused.isPresent()) {
            return log(repository, refused.get());
        }
        var raw = endpoint(repository, DATASETS, "/raw/" + commit + "/" + path);
        if (raw.isEmpty()) {
            return log(repository, notADataset(repository));
        }
        return log(repository, download(raw.get()));
    }

    /**
     * Confirms the pin with one request: {@code 200} is the pinned commit, {@code 404} is a commit the dataset does not
     * have, and a server error or no answer is unreachable.
     */
    private SourceFetch confirm(URI revision, String commit) {
        var answer = send(revision, BodyHandlers.ofByteArray());
        if (answer.failure().isPresent()) {
            return answer.failure().get();
        }
        var status = answer.response().statusCode();
        if (status == 200) {
            return SourceFetch.done("confirmed the pin at " + withoutQuery(revision));
        }
        if (status == 404) {
            return SourceFetch.pinMissing(String.format(Locale.ROOT, "the dataset has no commit %s: %s answered 404",
                    commit, withoutQuery(revision)));
        }
        return SourceFetch.unreachable(
                String.format(Locale.ROOT, "the revision API %s answered %d", withoutQuery(revision), status));
    }

    /**
     * Reads a file at the pinned commit with one request: the bytes as stored up to the raw-file maximum (a {@code
     * 2xx}), a body over the maximum (unreachable), a file absent at the pin ({@code 404}), a server error or no answer
     * (unreachable). The read is bounded by {@link LfsBatchClient.BoundedBody}, so a body over the maximum is never
     * held in memory.
     */
    private SourceFetch download(URI raw) {
        var answer = send(raw, info -> new LfsBatchClient.BoundedBody((long) maxRawFileSize + 1));
        if (answer.failure().isPresent()) {
            return answer.failure().get();
        }
        var status = answer.response().statusCode();
        if (status == 404) {
            return SourceFetch.pinMissing(String.format(Locale.ROOT, "the raw file %s answered 404",
                    withoutQuery(raw)));
        }
        if (status < 200 || status > 299) {
            return SourceFetch.unreachable(
                    String.format(Locale.ROOT, "the raw file %s answered %d", withoutQuery(raw), status));
        }
        var body = answer.response().body();
        if (body.length > maxRawFileSize) {
            return SourceFetch.unreachable(String.format(Locale.ROOT,
                    "the raw file %s is larger than the maximum of %d bytes", withoutQuery(raw), maxRawFileSize));
        }
        return SourceFetch.fetched(body, "raw file " + withoutQuery(raw));
    }

    /**
     * Sends one {@code GET} through {@code cui-http}.
     *
     * @param uri    the target
     * @param reader reads the response body
     * @return the response, or the outcome of a request that got none
     */
    private Answer send(URI uri, BodyHandler<byte[]> reader) {
        try (var handler = HttpHandler.builder().uri(uri).connectionTimeoutSeconds(connectTimeoutSeconds)
                     .readTimeoutSeconds(readTimeoutSeconds).allowInsecureHttp(HTTP.equals(scheme(uri))).build()) {
            var request = handler.requestBuilder().uri(uri);
            request.GET();
            return new Answer(handler.send(request.build(), reader), Optional.empty());
        } catch (IOException e) {
            return Answer.failed(SourceFetch.unreachable("%s did not answer: %s".formatted(withoutQuery(uri),
                    e.getClass().getSimpleName())));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Answer.failed(SourceFetch.unreachable("interrupted while asking " + withoutQuery(uri)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            // The message of such an exception may quote the full URL: name its type only.
            return Answer.failed(SourceFetch.unreachable("the request to %s is refused: %s".formatted(
                    withoutQuery(uri), e.getClass().getSimpleName())));
        }
    }

    /**
     * @param repository the repository of a source
     * @return the outcome of a repository the client refuses; empty when it is admitted
     */
    private Optional<SourceFetch> refuse(URI repository) {
        if (repository.getScheme() == null || repository.getHost() == null
                || repository.getRawUserInfo() != null) {
            return Optional.of(SourceFetch.unreachable(
                    "refused: the repository URL names no scheme or no host, or carries user information"));
        }
        if (!admitted(repository)) {
            return Optional.of(SourceFetch.unreachable(
                    "refused: the repository %s is not https".formatted(withoutUserInfo(repository))));
        }
        return Optional.empty();
    }

    /**
     * @param repository the repository of a source that is not a dataset URL
     * @return the outcome that ends the fetch
     */
    private static SourceFetch notADataset(URI repository) {
        return SourceFetch.unreachable(
                "refused: the repository is not a Hugging Face dataset URL: " + withoutUserInfo(repository));
    }

    /**
     * Logs the outcome of a step that does not fetch, then hands it back.
     *
     * @param repository the repository of the source
     * @param fetch      the outcome of the step
     * @return the outcome
     */
    private static SourceFetch log(URI repository, SourceFetch fetch) {
        if (!fetch.isFetched()) {
            LOGGER.warn(WARN.CORPUS_HTTPS_SOURCE_UNAVAILABLE, withoutUserInfo(repository), fetch.detail());
        }
        return fetch;
    }

    private boolean admitted(URI uri) {
        var scheme = scheme(uri);
        if (uri.getHost() == null || uri.getRawUserInfo() != null) {
            return false;
        }
        return HTTPS.equals(scheme) || (cleartextLoopback && HTTP.equals(scheme)
                && LOOPBACK.contains(uri.getHost().toLowerCase(Locale.ROOT)));
    }

    /**
     * @param repository the repository of a source
     * @param root       the path the endpoint is rooted at below the host
     * @param suffix     the path below the dataset
     * @return the URL of the endpoint, or empty when the repository is not a dataset URL or the URL is no URI
     */
    private static Optional<URI> endpoint(URI repository, String root, String suffix) {
        var dataset = dataset(repository);
        if (dataset.isEmpty()) {
            return Optional.empty();
        }
        try {
            // Built through the URI API so every character a path may not carry verbatim is encoded: a space, a '%',
            // a '#' and a '?' all reach the endpoint as the file name holds them, where concatenation would cut the
            // path at a '#' or a '?' and misread a '%'.
            return Optional.of(new URI(repository.getScheme(), repository.getRawAuthority(),
                    root + dataset.get() + suffix, null, null));
        } catch (URISyntaxException _) {
            return Optional.empty();
        }
    }

    /**
     * @param repository a Hugging Face dataset repository URL
     * @return the dataset below {@code /datasets/}, {@code <owner>/<name>}; empty when the URL names no such dataset
     */
    private static Optional<String> dataset(URI repository) {
        var path = repository.getPath();
        if (path == null) {
            return Optional.empty();
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.startsWith(DATASETS) || path.length() == DATASETS.length()) {
            return Optional.empty();
        }
        return Optional.of(path.substring(DATASETS.length()));
    }

    private static String scheme(URI uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    }

    /**
     * @return the URL for a log line or a detail: scheme, host, port and path, without user information, query or
     *         fragment, which may carry a credential or a signature. A URL without scheme or host is named as such and
     *         none of its parts is shown, so the method never fails on what a caller hands in.
     */
    static String withoutUserInfo(URI uri) {
        if (uri.getScheme() == null || uri.getHost() == null) {
            return "<a URL without scheme or host>";
        }
        return scheme(uri) + "://" + uri.getHost() + (uri.getPort() == -1 ? "" : ":" + uri.getPort())
                + Optional.ofNullable(uri.getRawPath()).orElse("");
    }

    /** @return the URL without its query, which may carry a signature */
    private static String withoutQuery(URI uri) {
        return withoutUserInfo(uri);
    }

    /** The response of a request, or the outcome of a request that got none. */
    private record Answer(HttpResponse<byte[]> response, Optional<SourceFetch> failure) {

        static Answer failed(SourceFetch failure) {
            return new Answer(null, Optional.of(failure));
        }
    }
}
