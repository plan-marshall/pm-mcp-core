/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.consult;

import java.util.Objects;

/**
 * The check of a submit's {@code consultation_ids} against the consultations delivered to the task (Model Work § The
 * Submit Refers to the Consultation): once the task received an answer, its submission lists every delivered
 * {@code consultation_id} and no other, else the submit is refused {@code invalid_link_args} with a reason that names
 * the missing or unknown reference, and the worker may submit again.
 *
 * @since 0.1
 */
public sealed interface SubmitValidation permits SubmitValidation.Accepted, SubmitValidation.Refused {

    /**
     * The list matches the consultations delivered to the task.
     *
     * @since 0.1
     */
    record Accepted() implements SubmitValidation {
    }

    /**
     * The list does not match the consultations delivered to the task.
     *
     * @param detail the reason, naming the missing or unknown reference
     * @since 0.1
     */
    record Refused(String detail) implements SubmitValidation {

        /** @throws NullPointerException if the detail is {@code null} */
        public Refused {
            Objects.requireNonNull(detail, "detail");
        }
    }
}
