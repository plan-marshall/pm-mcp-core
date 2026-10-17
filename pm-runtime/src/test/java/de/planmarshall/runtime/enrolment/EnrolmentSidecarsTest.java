/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditHeadRecord;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.enrolment.EnrolmentSidecars.Failure;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * Every HMAC sidecar under one key (PM-SEC-5, PM-CRED-2): the enrolment files, {@code harnesses.json} and the audit
 * head pass under the key they were signed with; each kind fails under another key or when tampered, naming the file;
 * the locks are taken in the total lock order behind the credentials lock.
 */
@DisplayName("EnrolmentSidecars: every sidecar under one key")
class EnrolmentSidecarsTest {

    private static final String USER = System.getProperty("user.name");
    private static final String OWNER_ONLY = "rw-------";
    private static final List<String> PROJECTS = List.of("beta-project", "alpha-project");
    private static final byte[] HARNESSES = """
            {"format_version":1,"harnesses":{}}""".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random = new SecureRandom();
    private final List<AuditEvent> events = new ArrayList<>();

    @TempDir
    Path base;

    private SecretKey key;
    private FileLockManager locks;
    private EnrolmentStore store;
    private EnrolmentSidecars sidecars;

    @BeforeEach
    void signEverything() throws IOException {
        key = newKey();
        AuditSink sink = events::add;
        locks = new FileLockManager(sink);
        store = new EnrolmentStore(base);
        Files.createDirectories(store.directory());
        Files.createDirectories(AuditHead.headFile(base).getParent());
        for (var projectId : PROJECTS) {
            signed(store.file(projectId), store.sidecar(projectId), enrolment(projectId), key);
        }
        signed(harnesses(), harnessesSidecar(), HARNESSES, key);
        writeHead(key);
        sidecars = new EnrolmentSidecars(base, USER);
    }

    private SecretKey newKey() {
        var bytes = new byte[EnrolmentKey.KEY_BYTES];
        random.nextBytes(bytes);
        return new SecretKeySpec(bytes, EnrolmentKey.ALGORITHM);
    }

    private static byte[] enrolment(String projectId) {
        return ("{\"project_id\":\"" + projectId + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private Path harnesses() {
        return base.resolve(HarnessesSidecarCheck.FILE_NAME);
    }

    private Path harnessesSidecar() {
        return base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX);
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(OWNER_ONLY));
    }

    private static void signed(Path file, Path sidecar, byte[] content, SecretKey under) throws IOException {
        write(file, content);
        write(sidecar, EnrolmentHmac.sidecar(under, content).write());
    }

    private void writeHead(SecretKey under) throws IOException {
        var unsigned = new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 1, UUID.randomUUID().toString(),
                AuditCodec.GENESIS_HASH, AuditCodec.GENESIS_HASH, AuditCodec.GENESIS_HASH);
        write(AuditHead.headFile(base), AuditCodec.writeHead(AuditHead.sign(under, unsigned)));
    }

    private Optional<Failure> verify(SecretKey under) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(LockKey.credentials(base));
            return sidecars.verify(transaction, under);
        }
    }

    private static void assertFailure(Optional<Failure> failure, Path file, String reason) {
        assertAll(
                () -> assertTrue(failure.isPresent()),
                () -> assertEquals(file, failure.orElseThrow().file()),
                () -> assertTrue(failure.orElseThrow().reason().contains(reason), failure.orElseThrow().reason()));
    }

    @Test
    @DisplayName("all three kinds pass under the key they were signed with")
    void passUnderTheirKey() {
        assertTrue(verify(key).isEmpty());
    }

    @Test
    @DisplayName("the locks are taken behind the credentials lock in the total order, with no lock_order_violation")
    void lockOrder() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(LockKey.credentials(base));

            var failure = sidecars.verify(transaction, key);

            assertAll(
                    () -> assertTrue(failure.isEmpty()),
                    () -> assertTrue(transaction.holds(LockKey.enrolment(base, "alpha-project"))),
                    () -> assertTrue(transaction.holds(LockKey.enrolment(base, "beta-project"))),
                    () -> assertTrue(transaction.holds(LockKey.harnesses(base))),
                    () -> assertTrue(transaction.holds(LockKey.auditLog(base))),
                    () -> assertTrue(events.stream().noneMatch(
                            event -> AuditEvent.LOCK_ORDER_VIOLATION.equals(event.event()))));
        }
    }

    @Test
    @DisplayName("a machine with no enrolment, no harnesses.json and no audit head has nothing to fail")
    void nothingSigned() throws Exception {
        for (var projectId : PROJECTS) {
            Files.delete(store.file(projectId));
            Files.delete(store.sidecar(projectId));
        }
        Files.delete(harnesses());
        Files.delete(harnessesSidecar());
        Files.delete(AuditHead.headFile(base));

        assertTrue(verify(newKey()).isEmpty());
    }

    @Test
    @DisplayName("under another key the first enrolment file in project order fails on its HMAC")
    void anotherKey() {
        assertFailure(verify(newKey()), store.file("alpha-project"), EnrolmentCheck.HMAC_MISMATCH);
    }

    @Nested
    @DisplayName("each kind fails on its own, naming its file")
    class EachKind {

        @Test
        @DisplayName("a tampered enrolment file fails on its size or SHA-256")
        void tamperedEnrolment() throws Exception {
            write(store.file("beta-project"), enrolment("gamma-project"));

            assertFailure(verify(key), store.file("beta-project"), EnrolmentCheck.DIGEST_MISMATCH);
        }

        @Test
        @DisplayName("an enrolment file signed under another key fails on its HMAC")
        void enrolmentUnderAnotherKey() throws Exception {
            signed(store.file("beta-project"), store.sidecar("beta-project"), enrolment("beta-project"), newKey());

            assertFailure(verify(key), store.file("beta-project"), EnrolmentCheck.HMAC_MISMATCH);
        }

        @Test
        @DisplayName("an enrolment file without its sidecar fails")
        void enrolmentWithoutSidecar() throws Exception {
            Files.delete(store.sidecar("alpha-project"));

            assertFailure(verify(key), store.file("alpha-project"), EnrolmentCheck.FILE_WITHOUT_SIDECAR);
        }

        @Test
        @DisplayName("an enrolment sidecar that fails its strict read fails as unreadable")
        void unreadableEnrolmentSidecar() throws Exception {
            write(store.sidecar("alpha-project"), "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8));

            assertFailure(verify(key), store.sidecar("alpha-project"), "unreadable");
        }

        @Test
        @DisplayName("an enrolment file with mode 0644 fails a per-use check")
        void insecureEnrolment() throws Exception {
            Files.setPosixFilePermissions(store.file("alpha-project"), PosixFilePermissions.fromString("rw-r--r--"));

            assertFailure(verify(key), store.file("alpha-project"), "mode 0644 broader than 0600");
        }

        @Test
        @DisplayName("a tampered harnesses.json fails")
        void tamperedHarnesses() throws Exception {
            signed(harnesses(), harnessesSidecar(), HARNESSES, newKey());

            assertFailure(verify(key), harnesses(), EnrolmentCheck.HMAC_MISMATCH);
        }

        @Test
        @DisplayName("an audit head signed under another key fails")
        void tamperedAuditHead() throws Exception {
            writeHead(newKey());

            assertFailure(verify(key), AuditHead.headFile(base), EnrolmentSidecars.HEAD_HMAC_MISMATCH);
        }

        @Test
        @DisplayName("an audit head that does not parse fails as unreadable")
        void unreadableAuditHead() throws Exception {
            write(AuditHead.headFile(base), "{}".getBytes(StandardCharsets.UTF_8));

            assertFailure(verify(key), AuditHead.headFile(base), "unreadable");
        }
    }
}
