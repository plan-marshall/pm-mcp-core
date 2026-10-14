/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.log;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Log messages of the module {@code pm-core}.
 * <p>
 * Identifier ranges: INFO 001-099, WARN 100-199, ERROR 200-299. A new message takes the next free identifier of
 * its level.
 *
 * @since 0.1
 */
@UtilityClass
public final class CoreLogMessages {

    /** Prefix of all log messages of {@code pm-core}. */
    public static final String PREFIX = "PM_CORE";

    /** INFO level messages. */
    @UtilityClass
    public static final class INFO {

        /**
         * Logged when the recovery of the enrolment pair completes a write that was stopped after the enrolment file
         * and its sidecar were staged completely: the new pair is in place.
         */
        public static final LogRecord ENROLMENT_PAIR_COMPLETED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("Stopped write of the enrolment of project '%s' completed by the recovery: transaction '%s'")
                .build();
    }

    /** WARN level messages. */
    @UtilityClass
    public static final class WARN {

        /**
         * Logged when the audit sink refused the record of a completed store mutation; the record stays owed in its
         * durable intent and is handed over again by the next recovery or repair. It covers an enrolment write as well,
         * whose record stays owed in the intent {@code <project_id>.json.audit-intent} and is handed over by the next
         * enrolment operation of the project or the recovery sweep.
         */
        public static final LogRecord AUDIT_RECORD_DEFERRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Audit record '%s' of transaction '%s' not handed over, kept owed in '%s': %s")
                .build();

        /**
         * Logged when the recovery of the enrolment pair discards a staged write that was stopped before the enrolment
         * file and its sidecar were staged completely, or whose staged sidecar does not match the file; the enrolment
         * is as it was before the write.
         */
        public static final LogRecord ENROLMENT_PAIR_DISCARDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Incomplete staged write of the enrolment of project '%s' discarded, "
                        + "the enrolment is as it was before the write: %s")
                .build();
    }
}
