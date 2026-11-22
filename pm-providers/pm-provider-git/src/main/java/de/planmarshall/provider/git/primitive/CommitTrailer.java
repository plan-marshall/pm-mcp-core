/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The trailer a server commit carries (PM-WF-16, Stage 3): the name and the email of the system that produced the
 * commit, independent of the commit's author and committer.
 * <p>
 * The values follow the textual rule the code applies to the stored key {@code commit_trailer}: a name of 1 to 128
 * characters without an angle bracket or a line break (CR, LF, U+0085, U+2028, U+2029), an email of at most 254
 * characters of the form
 * {@code local@domain} without an angle bracket or white space, and neither with an unpaired surrogate, which has no
 * UTF-8 encoding. The schema of the key is stricter than this rule: its patterns refuse every C0 and C1 control as
 * well, so a stored NUL or ESC never reaches the trailer line. The trailer is resolved per half from the operator's
 * local value, then the machine's
 * value, then the compiled default {@code plan-marshall <noreply@cuioss.de>}; a stored value that breaks a constraint
 * is ignored, never an exception.
 * <p>
 * Specification: {@code doc/specification/store-schemas/08-project-configuration-local.adoc} (Operator Local
 * Configuration).
 *
 * @param name  the name, within the constraints of the key
 * @param email the email, within the constraints of the key
 * @since 0.1
 */
public record CommitTrailer(String name, String email) {

    /** The largest name, in characters. */
    public static final int NAME_MAX_LENGTH = 128;

    /** The largest email, in characters. */
    public static final int EMAIL_MAX_LENGTH = 254;

    private static final Pattern NAME_PATTERN = Pattern.compile("[^<>\\r\\n]+");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^<>\\s@]+@[^<>\\s@]+", Pattern.UNICODE_CHARACTER_CLASS);

    /** Declared after the patterns, which its construction checks against. */
    private static final CommitTrailer COMPILED = new CommitTrailer("plan-marshall", "noreply@cuioss.de");

    /**
     * @param name  the name
     * @param email the email
     * @throws IllegalArgumentException if a value breaks the constraints of the key; a stored value is checked with
     *                                  {@link #isValidName(String)} and {@link #isValidEmail(String)} before it gets
     *                                  here
     */
    public CommitTrailer {
        Objects.requireNonNull(name, Resolution.NAME_HALF);
        Objects.requireNonNull(email, Resolution.EMAIL_HALF);
        if (!isValidName(name)) {
            throw new IllegalArgumentException("the trailer name breaks the constraints of commit_trailer.name");
        }
        if (!isValidEmail(email)) {
            throw new IllegalArgumentException("the trailer email breaks the constraints of commit_trailer.email");
        }
    }

    /**
     * @return the compiled default trailer, {@code plan-marshall <noreply@cuioss.de>}
     */
    public static CommitTrailer compiled() {
        return COMPILED;
    }

    /**
     * @param name a candidate name
     * @return whether it is a name of 1 to {@value #NAME_MAX_LENGTH} characters without an angle bracket, a line
     *         break (CR, LF, or one of the Unicode line separators U+0085, U+2028 and U+2029 that
     *         {@link CommitMessages} also treats as line ends) or an unpaired surrogate
     */
    public static boolean isValidName(String name) {
        var length = name.codePointCount(0, name.length());
        return length >= 1 && length <= NAME_MAX_LENGTH && !hasUnpairedSurrogate(name)
                && NAME_PATTERN.matcher(name).matches()
                && !CommitMessages.UNSPLIT_TERMINATORS.matcher(name).find();
    }

    /**
     * @param email a candidate email
     * @return whether it is an email of at most {@value #EMAIL_MAX_LENGTH} characters of the form
     *         {@code local@domain} without an angle bracket, white space or an unpaired surrogate; the Unicode
     *         line separators U+0085, U+2028 and U+2029 are white space in the Unicode sense and are refused with it
     */
    public static boolean isValidEmail(String email) {
        return email.codePointCount(0, email.length()) <= EMAIL_MAX_LENGTH && !hasUnpairedSurrogate(email)
                && EMAIL_PATTERN.matcher(email).matches();
    }

    /**
     * An unpaired high or low surrogate has no UTF-8 encoding, so it cannot reach the trailer of a commit. A surrogate
     * pair is one supplementary code point and is no unpaired surrogate; {@link String#codePoints()} yields an
     * unpaired surrogate as a code point in the surrogate range.
     */
    private static boolean hasUnpairedSurrogate(String value) {
        return value.codePoints().anyMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }

    /**
     * Resolves the trailer per half: the local value, else the machine value, else the compiled default. A stored
     * value that breaks the constraints of its half is passed over as if it were absent, and its half reports
     * {@link TrailerSource#INVALID_IGNORED} unless a later stored value of the half is used.
     *
     * @param local   the operator's stored value of the project, {@code null} when the project stores none
     * @param machine the stored default of the machine, {@code null} when the machine stores none
     * @return the resolved trailer with the source of each half
     */
    public static Resolution resolve(@Nullable Stored local, @Nullable Stored machine) {
        var name = resolveHalf(local == null ? null : local.name(), machine == null ? null : machine.name(),
                COMPILED.name(), CommitTrailer::isValidName);
        var email = resolveHalf(local == null ? null : local.email(), machine == null ? null : machine.email(),
                COMPILED.email(), CommitTrailer::isValidEmail);
        return new Resolution(new CommitTrailer(name.value(), email.value()), name.source(), email.source());
    }

    private static Half resolveHalf(@Nullable String local, @Nullable String machine, String compiled,
            Predicate<String> valid) {
        var refused = false;
        for (var candidate : Arrays.asList(local, machine)) {
            if (candidate == null) {
                continue;
            }
            if (valid.test(candidate)) {
                return new Half(candidate, TrailerSource.CONFIGURED);
            }
            refused = true;
        }
        return new Half(compiled, refused ? TrailerSource.INVALID_IGNORED : TrailerSource.DEFAULT);
    }

    private record Half(String value, TrailerSource source) {
    }

    /**
     * The trailer values stored at one level, as read from the store: each half may be absent or break the
     * constraints of the key.
     *
     * @param name  the stored name, {@code null} when the level stores none
     * @param email the stored email, {@code null} when the level stores none
     * @since 0.1
     */
    public record Stored(@Nullable String name, @Nullable String email) {
    }

    /**
     * The resolved trailer and the provenance of each half, recorded as {@code trailer_source: {name, email}} in the
     * commit's audit record and never rendered in facts. {@code git.stage-and-commit} returns it on {@code committed}
     * as the fact {@code trailer_source} in the shape of {@link #sourceFact()}.
     *
     * @param trailer     the resolved trailer
     * @param nameSource  where the name came from
     * @param emailSource where the email came from
     * @since 0.1
     */
    public record Resolution(CommitTrailer trailer, TrailerSource nameSource, TrailerSource emailSource) {

        /** The key of the name half in {@link #sourceFact()}. */
        public static final String NAME_HALF = "name";

        /** The key of the email half in {@link #sourceFact()}. */
        public static final String EMAIL_HALF = "email";

        /**
         * @param trailer     the resolved trailer
         * @param nameSource  the source of the name
         * @param emailSource the source of the email
         */
        public Resolution {
            Objects.requireNonNull(trailer, "trailer");
            Objects.requireNonNull(nameSource, "nameSource");
            Objects.requireNonNull(emailSource, "emailSource");
        }

        /**
         * @return the provenance as the audit record carries it, {@code {name: <source>, email: <source>}} with the
         *         wire names of the two sources; it holds no value of the trailer itself
         */
        public Map<String, String> sourceFact() {
            return Map.of(NAME_HALF, nameSource.wireName(), EMAIL_HALF, emailSource.wireName());
        }
    }

    /**
     * Where one half of the resolved trailer came from.
     *
     * @since 0.1
     */
    public enum TrailerSource {
        /** A stored value, local or machine, was used. */
        CONFIGURED("configured"),
        /** The compiled default was used and no stored value was refused. */
        DEFAULT("default"),
        /** A stored value was refused and treated as absent, and the compiled default was used. */
        INVALID_IGNORED("invalid_ignored");

        private final String wireName;

        TrailerSource(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in the audit record */
        public String wireName() {
            return wireName;
        }
    }
}
