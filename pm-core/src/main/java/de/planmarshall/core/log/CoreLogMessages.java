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
         * Logged when the recovery of the enrolment pair ({@code EnrolmentStore.recoverPair}, run by step 1 of the
         * project's recovery and by the next write of the project) completes a write that was stopped after the
         * enrolment file and its sidecar were staged completely; names the project and the transaction of the write.
         * The new pair is in place; the owed audit record of the write stays the unconfirmed one.
         * <p>
         * The component is {@code EnrolmentStore}.
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
         * Logged when the audit sink refuses the record of a completed store mutation (a roll-forward, a JSONL tail
         * repair, a reconciliation or an enrolment write); names the event, the transaction id of the record, which
         * names that one record and is the deduplication key of the audit log, not the transaction that owes it, and
         * the durable intent that keeps it owed (the transaction journal, {@code <store>.audit-intent}, or for an
         * enrolment write {@code enrolments/<project_id>.json.audit-intent}). The data outcome is unchanged, and the
         * next recovery or repair, or for an enrolment write the next enrolment operation of the project or
         * {@code Enrolments.replayOwedAudit()}, hands the record over again.
         * <p>
         * The component is {@code OwedAuditEvents}.
         */
        public static final LogRecord AUDIT_RECORD_DEFERRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Audit record '%s' with transaction id '%s' not handed over, kept owed in '%s': %s")
                .build();

        /**
         * Logged when the recovery of the enrolment pair discards the staged files of a write that was stopped before
         * the enrolment file and its sidecar were staged completely, or whose staged sidecar is torn, unreadable or
         * does not match the file it stands for; names the project and the reason. The enrolment is as it was before
         * the write; a staged sidecar whose file was already replaced is deleted and the file left to the check.
         * <p>
         * The component is {@code EnrolmentStore}.
         */
        public static final LogRecord ENROLMENT_PAIR_DISCARDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("Incomplete staged write of the enrolment of project '%s' discarded, "
                        + "the enrolment is as it was before the write: %s")
                .build();

        /**
         * Logged when the import of the corpus sources misses a source: a fetch step did not answer
         * ({@code unreachable}), the pinned commit, a file or an LFS object is absent ({@code pin_missing}), or the
         * manifest, the overlay or the source file does not yield the selected items ({@code malformed}); names the
         * source and the reason with its detail, never a credential. None of the source's items is written and the
         * other sources are imported; a source missed as {@code unreachable} or {@code pin_missing} makes the import's
         * verdict {@code PARTIAL}, one missed as {@code malformed} makes it {@code FAILED}, so no role is qualified on
         * it. A hash mismatch, which also misses the source and fails the import, is logged as {@code PM_CORE-200}
         * instead.
         * <p>
         * The component is {@code CorpusImport}.
         */
        public static final LogRecord CORPUS_SOURCE_MISSED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("Corpus source '%s' missed, its items are left out: %s")
                .build();
    }

    /** ERROR level messages. */
    @UtilityClass
    public static final class ERROR {

        /**
         * Logged when a file of a corpus source, its LFS pointer oid or the LFS object delivered for it does not have
         * the SHA-256 its manifest pins; names the source, the file, the expected hash and what was found. The source
         * is missed ({@code hash_mismatch}): none of its items is written, and the import's verdict is
         * {@code FAILED}, so no role is qualified on it.
         * <p>
         * The component is {@code CorpusImport}.
         */
        public static final LogRecord CORPUS_SOURCE_HASH_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Corpus source '%s' fails its hash: file '%s' expected %s, found %s")
                .build();
    }
}
