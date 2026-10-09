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

/**
 * The external event a primitive waits for before its outcome can change (PM-IMPL-5).
 *
 * @since 0.1
 */
public enum AwaitedEvent {
    /** The outcome depends only on state the primitive reads in the same transaction. */
    NONE,
    /** The end of a job. */
    JOB_SETTLED,
    /** The end of a task of a model state, with its closed task outcome. */
    TASK_SETTLED,
    /** A waiver record written by the operator: the waiver wait, an integrity resolution. */
    OPERATOR_WAIVER,
    /** A submission of the model. */
    MODEL_SUBMIT,
    /** The passing of time. */
    TIME,
    /** A re-observation of a provider, a remote, or the checkout. */
    EXTERNAL_STATE,
    /** An answer to a question record, on any channel. */
    QUESTION_ANSWER
}
