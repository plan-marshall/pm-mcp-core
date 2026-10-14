/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The manifest of one corpus source, {@code sources/<name>/manifest.json} (PM-WORK-6): the public repository a
 * dataset is fetched from, the commit it is pinned to, its licence and the files read from it, each with its SHA-256.
 * <p>
 * A file stored as a Git LFS pointer is marked {@link SourceFile#lfs()}; its hash is then the hash of the object the
 * pointer names, not of the pointer. The record refuses every value outside its format with an
 * {@link IllegalArgumentException}; {@link SourceManifestCodec} reads and writes the document.
 *
 * @param name       the source name, the directory below {@code sources/}: {@code ^[a-z][a-z0-9-]{1,63}$}
 * @param repository the repository the files are fetched from, an absolute {@code https} URI
 * @param commit     the pinned commit, 40 lower-case hexadecimal digits
 * @param licence    the SPDX licence identifier of the dataset
 * @param files      the files read from the repository at the commit, at least one, each path once; unmodifiable
 * @since 0.1
 */
public record SourceManifest(String name, URI repository, String commit, String licence, List<SourceFile> files) {

    /** A source name: the directory of the source below {@code sources/}. */
    public static final Pattern NAME = Pattern.compile("^[a-z][a-z0-9-]{1,63}$");

    /** A pinned commit: the full object id of SHA-1 git. */
    public static final Pattern COMMIT = Pattern.compile("^[0-9a-f]{40}$");

    /** A SHA-256 in lower-case hexadecimal digits. */
    public static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    /** An SPDX licence identifier, or an SPDX expression of identifiers joined by {@code -}, {@code .} or {@code +}. */
    public static final Pattern LICENCE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9.+-]*$");

    /** A repository-relative path: segments joined by {@code /}, none empty, {@code .} or {@code ..}. */
    public static final Pattern RELATIVE_PATH =
            Pattern.compile("^(?!.*(^|/)\\.{1,2}(/|$))[^/\\\\\\x00-\\x1f]+(/[^/\\\\\\x00-\\x1f]+)*$");

    /**
     * One file of a source.
     *
     * @param path   the repository-relative path of the file
     * @param sha256 the SHA-256 of the file, or of the LFS object if the file is an LFS pointer
     * @param lfs    {@code true} if the file is stored as a Git LFS pointer
     * @since 0.1
     */
    public record SourceFile(String path, String sha256, boolean lfs) {

        /**
         * @throws IllegalArgumentException if the path is not repository-relative or the hash is not a SHA-256
         */
        public SourceFile {
            require(path, RELATIVE_PATH, "path");
            require(sha256, SHA256, "sha256");
        }
    }

    /**
     * @throws IllegalArgumentException if a value is outside the format or a path is named twice
     */
    public SourceManifest {
        require(name, NAME, "name");
        requireRepository(repository);
        require(commit, COMMIT, "commit");
        require(licence, LICENCE, "licence");
        files = List.copyOf(Objects.requireNonNull(files, "files"));
        if (files.isEmpty()) {
            throw new IllegalArgumentException("'files' must name at least one file");
        }
        var paths = new HashSet<String>();
        for (var file : files) {
            if (!paths.add(file.path())) {
                throw new IllegalArgumentException("'files' names the path '%s' twice".formatted(file.path()));
            }
        }
    }

    /**
     * @param path a repository-relative path
     * @return the file of the source with that path; empty if the manifest does not name it
     */
    public Optional<SourceFile> file(String path) {
        return files.stream().filter(file -> file.path().equals(path)).findFirst();
    }

    /**
     * Requires a repository a source may be fetched from, and an imported item may name as its origin: an absolute
     * {@code https} URI with a host and without user information, query or fragment. The manifest and the imported item
     * share this rule, so every item generated from a manifest the codec accepts is accepted too.
     *
     * @param repository the repository URI
     * @throws IllegalArgumentException if the URI is outside the rule
     */
    static void requireRepository(URI repository) {
        Objects.requireNonNull(repository, "repository");
        if (!repository.isAbsolute() || !"https".equals(repository.getScheme()) || repository.getHost() == null
                || repository.getRawQuery() != null || repository.getRawFragment() != null
                || repository.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "'repository' must be an absolute https URI without user, query or fragment: " + repository);
        }
    }

    static void require(String value, Pattern pattern, String field) {
        Objects.requireNonNull(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' does not match %s: %s".formatted(field, pattern, value));
        }
    }
}
