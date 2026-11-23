/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The manifest {@code sources/sources.json} of an entry's source directory or of a plan's (Filesystem Layout § The
 * Source Directory): the record of the sources a start holds, one {@link Source} per stored source, and the revision
 * every write of a source advances.
 * <p>
 * The manifest is the record of the sources, not a second copy of them: a source is a set of files of the source
 * directory, each with its size and SHA-256, verified against the manifest when it is read. A stored source is never
 * changed; a source added later is a new entry of the manifest.
 *
 * @param revision the revision of the manifest, advanced by every write
 * @param sources  the sources, each with a source id of its own
 * @since 0.1
 */
@Builder
public record SourceManifest(long revision, List<Source> sources) {

    /** The format version of the manifest that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The longest reference the schema admits. */
    public static final int MAX_REFERENCE = 2048;

    /** The longest origin the schema admits. */
    public static final int MAX_ORIGIN = 512;

    /** The longest verdict reason the schema admits. */
    public static final int MAX_VERDICT_REASON = 512;

    private static final Pattern SOURCE_ID = Pattern.compile("[a-z][a-z0-9_]{0,63}");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /**
     * @throws NullPointerException     if the sources are {@code null}
     * @throws IllegalArgumentException if the revision is negative, a source id is used twice, a file path is stored
     *                                  by more than one source or repeats another in case, one stored path is the
     *                                  directory of another, the file name of the manifest is used as a stored path, or
     *                                  a source breaks its rules
     */
    public SourceManifest {
        Objects.requireNonNull(sources, "sources");
        if (revision < 0) {
            throw new IllegalArgumentException("'revision' must not be negative: " + revision);
        }
        sources = List.copyOf(sources);
        var ids = new HashSet<String>();
        var paths = new HashMap<String, String>();
        for (var source : sources) {
            if (!ids.add(source.sourceId())) {
                throw new IllegalArgumentException("The source id '%s' is used twice".formatted(source.sourceId()));
            }
            for (var file : source.files()) {
                requireDistinctPath(source.sourceId(), file.path(), paths);
            }
        }
    }

    /**
     * The sources are one set of files of one directory, so a path is stored once: the second source that names it is
     * refused here, where the manifest is built, instead of the move writing one file twice. The comparison folds the
     * case, because a case-insensitive filesystem addresses one file either way and the paths are the operator's file
     * names; a path that is the directory of another is refused too, because one create target cannot be a file and a
     * directory at once.
     */
    private static void requireDistinctPath(String sourceId, String path, Map<String, String> paths) {
        var folded = fold(path);
        var reserved = fold(SourceManifestCodec.FILE_NAME);
        if (reserved.equals(folded) || folded.startsWith(reserved + "/")) {
            throw new IllegalArgumentException("The source '%s' stores the reserved path '%s'".formatted(sourceId, path));
        }
        for (var stored : paths.entrySet()) {
            if (folded.equals(stored.getKey()) || nests(folded, stored.getKey())) {
                throw new IllegalArgumentException(
                        ("The source file '%s' is stored twice, or it and '%s' differ only in case or one is the "
                                + "directory of the other").formatted(path, stored.getValue()));
            }
        }
        paths.put(folded, path);
    }

    /**
     * @return the case-folded form of a stored path, the form a case-insensitive filesystem addresses. The path is
     *         normalized to NFC first, because a macOS filesystem is normalisation-insensitive and an operator's typed
     *         name and a directory listing can spell one file in two forms; the fold is then upper case then lower
     *         case, because the lower case of a capital sigma is otherwise a final sigma and the two forms would not
     *         meet. It errs toward a collision, never toward a missed one.
     */
    private static String fold(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC).toUpperCase(Locale.ROOT).toLowerCase(Locale.ROOT);
    }

    /** @return whether one of two case-folded paths lies below the other */
    private static boolean nests(String first, String second) {
        return first.startsWith(second + "/") || second.startsWith(first + "/");
    }

    /**
     * @return the manifest of an entry's empty source directory
     */
    public static SourceManifest empty() {
        return new SourceManifest(0, List.of());
    }

    /**
     * @param sourceId a source id
     * @return the source of that id; empty if the manifest holds none
     */
    public Optional<Source> source(String sourceId) {
        return sources.stream().filter(source -> source.sourceId().equals(sourceId)).findFirst();
    }

    /**
     * @param source a source to add
     * @return the manifest with the source added and its revision advanced
     * @throws IllegalArgumentException if the source id is used already
     */
    public SourceManifest withSource(Source source) {
        Objects.requireNonNull(source, "source");
        if (this.source(source.sourceId()).isPresent()) {
            throw new IllegalArgumentException("The source id '%s' is used already".formatted(source.sourceId()));
        }
        var added = new ArrayList<>(sources);
        added.add(source);
        return new SourceManifest(revision + 1, added);
    }

    /**
     * What a source is.
     *
     * @since 0.1
     */
    public enum Kind {
        /** A description, as written. */
        DESCRIPTION("description"),
        /** Documents, as copied or moved. */
        DOCUMENTS("documents"),
        /** An issue of a forge. */
        ISSUE("issue"),
        /** A pull request of a forge. */
        PR("pr"),
        /** A lesson as claimed. */
        LESSON("lesson"),
        /** Captured local changes. */
        LOCAL_CHANGES("local_changes"),
        /** The staged section of the epic's queue row. */
        EPIC_ROW("epic_row");

        private final String wireName;

        Kind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the manifest */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in the manifest
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Kind> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * Where a source came from, which decides whether it is trusted.
     *
     * @since 0.1
     */
    public enum Trust {
        /** What the operator wrote and what is on this machine. */
        TRUSTED("trusted"),
        /** What comes from outside the machine. */
        UNTRUSTED("untrusted");

        private final String wireName;

        Trust(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the manifest */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in the manifest
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Trust> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The screening verdict of an external source.
     *
     * @since 0.1
     */
    public enum Verdict {
        /** Stored and not screened yet. */
        UNSCREENED("unscreened"),
        /** Screened and clean. */
        CLEAN("clean"),
        /** Screened and suspicious. */
        SUSPICIOUS("suspicious"),
        /** The operator accepted a suspicious source with a reason. */
        ACCEPTED("accepted");

        private final String wireName;

        Verdict(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the manifest */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in the manifest
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Verdict> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One source of the manifest.
     *
     * @param sourceId      the id of the source, unique in its manifest
     * @param kind          what the source is
     * @param reference     where the source is named, for an issue or a pull request its URL
     * @param origin        where it came from
     * @param trust         whether it is trusted
     * @param files         the stored files, relative to the source directory
     * @param addedAt       the instant the source was stored
     * @param verdict       the screening verdict of an external source; {@code null} for a trusted one
     * @param verdictReason the operator's reason of an accepted source; {@code null} otherwise
     * @since 0.1
     */
    @Builder
    public record Source(String sourceId, Kind kind, String reference, String origin, Trust trust,
    List<SourceFile> files, Instant addedAt, @Nullable Verdict verdict, @Nullable String verdictReason) {

        /**
         * @throws NullPointerException     if a non-nullable component is {@code null}
         * @throws IllegalArgumentException if a value breaks its rules
         */
        public Source {
            requireSourceId(sourceId);
            Objects.requireNonNull(kind, "kind");
            requireText(reference, "reference", MAX_REFERENCE);
            requireText(origin, "origin", MAX_ORIGIN);
            Objects.requireNonNull(trust, "trust");
            files = requireFiles(sourceId, files);
            Objects.requireNonNull(addedAt, "addedAt");
            requireVerdict(sourceId, trust, verdict, verdictReason);
        }

        private static void requireSourceId(String sourceId) {
            Objects.requireNonNull(sourceId, "sourceId");
            if (!SOURCE_ID.matcher(sourceId).matches()) {
                throw new IllegalArgumentException("'source_id' is not a source id: " + sourceId);
            }
        }

        private static void requireText(String value, String name, int maximum) {
            Objects.requireNonNull(value, name);
            if (value.isBlank() || value.length() > maximum) {
                throw new IllegalArgumentException("'%s' must be 1 to %d characters".formatted(name, maximum));
            }
        }

        private static List<SourceFile> requireFiles(String sourceId, @Nullable List<SourceFile> files) {
            Objects.requireNonNull(files, "files");
            var copy = List.copyOf(files);
            if (copy.isEmpty()) {
                throw new IllegalArgumentException("The source '%s' holds no file".formatted(sourceId));
            }
            return copy;
        }

        private static void requireVerdict(String sourceId, Trust trust, @Nullable Verdict verdict,
                @Nullable String verdictReason) {
            if (verdictReason != null && verdictReason.isBlank()) {
                throw new IllegalArgumentException("'verdict_reason' must not be blank");
            }
            if (verdictReason != null && verdictReason.length() > MAX_VERDICT_REASON) {
                throw new IllegalArgumentException("'verdict_reason' has more than %d characters".formatted(
                        MAX_VERDICT_REASON));
            }
            if (trust == Trust.TRUSTED && verdict != null) {
                throw new IllegalArgumentException("A trusted source '%s' carries no verdict".formatted(sourceId));
            }
            if (trust == Trust.UNTRUSTED && verdict == null) {
                throw new IllegalArgumentException("An untrusted source '%s' carries a verdict".formatted(sourceId));
            }
            if (verdict != Verdict.ACCEPTED && verdictReason != null) {
                throw new IllegalArgumentException("Only an accepted source '%s' names a reason".formatted(sourceId));
            }
            if (verdict == Verdict.ACCEPTED && verdictReason == null) {
                throw new IllegalArgumentException("An accepted source '%s' names the operator's reason".formatted(
                        sourceId));
            }
        }
    }

    /**
     * One stored file of a source.
     *
     * @param path   the path of the file relative to the source directory
     * @param size   the size of the file, in bytes
     * @param sha256 the SHA-256 of the file, lower-case hexadecimal
     * @since 0.1
     */
    public record SourceFile(String path, long size, String sha256) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the path is not relative and normalized, is no path of this platform (a
         *                                  NUL among its characters), the size is negative, or the digest is not a
         *                                  SHA-256
         */
        public SourceFile {
            Objects.requireNonNull(path, "path");
            if (path.isBlank() || path.startsWith("/") || Arrays.stream(path.split("/", -1))
                    .anyMatch(segment -> segment.isEmpty() || ".".equals(segment) || "..".equals(segment))) {
                throw new IllegalArgumentException("'path' is not a relative, normalized source path: " + path);
            }
            try {
                Path.of(path);
            } catch (InvalidPathException e) {
                throw new IllegalArgumentException("'path' is no path of this platform: " + path, e);
            }
            if (size < 0) {
                throw new IllegalArgumentException("'size' must not be negative: " + size);
            }
            Objects.requireNonNull(sha256, "sha256");
            if (!SHA256.matcher(sha256).matches()) {
                throw new IllegalArgumentException("'sha256' is not a lower-case SHA-256: " + sha256);
            }
        }
    }
}
