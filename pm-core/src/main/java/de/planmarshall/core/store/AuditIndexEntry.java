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
 * One line of the audit index {@code logs/audit-index.jsonl} (PM-SEC-6): one rotated log file, appended under the
 * audit lock at its rotation.
 * <p>
 * The index is hash-chained like the log: every entry names the SHA-256 of the previous index line, the first entry
 * {@link AuditCodec#GENESIS_HASH}. An entry names the rotated file, its first and last record and the hash of its
 * checkpoint line, the line the first record of the next log chains to; a rotated file that was purged is still
 * accounted for by its entry, which is never removed.
 *
 * @param prevHash       the SHA-256 of the previous index line, in lower-case hex
 * @param file           the rotated file, {@code audit-<first_event_id>.jsonl}
 * @param firstEventId   the id of the first record of the rotated file
 * @param lastEventId    the id of the checkpoint record, the last record of the rotated file
 * @param recordCount    the number of records in the rotated file, the checkpoint included; at least 1
 * @param checkpointHash the SHA-256 of the checkpoint line, in lower-case hex
 * @since 0.1
 */
@With
public record AuditIndexEntry(String prevHash, String file, String firstEventId, String lastEventId, long recordCount,
String checkpointHash) {

    /** The format version of an index entry that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The form of the name of a rotated log file. */
    static final Pattern ROTATED_FILE_FORM = Pattern
            .compile("audit-[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\.jsonl");

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if a hash, the file name or an event id breaks its form, or the record count is
     *                                  below 1
     */
    public AuditIndexEntry {
        AuditRecord.requireForm(AuditRecord.HASH, Objects.requireNonNull(prevHash, "prevHash"), "prev_hash");
        AuditRecord.requireForm(ROTATED_FILE_FORM, Objects.requireNonNull(file, "file"), "file");
        AuditRecord.requireForm(AuditHeadRecord.UUID_FORM, Objects.requireNonNull(firstEventId, "firstEventId"),
                "first_event_id");
        AuditRecord.requireForm(AuditHeadRecord.UUID_FORM, Objects.requireNonNull(lastEventId, "lastEventId"),
                "last_event_id");
        if (recordCount < 1) {
            throw new IllegalArgumentException("'record_count' of an index entry is at least 1: " + recordCount);
        }
        AuditRecord.requireForm(AuditRecord.HASH, Objects.requireNonNull(checkpointHash, "checkpointHash"),
                "checkpoint_hash");
    }

    /**
     * @param firstEventId the id of the first record of a rotated log
     * @return the name the rotated log is renamed to, {@code audit-<first_event_id>.jsonl}
     */
    public static String rotatedFileName(String firstEventId) {
        return "audit-" + Objects.requireNonNull(firstEventId, "firstEventId") + ".jsonl";
    }
}
