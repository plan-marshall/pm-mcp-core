/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.VerificationRecord;

/**
 * The verifying-binary control (PM-CLIENT-5, Profile Verification): the installed binary is asked with a fixed argument
 * vector and only the record's own version and commit are accepted, while the two control records drive the
 * ineligibility assertions of the load-time gate.
 */
@DisplayName("The verifying-binary control")
class VerifyingBinaryCheckTest {

    private static final Path BINARY = Path.of("pm-mcpd");

    private static final VerificationRecord.Tool RECORDED = new VerificationRecord.Tool("0.1.1996", "abcdef0123");

    @Test
    @DisplayName("a binary of the recorded version and commit matches")
    void aMatchingBinaryMatches() {
        var installed = new VerifyingBinaryCheck.Identity(RECORDED.version(), RECORDED.commit());
        var check = VerifyingBinaryCheck.of(BINARY, (executable, argv) -> installed);

        var verdict = check.verify(RECORDED);

        var match = assertInstanceOf(VerifyingBinaryCheck.Verdict.Match.class, verdict);
        assertEquals(installed, match.installed(), "the match names the installed identity");
    }

    @Test
    @DisplayName("a binary of another commit is a mismatch that names both builds")
    void anotherCommitIsAMismatch() {
        var installed = new VerifyingBinaryCheck.Identity(RECORDED.version(), "ffffffff");
        var check = VerifyingBinaryCheck.of(BINARY, (executable, argv) -> installed);

        var verdict = check.verify(RECORDED);

        var mismatch = assertInstanceOf(VerifyingBinaryCheck.Verdict.Mismatch.class, verdict);
        assertAll(
                () -> assertEquals(installed, mismatch.installed(), "the mismatch names the installed build"),
                () -> assertEquals(RECORDED, mismatch.recorded(), "the mismatch names the recorded build"));
    }

    @Test
    @DisplayName("a binary that reports no identity is unreadable")
    void anUnreadableBinaryIsUnreadable() {
        var check = VerifyingBinaryCheck.of(BINARY, (executable, argv) -> {
            throw new IOException("no answer");
        });

        var verdict = check.verify(RECORDED);

        assertInstanceOf(VerifyingBinaryCheck.Verdict.Unreadable.class, verdict);
    }

    @Test
    @DisplayName("the verifier asks the installed binary with the fixed argument vector")
    void theFixedArgvIsUsed() {
        var seen = new AtomicReference<List<String>>();
        var check = VerifyingBinaryCheck.of(BINARY, (executable, argv) -> {
            seen.set(argv);
            return new VerifyingBinaryCheck.Identity(RECORDED.version(), RECORDED.commit());
        });

        check.verify(RECORDED);

        assertAll(
                () -> assertEquals(VerifyingBinaryCheck.FIXED_ARGV, seen.get(), "the fixed argv is used"),
                () -> assertFalse(seen.get().contains(RECORDED.commit()),
                        "no value of the record becomes an argument"));
    }

    @Test
    @DisplayName("both control records load and carry the stage that refuses the harness")
    void controlRecordsLoad() {
        var faults = VerificationRecord.read(resource("verification-record-faults-failed.json"), "faults");
        var isolation = VerificationRecord.read(resource("verification-record-isolation-not-run.json"), "isolation");

        assertAll(
                () -> assertEquals("failed", faults.stages().get("faults").status(),
                        "the faults control refuses"),
                () -> assertEquals("not_run", isolation.stages().get("isolation").status(),
                        "the isolation control refuses"));
    }

    private static byte[] resource(String name) {
        try (InputStream in = VerifyingBinaryCheckTest.class.getResourceAsStream("/worker/" + name)) {
            if (in == null) {
                throw new IllegalStateException("test resource is absent: " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("test resource could not be read: " + name, e);
        }
    }
}
