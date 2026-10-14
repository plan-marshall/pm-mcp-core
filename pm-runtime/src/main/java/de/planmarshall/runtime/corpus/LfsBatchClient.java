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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscriber;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.role.corpus.JsonValue.JsonList;
import de.planmarshall.core.role.corpus.JsonValue.JsonObject;
import de.planmarshall.core.role.corpus.JsonValue.JsonString;
import de.planmarshall.core.role.corpus.JsonValue.MalformedJson;
import de.planmarshall.core.role.corpus.LfsObjectSource;
import de.planmarshall.core.role.corpus.LfsPointer;
import de.planmarshall.core.role.corpus.Sha256;
import de.planmarshall.core.role.corpus.SourceFetch;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * Downloads the Git LFS object a pointer names from the LFS endpoint of a corpus source's repository (PM-WORK-6,
 * PM-EXEC-5), through {@code cui-http} and never through a git filter driver.
 * <p>
 * The client sends one request of the LFS batch API, {@code POST <repository>.git/info/lfs/objects/batch} with the
 * operation {@code download} and the transfer {@code basic} in the media type {@value #MEDIA_TYPE}, and downloads the
 * object from the {@code href} of the answer's download action, with the action's header values, which are never
 * logged. Every request goes to an {@code https} URL, within the connect timeout of
 * {@value #CONNECT_TIMEOUT_SECONDS} s and the read timeout of {@value #READ_TIMEOUT_SECONDS} s. The download is read
 * up to one byte beyond the pointer's size and no further.
 * <p>
 * An endpoint that does not answer, answers with a server error or refuses the request is
 * {@link SourceFetch.Outcome#UNREACHABLE}; a repository without an LFS endpoint (404), an object error in the batch
 * answer, or an object the download does not find is {@link SourceFetch.Outcome#PIN_MISSING}. Each such outcome is
 * logged as a warning. Bytes the endpoint delivers are {@link SourceFetch.Outcome#FETCHED} even when they are not the
 * pointer's object (another size, another SHA-256, or longer than the pointer's size): such bytes were delivered, so
 * the hash check of {@link de.planmarshall.core.role.corpus.CorpusImport} refuses them and fails the run.
 *
 * @since 0.1
 */
public final class LfsBatchClient implements LfsObjectSource {

    /** The connect timeout of every request, in seconds. */
    public static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** The read timeout of every request, in seconds. */
    public static final int READ_TIMEOUT_SECONDS = 60;

    /** The media type of the LFS batch API. */
    public static final String MEDIA_TYPE = "application/vnd.git-lfs+json";

    /** The path of the batch API below the {@code .git} URL of a repository. */
    static final String BATCH_PATH = "/info/lfs/objects/batch";

    private static final CuiLogger LOGGER = new CuiLogger(LfsBatchClient.class);
    private static final String HTTPS = "https";
    private static final String HTTP = "http";
    private static final Set<String> LOOPBACK = Set.of("localhost", "127.0.0.1", "[::1]", "::1");
    private static final int MAX_OBJECT_SIZE = Integer.MAX_VALUE - 8;

    private final boolean cleartextLoopback;
    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;

    /**
     * A client that sends every request over {@code https}, within the compiled timeouts.
     */
    public LfsBatchClient() {
        this(false, CONNECT_TIMEOUT_SECONDS, READ_TIMEOUT_SECONDS);
    }

    /**
     * @param cleartextLoopback     {@code true} admits plain {@code http} to a loopback address too, for a local test
     *                              server
     * @param connectTimeoutSeconds the connect timeout of every request
     * @param readTimeoutSeconds    the read timeout of every request
     */
    LfsBatchClient(boolean cleartextLoopback, int connectTimeoutSeconds, int readTimeoutSeconds) {
        this.cleartextLoopback = cleartextLoopback;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    @Override
    public SourceFetch object(URI repository, LfsPointer pointer) {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(pointer, "pointer");
        var fetch = download(repository, pointer);
        if (!fetch.isFetched()) {
            LOGGER.warn(WARN.CORPUS_LFS_OBJECT_UNAVAILABLE, pointer.oid(), withoutUserInfo(repository), fetch.detail());
        }
        return fetch;
    }

    private SourceFetch download(URI repository, LfsPointer pointer) {
        if (repository.getRawUserInfo() != null || repository.getHost() == null) {
            return SourceFetch.unreachable("refused: the repository URL names no host or carries user information");
        }
        var batch = batchUri(repository);
        if (!admitted(batch)) {
            return SourceFetch.unreachable("refused: the batch API %s is not https".formatted(batch));
        }
        var answer = send(batch, Map.of("Accept", MEDIA_TYPE, "Content-Type", MEDIA_TYPE), batchRequest(pointer),
                BodyHandlers.ofByteArray());
        if (answer.failure().isPresent()) {
            return answer.failure().get();
        }
        var status = answer.response().statusCode();
        if (status == 404) {
            return SourceFetch.pinMissing("the repository has no LFS endpoint: %s answered 404".formatted(batch));
        }
        if (status < 200 || status > 299) {
            return SourceFetch.unreachable("the batch API %s answered %d".formatted(batch, status));
        }
        var step = action(answer.response().body(), pointer);
        return step.failure() != null ? step.failure() : fetchObject(step.download(), pointer);
    }

    private static Step action(byte[] body, LfsPointer pointer) {
        JsonValue answer;
        try {
            answer = JsonValue.parse(body);
        } catch (MalformedJson e) {
            return Step.failed(SourceFetch.unreachable("the batch answer is no JSON: " + e.getMessage()));
        }
        var entry = member(answer, "objects").filter(JsonList.class::isInstance).map(JsonList.class::cast)
                .flatMap(objects -> objects.elements().stream()
                        .filter(element -> member(element, "oid").filter(new JsonString(pointer.oid())::equals)
                                .isPresent())
                        .findFirst());
        if (entry.isEmpty()) {
            return Step.failed(SourceFetch.pinMissing("the batch answer names no object " + pointer.oid()));
        }
        var error = member(entry.get(), "error");
        if (error.isPresent()) {
            return Step.failed(SourceFetch.pinMissing("the batch answer refuses the object: code %s, %s".formatted(
                    member(error.get(), "code").map(JsonValue::toJson).orElse("none"),
                    member(error.get(), "message").map(JsonValue::toJson).orElse("no message"))));
        }
        var download = member(entry.get(), "actions").flatMap(actions -> member(actions, "download"));
        var href = download.flatMap(action -> member(action, "href")).filter(JsonString.class::isInstance)
                .map(value -> ((JsonString) value).value());
        if (href.isEmpty()) {
            return Step.failed(SourceFetch.pinMissing("the batch answer has no download action for the object"));
        }
        URI uri;
        try {
            uri = URI.create(href.get());
        } catch (IllegalArgumentException e) {
            return Step.failed(SourceFetch.unreachable("the download action names no URL"));
        }
        var headers = new LinkedHashMap<String, String>();
        download.flatMap(action -> member(action, "header")).filter(JsonObject.class::isInstance)
                .map(JsonObject.class::cast).ifPresent(header -> header.members().forEach((name, value) -> {
                    if (value instanceof JsonString string) {
                        headers.put(name, string.value());
                    }
                }));
        return Step.download(new Download(uri, headers));
    }

    private SourceFetch fetchObject(Download download, LfsPointer pointer) {
        if (!admitted(download.uri())) {
            return SourceFetch.unreachable("refused: the download URL of %s is not https".formatted(pointer.oid()));
        }
        if (pointer.size() > MAX_OBJECT_SIZE) {
            return SourceFetch.unreachable("the object of %d bytes is too large to hold".formatted(pointer.size()));
        }
        var location = withoutQuery(download.uri());
        var answer = send(download.uri(), download.headers(), null, info -> new BoundedBody(pointer.size() + 1));
        if (answer.failure().isPresent()) {
            return answer.failure().get();
        }
        var status = answer.response().statusCode();
        if (status == 404 || status == 410) {
            return SourceFetch.pinMissing("the download %s answered %d".formatted(location, status));
        }
        if (status < 200 || status > 299) {
            return SourceFetch.unreachable("the download %s answered %d".formatted(location, status));
        }
        var body = answer.response().body();
        if (body.length != pointer.size() || !Sha256.matches(body, pointer.oid())) {
            return SourceFetch.fetched(body, "LFS download %s served %s bytes that are not the object %s"
                    .formatted(location, body.length > pointer.size() ? "more" : String.valueOf(body.length),
                            pointer.oid()));
        }
        return SourceFetch.fetched(body, "LFS object " + pointer.oid());
    }

    /**
     * Sends one request through {@code cui-http}.
     *
     * @param uri     the target
     * @param headers the request headers
     * @param body    the body of a {@code POST}; {@code null} sends a {@code GET}
     * @param reader  reads the response body
     * @return the response, or the outcome of a request that got none
     */
    private Answer send(URI uri, Map<String, String> headers, String body, BodyHandler<byte[]> reader) {
        try (var handler = HttpHandler.builder().uri(uri).connectionTimeoutSeconds(connectTimeoutSeconds)
                .readTimeoutSeconds(readTimeoutSeconds).allowInsecureHttp(HTTP.equals(scheme(uri))).build()) {
            var request = handler.requestBuilder().uri(uri);
            headers.forEach(request::header);
            if (body == null) {
                request.GET();
            } else {
                request.POST(BodyPublishers.ofString(body));
            }
            return new Answer(handler.send(request.build(), reader), Optional.empty());
        } catch (IOException e) {
            return Answer.failed(SourceFetch.unreachable("%s did not answer: %s".formatted(withoutQuery(uri),
                    e.getClass().getSimpleName())));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Answer.failed(SourceFetch.unreachable("interrupted while asking " + withoutQuery(uri)));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Answer.failed(SourceFetch.unreachable("the request to %s is refused: %s".formatted(
                    withoutQuery(uri), e.getMessage())));
        }
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
     * @return the batch API of its LFS endpoint: the {@code .git} URL of the repository and {@value #BATCH_PATH}
     */
    static URI batchUri(URI repository) {
        var path = Optional.ofNullable(repository.getRawPath()).orElse("");
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (!path.endsWith(".git")) {
            path = path + ".git";
        }
        return URI.create(repository.getScheme() + "://" + repository.getRawAuthority() + path + BATCH_PATH);
    }

    private static String batchRequest(LfsPointer pointer) {
        return "{\"operation\":\"download\",\"transfers\":[\"basic\"],\"objects\":[{\"oid\":\"%s\",\"size\":%d}],"
                .formatted(pointer.oid(), pointer.size()) + "\"hash_algo\":\"sha256\"}";
    }

    private static Optional<JsonValue> member(JsonValue value, String name) {
        return value instanceof JsonObject object ? object.get(name) : Optional.empty();
    }

    private static String scheme(URI uri) {
        return uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    }

    private static URI withoutUserInfo(URI uri) {
        return URI.create(scheme(uri) + "://" + Optional.ofNullable(uri.getHost()).orElse("")
                + (uri.getPort() == -1 ? "" : ":" + uri.getPort()) + Optional.ofNullable(uri.getRawPath()).orElse(""));
    }

    /** @return the URL without its query, which may carry a signature */
    private static String withoutQuery(URI uri) {
        return withoutUserInfo(uri).toString();
    }

    /** The download action of the batch answer. */
    private record Download(URI uri, Map<String, String> headers) {
    }

    /** The response of a request, or the outcome of a request that got none. */
    private record Answer(HttpResponse<byte[]> response, Optional<SourceFetch> failure) {

        static Answer failed(SourceFetch failure) {
            return new Answer(null, Optional.of(failure));
        }
    }

    /**
     * Reads a response body up to a limit and stops reading there: a body longer than the limit yields its first
     * {@code limit} bytes, and the rest is never received.
     */
    static final class BoundedBody implements BodySubscriber<byte[]> {

        private final long limit;
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        /** @param limit the most bytes read */
        BoundedBody(long limit) {
            this.limit = limit;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return;
            }
            for (var buffer : items) {
                var room = limit - bytes.size();
                var take = (int) Math.min(buffer.remaining(), room);
                var chunk = new byte[take];
                buffer.get(chunk);
                bytes.write(chunk, 0, take);
                if (bytes.size() >= limit) {
                    result.complete(bytes.toByteArray());
                    subscription.cancel();
                    return;
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            result.complete(bytes.toByteArray());
        }
    }

    /** The download action of the batch answer, or the outcome that ends the fetch. */
    private record Step(Download download, SourceFetch failure) {

        static Step download(Download download) {
            return new Step(download, null);
        }

        static Step failed(SourceFetch failure) {
            return new Step(null, failure);
        }
    }
}
