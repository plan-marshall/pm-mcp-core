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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck.Outcome;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck.Result;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The verification of the harness enrolment sidecar (PM-SEC-5, PM-CRED-2): {@code harnesses.json} passes against its
 * sidecar under the key it was signed with and fails under any other key; a missing half of the pair, a digest
 * mismatch, an HMAC mismatch, a failed per-use check and an unreadable sidecar each fail with their reason; the check
 * holds {@code locks/harnesses.lock} while it reads.
 */
@DisplayName("Harnesses sidecar check")
class HarnessesSidecarCheckTest {

    private static final String USER = System.getProperty("user.name");
    private static final String OWNER_ONLY = "rw-------";
    private static final byte[] CONTENT = """
            {"format_version":1,"harnesses":{}}""".getBytes(StandardCharsets.UTF_8);

    private final SecureRandom random = new SecureRandom();

    @TempDir
    Path base;

    private Path file;
    private Path sidecar;
    private SecretKey key;
    private FileLockManager locks;
    private HarnessesSidecarCheck check;

    @BeforeEach
    void setUp() {
        file = base.resolve(HarnessesSidecarCheck.FILE_NAME);
        sidecar = base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX);
        key = newKey();
        AuditSink sink = _ -> {
        };
        locks = new FileLockManager(sink);
        check = new HarnessesSidecarCheck(base, USER);
    }

    private SecretKey newKey() {
        var bytes = new byte[EnrolmentKey.KEY_BYTES];
        random.nextBytes(bytes);
        return new SecretKeySpec(bytes, EnrolmentKey.ALGORITHM);
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        Files.write(path, bytes);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(OWNER_ONLY));
    }

    private void signed(byte[] content) throws IOException {
        write(file, content);
        write(sidecar, EnrolmentHmac.sidecar(key, content).write());
    }

    private Result verify(SecretKey under) {
        try (var transaction = locks.openTransaction()) {
            return check.verify(transaction, under);
        }
    }

    private static void assertFailed(Result result, Outcome outcome, Path concerned, String reason) {
        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(outcome, result.outcome()),
                () -> assertEquals(Optional.of(concerned), result.file()),
                () -> assertTrue(result.reason().orElseThrow().contains(reason), result.reason().orElseThrow()));
    }

    @Test
    @DisplayName("a file and sidecar signed under a key pass under that key")
    void passesUnderItsKey() throws Exception {
        signed(CONTENT);

        var result = verify(key);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertEquals(Outcome.VERIFIED, result.outcome()),
                () -> assertTrue(result.file().isEmpty()),
                () -> assertTrue(result.reason().isEmpty()));
    }

    @Test
    @DisplayName("a file and sidecar signed under a key fail on the HMAC under another key")
    void failsUnderAnotherKey() throws Exception {
        signed(CONTENT);

        assertFailed(verify(newKey()), Outcome.TAMPERED, file, EnrolmentCheck.HMAC_MISMATCH);
    }

    @Test
    @DisplayName("neither the file nor its sidecar: nothing to verify, which passes")
    void bothAbsent() {
        var result = verify(key);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertEquals(Outcome.NOTHING_TO_VERIFY, result.outcome()),
                () -> assertTrue(result.file().isEmpty()));
    }

    @Test
    @DisplayName("the check holds locks/harnesses.lock in the caller's transaction")
    void acquiresTheHarnessesLock() throws Exception {
        signed(CONTENT);

        try (var transaction = locks.openTransaction()) {
            check.verify(transaction, key);

            assertTrue(transaction.holds(LockKey.harnesses(base)));
        }
    }

    @Nested
    @DisplayName("a tampered pair fails as tampered with its reason")
    class Tampered {

        @Test
        @DisplayName("a file without its sidecar")
        void fileWithoutSidecar() throws Exception {
            write(file, CONTENT);

            assertFailed(verify(key), Outcome.TAMPERED, file, HarnessesSidecarCheck.FILE_WITHOUT_SIDECAR);
        }

        @Test
        @DisplayName("a sidecar without its file")
        void sidecarWithoutFile() throws Exception {
            signed(CONTENT);
            Files.delete(file);

            assertFailed(verify(key), Outcome.TAMPERED, sidecar, HarnessesSidecarCheck.SIDECAR_WITHOUT_FILE);
        }

        @Test
        @DisplayName("a changed byte fails on the size or SHA-256")
        void changedByte() throws Exception {
            signed(CONTENT);
            var changed = CONTENT.clone();
            changed[changed.length - 2] = ' ';
            write(file, changed);

            assertFailed(verify(key), Outcome.TAMPERED, file, EnrolmentCheck.DIGEST_MISMATCH);
        }

        @Test
        @DisplayName("a sidecar with the right size and SHA-256 and a wrong HMAC fails on the HMAC")
        void wrongHmac() throws Exception {
            write(file, CONTENT);
            write(sidecar, new EnrolmentSidecar(StoreDigest.of(CONTENT), "0".repeat(64)).write());

            assertFailed(verify(key), Outcome.TAMPERED, file, EnrolmentCheck.HMAC_MISMATCH);
        }
    }

    @Nested
    @DisplayName("a pair that fails a per-use check fails as insecure (POSIX)")
    class Insecure {

        @Test
        @DisplayName("a file with mode 0644")
        void broaderMode() throws Exception {
            signed(CONTENT);
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

            assertFailed(verify(key), Outcome.INSECURE, file, "mode 0644 broader than 0600");
        }

        @Test
        @DisplayName("a sidecar that is a symbolic link, also when its target would pass")
        void symbolicLink() throws Exception {
            signed(CONTENT);
            var target = Files.move(sidecar, base.resolve("elsewhere.hmac"));
            Files.createSymbolicLink(sidecar, target);

            assertFailed(verify(key), Outcome.INSECURE, sidecar, EnrolmentFileCheck.SYMBOLIC_LINK);
        }
    }

    @Test
    @DisplayName("a sidecar that fails its strict read fails as unreadable")
    void unreadableSidecar() throws Exception {
        write(file, CONTENT);
        write(sidecar, "{\"format_version\":1}".getBytes(StandardCharsets.UTF_8));

        assertFailed(verify(key), Outcome.UNREADABLE, sidecar, "unreadable");
    }

    @Test
    @DisplayName("a result names a failure's file and reason and a pass's neither")
    void resultShape() {
        var none = Optional.<Path>empty();
        var noReason = Optional.<String>empty();
        var some = Optional.of(file);
        var reason = Optional.of("reason");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Result(Outcome.TAMPERED, none, noReason)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Result(Outcome.VERIFIED, some, reason)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new Result(Outcome.INSECURE, some, noReason)));
    }
}
