/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The JSON tree of the provider clients and the mapping of failed responses. Free-form string and number values, the
 * provider messages and the transport details are generated. What stays literal is contract and each test says so:
 * the JSON syntax ({@code null}, {@code true}, {@code false}, braces, an unterminated object, an HTML page), the
 * punctuation, spaces, quotes and escapes a round trip must preserve, the field names of the tree, the reason keys
 * {@code message} and {@code error}, the HTTP status codes and the {@code "HTTP <status>"} detail format.
 */
@EnableGeneratorController
@DisplayName("Json")
class JsonTest {

    @Test
    @DisplayName("round-trips a tree and navigates it (escapes, quotes, spaces and field names are the contract)")
    void roundTrip() {
        var first = word();
        var second = word();
        var quoted = word();
        var tail = word();
        var element = word();
        var text = first + "\n" + second + " \"" + quoted + "\"; " + tail + "\\";
        var escaped = first + "\\n" + second + " \\\"" + quoted + "\\\"; " + tail + "\\\\";
        long n = Generators.longs().next();
        int i = Generators.integers().next();
        var d = BigDecimal.valueOf(Generators.integers(0, 1_000_000).next(), 1);
        var o = Duration.ofSeconds(Generators.integers(1, 1_000_000).next());
        var tree = new LinkedHashMap<String, Object>();
        tree.put("s", text);
        tree.put("n", n);
        tree.put("i", i);
        tree.put("d", d);
        tree.put("b", true);
        tree.put("z", null);
        tree.put("l", Arrays.asList(element, null, Map.of("k", false)));
        tree.put("o", o);

        String json = Json.write(tree);
        Object parsed = Json.parse(json).orElseThrow();

        assertEquals("{\"s\":\"" + escaped + "\",\"n\":" + n + ",\"i\":" + i + ",\"d\":" + d
                + ",\"b\":true,\"z\":null,\"l\":[\"" + element + "\",null,{\"k\":false}],\"o\":\"" + o + "\"}", json,
                "the tree is written as compact escaped JSON");
        assertEquals(Optional.of(text), Json.string(parsed, "s"), "the escaped string reads back unchanged");
        assertEquals(Optional.of(String.valueOf(n)), Json.string(parsed, "n"), "a number reads back in its JSON form");
        assertTrue(Json.bool(parsed, "b"), "the true literal reads back as true");
        assertFalse(Json.bool(parsed, "missing"), "an absent key reads back as false");
        assertEquals(3, Json.list(parsed, "l").size(), "the array keeps all three of its elements");
        assertNull(Json.list(parsed, "l").get(1), "the null element of the array is kept");
        assertEquals(List.of(), Json.list(parsed, "s"), "a string read as a list yields no elements");
        assertEquals(Optional.empty(), Json.at(parsed, "s", "deeper"), "a path through a string yields nothing");
        assertEquals(Optional.empty(), Json.at(parsed, "z"), "the null value yields no tree at its key");
    }

    @Test
    @DisplayName("rejects text that is not JSON (the inputs are JSON syntax contract)")
    void rejects() {
        assertThrows(Json.JsonFormatException.class, () -> Json.parse(""), "an empty document is not JSON");
        assertThrows(Json.JsonFormatException.class, () -> Json.parse("{\"a\":"),
                "an unterminated object is not JSON");
        assertTrue(Json.parseOrNull("<html>").isEmpty(), "Text that is not JSON yields no tree");
        assertTrue(Json.parseOrNull("null").isEmpty(), "The JSON null literal yields no tree");
    }

    @Test
    @DisplayName("returns no tree for the JSON null literal (the null literal is the contract)")
    void parsesNullLiteralAsEmpty() {
        assertEquals(Optional.empty(), Json.parse("null"), "The JSON null literal yields no tree");
    }

    @Test
    @DisplayName("maps failed responses to contract outcomes (status codes and reason keys are the contract)")
    void mapsOutcomes() {
        var reason = phrase();
        var refused = phrase();
        var transport = word();
        var reset = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());

        assertEquals(Result.Outcome.AUTH_FAILED, failed(401, "").outcome(), "a 401 is a refused credential");
        assertEquals(Result.Outcome.PERMISSION_DENIED, failed(403, "").outcome(), "a 403 is a missing permission");
        assertEquals(Result.Outcome.NOT_FOUND, failed(404, "").outcome(), "a 404 is an absent entity");
        assertEquals(Result.Outcome.REJECTED, failed(409, message(reason)).outcome(),
                "a 409 with a reason is a provider refusal");
        assertEquals("HTTP 409: " + reason, failed(409, message(reason)).detail(),
                "the detail carries the provider's reason");
        assertEquals(Result.Outcome.FAILED, failed(500, word()).outcome(), "a 500 with a text body is a failure");
        assertEquals(Result.Outcome.FAILED, failed(500, message(reason)).outcome(),
                "a 500 with a reason is still a failure");
        assertEquals(Result.Outcome.FAILED,
                Result.failed(Response.failure(Response.Outcome.REFUSED, refused)).outcome(),
                "a request refused before sending is a failure");
        assertEquals(Result.Outcome.FAILED,
                Result.failed(Response.failure(Response.Outcome.TRANSPORT_FAILED, transport)).outcome(),
                "a transport failure maps to a failure");
        var limited = Result.failed(new Response(Response.Outcome.RATE_LIMITED, 429, "", Optional.empty(),
                Optional.empty(), Optional.of(reset), phrase()));
        assertEquals(Result.Outcome.RATE_LIMITED, limited.outcome(), "a rate-limited response stays rate limited");
        assertEquals(Optional.of(reset), limited.resetAt(), "the rate limit keeps its reset instant");
        assertFalse(limited.isOk(), "a rate-limited result is not OK");
        assertTrue(Result.ok(word(), false).isOk(), "an OK result of a partial read is OK");
    }

    @ParameterizedTest(name = "{0} {1}")
    @CsvSource(delimiter = '|', value = {
            "400|{\"message\":\"%s\"}|REJECTED|true",
            "400|{\"error\":\"%s\"}|REJECTED|true",
            "409|{\"message\":\"%s\"}|REJECTED|true",
            "409|''|REJECTED|false",
            "422|{\"message\":\"%s\"}|REJECTED|true",
            "405|{\"message\":\"%s\"}|REJECTED|true",
            "410|{\"message\":\"%s\"}|REJECTED|true",
            "400|''|FAILED|false",
            "400|<html>%s</html>|FAILED|false",
            "400|{\"message\":\" \"}|FAILED|false",
            "400|{\"message\":null}|FAILED|false",
            "401|{\"message\":\"%s\"}|AUTH_FAILED|true",
            "403|{\"message\":\"%s\"}|PERMISSION_DENIED|true",
            "404|{\"message\":\"%s\"}|NOT_FOUND|true",
            "502|{\"message\":\"%s\"}|FAILED|true",
            "302|{\"message\":\"%s\"}|FAILED|true"
    })
    @DisplayName("keeps a provider refusal (REJECTED) apart from a credential refusal and from a failure "
            + "(status codes, body shapes and the detail format are the contract)")
    void separatesRefusals(int status, String bodyShape, Result.Outcome outcome, boolean reasoned) {
        var reason = phrase();
        var result = failed(status, bodyShape.formatted(reason));

        assertEquals(outcome, result.outcome(), "the status and body shape decide the outcome");
        assertEquals(reasoned ? "HTTP " + status + ": " + reason : "HTTP " + status, result.detail(),
                "the detail is the status plus any provider reason");
        assertTrue(result.value().isEmpty(), "a failed result carries no value");
    }

    private static Result<Object> failed(int status, String body) {
        return Result.failed(new Response(Response.Outcome.HTTP_ERROR, status, body, Optional.empty(),
                Optional.empty(), Optional.empty(), "HTTP " + status));
    }

    /** The GitHub and GitLab error body with the provider's reason under {@code message}: a wire contract. */
    private static String message(String reason) {
        return "{\"message\":\"" + reason + "\"}";
    }

    private static String word() {
        return Generators.letterStrings(1, 12).next();
    }

    private static String phrase() {
        return word() + " " + word();
    }
}
