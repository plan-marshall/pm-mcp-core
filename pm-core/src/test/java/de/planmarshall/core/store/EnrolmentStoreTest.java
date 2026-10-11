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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The project enrolment store (PM-EXEC-2, PM-IMPL-7): one file and one sidecar per project, read and written only
 * under the project's own lock, the set of enrolled projects being the listing of the directory.
 */
@DisplayName("Enrolment store")
class EnrolmentStoreTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String HMAC = "b".repeat(64);

    @TempDir
    Path base;

    private EnrolmentStore store;
    private RecordingLockManager locks;

    @BeforeEach
    void createDirectory() throws IOException {
        Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY));
        store = new EnrolmentStore(base);
        locks = new RecordingLockManager();
    }

    private static ProjectEnrolment enrolment(String projectId, boolean pilot) {
        return new ProjectEnrolment(projectId, "/home/dev/git/" + projectId, Instant.parse("2026-10-11T08:00:00Z"),
                pilot, null, null, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(),
                new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    private static EnrolmentSidecar sidecarOf(ProjectEnrolment enrolment) {
        return new EnrolmentSidecar(StoreDigest.of(EnrolmentCodec.write(enrolment)), HMAC);
    }

    private void enrol(ProjectEnrolment enrolment) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(store.lockKey(enrolment.projectId()));
            store.write(transaction, enrolment, sidecarOf(enrolment));
        }
    }

    @Test
    @DisplayName("the file, its sidecar and its lock have the paths of the enrolment store")
    void paths() {
        assertAll(
                () -> assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json"),
                        store.file(PROJECT)),
                () -> assertEquals(base.toAbsolutePath().normalize().resolve("enrolments/pm-mcp-core.json.hmac"),
                        store.sidecar(PROJECT)),
                () -> assertEquals(LockKey.enrolment(base, PROJECT), store.lockKey(PROJECT)),
                () -> assertThrows(IllegalArgumentException.class, () -> store.file("../projects")));
    }

    @Nested
    @DisplayName("under the project's lock")
    class UnderLock {

        @Test
        @DisplayName("a written enrolment is read back as its canonical bytes and its sidecar record")
        void writeAndRead() {
            var enrolment = enrolment(PROJECT, true);

            enrol(enrolment);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));
                var content = store.readFile(transaction, PROJECT).orElseThrow();
                var sidecar = store.decodeSidecar(PROJECT, store.readSidecar(transaction, PROJECT).orElseThrow());

                assertAll(
                        () -> assertArrayEquals(EnrolmentCodec.write(enrolment), content),
                        () -> assertEquals(enrolment, store.decode(PROJECT, content)),
                        () -> assertEquals(sidecarOf(enrolment), sidecar),
                        () -> assertTrue(sidecar.matches(content)));
            }
            assertEquals(List.of(LockKey.enrolment(base, PROJECT), LockKey.enrolment(base, PROJECT)),
                    locks.acquisitions());
        }

        @Test
        @DisplayName("a project without an enrolment reads as no file and no sidecar")
        void absent() {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertAll(
                        () -> assertTrue(store.readFile(transaction, PROJECT).isEmpty()),
                        () -> assertTrue(store.readSidecar(transaction, PROJECT).isEmpty()));
            }
        }

        @Test
        @DisplayName("a sidecar that does not record the canonical bytes is refused and nothing is written")
        void mismatchedSidecar() {
            var enrolment = enrolment(PROJECT, false);
            var other = sidecarOf(enrolment(PROJECT, true));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertThrows(IllegalArgumentException.class, () -> store.write(transaction, enrolment, other));
            }
            assertAll(
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))));
        }

        @Test
        @DisplayName("a re-baseline replaces the sidecar only")
        void writeSidecar() throws Exception {
            var enrolment = enrolment(PROJECT, false);
            enrol(enrolment);
            var file = Files.readAllBytes(store.file(PROJECT));
            var rebaselined = new EnrolmentSidecar(StoreDigest.of(file), "c".repeat(64));

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));
                store.writeSidecar(transaction, PROJECT, rebaselined);
            }

            assertAll(
                    () -> assertArrayEquals(file, Files.readAllBytes(store.file(PROJECT))),
                    () -> assertArrayEquals(rebaselined.write(), Files.readAllBytes(store.sidecar(PROJECT))));
        }

        @Test
        @DisplayName("a removal deletes the file and its sidecar and nothing else, and reports what it found")
        void remove() throws Exception {
            enrol(enrolment(PROJECT, false));
            var projectStore = Files.createDirectories(base.resolve("projects").resolve(PROJECT));
            Files.writeString(projectStore.resolve("store.json"), "{}");

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey(PROJECT));

                assertTrue(store.remove(transaction, PROJECT));
                assertFalse(store.remove(transaction, PROJECT));
            }
            assertAll(
                    () -> assertFalse(Files.exists(store.file(PROJECT))),
                    () -> assertFalse(Files.exists(store.sidecar(PROJECT))),
                    () -> assertEquals("{}", Files.readString(projectStore.resolve("store.json"))));
        }
    }

    @Nested
    @DisplayName("without the project's lock")
    class WithoutLock {

        @Test
        @DisplayName("every read and write is refused as an internal fault, and another project's lock does not count")
        void refused() {
            var enrolment = enrolment(PROJECT, false);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(store.lockKey("other-project"));

                assertAll(
                        () -> assertEquals(Reason.STORE_LOCK_NOT_HELD, assertThrows(InternalFaultException.class,
                                () -> store.write(transaction, enrolment, sidecarOf(enrolment))).reason()),
                        () -> assertThrows(InternalFaultException.class, () -> store.readFile(transaction, PROJECT)),
                        () -> assertThrows(InternalFaultException.class,
                                () -> store.readSidecar(transaction, PROJECT)),
                        () -> assertThrows(InternalFaultException.class,
                                () -> store.writeSidecar(transaction, PROJECT, sidecarOf(enrolment))),
                        () -> assertThrows(InternalFaultException.class, () -> store.remove(transaction, PROJECT)));
            }
            assertFalse(Files.exists(store.file(PROJECT)));
        }
    }

    @Nested
    @DisplayName("listing and decoding")
    class ListingAndDecoding {

        @Test
        @DisplayName("the enrolled projects are the enrolment files of the directory, not sidecars or other entries")
        void projectIds() throws Exception {
            enrol(enrolment("zeta-project", false));
            enrol(enrolment(PROJECT, false));
            Files.writeString(store.directory().resolve("notes.txt"), "x");
            Files.writeString(store.directory().resolve("Upper.json"), "{}");

            assertEquals(Set.of(PROJECT, "zeta-project"), store.projectIds());
        }

        @Test
        @DisplayName("a store without its directory lists no project")
        void noDirectory() {
            assertTrue(new EnrolmentStore(base.resolve("elsewhere")).projectIds().isEmpty());
        }

        @Test
        @DisplayName("an enrolment whose project_id is not the name of its file is refused as unreadable")
        void foreignProjectId() {
            var content = EnrolmentCodec.write(enrolment("other-project", false));

            var refusal = assertThrows(StoreRefusal.class, () -> store.decode(PROJECT, content));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(store.file(PROJECT), refusal.file()));
        }

        @Test
        @DisplayName("an unreadable sidecar is refused naming the sidecar file")
        void unreadableSidecar() {
            var refusal = assertThrows(StoreRefusal.class,
                    () -> store.decodeSidecar(PROJECT, HMAC.getBytes(StandardCharsets.UTF_8)));

            assertEquals(store.sidecar(PROJECT), refusal.file());
        }
    }
}
