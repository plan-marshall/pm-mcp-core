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

import java.io.Serial;
import java.nio.file.Path;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * A store document or record that a reader refused (PM-IMPL-2): it is absent where it must exist, it does not parse
 * as the one shape of its file kind, or it declares another {@code format_version} than the current one.
 * <p>
 * A reader accepts exactly the current format version of a file kind and never tolerates another shape. What it
 * refuses it leaves as it is: a reader never repairs, migrates or rewrites a store. The refusal names the file, the
 * format version found in it (none if the file is unreadable or absent) and the current version, so a caller can
 * report it with the operator's next step.
 *
 * @since 0.1
 */
public final class StoreRefusal extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int NO_VERSION = Integer.MIN_VALUE;

    /**
     * Why a store document or record was refused. The set is closed.
     *
     * @since 0.1
     */
    public enum Outcome {
        /** The file does not exist where the reader requires it. */
        ABSENT("absent"),
        /** The content does not parse as the one shape of its file kind, or declares no format version. */
        UNREADABLE("unreadable"),
        /** The content declares an older format version than the current one. */
        FORMAT_OUTDATED("format_outdated"),
        /** The content declares a newer format version than the current one. */
        FORMAT_NEWER("format_newer");

        private final String code;

        Outcome(String code) {
            this.code = code;
        }

        /** @return the name of the outcome in a representation */
        public String code() {
            return code;
        }
    }

    /** Why the content was refused. */
    private final Outcome outcome;

    /** The refused file; a path is not serializable, so it is not kept across serialization. */
    private final transient Path file;

    /** The format version the content declares, or {@link #NO_VERSION}. */
    private final int found;

    /** The current format version of the file kind. */
    private final int current;

    private StoreRefusal(Outcome outcome, Path file, int found, int current, String detail, Throwable cause) {
        super(message(outcome, file, found, current, detail), cause);
        this.outcome = outcome;
        this.file = file;
        this.found = found;
        this.current = current;
    }

    /**
     * @param file    the file that does not exist
     * @param current the current format version of its file kind
     * @return the refusal of an absent file
     */
    public static StoreRefusal absent(Path file, int current) {
        return new StoreRefusal(Outcome.ABSENT, Objects.requireNonNull(file, "file"), NO_VERSION, current, null, null);
    }

    /**
     * @param file    the refused file
     * @param current the current format version of its file kind
     * @param detail  what does not parse, and where
     * @param cause   the failure of the parser; {@code null} if there is none
     * @return the refusal of unreadable content
     */
    public static StoreRefusal unreadable(Path file, int current, String detail, Throwable cause) {
        return new StoreRefusal(Outcome.UNREADABLE, Objects.requireNonNull(file, "file"), NO_VERSION, current,
                Objects.requireNonNull(detail, "detail"), cause);
    }

    /**
     * @param file    the refused file
     * @param found   the format version the content declares
     * @param current the current format version of its file kind
     * @return the refusal as outdated if the version found is older, else as newer
     * @throws IllegalArgumentException if the version found is the current one
     */
    public static StoreRefusal ofVersion(Path file, int found, int current) {
        if (found == current) {
            throw new IllegalArgumentException("format_version %d is the current one".formatted(found));
        }
        var outcome = found < current ? Outcome.FORMAT_OUTDATED : Outcome.FORMAT_NEWER;
        return new StoreRefusal(outcome, Objects.requireNonNull(file, "file"), found, current, null, null);
    }

    /** @return why the content was refused */
    public Outcome outcome() {
        return outcome;
    }

    /** @return the refused file; {@code null} only after the refusal was deserialized */
    public Path file() {
        return file;
    }

    /** @return the format version the content declares; empty if the file is absent or unreadable */
    public OptionalInt found() {
        return found == NO_VERSION ? OptionalInt.empty() : OptionalInt.of(found);
    }

    /** @return the current format version of the file kind */
    public int current() {
        return current;
    }

    private static String message(Outcome outcome, Path file, int found, int current, String detail) {
        var version = found == NO_VERSION ? "none" : Integer.toString(found);
        var message = "%s: %s (format_version found %s, current %d)".formatted(file, outcome.code(), version,
                current);
        return detail == null ? message : message + ": " + detail;
    }
}
