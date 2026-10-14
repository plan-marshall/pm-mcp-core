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

import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The outcome of one step of the fetch of a corpus source (PM-WORK-6, PM-EXEC-5): the bytes it fetched, or why it
 * fetched nothing. The set of outcomes is closed.
 * <p>
 * The detail names what was fetched or what failed and never carries a credential: the user information of every URI
 * in it is removed when the outcome is made.
 *
 * @since 0.1
 */
public final class SourceFetch {

    /**
     * The user information of a URI in a detail, which may carry a credential: everything of the authority up to its
     * last {@code @}, so a password with an unencoded {@code @} is removed whole.
     */
    private static final Pattern USER_INFO = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*://)[^/?#\\s]*@");

    private static final byte[] NOTHING = new byte[0];

    /**
     * The outcome of a fetch step. The set is closed.
     *
     * @since 0.1
     */
    public enum Outcome {
        /** The step fetched what it was asked for. */
        FETCHED,
        /** The transport failed: the repository or its endpoint did not answer. */
        UNREACHABLE,
        /** The repository answered, but the commit, the path or the object is absent at the pin. */
        PIN_MISSING
    }

    private final Outcome outcome;
    private final byte[] content;
    private final String detail;

    private SourceFetch(Outcome outcome, byte[] content, String detail) {
        this.outcome = outcome;
        this.content = content;
        this.detail = redact(Objects.requireNonNull(detail, "detail"));
    }

    /**
     * @param content the fetched bytes; copied
     * @param detail  what was fetched
     * @return the outcome {@link Outcome#FETCHED}
     */
    public static SourceFetch fetched(byte[] content, String detail) {
        return new SourceFetch(Outcome.FETCHED, Objects.requireNonNull(content, "content").clone(), detail);
    }

    /**
     * @param detail what was done, for a step that fetches no bytes
     * @return the outcome {@link Outcome#FETCHED} without content
     */
    public static SourceFetch done(String detail) {
        return new SourceFetch(Outcome.FETCHED, NOTHING, detail);
    }

    /**
     * @param detail what did not answer, and how
     * @return the outcome {@link Outcome#UNREACHABLE}
     */
    public static SourceFetch unreachable(String detail) {
        return new SourceFetch(Outcome.UNREACHABLE, NOTHING, detail);
    }

    /**
     * @param detail what is absent at the pin
     * @return the outcome {@link Outcome#PIN_MISSING}
     */
    public static SourceFetch pinMissing(String detail) {
        return new SourceFetch(Outcome.PIN_MISSING, NOTHING, detail);
    }

    /** @return the outcome */
    public Outcome outcome() {
        return outcome;
    }

    /** @return {@code true} for {@link Outcome#FETCHED} */
    public boolean isFetched() {
        return outcome == Outcome.FETCHED;
    }

    /** @return a copy of the fetched bytes; empty unless the outcome is {@link Outcome#FETCHED} with content */
    public byte[] content() {
        return content.clone();
    }

    /** @return what was fetched or what failed, without a credential */
    public String detail() {
        return detail;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SourceFetch fetch && outcome == fetch.outcome && Arrays.equals(content, fetch.content)
                && detail.equals(fetch.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outcome, Arrays.hashCode(content), detail);
    }

    @Override
    public String toString() {
        return "SourceFetch[%s, %d bytes, %s]".formatted(outcome, content.length, detail);
    }

    private static String redact(String detail) {
        return USER_INFO.matcher(detail).replaceAll("$1");
    }
}
