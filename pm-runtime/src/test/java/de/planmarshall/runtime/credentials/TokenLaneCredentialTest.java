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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The Token Lane entry a remote endpoint reads (Client Profiles § Remote Form): the secret is sent only to the origin of
 * the entry's {@code url}, and the entry refuses a secret or an origin that could not be sent safely.
 * <p>
 * The origin strings are the contract of the rule; the secret is generated.
 */
@EnableGeneratorController
@DisplayName("Token Lane credential")
class TokenLaneCredentialTest {

    private final String secret = Generators.letterStrings(8, 24).next();

    @Test
    @DisplayName("the origin is the lower-case scheme, the host and the port where one is named")
    void origin() {
        var noPort = new TokenLaneCredential.Entry(secret, URI.create("HTTPS://example.com/entry"));
        var withPort = new TokenLaneCredential.Entry(secret, URI.create("https://example.com:8443/entry"));

        assertAll(
                () -> assertEquals("https://example.com", noPort.origin(), "no port stays unnamed"),
                () -> assertEquals("https://example.com:8443", withPort.origin(), "a named port stays"),
                () -> assertEquals("https://example.com",
                        TokenLaneCredential.origin(URI.create("https://example.com")),
                        "the static origin answers the same rule"));
    }

    @Test
    @DisplayName("an entry refuses an empty secret and a URL that is not https with a host")
    void refusals() {
        var emptySecret = assertThrows(IllegalArgumentException.class,
                () -> new TokenLaneCredential.Entry("", URI.create("https://example.com")),
                "an empty secret is refused");
        var plainHttp = assertThrows(IllegalArgumentException.class,
                () -> new TokenLaneCredential.Entry(secret, URI.create("http://example.com")),
                "a URL that is not https is refused");

        assertAll(
                () -> assertEquals("A credential entry holds a secret", emptySecret.getMessage(),
                        "the refusal names the empty secret"),
                () -> assertEquals("A Token Lane entry names an https URL with a host", plainHttp.getMessage(),
                        "the refusal names the origin rule"));
    }

    @Test
    @DisplayName("the seam answers an absent entry")
    void entryOfAKey() {
        TokenLaneCredential credential = key -> Optional.empty();

        assertEquals(Optional.empty(), credential.entry("github"),
                "the seam answers an absent entry");
    }
}
