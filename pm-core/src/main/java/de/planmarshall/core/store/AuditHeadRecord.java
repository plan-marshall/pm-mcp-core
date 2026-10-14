/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import java.util.Objects;
import java.util.regex.Pattern;
import lombok.With;

/**
 * The head of the audit log {@code logs/audit.head} (PM-SEC-6): the state of the current log after the last append,
 * anchored by an HMAC.
 * <p>
 * The HMAC-SHA-256 covers the canonical JSON of every other field ({@link AuditCodec#signedContent(AuditHeadRecord)});
 * it is keyed by the runtime, which writes the head under the audit lock after every append. A head therefore
 * anchors the number of records, the last record and the last line of the audit index: a log cut back, reset or
 * replaced no longer matches it.
 *
 * @param file          the current log file, always {@value #FILE}
 * @param recordCount   the number of records in the current log
 * @param lastEventId   the id of the last record; {@code null} for an empty log
 * @param headHash      the SHA-256 of the last canonical line; {@link AuditCodec#GENESIS_HASH} for an empty log
 * @param indexHeadHash the SHA-256 of the last line of the audit index; {@link AuditCodec#GENESIS_HASH} before the
 *                      first rotation
 * @param hmac          the HMAC-SHA-256 over the other fields, in lower-case hex
 * @since 0.1
 */
@With
public record AuditHeadRecord(String file, long recordCount, String lastEventId, String headHash,
String indexHeadHash, String hmac) {

    /** The format version of a head that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The name of the current log file, the one value of {@code file}. */
    public static final String FILE = "audit.jsonl";

    /** The form of a UUID in lower case. */
    static final Pattern UUID_FORM = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    /**
     * @throws NullPointerException     if a component other than the last event id is {@code null}
     * @throws IllegalArgumentException if the file is not {@value #FILE}, the record count is negative, the last event
     *                                  id is no UUID, a hash breaks its form, or the last event id is absent for a
     *                                  log with records or present for an empty one
     */
    public AuditHeadRecord {
        if (!FILE.equals(Objects.requireNonNull(file, "file"))) {
            throw new IllegalArgumentException("'file' of an audit head must be '%s': %s".formatted(FILE, file));
        }
        if (recordCount < 0) {
            throw new IllegalArgumentException("'record_count' must not be negative: " + recordCount);
        }
        if (lastEventId != null) {
            AuditRecord.requireForm(UUID_FORM, lastEventId, "last_event_id");
        }
        if ((lastEventId == null) != (recordCount == 0)) {
            throw new IllegalArgumentException(
                    "'last_event_id' is null exactly for an empty log: %d records, last event id %s"
                            .formatted(recordCount, lastEventId));
        }
        AuditRecord.requireForm(AuditRecord.HASH, Objects.requireNonNull(headHash, "headHash"), "head_hash");
        AuditRecord.requireForm(AuditRecord.HASH, Objects.requireNonNull(indexHeadHash, "indexHeadHash"),
                "index_head_hash");
        AuditRecord.requireForm(AuditRecord.HASH, Objects.requireNonNull(hmac, "hmac"), "hmac");
    }
}
