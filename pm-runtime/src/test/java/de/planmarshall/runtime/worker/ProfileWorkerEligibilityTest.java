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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.profile.VerificationRecord;

/**
 * The verification-record eligibility gate (PM-CLIENT-5, Runtime check on load; PM-SEC-8, Consent Before Acting): the
 * pure predicate the supervisor consults before a start, over the failing {@code faults} stage, the {@code not_run}
 * isolation stage, a record written on another platform, the shipped profile, and the two no-claim controls.
 */
@DisplayName("The verification-record eligibility gate")
class ProfileWorkerEligibilityTest {

    private static final String CLIENT = "acme-cli";

    private static final String DIGEST = "sha256:" + "0".repeat(64);

    private static final RuntimePlatform THIS_PLATFORM = new RuntimePlatform("linux", "x86_64");

    @Test
    @DisplayName("a failed faults stage leaves an eligible harness ineligible")
    void aFailedFaultsStageRefuses() {
        var profile = profile(true, false, fixture("verification-record-faults-failed.json"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.STAGE_FAILED, refusal.reason(),
                        "the failed stage is the reason"),
                () -> assertEquals("faults", refusal.stage(), "the refusal names the stage that failed"),
                () -> assertEquals(CLIENT, refusal.clientId(), "the refusal names the profile"));
    }

    @Test
    @DisplayName("a not_run isolation stage leaves a zero-tool harness ineligible")
    void aNotRunIsolationStageRefuses() {
        var profile = profile(true, true, fixture("verification-record-isolation-not-run.json"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.STAGE_NOT_RUN, refusal.reason(),
                        "the stage that did not run is the reason"),
                () -> assertEquals("isolation", refusal.stage(), "the refusal names the isolation stage"));
    }

    @Test
    @DisplayName("a record written on another platform refuses before any stage is read")
    void anotherPlatformRefuses() {
        var profile = profile(true, false, fixture("verification-record-faults-failed.json"));

        var verdict = ProfileWorkerEligibility.of(profile, new RuntimePlatform("darwin", "aarch64")).verdict();

        var refusal = assertInstanceOf(EligibilityVerdict.Refused.class, verdict).refusal();
        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.OTHER_PLATFORM, refusal.reason(),
                        "the platform decides before the failing stage"),
                () -> assertNull(refusal.stage(), "no single stage decided the refusal"));
    }

    @Test
    @DisplayName("a shipped profile is honoured without a record")
    void aShippedProfileIsEligible() {
        var shipped = loaded(ProfileSetKind.SHIPPED, true, true, null);

        var verdict = ProfileWorkerEligibility.of(shipped, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "the release compiled the shipped profile");
    }

    @Test
    @DisplayName("a profile that claims nothing is honoured without a record")
    void aNoClaimControlIsEligible() {
        var profile = profile(false, false, null);

        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "a profile that makes no claim passes untouched");
    }

    @Test
    @DisplayName("an eligible harness with no zero-tool class is honoured when its record established the claim")
    void theNoZeroToolControlIsEligible() {
        var profile = profile(true, false, record(Map.of("faults", "passed", "egress", "passed")));

        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "the two claimed stages both passed");
    }

    @Test
    @DisplayName("a claiming profile without a record is refused")
    void aRecordAbsentRefuses() {
        var profile = profile(true, false, null);

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.RECORD_ABSENT, refusal.reason()),
                () -> assertNull(refusal.stage(), "no stage decided an absent record"));
    }

    @Test
    @DisplayName("a claimed stage the record never measured is refused")
    void aClaimStageAbsentRefuses() {
        var profile = profile(true, false, record(Map.of("faults", "passed")));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT, refusal.reason()),
                () -> assertEquals("egress", refusal.stage(), "the claim rests on the absent egress stage"));
    }

    @Test
    @DisplayName("a failed stage outside the claim still refuses the profile")
    void anIncidentalFailedStageRefuses() {
        var profile = profile(true, false,
                record(Map.of("faults", "passed", "egress", "passed", "identity", "failed")));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.STAGE_FAILED, refusal.reason()),
                () -> assertEquals("identity", refusal.stage(), "the incidental failure is the reason"));
    }

    @Test
    @DisplayName("the gate admits a start exactly when its verdict is eligible")
    void admitsFollowsTheVerdict() {
        var eligible = ProfileWorkerEligibility.of(
                profile(true, false, record(Map.of("faults", "passed", "egress", "passed"))), THIS_PLATFORM);
        var refused = ProfileWorkerEligibility.of(
                profile(true, false, fixture("verification-record-faults-failed.json")), THIS_PLATFORM);

        assertAll(
                () -> assertTrue(eligible.admits("implementer", CLIENT)),
                () -> assertFalse(refused.admits("implementer", CLIENT)),
                () -> assertEquals(CLIENT, eligible.profile().clientId(), "the gate keeps the profile it guards"));
    }

    private static EligibilityRefusal refusalOf(LoadedProfile profile) {
        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM).verdict();

        return assertInstanceOf(EligibilityVerdict.Refused.class, verdict).refusal();
    }

    private static LoadedProfile profile(boolean eligible, boolean zeroTool, VerificationRecord record) {
        return loaded(ProfileSetKind.MACHINE, eligible, zeroTool, record);
    }

    private static LoadedProfile loaded(ProfileSetKind kind, boolean eligible, boolean zeroTool,
            VerificationRecord record) {
        var bytes = document(eligible, zeroTool);
        return new LoadedProfile(CLIENT, kind, JsonDocument.parse(bytes, "profile"), bytes,
                LoadedProfile.State.VERIFIED, record);
    }

    private static byte[] document(boolean eligible, boolean zeroTool) {
        var classes = zeroTool ? "\"zero_tool\": {}" : "";
        return """
                {
                  "schema_version": 1,
                  "client_id": "%1$s",
                  "worker": {"eligible": %2$s},
                  "headless": {"classes": {%3$s}},
                  "tools": {"ask_user": "ask_user"}
                }
                """.formatted(CLIENT, eligible, classes).getBytes(StandardCharsets.UTF_8);
    }

    private static VerificationRecord fixture(String name) {
        try (InputStream in = ProfileWorkerEligibilityTest.class.getResourceAsStream("/worker/" + name)) {
            if (in == null) {
                throw new IllegalStateException("test resource is absent: " + name);
            }
            return VerificationRecord.read(in.readAllBytes(), name);
        } catch (IOException e) {
            throw new IllegalStateException("test resource could not be read: " + name, e);
        }
    }

    private static VerificationRecord record(Map<String, String> stages) {
        var stageJson = stages.entrySet().stream()
                .map(entry -> "\"%s\": {\"status\": \"%s\"}".formatted(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(", "));
        var json = """
                {
                  "format_version": 1,
                  "client_id": "%1$s",
                  "profile_schema_version": 1,
                  "profile_digest": "%2$s",
                  "verified_at": "2026-10-25T12:00:00Z",
                  "tool": {"version": "0.1.99", "commit": "1a2b3c4"},
                  "harness": {"executable": "claude", "version": "1.5.0", "executable_sha256": "%2$s",
                              "models": ["a-model"]},
                  "platform": {"os": "linux", "arch": "x86_64", "confinement": "landlock"},
                  "stages": {%3$s},
                  "values": [{"pointer": "/worker/eligible", "status": "measured"}]
                }
                """.formatted(CLIENT, DIGEST, stageJson);
        return VerificationRecord.read(json.getBytes(StandardCharsets.UTF_8), "verification.json");
    }
}
