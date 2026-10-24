/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.lock.FileLockManager;

/** The harness enrolment store: its HMAC sidecar, the re-baseline of a tampered file and the client ordering. */
@DisplayName("Harness enrolment store")
class HarnessEnrolmentStoreTest {

    private static final String USER = System.getProperty("user.name");
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @TempDir
    Path base;

    private HarnessEnrolmentStore store;

    @BeforeEach
    void setUp() {
        SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                EnrolmentKey.ALGORITHM);
        store = new HarnessEnrolmentStore(base, () -> key, new FileLockManager(event -> {
        }), USER);
    }

    @Test
    @DisplayName("writes and reads an enrolment, with the canonical file and its sidecar")
    void writeAndRead() throws Exception {
        store.enrol(enrolment("claude", "1.5.0"));
        assertEquals("1.5.0", store.find("claude").orElseThrow().version());
        assertTrue(Files.exists(base.resolve("harnesses.json")));
        assertTrue(Files.exists(base.resolve("harnesses.json.hmac")));
    }

    @Test
    @DisplayName("a tampered file is refused and never parsed")
    void tampered() throws Exception {
        store.enrol(enrolment("claude", "1.5.0"));
        Files.writeString(base.resolve("harnesses.json"), "{\"format_version\":1,\"harnesses\":{}}",
                StandardCharsets.UTF_8);
        var refusal = assertThrows(HarnessEnrolmentStore.StoreException.class, () -> store.find("claude"));
        assertEquals(HarnessEnrolmentStore.Refusal.TAMPERED, refusal.refusal());
    }

    @Test
    @DisplayName("a new enrolment re-baselines a tampered file with the named client only")
    void rebaseline() throws Exception {
        store.enrol(enrolment("claude", "1.5.0"));
        store.enrol(enrolment("opencode", "1.0.0"));
        Files.writeString(base.resolve("harnesses.json"), "{}", StandardCharsets.UTF_8);
        store.enrol(enrolment("claude", "1.6.0"));
        assertEquals(List.of("claude"), store.all().stream().map(HarnessEnrolment::clientId).toList());
        assertEquals("1.6.0", store.find("claude").orElseThrow().version());
        assertTrue(store.find("opencode").isEmpty());
    }

    @Test
    @DisplayName("an accepted self-update rewrites the version and the digest")
    void selfUpdate() {
        var enrolled = store.enrol(enrolment("claude", "1.5.0"));
        var updated = store.acceptSelfUpdate("claude", "1.6.0", "b".repeat(64), NOW.plusSeconds(60)).orElseThrow();
        assertEquals("1.6.0", updated.version());
        assertEquals("b".repeat(64), updated.sha256());
        assertEquals(enrolled.enrolledAt(), updated.enrolledAt());
        assertEquals(NOW.plusSeconds(60), updated.updatedAt());
    }

    @Test
    @DisplayName("an unenrol removes only the named client")
    void unenrol() {
        store.enrol(enrolment("claude", "1.5.0"));
        store.enrol(enrolment("opencode", "1.0.0"));
        assertTrue(store.unenrol("claude"));
        assertFalse(store.unenrol("claude"));
        assertEquals(List.of("opencode"), store.all().stream().map(HarnessEnrolment::clientId).toList());
    }

    private static HarnessEnrolment enrolment(String clientId, String version) {
        return new HarnessEnrolment(clientId, Path.of("/usr/local/bin/" + clientId).toAbsolutePath(), "a".repeat(64),
                version, NOW, NOW, new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED,
                        Map.of("input", "reported")), Map.of("text", "reported"), Map.of());
    }
}
