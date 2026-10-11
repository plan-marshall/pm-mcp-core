/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import java.io.UncheckedIOException;

/**
 * Where a component of the engine hands over an audit record (PM-IMPL-7). The audit store implements it; a component
 * that must record an event depends on this interface and not on the store.
 * <p>
 * The record of a completed store mutation is delivered at least once: the producer keeps a durable intent of the
 * record until this method returned, and hands the record over again after a failure or a stop. A record handed over
 * again carries the same transaction id ({@link AuditEvent#DETAIL_TRANSACTION_ID}), by which the duplicate is dropped.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface AuditSink {

    /**
     * @param event the record to append to the audit log
     * @throws UncheckedIOException if the record cannot be appended; a producer of a store mutation keeps its intent
     *                              then and hands the record over again later
     */
    void append(AuditEvent event);
}
