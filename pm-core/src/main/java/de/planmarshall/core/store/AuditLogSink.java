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

import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;

/**
 * The production {@link AuditSink} (PM-SEC-6, PM-IMPL-7): every record a component hands over becomes one record of
 * the hash-chained audit log.
 * <p>
 * The sink gives the record what the producer does not know: a fresh {@code event_id} and the {@code timestamp} from
 * the clock of the log, and the OS principal of the runtime as {@code actor}. A record whose details name a scope
 * ({@link AuditEvent#DETAIL_SCOPE_TYPE} with a type of the audit log, and {@link AuditEvent#DETAIL_SCOPE_ID}) carries
 * that scope; every other record is a machine-level record without a scope id. The details are kept as given,
 * {@code transaction_id} included, the key by which a reader drops a record handed over again.
 * <p>
 * Each record is appended in its own transaction that acquires the audit lock; two sinks of one runtime serialize on
 * its in-process lock.
 *
 * @since 0.1
 */
public final class AuditLogSink implements AuditSink {

    private final LockManager lockManager;
    private final AuditLog log;

    /**
     * @param lockManager the lock manager the audit lock is acquired from
     * @param log         the audit log the records are appended to
     * @throws NullPointerException if an argument is {@code null}
     */
    public AuditLogSink(LockManager lockManager, AuditLog log) {
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Appends the record to the audit log in a transaction of its own that holds the audit lock.
     *
     * @param event the record to append to the audit log
     * @throws IllegalArgumentException if the category or the outcome of the event is not one of the audit log
     * @throws UncheckedIOException     if the record cannot be appended; a producer of a store mutation keeps its
     *                                  intent then
     */
    @Override
    public void append(AuditEvent event) {
        var template = toRecord(event, log.clock(), log.osPrincipal());
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(log.lockKey());
            log.append(transaction, template);
        }
    }

    /**
     * The one mapping of a handed-over record to a record of the audit log; {@link AuditLog} forms the records it
     * writes itself (an owed tail repair of the log, or of the audit index during a rotation) by it as well, so an
     * unknown category or outcome is refused on every path.
     *
     * @param event the record as its producer handed it over
     * @param clock the clock of the instant and the id of the record
     * @param actor the OS principal of the runtime
     * @return the record of the audit log, chained to {@link AuditCodec#GENESIS_HASH} until it is appended
     * @throws IllegalArgumentException if the category or the outcome of the event is not one of the audit log
     */
    static AuditRecord toRecord(AuditEvent event, Clock clock, String actor) {
        Objects.requireNonNull(event, "event");
        var category = Category.fromWireName(event.category()).orElseThrow(
                () -> new IllegalArgumentException("Not a category of the audit log: " + event.category()));
        var outcome = Outcome.fromWireName(event.outcome()).orElseThrow(
                () -> new IllegalArgumentException("Not an outcome of the audit log: " + event.outcome()));
        var scope = stringDetail(event, AuditEvent.DETAIL_SCOPE_TYPE).flatMap(Scope::fromWireName);
        return AuditRecord.builder()
                .eventId(TransactionId.generate(clock).value())
                .prevHash(AuditCodec.GENESIS_HASH)
                .timestamp(clock.instant())
                .category(category)
                .event(event.event())
                .scopeType(scope.orElse(Scope.MACHINE))
                .scopeId(scope.flatMap(_ -> stringDetail(event, AuditEvent.DETAIL_SCOPE_ID)).orElse(null))
                .actor(actor)
                .outcome(outcome)
                .details(event.details())
                .build();
    }

    private static Optional<String> stringDetail(AuditEvent event, String name) {
        return event.details().get(name) instanceof String value ? Optional.of(value) : Optional.empty();
    }
}
