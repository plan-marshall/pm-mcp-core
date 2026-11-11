/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.guard.fixtures;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

/**
 * Positive control of the outbound-calls guard: a class that uses only data types of the HTTP client's API — a URI,
 * a request, a body handler and the timeout exception. None of them opens a connection, so rule A must hold on it.
 */
public final class DataTypesFixture {

    private DataTypesFixture() {
    }

    /** @return the address a request names */
    public static URI uri() {
        return URI.create("https://example.invalid/resource");
    }

    /** @return a request that opens nothing */
    public static HttpRequest request(URI uri) {
        return HttpRequest.newBuilder(uri).GET().build();
    }

    /** @return the handler that reads an answer as text */
    public static HttpResponse.BodyHandler<String> handler() {
        return HttpResponse.BodyHandlers.ofString();
    }

    /** @return the type name of a timeout, never a connection */
    public static String timeoutType(HttpTimeoutException failure) {
        return failure.getClass().getSimpleName();
    }
}
