/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The Token Lane credential entry a remote endpoint reads (Client Profiles § Remote Form, Credentials &amp; Enrolment §
 * Provider Credentials): the secret is read at the moment of a request, never cached, and sent only to the origin of the
 * entry's {@code url}.
 * <p>
 * The entry is operator-owned: the operator enters it with its {@code url}, and no project configuration adds one or
 * changes it. The reading is a seam: the operator channel implements it over the credential store the operator recorded,
 * and this package only consumes it.
 *
 * @since 0.1
 */
public interface TokenLaneCredential {

    /**
     * @param credentialKey the key of the Token Lane entry
     * @return the entry, or empty when the store holds none
     */
    Optional<Entry> entry(String credentialKey);

    /**
     * @param url a URL of a credential entry
     * @return its origin: the lower-case scheme, the host and the port where one is named
     */
    static String origin(URI url) {
        Objects.requireNonNull(url, "url");
        var scheme = url.getScheme() == null ? "" : url.getScheme().toLowerCase(Locale.ROOT);
        return scheme + "://" + url.getHost() + (url.getPort() == -1 ? "" : ":" + url.getPort());
    }

    /**
     * One Token Lane entry the remote form reads.
     *
     * @param secret the secret of the entry
     * @param url    the origin the operator allowed the secret for
     */
    record Entry(String secret, URI url) {

        /**
         * @throws NullPointerException     if a value is {@code null}
         * @throws IllegalArgumentException if the secret is empty, or the URL is not an {@code https} URL with a host
         */
        public Entry {
            Objects.requireNonNull(secret, "secret");
            if (secret.isEmpty()) {
                throw new IllegalArgumentException("A credential entry holds a secret");
            }
            Objects.requireNonNull(url, "url");
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getHost().isEmpty()) {
                throw new IllegalArgumentException("A Token Lane entry names an https URL with a host");
            }
        }

        /** @return the origin of the entry's URL, the only place its secret is sent */
        public String origin() {
            return TokenLaneCredential.origin(url);
        }
    }
}
