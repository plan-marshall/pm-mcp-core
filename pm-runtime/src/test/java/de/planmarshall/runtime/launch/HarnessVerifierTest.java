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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.lock.FileLockManager;

/** The launch-time verification of an enrolled harness, its self-update and its refusals. */
@DisplayName("Harness verifier")
class HarnessVerifierTest {

    private static final String USER = System.getProperty("user.name");
    private static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    @TempDir
    Path base;

    private final List<AuditEvent> audit = new ArrayList<>();
    private final AtomicInteger probes = new AtomicInteger();
    private final AtomicBoolean evidence = new AtomicBoolean();
    private Path executable;
    private HarnessEnrolmentStore store;

    @BeforeEach
    void setUp() throws IOException {
        SecretKey key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8),
                EnrolmentKey.ALGORITHM);
        store = new HarnessEnrolmentStore(base, () -> key, new FileLockManager(event -> {
        }), USER);
        executable = base.resolve("bin").resolve("claude");
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, "#!/bin/sh\necho 1.5.0\n", StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true);
    }

    @Test
    @DisplayName("a harness that is not enrolled refuses the launch")
    void notEnrolled() {
        var verification = verifier().verify("claude", LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_NOT_ENROLLED, refusal(verification));
    }

    @Test
    @DisplayName("a tampered store refuses the launch and is audited")
    void tampered() throws Exception {
        store.enrol(enrolment(digest(), "1.5.0"));
        Files.writeString(base.resolve("harnesses.json"), "{}", StandardCharsets.UTF_8);
        var verification = verifier().verify("claude", LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_TAMPERED, refusal(verification));
        assertTrue(audit.stream().anyMatch(event -> AuditEvent.ENROLMENT_TAMPERED.equals(event.event())));
    }

    @Test
    @DisplayName("an unchanged executable is accepted without a probe")
    void unchanged() {
        store.enrol(enrolment(digest(), "1.5.0"));
        var verification = verifier().verify("claude", LaunchFixtures.block());
        assertInstanceOf(HarnessVerification.Accepted.class, verification);
        assertEquals(0, probes.get());
    }

    @Test
    @DisplayName("a changed executable without update evidence refuses before running the probe")
    void changedWithoutEvidence() {
        store.enrol(enrolment("a".repeat(64), "1.5.0"));
        evidence.set(false);
        var verification = verifier().verify("claude", LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_UPDATE_UNVERIFIED, refusal(verification));
        assertEquals(0, probes.get());
    }

    @Test
    @DisplayName("a changed executable at an unsupported version refuses the launch")
    void unsupportedVersion() {
        store.enrol(enrolment("a".repeat(64), "1.5.0"));
        evidence.set(true);
        var verification = verifier("claude 2.0.0").verify("claude", LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_VERSION_UNSUPPORTED, refusal(verification));
        assertEquals(1, probes.get());
    }

    @Test
    @DisplayName("an accepted self-update rewrites the store and is audited")
    void acceptedSelfUpdate() {
        store.enrol(enrolment("a".repeat(64), "1.4.0"));
        evidence.set(true);
        var verification = verifier().verify("claude", LaunchFixtures.block());
        var accepted = (HarnessVerification.Accepted) verification;
        assertEquals("1.5.0", accepted.enrolment().version());
        assertEquals(digest(), accepted.enrolment().sha256());
        assertEquals("1.5.0", store.find("claude").orElseThrow().version());
        var event = audit.stream().filter(entry -> AuditEvent.HARNESS_UPDATED.equals(entry.event())).findFirst()
                .orElseThrow();
        assertEquals("1.4.0", event.details().get(AuditEvent.DETAIL_OLD_VERSION));
        assertEquals("1.5.0", event.details().get(AuditEvent.DETAIL_NEW_VERSION));
        assertNotEquals(event.details().get(AuditEvent.DETAIL_NEW_SHA256), "a".repeat(64));
    }

    @Test
    @DisplayName("a harness the operator added from a verified profile is enrolled and accepted like a shipped one")
    void operatorAddedHarness() {
        var added = base.resolve("bin").resolve("acme-cli");
        try {
            Files.writeString(added, "#!/bin/sh\necho 1.5.0\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        var digest = HarnessVerifier.digestExecutable(added).orElseThrow();
        store.enrol(new HarnessEnrolment("acme-cli", added, digest, "1.5.0", NOW, NOW,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of("input", "reported")),
                Map.of("text", "reported"), Map.of()));
        var verification = verifier().verify("acme-cli", HeadlessBlock.of(LaunchFixtures.profileForClient("acme-cli")));
        assertInstanceOf(HarnessVerification.Accepted.class, verification);
        assertEquals("acme-cli", ((HarnessVerification.Accepted) verification).enrolment().clientId());
    }

    @Test
    @DisplayName("a changed executable that changes again during the probe is refused")
    void recheckRefusesASecondChange() {
        store.enrol(enrolment("a".repeat(64), "1.5.0"));
        evidence.set(true);
        var verifier = new HarnessVerifier(store, HarnessVerifier.executableDigest(), (file, argv, pattern) -> {
            try {
                Files.writeString(file, "#!/bin/sh\necho 1.6.0\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return Optional.of("1.5.0");
        }, (declared, file) -> evidence.get(), audit::add, InstantSource.fixed(NOW));
        var verification = verifier.verify("claude", LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_UPDATE_UNVERIFIED, refusal(verification));
    }

    @Test
    @DisplayName("the version matcher reports an absent pattern or no match as empty")
    void versionMatching() {
        assertTrue(HarnessVerifier.matchVersion("claude 1.5.0", null).isEmpty());
        assertTrue(HarnessVerifier.matchVersion("no version here", "(\\d+\\.\\d+\\.\\d+)").isEmpty());
        assertEquals("2.3.4", HarnessVerifier.matchVersion("v2.3.4", "(\\d+\\.\\d+\\.\\d+)").orElseThrow());
    }

    private HarnessVerifier verifier() {
        return verifier("claude 1.5.0");
    }

    private HarnessVerifier verifier(String output) {
        return new HarnessVerifier(store, HarnessVerifier.executableDigest(), (file, argv, pattern) -> {
            probes.incrementAndGet();
            return HarnessVerifier.matchVersion(output, pattern);
        }, (declared, file) -> evidence.get(), audit::add, InstantSource.fixed(NOW));
    }

    private HarnessEnrolment enrolment(String sha256, String version) {
        return new HarnessEnrolment("claude", executable, sha256, version, NOW, NOW, new HarnessEnrolment.UsageReport(
                        HarnessEnrolment.UsageSource.DECLARED, Map.of("input", "reported")), Map.of("text", "reported"),
                Map.of());
    }

    private String digest() {
        return HarnessVerifier.digestExecutable(executable).orElseThrow();
    }

    private static LaunchRefusal refusal(HarnessVerification verification) {
        return ((HarnessVerification.Refused) verification).reason();
    }
}
