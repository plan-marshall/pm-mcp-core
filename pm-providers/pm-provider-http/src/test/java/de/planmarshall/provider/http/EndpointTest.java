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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The API endpoint of a provider. Hosts, path segments, ports and query values that are not the URL grammar under test
 * are generated; the schemes ({@code https}, {@code http}, {@code ftp}), the loopback hosts ({@code 127.0.0.1},
 * {@code localhost}), the default ports and the upper-case spelling in the origin rows are the contract of the
 * same-origin and cleartext rules, so they stay literals and each such test says so in its display name.
 */
@EnableGeneratorController
@DisplayName("Endpoint")
class EndpointTest {

    @Test
    @DisplayName("normalizes the base path and resolves relative and absolute targets")
    void resolves() {
        var host = host();
        var otherHost = host();
        var basePath = segment() + "/" + segment();
        var project = Generators.integers(1, Integer.MAX_VALUE).next();
        var target = segment();
        var query = segment();
        var endpoint = Endpoint.of(URI.create("https://" + host + "/" + basePath));

        assertEquals(URI.create("https://" + host + "/" + basePath + "/"), endpoint.baseUri(),
                "the base path gains its trailing slash");
        assertEquals(URI.create("https://" + host + "/" + basePath + "/" + target + "/" + project + "?q=" + query),
                endpoint.resolve("/" + target + "/" + project + "?q=" + query),
                "a leading-slash path resolves below the base");
        assertEquals(URI.create("https://" + otherHost + "/" + target), endpoint.resolve("https://" + otherHost + "/"
                + target), "an absolute target is returned unchanged");
        assertFalse(endpoint.cleartextLoopback(), "an https endpoint is not cleartext loopback");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "https://api.github.com/x, true",
            "https://API.GITHUB.COM:443/x, true",
            "https://api.github.com:444/x, false",
            "http://api.github.com/x, false",
            "https://uploads.github.com/x, false",
            "/relative, false"
    })
    @DisplayName("compares scheme, host and effective port (URLs are the origin grammar)")
    void sameOrigin(String uri, boolean same) {
        assertEquals(same, Endpoint.of(URI.create("https://api.github.com")).sameOrigin(URI.create(uri)),
                "the origin rule decides the row's verdict");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://api.github.com/", "ftp://localhost/", "http://10.0.0.1:8080/"})
    @DisplayName("refuses cleartext except on loopback (URLs are the scheme grammar)")
    void httpsOnly(String uri) {
        var base = URI.create(uri);

        assertThrows(IllegalArgumentException.class, () -> Endpoint.of(base),
                "a cleartext base off loopback is refused");
    }

    @Test
    @DisplayName("refuses a generated cleartext host that is not loopback (the http scheme is the contract)")
    void httpsOnlyGeneratedHost() {
        var base = URI.create("http://" + host() + "/" + segment());

        assertThrows(IllegalArgumentException.class, () -> Endpoint.of(base),
                "a cleartext base off loopback is refused");
    }

    @Test
    @DisplayName("admits cleartext on loopback for local fakes (loopback hosts and port 80 are the contract)")
    void loopback() {
        var port = Generators.integers(1024, 65535).next();
        var path = segment();

        assertTrue(Endpoint.of(URI.create("http://127.0.0.1:" + port + "/")).cleartextLoopback(),
                "an http loopback endpoint is cleartext");
        assertTrue(Endpoint.of(URI.create("http://localhost/")).sameOrigin(URI.create("http://localhost:80/" + path)),
                "the default http port equals the explicit 80");
    }

    private static String host() {
        return segment() + ".example.test";
    }

    private static String segment() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }
}
