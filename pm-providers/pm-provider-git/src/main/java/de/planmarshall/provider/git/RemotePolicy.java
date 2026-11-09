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

import java.net.URI;
import java.util.Locale;
import org.eclipse.jgit.transport.URIish;
import org.jspecify.annotations.Nullable;

/**
 * Which remote URLs the native transport may contact.
 * <p>
 * The native transport speaks HTTPS only, and only to the origin of the credential entry
 * ({@link #httpsOrigin(URI)}). {@link #localOnly()} admits repositories on the local disk
 * ({@code file://} or a plain path), which involve no network and no credential.
 *
 * @param origin the permitted HTTPS origin; {@code null} for local repositories only
 * @since 0.1
 */
public record RemotePolicy(@Nullable URI origin) {

    /**
     * @param origin the HTTPS origin, e.g. {@code https://github.com}
     * @return a policy admitting exactly that origin
     * @throws IllegalArgumentException if the origin is not an absolute {@code https} URI
     */
    public static RemotePolicy httpsOrigin(URI origin) {
        if (!"https".equalsIgnoreCase(origin.getScheme()) || origin.getHost() == null) {
            throw new IllegalArgumentException("not an https origin: " + origin);
        }
        return new RemotePolicy(origin);
    }

    /**
     * @return a policy admitting local repositories only
     */
    public static RemotePolicy localOnly() {
        return new RemotePolicy(null);
    }

    /**
     * @param uri the effective remote URL (after {@code insteadOf} rewriting)
     * @return whether the transport may contact it
     */
    public boolean permits(URIish uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (origin == null) {
            return uri.getHost() == null && (scheme.isEmpty() || "file".equals(scheme));
        }
        URI allowed = origin;
        return "https".equals(scheme) && allowed.getHost().equalsIgnoreCase(uri.getHost())
                && port(allowed.getPort()) == port(uri.getPort());
    }

    private static int port(int port) {
        return port == -1 ? 443 : port;
    }
}
