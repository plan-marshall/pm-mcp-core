/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.test;

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.easymock.Capture;
import org.easymock.CaptureType;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * The EasyMock audit sinks of the runtime tests: one that accepts and captures every append, and the names of the
 * records a capture holds.
 */
@UtilityClass
public class AuditSinkDoubles {

    /**
     * @param captured the capture of every append
     * @return an EasyMock sink that accepts and captures every append
     */
    public static AuditSink capturing(Capture<AuditEvent> captured) {
        AuditSink sink = createNiceMock(AuditSink.class);
        sink.append(capture(captured));
        expectLastCall().anyTimes();
        replay(sink);
        return sink;
    }

    /** @return an EasyMock sink that accepts and discards every append */
    public static AuditSink capturing() {
        return capturing(newCapture(CaptureType.ALL));
    }

    /**
     * @param captured the capture of the appends the sink accepted
     * @return the names of the captured records, in their order
     */
    public static List<String> eventNames(Capture<AuditEvent> captured) {
        return captured.getValues().stream().map(AuditEvent::event).toList();
    }
}
