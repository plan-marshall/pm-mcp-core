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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * The manifest of one corpus source, {@code sources/<name>/manifest.json} (PM-WORK-6): the public repository a
 * dataset is fetched from and the kind of source it is, the commit it is pinned to, its licence and the files read
 * from it, each with its SHA-256.
 * <p>
 * A file stored as a Git LFS pointer is marked {@link SourceFile#lfs()}; its hash is then the hash of the object the
 * pointer names, not of the pointer. The record refuses every value outside its format with an
 * {@link IllegalArgumentException}; {@link SourceManifestCodec} reads and writes the document.
 * <p>
 * Each pattern of this record is the pattern of the schemas of the package, compiled with {@link PythonRegex} so that it
 * has the meaning Python gives it (in {@link #RELATIVE_PATH}, {@code .} crosses {@code \r}, U+0085, U+2028 and U+2029).
 * The record matches the whole value, so it never accepts a value the schema refuses; it is stricter than the schema's
 * {@code re.search} only for a value ending in one {@code \n}, which the schema's {@code $} admits.
 *
 * @param name       the source name, the directory below {@code sources/}: {@code ^[a-z][a-z0-9-]{1,63}$}
 * @param repository the repository the files are fetched from, an absolute {@code https} URI
 * @param commit     the pinned commit, 40 lower-case hexadecimal digits
 * @param licence    the SPDX licence identifier of the dataset
 * @param fetch      the kind of source the files are fetched from: {@code git} or {@code huggingface}
 * @param files      the files read from the repository at the commit, at least one, each path once; unmodifiable
 * @since 0.1
 */
@Builder
public record SourceManifest(String name, URI repository, String commit, String licence, FetchKind fetch,
List<SourceFile> files) {

    /** A source name: the directory of the source below {@code sources/}. */
    public static final Pattern NAME_PATTERN = PythonRegex.compile("^[a-z][a-z0-9-]{1,63}$");

    /** The longest source name, in characters: the pattern admits two to 64. */
    public static final int NAME_MAX_LENGTH = 64;

    /** A pinned commit: the full object id of SHA-1 git. */
    public static final Pattern COMMIT_PATTERN = PythonRegex.compile("^[0-9a-f]{40}$");

    /** The length of a pinned commit, in characters: the pattern admits exactly 40. */
    public static final int COMMIT_MAX_LENGTH = 40;

    /** A SHA-256 in lower-case hexadecimal digits. */
    public static final Pattern SHA256 = PythonRegex.compile("^[0-9a-f]{64}$");

    /** The length of a SHA-256, in characters: the pattern admits exactly 64. */
    public static final int SHA256_MAX_LENGTH = 64;

    /** An SPDX licence identifier, or an SPDX expression of identifiers joined by {@code -}, {@code .} or {@code +}. */
    public static final Pattern LICENCE_PATTERN = PythonRegex.compile("^[A-Za-z0-9][A-Za-z0-9.+-]*$");

    /** The longest licence identifier, in characters: the pattern bounds no length. */
    public static final int LICENCE_MAX_LENGTH = 64;

    /**
     * A repository-relative path: segments joined by {@code /}, none empty, {@code .} or {@code ..}. A {@code ..}
     * segment behind any character but {@code \n} is found, as Python finds it, so a path whose first segment ends in
     * U+0085 before a {@code /../} is refused.
     */
    public static final Pattern RELATIVE_PATH =
            PythonRegex.compile("^(?!.*(^|/)\\.{1,2}(/|$))[^/\\\\\\x00-\\x1f]+(/[^/\\\\\\x00-\\x1f]+)*$");

    /** The longest repository-relative path, in characters: the pattern bounds no length. */
    public static final int RELATIVE_PATH_MAX_LENGTH = 1024;

    /** The longest repository URI, in characters: {@link #requireRepository} checks it before the URI's own rule. */
    public static final int REPOSITORY_MAX_LENGTH = 2048;

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
            require(path, RELATIVE_PATH_MAX_LENGTH, RELATIVE_PATH, "path");
            require(sha256, SHA256_MAX_LENGTH, SHA256, "sha256");
        }
    }

    /**
     * The kind of source a manifest names: the protocol the files are fetched over, and the part of the fetch that
     * serves it. The set is closed.
     *
     * @since 0.1
     */
    public enum FetchKind {
        /** The files are fetched from a git repository. */
        GIT("git"),
        /** The files are fetched over HTTPS from a Hugging Face dataset. */
        HUGGINGFACE("huggingface");

        private final String wireName;

        FetchKind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the kind in a document */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a kind in a document
         * @return the kind; empty if the name is not one
         */
        public static Optional<FetchKind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(kind -> kind.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * @throws IllegalArgumentException if a value is outside the format or a path is named twice
     */
    public SourceManifest {
        require(name, NAME_MAX_LENGTH, NAME_PATTERN, "name");
        requireRepository(repository);
        require(commit, COMMIT_MAX_LENGTH, COMMIT_PATTERN, "commit");
        require(licence, LICENCE_MAX_LENGTH, LICENCE_PATTERN, "licence");
        Objects.requireNonNull(fetch, "fetch");
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
     * @throws IllegalArgumentException if the URI is outside the rule or longer than {@link #REPOSITORY_MAX_LENGTH}
     */
    static void requireRepository(URI repository) {
        Objects.requireNonNull(repository, "repository");
        var uri = repository.toString();
        if (uri.codePointCount(0, uri.length()) > REPOSITORY_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "'repository' is longer than " + REPOSITORY_MAX_LENGTH + " characters");
        }
        if (!repository.isAbsolute() || !"https".equals(repository.getScheme()) || repository.getHost() == null
                || repository.getRawQuery() != null || repository.getRawFragment() != null
                || repository.getRawUserInfo() != null) {
            throw new IllegalArgumentException(
                    "'repository' must be an absolute https URI without user, query or fragment: " + repository);
        }
    }

    /**
     * Requires a value no longer than a bound, and only then its pattern, so an over-long value never reaches the
     * regular expression engine the way {@link SchemaSubset} checks the length before the pattern.
     *
     * @param value     the value
     * @param maxLength the longest value, in code points
     * @param pattern   the pattern of the value
     * @param field     the name of the member, named by a refusal
     * @throws IllegalArgumentException if the value is longer than the bound or does not match the pattern
     */
    static void require(String value, int maxLength, Pattern pattern, String field) {
        Objects.requireNonNull(value, field);
        if (value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException("'" + field + "' is longer than " + maxLength + " characters");
        }
        require(value, pattern, field);
    }

    static void require(String value, Pattern pattern, String field) {
        Objects.requireNonNull(value, field);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' does not match %s: %s".formatted(field, pattern, value));
        }
    }
}
