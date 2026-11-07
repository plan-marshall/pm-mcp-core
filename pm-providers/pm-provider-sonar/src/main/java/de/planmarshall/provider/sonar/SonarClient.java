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

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.provider.http.Endpoint;
import de.planmarshall.provider.http.HttpClient;
import de.planmarshall.provider.http.Json;
import de.planmarshall.provider.http.Response;
import de.planmarshall.provider.http.Result;
import de.planmarshall.provider.http.TokenSource;

/**
 * The issue client of Sonar (SonarQube and SonarCloud) over the shared HTTP base: the one read the later adapter of
 * the code-analysis and findings contracts builds on, the open issues of one pull request (PM-IMPL-1).
 * <p>
 * The read is {@code GET api/issues/search} with the project key (URL-encoded) in {@code projects}, the pull request in
 * {@code pullRequest}, {@code resolved=false}, and the page in {@code p} with {@value #PAGE_SIZE} issues per page in
 * {@code ps}. Sonar sends no {@code Link} header, so the client pages by the {@code paging} object of each answer and
 * reads the next page while {@code paging.pageIndex * paging.pageSize < paging.total}, at most {@value #MAX_PAGES}
 * pages (the result window of Sonar); a read stopped at that cap is an {@link Result.Outcome#OK} result that is not
 * {@link Result#complete() complete}.
 * <p>
 * The token goes with every request as {@code Authorization: Bearer} to the origin of the endpoint alone, through the
 * shared base. A failed answer maps through {@link Result#failed(Response)}, with one addition: a {@code 4xx} other than
 * {@code 401}, {@code 403} and {@code 404} whose body carries the error messages of Sonar ({@code errors[].msg}) is
 * {@link Result.Outcome#REJECTED} with those messages, because the shared mapping reads only {@code message} and
 * {@code error}. An answer whose body cannot be read, or that names an issue key Sonar could not have issued, is
 * {@link Result.Outcome#FAILED}. The client logs nothing and writes nothing.
 *
 * @since 0.1
 */
public final class SonarClient implements AutoCloseable {

    /** The issues per page the client asks for: the maximum Sonar serves. */
    public static final int PAGE_SIZE = 500;

    /** The pages the client reads at most: {@value #PAGE_SIZE} each, the 10 000-result window of Sonar. */
    public static final int MAX_PAGES = 20;

    /** The path of the issue search, relative to the base of the endpoint. */
    static final String ISSUE_SEARCH = "api/issues/search";

    /** The object of an answer that carries its paging. */
    private static final String PAGING = "paging";

    private final HttpClient http;

    /**
     * @param endpoint the Sonar endpoint, e.g. {@code https://sonarcloud.io/}
     * @param tokens   the token source, resolved per request
     */
    public SonarClient(Endpoint endpoint, TokenSource tokens) {
        this.http = new HttpClient(Objects.requireNonNull(endpoint, "endpoint"),
                Objects.requireNonNull(tokens, "tokens"), Map.of("Accept", "application/json"));
    }

    /**
     * Reads the open issues of one pull request, page by page.
     *
     * @param projectKey  the key of the Sonar project
     * @param pullRequest the number of the pull request, at least {@code 1}
     * @return the open issues in the order Sonar sends them; not {@link Result#complete() complete} when the read
     *         stopped at {@value #MAX_PAGES} pages, otherwise the failure of the first page that did not answer
     * @throws NullPointerException     for no project key
     * @throws IllegalArgumentException for a blank project key or a pull request number below {@code 1}
     */
    public Result<List<SonarIssue>> openIssues(String projectKey, int pullRequest) {
        Objects.requireNonNull(projectKey, "projectKey");
        if (projectKey.isBlank()) {
            throw new IllegalArgumentException("A Sonar project key is not blank");
        }
        if (pullRequest < 1) {
            throw new IllegalArgumentException("A pull request number is at least 1");
        }
        var query = ISSUE_SEARCH + "?projects=" + URLEncoder.encode(projectKey, StandardCharsets.UTF_8)
                + "&pullRequest=" + pullRequest + "&resolved=false";
        var issues = new ArrayList<SonarIssue>();
        for (var page = 1; page <= MAX_PAGES; page++) {
            Response response = http.get(query + "&p=" + page + "&ps=" + PAGE_SIZE);
            if (!response.isOk()) {
                return failed(response);
            }
            Page read;
            try {
                read = page(response.body());
            } catch (Json.JsonFormatException _) {
                // The parser's message may quote the body: the detail names the page alone.
                return Result.of(Result.Outcome.FAILED, "unreadable issue page " + page + ": no JSON");
            } catch (IllegalArgumentException e) {
                // The client's own message, or the refusal of SonarIssueKey: neither quotes remote text.
                return Result.of(Result.Outcome.FAILED, "unreadable issue page " + page + ": " + e.getMessage());
            }
            issues.addAll(read.issues());
            if (read.last()) {
                return Result.ok(List.copyOf(issues), true);
            }
        }
        return Result.ok(List.copyOf(issues), false);
    }

    /**
     * Maps a failed answer: a {@code 4xx} other than {@code 401}, {@code 403} and {@code 404} with the error messages
     * of Sonar is {@link Result.Outcome#REJECTED}; every other answer maps through the shared base.
     */
    private static <V> Result<V> failed(Response response) {
        var status = response.status();
        if (response.outcome() == Response.Outcome.HTTP_ERROR && status >= 400 && status < 500 && status != 401
                && status != 403 && status != 404) {
            var messages = errorMessages(response.body());
            if (!messages.isEmpty()) {
                return Result.of(Result.Outcome.REJECTED, "HTTP " + status + ": " + String.join("; ", messages));
            }
        }
        return Result.failed(response);
    }

    /** @return the {@code errors[].msg} of an error body of Sonar; empty when there are none or the body is no JSON */
    private static List<String> errorMessages(String body) {
        var tree = Json.parseOrNull(body).orElse(null);
        return Json.list(tree, "errors").stream()
                .map(error -> Json.string(error, "msg").orElse(""))
                .filter(message -> !message.isBlank())
                .toList();
    }

    /**
     * @throws IllegalArgumentException for a body that is no JSON, a missing or unusable {@code paging}, or an issue
     *                                  that cannot be read
     */
    private static Page page(String body) {
        var tree = Json.parse(body).orElseThrow(() -> new IllegalArgumentException("the answer is null"));
        var pageIndex = paging(tree, "pageIndex");
        var pageSize = paging(tree, "pageSize");
        var total = paging(tree, "total");
        if (pageIndex < 1 || pageSize < 1 || total < 0) {
            throw new IllegalArgumentException("the paging of the answer is out of range");
        }
        if (!(Json.at(tree, "issues").orElse(null) instanceof List<?>)) {
            throw new IllegalArgumentException("the answer holds no issues array");
        }
        var issues = Json.list(tree, "issues").stream().map(SonarClient::issue).toList();
        return new Page(issues, (long) pageIndex * pageSize >= total);
    }

    /**
     * @throws IllegalArgumentException for an issue that is no object, misses a field, carries a line that is no whole
     *                                  number, or names a key {@link SonarIssueKey} refuses
     */
    private static SonarIssue issue(@Nullable Object issue) {
        if (!(issue instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("an issue is no object");
        }
        var line = Json.at(issue, "line").map(value -> whole(value, "line")).orElse(null);
        return SonarIssue.builder()
                .key(new SonarIssueKey(text(issue, "key")))
                .rule(text(issue, "rule"))
                .severity(text(issue, "severity"))
                .type(text(issue, "type"))
                .component(text(issue, "component"))
                .line(line)
                .message(text(issue, "message"))
                .build();
    }

    /**
     * Reads one field of the {@code paging} object of an answer; the absent case is decided where the value is read.
     *
     * @param tree  the tree of the answer
     * @param field the field below {@code paging}
     * @return the whole number the field holds
     * @throws IllegalArgumentException for a field that is missing, no number or no whole number
     */
    private static int paging(Object tree, String field) {
        var name = PAGING + "." + field;
        return Json.at(tree, PAGING, field)
                .map(value -> whole(value, name))
                .orElseThrow(() -> new IllegalArgumentException(name + " is no number"));
    }

    private static String text(Object issue, String field) {
        if (!(Json.at(issue, field).orElse(null) instanceof String value)) {
            throw new IllegalArgumentException("an issue has no text " + field);
        }
        return value;
    }

    /**
     * @throws IllegalArgumentException for a value that is no number or no whole number
     */
    private static int whole(Object value, String field) {
        if (!(value instanceof BigDecimal number)) {
            throw new IllegalArgumentException(field + " is no number");
        }
        try {
            return number.intValueExact();
        } catch (ArithmeticException _) {
            throw new IllegalArgumentException(field + " is no whole number");
        }
    }

    /** Closes the shared HTTP base of the client. */
    @Override
    public void close() {
        http.close();
    }

    /**
     * One page of the issue search.
     *
     * @param issues the issues of the page
     * @param last   whether the paging of the page says no issue follows it
     */
    private record Page(List<SonarIssue> issues, boolean last) {
    }
}
