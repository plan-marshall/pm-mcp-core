/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.TransactionId;

/**
 * The {@link SecurityAudit} that writes the {@code agent_started} detection into the machine's audit log as one
 * {@code security} record naming the executable (Model Work § A Worker Off Track).
 *
 * @since 0.1
 */
public final class AuditLogSecurityAudit implements SecurityAudit {

    /** The name of the audit event. */
    public static final String EVENT = "agent_started";

    /** The actor of the record: the supervisor raised the detection. */
    public static final String ACTOR = "supervisor";

    private final AuditLog auditLog;
    private final LockManager lockManager;
    private final Clock clock;
    private final LockKey lockKey;

    /**
     * @param auditLog    the audit log of the machine
     * @param lockManager the lock manager of the runtime
     * @param clock       the clock of the event ids
     * @param base        the base directory of the runtime
     */
    public AuditLogSecurityAudit(AuditLog auditLog, LockManager lockManager, Clock clock, Path base) {
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.lockKey = LockKey.auditLog(base);
    }

    @Override
    public void agentStarted(Event event) {
        Objects.requireNonNull(event, "event");
        var details = new LinkedHashMap<String, Object>();
        details.put("executable", event.executable());
        details.put("generation_id", event.generationId());
        details.put("queue_task_id", event.queueTaskId());
        details.put("attempt", event.attempt());
        var auditRecord = AuditRecord.builder()
                .eventId(TransactionId.generate(clock).value())
                .prevHash(AuditCodec.GENESIS_HASH)
                .timestamp(event.at())
                .category(AuditRecord.Category.SECURITY)
                .event(EVENT)
                .actor(ACTOR)
                .outcome(AuditRecord.Outcome.WARNING)
                .details(Map.copyOf(details))
                .build();
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey);
            auditLog.append(transaction, auditRecord);
        }
    }
}
