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

import java.util.Objects;

/**
 * What a grading run measured: the record the grading wrote, the operator's decline, or the reason a grading was
 * refused before any item was offered (Model Work § Grading; § Judge Instrument Check).
 *
 * @since 0.1
 */
public sealed interface GradingOutcome {

    /**
     * A grading that ran and wrote its record.
     *
     * @param record the measurements it wrote
     * @since 0.1
     */
    record Graded(QualificationRecord record) implements GradingOutcome {

        /** @throws NullPointerException if the record is {@code null} */
        public Graded {
            Objects.requireNonNull(record, "record");
        }
    }

    /**
     * A grading the operator declined; it stays owed.
     *
     * @param owed always {@code true}: a declined grading is recorded as owed
     * @since 0.1
     */
    record Declined(boolean owed) implements GradingOutcome {
    }

    /**
     * A grading refused before any item was offered.
     *
     * @param reason the closed reason code
     * @param detail what was found
     * @since 0.1
     */
    record Refused(String reason, String detail) implements GradingOutcome {

        /** The corpus import failed: no role is qualified on it. */
        public static final String CORPUS_IMPORT_FAILED = "corpus_import_failed";

        /** The judge has no valid check record that passes, or its check ended without a verdict. */
        public static final String JUDGE_UNCHECKED = "judge_unchecked";

        /** The judge's model is the graded model or its sibling at the default configuration. */
        public static final String SCORER_NOT_INDEPENDENT = "scorer_not_independent";

        /** The role has no corpus topic, so it cannot qualify. */
        public static final String NO_TOPICS = "no_topics";

        /** No counted item of the role's topics could be graded. */
        public static final String NO_GRADED_ITEMS = "no_graded_items";

        /** @throws NullPointerException if a component is {@code null} */
        public Refused {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }
}
