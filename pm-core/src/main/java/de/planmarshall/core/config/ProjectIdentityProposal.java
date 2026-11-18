/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import de.planmarshall.core.store.LockKey;

/**
 * The identity an onboarding proposes for a project (State Catalogue § Project Onboarding, {@code project.identity}):
 * the id from the remote's name, made valid by the identifier grammar and unique on the machine, and the display name
 * from the remote's owner and name. The operator confirms or changes both, so this is a proposal, never a decision.
 * <p>
 * A remote that names no owner and no name proposes nothing; a name that the grammar cannot be made valid from does not
 * either. A name already taken on the machine gains the suffix {@code -2}, {@code -3}, … until it is free.
 *
 * @param projectId   the proposed project id
 * @param displayName the proposed display name, {@code <owner>/<name>}
 * @since 0.1
 */
public record ProjectIdentityProposal(String projectId, String displayName) {

    /** The longest identifier the grammar admits. */
    private static final int MAX_LENGTH = 63;

    private static final Pattern SCP_LIKE = Pattern.compile("^(?:[^@/\\s]+@)?[^:/\\s]+:(.+)$");

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the id is not one the grammar admits
     */
    public ProjectIdentityProposal {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(displayName, "displayName");
        if (!LockKey.isIdentifier(projectId)) {
            throw new IllegalArgumentException("Not a project id of the identifier grammar: " + projectId);
        }
    }

    /**
     * Proposes the identity from the checkout's remote.
     *
     * @param remoteUrl  the remote as the checkout records it, for example {@code https://host/owner/name.git}
     * @param takenIds   the project ids the machine already enrols
     * @return the proposal; empty when the remote names no owner and name, is a {@code file://} remote, or names a
     *         repository no valid id can be made from
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Optional<ProjectIdentityProposal> fromRemote(String remoteUrl, Collection<String> takenIds) {
        Objects.requireNonNull(remoteUrl, "remoteUrl");
        Objects.requireNonNull(takenIds, "takenIds");
        return path(remoteUrl).flatMap(ProjectIdentityProposal::split).flatMap(repository -> {
            var base = slug(repository.name());
            if (base.isEmpty()) {
                return Optional.empty();
            }
            var id = unique(base, takenIds);
            return LockKey.isIdentifier(id)
                    ? Optional.of(new ProjectIdentityProposal(id, repository.owner() + "/" + repository.name()))
                    : Optional.empty();
        });
    }

    /**
     * Makes the name the first candidate of the id grammar and appends the smallest free suffix.
     */
    private static String unique(String base, Collection<String> takenIds) {
        var candidate = base;
        var suffix = 2;
        while (takenIds.contains(candidate)) {
            var tail = "-" + suffix++;
            candidate = truncate(base, MAX_LENGTH - tail.length()) + tail;
        }
        return candidate;
    }

    /**
     * Reduces a repository name to the grammar: lower case, every run outside it becomes one hyphen, and the ends are
     * the alphanumeric digits the grammar requires.
     */
    private static String slug(String name) {
        var lower = name.toLowerCase(Locale.ROOT);
        var builder = new StringBuilder(lower.length());
        var pendingHyphen = false;
        for (var index = 0; index < lower.length(); index++) {
            var character = lower.charAt(index);
            if (character >= 'a' && character <= 'z' || character >= '0' && character <= '9') {
                if (pendingHyphen && !builder.isEmpty()) {
                    builder.append('-');
                }
                pendingHyphen = false;
                builder.append(character);
            } else {
                pendingHyphen = true;
            }
        }
        return truncate(builder.toString(), MAX_LENGTH);
    }

    private static String truncate(String value, int length) {
        var cut = value.length() > length ? value.substring(0, length) : value;
        while (cut.endsWith("-")) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    /** @return the {@code owner/name} path of a remote, or empty when it carries none */
    private static Optional<String> path(String remoteUrl) {
        var trimmed = remoteUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        var separator = trimmed.indexOf("://");
        if (separator >= 0) {
            var scheme = trimmed.substring(0, separator);
            if ("file".equalsIgnoreCase(scheme)) {
                return Optional.empty();
            }
            var afterScheme = trimmed.substring(separator + 3);
            var slash = afterScheme.indexOf('/');
            return slash < 0 ? Optional.empty() : Optional.of(afterScheme.substring(slash + 1));
        }
        var scp = SCP_LIKE.matcher(trimmed);
        return Optional.of(scp.matches() ? scp.group(1) : trimmed);
    }

    /** @return the owner and the name of a repository path, or empty when it names fewer than two segments */
    private static Optional<Repository> split(String path) {
        var segments = path.split("/");
        if (segments.length < 2) {
            return Optional.empty();
        }
        var owner = segments[segments.length - 2].trim();
        var name = segments[segments.length - 1].trim();
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - ".git".length());
        }
        return owner.isEmpty() || name.isEmpty() ? Optional.empty() : Optional.of(new Repository(owner, name));
    }

    /** The owner and the name of a repository, before the id is made from them. */
    private record Repository(String owner, String name) {
    }
}
