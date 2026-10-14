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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The result of one import of the corpus sources (PM-WORK-6, PM-TEST-6): which sources it covered with how many items,
 * which it missed and why, and the verdict that follows.
 * <p>
 * The verdict is {@link Verdict#FAILED} as soon as one source fails its hash or does not yield its items from its
 * overlay: the items of the other sources are still generated, but no role is qualified on a failed import. Otherwise
 * a source whose repository did not answer, or whose pin is absent, makes it {@link Verdict#PARTIAL}, with the missed
 * sources named. An import that missed nothing is {@link Verdict#COMPLETE}.
 *
 * @param covered the sources whose items were generated, in the order of the import; unmodifiable
 * @param missed  the sources none of whose items was generated, in the order of the import; unmodifiable
 * @since 0.1
 */
public record ImportResult(List<CoveredSource> covered, List<MissedSource> missed) {

    /**
     * The verdict of an import. The set is closed.
     *
     * @since 0.1
     */
    public enum Verdict {
        /** Every source was fetched, verified and generated. */
        COMPLETE,
        /** A source could not be fetched; the corpus lacks its items, and a qualification on it is partial. */
        PARTIAL,
        /** A source failed its hash or its overlay; no role is qualified on the import. */
        FAILED
    }

    /**
     * Why a source was missed. The set is closed.
     *
     * @since 0.1
     */
    public enum Reason {
        /** The repository or its LFS endpoint did not answer. */
        UNREACHABLE("unreachable"),
        /** The pinned commit, a file or an LFS object is absent at the pin. */
        PIN_MISSING("pin_missing"),
        /** A file or its LFS object does not have the SHA-256 the manifest pins. */
        HASH_MISMATCH("hash_mismatch"),
        /** The manifest, the overlay or the source file does not yield the items the overlay selects. */
        MALFORMED("malformed");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in a record */
        public String wireName() {
            return wireName;
        }

        /** @return {@code true} if a source missed for this reason fails the import */
        public boolean fails() {
            return this == HASH_MISMATCH || this == MALFORMED;
        }
    }

    /**
     * A source whose items were generated.
     *
     * @param name  the name of the source
     * @param items the number of items generated from it
     * @since 0.1
     */
    public record CoveredSource(String name, int items) {

        /**
         * @throws IllegalArgumentException if the item count is negative
         */
        public CoveredSource {
            Objects.requireNonNull(name, "name");
            if (items < 0) {
                throw new IllegalArgumentException("'items' must not be negative: " + items);
            }
        }
    }

    /**
     * A source none of whose items was generated.
     *
     * @param name   the name of the source
     * @param reason why it was missed
     * @param file   the repository-relative path of the file it concerns; {@code null} if it concerns the source as a
     *               whole
     * @param detail what failed, without a credential
     * @since 0.1
     */
    public record MissedSource(String name, Reason reason, String file, String detail) {

        /**
         * Requires the name, the reason and the detail.
         */
        public MissedSource {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * @throws IllegalArgumentException if a source is both covered and missed, or named twice
     */
    public ImportResult {
        covered = List.copyOf(Objects.requireNonNull(covered, "covered"));
        missed = List.copyOf(Objects.requireNonNull(missed, "missed"));
        var names = new HashSet<String>();
        covered.forEach(source -> requireOnce(names, source.name()));
        missed.forEach(source -> requireOnce(names, source.name()));
    }

    /** @return the verdict of the import */
    public Verdict verdict() {
        if (missed.stream().anyMatch(source -> source.reason().fails())) {
            return Verdict.FAILED;
        }
        return missed.isEmpty() ? Verdict.COMPLETE : Verdict.PARTIAL;
    }

    /** @return the number of items generated */
    public int items() {
        return covered.stream().mapToInt(CoveredSource::items).sum();
    }

    private static void requireOnce(HashSet<String> names, String name) {
        if (!names.add(name)) {
            throw new IllegalArgumentException("the source '%s' is named twice".formatted(name));
        }
    }
}
