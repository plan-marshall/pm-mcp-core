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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.core.profile.VerificationRecord;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The verification-record eligibility gate (PM-CLIENT-5, Runtime check on load; PM-SEC-8, Consent Before Acting): the
 * pure predicate the supervisor consults before a start. The five checks the specification asks of a record come first,
 * each with its negative control — the claimed {@code paths} stage, the ten {@code faults} trials, the platform's
 * confinement, the {@code isolation} stage of an enforced tool set, the record's profile digest and the {@code egress}
 * stage recorded {@code unenforced} — and their positive controls, then the earlier behaviours: the failing
 * {@code faults} stage, the {@code not_run} isolation stage, the absent {@code classes} stage, a record written on
 * another platform, the shipped profile and the two no-claim controls.
 * <p>
 * The executable digest is generated; {@code acme-cli}, the stage names, the statuses, the platform pair, the
 * confinement values, the digest member of the record and the JSON document shapes are the wire contract the gate reads,
 * and the display name says so where such a literal selects a control.
 */
@EnableGeneratorController
@DisplayName("The verification-record eligibility gate")
class ProfileWorkerEligibilityTest {

    private static final String CLIENT = "acme-cli";

    private static final RuntimePlatform THIS_PLATFORM = new RuntimePlatform("linux", "x86_64");

    private static final RuntimePlatform DARWIN_PLATFORM = new RuntimePlatform("darwin", "aarch64");

    private static final String CONFINED = "landlock";

    private static final String ENFORCED_WRITER = "\"writer\": {\"tool_set\": \"enforced\"}";

    private static final String ADVISORY_WRITER = "\"writer\": {\"tool_set\": \"advisory\"}";

    private String executableDigest;

    @BeforeEach
    void draw() {
        executableDigest = "sha256:" + RuntimeGenerators.sha256Digests().next();
    }

    @Test
    @DisplayName("a record without the claimed paths stage refuses")
    void aMissingPathsStageRefuses() {
        var profile = machine(recordFor("").faults(10).egress("passed"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT, refusal.reason(),
                        "the record never measured the paths stage"),
                () -> assertEquals("paths", refusal.stage(), "the refusal names the paths stage"));
    }

    @Test
    @DisplayName("a faults stage with nine trials refuses (the specification asks at least ten)")
    void nineTrialsRefuse() {
        var profile = machine(recordFor("").faults(9).conforming());

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.FAULTS_TRIALS_INSUFFICIENT, refusal.reason(),
                        "nine trials are short of the ten the record needs"),
                () -> assertEquals("faults", refusal.stage(), "the refusal names the faults stage"));
    }

    @Test
    @DisplayName("a faults stage without a trial count refuses")
    void aFaultsStageWithoutTrialsRefuses() {
        var profile = machine(recordFor("").faults(null).conforming());

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.FAULTS_TRIALS_INSUFFICIENT, refusal.reason(),
                        "a null count is a refusal, never ten trials"),
                () -> assertEquals("faults", refusal.stage(), "the refusal names the faults stage"));
    }

    @Test
    @DisplayName("a record whose platform confinement is none refuses")
    void noConfinementRefuses() {
        var profile = machine(recordFor("").conforming().confinement("none"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.NO_CONFINEMENT, refusal.reason(),
                        "the closed value none names a platform without confinement"),
                () -> assertNull(refusal.stage(), "the platform decided, not a stage"));
    }

    @Test
    @DisplayName("an enforced tool set without the measured isolation stage refuses")
    void anEnforcedToolSetWithoutIsolationRefuses() {
        var profile = machine(recordFor(ENFORCED_WRITER).conforming().stage("classes", "passed"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT, refusal.reason(),
                        "the isolation stage is the claim the record misses"),
                () -> assertEquals("isolation", refusal.stage(), "the refusal names the isolation stage"));
    }

    @Test
    @DisplayName("a record whose profile digest is another document's refuses")
    void anotherDigestRefuses() {
        var profile = machine(recordFor("").conforming().digestOfAnotherDocument());

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.DIGEST_MISMATCH, refusal.reason(),
                        "the record was verified for another document"),
                () -> assertNull(refusal.stage(), "no stage decided the digest mismatch"));
    }

    @Test
    @DisplayName("an egress stage recorded unenforced refuses while the machine has not accepted it")
    void unenforcedEgressWithoutAcceptanceRefuses() {
        var profile = machine(recordFor("").faults(10).stage("paths", "passed").egressUnenforced());

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.EGRESS_UNENFORCED, refusal.reason(),
                        "the record states unenforced egress the machine refuses"),
                () -> assertEquals("egress", refusal.stage(), "the refusal names the egress stage"));
    }

    @Test
    @DisplayName("a confinement of the other operating system refuses")
    void aForeignConfinementRefuses() {
        var linuxWithSandbox = machine(recordFor("").conforming().confinement("sandbox"));
        var darwinWithLandlock = machine(recordFor("").conforming().os("darwin").arch("aarch64")
                .confinement("landlock"));

        var refusal = refusalOf(linuxWithSandbox);
        var darwinVerdict = ProfileWorkerEligibility.of(darwinWithLandlock, DARWIN_PLATFORM).verdict();
        var darwinRefusal = assertInstanceOf(EligibilityVerdict.Refused.class, darwinVerdict,
                "the darwin verdict is a refusal").refusal();

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.NO_CONFINEMENT, refusal.reason(),
                        "the sandbox is not the confinement linux records"),
                () -> assertEquals(EligibilityRefusal.Reason.NO_CONFINEMENT, darwinRefusal.reason(),
                        "landlock is not the confinement darwin records"));
    }

    @Test
    @DisplayName("a darwin record that names its sandbox confinement is eligible")
    void darwinWithSandboxIsEligible() {
        var profile = machine(recordFor("").conforming().os("darwin").arch("aarch64").confinement("sandbox"));

        var verdict = ProfileWorkerEligibility.of(profile, DARWIN_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "darwin's sandbox is the confinement darwin records");
    }

    @Test
    @DisplayName("an endpoint profile needs no confinement and fails on a foreign digest")
    void endpointProfileSkipsConfinementAndChecksItsDigest() {
        var document = endpointDocument();
        var confined = new RecordBuilder(document);
        confined.confinement("none");
        ProfileStages.ENDPOINT_STAGES.forEach(stage -> confined.stage(stage, "passed"));
        var foreign = new RecordBuilder(document);
        ProfileStages.ENDPOINT_STAGES.forEach(stage -> foreign.stage(stage, "passed"));
        foreign.digestOfAnotherDocument();

        var accepted = machine(confined);
        var refused = ProfileWorkerEligibility.of(machine(foreign), THIS_PLATFORM).verdict();

        assertAll(
                () -> assertTrue(ProfileWorkerEligibility.of(accepted, THIS_PLATFORM).verdict().isEligible(),
                        "an endpoint profile needs no worker confinement"),
                () -> assertEquals(EligibilityRefusal.Reason.DIGEST_MISMATCH,
                        assertInstanceOf(EligibilityVerdict.Refused.class, refused,
                                "the foreign digest is refused").refusal().reason(),
                        "an endpoint profile is checked against its own digest"));
    }

    @Test
    @DisplayName("a record that passes every check and ten trials is eligible")
    void aConformingRecordIsEligible() {
        var profile = machine(recordFor("").conforming());

        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "the five checks hold as the specification asks");
    }

    @Test
    @DisplayName("an accepted unenforced egress record is eligible")
    void acceptedUnenforcedEgressIsEligible() {
        var profile = machine(recordFor("").faults(10).stage("paths", "passed").egressUnenforced());

        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM, true).verdict();

        assertTrue(verdict.isEligible(), "the machine accepted unenforced egress");
    }

    @Test
    @DisplayName("the refusal does not depend on the order of the stages in the record")
    void theRefusalDoesNotDependOnStageOrder() {
        var claimed = machine(recordFor(ENFORCED_WRITER).stage("classes", "passed").stage("isolation", "passed"));
        var claimedReversed = machine(recordFor(ENFORCED_WRITER).stage("classes", "passed")
                .stage("isolation", "passed").reversed());
        var incidental = machine(recordFor("").conforming().stage("identity", "failed").stage("limits", "failed"));
        var incidentalReversed = machine(recordFor("").conforming().stage("identity", "failed")
                .stage("limits", "failed").reversed());

        var refusal = refusalOf(claimed);
        var claimedRefusal = refusalOf(claimedReversed);
        var incidentalRefusal = refusalOf(incidental);
        var reversedRefusal = refusalOf(incidentalReversed);

        assertAll(
                () -> assertEquals("faults", refusal.stage(),
                        "the claim order names the first missing stage"),
                () -> assertEquals(refusal.reason(), claimedRefusal.reason(),
                        "the reversed claim refuses for the same reason"),
                () -> assertEquals(refusal.stage(), claimedRefusal.stage(),
                        "the reversed claim names the same stage"),
                () -> assertEquals(incidentalRefusal.reason(), reversedRefusal.reason(),
                        "the reversed record refuses for the same reason"),
                () -> assertEquals(incidentalRefusal.stage(), reversedRefusal.stage(),
                        "the reversed record names the same stage"));
    }

    @Test
    @DisplayName("a failed faults stage leaves an eligible harness ineligible")
    void aFailedFaultsStageRefuses() {
        var profile = withFixture(true, "", "verification-record-faults-failed.json");

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.STAGE_FAILED, refusal.reason(),
                        "the failed stage is the reason"),
                () -> assertEquals("faults", refusal.stage(), "the refusal names the stage that failed"),
                () -> assertEquals(CLIENT, refusal.harnessId(), "the refusal names the profile"));
    }

    @Test
    @DisplayName("a not_run isolation stage leaves a zero-tool harness ineligible")
    void aNotRunIsolationStageRefuses() {
        var profile = withFixture(true, "\"zero_tool\": {}", "verification-record-isolation-not-run.json");

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.STAGE_NOT_RUN, refusal.reason(),
                        "the stage that did not run is the reason"),
                () -> assertEquals("isolation", refusal.stage(), "the refusal names the isolation stage"));
    }

    @Test
    @DisplayName("a record written on another platform refuses before any stage is read")
    void anotherPlatformRefuses() {
        var profile = withFixture(true, "", "verification-record-faults-failed.json");

        var verdict = ProfileWorkerEligibility.of(profile, new RuntimePlatform("darwin", "aarch64")).verdict();

        var refusal = assertInstanceOf(EligibilityVerdict.Refused.class, verdict,
                "an off-platform verdict is a refusal").refusal();
        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.OTHER_PLATFORM, refusal.reason(),
                        "the platform decides before the failing stage"),
                () -> assertNull(refusal.stage(), "no single stage decided the refusal"));
    }

    @Test
    @DisplayName("a shipped profile is honoured without a record")
    void aShippedProfileIsEligible() {
        var shipped = loaded(ProfileSetKind.SHIPPED, true, "\"zero_tool\": {}", null);

        var verdict = ProfileWorkerEligibility.of(shipped, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "the release compiled the shipped profile");
    }

    @Test
    @DisplayName("a profile that claims nothing is honoured without a record")
    void aNoClaimControlIsEligible() {
        var profile = loaded(ProfileSetKind.MACHINE, false, "", null);

        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "a profile that makes no claim passes untouched");
    }

    @Test
    @DisplayName("an enforced tool set is admitted only against the measured classes and isolation stages")
    void anEnforcedToolSetNeedsTheClassesStage() {
        var measured = machine(recordFor(ENFORCED_WRITER).conforming().stage("classes", "passed")
                .stage("isolation", "passed"));
        var unmeasured = machine(recordFor(ENFORCED_WRITER).conforming());

        var refusal = refusalOf(unmeasured);

        assertAll(
                () -> assertTrue(ProfileWorkerEligibility.of(measured, THIS_PLATFORM).verdict().isEligible(),
                        "the measured classes and isolation stages establish it"),
                () -> assertEquals(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT, refusal.reason(),
                        "an enforced tool set is a claim the record must measure"),
                () -> assertEquals("classes", refusal.stage(), "the absent classes stage refuses the claim"));
    }

    @Test
    @DisplayName("an advisory tool set makes no claim and needs no classes stage")
    void anAdvisoryToolSetMakesNoClaim() {
        var advisory = machine(recordFor(ADVISORY_WRITER).conforming());

        var verdict = ProfileWorkerEligibility.of(advisory, THIS_PLATFORM).verdict();

        assertTrue(verdict.isEligible(), "an advisory tool set claims nothing about enforcement");
    }

    @Test
    @DisplayName("a claiming profile without a record is refused")
    void aRecordAbsentRefuses() {
        var profile = loaded(ProfileSetKind.MACHINE, true, "", null);

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.RECORD_ABSENT, refusal.reason(),
                        "an absent record is the reason"),
                () -> assertNull(refusal.stage(), "no stage decided an absent record"));
    }

    @Test
    @DisplayName("a claimed stage the record never measured is refused")
    void aClaimStageAbsentRefuses() {
        var profile = machine(recordFor("").faults(10).stage("paths", "passed"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT,
                        refusal.reason(), "the unmeasured claim stage is the reason"),
                () -> assertEquals("egress", refusal.stage(), "the claim rests on the absent egress stage"));
    }

    @Test
    @DisplayName("a failed stage outside the claim still refuses the profile")
    void anIncidentalFailedStageRefuses() {
        var profile = machine(recordFor("").conforming().stage("identity", "failed"));

        var refusal = refusalOf(profile);

        assertAll(
                () -> assertEquals(EligibilityRefusal.Reason.STAGE_FAILED,
                        refusal.reason(), "the failing stage refuses the profile"),
                () -> assertEquals("identity", refusal.stage(), "the incidental failure is the reason"));
    }

    @Test
    @DisplayName("the gate admits a start exactly when its verdict is eligible")
    void admitsFollowsTheVerdict() {
        var eligible = ProfileWorkerEligibility.of(machine(recordFor("").conforming()), THIS_PLATFORM);
        var refused = ProfileWorkerEligibility.of(
                withFixture(true, "", "verification-record-faults-failed.json"), THIS_PLATFORM);

        assertAll(
                () -> assertTrue(eligible.admits("implementer", CLIENT), "an eligible verdict admits the start"),
                () -> assertFalse(refused.admits("implementer", CLIENT), "a refused verdict blocks the start"),
                () -> assertEquals(CLIENT, eligible.profile().harnessId(), "the gate keeps the profile it guards"));
    }

    private static EligibilityRefusal refusalOf(LoadedProfile profile) {
        var verdict = ProfileWorkerEligibility.of(profile, THIS_PLATFORM).verdict();

        return assertInstanceOf(EligibilityVerdict.Refused.class, verdict, "the verdict is a refusal").refusal();
    }

    private static LoadedProfile machine(RecordBuilder builder) {
        return loaded(ProfileSetKind.MACHINE, builder.document, builder.build());
    }

    private RecordBuilder recordFor(String classes) {
        return new RecordBuilder(document(true, classes));
    }

    private static LoadedProfile withFixture(boolean eligible, String classes, String name) {
        var document = document(eligible, classes);
        return loaded(ProfileSetKind.MACHINE, document, fixture(name, document));
    }

    private static LoadedProfile loaded(ProfileSetKind kind, boolean eligible, String classes,
            VerificationRecord verificationRecord) {
        return loaded(kind, document(eligible, classes), verificationRecord);
    }

    private static LoadedProfile loaded(ProfileSetKind kind, byte[] document, VerificationRecord verificationRecord) {
        return new LoadedProfile(CLIENT, kind, JsonDocument.parse(document, "profile"), document,
                LoadedProfile.State.VERIFIED, verificationRecord);
    }

    private static byte[] document(boolean eligible, String classes) {
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

    private static byte[] endpointDocument() {
        // the contract is the validated endpoint profile document of the profile sets (Client Profiles § Endpoint
        // Profile Schema): a loopback endpoint, its identity and its model tiers
        return """
                {
                  "profile_kind": "endpoint",
                  "schema_version": 1,
                  "harness_id": "%1$s",
                  "display_name": "Local runner",
                  "min_daemon_version": "0.1.0",
                  "icon": null,
                  "executable": "ollama",
                  "identity": {"version_probe": ["--version"], "version_pattern": "version ([0-9.]+)"},
                  "update_evidence": {"method": "none", "signer": null},
                  "endpoint": {
                    "protocol": "openai_chat",
                    "base_url": "http://127.0.0.1:11434",
                    "readiness_path": "/api/tags",
                    "models_path": "/api/tags",
                    "models_pointer": "/models",
                    "result_schema": "enforced",
                    "context_tokens": 8192,
                    "answer_reserve_tokens": 1024,
                    "request_timeout_seconds": 60
                  },
                  "model_tiers": {"frontier": "big", "standard": "mid", "fast": "small"},
                  "weights_probe": {"source": "models_listing", "pointer": "/digest", "argv": null, "pattern": null}
                }
                """.formatted(CLIENT).getBytes(StandardCharsets.UTF_8);
    }

    private static String documentDigest(byte[] document) {
        return CanonicalJson.digest(JsonDocument.parse(document, "profile"));
    }

    /**
     * A fixture record whose digest is bound to the document under test: the two control records are the stage shapes,
     * and the digest of the generated document is the one the gate compares with.
     */
    private static VerificationRecord fixture(String name, byte[] document) {
        try (InputStream in = ProfileWorkerEligibilityTest.class.getResourceAsStream("/worker/" + name)) {
            if (in == null) {
                throw new IllegalStateException("test resource is absent: " + name);
            }
            var text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            var member = "\"profile_digest\": \"";
            var start = text.indexOf(member) + member.length();
            var bound = text.substring(0, start) + documentDigest(document)
                    + text.substring(text.indexOf('"', start));
            return VerificationRecord.read(bound.getBytes(StandardCharsets.UTF_8), name);
        } catch (IOException e) {
            throw new IllegalStateException("test resource could not be read: " + name, e);
        }
    }

    /** The record under construction: conforming for the document under test, with the knobs the controls need. */
    private final class RecordBuilder {

        private final byte[] document;
        private final Map<String, String> stages = new LinkedHashMap<>();
        private String os = "linux";
        private String arch = "x86_64";
        private String confinement = CONFINED;
        private @Nullable String profileDigest;
        private @Nullable Integer faultsTrials = 10;
        private Map.@Nullable Entry<String, String> egressResults;

        private RecordBuilder(byte[] document) {
            this.document = document;
        }

        private RecordBuilder faults(@Nullable Integer trials) {
            faultsTrials = trials;
            return stage("faults", "passed");
        }

        private RecordBuilder egress(String status) {
            return stage("egress", status);
        }

        private RecordBuilder stage(String name, String status) {
            stages.put(name, status);
            return this;
        }

        private RecordBuilder egressUnenforced() {
            egressResults = Map.entry("enforcement", "unenforced");
            return stage("egress", "passed");
        }

        /** Everything the specification asks of an eligible harness's record; a trial count already chosen is kept. */
        private RecordBuilder conforming() {
            if (!stages.containsKey("faults")) {
                faults(10);
            }
            return stage("paths", "passed").stage("egress", "passed");
        }

        private RecordBuilder confinement(String value) {
            confinement = value;
            return this;
        }

        private RecordBuilder os(String value) {
            os = value;
            return this;
        }

        private RecordBuilder arch(String value) {
            arch = value;
            return this;
        }

        private RecordBuilder digestOfAnotherDocument() {
            profileDigest = "sha256:" + RuntimeGenerators.sha256Digests().next();
            return this;
        }

        /** The same stages, laid into the record in the reverse order, for the order-independence control. */
        private RecordBuilder reversed() {
            var names = new ArrayList<>(stages.keySet());
            Collections.reverse(names);
            var ordered = new LinkedHashMap<String, String>();
            names.forEach(name -> ordered.put(name, stages.get(name)));
            stages.clear();
            stages.putAll(ordered);
            return this;
        }

        private VerificationRecord build() {
            var digest = profileDigest == null ? documentDigest(document) : profileDigest;
            var json = """
                    {
                      "format_version": 1,
                      "client_id": "%1$s",
                      "profile_schema_version": 1,
                      "profile_digest": "%2$s",
                      "verified_at": "2026-10-25T12:00:00Z",
                      "tool": {"version": "0.1.99", "commit": "1a2b3c4"},
                      "harness": {"executable": "claude", "version": "1.5.0", "executable_sha256": "%3$s",
                                  "models": ["a-model"]},
                      "platform": {"os": "%4$s", "arch": "%5$s", "confinement": "%6$s"},
                      "stages": {%7$s},
                      "values": [{"pointer": "/worker/eligible", "status": "measured"}]
                    }
                    """.formatted(CLIENT, digest, executableDigest, os, arch, confinement, stageJson());
            return VerificationRecord.read(json.getBytes(StandardCharsets.UTF_8), "verification.json");
        }

        private String stageJson() {
            return stages.entrySet().stream().map(this::stageJson).collect(Collectors.joining(", "));
        }

        private String stageJson(Map.Entry<String, String> stage) {
            var trials = "faults".equals(stage.getKey()) && faultsTrials != null
                    ? ", \"trials\": " + faultsTrials
                    : "";
            var results = "egress".equals(stage.getKey()) && egressResults != null
                    ? ", \"results\": {\"%s\": \"%s\"}".formatted(egressResults.getKey(), egressResults.getValue())
                    : "";
            return "\"%s\": {\"status\": \"%s\"%s%s}".formatted(stage.getKey(), stage.getValue(), trials, results);
        }
    }
}
