/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.io.Serial;
import lombok.Getter;

/**
 * Thrown when a {@link PrimitiveRegistry} refuses a primitive (PM-IMPL-5). The {@link Reason} tells the refusals
 * apart; the message adds the detail for a reader.
 *
 * @since 0.1
 */
public final class PrimitiveRegistrationException extends IllegalArgumentException {

    @Serial
    private static final long serialVersionUID = 1L;

    /** Why a primitive was refused. */
    public enum Reason {
        /** Another primitive passed to the registry has the same id. */
        DUPLICATE_ID,
        /** The id is missing or is not two lower-case identifiers joined by a dot. */
        MALFORMED_ID,
        /** The parameter type is missing or is not a record class. */
        PARAMETER_TYPE_NOT_A_RECORD,
        /**
         * The outcome set is not closed: the outcome type is missing, is not an enum of outcomes, or has no
         * constant.
         */
        OPEN_OUTCOME_SET,
        /** Two outcomes of the primitive have the same wire name. */
        DUPLICATE_WIRE_NAME,
        /** The wire name of an outcome is missing or is not a lower-case identifier. */
        MALFORMED_WIRE_NAME,
        /** An outcome uses a wire name that is reserved for the engine. */
        RESERVED_WIRE_NAME,
        /**
         * The job side effect is not declared consistently: the primitive enqueues a job but has no job-starting
         * outcome, or it has a job-starting outcome but does not declare that it enqueues a job.
         */
        UNDECLARED_JOB_SIDE_EFFECT,
        /**
         * The primitive declares an attempt cap but no exhaustion outcome that names one of its outcomes of class
         * {@link OutcomeClass#FAILURE}.
         */
        ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION,
        /** The primitive declares no awaited event. */
        MISSING_AWAITED_EVENT
    }

    /** The id of the refused primitive as it declared it; the text {@code null} when it declared none. */
    @Getter
    private final String primitiveId;

    /** The reason of the refusal. */
    @Getter
    private final Reason reason;

    /**
     * @param primitiveId the id of the refused primitive as it declared it
     * @param reason      the reason
     * @param message     the detail
     */
    public PrimitiveRegistrationException(String primitiveId, Reason reason, String message) {
        super("%s: primitive '%s' %s".formatted(reason, primitiveId, message));
        this.primitiveId = String.valueOf(primitiveId);
        this.reason = reason;
    }
}
