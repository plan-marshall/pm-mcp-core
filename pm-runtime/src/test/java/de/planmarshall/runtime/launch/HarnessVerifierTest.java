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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The launch-time verification of an enrolled harness, its self-update and its refusals.
 *
 * <p>
 * The instants, the HMAC key, the stale digests, the versions inside and outside the fixture profile's supported
 * range, the operator-added client id, the endpoint origin and credential key and the probed texts are generated. The
 * literals that stay are the contract: the fixture profile's client id {@code claude} and its supported range
 * {@code >=1.0.0 <2.0.0}, which the versions are drawn against, the version pattern under test, the store's file name
 * {@code harnesses.json}, the shell script shape of the fixture executable, and the usage figure and event names with
 * their stage result {@code reported}.
 */
@EnableGeneratorController
@DisplayName("Harness verifier")
class HarnessVerifierTest {

    private static final String USER = System.getProperty("user.name");
    private static final String CLIENT = "claude";
    private static final String VERSION_PATTERN = "(\\d+\\.\\d+\\.\\d+)";

    @TempDir
    Path base;

    private final List<AuditEvent> audit = new ArrayList<>();
    private final AtomicInteger probes = new AtomicInteger();
    private final AtomicBoolean evidence = new AtomicBoolean();
    private Path executable;
    private HarnessEnrolmentStore store;
    private Instant now;
    private String version;

    @BeforeEach
    void setUp() throws IOException {
        now = LaunchFixtures.now();
        version = supportedVersion();
        SecretKey key = new SecretKeySpec(Generators.letterStrings(32, 32).next().getBytes(StandardCharsets.UTF_8),
                EnrolmentKey.ALGORITHM);
        store = new HarnessEnrolmentStore(base, () -> key, new FileLockManager(event -> {
        }), USER);
        executable = base.resolve("bin").resolve(CLIENT);
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, script(version), StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true);
    }

    @Test
    @DisplayName("a harness that is not enrolled refuses the launch")
    void notEnrolled() {
        var verification = verifier().verify(CLIENT, LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_NOT_ENROLLED, refusal(verification),
                "a harness not enrolled refuses the launch");
    }

    @Test
    @DisplayName("an endpoint-form entry of the client refuses the launch as not enrolled")
    void endpointEntry() {
        store.enrol(HarnessEnrolment.endpoint(CLIENT, "https://" + lowerLetters() + ".example.test", lowerLetters(),
                now, new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of())));

        var verification = verifier().verify(CLIENT, LaunchFixtures.block());

        assertEquals(LaunchRefusal.HARNESS_NOT_ENROLLED, refusal(verification),
                "an endpoint entry is no enrolled harness");
        assertTrue(audit.stream().noneMatch(event -> AuditEvent.HARNESS_UPDATED.equals(event.event())),
                "an endpoint entry audits no harness update");
    }

    @Test
    @DisplayName("a tampered store refuses the launch and is audited")
    void tampered() throws Exception {
        store.enrol(enrolment(digest(), version));
        Files.writeString(base.resolve("harnesses.json"), "{}", StandardCharsets.UTF_8);
        var verification = verifier().verify(CLIENT, LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_TAMPERED, refusal(verification), "a tampered store refuses the launch");
        assertTrue(audit.stream().anyMatch(event -> AuditEvent.ENROLMENT_TAMPERED.equals(event.event())),
                "the tampered store is audited");
    }

    @Test
    @DisplayName("an unchanged executable is accepted without a probe")
    void unchanged() {
        store.enrol(enrolment(digest(), version));
        var verification = verifier().verify(CLIENT, LaunchFixtures.block());
        assertInstanceOf(HarnessVerification.Accepted.class, verification, "an unchanged executable is accepted");
        assertEquals(0, probes.get(), "an unchanged executable is not probed");
    }

    @Test
    @DisplayName("a changed executable without update evidence refuses before running the probe")
    void changedWithoutEvidence() {
        store.enrol(enrolment(RuntimeGenerators.sha256Digests().next(), version));
        evidence.set(false);
        var verification = verifier().verify(CLIENT, LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_UPDATE_UNVERIFIED, refusal(verification),
                "a change without evidence refuses the launch");
        assertEquals(0, probes.get(), "the probe is not run without evidence");
    }

    @Test
    @DisplayName("a changed executable at an unsupported version refuses the launch")
    void unsupportedVersion() {
        store.enrol(enrolment(RuntimeGenerators.sha256Digests().next(), version));
        evidence.set(true);
        var unsupported = Generators.integers(2, 9).next() + "." + Generators.integers(0, 20).next() + "."
                + Generators.integers(0, 20).next();
        var verification = verifier(CLIENT + " " + unsupported).verify(CLIENT, LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_VERSION_UNSUPPORTED, refusal(verification),
                "an unsupported version refuses the launch");
        assertEquals(1, probes.get(), "the changed executable was probed once");
    }

    @Test
    @DisplayName("an accepted self-update rewrites the store and is audited")
    void acceptedSelfUpdate() {
        var stale = RuntimeGenerators.sha256Digests().next();
        var old = version + "-" + lowerLetters();
        store.enrol(enrolment(stale, old));
        evidence.set(true);
        var verification = verifier().verify(CLIENT, LaunchFixtures.block());
        var accepted = assertInstanceOf(HarnessVerification.Accepted.class, verification,
                "a verified self-update is accepted");
        assertEquals(version, accepted.enrolment().version(), "the accepted entry has the probed version");
        assertEquals(digest(), accepted.enrolment().sha256(), "the accepted entry has the new digest");
        assertEquals(version, store.find(CLIENT).orElseThrow().version(), "the store holds the probed version");
        var event = audit.stream().filter(entry -> AuditEvent.HARNESS_UPDATED.equals(entry.event())).findFirst()
                .orElseThrow();
        assertEquals(old, event.details().get(AuditEvent.DETAIL_OLD_VERSION), "the audit names the old version");
        assertEquals(version, event.details().get(AuditEvent.DETAIL_NEW_VERSION), "the audit names the new version");
        assertNotEquals(event.details().get(AuditEvent.DETAIL_NEW_SHA256), stale,
                "the audit names a digest other than the stale");
    }

    @Test
    @DisplayName("a harness the operator added from a verified profile is enrolled and accepted like a shipped one")
    void operatorAddedHarness() {
        var client = "c" + lowerLetters() + "-cli";
        var added = base.resolve("bin").resolve(client);
        try {
            Files.writeString(added, script(version), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        var digest = HarnessVerifier.digestExecutable(added).orElseThrow();
        store.enrol(new HarnessEnrolment(client, added, digest, version, now, now,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of("input", "reported")),
                Map.of("text", "reported"), Map.of()));
        var verification = verifier().verify(client, HeadlessBlock.of(LaunchFixtures.profileForClient(client)));
        var accepted = assertInstanceOf(HarnessVerification.Accepted.class, verification,
                "an operator-added harness is accepted");
        assertEquals(client, accepted.enrolment().clientId(), "the accepted entry is the operator's client");
    }

    @Test
    @DisplayName("a changed executable that changes again during the probe is refused")
    void recheckRefusesASecondChange() {
        store.enrol(enrolment(RuntimeGenerators.sha256Digests().next(), version));
        evidence.set(true);
        var second = version + "." + Generators.integers(0, 99).next();
        var verifier = new HarnessVerifier(store, HarnessVerifier.executableDigest(), (file, argv, pattern) -> {
            try {
                Files.writeString(file, script(second), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return Optional.of(version);
        }, (declared, file) -> evidence.get(), audit::add, InstantSource.fixed(now));
        var verification = verifier.verify(CLIENT, LaunchFixtures.block());
        assertEquals(LaunchRefusal.HARNESS_UPDATE_UNVERIFIED, refusal(verification),
                "a second change during the probe is refused");
    }

    @Test
    @DisplayName("the version matcher reports an absent pattern or no match as empty")
    void versionMatching() {
        var probed = supportedVersion();
        assertTrue(HarnessVerifier.matchVersion(CLIENT + " " + probed, null).isEmpty(),
                "an absent pattern matches no version");
        assertTrue(HarnessVerifier.matchVersion(lowerLetters() + " " + lowerLetters(), VERSION_PATTERN).isEmpty(),
                "a text without a version matches nothing");
        assertEquals(probed, HarnessVerifier.matchVersion("v" + probed, VERSION_PATTERN).orElseThrow(),
                "the pattern's group is the matched version");
    }

    private HarnessVerifier verifier() {
        return verifier(CLIENT + " " + version);
    }

    private HarnessVerifier verifier(String output) {
        return new HarnessVerifier(store, HarnessVerifier.executableDigest(), (file, argv, pattern) -> {
            probes.incrementAndGet();
            return HarnessVerifier.matchVersion(output, pattern);
        }, (declared, file) -> evidence.get(), audit::add, InstantSource.fixed(now));
    }

    private HarnessEnrolment enrolment(String sha256, String enrolledVersion) {
        return new HarnessEnrolment(CLIENT, executable, sha256, enrolledVersion, now, now,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of("input", "reported")),
                Map.of("text", "reported"), Map.of());
    }

    private String digest() {
        return HarnessVerifier.digestExecutable(executable).orElseThrow();
    }

    private static LaunchRefusal refusal(HarnessVerification verification) {
        return assertInstanceOf(HarnessVerification.Refused.class, verification, "the verification is a refusal")
                .reason();
    }

    /** @return a version inside the fixture profile's supported range {@code >=1.0.0 <2.0.0} */
    private static String supportedVersion() {
        return "1." + Generators.integers(0, 20).next() + "." + Generators.integers(0, 20).next();
    }

    private static String script(String printed) {
        return "#!/bin/sh\necho " + printed + "\n";
    }

    private static String lowerLetters() {
        return Generators.letterStrings(2, 10).next().toLowerCase(Locale.ROOT);
    }
}
