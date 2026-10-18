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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.ProjectStore.Creation;
import de.planmarshall.core.store.ProjectStore.Directory;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The per-project machine store (PM-ARCH-1, PM-IMPL-2): its directories resolve below
 * {@code <PM_MCP_BASE>/projects/<project_id>/}, its creation under the workspace lock writes {@code store.json} once,
 * and a read of its identity tells a store that does not exist from an identity record that cannot be read, never
 * answering either as an empty record.
 */
@DisplayName("Project store")
class ProjectStoreTest {

    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private ProjectStore store;
    private RecordingLockManager locks;
    private ProjectStoreIdentity identity;

    @BeforeEach
    void createStore() {
        store = new ProjectStore(base, PROJECT);
        locks = new RecordingLockManager();
        identity = ProjectStoreIdentityCodecTest.identity(PROJECT, "/home/oliver/git/pm-mcp-core");
    }

    private Creation create(ProjectStoreIdentity created) {
        try (var transaction = locks.openTransaction()) {
            return store.create(transaction, created);
        }
    }

    @Nested
    @DisplayName("its paths")
    class Paths {

        @Test
        @DisplayName("the store and its identity record lie below projects/<project_id>/ of the base directory")
        void root() {
            var root = base.toAbsolutePath().normalize().resolve("projects").resolve(PROJECT);

            assertAll(
                    () -> assertEquals(root, store.root()),
                    () -> assertEquals(root.resolve("store.json"), store.identityFile()),
                    () -> assertEquals(PROJECT, store.projectId()),
                    () -> assertEquals(LockKey.workspace(base, PROJECT), store.lockKey()));
        }

        @Test
        @DisplayName("every directory resolves below the store by its name")
        void directories() {
            var names = Arrays.stream(Directory.values()).map(directory -> store.directory(directory)).toList();

            assertEquals(Stream.of("plans", "archive", "workspace", "epics", "pending", "locks", "queue", "jobs")
                    .map(store.root()::resolve).toList(), names);
        }

        @Test
        @DisplayName("a project id outside the identifier grammar never becomes a path")
        void invalidProjectId() {
            assertThrows(IllegalArgumentException.class, () -> new ProjectStore(base, "../other"));
        }
    }

    @Nested
    @DisplayName("its creation")
    class Creating {

        @Test
        @DisplayName("creates every directory and writes the identity, under the workspace lock")
        void createsStore() throws Exception {
            Creation creation;
            try (var transaction = locks.openTransaction()) {
                creation = store.create(transaction, identity);

                assertTrue(transaction.holds(LockKey.workspace(base, PROJECT)), "the workspace lock is held");
            }

            assertAll(
                    () -> assertEquals(Creation.CREATED, creation),
                    () -> assertArrayEquals(ProjectStoreIdentityCodec.write(identity),
                            Files.readAllBytes(store.identityFile())),
                    () -> assertTrue(Arrays.stream(Directory.values())
                                    .allMatch(directory -> Files.isDirectory(store.directory(directory))),
                            "every directory exists"),
                    () -> assertEquals(new ProjectStore.Present(identity), store.readIdentity()));
        }

        @Test
        @DisplayName("writes store.json once: a second creation leaves the recorded identity as it is")
        void writesOnce() throws Exception {
            create(identity);
            var recorded = Files.readAllBytes(store.identityFile());
            var renamed = new ProjectStoreIdentity(PROJECT, "/elsewhere/pm-mcp-core", "renamed",
                    identity.createdAt().plusSeconds(60), null);

            var second = create(renamed);

            assertAll(
                    () -> assertEquals(Creation.EXISTED, second),
                    () -> assertArrayEquals(recorded, Files.readAllBytes(store.identityFile())));
        }

        @Test
        @DisplayName("leaves an unreadable identity record as it is")
        void keepsUnreadableRecord() throws Exception {
            Files.createDirectories(store.root());
            Files.writeString(store.identityFile(), "{\"format_version\":1", UTF_8);

            var creation = create(identity);

            assertAll(
                    () -> assertEquals(Creation.EXISTED, creation),
                    () -> assertEquals("{\"format_version\":1", Files.readString(store.identityFile(), UTF_8)));
        }

        @Test
        @DisplayName("refuses the identity of another project and writes nothing")
        void otherProject() {
            var other = ProjectStoreIdentityCodecTest.identity("other-project", "/home/oliver/git/other");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> create(other)),
                    () -> assertFalse(Files.exists(store.root()), "nothing is created"),
                    () -> assertEquals(0, locks.acquisitions().size(), "no lock is taken"));
        }
    }

    static Stream<Arguments> unreadableRecords() {
        return Stream.of(
                Arguments.of("an empty file", "", Outcome.UNREADABLE),
                Arguments.of("a torn record", "{\"format_version\":1,\"project_id\":\"pm-mcp-core\"",
                        Outcome.UNREADABLE),
                Arguments.of("an empty object with its version", "{\"format_version\":1}", Outcome.UNREADABLE),
                Arguments.of("a record outside the schema", ProjectStoreIdentityCodecTest.ENROLLED
                        .replace("\"unenrolled_at\":null", "\"unenrolled_at\":null,\"x\":1"), Outcome.UNREADABLE),
                Arguments.of("a newer format version", ProjectStoreIdentityCodecTest.ENROLLED
                        .replace("\"format_version\":1", "\"format_version\":2"), Outcome.FORMAT_NEWER));
    }

    @Nested
    @DisplayName("the read of its identity")
    class ReadingIdentity {

        @Test
        @DisplayName("of a store that does not exist is absent, naming the record")
        void absent() {
            assertEquals(new ProjectStore.Absent(store.identityFile()), store.readIdentity());
        }

        @Test
        @DisplayName("of a store directory without store.json is absent, never an empty record")
        void directoryWithoutRecord() throws Exception {
            Files.createDirectories(store.root());

            assertEquals(new ProjectStore.Absent(store.identityFile()), store.readIdentity());
        }

        @Test
        @DisplayName("matched control: an unenrolled identity is present with its unenrolment")
        void presentUnenrolled() throws Exception {
            Files.createDirectories(store.root());
            Files.writeString(store.identityFile(), ProjectStoreIdentityCodecTest.UNENROLLED, UTF_8);

            var read = assertInstanceOf(ProjectStore.Present.class, store.readIdentity());

            assertEquals(ProjectStoreIdentityCodecTest.read(ProjectStoreIdentityCodecTest.UNENROLLED),
                    read.identity());
        }

        @Test
        @DisplayName("of a store.json of another project, restored into this store, is unreadable naming the mismatch")
        void foreignIdentity() throws Exception {
            var beta = new ProjectStore(base, "beta");
            Files.createDirectories(beta.root());
            Files.writeString(beta.identityFile(), ProjectStoreIdentityCodecTest.ENROLLED, UTF_8);

            var read = assertInstanceOf(ProjectStore.Unreadable.class, beta.readIdentity());

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, read.refusal().outcome()),
                    () -> assertEquals(beta.identityFile(), read.refusal().file()),
                    () -> assertTrue(read.refusal().getMessage()
                            .contains("project_id " + PROJECT + " is not the project beta"),
                            read.refusal().getMessage()));
        }

        @Test
        @DisplayName("matched control: the same store.json in the store of its own project is present")
        void ownIdentity() throws Exception {
            Files.createDirectories(store.root());
            Files.writeString(store.identityFile(), ProjectStoreIdentityCodecTest.ENROLLED, UTF_8);

            var read = assertInstanceOf(ProjectStore.Present.class, store.readIdentity());

            assertEquals(ProjectStoreIdentityCodecTest.read(ProjectStoreIdentityCodecTest.ENROLLED), read.identity());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.ProjectStoreTest#unreadableRecords")
        @DisplayName("of a store.json that cannot be read is unreadable with the refusal, never an empty record")
        void unreadable(String name, String content, Outcome outcome) throws Exception {
            Files.createDirectories(store.root());
            Files.writeString(store.identityFile(), content, UTF_8);

            var read = assertInstanceOf(ProjectStore.Unreadable.class, store.readIdentity());

            assertAll(
                    () -> assertEquals(outcome, read.refusal().outcome()),
                    () -> assertEquals(store.identityFile(), read.refusal().file()));
        }
    }
}
