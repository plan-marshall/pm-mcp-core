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
import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditSink;

/**
 * Nothing with state lies below the repository of a project (PM-ARCH-1, PM-IMPL-2): the stores of the operator's acts
 * and of the questions, and the per-project machine store with its identity, write below the base directory of the
 * runtime only. Driven against a fixture repository with its {@code .git} and its {@code .marshall/local/}, they leave
 * the repository's files as they were: no file is written below its root, {@code .marshall/local/} included. The
 * repository layout ({@code .git/HEAD}, {@code .marshall/local/}) is the layout contract; plan, entry, question ids and
 * directory names are generated.
 */
@EnableGeneratorController
@DisplayName("Project store: no state in the repository")
class ProjectStoreNoStateInRepositoryTest {

    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path temporary;

    private String planId;
    private Clock clock;
    private EntryId entry;
    private Path base;
    private Path repository;
    private RecordingLockManager locks;
    private AuditSink audit;

    private static String name(String prefix) {
        return prefix + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @BeforeEach
    void createFixtures() throws IOException {
        planId = name("plan-");
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next()),
                ZoneOffset.UTC);
        entry = new EntryId("ent-%016x".formatted(Generators.longs(0, Long.MAX_VALUE).next()));
        base = Files.createDirectories(temporary.resolve(name("base-")));
        repository = Files.createDirectories(temporary.resolve(name("repository-")));
        Files.createDirectories(repository.resolve(".git"));
        // Git wire token: the HEAD file of a repository on its main branch.
        Files.writeString(repository.resolve(".git/HEAD"), "ref: refs/heads/main\n");
        Files.createDirectories(repository.resolve(".marshall/local/worktrees"));
        locks = new RecordingLockManager();
        audit = AuditSinkDoubles.capturing(newCapture(CaptureType.ALL));
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
            var scopeTransaction = ScopeTransaction.open(transaction, scope, audit, clock);
            write.accept(scopeTransaction);
            scopeTransaction.commit();
        }
    }

    @Test
    @DisplayName("the new stores write below the base directory and leave the repository's files as they were")
    void noFileBelowTheRepository() throws Exception {
        var before = files(repository);
        var store = new ProjectStore(base, PROJECT);
        var plan = JournalScope.plan(base, PROJECT, planId);
        var workspace = JournalScope.workspace(base, PROJECT);
        var first = Generators.integers(1, 997).next();
        var q1 = "Q-%03d".formatted(first);
        var q2 = "Q-%03d".formatted(first + 1);
        var q3 = "Q-%03d".formatted(first + 2);

        try (var transaction = locks.openTransaction()) {
            store.create(transaction, ProjectStoreIdentityCodecTest.identity(PROJECT, repository.toString()));
        }
        Files.createDirectories(plan.journalFile().getParent());
        Files.createDirectories(workspace.journalFile().getParent());
        Files.createDirectories(QuestionLog.ofEntry(workspace, entry).store().getParent());
        commit(plan, write -> {
            new WaiverLog(plan).append(write, WaiverRecordCodecTest.met());
            new WaiverConsumptionLog(plan).append(write, WaiverConsumptionCodecTest.consumption());
            assertEquals(Optional.empty(), new QuestionLog(plan).ask(write, List.of(asked(q1, q1))),
                    "the plan's question should be asked");
        });
        commit(workspace, write -> {
            assertEquals(Optional.empty(), new QuestionLog(workspace).ask(write, List.of(asked(q2, q2))),
                    "the workspace's question should be asked");
            assertEquals(Optional.empty(),
                    QuestionLog.ofEntry(workspace, entry).ask(write, List.of(asked(q3, q3))),
                    "the entry's question should be asked");
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
                () -> assertTrue(written.contains(QuestionLog.ofEntry(workspace, entry).store()),
                        "the entry's questions.jsonl"),
                () -> assertTrue(written.stream().allMatch(file -> file.startsWith(store.root())),
                        "every written file lies in the project's machine store"));
    }

    @Test
    @DisplayName("matched control: a file written below the repository is seen by the comparison")
    void comparisonSeesRepositoryFiles() throws Exception {
        var before = files(repository);

        var state = repository.resolve(".marshall/local").resolve(name("state-") + ".json");
        Files.writeString(state, "{}");

        assertEquals(Set.of(state),
                files(repository).stream().filter(file -> !before.contains(file)).collect(Collectors.toSet()),
                "the comparison should see the new file");
    }
}
