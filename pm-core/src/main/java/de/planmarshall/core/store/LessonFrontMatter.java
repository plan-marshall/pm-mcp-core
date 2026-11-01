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

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * The front matter of a lesson of the project lesson store (Store Schemas § Lesson Record, the {@code
 * LessonFrontMatter} schema; PM-IMPL-2).
 * <p>
 * The record states the rules the specification states beside the schema and refuses a violation with an
 * {@link IllegalArgumentException}: a {@code wire_check} remedy carries its {@code check} and no other kind does
 * (detail {@code remedy_check_mismatch}); {@code retired} is set exactly when the status is {@code retired}; a
 * {@code rule} is set exactly for the category {@code arch-constraint}, and {@code recurrence_count} and
 * {@code last_seen} only for it; {@code sources} and {@code revisions} are never empty; and {@code revision} is the
 * {@code n} of the last entry of {@code revisions}. {@code derived_from_untrusted} is a primitive the record only
 * carries: the server sets it when it writes the lesson, and it never turns back to {@code false}.
 * <p>
 * The members are declared in the order of the schema; the codec writes them in that order. A member the schema
 * admits as {@code null} is a {@link Nullable} component, a member the schema does not require is empty when it was
 * absent, and an enumeration is one of the closed values of this type.
 *
 * @param formatVersion       the format version, always {@value #FORMAT_VERSION}
 * @param lessonId            the identifier of the lesson
 * @param title               the title, the lesson's short name
 * @param component           the component the lesson is about
 * @param category            the kind of the lesson
 * @param bundle              the bundle of the component; {@code null} when the lesson names none
 * @param rule                the recurrence key of an {@code arch-constraint} lesson; {@code null} otherwise
 * @param remedy              what would have prevented the mistake
 * @param recurrenceCount     how often an {@code arch-constraint} lesson recurred; {@code null} otherwise
 * @param lastSeen            the UTC date an {@code arch-constraint} lesson was last seen; {@code null} otherwise
 * @param status              the lifecycle status of the lesson
 * @param provenance          what the lesson derives from
 * @param derivedFromUntrusted whether the facts of the task that wrote the lesson delivered untrusted text
 * @param audience            the audience the lesson was classified for
 * @param lastFilingFailure   the failure of the last upstream filing; {@code null} when none failed
 * @param createdAt           the instant the lesson was created
 * @param toolVersion         the release version of the runtime that created the lesson
 * @param promotions          the destinations that absorbed the lesson, in order
 * @param revision            the counter of the current body
 * @param sources             the evidence of the current revision's body
 * @param revisions           the append-only history of the bodies, the last entry being the current revision
 * @param mergedFrom          the lessons merged into this one, in order
 * @param claim               the claim of the lesson by a plan; {@code null} when it is not claimed
 * @param retired             the retirement of the lesson; {@code null} while it is not retired
 * @since 0.1
 */
@Builder(toBuilder = true)
public record LessonFrontMatter(int formatVersion, LessonId lessonId, String title, String component,
Category category, @Nullable String bundle, @Nullable String rule, Remedy remedy,
@Nullable Integer recurrenceCount, @Nullable LocalDate lastSeen, Status status, Provenance provenance,
boolean derivedFromUntrusted, Audience audience, @Nullable LastFilingFailure lastFilingFailure,
Instant createdAt, String toolVersion, List<Promotion> promotions, int revision, List<Source> sources,
List<Revision> revisions, List<MergedFrom> mergedFrom, @Nullable Claim claim, @Nullable Retired retired) {

    /** The format version of a lesson record that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The shortest statement of a remedy the schema admits. */
    public static final int MIN_REMEDY_STATEMENT = 10;

    /** The longest statement of a remedy the schema admits. */
    public static final int MAX_REMEDY_STATEMENT = 1000;

    /** The shortest check of a {@code wire_check} remedy the schema admits. */
    public static final int MIN_REMEDY_CHECK = 1;

    /** The longest check of a {@code wire_check} remedy the schema admits. */
    public static final int MAX_REMEDY_CHECK = 512;

    /** The smallest recurrence count the schema admits. */
    public static final int MIN_RECURRENCE_COUNT = 1;

    /** The smallest revision counter the schema admits. */
    public static final int MIN_REVISION = 1;

    /** The smallest number of sources a lesson and a revision carry. */
    public static final int MIN_SOURCES = 1;

    /** The smallest number of revisions a lesson carries. */
    public static final int MIN_REVISIONS = 1;

    /** The shape of a SHA-256 digest. */
    public static final String DIGEST_PATTERN = "^[0-9a-f]{64}$";

    /** The shape of the {@code plan_id} of a claim, as the schema states it. */
    public static final String PLAN_ID_PATTERN = "^[a-z0-9][a-z0-9-]{1,62}[a-z0-9]$";

    /** The detail of a remedy whose {@code check} is set for the wrong kind. */
    public static final String REMEDY_CHECK_MISMATCH = "remedy_check_mismatch";

    private static final Pattern DIGEST = Pattern.compile(DIGEST_PATTERN);
    private static final Pattern PLAN_ID = Pattern.compile(PLAN_ID_PATTERN);

    /**
     * @param formatVersion        the format version, always {@value #FORMAT_VERSION}
     * @param lessonId             the identifier of the lesson
     * @param title                the title, the lesson's short name
     * @param component            the component the lesson is about
     * @param category             the kind of the lesson
     * @param bundle               the bundle of the component; {@code null} when the lesson names none
     * @param rule                 the recurrence key of an {@code arch-constraint} lesson; {@code null} otherwise
     * @param remedy               what would have prevented the mistake
     * @param recurrenceCount      how often an {@code arch-constraint} lesson recurred; {@code null} otherwise
     * @param lastSeen             the UTC date an {@code arch-constraint} lesson was last seen; {@code null} otherwise
     * @param status               the lifecycle status of the lesson
     * @param provenance           what the lesson derives from
     * @param derivedFromUntrusted whether the facts of the task that wrote the lesson delivered untrusted text
     * @param audience             the audience the lesson was classified for
     * @param lastFilingFailure    the failure of the last upstream filing; {@code null} when none failed
     * @param createdAt            the instant the lesson was created
     * @param toolVersion          the release version of the runtime that created the lesson
     * @param promotions           the destinations that absorbed the lesson, in order
     * @param revision             the counter of the current body
     * @param sources              the evidence of the current revision's body
     * @param revisions            the append-only history of the bodies, the last entry being the current revision
     * @param mergedFrom           the lessons merged into this one, in order
     * @param claim                the claim of the lesson by a plan; {@code null} when it is not claimed
     * @param retired              the retirement of the lesson; {@code null} while it is not retired
     * @throws IllegalArgumentException if a rule the specification states beside the schema is violated
     */
    public LessonFrontMatter {
        Objects.requireNonNull(lessonId, "lessonId");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(audience, "audience");
        Objects.requireNonNull(remedy, "remedy");
        requireText(title, "title");
        requireText(component, "component");
        requireText(toolVersion, "tool_version");
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "A lesson front matter is format version %d, not %d".formatted(FORMAT_VERSION, formatVersion));
        }
        if ((category == Category.ARCH_CONSTRAINT) != (rule != null)) {
            throw new IllegalArgumentException(
                    "A rule is set exactly for the category 'arch-constraint': category '%s', rule '%s'"
                            .formatted(category.wireName(), rule));
        }
        if (category != Category.ARCH_CONSTRAINT && (recurrenceCount != null || lastSeen != null)) {
            throw new IllegalArgumentException(
                    "A recurrence count and a last-seen date are set only for the category 'arch-constraint', not '%s'"
                            .formatted(category.wireName()));
        }
        if (recurrenceCount != null && recurrenceCount < MIN_RECURRENCE_COUNT) {
            throw new IllegalArgumentException(
                    "A recurrence count is at least %d: %d".formatted(MIN_RECURRENCE_COUNT, recurrenceCount));
        }
        if ((status == Status.RETIRED) != (retired != null)) {
            throw new IllegalArgumentException(
                    "A retirement is set exactly when the status is 'retired': status '%s'"
                            .formatted(status.wireName()));
        }
        if (revision < MIN_REVISION) {
            throw new IllegalArgumentException("A revision counter is at least %d: %d".formatted(MIN_REVISION, revision));
        }
        sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (sources.size() < MIN_SOURCES) {
            throw new IllegalArgumentException(
                    "A lesson carries at least %d source".formatted(MIN_SOURCES));
        }
        revisions = List.copyOf(Objects.requireNonNull(revisions, "revisions"));
        if (revisions.size() < MIN_REVISIONS) {
            throw new IllegalArgumentException(
                    "A lesson carries at least %d revision".formatted(MIN_REVISIONS));
        }
        promotions = List.copyOf(Objects.requireNonNull(promotions, "promotions"));
        mergedFrom = List.copyOf(Objects.requireNonNull(mergedFrom, "mergedFrom"));
        var last = revisions.getLast();
        if (last.n() != revision) {
            throw new IllegalArgumentException(
                    "The revision %d is the n of the last revisions entry, which is %d".formatted(revision, last.n()));
        }
    }

    private static void requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("'%s' is at least one character".formatted(name));
        }
    }

    private static void requireDigest(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!DIGEST.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is a SHA-256 digest: '%s'".formatted(name, value));
        }
    }

    /**
     * The kind of a lesson, the {@code category} of the schema.
     *
     * @since 0.1
     */
    public enum Category {
        /** A defect. */
        BUG("bug"),
        /** An improvement of a behaviour that was not a defect. */
        IMPROVEMENT("improvement"),
        /** A pattern to avoid. */
        ANTI_PATTERN("anti-pattern"),
        /** A constraint of the architecture that recurs. */
        ARCH_CONSTRAINT("arch-constraint");

        private final String wireName;

        Category(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Category> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The lifecycle status of a lesson, the {@code status} of the schema.
     *
     * @since 0.1
     */
    public enum Status {
        /** A live lesson. */
        ACTIVE("active"),
        /** An upstream-audience lesson awaiting the operator's reviewed release. */
        UPSTREAM_PENDING("upstream_pending"),
        /** A retired lesson, its record kept in the same file. */
        RETIRED("retired");

        private final String wireName;

        Status(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Status> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * What a lesson derives from, the {@code provenance} of the schema.
     *
     * @since 0.1
     */
    public enum Provenance {
        /** A run artifact: a log, a report, a diff. */
        RUN_ARTIFACT("run_artifact"),
        /** A tool return. */
        TOOL_RETURN("tool_return"),
        /** An audited operator action. */
        OPERATOR_ACTION("operator_action");

        private final String wireName;

        Provenance(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Provenance> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The audience a lesson was classified for, the {@code audience} of the schema.
     *
     * @since 0.1
     */
    public enum Audience {
        /** A lesson of the project. */
        LOCAL("local"),
        /** A lesson of a component the distribution ships. */
        UPSTREAM("upstream");

        private final String wireName;

        Audience(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Audience> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The failure of an upstream filing, the {@code last_filing_failure} of the schema.
     *
     * @since 0.1
     */
    public enum LastFilingFailure {
        /** The provider was unreachable. */
        OFFLINE("offline"),
        /** The provider refused the filing. */
        FORBIDDEN("forbidden"),
        /** The provider's rate limit was reached. */
        RATE_LIMITED("rate_limited"),
        /** The operator or the server refused the filing. */
        REFUSED("refused");

        private final String wireName;

        LastFilingFailure(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a record */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a record
         * @return the value of that name; empty if no value has it
         */
        public static Optional<LastFilingFailure> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One piece of evidence a lesson's body was derived from, the {@code source} of the schema.
     *
     * @param kind   what kind of artifact the source is
     * @param ref    the identifier or store path the server resolved
     * @param digest the SHA-256 of the cited artifact where it is a file; {@code null} otherwise
     * @since 0.1
     */
    public record Source(Source.Kind kind, String ref, @Nullable String digest) {

        /**
         * @param kind   what kind of artifact the source is
         * @param ref    the identifier or store path the server resolved
         * @param digest the SHA-256 of the cited artifact where it is a file; {@code null} otherwise
         * @throws IllegalArgumentException if the reference is empty or the digest is not a SHA-256
         */
        public Source {
            Objects.requireNonNull(kind, "kind");
            requireText(ref, "ref");
            if (digest != null) {
                requireDigest(digest, "digest");
            }
        }

        /**
         * The kind of a source, the {@code kind} of the schema's source.
         *
         * @since 0.1
         */
        public enum Kind {
            /** A plan. */
            PLAN("plan"),
            /** A finding. */
            FINDING("finding"),
            /** A job. */
            JOB("job"),
            /** A landing. */
            LANDING("landing"),
            /** A log. */
            LOG("log"),
            /** An audited operator action. */
            OPERATOR_ACTION("operator_action"),
            /** Another lesson. */
            LESSON("lesson");

            private final String wireName;

            Kind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a record */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Kind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * What would have prevented a lesson's mistake, the {@code remedy} of the schema.
     *
     * @param kind      the kind of the remedy
     * @param statement the check, the standard or the pointer, stated so that a plan can implement it
     * @param check     the existing check of a {@code wire_check}; {@code null} for every other kind
     * @since 0.1
     */
    public record Remedy(Remedy.Kind kind, String statement, @Nullable String check) {

        /**
         * @param kind      the kind of the remedy
         * @param statement the check, the standard or the pointer, stated so that a plan can implement it
         * @param check     the existing check of a {@code wire_check}; {@code null} for every other kind
         * @throws IllegalArgumentException if the statement or the check is outside its bounds, or the check is set
         *                                  for the wrong kind (detail {@value #REMEDY_CHECK_MISMATCH})
         */
        public Remedy {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(statement, "statement");
            if (statement.length() < MIN_REMEDY_STATEMENT || statement.length() > MAX_REMEDY_STATEMENT) {
                throw new IllegalArgumentException(
                        "A remedy statement is %d to %d characters: %d".formatted(MIN_REMEDY_STATEMENT,
                                MAX_REMEDY_STATEMENT, statement.length()));
            }
            if ((kind == Kind.WIRE_CHECK) != (check != null)) {
                throw new IllegalArgumentException(
                        "%s: a check is set exactly for the kind 'wire_check', not for '%s'"
                                .formatted(REMEDY_CHECK_MISMATCH, kind.wireName()));
            }
            if (check != null && (check.length() < MIN_REMEDY_CHECK || check.length() > MAX_REMEDY_CHECK)) {
                throw new IllegalArgumentException(
                        "A remedy check is %d to %d characters: %d".formatted(MIN_REMEDY_CHECK, MAX_REMEDY_CHECK,
                                check.length()));
            }
        }

        /**
         * The kind of a remedy, the {@code kind} of the schema's remedy.
         *
         * @since 0.1
         */
        public enum Kind {
            /** A deterministic check to add. */
            CHECK("check"),
            /** An existing check that ran too late or not at all. */
            WIRE_CHECK("wire_check"),
            /** A judgment rule for the review roles. */
            REVIEW_STANDARD("review_standard"),
            /** A reference to where the information was. */
            POINTER("pointer");

            private final String wireName;

            Kind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a record */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Kind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * A destination that absorbed a lesson, one entry of the {@code promotions} of the schema.
     *
     * @param kind the kind of the destination
     * @param ref  the reference of the destination
     * @param at   the instant the destination absorbed the lesson
     * @since 0.1
     */
    public record Promotion(Promotion.Kind kind, String ref, Instant at) {

        /**
         * @param kind the kind of the destination
         * @param ref  the reference of the destination
         * @param at   the instant the destination absorbed the lesson
         */
        public Promotion {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(at, "at");
        }

        /**
         * The kind of a promotion destination, the {@code kind} of the schema's promotion.
         *
         * @since 0.1
         */
        public enum Kind {
            /** An epic. */
            EPIC("epic"),
            /** A requirement. */
            REQUIREMENT("requirement"),
            /** A plan. */
            PLAN("plan"),
            /** An issue. */
            ISSUE("issue");

            private final String wireName;

            Kind(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a record */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Kind> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }

    /**
     * One entry of the append-only history of a lesson's bodies, the {@code revisions} of the schema.
     *
     * @param n          the counter of the revision
     * @param at         the instant the revision was written
     * @param sources    the evidence of that revision's body
     * @param bodySha256 the SHA-256 of the UTF-8 body of that revision
     * @since 0.1
     */
    @Builder
    public record Revision(int n, Instant at, List<Source> sources, String bodySha256) {

        /**
         * @param n          the counter of the revision
         * @param at         the instant the revision was written
         * @param sources    the evidence of that revision's body
         * @param bodySha256 the SHA-256 of the UTF-8 body of that revision
         * @throws IllegalArgumentException if the counter is below its minimum, the sources are empty or the digest
         *                                  is not a SHA-256
         */
        public Revision {
            if (n < MIN_REVISION) {
                throw new IllegalArgumentException("A revision counter is at least %d: %d".formatted(MIN_REVISION, n));
            }
            Objects.requireNonNull(at, "at");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
            if (sources.size() < MIN_SOURCES) {
                throw new IllegalArgumentException("A revision carries at least %d source".formatted(MIN_SOURCES));
            }
            requireDigest(bodySha256, "body_sha256");
        }
    }

    /**
     * One lesson merged into this one, one entry of the {@code merged_from} of the schema.
     *
     * @param lessonId the lesson that was merged into this one
     * @param at       the instant of the merge
     * @param by       the queue task id of the lessons task, or the operator principal
     * @since 0.1
     */
    public record MergedFrom(LessonId lessonId, Instant at, String by) {

        /**
         * @param lessonId the lesson that was merged into this one
         * @param at       the instant of the merge
         * @param by       the queue task id of the lessons task, or the operator principal
         */
        public MergedFrom {
            Objects.requireNonNull(lessonId, "lessonId");
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(by, "by");
        }
    }

    /**
     * The claim of a lesson by a plan, the {@code claim} of the schema.
     *
     * @param planId    the plan that claimed the lesson
     * @param claimedAt the instant the claim was written
     * @since 0.1
     */
    public record Claim(String planId, Instant claimedAt) {

        /**
         * @param planId    the plan that claimed the lesson
         * @param claimedAt the instant the claim was written
         * @throws IllegalArgumentException if the plan id does not match the schema's pattern
         */
        public Claim {
            Objects.requireNonNull(planId, "planId");
            if (!PLAN_ID.matcher(planId).matches()) {
                throw new IllegalArgumentException("A plan id matches %s: '%s'".formatted(PLAN_ID_PATTERN, planId));
            }
            Objects.requireNonNull(claimedAt, "claimedAt");
        }
    }

    /**
     * The retirement of a lesson, the {@code retired} of the schema; the record outlives the retirement in the same
     * file.
     *
     * @param at             the instant of the retirement
     * @param by             the step id, plan id, entry id, review or operator principal that retired the lesson
     * @param verdict        the closed verdict of the retirement
     * @param into           the lesson it was merged into or superseded by; {@code null} otherwise
     * @param reason         the reason of the retirement
     * @param coveringClause the clause that covers the lesson; {@code null} unless the verdict requires it
     * @param coveringInput  the concrete input that clause's worked example resolves; {@code null} unless required
     * @param lessonState    the state the lesson was in when it was retired
     * @since 0.1
     */
    @Builder
    public record Retired(Instant at, String by, Retired.Verdict verdict, @Nullable LessonId into, String reason,
    @Nullable String coveringClause, @Nullable String coveringInput, Retired.LessonState lessonState) {

        /**
         * @param at             the instant of the retirement
         * @param by             the step id, plan id, entry id, review or operator principal that retired the lesson
         * @param verdict        the closed verdict of the retirement
         * @param into           the lesson it was merged into or superseded by; {@code null} otherwise
         * @param reason         the reason of the retirement
         * @param coveringClause the clause that covers the lesson; {@code null} unless the verdict requires it
         * @param coveringInput  the concrete input that clause's worked example resolves; {@code null} unless required
         * @param lessonState    the state the lesson was in when it was retired
         */
        public Retired {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(verdict, "verdict");
            Objects.requireNonNull(lessonState, "lessonState");
            requireText(by, "by");
            requireText(reason, "reason");
        }

        /**
         * The verdict of a retirement, the closed set of the schema.
         *
         * @since 0.1
         */
        public enum Verdict {
            /** The plan landed its changes. */
            APPLIED("applied"),
            /** A clause covers the lesson completely. */
            COMPLETELY_COVERED("completely_covered"),
            /** Another lesson states the same. */
            REDUNDANT("redundant"),
            /** Another lesson supersedes it. */
            SUPERSEDED("superseded"),
            /** It was merged into another lesson. */
            MERGED("merged"),
            /** It no longer applies. */
            OBSOLETE("obsolete");

            private final String wireName;

            Verdict(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a record */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<Verdict> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }

        /**
         * The state a lesson was in when it was retired, the {@code retired.lesson_state} of the schema.
         *
         * @since 0.1
         */
        public enum LessonState {
            /** The lesson resolved {@code found}. */
            FOUND("found"),
            /** The lesson resolved {@code present_unreadable} and was retired by an operator act. */
            PRESENT_UNREADABLE("present_unreadable");

            private final String wireName;

            LessonState(String wireName) {
                this.wireName = wireName;
            }

            /** @return the name of the value in a record */
            public String wireName() {
                return wireName;
            }

            /**
             * @param wireName the name of a value in a record
             * @return the value of that name; empty if no value has it
             */
            public static Optional<LessonState> fromWireName(String wireName) {
                return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
            }
        }
    }
}
