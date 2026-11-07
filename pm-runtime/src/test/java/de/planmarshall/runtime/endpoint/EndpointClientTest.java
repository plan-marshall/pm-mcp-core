/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The HTTP calls to a loopback endpoint (PM-CLIENT-2, Client Profiles § The Request): an answer keeps its status and
 * body, a target that answers nothing is unreachable, and the URL is the profile's base URL and the path.
 * <p>
 * Every request is answered by the response primed per test into the {@link ModuleDispatcherElement} of
 * {@link #getModuleDispatcher()}, keyed by method and request target, so a request for a wrong path or with a wrong
 * method gets no answer. The port comes from the injected {@link URIBuilder}; the host is the loopback literal of the
 * address the server is bound to, because the client admits only the two loopback literals. Contract literals, kept
 * by design: the {@code /models} path and the chat path of the protocol, the status codes, the JSON media type, the
 * loopback literals and the private address the client refuses, the {@code example.test} domain of a host that is no
 * loopback address, and the {@code unreachable} and {@code not a loopback address} wording of the details. The bodies,
 * the error path, the model, the host name and the port of a composed URL are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("Endpoint client")
class EndpointClientTest {

    /** The path of the models listing of the protocol (contract). */
    private static final String MODELS = "/models";

    /** Canned responses by {@code METHOD target}; each answers every request to its target. */
    private final Map<String, MockResponse> routes = new ConcurrentHashMap<>();
    private final EndpointClient client = new EndpointClient();
    private MockWebServer server;
    private String base;

    /** Answers a GET or POST with the response primed for its method and target; anything else gets none. */
    ModuleDispatcherElement getModuleDispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/";
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                return answer(request);
            }

            @Override
            public Optional<MockResponse> handlePost(RecordedRequest request) {
                return answer(request);
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET, HttpMethodMapper.POST);
            }
        };
    }

    private Optional<MockResponse> answer(RecordedRequest request) {
        return Optional.ofNullable(routes.get(request.getMethod() + " " + request.getTarget()));
    }

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        base = "http://" + host + ":" + uriBuilder.build().getPort();
    }

    private void on(String method, String target, int status, String body) {
        routes.put(method + " " + target, new MockResponse.Builder().code(status).body(body).build());
    }

    /** @return a generated lower-case word, safe in a URL path, a host label and a JSON string */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    private RecordedRequest nextRequest() throws InterruptedException {
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded, "a request reached the mock web server");
        return recorded;
    }

    @Test
    @DisplayName("a GET keeps the status and the body of the answer")
    void getKeepsTheAnswer() throws Exception {
        var body = "{\"id\":\"" + word() + "\"}";
        on("GET", MODELS, 200, body);

        var response = client.get(EndpointFixtures.profile(base), MODELS);
        var recorded = nextRequest();

        assertAll("the kept answer",
                () -> assertTrue(response.successful(), "a 200 answer is a successful call"),
                () -> assertEquals(200, response.status(), "the status of the answer is kept"),
                () -> assertEquals(body, response.body(), "the body of the answer is kept unchanged"),
                () -> assertEquals(EndpointClient.Failure.NONE, response.failure(), "an answer is no failure"),
                () -> assertEquals("GET", recorded.getMethod(), "the listing is read with a GET request"),
                () -> assertEquals(MODELS, recorded.getTarget(), "the path is appended to the base URL"));
    }

    @Test
    @DisplayName("an error status is an answer, not a failure of the call")
    void errorStatusIsAnAnswer() throws Exception {
        var path = "/" + word();
        var body = "{\"error\":\"" + word() + "\"}";
        on("GET", path, 500, body);

        var response = client.get(EndpointFixtures.profile(base), path);
        var recorded = nextRequest();

        assertAll("the error answer",
                () -> assertFalse(response.successful(), "a 500 answer is no successful call"),
                () -> assertEquals(500, response.status(), "the error status of the answer is kept"),
                () -> assertEquals(body, response.body(), "the body of the error answer is kept"),
                () -> assertEquals(EndpointClient.Failure.NONE, response.failure(), "an error status is no failure"),
                () -> assertEquals(EndpointClient.MEDIA_TYPE, recorded.getHeaders().get("Content-Type"),
                        "the request names the JSON media type"));
    }

    @Test
    @DisplayName("a POST sends the body it is given")
    void postSendsTheBody() throws Exception {
        var body = "{\"model\":\"" + word() + "\"}";
        on("POST", EndpointClient.CHAT_PATH, 200, "{}");

        var response = client.post(EndpointFixtures.profile(base), EndpointClient.CHAT_PATH, body);
        var recorded = nextRequest();

        assertAll("the sent body",
                () -> assertTrue(response.successful(), "the POST is answered with a 200"),
                () -> assertEquals("POST", recorded.getMethod(), "the request is sent as a POST"),
                () -> assertEquals(EndpointClient.CHAT_PATH, recorded.getTarget(), "the chat path is requested"),
                () -> assertEquals(body, recorded.getBody().utf8(), "the given body is sent unchanged"));
    }

    @Test
    @DisplayName("a target that answers nothing is unreachable")
    void unreachable() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }

        var response = client.get(EndpointFixtures.profile("http://127.0.0.1:" + port), MODELS);

        assertAll("the unreachable target",
                () -> assertFalse(response.successful(), "a closed port is no successful call"),
                () -> assertEquals(EndpointClient.Failure.UNREACHABLE, response.failure(),
                        "a closed port is an unreachable target"),
                () -> assertTrue(response.detail().contains("unreachable"), response::detail),
                () -> assertEquals(0, response.status(), "a call without answer has status 0"));
    }

    @Test
    @DisplayName("a target that is not a loopback address is refused before any request")
    void notLoopback() {
        var response = client.get(EndpointFixtures.profile("https://" + word() + ".example.test"), MODELS);

        assertAll("the refused target",
                () -> assertFalse(response.successful(), "a refused target is no successful call"),
                () -> assertEquals(EndpointClient.Failure.UNREACHABLE, response.failure(),
                        "a refused target is reported unreachable"),
                () -> assertTrue(response.detail().contains("not a loopback address"), response::detail),
                () -> assertEquals(0, server.getRequestCount(), "no request reaches any server"));
    }

    @Test
    @DisplayName("the URL is the base URL and the path, with or without its leading slash")
    void uri() {
        int port = Generators.integers(1024, 65_535).next();
        var path = word();
        var profile = EndpointFixtures.profile("http://127.0.0.1:" + port);
        var expected = URI.create("http://127.0.0.1:" + port + "/" + path);

        assertAll("the composed URL",
                () -> assertEquals(expected, EndpointClient.uri(profile, "/" + path),
                        "a leading slash is kept as the separator"),
                () -> assertEquals(expected, EndpointClient.uri(profile, path),
                        "a missing leading slash is added"));
    }

    @Test
    @DisplayName("both loopback literals are admitted, with the brackets URI keeps")
    void loopbackLiterals() {
        int port = Generators.integers(1024, 65_535).next();

        assertAll("the loopback literals",
                () -> assertTrue(EndpointClient.loopback(URI.create("http://127.0.0.1:" + port)),
                        "the IPv4 loopback literal is admitted"),
                () -> assertTrue(EndpointClient.loopback(URI.create("http://[::1]:" + port)),
                        "the IPv6 loopback literal is admitted"),
                () -> assertFalse(EndpointClient.loopback(URI.create("http://10.0.0.2:" + port)),
                        "a private address is no loopback address"),
                () -> assertFalse(EndpointClient.loopback(URI.create("https://" + word() + ".example.test")),
                        "a host name is no loopback address"));
    }
}
