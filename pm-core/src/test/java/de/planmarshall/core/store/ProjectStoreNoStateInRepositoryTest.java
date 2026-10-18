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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Nothing with state lies below the repository of a project (PM-ARCH-1, PM-IMPL-2): the stores of the operator's acts
 * and of the questions, and the per-project machine store with its identity, write below the base directory of the
 * runtime only. Driven against a fixture repository with its {@code .git} and its {@code .marshall/local/}, they leave
 * the repository's files as they were: no file is written below its root, {@code .marshall/local/} included.
 */
@DisplayName("Project store: no state in the repository")
class ProjectStoreNoStateInRepositoryTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);
    private static final EntryId ENTRY = new EntryId("ent-0123456789abcdef");

    @TempDir
    Path temporary;

    private Path base;
    private Path repository;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createFixtures() throws IOException {
        base = Files.createDirectories(temporary.resolve("base"));
        repository = Files.createDirectories(temporary.resolve("repository"));
        Files.createDirectories(repository.resolve(".git"));
        Files.writeString(repository.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.createDirectories(repository.resolve(".marshall/local/worktrees"));
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static Set<Path> files(Path root) throws IOException {
        try (var walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).collect(Collectors.toUnmodifiableSet());
        }
    }

    /** Runs one write of a scope in a transaction of its own and commits it. */
    private void commit(JournalScope scope, Consumer<ScopeTransaction> write) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var scopeTransaction = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            write.accept(scopeTransaction);
            scopeTransaction.commit();
        }
    }

    @Test
    @DisplayName("the new stores write below the base directory and leave the repository's files as they were")
    void noFileBelowTheRepository() throws Exception {
        var before = files(repository);
        var store = new ProjectStore(base, PROJECT);
        var plan = JournalScope.plan(base, PROJECT, PLAN);
        var workspace = JournalScope.workspace(base, PROJECT);

        try (var transaction = locks.openTransaction()) {
            store.create(transaction, ProjectStoreIdentityCodecTest.identity(PROJECT, repository.toString()));
        }
        Files.createDirectories(plan.journalFile().getParent());
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(QuestionLog.ofEntry(workspace, ENTRY).store().getParent());
        commit(plan, write -> {
            new WaiverLog(plan).append(write, WaiverRecordCodecTest.met());
            new WaiverConsumptionLog(plan).append(write, WaiverConsumptionCodecTest.consumption());
            assertEquals(Optional.empty(), new QuestionLog(plan).ask(write, List.of(asked("Q-001", "Q-001"))));
        });
        commit(workspace, write -> {
            assertEquals(Optional.empty(), new QuestionLog(workspace).ask(write, List.of(asked("Q-002", "Q-002"))));
            assertEquals(Optional.empty(),
                    QuestionLog.ofEntry(workspace, ENTRY).ask(write, List.of(asked("Q-003", "Q-003"))));
        });

        var written = files(base);
        assertAll(
                () -> assertEquals(before, files(repository), "no file is written below the repository root"),
                () -> assertFalse(base.startsWith(repository), "the base directory lies outside the repository"),
                () -> assertTrue(written.contains(store.identityFile()), "store.json is written below the base"),
                () -> assertTrue(written.contains(new WaiverLog(plan).store()), "waivers.jsonl below the base"),
                () -> assertTrue(written.contains(new WaiverConsumptionLog(plan).store()),
                        "waiver-consumptions.jsonl below the base"),
                () -> assertTrue(written.contains(new QuestionLog(plan).store()), "the plan's questions.jsonl"),
                () -> assertTrue(written.contains(new QuestionLog(workspace).store()),
                        "the workspace's questions.jsonl"),
                () -> assertTrue(written.contains(QuestionLog.ofEntry(workspace, ENTRY).store()),
                        "the entry's questions.jsonl"),
                () -> assertTrue(written.stream().allMatch(file -> file.startsWith(store.root())),
                        "every written file lies in the project's machine store"));
    }

    @Test
    @DisplayName("matched control: a file written below the repository is seen by the comparison")
    void comparisonSeesRepositoryFiles() throws Exception {
        var before = files(repository);

        Files.writeString(repository.resolve(".marshall/local/state.json"), "{}");

        assertEquals(Set.of(repository.resolve(".marshall/local/state.json")),
                files(repository).stream().filter(file -> !before.contains(file)).collect(Collectors.toSet()));
    }
}
