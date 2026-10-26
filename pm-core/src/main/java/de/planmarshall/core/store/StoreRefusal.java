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
import java.util.Locale;
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
 * <p>
 * An enrolment file is also refused for its integrity (PM-EXEC-2, PM-SEC-4, PM-SEC-5): as
 * {@link Outcome#ENROLMENT_TAMPERED} when it does not match its sidecar, naming the operator command that re-baselines
 * it, as {@link Outcome#ENROLMENT_INSECURE} when it fails a per-use check, and as {@link Outcome#ENROLMENT_INVALID}
 * when a value violates the enrolment rules (PM-EXEC-1), so the runtime neither signs nor uses it. Such a refusal names
 * no format version found; its current version is that of the enrolment file.
 *
 * @since 0.1
 */
public final class StoreRefusal extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int NO_VERSION = Integer.MIN_VALUE;

    /** The parameter name {@link LockKey#identifier(String, String)} reports for an invalid project id. */
    private static final String PROJECT_ID = "projectId";

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
        FORMAT_NEWER("format_newer"),
        /**
         * An enrolment file does not match its integrity sidecar, or one of the two exists without the other
         * (PM-EXEC-2, PM-SEC-5); nothing in the file is trusted until the operator re-baselines it.
         */
        ENROLMENT_TAMPERED("enrolment_tampered"),
        /**
         * An enrolment file or its sidecar fails a per-use check: a foreign owner, a mode broader than {@code 0600},
         * or a symbolic link (PM-SEC-4).
         */
        ENROLMENT_INSECURE("enrolment_insecure"),
        /**
         * An enrolment that the runtime refuses to sign or to use because a value violates the enrolment rules
         * (PM-EXEC-1).
         */
        ENROLMENT_INVALID("enrolment_invalid"),
        /**
         * The document changed since the caller read it: a write against another revision than the current one is
         * refused instead of overwriting the change of another writer (Store Schemas § Operator Local Configuration).
         */
        CONFLICT("conflict");

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
            throw new IllegalArgumentException("format_version " + found + " is the current one");
        }
        var outcome = found < current ? Outcome.FORMAT_OUTDATED : Outcome.FORMAT_NEWER;
        return new StoreRefusal(outcome, Objects.requireNonNull(file, "file"), found, current, null, null);
    }

    /**
     * The refusal of an enrolment whose integrity check failed. Its message names the operator command that
     * re-baselines the enrolment after the operator reviewed it.
     *
     * @param file      the refused enrolment file or sidecar
     * @param projectId the enrolled project
     * @param detail    what failed: the HMAC, the recorded size or SHA-256, a sidecar without its file, a file without
     *                  its sidecar
     * @return the refusal as {@link Outcome#ENROLMENT_TAMPERED}
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public static StoreRefusal enrolmentTampered(Path file, String projectId, String detail) {
        var command = "pm-operator accept-edit enrolment " + LockKey.identifier(projectId, PROJECT_ID);
        return new StoreRefusal(Outcome.ENROLMENT_TAMPERED, Objects.requireNonNull(file, "file"), NO_VERSION,
                ProjectEnrolment.FORMAT_VERSION,
                "%s; review the file, then re-baseline it with '%s'".formatted(
                        Objects.requireNonNull(detail, "detail"), command),
                null);
    }

    /**
     * The refusal of an enrolment file or sidecar that failed a per-use check.
     *
     * @param file   the refused enrolment file or sidecar
     * @param reason what failed: a foreign owner, a mode broader than {@code 0600}, a symbolic link
     * @return the refusal as {@link Outcome#ENROLMENT_INSECURE}
     */
    public static StoreRefusal enrolmentInsecure(Path file, String reason) {
        return new StoreRefusal(Outcome.ENROLMENT_INSECURE, Objects.requireNonNull(file, "file"), NO_VERSION,
                ProjectEnrolment.FORMAT_VERSION, Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * The refusal of an enrolment that the runtime refuses to sign or to use because a value violates the enrolment
     * rules.
     *
     * @param file      the refused enrolment file or sidecar
     * @param projectId the enrolled project
     * @param reason    which rule is violated, and by which value
     * @return the refusal as {@link Outcome#ENROLMENT_INVALID}
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public static StoreRefusal enrolmentInvalid(Path file, String projectId, String reason) {
        Objects.requireNonNull(file, "file");
        LockKey.identifier(Objects.requireNonNull(projectId, PROJECT_ID), PROJECT_ID);
        return new StoreRefusal(Outcome.ENROLMENT_INVALID, file, NO_VERSION, ProjectEnrolment.FORMAT_VERSION,
                Objects.requireNonNull(reason, "reason"), null);
    }

    /**
     * The refusal of a write against a revision that is not the current one: the document changed since the caller
     * read it, and the write would overwrite that change.
     * <p>
     * The revision pair is carried in the message, not in the format-version fields: {@link #found()} is
     * {@link #NO_VERSION} and {@link #current()} the document's format version, because a revision is not a format
     * version and the two must never be read as one another.
     *
     * @param file     the refused document
     * @param expected the revision the caller read
     * @param current  the revision the document holds
     * @return the refusal as {@link Outcome#CONFLICT}
     */
    public static StoreRefusal conflict(Path file, long expected, long current) {
        return new StoreRefusal(Outcome.CONFLICT, Objects.requireNonNull(file, "file"), NO_VERSION,
                QualificationSettingsStore.FORMAT_VERSION,
                "the write names revision %d, the document holds %d".formatted(expected, current), null);
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
        if (outcome == Outcome.ENROLMENT_TAMPERED || outcome == Outcome.ENROLMENT_INSECURE
                || outcome == Outcome.ENROLMENT_INVALID) {
            return "%s: %s: %s".formatted(file, outcome.code(), detail);
        }
        var version = found == NO_VERSION ? "none" : Integer.toString(found);
        var message = String.format(Locale.ROOT, "%s: %s (format_version found %s, current %d)", file,
                outcome.code(), version, current);
        return detail == null ? message : message + ": " + detail;
    }
}
