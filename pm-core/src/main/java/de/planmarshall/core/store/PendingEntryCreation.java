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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import lombok.Builder;

import de.planmarshall.core.store.SourceManifest.Kind;
import de.planmarshall.core.store.SourceManifest.Source;
import de.planmarshall.core.store.SourceManifest.SourceFile;

/**
 * The creation of a scope from a pending entry: the move of the entry's source directory, its transcript and its
 * decision store into the new scope, and the composition of the scope's {@code request.md}, in the transaction of the
 * new scope and the transaction of the workspace that delete the entry's stores; the entry's directory is deleted last
 * (Store Schemas § Pending Entry, Creation; Filesystem Layout § The Source Directory).
 * <p>
 * The move is the store-level part of {@code start.create-scope}: the unit calls {@link #move} between its own writes
 * of the new scope, commits the new scope's transaction first and the workspace's second, and then calls
 * {@link #finish}, which deletes the entry's directory. Two crash windows follow. A stop after the new scope's commit
 * and before the workspace's leaves the entry's record in place; the new scope already holds the stores, so a repeated
 * move is refused by their create targets and the operator ends the entry by abandoning it. A stop after both commits
 * and before {@link #finish} leaves the record gone and the directory behind, which the sweep removes by the evidence
 * of the absent record; a second move then refuses as an entry that does not exist. The directories of the new scope
 * ({@code sources/}, {@code chat/} and the parents of the stored files) are created before the stores are staged,
 * because a transaction writes stores and never directories.
 * <p>
 * The sources are copied, never moved file by file: every file is verified against the size and SHA-256 of the
 * manifest before it is written, a file that differs is refused, and the entry's directory goes whole in
 * {@link #finish}. The transcript is copied file by file; the question log is moved by
 * {@link QuestionLog#moveEntryQuestions}; the decision store is copied and deleted with the entry.
 *
 * @since 0.1
 */
public final class PendingEntryCreation {

    private static final String SOURCE_DIRECTORY = PendingEntryStore.SOURCES;
    private static final String CHAT_DIRECTORY = PendingEntryStore.CHAT;
    private static final String REQUEST = "request.md";

    private final JournalScope workspace;
    private final PendingEntryStore entries;

    /**
     * @param workspace the workspace scope of the project
     * @throws IllegalArgumentException if the scope is no workspace scope
     */
    public PendingEntryCreation(JournalScope workspace) {
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        this.entries = new PendingEntryStore(workspace);
    }

    /**
     * Adds the entry's source directory, transcript, decision store, question log and composed request document to the
     * new scope's transaction, and the deletions of the entry's question and decision stores to the workspace's
     * transaction.
     *
     * @param workspaceTransaction an open transaction of the workspace scope
     * @param target               an open transaction of the plan or epic scope the entry creates
     * @param entryId              the entry
     * @return what the move carried
     * @throws IllegalArgumentException if a transaction belongs to another scope, the target is no plan or epic, the
     *                                  entry does not exist, or a store is a target of its transaction already
     * @throws IllegalStateException    if a transaction was committed
     * @throws StoreRefusal             if the manifest is unreadable, or a stored file differs from its manifest entry
     * @throws UncheckedIOException     if the entry's files cannot be read
     */
    public Moved move(ScopeTransaction workspaceTransaction, ScopeTransaction target, EntryId entryId) {
        Objects.requireNonNull(workspaceTransaction, "workspaceTransaction");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(entryId, "entryId");
        requireOwnWorkspace(workspaceTransaction);
        var type = target.scope().type();
        if (type != JournalScopeType.PLAN && type != JournalScopeType.EPIC) {
            throw new IllegalArgumentException("An entry creates a plan or an epic, not a " + type.wireName());
        }
        if (entries.read(entryId).isEmpty()) {
            throw new IllegalArgumentException("The pending entry %s does not exist".formatted(entryId));
        }
        var manifest = entries.readSources(entryId).orElse(SourceManifest.empty());
        var sourceDirectory = entries.sourcesDirectory(entryId);
        var files = 0;
        for (var source : manifest.sources()) {
            for (var stored : source.files()) {
                put(target, SOURCE_DIRECTORY + "/" + stored.path(), verified(sourceDirectory, stored));
                files++;
            }
        }
        put(target, SOURCE_DIRECTORY + "/" + SourceManifestCodec.FILE_NAME, SourceManifestCodec.write(manifest));
        workspaceTransaction.delete(PendingEntryStore.directoryPath(entryId) + "/"
                + PendingEntryStore.SOURCE_MANIFEST);
        var chat = copyChat(target, entryId);
        var decisions = copyDecision(workspaceTransaction, target, entryId);
        var questions = QuestionLog.moveEntryQuestions(workspaceTransaction, entryId, target);
        put(target, REQUEST, composeRequest(sourceDirectory, manifest).getBytes(StandardCharsets.UTF_8));
        // The record is the last store the workspace transaction deletes: it drives the recovery of the removal
        workspaceTransaction.delete(PendingEntryStore.storePath(entryId));
        return new Moved(manifest.sources().size(), files, chat, questions, decisions);
    }

    /**
     * Deletes the entry's directory with what the move left in it, the last act of the creation. It is called after
     * both transactions committed, so a stop before it leaves only the residue the sweep removes.
     *
     * @param entryId the entry
     * @return whether the directory existed
     * @throws IllegalStateException if the entry's record still exists; its stores must be deleted first
     * @throws UncheckedIOException  if the directory or a file in it cannot be removed
     */
    public boolean finish(EntryId entryId) {
        return entries.discard(entryId);
    }

    /**
     * The document {@code request.md} of the new scope, composed from the sources: the text of each description, as
     * written, and a reference to each other source, in the order of the manifest (Filesystem Layout § The Source
     * Directory).
     *
     * @param sourceDirectory the entry's source directory
     * @param manifest        its manifest
     * @return the document, ending with a newline
     * @throws StoreRefusal         if a description's file differs from its manifest entry
     * @throws UncheckedIOException if a description's file cannot be read
     */
    public static String composeRequest(Path sourceDirectory, SourceManifest manifest) {
        Objects.requireNonNull(sourceDirectory, "sourceDirectory");
        Objects.requireNonNull(manifest, "manifest");
        var out = new StringBuilder("# Request\n\n");
        for (var source : manifest.sources()) {
            if (source.kind() != Kind.DESCRIPTION) {
                continue;
            }
            for (var stored : source.files()) {
                out.append(new String(verified(sourceDirectory, stored), StandardCharsets.UTF_8)).append("\n\n");
            }
        }
        out.append("## Sources\n\n");
        for (var source : manifest.sources()) {
            out.append(reference(source)).append('\n');
        }
        return out.toString();
    }

    private static String reference(Source source) {
        return "- `%s` (%s) — %s, from %s".formatted(source.sourceId(), source.kind().wireName(),
                source.reference(), source.origin());
    }

    /** Reads one stored file and refuses it when its size or digest differs from its manifest entry. */
    private static byte[] verified(Path sourceDirectory, SourceFile stored) {
        var file = sourceDirectory.resolve(stored.path()).normalize();
        if (!file.startsWith(sourceDirectory)) {
            throw StoreRefusal.unreadable(sourceDirectory.resolve(stored.path()), SourceManifest.FORMAT_VERSION,
                    "the source path '%s' leaves the source directory".formatted(stored.path()), null);
        }
        byte[] content;
        try {
            content = Files.readAllBytes(file);
        } catch (IOException e) {
            throw StoreRefusal.unreadable(file, SourceManifest.FORMAT_VERSION,
                    "the source file cannot be read: " + e.getMessage(), e);
        }
        var digest = sha256(content);
        if (content.length != stored.size() || !digest.equals(stored.sha256())) {
            throw StoreRefusal.unreadable(file, SourceManifest.FORMAT_VERSION,
                    "the source file differs from its manifest entry", null);
        }
        return content;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM provides SHA-256", e);
        }
    }

    /** Copies the regular files of the entry's transcript into the target's {@code chat/}, in name order. */
    private int copyChat(ScopeTransaction target, EntryId entryId) {
        var directory = entries.chatDirectory(entryId);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        var files = new ArrayList<Path>();
        try (var walk = Files.walk(directory)) {
            walk.filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(file -> directory.relativize(file).toString()))
                    .forEach(files::add);
        } catch (IOException e) {
            throw new UncheckedIOException("The transcript of %s cannot be read".formatted(entryId), e);
        }
        for (var file : files) {
            var relative = directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
            put(target, CHAT_DIRECTORY + "/" + relative, read(file));
        }
        return files.size();
    }

    /** Copies the entry's decision store into the target's, and deletes the entry's in the workspace transaction. */
    private int copyDecision(ScopeTransaction workspaceTransaction, ScopeTransaction target, EntryId entryId) {
        var path = PendingEntryStore.directoryPath(entryId) + "/" + PendingEntryStore.DECISION_LOG;
        var file = workspace.resolve(path);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        target.create(PendingEntryStore.DECISION_LOG, read(file));
        workspaceTransaction.delete(path);
        return 1;
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("The file cannot be read: " + file, e);
        }
    }

    /**
     * Adds one store of the new scope as a create target, after creating its directory: a transaction writes stores,
     * never directories, and the target scope's {@code sources/} and {@code chat/} exist only once the caller or this
     * method creates them.
     */
    private static void put(ScopeTransaction target, String relativePath, byte[] content) {
        var store = target.scope().resolveWithoutLinks(relativePath);
        try {
            Files.createDirectories(store.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("The directory of '%s' cannot be created".formatted(relativePath), e);
        }
        target.create(relativePath, content);
    }

    private void requireOwnWorkspace(ScopeTransaction transaction) {
        if (!transaction.scope().equals(workspace)) {
            throw new IllegalArgumentException("A transaction of %s does not write the pending entries of %s"
                    .formatted(transaction.scope().pathBase(), workspace.pathBase()));
        }
    }

    /**
     * What a move carried into the new scope.
     *
     * @param sources         the sources of the manifest
     * @param sourceFiles     the files written below {@code sources/}
     * @param chatFiles       the transcript files written below {@code chat/}
     * @param questionLines   the question records moved into the scope's question store
     * @param decisionStores  the decision stores written into the scope, 0 or 1
     * @since 0.1
     */
    @Builder
    public record Moved(int sources, int sourceFiles, int chatFiles, int questionLines, int decisionStores) {
    }
}
