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

import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.delivered;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;
import de.planmarshall.core.store.SourceManifest.Source;
import de.planmarshall.core.store.SourceManifest.SourceFile;
import de.planmarshall.core.store.SourceManifest.Trust;
import de.planmarshall.core.store.SourceManifest.Verdict;

/**
 * The creation of a scope from a pending entry (Store Schemas § Pending Entry, Creation): the source directory, the
 * transcript, the question log and the decision store move into the new scope in its transaction and the workspace's,
 * the request document is composed from the sources, every stored file is verified against its manifest entry, and the
 * entry's directory is deleted last. The project, the ids, the content and the instants are generated; the store paths,
 * the member names and the request headings are contracts.
 */
@EnableGeneratorController
@DisplayName("Pending entry creation")
class PendingEntryCreationTest {

    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private final Instant at = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final Clock clock = Clock.fixed(at, ZoneOffset.UTC);
    private final String planId = "plan-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    private final String description = Generators.letterStrings(10, 40).next() + " " + Generators.letterStrings(5, 20)
            .next();
    private final String chat = Generators.letterStrings(10, 40).next();
    private final String decision = Generators.letterStrings(10, 40).next();
    private final String reference = "https://github.com/example/repo/issues/"
            + Generators.integers(1, 100_000).next();
    private final String descriptionSourceId = "source_" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    private final String issueSourceId = "source_" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    private final String origin = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);

    private JournalScope workspace;
    private JournalScope plan;
    private PendingEntryStore store;
    private PendingEntryCreation creation;
    private RecordingLockManager locks;
    private AuditSink audit;
    private EntryId entryId;

    @BeforeEach
    void createScopes() throws IOException {
        entryId = new EntryId("ent-%016x".formatted(Generators.longs(0, Long.MAX_VALUE).next()));
        workspace = JournalScope.workspace(base, PROJECT);
        plan = JournalScope.plan(base, PROJECT, planId);
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(workspace.resolve(PendingEntryStore.DIRECTORY));
        Files.createDirectories(plan.journalFile().getParent());
        store = new PendingEntryStore(workspace);
        creation = new PendingEntryCreation(workspace);
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
    }

    private PendingEntryRecord entry() {
        return PendingEntryRecord.builder().entryId(entryId).kind(Kind.PLAN).createdAt(at).updatedAt(at)
                .bootstrap(Bootstrap.builder().state("start.ready").callStack(List.of()).facts("{}").options("{}")
                        .build())
                .build();
    }

    private SourceFile stored(byte[] content) {
        return new SourceFile("description-1.md", content.length,
                StoreDigest.of(content).sha256());
    }

    /** Creates the entry with a description, an issue, a transcript, a question and a decision. */
    private SourceManifest prepareEntry() throws IOException {
        var descriptionFile = description.getBytes(UTF_8);
        var manifest = new SourceManifest(2, List.of(
                new Source(descriptionSourceId, SourceManifest.Kind.DESCRIPTION, "the operator", origin,
                        Trust.TRUSTED, List.of(stored(descriptionFile)), at, null, null),
                new Source(issueSourceId, SourceManifest.Kind.ISSUE, reference, origin, Trust.UNTRUSTED,
                        List.of(new SourceFile("issue.md", 2, StoreDigest.of("{}".getBytes(UTF_8)).sha256())), at,
                        Verdict.CLEAN, null)));
        Files.createDirectories(plan.pathBase().resolve("sources"));
        Files.createDirectories(plan.pathBase().resolve("chat"));
        inWorkspace(transaction -> store.create(transaction, entry()));
        inWorkspace(transaction -> store.replaceSources(transaction, entryId, manifest));
        Files.createDirectories(store.chatDirectory(entryId));
        Files.writeString(store.chatDirectory(entryId).resolve("session.md"), chat, UTF_8);
        Files.writeString(store.sourcesDirectory(entryId).resolve("description-1.md"), description, UTF_8);
        Files.writeString(store.sourcesDirectory(entryId).resolve("issue.md"), "{}", UTF_8);
        inWorkspace(transaction -> QuestionLog.ofEntry(workspace, entryId)
                .append(transaction, List.of(asked("Q-001", "Q-001"), delivered("Q-001", 1))));
        Files.writeString(workspace.resolve(PendingEntryStore.directoryPath(entryId) + "/"
                + PendingEntryStore.DECISION_LOG), decision, UTF_8);
        return manifest;
    }

    private void inWorkspace(Consumer<ScopeTransaction> work) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            var write = ScopeTransaction.open(transaction, workspace, audit, clock);
            work.accept(write);
            write.commit();
        }
    }

    /** Runs the move in one lock session of the two keys and returns what it carried. */
    private PendingEntryCreation.Moved move() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            transaction.acquire(plan.lockKey());
            var workspaceWrite = ScopeTransaction.open(transaction, workspace, audit, clock);
            var target = ScopeTransaction.open(transaction, plan, audit, clock);
            var moved = creation.move(workspaceWrite, target, entryId);
            target.commit();
            workspaceWrite.commit();
            return moved;
        }
    }

    @Test
    @DisplayName("the sources, transcript, questions and decisions move into the new scope, the directory last")
    void movesIntoTheNewScope() throws Exception {
        var manifest = prepareEntry();

        var moved = move();
        var discarded = creation.finish(entryId);

        assertAll(
                () -> assertEquals(new PendingEntryCreation.Moved(2, 2, 1, 2, 1), moved,
                        "the move should carry every member of the entry"),
                () -> assertArrayEquals(SourceManifestCodec.write(manifest),
                        Files.readAllBytes(plan.pathBase().resolve("sources/sources.json")),
                        "the manifest should be copied into the new scope"),
                () -> assertEquals(description, Files.readString(plan.pathBase().resolve("sources/description-1.md")),
                        "the stored description should be copied with its content"),
                () -> assertEquals(chat, Files.readString(plan.pathBase().resolve("chat/session.md")),
                        "the transcript should be copied with its content"),
                () -> assertEquals(decision, Files.readString(plan.pathBase().resolve("decision-log.jsonl")),
                        "the decision store should be copied with its content"),
                () -> assertTrue(Files.readString(plan.pathBase().resolve("questions.jsonl")).contains("Q-001"),
                        "the question records should be moved into the new scope"),
                () -> assertTrue(Files.readString(plan.pathBase().resolve("request.md"))
                                .startsWith("# Request\n\n" + description),
                        "the composed request should open with the description"),
                () -> assertTrue(discarded, "the entry directory should exist until the last act"),
                () -> assertFalse(Files.exists(store.entryDirectory(entryId)), "the directory should be gone last"),
                () -> assertEquals(Optional.empty(), store.read(entryId), "the record should be gone"));
    }

    @Test
    @DisplayName("the request document is composed from the descriptions and references every other source")
    void composesTheRequest() throws Exception {
        var manifest = prepareEntry();

        var document = PendingEntryCreation.composeRequest(store.sourcesDirectory(entryId), manifest);

        assertAll(
                () -> assertTrue(document.startsWith("# Request\n\n" + description + "\n\n"),
                        "the description should open the document as written"),
                () -> assertTrue(document.contains("## Sources"), "the sources should be listed"),
                () -> assertTrue(document.contains("`" + issueSourceId + "` (issue) — " + reference),
                        "an issue should be referenced by its URL"),
                () -> assertTrue(document.contains("`" + descriptionSourceId + "` (description)"),
                        "a description should be referenced too"));
    }

    @Test
    @DisplayName("a source directory that is not normalized still composes the request of every valid file")
    void composesFromANonNormalizedDirectory() throws Exception {
        var manifest = prepareEntry();
        var notNormalized = store.sourcesDirectory(entryId).resolve("..").resolve("sources");

        var document = PendingEntryCreation.composeRequest(notNormalized, manifest);

        assertTrue(document.startsWith("# Request\n\n" + description),
                "an unnormalized source directory still composes");
    }

    @Test
    @DisplayName("a stored file that differs from its manifest entry refuses the move")
    void refusesADifferingSource() throws Exception {
        prepareEntry();
        Files.writeString(store.sourcesDirectory(entryId).resolve("description-1.md"), "changed", UTF_8);

        assertThrows(StoreRefusal.class, this::move, "a differing source file should refuse the move");
        assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                "nothing should be written into the new scope");
    }

    @Test
    @DisplayName("an epic target is refused with its reason: an epic's sources belong to its ledger")
    void refusesAnEpicTarget() throws Exception {
        prepareEntry();
        var epic = JournalScope.epic(base, PROJECT, "epic-" + Generators.letterStrings(3, 12).next()
                .toLowerCase(Locale.ROOT));
        Files.createDirectories(epic.journalFile().getParent());
        var probe = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT) + ".json";

        IllegalArgumentException refusal;
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            transaction.acquire(epic.lockKey());
            var workspaceWrite = ScopeTransaction.open(transaction, workspace, audit, clock);
            var epicWrite = ScopeTransaction.open(transaction, epic, audit, clock);
            workspaceWrite.create(PendingEntryStore.entryPath(entryId, probe), new byte[0]);
            epicWrite.create(probe, new byte[0]);
            refusal = assertThrows(IllegalArgumentException.class,
                    () -> creation.move(workspaceWrite, epicWrite, entryId),
                    "an epic's machine store takes no source");
            epicWrite.commit();
            workspaceWrite.commit();
        }

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("ledger"),
                        "the reason should name the epic's ledger"),
                () -> assertTrue(refusal.getMessage().contains("ledger copy"),
                        "the reason should name the ledger copy that is owed"),
                () -> assertFalse(refusal.getMessage().contains("PLAN-V2-133"),
                        "the reason should name the capability, not a plan id"),
                () -> assertTrue(Files.exists(epic.pathBase().resolve(probe)),
                        "the epic's own store commits, so no move wrote"),
                () -> assertFalse(Files.exists(epic.pathBase().resolve("sources")),
                        "no source directory should be written into the epic"),
                () -> assertFalse(Files.exists(epic.pathBase().resolve("sources/sources.json")),
                        "no manifest should be written into the epic"),
                () -> assertFalse(Files.exists(epic.pathBase().resolve("chat")),
                        "no transcript should be written into the epic"),
                () -> assertFalse(Files.exists(new QuestionLog(epic).store()),
                        "no question log should be written into the epic"),
                () -> assertFalse(Files.exists(epic.pathBase().resolve("request.md")),
                        "no request should be written into the epic"));
    }

    @Test
    @DisplayName("a file of the source directory with no manifest entry refuses the move, naming it")
    void refusesAFileWithoutManifestEntry() throws Exception {
        prepareEntry();
        Files.writeString(store.sourcesDirectory(entryId).resolve("hand-written.md"),
                Generators.letterStrings(10, 40).next(), UTF_8);

        var refusal = assertThrows(StoreRefusal.class, this::move,
                "a file no manifest entry names should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("hand-written.md"),
                        "the refusal should name the file"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a source that is a symbolic link refuses the move, so the read never leaves the entry")
    void refusesASymbolicLink() throws Exception {
        prepareEntry();
        var outside = Files.writeString(base.resolve("outside.md"), description, UTF_8);
        var stored = store.sourcesDirectory(entryId).resolve("description-1.md");
        Files.delete(stored);
        Files.createSymbolicLink(stored, outside);

        var refusal = assertThrows(StoreRefusal.class, this::move, "a linked source should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("symbolic link"),
                        "the refusal should name the link"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a source directory that is a link refuses the move before a file of it is read")
    void refusesASourceDirectoryThatIsNoDirectory() throws Exception {
        prepareEntry();
        var sources = store.sourcesDirectory(entryId);
        var elsewhere = Files.createDirectory(base.resolve("elsewhere"));
        Files.write(elsewhere.resolve(SourceManifestCodec.FILE_NAME),
                SourceManifestCodec.write(SourceManifest.empty()));
        Files.delete(sources.resolve("description-1.md"));
        Files.delete(sources.resolve("issue.md"));
        Files.delete(sources.resolve(SourceManifestCodec.FILE_NAME));
        Files.delete(sources);
        Files.createSymbolicLink(sources, elsewhere);

        var refusal = assertThrows(StoreRefusal.class, this::move,
                "a linked source directory should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("no directory"),
                        "the refusal should name the source directory"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a transcript that is a symbolic link refuses the move, never dropped with the directory")
    void refusesALinkedTranscript() throws Exception {
        prepareEntry();
        var transcript = store.chatDirectory(entryId);
        var elsewhere = Files.createDirectory(base.resolve("elsewhere-chat"));
        Files.delete(transcript.resolve("session.md"));
        Files.delete(transcript);
        Files.createSymbolicLink(transcript, elsewhere);

        var refusal = assertThrows(StoreRefusal.class, this::move,
                "a linked transcript should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("transcript"),
                        "the refusal should name the transcript"),
                () -> assertTrue(refusal.getMessage().contains("symbolic link"),
                        "the refusal should name the link it found"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a link inside the transcript refuses the move, where it would be dropped and deleted")
    void refusesALinkInsideTheTranscript() throws Exception {
        prepareEntry();
        var outside = Files.writeString(base.resolve("outside-chat.md"), chat, UTF_8);
        Files.createSymbolicLink(store.chatDirectory(entryId).resolve("linked.md"), outside);

        var refusal = assertThrows(StoreRefusal.class, this::move,
                "a link inside the transcript should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("linked.md"),
                        "the refusal should name the linked transcript file"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("the walk refuses an unnamed link below the source directory before a stored file is read")
    void refusesAPathBelowALinkedParent() throws Exception {
        var parent = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var stored = new SourceFile(parent + "/" + SourceManifestCodec.FILE_NAME + ".md", 2,
                StoreDigest.of("{}".getBytes(UTF_8)).sha256());
        var manifest = new SourceManifest(1, List.of(new Source(sourceIdFor("parent"), SourceManifest.Kind.DESCRIPTION,
                "ref", "operator", Trust.TRUSTED, List.of(stored), at, null, null)));
        Files.createDirectories(plan.pathBase().resolve("sources"));
        inWorkspace(transaction -> store.create(transaction, entry()));
        inWorkspace(transaction -> store.replaceSources(transaction, entryId, manifest));
        var elsewhere = Files.createDirectory(base.resolve("elsewhere-sources"));
        Files.writeString(elsewhere.resolve(SourceManifestCodec.FILE_NAME + ".md"), "{}", UTF_8);
        Files.createSymbolicLink(store.sourcesDirectory(entryId).resolve(parent), elsewhere);

        var refusal = assertThrows(StoreRefusal.class, this::move,
                "an unnamed link below sources should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains(parent),
                        "the refusal should name the linked directory"),
                () -> assertTrue(refusal.getMessage().contains("symbolic link"),
                        "the refusal should name the link it found"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a stored path below a linked parent refuses the composition through the read guard")
    void refusesAPathBelowALinkedParentInTheComposition() throws Exception {
        var parent = Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var content = Generators.letterStrings(5, 20).next().getBytes(UTF_8);
        // Fixture path: the stored file name the manifest names below the linked directory
        var stored = new SourceFile(parent + "/description.md", content.length, StoreDigest.of(content).sha256());
        var manifest = new SourceManifest(1, List.of(new Source(sourceIdFor("parent"), SourceManifest.Kind.DESCRIPTION,
                "ref", "operator", Trust.TRUSTED, List.of(stored), at, null, null)));
        var sources = Files.createDirectories(base.resolve("composition-sources"));
        var elsewhere = Files.createDirectory(base.resolve("elsewhere-composition"));
        Files.write(elsewhere.resolve("description.md"), content);
        Files.createSymbolicLink(sources.resolve(parent), elsewhere);

        var refusal = assertThrows(StoreRefusal.class,
                () -> PendingEntryCreation.composeRequest(sources, manifest),
                "a linked parent should refuse the composition");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("description.md"),
                        "the refusal should name the stored file"),
                () -> assertTrue(refusal.getMessage().contains("symbolic link"),
                        "the refusal should name the link it found"));
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a special file the manifest names refuses the move, never opened as a source")
    void refusesASpecialFileTheManifestNames() throws Exception {
        prepareEntry();
        // Fixture path: the stored file name prepareEntry's manifest names
        var socket = store.sourcesDirectory(entryId).resolve("description-1.md");
        Files.delete(socket);
        try (var channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX)) {
            channel.bind(UnixDomainSocketAddress.of(socket));

            var refusal = assertThrows(StoreRefusal.class, this::move,
                    "a special file the manifest names should refuse the move");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains("description-1.md"),
                            "the refusal should name the special file"),
                    () -> assertTrue(refusal.getMessage().contains("no regular file"),
                            "the refusal should say the file is no regular file"),
                    () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                            "nothing should be written into the new scope"));
        }
    }

    @Test
    @EnabledOnOs({OS.LINUX, OS.MAC})
    @DisplayName("a transcript that is no directory refuses the move with its own reason")
    void refusesATranscriptThatIsNoDirectory() throws Exception {
        prepareEntry();
        var transcript = store.chatDirectory(entryId);
        Files.delete(transcript.resolve("session.md"));
        Files.delete(transcript);
        Files.writeString(transcript, chat, UTF_8);

        var refusal = assertThrows(StoreRefusal.class, this::move,
                "a transcript that is a file should refuse the move");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains("no directory"),
                        "the refusal should say the transcript is no directory"),
                () -> assertFalse(Files.exists(plan.pathBase().resolve("sources/sources.json")),
                        "nothing should be written into the new scope"));
    }

    private String sourceIdFor(String prefix) {
        return prefix + "_" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("finish reports a directory another end removed first, never an error")
    void finishToleratesARemovedDirectory() throws Exception {
        prepareEntry();
        move();
        creation.finish(entryId);

        assertFalse(creation.finish(entryId), "a removed directory should be reported as absent");
    }

    /** Runs the move and commits the new scope only: the crash window between the two commits. */
    private void moveTargetOnly() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(workspace.lockKey());
            transaction.acquire(plan.lockKey());
            var workspaceWrite = ScopeTransaction.open(transaction, workspace, audit, clock);
            var target = ScopeTransaction.open(transaction, plan, audit, clock);
            creation.move(workspaceWrite, target, entryId);
            target.commit();
        }
    }

    @Test
    @DisplayName("a creation cut after the new scope's commit is refused on a retry, never carried twice")
    void refusesARetryAfterTheTargetCommitted() throws Exception {
        prepareEntry();
        moveTargetOnly();

        assertThrows(IllegalStateException.class, this::move,
                "the target stores refuse a repeated move");
    }

    @Test
    @DisplayName("an entry that does not exist is refused, never moved as an empty one")
    void refusesAnAbsentEntry() {
        assertThrows(IllegalArgumentException.class, this::move,
                "a missing entry cannot be moved");
    }

    @Test
    @DisplayName("a second move of the consumed entry is refused, so its stores are never carried twice")
    void refusesARepeatedMove() throws Exception {
        prepareEntry();
        move();

        assertThrows(IllegalArgumentException.class, this::move,
                "the consumed entry cannot be moved again");
    }
}
