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

import java.io.ByteArrayOutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.stream.Collectors;

import de.planmarshall.core.store.JsonlStoreFile.Accepted;
import de.planmarshall.core.store.JsonlStoreFile.Entry;
import de.planmarshall.core.store.JsonlStoreFile.Refused;
import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;
import de.planmarshall.core.store.QuestionRefusal.Conflict;
import de.planmarshall.core.store.QuestionRefusal.Unreadable;

/**
 * The question log {@code questions.jsonl} of one scope (PM-WF-12, PM-IMPL-2, PM-IMPL-7): one {@link QuestionRecord}
 * per event of a question, appended and never changed.
 * <p>
 * Every scope that asks has one log: a plan {@code plans/<plan_id>/questions.jsonl}, an epic
 * {@code epics/<epic_id>/questions.jsonl}, the workspace {@code workspace/questions.jsonl}, and a pending entry
 * {@code pending/<entry_id>/questions.jsonl}, which belongs to the workspace scope ({@link #ofEntry(JournalScope,
 * EntryId)}) and moves into the scope the entry creates ({@link #moveEntryQuestions(ScopeTransaction, EntryId,
 * ScopeTransaction)}). The directory of a log exists before its first append; this type does not create it.
 * <p>
 * An append is never committed on its own: it adds the lines as one append target to the {@link ScopeTransaction} of
 * the caller, which holds the lock of the scope. Before it adds them, the log reads the questions they are about while
 * that lock is held and holds two invariants, returning a {@link QuestionRefusal} and adding nothing when a line breaks
 * one:
 * <ul>
 * <li>a question is closed by exactly one {@code answered}, {@code withdrawn} or {@code cancelled} line: a further
 * line about a closed question, a second closing line among them, is {@link AlreadyAnswered};</li>
 * <li>an answer, an adjudication and a delivery name the current revision of their decision: another revision is a
 * {@link Conflict} carrying the current one.</li>
 * </ul>
 * Both are decided from the lines the log holds, and the check fails closed: when the log holds a line that was
 * refused when read, which names no question the log can read and may close or revise any of them, every append is
 * refused {@link Unreadable}, an {@code asked} line included, and nothing is added.
 * A revision appends a {@code revised} line for every question of the set that is open when it begins, each with the
 * next revision, the one after its current revision, and carries the decision of the set on the line of its first
 * question only. Once the first question of a set is closed, the set no longer carries a changed decision: a revision
 * of its remaining open questions carries none, and a changed decision needs a new decision. A decision is asked whole:
 * its parts are fixed when its set is asked, so a line that asks a further part into a set the log already holds is a
 * fault of the caller; a change of the decision is a revision of its open questions.
 * Whether an answer fits its part is checked by the engine, not here. A line that is malformed for the log (a question
 * asked twice, a part asked into a set the log holds, a part whose {@code question_set} is no first question of a set,
 * a line about a question the log does not have, a revision that is not the next one, a set revised in part, a decision
 * on a revised line of another question than the first) is a fault of the caller and is refused with an
 * {@link IllegalArgumentException}.
 * <p>
 * A read without the lock ({@link #read()}) reports the log as it is at that moment: an append in progress shows as a
 * torn tail and never as a line, and a refused line keeps its line number while the lines around it stay readable. The
 * last readable line of a question is its current line.
 *
 * @since 0.1
 */
public final class QuestionLog {

    /** The file name of the question log of a scope. */
    public static final String FILE_NAME = "questions.jsonl";

    private static final String WORKSPACE_DIRECTORY = "workspace";

    /** The name of the workspace transaction or scope parameter, which a {@code null} argument is reported by. */
    private static final String WORKSPACE_PARAMETER = WORKSPACE_DIRECTORY;

    /** The events whose line names the current revision of its decision. */
    private static final Set<QuestionEvent> NAMES_CURRENT_REVISION = EnumSet.of(QuestionEvent.ADJUDICATED,
            QuestionEvent.DELIVERED, QuestionEvent.ANSWERED);

    private final JournalScope scope;
    private final String storePath;

    /**
     * What a read of the log found: one entry per complete line, each a record or a refused line, and a torn tail
     * apart from them.
     *
     * @param store          the log that was read
     * @param entries        one entry per complete line, in the order of the log
     * @param tornTailOffset the byte offset where a last line without a newline begins; empty if there is none
     * @since 0.1
     */
    public record Lines(Path store, List<Entry<QuestionRecord>> entries, OptionalLong tornTailOffset) {

        /** @throws NullPointerException if a component is {@code null} */
        public Lines {
            Objects.requireNonNull(store, "store");
            entries = List.copyOf(entries);
            Objects.requireNonNull(tornTailOffset, "tornTailOffset");
        }

        /** @return the records that were read, in the order of the log, without the refused lines */
        public List<QuestionRecord> records() {
            return entries.stream().filter(entry -> entry instanceof Accepted<QuestionRecord>)
                    .map(entry -> ((Accepted<QuestionRecord>) entry).value()).toList();
        }

        /** @return the refused lines with their line numbers and refusals, in the order of the log */
        public List<Refused<QuestionRecord>> refused() {
            return entries.stream().filter(entry -> entry instanceof Refused<QuestionRecord>)
                    .map(entry -> (Refused<QuestionRecord>) entry).toList();
        }

        /**
         * @param questionId a question
         * @return the lines of the question, in the order of the log
         */
        public List<QuestionRecord> of(String questionId) {
            Objects.requireNonNull(questionId, "questionId");
            return records().stream().filter(line -> line.questionId().equals(questionId)).toList();
        }

        /**
         * The lines this reads are the readable ones: a line that was refused when read is not among them, so for a
         * question with a refused closing line the result is not its closing line. A caller that needs the state of
         * the question consults {@link #refused()} first and reports the question unreadable when it holds a line.
         *
         * @param questionId a question
         * @return the last readable line of the question, its current line; empty if the log has none
         */
        public Optional<QuestionRecord> current(String questionId) {
            var lines = of(questionId);
            return lines.isEmpty() ? Optional.empty() : Optional.of(lines.getLast());
        }

        /**
         * Only readable lines count: a question whose closing line was refused when read is answered {@code false}
         * here, though it may be closed. Consult {@link #refused()} before relying on a {@code false}.
         *
         * @param questionId a question
         * @return whether the question has a readable closing line
         */
        public boolean isClosed(String questionId) {
            return of(questionId).stream().anyMatch(QuestionRecord::closes);
        }

        /**
         * Only readable lines count, as for {@link #isClosed(String)}: a revised line that was refused when read does
         * not raise the revision returned here.
         *
         * @param questionId a question
         * @return the current revision of its decision, the revision of its last readable asked or revised line; empty
         *         if the log has no readable line that asked it
         */
        public OptionalInt currentRevision(String questionId) {
            return of(questionId).stream().filter(line -> line.event() == QuestionEvent.ASKED
                    || line.event() == QuestionEvent.REVISED).mapToInt(QuestionRecord::revision).reduce((a, b) -> b);
        }
    }

    /** What the log knows about one question while it checks the lines to append. */
    private static final class Question {

        private final String questionSet;
        private int revision;
        private boolean closed;

        private Question(String questionSet, int revision) {
            this.questionSet = questionSet;
            this.revision = revision;
        }
    }

    /**
     * @param scope the plan, epic or workspace scope whose log this is
     * @throws IllegalArgumentException if the scope is the lesson store, which has no question log
     */
    public QuestionLog(JournalScope scope) {
        this(scope, switch (Objects.requireNonNull(scope, "scope").type()) {
            case PLAN, EPIC -> FILE_NAME;
            case WORKSPACE -> WORKSPACE_DIRECTORY + "/" + FILE_NAME;
            case MACHINE -> FILE_NAME;
            case LESSONS -> throw new IllegalArgumentException("The lesson store has no question log");
        });
    }

    private QuestionLog(JournalScope scope, String storePath) {
        this.scope = scope;
        this.storePath = storePath;
    }

    /**
     * @param workspace the workspace scope of the project, whose stores include the pending entries
     * @param entryId   the pending entry
     * @return the question log of the entry, {@code pending/<entry_id>/questions.jsonl}
     * @throws IllegalArgumentException if the scope is no workspace scope
     */
    public static QuestionLog ofEntry(JournalScope workspace, EntryId entryId) {
        Objects.requireNonNull(workspace, WORKSPACE_PARAMETER);
        Objects.requireNonNull(entryId, "entryId");
        if (workspace.type() != JournalScopeType.WORKSPACE) {
            throw new IllegalArgumentException("The question log of an entry belongs to the workspace scope, not to "
                    + workspace.type().wireName());
        }
        return new QuestionLog(workspace, PendingEntryStore.DIRECTORY + "/" + entryId.value() + "/" + FILE_NAME);
    }

    /** @return the log file, absolute and normalized */
    public Path store() {
        return scope.resolve(storePath);
    }

    /** @return the path of the log relative to the path base of its scope, as its journal and sidecar name it */
    public String storePath() {
        return storePath;
    }

    /**
     * Reads the log without a lock. A torn tail is reported, never read as a line, and a refused line keeps its line
     * number; nothing is repaired.
     *
     * @return what the log holds; no entry if it does not exist
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public Lines read() {
        var file = store();
        var content = new JsonlStoreFile(file, scope.lockKey()).snapshot();
        return new Lines(file, content.decode(QuestionRecord.FORMAT_VERSION,
                line -> QuestionRecordCodec.read(file, line)), content.tornTailOffset());
    }

    /**
     * Asks the questions of one decision: one {@code asked} line per question, the first naming the set.
     *
     * @param transaction an open transaction of this log's scope
     * @param questions   the asked lines, the first question first
     * @return {@link Unreadable} if the log holds a refused line; empty if the lines were added to the transaction
     * @throws IllegalArgumentException if a line is no asked line, the lines are not one set named by its first
     *                                  question, a question is asked already, or a part is asked into a set the log
     *                                  already holds
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> ask(ScopeTransaction transaction, List<QuestionRecord> questions) {
        requireEvent(questions, QuestionEvent.ASKED);
        var set = questions.getFirst().questionId();
        if (questions.stream().anyMatch(question -> !set.equals(question.questionSet()))) {
            throw new IllegalArgumentException("The questions asked together are the set named by its first question "
                    + set);
        }
        return append(transaction, questions);
    }

    /**
     * Revises an open decision: a {@code revised} line for every open question of its set, with the next revision.
     *
     * @param transaction an open transaction of this log's scope
     * @param lines       the revised lines
     * @return {@link AlreadyAnswered} if a question of the lines is closed, {@link Unreadable} if the log holds a
     *         refused line; empty if the lines were added
     * @throws IllegalArgumentException if a line is no revised line, a line names another revision than the one after
     *                                  the current revision of its question, a line of another question than the first
     *                                  of its set carries the decision, or the lines do not revise every open
     *                                  question of the set with one revision
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> revise(ScopeTransaction transaction, List<QuestionRecord> lines) {
        requireEvent(lines, QuestionEvent.REVISED);
        return append(transaction, lines);
    }

    /**
     * Records the adjudication of a revision.
     *
     * @param transaction an open transaction of this log's scope
     * @param lines       the adjudicated lines
     * @return a refusal if a question is closed or the revision is not current; empty if the lines were added
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> adjudicate(ScopeTransaction transaction, List<QuestionRecord> lines) {
        requireEvent(lines, QuestionEvent.ADJUDICATED);
        return append(transaction, lines);
    }

    /**
     * Records the delivery of a revision to the operator's clients.
     *
     * @param transaction an open transaction of this log's scope
     * @param lines       the delivered lines
     * @return a refusal if a question is closed or the revision is not current; empty if the lines were added
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> deliver(ScopeTransaction transaction, List<QuestionRecord> lines) {
        requireEvent(lines, QuestionEvent.DELIVERED);
        return append(transaction, lines);
    }

    /**
     * Records the answers to the parts of a decision.
     *
     * @param transaction an open transaction of this log's scope
     * @param lines       the answered lines
     * @return {@link AlreadyAnswered} if a question is closed or answered twice, {@link Conflict} if an answer names
     *         another revision than the current one, {@link Unreadable} if the log holds a refused line; empty if the
     *         lines were added
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> answer(ScopeTransaction transaction, List<QuestionRecord> lines) {
        requireEvent(lines, QuestionEvent.ANSWERED);
        return append(transaction, lines);
    }

    /**
     * Withdraws open questions.
     *
     * @param transaction an open transaction of this log's scope
     * @param lines       the withdrawn lines
     * @return {@link AlreadyAnswered} if a question is closed, {@link Unreadable} if the log holds a refused line;
     *         empty if the lines were added
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> withdraw(ScopeTransaction transaction, List<QuestionRecord> lines) {
        requireEvent(lines, QuestionEvent.WITHDRAWN);
        return append(transaction, lines);
    }

    /**
     * Cancels open questions at the end of their scope.
     *
     * @param transaction an open transaction of this log's scope
     * @param lines       the cancelled lines
     * @return {@link AlreadyAnswered} if a question is closed, {@link Unreadable} if the log holds a refused line;
     *         empty if the lines were added
     * @see #append(ScopeTransaction, List)
     */
    public Optional<QuestionRefusal> cancel(ScopeTransaction transaction, List<QuestionRecord> lines) {
        requireEvent(lines, QuestionEvent.CANCELLED);
        return append(transaction, lines);
    }

    /**
     * Adds lines of any events as one append target to a transaction of the scope, after checking them in their order
     * against the log and against the lines before them, as an adjudication and the answer of a decided set are
     * appended in one transaction. The log is read while the caller's transaction holds the lock of the scope.
     *
     * @param transaction an open transaction of this log's scope, opened by the caller's lock transaction
     * @param lines       the lines, in the order they are appended
     * @return {@link Unreadable} if the log holds a refused line, else the refusal of the first line that breaks an
     *         invariant, nothing being added; empty if the lines were added
     * @throws IllegalArgumentException if the transaction belongs to another scope, the log is a target of the
     *                                  transaction already, there is no line, or a line is malformed for the log
     * @throws IllegalStateException    if the transaction was committed
     * @throws UncheckedIOException     if the log exists and cannot be read
     */
    public Optional<QuestionRefusal> append(ScopeTransaction transaction, List<QuestionRecord> lines) {
        Objects.requireNonNull(transaction, "transaction");
        WaiverLog.requireOwnScope(transaction, scope, "question log");
        var toAppend = List.copyOf(lines);
        if (toAppend.isEmpty()) {
            throw new IllegalArgumentException("An append to the question log has at least one line");
        }
        var log = read();
        var refused = log.refused();
        if (!refused.isEmpty()) {
            return Optional.of(new Unreadable(toAppend.getFirst().questionId(), refused.getFirst().line()));
        }
        var questions = questions(log.records());
        var askedBefore = questions.values().stream().map(question -> question.questionSet)
                .collect(Collectors.toSet());
        var openAtRevision = new LinkedHashMap<String, Set<String>>();
        for (var line : toAppend) {
            snapshotOpenSet(questions, openAtRevision, line);
            var refusal = check(questions, askedBefore, line);
            if (refusal.isPresent()) {
                return refusal;
            }
        }
        requireWholeRevisions(questions, openAtRevision, toAppend);
        var block = new ByteArrayOutputStream();
        toAppend.forEach(line -> block.writeBytes(WaiverLog.line(QuestionRecordCodec.write(line))));
        transaction.append(storePath, block.toByteArray());
        return Optional.empty();
    }

    /**
     * Moves the question log of a pending entry into the scope the entry creates. The complete lines of
     * {@code pending/<entry_id>/questions.jsonl} are added unchanged as the new {@code questions.jsonl} of the new
     * scope to its transaction, which creates the log and is refused at its commit if the scope has one already, and
     * the removal of the entry's log is added to the transaction of the workspace; a torn tail is not moved.
     * <p>
     * A scope's transaction writes only the stores of its scope, so the move is two targets of two transactions, both
     * held by the caller that creates the scope. The caller commits the transaction of the new scope first: a stop
     * before the second commit leaves the entry's log in place, and a repeated move is refused by the creation instead
     * of appending the lines twice.
     *
     * @param workspace an open transaction of the workspace scope
     * @param entryId   the pending entry
     * @param target    an open transaction of the plan or epic scope the entry creates
     * @return the number of lines moved; 0 if the entry has no log or no complete line
     * @throws IllegalArgumentException if the workspace transaction is not of a workspace, the target is not a plan
     *                                  or an epic, or a store is a target of its transaction already
     * @throws IllegalStateException    if a transaction was committed
     * @throws UncheckedIOException     if the entry's log exists and cannot be read
     */
    public static int moveEntryQuestions(ScopeTransaction workspace, EntryId entryId, ScopeTransaction target) {
        Objects.requireNonNull(workspace, WORKSPACE_PARAMETER);
        Objects.requireNonNull(target, "target");
        var entry = ofEntry(workspace.scope(), entryId);
        var type = target.scope().type();
        if (type != JournalScopeType.PLAN && type != JournalScopeType.EPIC) {
            throw new IllegalArgumentException("An entry creates a plan or an epic, not a " + type.wireName());
        }
        var file = entry.store();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        var content = new JsonlStoreFile(file, workspace.scope().lockKey()).snapshot();
        var block = new ByteArrayOutputStream();
        content.lines().forEach(line -> block.writeBytes(WaiverLog.line(line.content())));
        if (!content.lines().isEmpty()) {
            target.create(new QuestionLog(target.scope()).storePath(), block.toByteArray());
        }
        workspace.delete(entry.storePath());
        return content.lines().size();
    }

    private static Map<String, Question> questions(List<QuestionRecord> records) {
        var questions = new HashMap<String, Question>();
        for (var line : records) {
            if (line.event() == QuestionEvent.ASKED) {
                questions.putIfAbsent(line.questionId(), new Question(line.questionSet(), line.revision()));
            }
            var question = questions.get(line.questionId());
            if (question == null) {
                continue;
            }
            if (line.event() == QuestionEvent.REVISED) {
                question.revision = line.revision();
            }
            question.closed |= line.closes();
        }
        return questions;
    }

    /**
     * Checks that an asked line that is not the first question of its set joins a set the same append has just asked:
     * no set of the log holds it, the question it names is that set's own first question, that question is still open,
     * and it is still at revision 1, so a revision or a closing line begun earlier in the append already excludes the
     * further part.
     */
    private static void requirePartOfNewSet(Map<String, Question> questions, Set<String> askedBefore, String id,
            String set) {
        if (askedBefore.contains(set)) {
            throw new IllegalArgumentException(
                    "The set %s is asked already; a set is asked whole, and a further part needs a new decision"
                            .formatted(set));
        }
        var named = questions.get(set);
        if (named == null) {
            throw new IllegalArgumentException("The question %s names the set %s, which is not asked"
                    .formatted(id, set));
        }
        if (!named.questionSet.equals(set)) {
            throw new IllegalArgumentException(
                    "The question %s names %s as its set, which is no first question of a set".formatted(id, set));
        }
        if (named.closed) {
            throw new IllegalArgumentException(
                    "The set %s is closed already; a set is asked whole, and a further part needs a new decision"
                            .formatted(set));
        }
        if (named.revision != 1) {
            throw new IllegalArgumentException(
                    "The set %s is revised already; a set is asked whole, and a further part needs a new decision"
                            .formatted(set));
        }
    }

    /** Checks one line against what the log and the lines before it hold, and applies it when it passes. */
    private static Optional<QuestionRefusal> check(Map<String, Question> questions, Set<String> askedBefore,
            QuestionRecord line) {
        var id = line.questionId();
        if (line.event() == QuestionEvent.ASKED) {
            if (questions.containsKey(id)) {
                throw new IllegalArgumentException("The question %s is asked already".formatted(id));
            }
            if (!line.questionSet().equals(id)) {
                requirePartOfNewSet(questions, askedBefore, id, line.questionSet());
            }
            questions.put(id, new Question(line.questionSet(), line.revision()));
            return Optional.empty();
        }
        var question = questions.get(id);
        if (question == null) {
            throw new IllegalArgumentException("The question %s is not asked in this log".formatted(id));
        }
        if (question.closed) {
            return Optional.of(new AlreadyAnswered(id));
        }
        switch (line.event()) {
            case REVISED -> {
                if (line.revision() != question.revision + 1) {
                    throw new IllegalArgumentException("The revision %d of %s is not the next revision %d"
                            .formatted(line.revision(), id, question.revision + 1));
                }
                if (line.decision() != null && !line.questionId().equals(question.questionSet)) {
                    throw new IllegalArgumentException(
                            "The decision is carried by the first question of the set, and only there: " + id);
                }
                question.revision = line.revision();
            }
            case QuestionEvent event when NAMES_CURRENT_REVISION.contains(event)
                    && line.revision() != question.revision -> {
                return Optional.of(new Conflict(id, question.revision));
            }
            default -> {
                // a line of the current revision; withdrawn, cancelled: no revision to check
            }
        }
        question.closed = line.closes();
        return Optional.empty();
    }

    /**
     * Records, before the first revised line of a set is checked, the questions of that set that are open at that
     * moment: the questions the revision has to cover. A question closed by a line before it is not among them.
     */
    private static void snapshotOpenSet(Map<String, Question> questions, Map<String, Set<String>> openAtRevision,
            QuestionRecord line) {
        if (line.event() != QuestionEvent.REVISED) {
            return;
        }
        var question = questions.get(line.questionId());
        if (question == null) {
            return;
        }
        openAtRevision.computeIfAbsent(question.questionSet, set -> questions.entrySet().stream()
                .filter(entry -> entry.getValue().questionSet.equals(set) && !entry.getValue().closed)
                .map(Map.Entry::getKey).collect(Collectors.toSet()));
    }

    /**
     * A revision revises the whole set: every question of a set the lines revise that was open when the revision
     * began, at the first revised line of the set, has a revised line among them, and all of them name one revision.
     * A question of the set the lines close after it was revised stays covered.
     */
    private static void requireWholeRevisions(Map<String, Question> questions, Map<String, Set<String>> openAtRevision,
            List<QuestionRecord> lines) {
        var revised = lines.stream().filter(line -> line.event() == QuestionEvent.REVISED).toList();
        for (var opened : openAtRevision.entrySet()) {
            var set = opened.getKey();
            var ofSet = revised.stream().filter(line -> questions.get(line.questionId()).questionSet.equals(set))
                    .toList();
            Set<Integer> revisions = ofSet.stream().map(QuestionRecord::revision).collect(Collectors.toSet());
            Set<String> covered = ofSet.stream().map(QuestionRecord::questionId).collect(Collectors.toSet());
            var open = opened.getValue();
            if (revisions.size() != 1 || !covered.equals(open)) {
                throw new IllegalArgumentException(
                        "A revision of the set %s revises every open question %s with one revision, not %s"
                                .formatted(set, open, covered));
            }
        }
    }

    private static void requireEvent(List<QuestionRecord> lines, QuestionEvent event) {
        Objects.requireNonNull(lines, "lines");
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("An append to the question log has at least one line");
        }
        for (var line : lines) {
            if (Objects.requireNonNull(line, "line").event() != event) {
                throw new IllegalArgumentException("A %s line is no %s line".formatted(line.event().wireName(),
                        event.wireName()));
            }
        }
    }
}
