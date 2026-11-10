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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;

import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import lombok.experimental.UtilityClass;
import org.easymock.Capture;
import org.easymock.CaptureType;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * EasyMock audit sinks for the store tests: the failure script a test asks for, expressed as a mock's expectations,
 * so no hand-written sink carries the behaviour.
 */
@UtilityClass
class AuditSinkDoubles {

    /** What the stopping sink throws at its first append, as a killed process stops it. */
    static final class ProcessStopped extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        ProcessStopped() {
            super("process stopped at the audit append");
        }
    }

    /**
     * @param captured the capture of the appends the sink accepts after the refusal
     * @return an EasyMock sink that refuses its first append and captures every later one
     */
    static AuditSink refusing(Capture<AuditEvent> captured) {
        AuditSink sink = createMock(AuditSink.class);
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().andThrow(new UncheckedIOException(new IOException("the audit log cannot be written")));
        sink.append(capture(captured));
        expectLastCall().anyTimes();
        replay(sink);
        return sink;
    }

    /** @return an EasyMock sink that refuses its first append and discards every later one */
    static AuditSink refusing() {
        Capture<AuditEvent> captured = newCapture(CaptureType.ALL);
        return refusing(captured);
    }

    /** @return an EasyMock sink that stops the caller at its first append */
    static AuditSink stopping() {
        AuditSink sink = createMock(AuditSink.class);
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().andThrow(new ProcessStopped());
        sink.append(anyObject(AuditEvent.class));
        expectLastCall().anyTimes();
        replay(sink);
        return sink;
    }
}
