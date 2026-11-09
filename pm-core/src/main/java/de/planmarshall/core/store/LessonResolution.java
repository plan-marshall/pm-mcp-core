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

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What one lesson of the project lesson store resolves to (Store Schemas § Lesson Record, One Resolver, Four States;
 * PM-IMPL-7): the four resolution states — {@link Found}, {@link Absent}, {@link PresentUnreadable} and
 * {@link EmptyShell} — and the three integrity states of the digest sidecar, {@link StoreTampered},
 * {@link DeletedOutOfBand} and {@link Untracked}.
 * <p>
 * A lesson is listed under its {@link LessonId} and resolves to exactly one value of this sealed type, always under
 * the id it was listed under — a front matter that states another {@code lesson_id} does not move the lesson. A
 * {@link PresentUnreadable} lesson is listed with its path and is never reported as {@link Absent}, and an
 * {@link EmptyShell} is a content state, never a verdict on the lesson.
 * <p>
 * The three integrity states come from the digest sidecar ({@link ScopeIntegrity}). A lock-free read reports a file
 * that differs from its recorded digest as {@link Found} with {@link StoreIntegrity#DIGEST_UNVERIFIED} and decides
 * nothing (PM-IMPL-7); only the locked read raises {@link StoreTampered} and {@link Untracked}, and reports a file that
 * is gone while its entry remains as {@link DeletedOutOfBand}. The one integrity state a lock-free read does decide is
 * {@link DeletedOutOfBand}: a sidecar entry whose file is gone is an observed absence, never a digest verdict, and it is
 * never {@link Absent}.
 * <p>
 * The same file carries the outcomes of the store's transitions: the successful {@link Claimed}, {@link Released},
 * {@link Retired} and {@link Revised}, and {@link Unavailable} for every state in which a transition changes nothing.
 * A refusal is a value here, never an exception: the caller decides what a state means for its own transition.
 *
 * @since 0.1
 */
public sealed interface LessonResolution {

    /** The name of the id component in the refusal messages of the records of this type. */
    String LESSON_ID = "lessonId";

    /** The name of the lesson component in the refusal messages of the records of this type. */
    String LESSON = "lesson";

    /** @return the lesson this resolution is about, the id it is listed under */
    LessonId lessonId();

    /** @return the state of the lesson */
    State state();

    /**
     * The state a lesson resolves to, as the specification names it.
     *
     * @since 0.1
     */
    enum State {
        /** The lesson exists, its front matter decodes and it carries a body. */
        FOUND("found"),
        /** No file and no sidecar entry: the lesson never existed or its retirement removed nothing. */
        ABSENT("absent"),
        /** A file exists, but its front matter is missing or invalid; it is listed with its path. */
        PRESENT_UNREADABLE("present_unreadable"),
        /** The front matter decodes and the body carries no non-whitespace character. */
        EMPTY_SHELL("empty_shell"),
        /** The file differs from its recorded size or SHA-256, decided under the lock of the store. */
        STORE_TAMPERED("store_tampered"),
        /** A sidecar entry whose file is gone: the file was removed outside a transaction. */
        DELETED_OUT_OF_BAND("deleted_out_of_band"),
        /** A file without a sidecar entry: no transaction of the store wrote it. */
        UNTRACKED("untracked");

        private final String wireName;

        State(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the state in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * The lesson exists and decodes. The content is usable only where the integrity is
     * {@link StoreIntegrity#VERIFIED}: a {@link StoreIntegrity#DIGEST_UNVERIFIED} or {@link StoreIntegrity#UNCHECKED}
     * read is a candidate the caller must not decide on (PM-IMPL-7, PM-WATCH-STORE-14).
     *
     * @param lessonId  the id the lesson is listed under
     * @param lesson    the lesson
     * @param path      the file the lesson was read from
     * @param integrity what the read can say about the content
     * @since 0.1
     */
    @Builder
    record Found(LessonId lessonId, Lesson lesson, Path path, StoreIntegrity integrity) implements LessonResolution {

        /** @throws NullPointerException if a component is {@code null} */
        public Found {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(lesson, LESSON);
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(integrity, "integrity");
        }

        @Override
        public State state() {
            return State.FOUND;
        }
    }

    /**
     * The lesson does not exist: neither a file nor a sidecar entry names it.
     *
     * @param lessonId the id the lesson is listed under
     * @since 0.1
     */
    record Absent(LessonId lessonId) implements LessonResolution {

        /** @throws NullPointerException if the id is {@code null} */
        public Absent {
            Objects.requireNonNull(lessonId, LESSON_ID);
        }

        @Override
        public State state() {
            return State.ABSENT;
        }
    }

    /**
     * A file exists, but its front matter is missing or invalid. The lesson is listed with its path and is never
     * {@link Absent}: a caller reports it as {@code present_unreadable} and never acts on a false absence.
     *
     * @param lessonId the id the lesson is listed under
     * @param path     the file that does not decode
     * @param reason   why the file does not decode
     * @param detail   what exactly could not be read
     * @since 0.1
     */
    @Builder
    record PresentUnreadable(LessonId lessonId, Path path, LessonCodec.UnreadableReason reason, String detail)
            implements LessonResolution {

        /** @throws NullPointerException if a component is {@code null} */
        public PresentUnreadable {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }

        @Override
        public State state() {
            return State.PRESENT_UNREADABLE;
        }
    }

    /**
     * The front matter decodes, but the body carries no non-whitespace character. It is a content state, never a
     * verdict: a lesson in it is found and addressable. The integrity travels on it as on {@link Found}, so a blank body
     * that differs from its sidecar entry is still reported as unverified.
     *
     * @param lessonId the id the lesson is listed under
     * @param lesson   the lesson
     * @param path     the file the lesson was read from
     * @param integrity what the read can say about the content
     * @since 0.1
     */
    @Builder
    record EmptyShell(LessonId lessonId, Lesson lesson, Path path, StoreIntegrity integrity)
            implements LessonResolution {

        /** @throws NullPointerException if a component is {@code null} */
        public EmptyShell {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(lesson, LESSON);
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(integrity, "integrity");
        }

        @Override
        public State state() {
            return State.EMPTY_SHELL;
        }
    }

    /**
     * The file differs from the size or SHA-256 its sidecar entry records, decided under the lock of the store after
     * its journal was recovered: the file was edited outside a transaction (PM-IMPL-7). Only the operator's
     * {@code accept-edit} re-baselines it, so no store transition touches the lesson.
     *
     * @param lessonId the id the lesson is listed under
     * @param path     the file that differs from its entry
     * @param recorded the digest the sidecar records
     * @param current  the digest of the file as it is; {@code null} if the file does not exist
     * @since 0.1
     */
    @Builder
    record StoreTampered(LessonId lessonId, Path path, StoreDigest recorded, @Nullable StoreDigest current)
            implements LessonResolution {

        /** @throws NullPointerException if a component other than {@code current} is {@code null} */
        public StoreTampered {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(recorded, "recorded");
        }

        @Override
        public State state() {
            return State.STORE_TAMPERED;
        }
    }

    /**
     * A sidecar entry names the lesson, but its file is gone: it was removed outside a transaction. The lesson is
     * never {@link Absent}, and nothing retries it.
     *
     * @param lessonId the id the lesson is listed under
     * @since 0.1
     */
    record DeletedOutOfBand(LessonId lessonId) implements LessonResolution {

        /** @throws NullPointerException if the id is {@code null} */
        public DeletedOutOfBand {
            Objects.requireNonNull(lessonId, LESSON_ID);
        }

        @Override
        public State state() {
            return State.DELETED_OUT_OF_BAND;
        }
    }

    /**
     * A file exists, but the sidecar has no entry for it: no transaction of the store wrote it.
     *
     * @param lessonId the id the lesson is listed under
     * @param path     the file without an entry
     * @since 0.1
     */
    record Untracked(LessonId lessonId, Path path) implements LessonResolution {

        /** @throws NullPointerException if a component is {@code null} */
        public Untracked {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(path, "path");
        }

        @Override
        public State state() {
            return State.UNTRACKED;
        }
    }

    /**
     * The outcome of a transition of the store: one of the four successful outcomes, or {@link Unavailable} for every
     * state in which the transition changed nothing. A transition that cannot act is never an exception: it reports
     * the state it found, so a caller can retry, report it, or leave the lesson alone.
     *
     * @since 0.1
     */
    sealed interface Outcome permits Claimed, Released, Retired, Revised, Unavailable {

        /** @return the lesson the transition was about */
        LessonId lessonId();
    }

    /**
     * The lesson was claimed by the plan.
     *
     * @param lessonId the id the lesson is listed under
     * @param lesson   the lesson as it is recorded after the transition
     * @since 0.1
     */
    record Claimed(LessonId lessonId, Lesson lesson) implements Outcome {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Claimed {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(lesson, LESSON);
        }
    }

    /**
     * The claim was released: the lesson carries no claim and is not retired.
     *
     * @param lessonId the id the lesson is listed under
     * @param lesson   the lesson as it is recorded after the transition
     * @since 0.1
     */
    record Released(LessonId lessonId, Lesson lesson) implements Outcome {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Released {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(lesson, LESSON);
        }
    }

    /**
     * The lesson was retired, or was retired already by the same plan with the verdict {@code applied} and was left
     * untouched (the end of a claim is repeatable).
     *
     * @param lessonId the id the lesson is listed under
     * @param lesson   the lesson as it is recorded after the transition
     * @since 0.1
     */
    record Retired(LessonId lessonId, Lesson lesson) implements Outcome {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Retired {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(lesson, LESSON);
        }
    }

    /**
     * The body was replaced by its next revision.
     *
     * @param lessonId the id the lesson is listed under
     * @param lesson   the lesson as it is recorded after the transition
     * @since 0.1
     */
    record Revised(LessonId lessonId, Lesson lesson) implements Outcome {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Revised {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(lesson, LESSON);
        }
    }

    /**
     * The transition changed nothing and reports the state it found. {@code claimingPlan} is set exactly for
     * {@link Refusal#CLAIMED}: it names the plan that holds the claim, which is what the caller reports beside the
     * state.
     *
     * @param lessonId     the lesson the transition was about
     * @param refusal      why the transition changed nothing
     * @param claimingPlan the plan that holds the claim; {@code null} for every refusal but {@link Refusal#CLAIMED}
     * @since 0.1
     */
    record Unavailable(LessonId lessonId, Refusal refusal, @Nullable String claimingPlan) implements Outcome {

        /**
         * @throws NullPointerException     if the lesson or the refusal is {@code null}
         * @throws IllegalArgumentException if a claiming plan is named for another refusal than
         *                                  {@link Refusal#CLAIMED}, or none for {@link Refusal#CLAIMED}
         */
        public Unavailable {
            Objects.requireNonNull(lessonId, LESSON_ID);
            Objects.requireNonNull(refusal, "refusal");
            if ((refusal == Refusal.CLAIMED) != (claimingPlan != null)) {
                throw new IllegalArgumentException(
                        "A claiming plan is named exactly for the refusal 'claimed', not for '%s'"
                                .formatted(refusal.wireName()));
            }
        }
    }

    /**
     * Why a transition changed nothing: the resolution states of a lesson the transition could not act on, plus the
     * three states only a transition can find.
     *
     * @since 0.1
     */
    enum Refusal {
        /** No file and no sidecar entry names the lesson. */
        ABSENT(State.ABSENT),
        /** The file exists and its front matter is missing or invalid. */
        PRESENT_UNREADABLE(State.PRESENT_UNREADABLE),
        /** The front matter decodes and the body carries no non-whitespace character. */
        EMPTY_SHELL(State.EMPTY_SHELL),
        /** The file differs from its sidecar entry. */
        STORE_TAMPERED(State.STORE_TAMPERED),
        /** A sidecar entry whose file is gone. */
        DELETED_OUT_OF_BAND(State.DELETED_OUT_OF_BAND),
        /** A file without a sidecar entry. */
        UNTRACKED(State.UNTRACKED),
        /** The lesson is found, but another plan holds its claim. */
        CLAIMED(null),
        /** The lesson is found but retired; its record stays in place. */
        RETIRED(null),
        /** The transition needs the caller's claim and the lesson carries none. */
        UNCLAIMED(null);

        @Nullable
        private final State state;

        Refusal(@Nullable State state) {
            this.state = state;
        }

        /**
         * @param state a state of a resolution
         * @return the refusal that names it
         */
        public static Refusal of(State state) {
            return switch (Objects.requireNonNull(state, "state")) {
                case FOUND -> throw new IllegalArgumentException("A found lesson is not a refusal");
                case ABSENT -> ABSENT;
                case PRESENT_UNREADABLE -> PRESENT_UNREADABLE;
                case EMPTY_SHELL -> EMPTY_SHELL;
                case STORE_TAMPERED -> STORE_TAMPERED;
                case DELETED_OUT_OF_BAND -> DELETED_OUT_OF_BAND;
                case UNTRACKED -> UNTRACKED;
            };
        }

        /** @return the name of the refusal in a representation, the state's name for a resolution state */
        public String wireName() {
            return state == null ? name().toLowerCase(Locale.ROOT) : state.wireName();
        }
    }
}
