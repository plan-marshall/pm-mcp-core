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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The issue or pull-request URL of a source, resolved to its forge, repository and number.
 * <p>
 * Each forge has its own URL grammar: GitHub and Forgejo {@code /<owner>/<name>/issues/<n>} (a pull request
 * {@code /<owner>/<name>/pull/<n>} on GitHub, {@code /<owner>/<name>/pulls/<n>} on Forgejo), GitLab
 * {@code /<group>/…/<project>/-/issues/<n>} (a merge request {@code /-/merge_requests/<n>}). A host-to-forge table,
 * passed by the caller or the {@link #BUILT_IN_HOSTS built-in one}, selects the forge and so the grammar. A URL on a host
 * the table does not name is the outcome {@link Outcome#UNKNOWN_FORGE} as a value; a malformed or hostile URL is
 * {@link Outcome#INVALID}. Neither is thrown.
 *
 * @param forge      the provider id of the forge
 * @param repository the repository the URL names
 * @param number     the issue or pull-request number, at least 1
 * @param kind       whether the URL names an issue or a pull request
 * @since 0.1
 */
@Builder
public record ForgeUrl(String forge, RepositoryRef repository, int number, Kind kind) {

    /** The forges a URL can name. */
    private static final String GITLAB = "gitlab";
    /** The path segment GitLab puts before the operation. */
    private static final String GITLAB_SEPARATOR = "-";
    /** The longest URL path that is considered. */
    private static final int MAX_PATH_LENGTH = 2048;
    /** The most path segments that are considered. */
    private static final int MAX_SEGMENTS = 12;
    /** The longest one path segment that is considered. */
    private static final int MAX_SEGMENT_LENGTH = 255;
    /** The most digits an issue or pull-request number has. */
    private static final int MAX_NUMBER_DIGITS = 9;

    /** The host-to-forge table of the well-known public forges. */
    public static final Map<String, String> BUILT_IN_HOSTS = Map.of(
                            "github.com", "github",
                            "gitlab.com", GITLAB,
                            "codeberg.org", "forgejo");

    /** Whether the URL names an issue or a pull request. */
    public enum Kind {
        /** An issue. */
        ISSUE,
        /** A pull request. */
        PULL_REQUEST
    }

    /** The outcome of resolving a URL. */
    public enum Outcome {
        /** The URL is a known forge's issue or pull-request URL and was resolved. */
        RESOLVED,
        /** The URL's host is not in the host-to-forge table. */
        UNKNOWN_FORGE,
        /** The URL is malformed or hostile. */
        INVALID
    }

    /**
     * The outcome of resolving one URL: the resolved value, or the reason it was not resolved.
     *
     * @param outcome  the outcome
     * @param resolved the resolved URL; {@code null} exactly when the outcome is not {@link Outcome#RESOLVED}
     * @param detail   the host that is unknown, the reason a URL is invalid, or empty
     */
    public record Resolution(Outcome outcome, @Nullable ForgeUrl resolved, String detail) {

        /**
         * @param outcome  the outcome
         * @param resolved the resolved URL
         * @param detail   the detail
         */
        public Resolution {
            Objects.requireNonNull(outcome, "outcome");
            detail = detail == null ? "" : detail;
            if ((outcome == Outcome.RESOLVED) != (resolved != null)) {
                throw new IllegalArgumentException("a resolution carries a URL exactly when it is resolved");
            }
        }

        /**
         * @return the resolved URL; empty unless the outcome is {@link Outcome#RESOLVED}
         */
        public Optional<ForgeUrl> url() {
            return Optional.ofNullable(resolved);
        }

        /**
         * @param url the resolved URL
         * @return a resolved outcome
         */
        public static Resolution resolved(ForgeUrl url) {
            return new Resolution(Outcome.RESOLVED, url, "");
        }

        /**
         * @param host the host the table does not name
         * @return an unknown-forge outcome
         */
        public static Resolution unknownForge(String host) {
            return new Resolution(Outcome.UNKNOWN_FORGE, null, "unknown_forge: " + host);
        }

        /**
         * @param reason the reason the URL is invalid
         * @return an invalid outcome
         */
        public static Resolution invalid(String reason) {
            return new Resolution(Outcome.INVALID, null, reason);
        }
    }

    /**
     * @param forge      the provider id
     * @param repository the repository
     * @param number     the issue or pull-request number
     * @param kind       the kind of source
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the forge does not match the provider id pattern, the number is below 1, or
     *                                  the repository names another forge
     */
    public ForgeUrl {
        Objects.requireNonNull(forge, "forge");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(kind, "kind");
        if (!FindingSourceKey.isProviderId(forge)) {
            throw new IllegalArgumentException("the forge '%s' does not match the provider id pattern".formatted(forge));
        }
        if (number < 1) {
            throw new IllegalArgumentException("a source number is at least 1, was " + number);
        }
        if (!forge.equals(repository.forge())) {
            throw new IllegalArgumentException("the repository names the forge '%s', not '%s'"
                                    .formatted(repository.forge(), forge));
        }
    }

    /**
     * Resolves a URL against the {@link #BUILT_IN_HOSTS built-in host table}.
     *
     * @param url the issue or pull-request URL
     * @return the resolution
     */
    public static Resolution resolve(String url) {
        return resolve(url, BUILT_IN_HOSTS);
    }

    /**
     * Resolves a URL against a host-to-forge table.
     *
     * @param url         the issue or pull-request URL
     * @param hostToForge the host-to-forge table; a host is matched case-insensitively
     * @return the resolution
     */
    public static Resolution resolve(String url, Map<String, String> hostToForge) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(hostToForge, "hostToForge");
        URI uri;
        try {
            uri = URI.create(url.strip());
        } catch (IllegalArgumentException e) {
            return Resolution.invalid("not a URL: " + e.getMessage());
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.isOpaque() || uri.getHost() == null
                || uri.getUserInfo() != null) {
            return Resolution.invalid("not an https URL without user information");
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith("/") || path.contains("\\") || path.length() > MAX_PATH_LENGTH) {
            return Resolution.invalid("the path is not a plain URL path");
        }
        List<String> segments = segments(path.substring(1));
        if (segments == null) {
            return Resolution.invalid("the path has an empty, dotted or over-long segment");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        String forge = forgeOf(hostToForge, host);
        if (forge == null) {
            return Resolution.unknownForge(host);
        }
        String origin = uri.getPort() == -1 ? "https://" + host : "https://" + host + ":" + uri.getPort();
        return GITLAB.equals(forge) ? resolveGitLab(forge, origin, segments)
                                : resolveGitHub(forge, origin, segments);
    }

    private static String forgeOf(Map<String, String> hostToForge, String host) {
        for (Map.Entry<String, String> entry : hostToForge.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(host)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** @return the segments without a trailing empty one, or {@code null} when one is unusable */
    private static List<String> segments(String path) {
        List<String> segments = new ArrayList<>(List.of(path.split("/", -1)));
        if (!segments.isEmpty() && segments.getLast().isEmpty()) {
            segments.removeLast();
        }
        if (segments.isEmpty() || segments.size() > MAX_SEGMENTS) {
            return null;
        }
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)
                    || segment.length() > MAX_SEGMENT_LENGTH) {
                return null;
            }
        }
        return segments;
    }

    private static Resolution resolveGitHub(String forge, String origin, List<String> segments) {
        if (segments.size() != 4) {
            return Resolution.invalid("a GitHub or Forgejo source URL has four path segments");
        }
        Kind kind = switch (segments.get(2)) {
            case "issues" -> Kind.ISSUE;
            case "pull" -> Kind.PULL_REQUEST;
            case "pulls" -> "github".equals(forge) ? null : Kind.PULL_REQUEST;
            default -> null;
        };
        if (kind == null) {
            return Resolution.invalid("the path is neither an issue nor a pull-request path");
        }
        Optional<Integer> number = number(segments.get(3));
        if (number.isEmpty()) {
            return Resolution.invalid("the number is not a positive integer");
        }
        try {
            return Resolution.resolved(new ForgeUrl(forge,
                    new RepositoryRef(forge, origin, segments.get(0), segments.get(1)), number.get(), kind));
        } catch (IllegalArgumentException e) {
            return Resolution.invalid(e.getMessage());
        }
    }

    private static Resolution resolveGitLab(String forge, String origin, List<String> segments) {
        if (segments.size() < 5 || !GITLAB_SEPARATOR.equals(segments.get(segments.size() - 3))) {
            return Resolution.invalid("a GitLab source URL ends with /-/issues/<n> or /-/merge_requests/<n>");
        }
        Kind kind = switch (segments.get(segments.size() - 2)) {
            case "issues" -> Kind.ISSUE;
            case "merge_requests" -> Kind.PULL_REQUEST;
            default -> null;
        };
        if (kind == null) {
            return Resolution.invalid("the path is neither an issue nor a merge-request path");
        }
        Optional<Integer> number = number(segments.getLast());
        if (number.isEmpty()) {
            return Resolution.invalid("the number is not a positive integer");
        }
        List<String> repository = segments.subList(0, segments.size() - 3);
        String name = repository.getLast();
        String ownerOrPath = String.join("/", repository.subList(0, repository.size() - 1));
        try {
            return Resolution.resolved(new ForgeUrl(forge, new RepositoryRef(forge, origin, ownerOrPath, name),
                    number.get(), kind));
        } catch (IllegalArgumentException e) {
            return Resolution.invalid(e.getMessage());
        }
    }

    private static Optional<Integer> number(String segment) {
        if (segment.isEmpty() || segment.length() > MAX_NUMBER_DIGITS || segment.charAt(0) == '0') {
            return Optional.empty();
        }
        for (int i = 0; i < segment.length(); i++) {
            if (!Character.isDigit(segment.charAt(i))) {
                return Optional.empty();
            }
        }
        return Optional.of(Integer.parseInt(segment));
    }
}
