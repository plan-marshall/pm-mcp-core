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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.core.profile.ProfileValidator;
import de.planmarshall.core.profile.VerificationRecord;
import de.planmarshall.core.role.HarnessProfile;
import de.planmarshall.core.role.RoleClass;

/**
 * The verification-record gate (PM-CLIENT-5, Runtime check on load; PM-SEC-8, Consent Before Acting): the pure
 * predicate the supervisor consults before every start, which decides whether a machine or project profile's claims may
 * be believed on the platform the runtime runs on.
 * <p>
 * No value of a profile is believed because the profile says so. A shipped profile is trusted as today — the release
 * compiled its document — while a machine or project profile makes exactly three claims that the gate examines:
 * {@code worker.eligible: true} is honoured only when the record carries {@code faults} with at least ten trials, the
 * confinement its operating system's verification records — Landlock on Linux, the sandbox profile on macOS —
 * {@code paths} and {@code egress} as {@code passed} (or an {@code egress} recorded {@code unenforced} that the
 * machine has accepted), a non-null {@code zero_tool} class only when {@code classes} and {@code isolation} passed, and
 * an {@code enforced} tool set on {@code writer} or {@code read_only} only when the record measured the {@code classes}
 * and {@code isolation} stages that establish it. The record must be the one written for
 * this document: its {@code profile_digest} is compared with the canonical-JSON digest of the loaded profile. Beyond the
 * claim's own stages, every stage the record carries must be {@code passed}: a record that measured a failure anywhere
 * does not establish the profile's claims. A record written on another operating system or architecture refuses,
 * leaving the profile usable for detection and rendering but its harness ineligible for workers. A profile that makes
 * no claim ({@code eligible: false}, {@code zero_tool: null}, every tool set {@code advisory}) passes untouched.
 * <p>
 * The specification's egress-acceptance half is an input value of the gate: whether the operator accepted unenforced
 * egress is a machine-level decision the egress owner holds, so the gate reads it as a plain boolean whose default is
 * "not accepted" (ADR runtime-worker/015). The gate builds neither the acceptance nor the record of it. A record states
 * unenforced egress in the {@code egress} stage's {@code results} as {@code enforcement: unenforced}; the
 * specification's closed-data sentence names the results of the stages {@code events} and {@code usage} only, and the
 * spelling of the {@code egress} carrier is this gate's contract until the verification runner's plan writes it into
 * the specification.
 * <p>
 * The refusal is a function of the claim alone: the claimed stages are examined in the order the claim names them, and
 * every other recorded stage in its name's order, so a record's own stage order never changes the refusal, and no step
 * reads the host's clock.
 * <p>
 * The class is the {@link SupervisorEnvironment.EligibilityGate} of the supervisor's start path and a value over the
 * profile it guards: {@link #evaluate(LoadedProfile, RuntimePlatform, HarnessProfile)} is the pure predicate, and
 * {@link #of(LoadedProfile, RuntimePlatform)} builds the gate the environment consults.
 *
 * @since 0.1
 */
public final class ProfileWorkerEligibility implements SupervisorEnvironment.EligibilityGate {

    private static final String PASSED = "passed";
    private static final String FAILED = "failed";
    private static final String NOT_RUN = "not_run";

    private static final String FAULTS_STAGE = "faults";
    private static final String PATHS_STAGE = "paths";
    private static final String EGRESS_STAGE = "egress";
    private static final String ISOLATION_STAGE = "isolation";
    private static final String CLASSES_STAGE = "classes";

    /** The trials the stage {@code faults} needs before an eligible claim may rest on it. */
    private static final int MINIMUM_FAULTS_TRIALS = 10;

    /** The confinement each operating system's verification records; any other pair establishes none. */
    private static final Map<String, String> CONFINEMENTS = Map.of("linux", "landlock", "darwin", "sandbox");

    /** The result under which the stage {@code egress} records whether it enforced egress (ADR runtime-worker/015). */
    private static final String ENFORCEMENT_RESULT = "enforcement";
    private static final String ENFORCED = "enforced";

    private static final List<String> ELIGIBLE_STAGES = List.of(FAULTS_STAGE, PATHS_STAGE, EGRESS_STAGE);

    /** The stages a class claim rests on: a {@code zero_tool} class and an enforced tool set alike. */
    private static final List<String> CLASS_CLAIM_STAGES = List.of(CLASSES_STAGE, ISOLATION_STAGE);

    /** The stages an endpoint harness's verification records: its profile carries no worker, tool or session claim. */
    private static final List<String> ENDPOINT_STAGES = ProfileStages.ENDPOINT_STAGES;

    private final LoadedProfile profile;
    private final RuntimePlatform platform;
    private final HarnessProfile facts;
    private final boolean unenforcedEgressAccepted;

    private ProfileWorkerEligibility(LoadedProfile profile, RuntimePlatform platform,
            boolean unenforcedEgressAccepted) {
        this.profile = profile;
        this.platform = platform;
        this.facts = HarnessProfile.of(profile);
        this.unenforcedEgressAccepted = unenforcedEgressAccepted;
    }

    /**
     * @param profile  the effective profile of the harness the supervisor would start a worker on
     * @param platform the platform the runtime runs on
     * @return the gate over the profile and the platform, without an acceptance of unenforced egress
     * @throws NullPointerException if a value is {@code null}
     */
    public static ProfileWorkerEligibility of(LoadedProfile profile, RuntimePlatform platform) {
        return of(profile, platform, false);
    }

    /**
     * @param profile                  the effective profile of the harness the supervisor would start a worker on
     * @param platform                 the platform the runtime runs on
     * @param unenforcedEgressAccepted whether the operator's acceptance of unenforced egress holds for the machine
     * @return the gate over the profile, the platform and the machine's acceptance
     * @throws NullPointerException if a value is {@code null}
     */
    public static ProfileWorkerEligibility of(LoadedProfile profile, RuntimePlatform platform,
            boolean unenforcedEgressAccepted) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(platform, "platform");
        return new ProfileWorkerEligibility(profile, platform, unenforcedEgressAccepted);
    }

    /**
     * @param profile the effective profile of the harness the supervisor would start a worker on
     * @return the gate over the profile and the platform this runtime runs on, without an acceptance of unenforced
     *         egress
     * @throws NullPointerException if the profile is {@code null}
     */
    public static ProfileWorkerEligibility of(LoadedProfile profile) {
        return of(profile, RuntimePlatform.current(), false);
    }

    /**
     * {@inheritDoc}
     * <p>
     * The gate decides from the profile's claims alone: the role and the harness are the caller's names for the worker
     * it wants to start, and the profile the gate holds is already the effective one of that harness. The refusal it
     * finds is available from {@link #verdict()}; the start path reads only {@code admits}, and a report asks the verdict
     * for the reason, the stage and the harness id.
     */
    @Override
    public boolean admits(String role, String harness) {
        return verdict().isEligible();
    }

    /** @return the verdict over the profile's claims */
    public EligibilityVerdict verdict() {
        return evaluate(profile, platform, facts, unenforcedEgressAccepted);
    }

    /** @return the profile the gate guards */
    public LoadedProfile profile() {
        return profile;
    }

    /**
     * The pure predicate: whether the profile's claims are established by its verification record on the platform.
     * <p>
     * A shipped profile is honoured, and so is a machine or project profile that makes no claim. A claimed profile is
     * refused when it has no record, when the record was written on another platform, when the record's digest is not
     * the digest of the profile, when an eligible claim rests on a platform without its operating system's confinement,
     * when a stage its claim rests on is absent from the record, when the {@code faults} stage ran fewer than ten trials,
     * when the {@code egress} stage records unenforced egress the machine has not accepted, or when any recorded stage is
     * not {@code passed}. The stages the claim itself rests on are examined first, so the refusal names the stage that
     * decided the claim where one exists.
     *
     * @param profile  the profile whose claims are examined
     * @param platform the platform the runtime runs on
     * @param facts    the facts projected from the profile
     * @return the verdict
     * @throws NullPointerException if a value is {@code null}
     */
    public static EligibilityVerdict evaluate(LoadedProfile profile, RuntimePlatform platform, HarnessProfile facts) {
        return evaluate(profile, platform, facts, false);
    }

    /**
     * The pure predicate, with the machine's acceptance of unenforced egress as an input value.
     *
     * @param profile                  the profile whose claims are examined
     * @param platform                 the platform the runtime runs on
     * @param facts                    the facts projected from the profile
     * @param unenforcedEgressAccepted whether the operator's acceptance of unenforced egress holds for the machine
     * @return the verdict
     * @throws NullPointerException if a value is {@code null}
     */
    public static EligibilityVerdict evaluate(LoadedProfile profile, RuntimePlatform platform, HarnessProfile facts,
            boolean unenforcedEgressAccepted) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(facts, "facts");
        if (profile.kind() == ProfileSetKind.SHIPPED) {
            return EligibilityVerdict.eligible();
        }
        var endpoint = ProfileValidator.isEndpoint(profile.document());
        var claimedStages = claimedStages(profile, facts);
        if (claimedStages.isEmpty()) {
            return EligibilityVerdict.eligible();
        }
        var verificationRecord = profile.verificationRecord();
        if (verificationRecord == null) {
            return refuse(EligibilityRefusal.Reason.RECORD_ABSENT, null, profile);
        }
        if (!platform.matches(verificationRecord.platform())) {
            return refuse(EligibilityRefusal.Reason.OTHER_PLATFORM, null, profile);
        }
        if (!profile.digest().equals(verificationRecord.profileDigest())) {
            return refuse(EligibilityRefusal.Reason.DIGEST_MISMATCH, null, profile);
        }
        if (!endpoint && facts.eligible() && !confined(verificationRecord.platform())) {
            return refuse(EligibilityRefusal.Reason.NO_CONFINEMENT, null, profile);
        }
        for (var stage : claimedStages) {
            var recorded = verificationRecord.stages().get(stage);
            if (recorded == null) {
                return refuse(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT, stage, profile);
            }
            var refusal = refusalOf(recorded, stage, profile, unenforcedEgressAccepted);
            if (refusal.isPresent()) {
                return EligibilityVerdict.refused(refusal.get());
            }
        }
        var incidentalStages = new TreeSet<>(verificationRecord.stages().keySet());
        incidentalStages.removeAll(claimedStages);
        for (var stage : incidentalStages) {
            var refusal = refusalOf(verificationRecord.stages().get(stage), stage, profile, unenforcedEgressAccepted);
            if (refusal.isPresent()) {
                return EligibilityVerdict.refused(refusal.get());
            }
        }
        return EligibilityVerdict.eligible();
    }

    /**
     * @param facts the facts projected from a profile
     * @return the stages the profile's claims rest on; empty when the profile makes no claim
     */
    private static List<String> claimedStages(LoadedProfile profile, HarnessProfile facts) {
        if (ProfileValidator.isEndpoint(profile.document())) {
            return ENDPOINT_STAGES;
        }
        var stages = new ArrayList<String>();
        if (facts.eligible()) {
            stages.addAll(ELIGIBLE_STAGES);
        }
        if (facts.runs(RoleClass.ZERO_TOOL) || enforcesAToolSet(facts)) {
            CLASS_CLAIM_STAGES.stream().filter(stage -> !stages.contains(stage)).forEach(stages::add);
        }
        return List.copyOf(stages);
    }

    /**
     * @param facts the facts projected from a profile
     * @return whether the profile claims an enforced tool set on a role class that has one
     */
    private static boolean enforcesAToolSet(HarnessProfile facts) {
        return Stream.of(RoleClass.WRITER, RoleClass.READ_ONLY)
                .anyMatch(roleClass -> facts.toolSet(roleClass)
                        .filter(HarnessProfile.ToolSet.ENFORCED::equals).isPresent());
    }

    private static Optional<EligibilityRefusal> refusalOf(VerificationRecord.Stage stage, String name,
            LoadedProfile profile, boolean unenforcedEgressAccepted) {
        return switch (stage.status()) {
            case PASSED -> stageFactRefusal(stage, name, profile, unenforcedEgressAccepted);
            case FAILED -> Optional.of(
                    new EligibilityRefusal(EligibilityRefusal.Reason.STAGE_FAILED, name, profile.harnessId()));
            case NOT_RUN -> Optional.of(
                    new EligibilityRefusal(EligibilityRefusal.Reason.STAGE_NOT_RUN, name, profile.harnessId()));
            default -> throw new IllegalStateException("Not a stage status: " + stage.status());
        };
    }

    /**
     * The fact a {@code passed} stage must still establish: the {@code faults} stage its ten trials, and the
     * {@code egress} stage that it enforced egress or that the machine accepted its {@code unenforced} recording.
     *
     * @param stage                    the recorded stage
     * @param name                     the stage's name
     * @param profile                  the profile under test
     * @param unenforcedEgressAccepted whether the operator's acceptance of unenforced egress holds for the machine
     * @return the refusal when the stage does not establish its fact; empty otherwise
     */
    private static Optional<EligibilityRefusal> stageFactRefusal(VerificationRecord.Stage stage, String name,
            LoadedProfile profile, boolean unenforcedEgressAccepted) {
        if (FAULTS_STAGE.equals(name) && (stage.trials() == null || stage.trials() < MINIMUM_FAULTS_TRIALS)) {
            return Optional.of(new EligibilityRefusal(EligibilityRefusal.Reason.FAULTS_TRIALS_INSUFFICIENT, name,
                    profile.harnessId()));
        }
        if (EGRESS_STAGE.equals(name) && !unenforcedEgressAccepted && unenforced(stage)) {
            return Optional.of(new EligibilityRefusal(EligibilityRefusal.Reason.EGRESS_UNENFORCED, name,
                    profile.harnessId()));
        }
        return Optional.empty();
    }

    /**
     * @param platform the platform a record was written on
     * @return whether the platform names the confinement its operating system's verification records: Landlock on
     *         {@code linux}, the sandbox profile on {@code darwin}. A confinement of the other operating system, the
     *         closed value {@code none} and any value outside the pair establish none.
     */
    private static boolean confined(VerificationRecord.Platform platform) {
        return platform.confinement().equals(CONFINEMENTS.get(platform.os()));
    }

    /**
     * @param stage the stage {@code egress}
     * @return whether the stage records that egress could not be enforced. The record carries that in the stage's
     *         {@code results} under {@code enforcement}; a stage that states an enforcement at all and does not state
     *         {@code enforced} is read as unenforced, the closed side, and a stage that states none is read as the
     *         specification's plain {@code passed}.
     */
    private static boolean unenforced(VerificationRecord.Stage stage) {
        return !ENFORCED.equals(stage.results().getOrDefault(ENFORCEMENT_RESULT, ENFORCED));
    }

    private static EligibilityVerdict refuse(EligibilityRefusal.Reason reason, @Nullable String stage,
            LoadedProfile profile) {
        return EligibilityVerdict.refused(new EligibilityRefusal(reason, stage, profile.harnessId()));
    }
}
