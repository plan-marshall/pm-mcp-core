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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.Builder;

import de.planmarshall.core.store.LessonFrontMatter.Audience;
import de.planmarshall.core.store.LessonFrontMatter.Category;
import de.planmarshall.core.store.LessonFrontMatter.Claim;
import de.planmarshall.core.store.LessonFrontMatter.Provenance;
import de.planmarshall.core.store.LessonFrontMatter.Remedy;
import de.planmarshall.core.store.LessonFrontMatter.Retired;
import de.planmarshall.core.store.LessonFrontMatter.Retired.LessonState;
import de.planmarshall.core.store.LessonFrontMatter.Retired.Verdict;
import de.planmarshall.core.store.LessonFrontMatter.Revision;
import de.planmarshall.core.store.LessonFrontMatter.Source;
import de.planmarshall.core.store.LessonFrontMatter.Status;

/**
 * The lesson store of a project (Store Schemas § Lesson Record; PM-IMPL-2, PM-IMPL-7): one Markdown file
 * {@code <lesson_id>.md} per lesson below {@code lessons/} of the per-project machine store, the digest sidecar
 * {@code lessons/.digests.json} and the journal {@code lessons/.transaction-journal.json} of the lesson scope.
 * <p>
 * Every write is a target of the caller's {@link ScopeTransaction} of the lesson scope, which holds the lesson store
 * lock {@code locks/lessons.lock}: a lesson is created as a create target, replaced as a replace target, so it is
 * journaled and the sidecar covers it. The store opens no transaction and takes no lock itself; a transition that
 * also writes outside the lesson store is ordered by its caller (Store Schemas § Transaction Journal, Writes outside
 * the scope).
 * <p>
 * <em>Allocation</em> draws the id of the current UTC hour with the next per-hour sequence: the file names of the hour
 * present and the create targets the same transaction already holds, so two writers under the lock never share an id,
 * and an id is never reused because a lesson file is never deleted. <em>Reading</em> has two flavours: the lock-free
 * {@link #list()} and {@link #get(LessonId)} report a difference from the sidecar as {@link StoreIntegrity}, never as
 * an edit (PM-IMPL-7), and the locked {@link #get(ScopeTransaction, LessonId)}, which every transition uses, recovers
 * the journal of the scope and decides through {@link ScopeIntegrity}. <em>Transitions</em> — {@link #claim},
 * {@link #release}, {@link #endClaimApplied}, {@link #revise} and {@link #retire} — decide from the front matter
 * actually recorded, never from their arguments: a lesson in any other state than {@link LessonResolution.State#FOUND}
 * is left as it is and reported as {@link LessonResolution.Unavailable} (R14).
 * <p>
 * The listing names only files whose name parses as {@code <lesson_id>.md}: the staged files
 * {@code <lesson_id>.md.tmp.<transaction id>} and the sidecar are never lessons (R14 (6)). No path turns
 * {@link LessonResolution.State#ABSENT} into a retry.
 *
 * @since 0.1
 */
public final class LessonStore {

    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);
    private static final String FIRST_SEQUENCE = "001";
    private static final String PLAN_ID = "planId";
    private static final Comparator<LessonId> BY_VALUE = Comparator.comparing(LessonId::value);

    private final JournalScope scope;

    /**
     * @param lessons the lesson scope of the project
     * @throws IllegalArgumentException if the scope is no lesson scope
     */
    public LessonStore(JournalScope lessons) {
        this.scope = Objects.requireNonNull(lessons, "lessons");
        if (lessons.type() != JournalScopeType.LESSONS) {
            throw new IllegalArgumentException("Lesson records belong to the lesson store scope, not to "
                    + lessons.type().wireName());
        }
    }

    /** @return the directory {@code lessons/} of the project, absolute and normalized */
    public Path directoryPath() {
        return scope.pathBase();
    }

    /**
     * @param lessonId a lesson
     * @return the lesson file, absolute and normalized
     */
    public Path store(LessonId lessonId) {
        return scope.resolve(storePath(lessonId));
    }

    /**
     * @param lessonId a lesson
     * @return the path of the lesson file relative to the path base of the lesson store, as its journal and sidecar
     *         name it
     */
    public static String storePath(LessonId lessonId) {
        return Objects.requireNonNull(lessonId, "lessonId").fileName();
    }

    // ---------------------------------------------------------------- allocation and creation

    /**
     * Draws the next free lesson id of the current UTC hour: the hour plus the per-hour sequence that follows every
     * sequence present on disk, every sequence a sidecar entry records (an id is never reused, not even after a file was
     * removed out of band) and every lesson the transaction already holds. A draw is free for the transaction that
     * creates its lesson in the same step; two draws before the first create of the transaction return the same id,
     * because neither is a target yet.
     *
     * @param transaction an open transaction of the lesson scope, which holds the lesson store lock
     * @param clock       the clock whose hour and instant the id and the lesson carry
     * @return a free lesson id
     * @throws IllegalArgumentException if the transaction belongs to another scope
     */
    public LessonId allocate(ScopeTransaction transaction, Clock clock) {
        requireOwnScope(transaction);
        var hour = HOUR.format(clock.instant());
        var sequence = nextSequence(hour);
        var candidate = new LessonId(hour + "-" + sequence);
        while (transaction.isTarget(store(candidate))) {
            sequence = increment(sequence);
            candidate = new LessonId(hour + "-" + sequence);
        }
        return candidate;
    }

    /**
     * Adds a new lesson as a create target of a transaction of the lesson store; it is written when the caller
     * commits. The store derives every server-set member itself: {@code format_version} 1, the id, {@code created_at},
     * the status ({@code upstream_pending} for the audience {@code upstream}, {@code active} otherwise), revision 1
     * with its first history entry, and the null claim and retirement.
     *
     * @param transaction an open transaction of the lesson store
     * @param lessonId    the id the new lesson is written under, drawn by {@link #allocate}
     * @param newLesson   the members the caller gives
     * @param clock       the clock of {@code created_at} and of the first revision
     * @return the lesson as it is written
     * @throws IllegalArgumentException if the transaction belongs to another scope, or a member violates a rule of
     *                                  the record
     * @throws IllegalStateException    if the transaction was committed, or a file of the id exists when it commits
     *                                  (the create target must be absent)
     */
    public Lesson create(ScopeTransaction transaction, LessonId lessonId, NewLesson newLesson, Clock clock) {
        requireOwnScope(transaction);
        Objects.requireNonNull(lessonId, LessonResolution.LESSON_ID);
        Objects.requireNonNull(newLesson, "newLesson");
        if (recordedEntry(storePath(lessonId))) {
            throw new IllegalArgumentException("The lesson %s was recorded already and is never created again"
                    .formatted(lessonId));
        }
        var now = Objects.requireNonNull(clock, "clock").instant();
        var sources = newLesson.sources();
        var bodySha256 = StoreSnapshot.sha256(newLesson.body().getBytes(StandardCharsets.UTF_8));
        var frontMatter = LessonFrontMatter.builder()
                .formatVersion(LessonFrontMatter.FORMAT_VERSION)
                .lessonId(lessonId)
                .title(newLesson.title())
                .component(newLesson.component())
                .category(newLesson.category())
                .bundle(newLesson.bundle())
                .rule(newLesson.rule())
                .remedy(newLesson.remedy())
                .status(newLesson.audience() == Audience.UPSTREAM ? Status.UPSTREAM_PENDING : Status.ACTIVE)
                .provenance(newLesson.provenance())
                .derivedFromUntrusted(newLesson.derivedFromUntrusted())
                .audience(newLesson.audience())
                .createdAt(now)
                .toolVersion(newLesson.toolVersion())
                .promotions(List.of())
                .revision(LessonFrontMatter.MIN_REVISION)
                .sources(sources)
                .revisions(List.of(new Revision(LessonFrontMatter.MIN_REVISION, now, sources, bodySha256)))
                .mergedFrom(List.of())
                .claim(null)
                .retired(null)
                .build();
        var lesson = new Lesson(frontMatter, newLesson.body());
        transaction.create(storePath(lessonId), LessonCodec.encode(lesson));
        return lesson;
    }

    // ---------------------------------------------------------------- reading

    /**
     * Resolves a lesson without a lock. The content and its integrity come from one {@link StoreSnapshot}, so a file
     * that differs from its sidecar entry is {@link LessonResolution.Found} with
     * {@link StoreIntegrity#DIGEST_UNVERIFIED} and never an edit (PM-IMPL-7). A record of the sidecar whose file is
     * gone is {@link LessonResolution.DeletedOutOfBand}: an observed absence, not a digest verdict, and never
     * {@link LessonResolution.Absent}.
     *
     * @param lessonId the id the lesson is listed under
     * @return the resolution
     * @throws StoreRefusal         if the sidecar is unreadable or of another format version
     * @throws UncheckedIOException if a file of the store exists and cannot be read
     */
    public LessonResolution get(LessonId lessonId) {
        Objects.requireNonNull(lessonId, LessonResolution.LESSON_ID);
        var relative = storePath(lessonId);
        var file = store(lessonId);
        var snapshot = ScopeIntegrity.snapshot(scope, relative);
        return snapshot.content()
                .map(content -> decode(lessonId, file, content, snapshot.integrity()))
                .orElseGet(() -> recordedEntry(relative)
                        ? new LessonResolution.DeletedOutOfBand(lessonId)
                        : new LessonResolution.Absent(lessonId));
    }

    /**
     * Resolves a lesson under the lock of the store: it recovers the journal of the scope and decides through
     * {@link ScopeIntegrity#verify}, so a file that differs from its entry is {@link LessonResolution.StoreTampered}
     * or, when it is gone, {@link LessonResolution.DeletedOutOfBand}, and a file without an entry is
     * {@link LessonResolution.Untracked}. The bytes it decodes are the bytes it hashed: a file replaced between the
     * verification and the read is reported as {@link LessonResolution.StoreTampered} as well. Every transition uses
     * this flavour.
     *
     * @param transaction an open transaction of the lesson scope, which holds the lesson store lock
     * @param lessonId    the id the lesson is listed under
     * @return the resolution
     * @throws IllegalArgumentException if the transaction belongs to another scope
     * @throws StoreRefusal             if the journal or the sidecar is unreadable, or the journal cannot be rolled
     *                                  forward
     * @throws UncheckedIOException     if a file of the store cannot be read
     */
    public LessonResolution get(ScopeTransaction transaction, LessonId lessonId) {
        requireOwnScope(transaction);
        Objects.requireNonNull(lessonId, LessonResolution.LESSON_ID);
        var file = store(lessonId);
        var verification = ScopeIntegrity.verify(transaction.lockTransaction(), scope, storePath(lessonId),
                transaction.auditSink());
        return switch (verification) {
            case ScopeIntegrity.Verified verified -> verified(lessonId, file, verified);
            case ScopeIntegrity.Absent _ -> new LessonResolution.Absent(lessonId);
            case ScopeIntegrity.Mismatch(_, StoreDigest recorded, Optional<StoreDigest> current)
                -> current.isPresent()
                    ? new LessonResolution.StoreTampered(lessonId, file, recorded, current)
                    : new LessonResolution.DeletedOutOfBand(lessonId);
            case ScopeIntegrity.Untracked _ -> new LessonResolution.Untracked(lessonId, file);
            case JournalRecovery.InvariantViolation violation -> throw StoreRefusal.unreadable(scope.journalFile(),
                    TransactionJournal.FORMAT_VERSION,
                    "the journal of the lesson store could not be rolled forward: " + violation.stores(), null);
        };
    }

    /** Decodes the bytes just read, and reports a file that no longer has the digest that was verified. */
    private LessonResolution verified(LessonId lessonId, Path file, ScopeIntegrity.Verified verified) {
        var content = read(file);
        var current = StoreDigest.of(content);
        if (!current.equals(verified.digest())) {
            return new LessonResolution.StoreTampered(lessonId, file, verified.digest(),
                    Optional.of(current));
        }
        return decode(lessonId, file, content, StoreIntegrity.VERIFIED);
    }

    /**
     * Lists every lesson of the store, resolved without a lock, in the order of the ids. The listing names the files
     * whose name parses as {@code <lesson_id>.md} and the lessons the sidecar records, so a file without an entry and
     * an entry whose file is gone are both listed; a staged file and the sidecar are never lessons.
     *
     * @return one resolution per lesson, in the order of the ids; empty if the store holds none
     * @throws StoreRefusal         if the sidecar is unreadable or of another format version
     * @throws UncheckedIOException if the directory or a file of the store cannot be read
     */
    public List<LessonResolution> list() {
        return lessonIds().stream().map(this::get).toList();
    }

    /**
     * The unclaimed candidates of a lesson start: every lesson that resolves {@link LessonResolution.Found} with a
     * verified digest, is {@code active} and carries no claim, newest id first. A lesson whose digest is unverified or
     * untracked is not a candidate: it is the state the specification forbids reaching a plan. A lesson marked
     * {@code derived_from_untrusted} is listed and carries the mark in its front matter, which its caller screens.
     *
     * @return the candidates, newest id first
     * @see #list()
     */
    public List<LessonResolution.Found> unclaimed() {
        var candidates = new ArrayList<LessonResolution.Found>();
        for (var resolution : list()) {
            if (resolution instanceof LessonResolution.Found found
                    && found.integrity() == StoreIntegrity.VERIFIED
                    && found.lesson().frontMatter().status() == Status.ACTIVE
                    && found.lesson().frontMatter().claim() == null) {
                candidates.add(found);
            }
        }
        candidates.sort(Comparator.comparing((LessonResolution.Found found) -> found.lessonId().value()).reversed());
        return List.copyOf(candidates);
    }

    /**
     * The lessons the plan claimed, for the recovery of the plan's end.
     *
     * @param planId the plan
     * @return the claimed lessons that resolve {@link LessonResolution.Found}, in the order of the ids
     * @see #claims()
     */
    public List<LessonResolution.Found> claimedBy(String planId) {
        Objects.requireNonNull(planId, PLAN_ID);
        return claims().claimed().stream()
                .filter(found -> planId.equals(found.lesson().frontMatter().claim().planId()))
                .toList();
    }

    /**
     * Every claim of the store, read from the front matter: the lessons that state a claim and resolve
     * {@link LessonResolution.Found}, and the {@link LessonResolution.State#PRESENT_UNREADABLE} lessons listed
     * separately by id, because their claim can be read by no one and is never released without an operator act.
     *
     * @return the claims
     */
    public Claims claims() {
        var claimed = new ArrayList<LessonResolution.Found>();
        var unreadable = new TreeSet<>(BY_VALUE);
        for (var resolution : list()) {
            if (resolution instanceof LessonResolution.Found found && found.lesson().frontMatter().claim() != null) {
                claimed.add(found);
            } else if (resolution instanceof LessonResolution.PresentUnreadable present) {
                unreadable.add(present.lessonId());
            }
        }
        return new Claims(claimed, unreadable);
    }

    // ---------------------------------------------------------------- revision and retirement

    /**
     * Replaces the body of a lesson wholesale by its next revision: {@code revision} + 1, the new
     * {@code sources} (never empty) and an appended {@code revisions} entry carrying them and the SHA-256 of the new
     * UTF-8 body. Old bodies are not kept. {@code derived_from_untrusted} is monotonic: a lesson that is {@code true}
     * stays {@code true} whatever the argument, and a revision whose sources name a lesson of the kind {@code lesson}
     * that is {@code true} becomes {@code true} (PM-WATCH-STORE-17, PM-WATCH-STORE-58).
     *
     * @param transaction           an open transaction of the lesson store
     * @param lessonId              the lesson
     * @param body                  the new body, verbatim
     * @param sources               the evidence of the new body; never empty
     * @param derivedFromUntrusted  whether the facts of the writing task delivered untrusted text
     * @param now                   the instant of the revision
     * @return the revision, or {@link LessonResolution.Unavailable} for a lesson this transition does not act on —
     *         absent, unreadable, empty, tampered, deleted out of band, untracked, or retired
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the sources are empty
     */
    public LessonResolution.Outcome revise(ScopeTransaction transaction, LessonId lessonId, String body,
            List<Source> sources, boolean derivedFromUntrusted, Instant now) {
        requireOwnScope(transaction);
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(now, "now");
        var evidence = List.copyOf(Objects.requireNonNull(sources, "sources"));
        if (evidence.isEmpty()) {
            throw new IllegalArgumentException("A revision carries at least one source");
        }
        var resolution = get(transaction, lessonId);
        if (!(resolution instanceof LessonResolution.Found found)) {
            return unavailable(resolution);
        }
        var frontMatter = found.lesson().frontMatter();
        if (frontMatter.status() == Status.RETIRED) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.RETIRED, null);
        }
        var revision = frontMatter.revision() + 1;
        var next = frontMatter.toBuilder()
                .derivedFromUntrusted(frontMatter.derivedFromUntrusted() || derivedFromUntrusted
                        || citesUntrustedLesson(transaction, evidence))
                .revision(revision)
                .sources(evidence)
                .revisions(append(frontMatter.revisions(),
                        new Revision(revision, now, evidence, StoreSnapshot.sha256(
                                body.getBytes(StandardCharsets.UTF_8)))))
                .build();
        var lesson = new Lesson(next, body);
        transaction.replace(storePath(lessonId), LessonCodec.encode(lesson));
        return new LessonResolution.Revised(lesson);
    }

    /**
     * Retires a lesson: {@code retired} and {@code status: retired} are set in one transaction and the file stays in
     * place. It acts only on a lesson that resolves {@link LessonResolution.Found} and carries no claim; in every other
     * state it changes nothing and reports the state as {@link LessonResolution.Unavailable}.
     *
     * @param transaction an open transaction of the lesson store
     * @param lessonId    the lesson
     * @param retirement  the retirement record: a verdict of the closed set, with its required members
     * @return the retirement, or {@link LessonResolution.Unavailable}
     * @throws IllegalArgumentException if the transaction belongs to another scope, the verdict is missing, a
     *                                  {@code completely_covered} retirement lacks a non-blank clause or input, the
     *                                  {@code into} of a {@code merged} or {@code superseded} retirement is missing or
     *                                  set for another verdict, or the recorded state is not {@code found}
     */
    public LessonResolution.Outcome retire(ScopeTransaction transaction, LessonId lessonId, Retired retirement) {
        requireOwnScope(transaction);
        Objects.requireNonNull(retirement, "retirement");
        requireValid(retirement);
        var resolution = get(transaction, lessonId);
        if (!(resolution instanceof LessonResolution.Found found)) {
            return unavailable(resolution);
        }
        var frontMatter = found.lesson().frontMatter();
        if (frontMatter.status() == Status.RETIRED) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.RETIRED, null);
        }
        if (frontMatter.claim() != null) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.CLAIMED,
                    frontMatter.claim().planId());
        }
        var retired = frontMatter.toBuilder()
                .status(Status.RETIRED)
                .retired(retirement)
                .build();
        var lesson = new Lesson(retired, found.lesson().body());
        transaction.replace(storePath(lessonId), LessonCodec.encode(lesson));
        return new LessonResolution.Retired(lesson);
    }

    // ---------------------------------------------------------------- claim, release and the end of a claim

    /**
     * Claims a lesson for a plan: {@code claim{plan_id, claimed_at}} is set. A lesson already claimed by another plan,
     * a retired lesson and a lesson that does not resolve {@link LessonResolution.Found} change nothing and are
     * reported as {@link LessonResolution.Unavailable}, naming the claiming plan where there is one.
     *
     * @param transaction an open transaction of the lesson store
     * @param lessonId    the lesson
     * @param planId      the plan that claims the lesson
     * @param now         the instant of the claim
     * @return the claim, or {@link LessonResolution.Unavailable}
     * @throws IllegalArgumentException if the transaction belongs to another scope, or the plan id does not match the
     *                                  schema's pattern
     */
    public LessonResolution.Outcome claim(ScopeTransaction transaction, LessonId lessonId, String planId,
            Instant now) {
        requireOwnScope(transaction);
        Objects.requireNonNull(planId, PLAN_ID);
        Objects.requireNonNull(now, "now");
        var resolution = get(transaction, lessonId);
        if (!(resolution instanceof LessonResolution.Found found)) {
            return unavailable(resolution);
        }
        var frontMatter = found.lesson().frontMatter();
        if (frontMatter.status() == Status.RETIRED) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.RETIRED, null);
        }
        if (frontMatter.claim() != null) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.CLAIMED,
                    frontMatter.claim().planId());
        }
        var claimed = frontMatter.toBuilder().claim(new Claim(planId, now)).build();
        var lesson = new Lesson(claimed, found.lesson().body());
        transaction.replace(storePath(lessonId), LessonCodec.encode(lesson));
        return new LessonResolution.Claimed(lesson);
    }

    /**
     * Releases the claim of a plan: {@code claim} becomes {@code null} and the lesson is not retired. It acts only when
     * the durable {@code claim.plan_id} is the plan's, which is the only authorization it takes (R14 (2)); a lesson
     * without a claim, a claim of another plan and a retired lesson change nothing and are reported as
     * {@link LessonResolution.Unavailable}.
     *
     * @param transaction an open transaction of the lesson store
     * @param lessonId    the lesson
     * @param planId      the plan that released the lesson
     * @return the release, or {@link LessonResolution.Unavailable}
     * @throws IllegalArgumentException if the transaction belongs to another scope
     */
    public LessonResolution.Outcome release(ScopeTransaction transaction, LessonId lessonId, String planId) {
        requireOwnScope(transaction);
        Objects.requireNonNull(planId, PLAN_ID);
        var resolution = get(transaction, lessonId);
        if (!(resolution instanceof LessonResolution.Found found)) {
            return unavailable(resolution);
        }
        var frontMatter = found.lesson().frontMatter();
        if (frontMatter.status() == Status.RETIRED) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.RETIRED, null);
        }
        if (frontMatter.claim() == null) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.UNCLAIMED, null);
        }
        if (!planId.equals(frontMatter.claim().planId())) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.CLAIMED,
                    frontMatter.claim().planId());
        }
        var released = frontMatter.toBuilder().claim(null).build();
        var lesson = new Lesson(released, found.lesson().body());
        transaction.replace(storePath(lessonId), LessonCodec.encode(lesson));
        return new LessonResolution.Released(lesson);
    }

    /**
     * Ends the claim of a plan that landed its changes: the lesson is retired with the verdict {@code applied},
     * {@code by} the plan, and the claim is cleared — the link to the plan stays in {@code retired.by}, which is what
     * reach is computed from. It acts only when the durable {@code claim.plan_id} is the plan's (R14 (2)), and a
     * lesson already retired by that plan with the verdict {@code applied} is left untouched and reported as
     * {@link LessonResolution.Retired}, so the recovery of the plan's end can repeat it.
     *
     * @param transaction an open transaction of the lesson store
     * @param lessonId    the lesson
     * @param planId      the plan that landed its changes
     * @param now         the instant of the retirement
     * @return the retirement, or {@link LessonResolution.Unavailable}
     * @throws IllegalArgumentException if the transaction belongs to another scope
     */
    public LessonResolution.Outcome endClaimApplied(ScopeTransaction transaction, LessonId lessonId, String planId,
            Instant now) {
        requireOwnScope(transaction);
        Objects.requireNonNull(planId, PLAN_ID);
        Objects.requireNonNull(now, "now");
        var resolution = get(transaction, lessonId);
        if (!(resolution instanceof LessonResolution.Found found)) {
            return unavailable(resolution);
        }
        var frontMatter = found.lesson().frontMatter();
        if (frontMatter.status() == Status.RETIRED) {
            var recorded = frontMatter.retired();
            if (recorded != null && recorded.verdict() == Verdict.APPLIED && planId.equals(recorded.by())) {
                return new LessonResolution.Retired(found.lesson());
            }
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.RETIRED, null);
        }
        if (frontMatter.claim() == null) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.UNCLAIMED, null);
        }
        if (!planId.equals(frontMatter.claim().planId())) {
            return new LessonResolution.Unavailable(lessonId, LessonResolution.Refusal.CLAIMED,
                    frontMatter.claim().planId());
        }
        var retirement = Retired.builder()
                .at(now)
                .by(planId)
                .verdict(Verdict.APPLIED)
                .reason(END_OF_CLAIM_REASON)
                .lessonState(LessonState.FOUND)
                .build();
        var retired = frontMatter.toBuilder()
                .status(Status.RETIRED)
                .retired(retirement)
                .claim(null)
                .build();
        var lesson = new Lesson(retired, found.lesson().body());
        transaction.replace(storePath(lessonId), LessonCodec.encode(lesson));
        return new LessonResolution.Retired(lesson);
    }

    /** The reason recorded by the end of a claim, whose verdict is the plan's landing. */
    static final String END_OF_CLAIM_REASON = "the plan landed its changes";

    // ---------------------------------------------------------------- helpers

    /**
     * The members a caller gives when it creates a lesson; every other member is derived by {@link #create}.
     *
     * @param title                the title, the lesson's short name
     * @param component            the component the lesson is about
     * @param category             the kind of the lesson
     * @param bundle               the bundle of the component; {@code null} when the lesson names none
     * @param rule                 the recurrence key of an {@code arch-constraint} lesson; {@code null} otherwise
     * @param remedy               what would have prevented the mistake
     * @param provenance           what the lesson derives from
     * @param audience             the audience the audience predicate classified
     * @param derivedFromUntrusted whether the facts of the writing task delivered untrusted text
     * @param toolVersion          the release version of the runtime
     * @param sources              the evidence of the body; never empty
     * @param body                 the Markdown body, verbatim
     * @since 0.1
     */
    @Builder
    public record NewLesson(String title, String component, Category category, @Nullable String bundle,
    @Nullable String rule, Remedy remedy, Provenance provenance, Audience audience,
    boolean derivedFromUntrusted, String toolVersion, List<Source> sources, String body) {

        /**
         * @throws NullPointerException if a component that the caller must give is {@code null}
         */
        public NewLesson {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(remedy, "remedy");
            Objects.requireNonNull(provenance, "provenance");
            Objects.requireNonNull(audience, "audience");
            Objects.requireNonNull(toolVersion, "toolVersion");
            Objects.requireNonNull(body, "body");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        }
    }

    /**
     * The claims of the store: the lessons that state a claim and resolve {@link LessonResolution.Found}, and the
     * {@link LessonResolution.State#PRESENT_UNREADABLE} lessons by id.
     *
     * @param claimed    the claimed lessons, in the order of the ids
     * @param unreadable the ids of the unreadable lessons, sorted
     * @since 0.1
     */
    public record Claims(List<LessonResolution.Found> claimed, SortedSet<LessonId> unreadable) {

        /** @throws NullPointerException if a component is {@code null} */
        public Claims {
            claimed = List.copyOf(claimed);
            var sorted = new TreeSet<LessonId>(BY_VALUE);
            sorted.addAll(unreadable);
            unreadable = Collections.unmodifiableSortedSet(sorted);
        }
    }

    /** The ids the store lists: every file name that parses and every lesson the sidecar records. */
    private SortedSet<LessonId> lessonIds() {
        var ids = new TreeSet<>(BY_VALUE);
        try (var entries = Files.list(directoryPath())) {
            entries.filter(entry -> Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                    .map(entry -> LessonId.fromFileName(entry.getFileName().toString()))
                    .flatMap(Optional::stream).forEach(ids::add);
        } catch (NoSuchFileException _) {
            // The directory is created with the first transaction of the store; an absent one lists nothing.
        } catch (IOException e) {
            throw new UncheckedIOException("The lesson store cannot be listed: " + directoryPath(), e);
        }
        DigestSidecar.load(scope.sidecarFile()).stores().keySet().stream()
                .map(LessonStore::lessonIdOfStorePath).flatMap(Optional::stream).forEach(ids::add);
        return Collections.unmodifiableSortedSet(ids);
    }

    /**
     * @param relative a store path of the lesson scope
     * @return whether the sidecar of the scope records an entry for it
     */
    private boolean recordedEntry(String relative) {
        return DigestSidecar.load(scope.sidecarFile()).digest(relative).isPresent();
    }

    private static Optional<LessonId> lessonIdOfStorePath(String storePath) {
        return storePath.indexOf('/') < 0 ? LessonId.fromFileName(storePath) : Optional.empty();
    }

    /** The next sequence string of the hour: one past every sequence present on disk or recorded by a sidecar entry. */
    private String nextSequence(String hour) {
        String highest = null;
        for (var lessonId : lessonIds()) {
            if (!lessonId.hourPrefix().equals(hour)) {
                continue;
            }
            var present = lessonId.value().substring(LessonId.HOUR_PREFIX_LENGTH + 1);
            if (highest == null || isHigher(present, highest)) {
                highest = present;
            }
        }
        return highest == null ? FIRST_SEQUENCE : increment(highest);
    }

    /**
     * @param candidate a sequence of the same hour
     * @param current   the highest sequence seen so far
     * @return whether the candidate is higher: a longer digit string is a higher number, then the digits compare
     */
    private static boolean isHigher(String candidate, String current) {
        return candidate.length() == current.length() ? candidate.compareTo(current) > 0
                : candidate.length() > current.length();
    }

    /** @param sequence a sequence of the hour; @return the next one, a digit longer when every digit was a nine */
    private static String increment(String sequence) {
        var digits = sequence.toCharArray();
        for (var index = digits.length - 1; index >= 0; index--) {
            if (digits[index] != '9') {
                digits[index]++;
                return new String(digits);
            }
            digits[index] = '0';
        }
        return "1" + new String(digits);
    }

    /**
     * @param transaction an open transaction of the lesson store
     * @param sources     the sources of a revision
     * @return whether one of them names a lesson of the kind {@code lesson} that is marked untrusted
     */
    private boolean citesUntrustedLesson(ScopeTransaction transaction, List<Source> sources) {
        for (var source : sources) {
            if (source.kind() == Source.Kind.LESSON && LessonId.parse(source.ref()).isPresent()
                    && get(transaction, new LessonId(source.ref())) instanceof LessonResolution.Found cited
                    && cited.lesson().frontMatter().derivedFromUntrusted()) {
                return true;
            }
        }
        return false;
    }

    private static List<Revision> append(List<Revision> revisions, Revision next) {
        var appended = new ArrayList<>(revisions);
        appended.add(next);
        return List.copyOf(appended);
    }

    private LessonResolution decode(LessonId lessonId, Path file, byte[] content, StoreIntegrity integrity) {
        return switch (LessonCodec.decode(content)) {
            case LessonCodec.Readable(Lesson lesson) -> lesson.body().isBlank()
                    ? new LessonResolution.EmptyShell(lessonId, lesson, file)
                    : new LessonResolution.Found(lessonId, lesson, file, integrity);
            case LessonCodec.Unreadable(LessonCodec.UnreadableReason reason, String detail) ->
                new LessonResolution.PresentUnreadable(lessonId, file, reason, detail);
        };
    }

    private byte[] read(Path file) {
        return StoreSnapshot.read(file).content().orElseThrow(() -> new UncheckedIOException(
                new IOException("The lesson file vanished after it was verified: " + file)));
    }

    private static LessonResolution.Outcome unavailable(LessonResolution resolution) {
        return new LessonResolution.Unavailable(resolution.lessonId(), LessonResolution.Refusal.of(resolution.state()),
                null);
    }

    private static void requireValid(Retired retirement) {
        var verdict = retirement.verdict();
        if (verdict == Verdict.COMPLETELY_COVERED) {
            requirePresent(retirement.coveringClause(), "covering_clause", verdict);
            requirePresent(retirement.coveringInput(), "covering_input", verdict);
        }
        if ((verdict == Verdict.MERGED || verdict == Verdict.SUPERSEDED) != (retirement.into() != null)) {
            throw new IllegalArgumentException(
                    "The 'into' of a retirement is set exactly for 'merged' and 'superseded', not for '%s'"
                            .formatted(verdict.wireName()));
        }
        if (retirement.lessonState() != LessonState.FOUND) {
            throw new IllegalArgumentException(
                    "Only an operator act retires a 'present_unreadable' lesson; this store retires a found one");
        }
    }

    private static void requirePresent(@Nullable String value, String member, Verdict verdict) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "The verdict '%s' requires a non-blank '%s'".formatted(verdict.wireName(), member));
        }
    }

    private void requireOwnScope(ScopeTransaction transaction) {
        Objects.requireNonNull(transaction, "transaction");
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("A transaction of %s does not write the lesson store of %s"
                    .formatted(transaction.scope().pathBase(), scope.pathBase()));
        }
    }
}
