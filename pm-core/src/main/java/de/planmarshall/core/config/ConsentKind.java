/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed list of the actions the server asks consent for before it acts (PM-SEC-8, Store Schemas § Consent Before
 * Acting): looking at the machine or a checkout, running a program it found, or spending model tokens on its own
 * initiative.
 * <p>
 * Each kind names how long a {@code yes_remember} lasts. A kind {@link Remembered#MACHINE remembered for the machine}
 * covers every project of the machine and is stored without a project. A kind {@link Remembered#PROJECT remembered for
 * the project} is stored with its project, and {@code run_checkout_scripts} additionally with the digest of every
 * script it covers. A kind {@link Remembered#NEVER never remembered} is asked afresh at every action: a
 * {@code yes_remember} link is not offered for it, and {@link ConsentGate} refuses one.
 * <p>
 * A consent covers its kind and never a wider one, so a remembered {@link #SCAN_HARNESSES} runs no login or model probe.
 *
 * @since 0.1
 */
public enum ConsentKind {

    /** Look for the executables of the profiles' harnesses and run their version probe. */
    SCAN_HARNESSES("scan_harnesses", Remembered.MACHINE),
    /** Download the makers' icons of the found harnesses once, through the runtime. */
    FETCH_ICONS("fetch_icons", Remembered.MACHINE),
    /** Run the login probe of each chosen harness. */
    PROBE_HARNESS_LOGIN("probe_harness_login", Remembered.MACHINE),
    /** Run a harness the binary does not know to verify its draft profile; asked before every verification. */
    VERIFY_HARNESS_PROFILE("verify_harness_profile", Remembered.NEVER),
    /** Run the model probe of each enrolled harness. */
    PROBE_HARNESS_MODELS("probe_harness_models", Remembered.MACHINE),
    /** A task of the steward with its expected tokens. */
    MODEL_ADVICE("model_advice", Remembered.MACHINE),
    /** Write, read, and delete one test entry in the chosen OS credential store. */
    CHECK_CREDENTIAL_STORE("check_credential_store", Remembered.MACHINE),
    /** Look for the providers' CLI tools and the credentials already stored. */
    SCAN_PROVIDERS("scan_providers", Remembered.MACHINE),
    /** Call each chosen provider with its credential and run its CLI tool. */
    VERIFY_PROVIDERS("verify_providers", Remembered.MACHINE),
    /** Every consent of one automatic onboarding run, for that run only. */
    AUTO_RUN("auto_run", Remembered.NEVER),
    /** Read a project's checkout and run the found host tools' version probes. */
    INSPECT_CHECKOUT("inspect_checkout", Remembered.PROJECT),
    /** Run the named scripts of a project's checkout, each with the digest it was granted for. */
    RUN_CHECKOUT_SCRIPTS("run_checkout_scripts", Remembered.PROJECT),
    /** A role task of the project setup whose expected tokens are shown first. */
    MODEL_COST("model_cost", Remembered.PROJECT),
    /** Ask the organisation's registry for a release newer than the installed one. */
    CHECK_UPDATES("check_updates", Remembered.MACHINE);

    /** How long a remembered consent lasts. */
    public enum Remembered {
        /** For the machine: one entry covers every project, and no project is named. */
        MACHINE,
        /** For the project named with the entry; {@code run_checkout_scripts} also names its scripts. */
        PROJECT,
        /** Never: the action is asked afresh every time, and {@code yes_remember} is not offered. */
        NEVER
    }

    private final String wireName;
    private final Remembered remembered;

    ConsentKind(String wireName, Remembered remembered) {
        this.wireName = wireName;
        this.remembered = remembered;
    }

    /** @return the value of {@code kind} in {@code consents.json} */
    public String wireName() {
        return wireName;
    }

    /** @return how long a {@code yes_remember} of the kind lasts */
    public Remembered remembered() {
        return remembered;
    }

    /** @return whether the kind is stored with the project it was granted for */
    public boolean projectScoped() {
        return remembered == Remembered.PROJECT;
    }

    /** @return whether the kind is never remembered, so no {@code yes_remember} link exists for it */
    public boolean rememberable() {
        return remembered != Remembered.NEVER;
    }

    /** @return whether the entry of the kind lists the scripts it covers */
    public boolean scriptScoped() {
        return this == RUN_CHECKOUT_SCRIPTS;
    }

    /**
     * @param wireName a value of {@code kind}
     * @return the kind, or empty for a value outside the closed list
     */
    public static Optional<ConsentKind> of(String wireName) {
        return Arrays.stream(values()).filter(kind -> kind.wireName.equals(wireName)).findFirst();
    }
}
