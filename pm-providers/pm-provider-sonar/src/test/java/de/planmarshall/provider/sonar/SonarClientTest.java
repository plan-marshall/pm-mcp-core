/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.sonar;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import mockwebserver3.MockResponse;
import mockwebserver3.RecordedRequest;

import de.planmarshall.provider.http.Endpoint;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Result;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The issue client of Sonar against a mock Sonar API.
 * <p>
 * Every request is answered by one canned response primed per test into the {@link ModuleDispatcherElement} of
 * {@link #dispatcher()}, keyed by method and request target: the path {@code /api/issues/search} and the whole query
 * ({@code projects}, {@code pullRequest}, {@code resolved=false}, {@code p}, {@code ps}), so a request with a wrong
 * method, path, query or page gets no answer. A paged read primes one answer per value of {@code p}. Each nested group
 * returns that one element from its own {@code getModuleDispatcher()}.
 * <p>
 * Contract literals, kept by design: the endpoint path, the query names and their order, {@code resolved=false},
 * the page size {@code 500}; the JSON field names {@code issues}, {@code paging}, {@code pageIndex}, {@code pageSize},
 * {@code total}, {@code key}, {@code rule}, {@code severity}, {@code type}, {@code component}, {@code line},
 * {@code message}, {@code errors} and {@code msg}; the {@code Bearer} scheme and the {@code Retry-After} header; the
 * status codes; the severities and types of Sonar; the {@code :} that separates the parts of a Sonar project key.
 * Issue keys, rules, components, lines, messages, project keys, pull-request numbers, tokens and error messages are
 * generated. The client logs nothing, so the test asserts no log record.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("SonarClient")
class SonarClientTest {

    /** The path of the issue search (contract: Sonar's Web API). */
    private static final String SEARCH = "/api/issues/search";

    /** Canned responses by {@code METHOD target}; each answers one request, in the order they were primed. */
    private final Map<String, Deque<MockResponse>> routes = new ConcurrentHashMap<>();
    /** The requests the dispatcher saw, in arrival order. */
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private String token;
    private String projectKey;
    private int pullRequest;
    private SonarClient client;

    /** Records every request and answers it with the next response primed for its method and target. */
    ModuleDispatcherElement dispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return SEARCH;
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                requests.add(request);
                Deque<MockResponse> queue = routes.get(request.getMethod() + " " + request.getTarget());
                return Optional.ofNullable(queue == null ? null : queue.poll());
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET);
            }
        };
    }

    @BeforeEach
    void start(URIBuilder uriBuilder) {
        URI root = uriBuilder.build();
        token = word();
        // a Sonar project key joins its parts with ':', which the query carries URL-encoded
        projectKey = word() + ":" + word();
        pullRequest = Generators.integers(1, 100_000).next();
        client = new SonarClient(Endpoint.of(URI.create(root.getScheme() + "://" + root.getHost() + ":"
                + root.getPort() + "/")), () -> Optional.of(token));
    }

    @AfterEach
    void stop() {
        client.close();
    }

    /** @return the request target of page {@code page} of the open issues of the pull request under test */
    private String target(int page) {
        return SEARCH + "?projects=" + URLEncoder.encode(projectKey, StandardCharsets.UTF_8) + "&pullRequest="
                + pullRequest + "&resolved=false&p=" + page + "&ps=500";
    }

    private void onPage(int page, MockResponse response) {
        routes.computeIfAbsent("GET " + target(page), k -> new ConcurrentLinkedDeque<>()).add(response);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse.Builder().code(status).body(body).addHeader("Content-Type", "application/json")
                .build();
    }

    /** @return an answer of the issue search: the issues of page {@code page} of {@code total} issues */
    private static MockResponse page(int page, int total, List<SonarIssue> issues) {
        var body = Map.of("paging", Map.of("pageIndex", page, "pageSize", 500, "total", total),
                "issues", issues.stream().map(SonarClientTest::wire).toList());
        return json(200, Json.write(body));
    }

    /** @return the issue as Sonar sends it; an issue without line carries no {@code line} field */
    private static Map<String, Object> wire(SonarIssue issue) {
        var fields = new HashMap<String, Object>();
        fields.put("key", issue.key().value());
        fields.put("rule", issue.rule());
        fields.put("severity", issue.severity());
        fields.put("type", issue.type());
        fields.put("component", issue.component());
        fields.put("message", issue.message());
        if (issue.line() != null) {
            fields.put("line", issue.line());
        }
        return fields;
    }

    /** @return a generated word, safe in a query, a JSON string and a header */
    private static String word() {
        return Generators.letterStrings(4, 16).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated open issue; the severity and the type are values of Sonar's wire format */
    private SonarIssue issue(boolean withLine) {
        return SonarIssue.builder()
                .key(new SonarIssueKey(word() + "-" + word()))
                .rule("java:S" + Generators.integers(100, 9999).next())
                .severity(Generators.fixedValues("BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO").next())
                .type(Generators.fixedValues("BUG", "VULNERABILITY", "CODE_SMELL").next())
                .component(projectKey + ":src/" + word() + ".java")
                .line(withLine ? Generators.integers(1, 10_000).next() : null)
                .message(word() + " " + word())
                .build();
    }

    @Nested
    @DisplayName("read")
    class Read {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("reads the open issues of a pull request with the token as Bearer")
        void readsOpenIssues() {
            var issues = List.of(issue(true), issue(false));
            onPage(1, page(1, issues.size(), issues));

            var result = client.openIssues(projectKey, pullRequest);

            var request = requests.getFirst();
            assertAll("the read",
                    () -> assertEquals(issues, result.value(), "the issues are read field by field"),
                    () -> assertTrue(result.complete(), "a single page read is complete"),
                    () -> assertEquals(target(1), request.getTarget(), "the query names project, PR and page"),
                    () -> assertEquals("GET", request.getMethod(), "the issue search is a GET request"),
                    () -> assertEquals("Bearer " + token, request.getHeaders().get("Authorization"),
                            "the token is sent as a bearer token"),
                    () -> assertEquals("application/json", request.getHeaders().get("Accept"),
                            "the client asks for JSON"));
        }

        @Test
        @DisplayName("reads a pull request without open issues as an empty complete list")
        void readsNoIssues() {
            onPage(1, page(1, 0, List.of()));

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the empty read",
                    () -> assertEquals(List.of(), result.value(), "no open issue is read"),
                    () -> assertTrue(result.complete(), "the empty read is complete"),
                    () -> assertEquals(1, requests.size(), "one page is asked for"));
        }

        @Test
        @DisplayName("refuses no, a blank or an unusable project key and a pull request below 1")
        void refusesArguments() {
            var blank = " ".repeat(Generators.integers(0, 3).next());
            var belowOne = Generators.integers(-100, 0).next();

            assertAll("the refused arguments",
                    () -> assertThrows(NullPointerException.class, () -> client.openIssues(null, pullRequest),
                            "a missing project key is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> client.openIssues(blank, pullRequest),
                            "a blank project key is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> client.openIssues(projectKey, belowOne),
                            "a pull request below 1 is refused"),
                    () -> assertTrue(requests.isEmpty(), "a refused read sends no request"));
        }
    }

    @Nested
    @DisplayName("paging")
    class Paging {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @Test
        @DisplayName("reads every page while paging.total says more issues follow")
        void readsTwoPages() {
            var first = List.of(issue(true));
            var second = List.of(issue(true), issue(false));
            // the paging of Sonar, not the length of the page, says whether a page follows
            int total = 500 + Generators.integers(1, 500).next();
            onPage(1, page(1, total, first));
            onPage(2, page(2, total, second));

            var result = client.openIssues(projectKey, pullRequest);

            var all = new ArrayList<>(first);
            all.addAll(second);
            assertAll("the paged read",
                    () -> assertEquals(all, result.value(), "the issues of both pages are read"),
                    () -> assertTrue(result.complete(), "a read to the last page is complete"),
                    () -> assertEquals(List.of(target(1), target(2)),
                            requests.stream().map(RecordedRequest::getTarget).toList(),
                            "the pages are asked for in order"));
        }

        @Test
        @DisplayName("stops at 20 pages and reports the read as not complete")
        void stopsAtTheCap() {
            // more issues than the 20 pages of 500 the client reads
            int total = SonarClient.MAX_PAGES * SonarClient.PAGE_SIZE + Generators.integers(1, 1000).next();
            var issues = new ArrayList<SonarIssue>();
            for (int p = 1; p <= SonarClient.MAX_PAGES + 1; p++) {
                var issue = issue(true);
                if (p <= SonarClient.MAX_PAGES) {
                    issues.add(issue);
                }
                onPage(p, page(p, total, List.of(issue)));
            }

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the capped read",
                    () -> assertEquals(Result.Outcome.OK, result.outcome(), "a capped read is still OK"),
                    () -> assertFalse(result.complete(), "a read stopped at the cap is incomplete"),
                    () -> assertEquals(issues, result.value(), "the issues of the 20 pages are kept"),
                    () -> assertEquals(SonarClient.MAX_PAGES, requests.size(), "no page past the cap is asked"));
        }

        @Test
        @DisplayName("reports the failure of a later page")
        void failsOnALaterPage() {
            int total = 500 + Generators.integers(1, 500).next();
            onPage(1, page(1, total, List.of(issue(true))));
            onPage(2, json(500, "{}"));

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the failed later page",
                    () -> assertEquals(Result.Outcome.FAILED, result.outcome(), "a failed later page fails the read"),
                    () -> assertNull(result.value(), "a failed read carries no issues"));
        }
    }

    @Nested
    @DisplayName("outcomes")
    class Outcomes {

        ModuleDispatcherElement getModuleDispatcher() {
            return dispatcher();
        }

        @ParameterizedTest(name = "HTTP {0} is {1}")
        @CsvSource({"401, AUTH_FAILED", "403, PERMISSION_DENIED", "404, NOT_FOUND"})
        @DisplayName("maps a refused credential and a missing project by status (contract: status codes)")
        void mapsByStatus(int status, Result.Outcome outcome) {
            var message = word();
            onPage(1, json(status, Json.write(Map.of("errors", List.of(Map.of("msg", message))))));

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the refused read",
                    () -> assertEquals(outcome, result.outcome(), "the status maps to its outcome"),
                    () -> assertNull(result.value(), "a refused read carries no issues"));
        }

        @Test
        @DisplayName("reports a 400 with the error messages of Sonar as rejected")
        void rejectsWithSonarMessages() {
            var first = word();
            var second = word();
            onPage(1, json(400, Json.write(Map.of("errors", List.of(Map.of("msg", first), Map.of("msg", second))))));

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the rejected read",
                    () -> assertEquals(Result.Outcome.REJECTED, result.outcome(), "a reasoned 400 is rejected"),
                    () -> assertEquals("HTTP 400: " + first + "; " + second, result.detail(),
                            "the detail carries Sonar's messages"));
        }

        @Test
        @DisplayName("reports a 400 without error messages as failed")
        void failsWithoutSonarMessages() {
            onPage(1, json(400, Json.write(Map.of("errors", List.of(Map.of("msg", " "))))));

            assertEquals(Result.Outcome.FAILED, client.openIssues(projectKey, pullRequest).outcome(),
                    "a 400 without a reason is a failure");
        }

        @Test
        @DisplayName("reports a 429 as rate limited until the Retry-After of the answer")
        void rateLimited() {
            long seconds = Generators.integers(60, 3600).next();
            onPage(1, new MockResponse.Builder().code(429).addHeader("Retry-After", String.valueOf(seconds)).build());
            var before = Instant.now();

            var result = client.openIssues(projectKey, pullRequest);

            var resetAt = result.resetAt();
            assertAll("the rate limit",
                    () -> assertEquals(Result.Outcome.RATE_LIMITED, result.outcome(), "a 429 is a rate limit"),
                    () -> assertFalse(resetAt.isBefore(before.plusSeconds(seconds)),
                            "the reset is Retry-After from now"),
                    () -> assertTrue(resetAt.isBefore(Instant.now().plusSeconds(seconds + 1)),
                            "the reset is not later than Retry-After"));
        }

        @Test
        @DisplayName("reports a server error as failed")
        void serverError() {
            onPage(1, json(500, "{}"));

            assertEquals(Result.Outcome.FAILED, client.openIssues(projectKey, pullRequest).outcome(),
                    "a server error is a failure");
        }

        @Test
        @DisplayName("reports a body that is no JSON as failed without quoting it")
        void noJson() {
            var body = "<html>" + word() + "</html>";
            onPage(1, json(200, body));

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the unreadable answer",
                    () -> assertEquals(Result.Outcome.FAILED, result.outcome(), "an answer of no JSON is a failure"),
                    () -> assertEquals("unreadable issue page 1: no JSON", result.detail(),
                            "the detail names the page, not the body"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.provider.sonar.SonarClientTest#unreadablePages")
        @DisplayName("reports an answer it cannot read as failed (the field names are the wire format)")
        void unreadable(String what, String body) {
            onPage(1, json(200, body));

            var result = client.openIssues(projectKey, pullRequest);

            assertAll("the unreadable page " + what,
                    () -> assertEquals(Result.Outcome.FAILED, result.outcome(), "an unreadable page is a failure"),
                    () -> assertTrue(result.detail().startsWith("unreadable issue page 1: "),
                            "the detail names the unreadable page"));
        }
    }

    /** @return answers of the issue search the client cannot read, each named by what makes it unreadable */
    static Stream<Arguments> unreadablePages() {
        var paging = Map.of("pageIndex", 1, "pageSize", 500, "total", 1);
        Map<String, Object> issue = Map.of("key", word(), "rule", word(), "severity", "MAJOR", "type", "BUG",
                "component", word(), "message", word());
        return Stream.of(
                Arguments.of("the JSON null literal", "null"),
                Arguments.of("no paging", Json.write(Map.of("issues", List.of()))),
                Arguments.of("a paging out of range",
                        Json.write(Map.of("paging", Map.of("pageIndex", 0, "pageSize", 500, "total", 1),
                                "issues", List.of()))),
                Arguments.of("a page size that is no whole number",
                        "{\"paging\":{\"pageIndex\":1,\"pageSize\":1.5,\"total\":1},\"issues\":[]}"),
                Arguments.of("no issues array", Json.write(Map.of("paging", paging, "issues", Map.of()))),
                Arguments.of("an issue that is no object",
                        Json.write(Map.of("paging", paging, "issues", List.of(word())))),
                Arguments.of("an issue without rule", Json.write(Map.of("paging", paging,
                        "issues", List.of(without(issue, "rule"))))),
                Arguments.of("a line that is no number", Json.write(Map.of("paging", paging,
                        "issues", List.of(with(issue, "line", word()))))),
                Arguments.of("an issue key Sonar could not have issued", Json.write(Map.of("paging", paging,
                        "issues", List.of(with(issue, "key", word() + " " + word()))))));
    }

    private static Map<String, Object> with(Map<String, Object> issue, String field, Object value) {
        var changed = new HashMap<>(issue);
        changed.put(field, value);
        return changed;
    }

    private static Map<String, Object> without(Map<String, Object> issue, String field) {
        var changed = new HashMap<>(issue);
        changed.remove(field);
        return changed;
    }
}
