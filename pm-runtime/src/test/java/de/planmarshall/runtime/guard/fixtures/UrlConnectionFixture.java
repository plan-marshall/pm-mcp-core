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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

/**
 * Control of the outbound-calls guard: a class opening a URL connection and a URL stream, on which rule A must
 * fail once per call.
 */
public final class UrlConnectionFixture {

    private UrlConnectionFixture() {
    }

    /**
     * @throws IOException if the connection fails
     */
    public static URLConnection connection(URI uri) throws IOException {
        return uri.toURL().openConnection();
    }

    /**
     * @throws IOException if the stream fails
     */
    public static InputStream stream(URI uri) throws IOException {
        return uri.toURL().openStream();
    }
}
