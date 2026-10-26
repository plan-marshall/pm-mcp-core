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
import java.util.Objects;
import java.util.stream.Stream;

import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
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
 * {@code worker.eligible: true} is honoured only when the record carries {@code faults} and {@code egress} as
 * {@code passed}, a non-null {@code zero_tool} class only when {@code classes} and {@code isolation} passed, and an
 * {@code enforced} tool set on {@code writer} or {@code read_only} only when the record measured the {@code classes}
 * stage that establishes it. Beyond the claim's own stages, every stage the record carries must be {@code passed}: a
 * record that measured a failure anywhere does not establish the profile's claims. A record written on another operating
 * system or architecture refuses, leaving the profile usable for detection and rendering but its harness ineligible for
 * workers. A profile that makes no claim ({@code eligible: false}, {@code zero_tool: null}, every tool set
 * {@code advisory}) passes untouched.
 * <p>
 * The egress-acceptance half of the specification is deliberately not here: whether the operator accepts unenforced
 * egress is a machine-level decision the egress owner holds, and this gate only requires the record's {@code egress}
 * stage to be {@code passed} (ADR runtime-worker/006).
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

    private static final List<String> ELIGIBLE_STAGES = List.of("faults", "egress");
    private static final List<String> ZERO_TOOL_STAGES = List.of("classes", "isolation");
    private static final String CLASSES_STAGE = "classes";

    private final LoadedProfile profile;
    private final RuntimePlatform platform;
    private final HarnessProfile facts;

    private ProfileWorkerEligibility(LoadedProfile profile, RuntimePlatform platform) {
        this.profile = profile;
        this.platform = platform;
        this.facts = HarnessProfile.of(profile);
    }

    /**
     * @param profile  the effective profile of the harness the supervisor would start a worker on
     * @param platform the platform the runtime runs on
     * @return the gate over the profile and the platform
     * @throws NullPointerException if a value is {@code null}
     */
    public static ProfileWorkerEligibility of(LoadedProfile profile, RuntimePlatform platform) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(platform, "platform");
        return new ProfileWorkerEligibility(profile, platform);
    }

    /**
     * @param profile the effective profile of the harness the supervisor would start a worker on
     * @return the gate over the profile and the platform this runtime runs on
     * @throws NullPointerException if the profile is {@code null}
     */
    public static ProfileWorkerEligibility of(LoadedProfile profile) {
        return of(profile, RuntimePlatform.current());
    }

    /**
     * {@inheritDoc}
     * <p>
     * The gate decides from the profile's claims alone: the role and the harness are the caller's names for the worker
     * it wants to start, and the profile the gate holds is already the effective one of that harness. The refusal it
     * finds is available from {@link #verdict()}, so the caller records the reason rather than a bare boolean.
     */
    @Override
    public boolean admits(String role, String harness) {
        return verdict().isEligible();
    }

    /** @return the verdict over the profile's claims */
    public EligibilityVerdict verdict() {
        return evaluate(profile, platform, facts);
    }

    /** @return the profile the gate guards */
    public LoadedProfile profile() {
        return profile;
    }

    /**
     * The pure predicate: whether the profile's claims are established by its verification record on the platform.
     * <p>
     * A shipped profile is honoured, and so is a machine or project profile that makes no claim. A claimed profile is
     * refused when it has no record, when the record was written on another platform, when a stage its claim rests on
     * is absent from the record, or when any recorded stage is not {@code passed}. The stages the claim itself rests on
     * are examined first, so the refusal names the stage that decided the claim where one exists.
     *
     * @param profile  the profile whose claims are examined
     * @param platform the platform the runtime runs on
     * @param facts    the facts projected from the profile
     * @return the verdict
     * @throws NullPointerException if a value is {@code null}
     */
    public static EligibilityVerdict evaluate(LoadedProfile profile, RuntimePlatform platform, HarnessProfile facts) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(platform, "platform");
        Objects.requireNonNull(facts, "facts");
        if (profile.kind() == ProfileSetKind.SHIPPED) {
            return EligibilityVerdict.eligible();
        }
        var claimedStages = claimedStages(facts);
        if (claimedStages.isEmpty()) {
            return EligibilityVerdict.eligible();
        }
        var record = profile.verificationRecord();
        if (record == null) {
            return refuse(EligibilityRefusal.Reason.RECORD_ABSENT, null, profile);
        }
        if (!platform.matches(record.platform())) {
            return refuse(EligibilityRefusal.Reason.OTHER_PLATFORM, null, profile);
        }
        for (var stage : claimedStages) {
            var recorded = record.stages().get(stage);
            if (recorded == null) {
                return refuse(EligibilityRefusal.Reason.CLAIM_STAGE_ABSENT, stage, profile);
            }
            var refusal = refusalOf(recorded, stage, profile);
            if (refusal != null) {
                return EligibilityVerdict.refused(refusal);
            }
        }
        for (var entry : record.stages().entrySet()) {
            if (claimedStages.contains(entry.getKey())) {
                continue;
            }
            var refusal = refusalOf(entry.getValue(), entry.getKey(), profile);
            if (refusal != null) {
                return EligibilityVerdict.refused(refusal);
            }
        }
        return EligibilityVerdict.eligible();
    }

    /**
     * @param facts the facts projected from a profile
     * @return the stages the profile's claims rest on; empty when the profile makes no claim
     */
    private static List<String> claimedStages(HarnessProfile facts) {
        var stages = new ArrayList<String>();
        if (facts.eligible()) {
            stages.addAll(ELIGIBLE_STAGES);
        }
        if (facts.runs(RoleClass.ZERO_TOOL)) {
            stages.addAll(ZERO_TOOL_STAGES);
        }
        if (enforcesAToolSet(facts) && !stages.contains(CLASSES_STAGE)) {
            stages.add(CLASSES_STAGE);
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

    private static EligibilityRefusal refusalOf(VerificationRecord.Stage stage, String name, LoadedProfile profile) {
        return switch (stage.status()) {
            case PASSED -> null;
            case FAILED -> new EligibilityRefusal(EligibilityRefusal.Reason.STAGE_FAILED, name, profile.clientId());
            case NOT_RUN -> new EligibilityRefusal(EligibilityRefusal.Reason.STAGE_NOT_RUN, name, profile.clientId());
            default -> throw new IllegalStateException("Not a stage status: " + stage.status());
        };
    }

    private static EligibilityVerdict refuse(EligibilityRefusal.Reason reason, String stage, LoadedProfile profile) {
        return EligibilityVerdict.refused(new EligibilityRefusal(reason, stage, profile.clientId()));
    }
}
