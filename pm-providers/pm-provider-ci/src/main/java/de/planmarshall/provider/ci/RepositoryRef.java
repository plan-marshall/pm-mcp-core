/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci;

import de.planmarshall.core.findings.FindingSourceKey;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import lombok.Builder;

/**
 * The repository a CI operation addresses: its forge, its web origin and its owner (or, on GitLab, its group
 * path) and name.
 * <p>
 * The origin is the forge's web origin, the one the operator's credential entry records, and it is the origin a
 * provider is bound to: an issue on another origin is refused before anything is sent. The forge is the provider
 * id and the credential key, and it equals the derived fact {@code ci_provider}.
 *
 * @param forge      the provider id ({@code github}, {@code gitlab} or {@code forgejo})
 * @param origin     the forge's web origin, without a path: {@code https://github.com}
 * @param ownerOrPath the owner of a GitHub or Forgejo repository, or the group path of a GitLab project
 * @param name       the repository or project name
 * @since 0.1
 */
@Builder
public record RepositoryRef(String forge, String origin, String ownerOrPath, String name) {

    /** The longest path a group path may have. */
    private static final int MAX_PATH_LENGTH = 2048;
    /** The longest one path segment may have. */
    private static final int MAX_SEGMENT_LENGTH = 255;

    /**
     * @param forge       the provider id
     * @param origin      the web origin without a path
     * @param ownerOrPath the owner or group path
     * @param name        the repository or project name
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the forge does not match the provider id pattern, the origin is not an
     *                                  https origin without a path, or a path segment is empty, {@code .} or
     *                                  {@code ..}
     */
    public RepositoryRef {
        Objects.requireNonNull(forge, "forge");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(ownerOrPath, "ownerOrPath");
        Objects.requireNonNull(name, "name");
        if (!FindingSourceKey.isProviderId(forge)) {
            throw new IllegalArgumentException("the forge '%s' does not match the provider id pattern".formatted(forge));
        }
        origin = originOf(origin);
        if ("gitlab".equals(forge) ? !isSegmentPath(ownerOrPath) : !isSegment(ownerOrPath)) {
            throw new IllegalArgumentException("the owner or group path '%s' is not a path of plain segments"
                    .formatted(ownerOrPath));
        }
        if (!isSegment(name)) {
            throw new IllegalArgumentException("the repository name '%s' is not one plain segment".formatted(name));
        }
    }

    /**
     * @param origin a forge web origin
     * @return the origin normalised: its host in lower case, its default port dropped, no path
     * @throws IllegalArgumentException if the value is not an https origin without a path
     */
    public static String normaliseOrigin(String origin) {
        return originOf(origin);
    }

    private static String originOf(String origin) {
        URI uri;
        try {
            uri = URI.create(origin);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("the origin '%s' is not a URL".formatted(origin), e);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                                || uri.getQuery() != null || uri.getFragment() != null
                                || !(uri.getPath() == null || uri.getPath().isEmpty() || "/".equals(uri.getPath()))) {
            throw new IllegalArgumentException("the origin '%s' is not an https origin without a path".formatted(origin));
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean defaultPort = uri.getPort() == -1 || uri.getPort() == 443;
        return defaultPort ? "https://" + host : "https://" + host + ":" + uri.getPort();
    }

    private static boolean isSegmentPath(String path) {
        if (path.isEmpty() || path.length() > MAX_PATH_LENGTH || path.startsWith("/") || path.endsWith("/")) {
            return false;
        }
        for (String segment : path.split("/", -1)) {
            if (!isSegment(segment)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSegment(String segment) {
        if (segment.isEmpty() || segment.length() > MAX_SEGMENT_LENGTH || ".".equals(segment)
                || "..".equals(segment)) {
            return false;
        }
        return segment.chars().allMatch(character -> Character.isLetterOrDigit(character)
                || character == '.' || character == '-' || character == '_');
    }
}
