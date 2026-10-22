/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.io.Serial;
import java.util.Objects;

/**
 * A profile, verification record or lookup refused as a whole (PM-CLIENT-5).
 * <p>
 * A profile is never used in part: the refusal names the document, the closed outcome code, and the JSON Pointer of the
 * value that broke a rule, so the operator can go to the one value. The codes are the vocabulary of the specification:
 * {@link Outcome#PROFILE_INVALID} for a document that breaks the schema or one of the hardening rules,
 * {@link Outcome#PROFILE_UNREADABLE} for content that is not JSON or breaks a bound of the reader,
 * {@link Outcome#PROFILE_UNVERIFIED} for a machine or project profile that does not count for want of a valid record,
 * {@link Outcome#RECORD_INVALID} for a record that breaks its closed shape, and
 * {@link Outcome#HARNESS_VERSION_UNSUPPORTED} for an enrolled harness outside the profile's supported range.
 *
 * @since 0.1
 */
public final class ProfileRefusal extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why a profile was refused. The set is closed. */
    public enum Outcome {
        /** The document does not have the shape of a profile, or breaks one of its rules. */
        PROFILE_INVALID("profile_invalid"),
        /** The document is not readable, or breaks a bound of the reader. */
        PROFILE_UNREADABLE("profile_unreadable"),
        /** A machine or project profile has no valid verification record, or one that fails its HMAC. */
        PROFILE_UNVERIFIED("profile_unverified"),
        /** A verification record does not have its closed shape. */
        RECORD_INVALID("record_invalid"),
        /** The enrolled harness is outside the supported version range of its profile. */
        HARNESS_VERSION_UNSUPPORTED("harness_version_unsupported");

        private final String code;

        Outcome(String code) {
            this.code = code;
        }

        /** @return the code of the outcome in a representation */
        public String code() {
            return code;
        }
    }

    private final Outcome outcome;
    private final String document;
    private final String pointer;
    private final String detail;

    private ProfileRefusal(Outcome outcome, String document, String pointer, String detail, Throwable cause) {
        super(message(outcome, document, pointer, detail), cause);
        this.outcome = outcome;
        this.document = Objects.requireNonNull(document, "document");
        this.pointer = pointer == null ? "" : pointer;
        this.detail = detail == null ? "" : detail;
    }

    /**
     * @param document the document, as a representation names it
     * @param pointer  the JSON Pointer of the value that broke a rule
     * @param detail   what is wrong with it
     * @return the refusal
     */
    public static ProfileRefusal invalid(String document, String pointer, String detail) {
        return new ProfileRefusal(Outcome.PROFILE_INVALID, document, pointer, detail, null);
    }

    /**
     * @param document the document, as a representation names it
     * @param detail   why it cannot be read
     * @return the refusal
     */
    public static ProfileRefusal unreadable(String document, String detail) {
        return new ProfileRefusal(Outcome.PROFILE_UNREADABLE, document, "", detail, null);
    }

    /**
     * @param document the document, as a representation names it
     * @param detail   why it does not count
     * @return the refusal
     */
    public static ProfileRefusal unverified(String document, String detail) {
        return new ProfileRefusal(Outcome.PROFILE_UNVERIFIED, document, "", detail, null);
    }

    /**
     * @param document the record, as a representation names it
     * @param pointer  the JSON Pointer of the value that broke a rule
     * @param detail   what is wrong with it
     * @return the refusal
     */
    public static ProfileRefusal recordInvalid(String document, String pointer, String detail) {
        return new ProfileRefusal(Outcome.RECORD_INVALID, document, pointer, detail, null);
    }

    /**
     * @param document the profile, as a representation names it
     * @param pointer  the JSON Pointer of the supported range
     * @param detail   the version the enrolled harness reports
     * @return the refusal
     */
    public static ProfileRefusal versionUnsupported(String document, String pointer, String detail) {
        return new ProfileRefusal(Outcome.HARNESS_VERSION_UNSUPPORTED, document, pointer, detail, null);
    }

    /** @return why the profile was refused */
    public Outcome outcome() {
        return outcome;
    }

    /** @return the code of {@link #outcome()} in a representation */
    public String code() {
        return outcome.code();
    }

    /** @return the refused document, as a representation names it */
    public String document() {
        return document;
    }

    /** @return the JSON Pointer of the value that broke a rule; empty for a refusal of the document as a whole */
    public String pointer() {
        return pointer;
    }

    /** @return what is wrong, naming the value */
    public String detail() {
        return detail;
    }

    private static String message(Outcome outcome, String document, String pointer, String detail) {
        var where = pointer == null || pointer.isEmpty() ? "" : " at '%s'".formatted(pointer);
        return "%s: %s%s: %s".formatted(document, outcome.code(), where, detail == null ? "" : detail);
    }
}
