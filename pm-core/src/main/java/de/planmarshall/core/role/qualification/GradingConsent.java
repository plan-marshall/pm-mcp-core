/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.Arrays;
import java.util.Optional;

/**
 * The operator's answer to the offer to grade a role (Model Work § Grading at Install): a grading starts only behind
 * the operator's consent, with the expected tokens shown. A declined grading is recorded as owed, and its roles stay
 * unqualified.
 *
 * @since 0.1
 */
public enum GradingConsent {

    /** The operator consented to the model cost. */
    GRANTED("granted"),

    /** The operator declined; the grading stays owed. */
    DECLINED("declined");

    private final String wireName;

    GradingConsent(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the answer in a report */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of an answer in a document
     * @return the answer; empty if the name is not one
     */
    public static Optional<GradingConsent> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(consent -> consent.wireName.equals(wireName)).findFirst();
    }
}
