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
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;

import de.cuioss.test.generator.Generators;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import lombok.experimental.UtilityClass;
import org.easymock.Capture;
import org.easymock.CaptureType;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;

/**
 * EasyMock audit sinks for the store tests: the accepting sink a test captures into, and the failure script a test asks
 * for, expressed as a mock's expectations, so no hand-written sink carries the behaviour.
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
        return refusing(newCapture(CaptureType.ALL));
    }

    /**
     * @param captured the capture of every append
     * @return an EasyMock sink that accepts and captures every append
     */
    static AuditSink capturing(Capture<AuditEvent> captured) {
        AuditSink sink = createNiceMock(AuditSink.class);
        sink.append(capture(captured));
        expectLastCall().anyTimes();
        replay(sink);
        return sink;
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

    /**
     * @param records records whose transaction id a test cannot know in advance, such as the id of a tail repair
     * @return the records without their transaction id, in their order
     */
    static List<AuditEvent> withoutTransactionIds(List<AuditEvent> records) {
        return records.stream().map(AuditSinkDoubles::withoutTransactionId).toList();
    }

    /**
     * @param store the JSONL store
     * @param torn  the bytes a repair cut off
     * @return the record of the repair without its transaction id
     */
    static AuditEvent tailRepairWithoutId(Path store, byte[] torn) {
        return withoutTransactionId(AuditEvent.jsonlTailRepaired(store, torn.length, StoreSnapshot.sha256(torn),
                Generators.letterStrings(3, 12).next()));
    }

    private static AuditEvent withoutTransactionId(AuditEvent event) {
        var details = new HashMap<>(event.details());
        details.remove(AuditEvent.DETAIL_TRANSACTION_ID);
        return new AuditEvent(event.event(), event.category(), event.outcome(), details);
    }
}
