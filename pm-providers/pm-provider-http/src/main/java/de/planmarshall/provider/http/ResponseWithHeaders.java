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

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The response of one provider request together with the headers it carried. The headers are read where an answer
 * states a fact outside its body, for example the scopes and the expiry of a GitHub personal access token.
 *
 * @param response the response
 * @param headers  the response headers, by lowercase name
 * @since 0.1
 */
public record ResponseWithHeaders(Response response, Map<String, String> headers) {

    /**
     * @param response the response
     * @param headers  the headers
     * @throws NullPointerException if a component is {@code null}
     */
    public ResponseWithHeaders {
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(headers, "headers");
        var lowered = new LinkedHashMap<String, String>();
        headers.forEach((name, value) -> lowered.put(name.toLowerCase(Locale.ROOT), value));
        headers = Map.copyOf(lowered);
    }

    /**
     * @param name a header name, matched case-insensitively
     * @return the header's value; empty if the response did not carry it
     */
    public Optional<String> header(String name) {
        return Optional.ofNullable(headers.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * @return whether the response is {@link Response#isOk()}
     */
    public boolean isOk() {
        return response.isOk();
    }
}
