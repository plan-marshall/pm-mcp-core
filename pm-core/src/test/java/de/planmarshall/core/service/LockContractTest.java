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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import de.planmarshall.core.log.PmMcpLogMessages;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.store.LockKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The contract of the lock manager towards its callers (PM-IMPL-7): what a refused out-of-order acquisition hands to
 * the audit log and to the failing call.
 */
@DisplayName("Lock contract")
class LockContractTest {

    private static final Path BASE = Path.of("/pm-base");
    private static final String PROJECT = "sample-project";
    private static final LockKey WORKSPACE = LockKey.workspace(BASE, PROJECT);
    private static final LockKey QUEUE = LockKey.queue(BASE, PROJECT);
    private static final LockKey PLAN = LockKey.plan(BASE, PROJECT, "plan-one");
    private static final String WORKSPACE_LOCK = "/pm-base/projects/sample-project/locks/workspace.lock";
    private static final String QUEUE_LOCK = "/pm-base/projects/sample-project/locks/queue.lock";
    private static final String PLAN_LOCK = "/pm-base/projects/sample-project/locks/plan-plan-one.lock";

    @Nested
    @DisplayName("audit event")
    class AuditEvents {

        @Test
        @DisplayName("a lock order violation is the event lock_order_violation of category store with outcome refused")
        void lockOrderViolation() {
            var event = AuditEvent.lockOrderViolation(List.of(WORKSPACE, QUEUE), PLAN);

            assertAll(
                    () -> assertEquals("lock_order_violation", event.event()),
                    () -> assertEquals("store", event.category()),
                    () -> assertEquals("refused", event.outcome()));
        }

        @Test
        @DisplayName("its details are exactly held and requested, as lock paths")
        void details() {
            var event = AuditEvent.lockOrderViolation(List.of(WORKSPACE, QUEUE), PLAN);

            assertEquals(Map.of("held", List.of(WORKSPACE_LOCK, QUEUE_LOCK), "requested", PLAN_LOCK), event.details());
        }

        @Test
        @DisplayName("an event keeps its details apart from the map it was built from")
        void detailsAreCopied() {
            var source = new HashMap<String, Object>(Map.of("requested", PLAN_LOCK));
            var event = new AuditEvent("lock_order_violation", "store", "refused", source);

            source.put("held", List.of());

            assertAll(
                    () -> assertEquals(Set.of("requested"), event.details().keySet()),
                    () -> assertThrows(UnsupportedOperationException.class,
                            () -> event.details().put("held", List.of())));
        }

        @Test
        @DisplayName("an event without a name, a category, an outcome or details is refused")
        void missingComponents() {
            Map<String, Object> details = Map.of();

            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> new AuditEvent(null, "store", "refused", details)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new AuditEvent("lock_order_violation", null, "refused", details)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new AuditEvent("lock_order_violation", "store", null, details)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new AuditEvent("lock_order_violation", "store", "refused", null)));
        }

        @Test
        @DisplayName("a sink receives the events in the order they are appended")
        void sink() {
            var appended = new ArrayList<AuditEvent>();
            AuditSink sink = appended::add;
            var first = AuditEvent.lockOrderViolation(List.of(QUEUE), PLAN);
            var second = AuditEvent.lockOrderViolation(List.of(QUEUE), WORKSPACE);

            sink.append(first);
            sink.append(second);

            assertEquals(List.of(first, second), appended);
        }
    }

    @Nested
    @DisplayName("internal fault")
    class InternalFault {

        @Test
        @DisplayName("a lock order violation carries its reason and the details of its audit event")
        void reasonAndDetails() {
            var fault = InternalFaultException.lockOrderViolation(List.of(WORKSPACE, QUEUE), PLAN);

            assertAll(
                    () -> assertEquals(Reason.LOCK_ORDER_VIOLATION, fault.reason()),
                    () -> assertEquals("lock_order_violation", fault.reason().code()),
                    () -> assertEquals(AuditEvent.lockOrderViolation(List.of(WORKSPACE, QUEUE), PLAN).details(),
                            fault.details()));
        }

        @Test
        @DisplayName("its message names the requested and the held lock paths and no other path")
        void message() {
            var fault = InternalFaultException.lockOrderViolation(List.of(WORKSPACE, QUEUE), PLAN);

            var paths = Pattern.compile("/[^\\s',\\]]+").matcher(fault.getMessage()).results()
                    .map(match -> match.group()).toList();

            assertEquals(List.of(PLAN_LOCK, WORKSPACE_LOCK, QUEUE_LOCK), paths);
        }

        @Test
        @DisplayName("the reason of a fault is closed and starts with the lock order violation")
        void reasons() {
            assertEquals(Reason.LOCK_ORDER_VIOLATION, Reason.values()[0]);
        }

        @Test
        @DisplayName("a fault without a reason, a message or details is refused")
        void missingArguments() {
            Map<String, Object> details = Map.of();

            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> new InternalFaultException(null, "message", details)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new InternalFaultException(Reason.LOCK_ORDER_VIOLATION, null, details)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new InternalFaultException(Reason.LOCK_ORDER_VIOLATION, "message", null)));
        }

        @Test
        @DisplayName("a fault that was serialized keeps its reason and its message and has no details")
        void serialized() throws Exception {
            var fault = InternalFaultException.lockOrderViolation(List.of(QUEUE), PLAN);
            var bytes = new ByteArrayOutputStream();
            try (var out = new ObjectOutputStream(bytes)) {
                out.writeObject(fault);
            }

            InternalFaultException restored;
            try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                restored = (InternalFaultException) in.readObject();
            }

            assertAll(
                    () -> assertEquals(Reason.LOCK_ORDER_VIOLATION, restored.reason()),
                    () -> assertEquals(fault.getMessage(), restored.getMessage()),
                    () -> assertTrue(restored.details().isEmpty()),
                    () -> assertFalse(fault.details().isEmpty()));
        }
    }

    @Nested
    @DisplayName("log messages")
    class LogMessages {

        @Test
        @DisplayName("the messages of locks and leases carry the identifiers 50, 150 and 250")
        void identifiers() {
            assertAll(
                    () -> assertEquals(50, PmMcpLogMessages.INFO.STALE_LEASE_SWEEP_FINISHED.getIdentifier()),
                    () -> assertEquals(150, PmMcpLogMessages.WARN.LEASE_ORPHANED.getIdentifier()),
                    () -> assertEquals(250, PmMcpLogMessages.ERROR.LOCK_ORDER_VIOLATION.getIdentifier()));
        }

        @Test
        @DisplayName("the lock order violation message names the requested and the held lock paths")
        void lockOrderViolation() {
            var message = PmMcpLogMessages.ERROR.LOCK_ORDER_VIOLATION.format(PLAN_LOCK, List.of(QUEUE_LOCK));

            assertAll(
                    () -> assertTrue(message.contains(PLAN_LOCK), message),
                    () -> assertTrue(message.contains(QUEUE_LOCK), message));
        }
    }
}
