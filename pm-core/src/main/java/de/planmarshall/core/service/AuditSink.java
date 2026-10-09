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

/**
 * Where a component of the engine hands over an audit record (PM-IMPL-7). The audit store implements it; a component
 * that must record an event depends on this interface and not on the store.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface AuditSink {

    /**
     * @param event the record to append to the audit log
     */
    void append(AuditEvent event);
}
