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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The audit records built by the factories of {@link AuditEvent}: those of the store substrate (PM-IMPL-7) and those
 * of the enrolment store (PM-EXEC-2, PM-SEC-4, PM-SEC-5). Each event has its name, its category, its outcome and
 * exactly the details the specification names.
 */
@DisplayName("Audit event")
class AuditEventTest {

    private static final String TRANSACTION_ID = "01970000-0000-7000-8000-000000000001";

    @Test
    @DisplayName("a torn tail repair names the store, the bytes cut off, their SHA-256 and the id of the repair")
    void jsonlTailRepaired() {
        var store = Path.of("/base/projects/pm-mcp-core/plans/a-plan/findings.jsonl");
        var sha = "a".repeat(64);

        var event = AuditEvent.jsonlTailRepaired(store, 17, sha, TRANSACTION_ID);

        assertAll(
                () -> assertEquals("jsonl_tail_repaired", event.event()),
                () -> assertEquals("store", event.category()),
                () -> assertEquals("succeeded", event.outcome()),
                () -> assertEquals(Map.of("store", store.toString(), "truncated_bytes", 17L, "truncated_sha256", sha,
                        "transaction_id", TRANSACTION_ID), event.details()));
    }

    @Test
    @DisplayName("a cross-scope reconciliation names the rule, the store it wrote and the id of the record")
    void crossScopeReconciled() {
        var event = AuditEvent.crossScopeReconciled("plan_end", "lessons/2026-10-10-01.md", TRANSACTION_ID);

        assertAll(
                () -> assertEquals("cross_scope_reconciled", event.event()),
                () -> assertEquals("store", event.category()),
                () -> assertEquals("succeeded", event.outcome()),
                () -> assertEquals(Map.of("reconciliation", "plan_end", "store", "lessons/2026-10-10-01.md",
                        "transaction_id", TRANSACTION_ID), event.details()));
    }

    @Test
    @DisplayName("a stall record is one transition warning with the scope, the state, the cause and the span")
    void planStalled() {
        var progress = Instant.parse("2026-10-20T10:00:00Z");

        var event = AuditEvent.planStalled("plan", "a-plan", "execute.review", "no_progress", progress, 610);

        assertAll(
                () -> assertEquals("plan_stalled", event.event()),
                () -> assertEquals("transition", event.category()),
                () -> assertEquals("warning", event.outcome()),
                () -> assertEquals(Map.of("scope_type", "plan", "scope_id", "a-plan", "state", "execute.review",
                                "cause", "no_progress", "idle_since", progress.toString(), "idle_seconds", 610L),
                        event.details()));
    }

    @Test
    @DisplayName("a stall record without its scope, state, cause or instant is refused")
    void planStalledRefusesMissingValues() {
        var progress = Instant.parse("2026-10-20T10:00:00Z");

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled(null, "a-plan", "s", "no_progress", progress, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", null, "s", "no_progress", progress, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", "a-plan", null, "no_progress", progress, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", "a-plan", "s", null, progress, 1)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", "a-plan", "s", "no_progress", null, 1)));
    }

    @Nested
    @DisplayName("worker start failures")
    class WorkerStartFailures {

        private static final String PROJECT = "pm-mcp-core";

        @Test
        @DisplayName("a start failure is a failed operator record naming the project, role, harness and error text")
        void workerStartFailed() {
            var detail = "the harness exited before its first call";

            var event = AuditEvent.workerStartFailed(PROJECT, "supervisor", "claude", detail);

            assertAll(
                    () -> assertEquals("worker_start_failed", event.event()),
                    () -> assertEquals(AuditEvent.WORKER_START_FAILED, event.event()),
                    () -> assertEquals("operator", event.category()),
                    () -> assertEquals("failed", event.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "role", "supervisor", "harness", "claude",
                            "harness_error", detail), event.details()));
        }

        @Test
        @DisplayName("a start failure without its project, role, harness or error text is refused")
        void workerStartFailedRefusesMissingValues() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(null, "supervisor", "claude", "detail")),
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(PROJECT, null, "claude", "detail")),
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(PROJECT, "supervisor", null, "detail")),
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(PROJECT, "supervisor", "claude", null)));
        }
    }

    @Nested
    @DisplayName("roll-forward of a transaction")
    class RolledForward {

        @Test
        @DisplayName("for a plan scope it names the scope type, the scope id, the transaction and the targets")
        void planScope() {
            var event = AuditEvent.transactionRolledForward("plan", "a-plan", TRANSACTION_ID,
                    List.of("mcp/status.json", "findings.jsonl"));

            assertAll(
                    () -> assertEquals("transaction_rolled_forward", event.event()),
                    () -> assertEquals("store", event.category()),
                    () -> assertEquals("succeeded", event.outcome()),
                    () -> assertEquals(Map.of("scope_type", "plan", "scope_id", "a-plan", "transaction_id",
                                    TRANSACTION_ID, "targets", List.of("mcp/status.json", "findings.jsonl")),
                            event.details()));
        }

        /** The matched case to the one above: the workspace scope has no id, so the detail is left out. */
        @Test
        @DisplayName("for the workspace scope it leaves the scope id out")
        void workspaceScope() {
            var event = AuditEvent.transactionRolledForward("workspace", null, TRANSACTION_ID,
                    List.of("pending/ent-0123abcd.json"));

            assertAll(
                    () -> assertFalse(event.details().containsKey("scope_id")),
                    () -> assertEquals(Map.of("scope_type", "workspace", "transaction_id", TRANSACTION_ID, "targets",
                            List.of("pending/ent-0123abcd.json")), event.details()));
        }

        @Test
        @DisplayName("the targets are copied: a later change of the caller's list does not reach the record")
        void targetsCopied() {
            var targets = new ArrayList<>(List.of("mcp/status.json"));

            var event = AuditEvent.transactionRolledForward("epic", "an-epic", TRANSACTION_ID, targets);
            targets.add("status.json");

            assertEquals(List.of("mcp/status.json"), event.details().get("targets"));
        }
    }

    @Test
    @DisplayName("a removed pending entry record names the entry, its store, its creation instant and the transaction")
    void pendingEntryRemoved() {
        var event = AuditEvent.pendingEntryRemoved("ent-0123abcd", "pending/ent-0123abcd.json",
                "2026-10-13T08:00:00Z", TRANSACTION_ID);

        assertAll(
                () -> assertEquals("pending_entry_removed", event.event()),
                () -> assertEquals(AuditEvent.PENDING_ENTRY_REMOVED, event.event()),
                () -> assertEquals("store", event.category()),
                () -> assertEquals("succeeded", event.outcome()),
                () -> assertEquals(Map.of("entry_id", "ent-0123abcd", "store", "pending/ent-0123abcd.json",
                        "created_at", "2026-10-13T08:00:00Z", "transaction_id", TRANSACTION_ID), event.details()),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved(null, "s", "c", TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved("e", null, "c", TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved("e", "s", null, TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved("e", "s", "c", null)));
    }

    @Nested
    @DisplayName("enrolment events")
    class Enrolment {

        private static final String PROJECT = "pm-mcp-core";

        @Test
        @DisplayName("a tampered enrolment is a refused security event naming the project and the reason")
        void enrolmentTampered() {
            var event = AuditEvent.enrolmentTampered(PROJECT, "hmac_mismatch");

            assertAll(
                    () -> assertEquals("enrolment_tampered", event.event()),
                    () -> assertEquals("security", event.category()),
                    () -> assertEquals("refused", event.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", "hmac_mismatch"), event.details()));
        }

        @Test
        @DisplayName("an insecure enrolment is a refused security event naming the project and the reason")
        void enrolmentInsecure() {
            var event = AuditEvent.enrolmentInsecure(PROJECT, "mode broader than 0600");

            assertAll(
                    () -> assertEquals("enrolment_insecure", event.event()),
                    () -> assertEquals("security", event.category()),
                    () -> assertEquals("refused", event.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", "mode broader than 0600"),
                            event.details()));
        }

        /** Built like the insecure record above, which is its matched control: same shape, another event name. */
        @Test
        @DisplayName("an invalid enrolment is a refused security event naming the project and the reason, no transaction")
        void enrolmentInvalid() {
            var reason = "build_environment: GH_TOKEN is a credential variable";

            var event = AuditEvent.enrolmentInvalid(PROJECT, reason);
            var insecure = AuditEvent.enrolmentInsecure(PROJECT, reason);

            assertAll(
                    () -> assertEquals("enrolment_invalid", event.event()),
                    () -> assertEquals(AuditEvent.ENROLMENT_INVALID, event.event()),
                    () -> assertEquals("security", event.category()),
                    () -> assertEquals("refused", event.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", reason), event.details()),
                    () -> assertFalse(event.details().containsKey("transaction_id")),
                    () -> assertEquals(insecure.category(), event.category()),
                    () -> assertEquals(insecure.outcome(), event.outcome()),
                    () -> assertEquals(insecure.details(), event.details()));
        }

        @Test
        @DisplayName("an unenrol refused for live work is a refused operator event with the reason scope_active")
        void unenrolRefused() {
            var event = AuditEvent.unenrolRefused(PROJECT);

            assertAll(
                    () -> assertEquals("unenrol_refused", event.event()),
                    () -> assertEquals("operator", event.category()),
                    () -> assertEquals("refused", event.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", "scope_active"), event.details()));
        }

        @Test
        @DisplayName("a written enrolment is a succeeded operator event naming the project and the transaction")
        void enrolmentWritten() {
            assertSucceededOperatorEvent("enrolment_written", AuditEvent.enrolmentWritten(PROJECT, TRANSACTION_ID));
        }

        @Test
        @DisplayName("a removed enrolment is a succeeded operator event naming the project and the transaction")
        void enrolmentRemoved() {
            assertSucceededOperatorEvent("enrolment_removed", AuditEvent.enrolmentRemoved(PROJECT, TRANSACTION_ID));
        }

        @Test
        @DisplayName("a re-baselined enrolment is a succeeded operator event naming the project and the transaction")
        void enrolmentRebaselined() {
            assertSucceededOperatorEvent("enrolment_rebaselined",
                    AuditEvent.enrolmentRebaselined(PROJECT, TRANSACTION_ID));
        }

        /** The matched control to the three above: a refusal changed nothing, is not owed and carries no transaction. */
        @Test
        @DisplayName("the refusal records carry no transaction id and equal what they were")
        void refusalsWithoutTransaction() {
            var refusals = List.of(AuditEvent.enrolmentTampered(PROJECT, "hmac_mismatch"),
                    AuditEvent.enrolmentInsecure(PROJECT, "symbolic link"), AuditEvent.unenrolRefused(PROJECT));

            assertAll(
                    () -> assertTrue(refusals.stream()
                            .noneMatch(event -> event.details().containsKey("transaction_id"))),
                    () -> assertEquals(List.of(
                            new AuditEvent("enrolment_tampered", "security", "refused",
                                    Map.of("project_id", PROJECT, "reason", "hmac_mismatch")),
                            new AuditEvent("enrolment_insecure", "security", "refused",
                                    Map.of("project_id", PROJECT, "reason", "symbolic link")),
                            new AuditEvent("unenrol_refused", "operator", "refused",
                                    Map.of("project_id", PROJECT, "reason", "scope_active"))), refusals));
        }

        @Test
        @DisplayName("the unconfirmed form of a succeeded record: failed, reason not_confirmed, same transaction")
        void notConfirmed() {
            var succeeded = AuditEvent.enrolmentWritten(PROJECT, TRANSACTION_ID);

            var unconfirmed = succeeded.notConfirmed();

            assertAll(
                    () -> assertEquals("enrolment_written", unconfirmed.event()),
                    () -> assertEquals("operator", unconfirmed.category()),
                    () -> assertEquals("failed", unconfirmed.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "transaction_id", TRANSACTION_ID, "reason",
                            "not_confirmed"), unconfirmed.details()),
                    () -> assertEquals("succeeded", succeeded.outcome()));
        }

        @Test
        @DisplayName("only a succeeded record with a transaction id has an unconfirmed form")
        void notConfirmedRefused() {
            var refusal = AuditEvent.unenrolRefused(PROJECT);
            var unconfirmed = AuditEvent.enrolmentRemoved(PROJECT, TRANSACTION_ID).notConfirmed();
            var withoutTransaction = new AuditEvent("enrolment_written", "operator", "succeeded",
                    Map.of("project_id", PROJECT));

            assertAll(
                    () -> assertThrows(IllegalStateException.class, refusal::notConfirmed),
                    () -> assertThrows(IllegalStateException.class, unconfirmed::notConfirmed),
                    () -> assertThrows(IllegalStateException.class, withoutTransaction::notConfirmed));
        }

        private void assertSucceededOperatorEvent(String name, AuditEvent event) {
            assertAll(
                    () -> assertEquals(name, event.event()),
                    () -> assertEquals("operator", event.category()),
                    () -> assertEquals("succeeded", event.outcome()),
                    () -> assertEquals(Map.of("project_id", PROJECT, "transaction_id", TRANSACTION_ID),
                            event.details()));
        }
    }

    @Test
    @DisplayName("a missing store, scope type, transaction, target list, project or reason is refused")
    void missingArguments() {
        var targets = List.of("mcp/status.json");
        var store = Path.of("a");
        var project = "pm-mcp-core";

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.jsonlTailRepaired(null, 1, "a", TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.jsonlTailRepaired(store, 1, null, TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.jsonlTailRepaired(store, 1, "a", null)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward(null, null, TRANSACTION_ID, targets)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward("plan", "a-plan", null, targets)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward("plan", "a-plan", TRANSACTION_ID, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.crossScopeReconciled(null, "s", TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.crossScopeReconciled("r", null, TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.crossScopeReconciled("r", "s", null)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentTampered(null, "r")),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentTampered(project, null)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentInsecure(null, "r")),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentInsecure(project, null)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentInvalid(null, "r")),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentInvalid(project, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentWritten(null, TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentWritten(project, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentRemoved(null, TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentRemoved(project, null)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentRebaselined(null, TRANSACTION_ID)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentRebaselined(project, null)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.unenrolRefused(null)));
    }
}
