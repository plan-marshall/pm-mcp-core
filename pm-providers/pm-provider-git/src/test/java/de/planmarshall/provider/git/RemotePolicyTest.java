/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The remote policy of the git transport. Every remote URL below is a literal by design: the scheme, host, port and
 * spelling of each is the remote-policy grammar under test, and each row states the verdict that grammar assigns it.
 */
@EnableGeneratorController
@DisplayName("RemotePolicy")
class RemotePolicyTest {

    /** The https origin of the credential entry: the contract the policy admits. */
    private static final URI ORIGIN = URI.create("https://github.com");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "https://github.com/cuioss/x.git, true",
            "https://GITHUB.com:443/cuioss/x.git, true",
            "https://github.com:8443/cuioss/x.git, false",
            "https://gitlab.com/cuioss/x.git, false",
            "http://github.com/cuioss/x.git, false",
            "git@github.com:cuioss/x.git, false",
            "ssh://git@github.com/cuioss/x.git, false",
            "file:///tmp/x.git, false"
    })
    @DisplayName("admits only the https origin of the credential entry (URLs are the policy grammar)")
    void httpsOrigin(String remote, boolean permitted) throws Exception {
        var policy = RemotePolicy.httpsOrigin(ORIGIN);

        assertEquals(permitted, policy.permits(new URIish(remote)), "the https policy decides the remote's URL");
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "file:///tmp/x.git, true",
            "/tmp/x.git, true",
            "https://github.com/cuioss/x.git, false",
            "git@github.com:cuioss/x.git, false"
    })
    @DisplayName("admits local repositories only (URLs are the policy grammar)")
    void localOnly(String remote, boolean permitted) throws Exception {
        assertEquals(permitted, RemotePolicy.localOnly().permits(new URIish(remote)),
                "the local policy decides the remote's URL");
    }

    @Test
    @DisplayName("rejects a non-https origin (the http scheme is the contract)")
    void rejectsOrigin() {
        var origin = URI.create("http://github.com");

        assertThrows(IllegalArgumentException.class, () -> RemotePolicy.httpsOrigin(origin),
                "an origin without https is refused");
    }
}
