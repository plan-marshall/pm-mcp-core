/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lock;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * The audit sink of the tests: it keeps the records it is handed, in the order they arrive, and writes nothing.
 */
final class RecordingAuditSink implements AuditSink {

    private final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditEvent event) {
        events.add(event);
    }

    /** @return the records handed over so far, in their order */
    List<AuditEvent> events() {
        return List.copyOf(events);
    }
}
