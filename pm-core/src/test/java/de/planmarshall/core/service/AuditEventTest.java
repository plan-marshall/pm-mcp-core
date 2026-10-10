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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The audit records of the store substrate (PM-IMPL-7): each event has its name, the category {@code store}, the
 * outcome {@code succeeded} and exactly the details the specification names.
 */
@DisplayName("Audit event")
class AuditEventTest {

    private static final String TRANSACTION_ID = "01970000-0000-7000-8000-000000000001";

    @Test
    @DisplayName("a torn tail repair names the store, the bytes cut off and their SHA-256")
    void jsonlTailRepaired() {
        var store = Path.of("/base/projects/pm-mcp-core/plans/a-plan/findings.jsonl");
        var sha = "a".repeat(64);

        var event = AuditEvent.jsonlTailRepaired(store, 17, sha);

        assertAll(
                () -> assertEquals("jsonl_tail_repaired", event.event()),
                () -> assertEquals("store", event.category()),
                () -> assertEquals("succeeded", event.outcome()),
                () -> assertEquals(Map.of("store", store.toString(), "truncated_bytes", 17L, "truncated_sha256", sha),
                        event.details()));
    }

    @Test
    @DisplayName("a cross-scope reconciliation names the rule and the store it wrote")
    void crossScopeReconciled() {
        var event = AuditEvent.crossScopeReconciled("plan_end", "lessons/2026-10-10-01.md");

        assertAll(
                () -> assertEquals("cross_scope_reconciled", event.event()),
                () -> assertEquals("store", event.category()),
                () -> assertEquals("succeeded", event.outcome()),
                () -> assertEquals(Map.of("reconciliation", "plan_end", "store", "lessons/2026-10-10-01.md"),
                        event.details()));
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
    @DisplayName("a missing store, scope type, transaction or target list is refused")
    void missingArguments() {
        var targets = List.of("mcp/status.json");

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.jsonlTailRepaired(null, 1, "a")),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.jsonlTailRepaired(Path.of("a"), 1,
                        null)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward(null, null, TRANSACTION_ID, targets)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward("plan", "a-plan", null, targets)),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward("plan", "a-plan", TRANSACTION_ID, null)),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.crossScopeReconciled(null, "s")),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.crossScopeReconciled("r", null)));
    }
}
