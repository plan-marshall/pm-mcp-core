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

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * The audit sink of the delivery tests: it fails the first appends it is handed, either by refusing them as an audit
 * store that cannot write does, or by stopping the caller as a killed process stops it, and records the appends after
 * them in their order.
 */
final class FailingAuditSink implements AuditSink {

    /** What the sink throws to stop the caller at the append, as a killed process stops it. */
    static final class ProcessStopped extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        ProcessStopped() {
            super("process stopped at the audit append");
        }
    }

    private final AtomicInteger failuresLeft;
    private final boolean stop;
    private final RecordingAuditSink delivered = new RecordingAuditSink();

    private FailingAuditSink(int failures, boolean stop) {
        this.failuresLeft = new AtomicInteger(failures);
        this.stop = stop;
    }

    /**
     * @param failures how many appends are refused first
     * @return a sink that refuses the first appends with an {@link UncheckedIOException}
     */
    static FailingAuditSink refusing(int failures) {
        return new FailingAuditSink(failures, false);
    }

    /**
     * @param failures how many appends stop the caller first
     * @return a sink that throws {@link ProcessStopped} at the first appends
     */
    static FailingAuditSink stopping(int failures) {
        return new FailingAuditSink(failures, true);
    }

    @Override
    public void append(AuditEvent event) {
        if (failuresLeft.getAndDecrement() > 0) {
            if (stop) {
                throw new ProcessStopped();
            }
            throw new UncheckedIOException(new IOException("the audit log cannot be written"));
        }
        delivered.append(event);
    }

    /** @return the records accepted so far, in their order */
    List<AuditEvent> events() {
        return delivered.events();
    }
}
