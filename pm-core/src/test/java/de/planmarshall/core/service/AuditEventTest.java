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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The audit records built by the factories of {@link AuditEvent}: those of the store substrate (PM-IMPL-7) and those
 * of the enrolment store (PM-EXEC-2, PM-SEC-4, PM-SEC-5). Each event has its name, its category, its outcome and
 * exactly the details the specification names.
 */
@EnableGeneratorController
@DisplayName("Audit event")
class AuditEventTest {

    private static final String TRANSACTION_ID = Generators.letterStrings(8, 20).next();

    @Test
    @DisplayName("a torn tail repair names the store, the bytes cut off, their SHA-256 and the id of the repair")
    void jsonlTailRepaired() {
        var store = Path.of("/base/projects/pm-mcp-core/plans/a-plan/findings.jsonl");
        var sha = "%064x".formatted(Generators.longs().next());
        var truncatedBytes = Generators.longs(1, 10_000).next();

        var event = AuditEvent.jsonlTailRepaired(store, truncatedBytes, sha, TRANSACTION_ID);

        assertAll(
                () -> assertEquals("jsonl_tail_repaired", event.event(), "torn tail repair event name"),
                () -> assertEquals("store", event.category(), "tail repair event category is store"),
                () -> assertEquals("succeeded", event.outcome(), "tail repair outcome is succeeded"),
                () -> assertEquals(Map.of("store", store.toString(), "truncated_bytes", truncatedBytes,
                                "truncated_sha256", sha, "transaction_id", TRANSACTION_ID), event.details(),
                        "tail repair details name store, bytes, hash, transaction"));
    }

    @Test
    @DisplayName("a cross-scope reconciliation names the rule, the store it wrote and the id of the record")
    void crossScopeReconciled() {
        var event = AuditEvent.crossScopeReconciled("plan_end", "lessons/2026-10-10-01.md", TRANSACTION_ID);

        assertAll(
                () -> assertEquals("cross_scope_reconciled", event.event(), "cross-scope event name"),
                () -> assertEquals("store", event.category(), "cross-scope category is store"),
                () -> assertEquals("succeeded", event.outcome(), "cross-scope outcome is succeeded"),
                () -> assertEquals(Map.of("reconciliation", "plan_end", "store", "lessons/2026-10-10-01.md",
                                "transaction_id", TRANSACTION_ID), event.details(),
                        "cross-scope details name rule, store and transaction"));
    }

    @Test
    @DisplayName("a stall record is one transition warning with the scope, the state, the cause and the span")
    void planStalled() {
        var progress = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var scopeId = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var idleSeconds = Generators.longs(1, 100_000).next();

        var event = AuditEvent.planStalled("plan", scopeId, "execute.review", "no_progress", progress, idleSeconds);

        assertAll(
                () -> assertEquals("plan_stalled", event.event(), "stall record event name is plan_stalled"),
                () -> assertEquals("transition", event.category(), "stall record category is transition"),
                () -> assertEquals("warning", event.outcome(), "stall record outcome is warning"),
                () -> assertEquals(Map.of("scope_type", "plan", "scope_id", scopeId, "state", "execute.review",
                        "cause", "no_progress", "idle_since", progress.toString(), "idle_seconds",
                        idleSeconds), event.details(), "stall details name scope, state, cause and span"));
    }

    @Test
    @DisplayName("a stall record without its scope, state, cause or instant is refused")
    void planStalledRefusesMissingValues() {
        var progress = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var scopeId = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var state = Generators.letterStrings(3, 10).next();
        var idleSeconds = Generators.longs(1, 1_000).next();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled(null, scopeId, state, "no_progress", progress, idleSeconds),
                        "stall record needs a scope type"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", null, state, "no_progress", progress, idleSeconds),
                        "stall record needs a scope id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", scopeId, null, "no_progress", progress, idleSeconds),
                        "stall record needs a state"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", scopeId, state, null, progress, idleSeconds),
                        "stall record needs a cause"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.planStalled("plan", scopeId, state, "no_progress", null, idleSeconds),
                        "stall record needs an idle instant"));
    }

    @Nested
    @DisplayName("worker start failures")
    class WorkerStartFailures {

        private static final String PROJECT =
                "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

        @Test
        @DisplayName("a start failure is a failed operator record naming the project, role, harness and error text")
        void workerStartFailed() {
            var detail = Generators.letterStrings(10, 60).next();

            var event = AuditEvent.workerStartFailed(PROJECT, "supervisor", "claude", detail);

            assertAll(
                    () -> assertEquals("worker_start_failed", event.event(), "start failure event name"),
                    () -> assertEquals(AuditEvent.WORKER_START_FAILED, event.event(),
                            "start failure constant agrees"),
                    () -> assertEquals("operator", event.category(), "start failure category is operator"),
                    () -> assertEquals("failed", event.outcome(), "start failure outcome is failed"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "role", "supervisor", "harness", "claude",
                                    "harness_error", detail), event.details(),
                            "start failure names project, role, harness, error"));
        }

        @Test
        @DisplayName("a start failure without its project, role, harness or error text is refused")
        void workerStartFailedRefusesMissingValues() {
            var detail = Generators.letterStrings(5, 20).next();

            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(null, "supervisor", "claude", detail),
                            "start failure needs a project id"),
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(PROJECT, null, "claude", detail),
                            "start failure needs a role"),
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(PROJECT, "supervisor", null, detail),
                            "start failure needs a harness"),
                    () -> assertThrows(NullPointerException.class,
                            () -> AuditEvent.workerStartFailed(PROJECT, "supervisor", "claude", null),
                            "start failure needs the error text"));
        }
    }

    @Nested
    @DisplayName("roll-forward of a transaction")
    class RolledForward {

        @Test
        @DisplayName("for a plan scope it names the scope type, the scope id, the transaction and the targets")
        void planScope() {
            var scopeId = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
            var targets = List.of("mcp/status.json", "findings.jsonl");

            var event = AuditEvent.transactionRolledForward("plan", scopeId, TRANSACTION_ID, targets);

            assertAll(
                    () -> assertEquals("transaction_rolled_forward", event.event(), "roll-forward event name"),
                    () -> assertEquals("store", event.category(), "roll-forward category is store"),
                    () -> assertEquals("succeeded", event.outcome(), "roll-forward outcome is succeeded"),
                    () -> assertEquals(Map.of("scope_type", "plan", "scope_id", scopeId, "transaction_id",
                                    TRANSACTION_ID, "targets", targets), event.details(),
                            "roll-forward details name scope, transaction, targets"));
        }

        /** The matched case to the one above: the workspace scope has no id, so the detail is left out. */
        @Test
        @DisplayName("for the workspace scope it leaves the scope id out")
        void workspaceScope() {
            var event = AuditEvent.transactionRolledForward("workspace", null, TRANSACTION_ID,
                    List.of("pending/ent-0123abcd.json"));

            assertAll(
                    () -> assertFalse(event.details().containsKey("scope_id"), "workspace record has no scope id"),
                    () -> assertEquals(Map.of("scope_type", "workspace", "transaction_id", TRANSACTION_ID, "targets",
                                    List.of("pending/ent-0123abcd.json")), event.details(),
                            "workspace record names type, transaction, targets"));
        }

        @Test
        @DisplayName("the targets are copied: a later change of the caller's list does not reach the record")
        void targetsCopied() {
            var targets = new ArrayList<>(List.of("mcp/status.json"));
            var scopeId = "e" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

            var event = AuditEvent.transactionRolledForward("epic", scopeId, TRANSACTION_ID, targets);
            targets.add("status.json");

            assertEquals(List.of("mcp/status.json"), event.details().get("targets"),
                    "targets list is copied, not shared");
        }
    }

    @Test
    @DisplayName("a removed pending entry names the entry, its directory, the run instant and the transaction")
    void pendingEntryRemoved() {
        var entryId = "ent-" + Generators.letterStrings(4, 8).next().toLowerCase(Locale.ROOT);
        var directory = "pending/" + entryId;
        // The instant carries a fraction, so removed_at's documented form (a fraction when there is one) is guarded
        var removedAt = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next(),
                Generators.longs(1, 999_999_999L).next());

        var event = AuditEvent.pendingEntryRemoved(entryId, directory, removedAt, TRANSACTION_ID);

        assertAll(
                () -> assertEquals("pending_entry_removed", event.event(), "pending removal event name"),
                () -> assertEquals(AuditEvent.PENDING_ENTRY_REMOVED, event.event(),
                        "pending removal constant agrees"),
                () -> assertEquals("store", event.category(), "pending removal category is store"),
                () -> assertEquals("succeeded", event.outcome(), "pending removal outcome is succeeded"),
                () -> assertEquals(Map.of("entry_id", entryId, "store", directory, "removed_at",
                                removedAt.toString(), "transaction_id", TRANSACTION_ID), event.details(),
                        "pending removal names entry, directory, instant, transaction"),
                () -> assertTrue(String.valueOf(event.details().get(AuditEvent.DETAIL_REMOVED_AT))
                                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3,9}Z"),
                        "removed_at is an ISO-8601 instant with its fraction"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved(null, directory, removedAt, TRANSACTION_ID),
                        "removal needs an entry id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved(entryId, null, removedAt, TRANSACTION_ID),
                        "removal needs a directory"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved(entryId, directory, null, TRANSACTION_ID),
                        "removal needs the run instant"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.pendingEntryRemoved(entryId, directory, removedAt, null),
                        "removal needs a transaction id"));
    }

    @Nested
    @DisplayName("enrolment events")
    class Enrolment {

        private static final String PROJECT =
                "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

        @Test
        @DisplayName("a tampered enrolment is a refused security event naming the project and the reason")
        void enrolmentTampered() {
            var event = AuditEvent.enrolmentTampered(PROJECT, "hmac_mismatch");

            assertAll(
                    () -> assertEquals("enrolment_tampered", event.event(), "tampered enrolment event name"),
                    () -> assertEquals("security", event.category(), "tampered enrolment category is security"),
                    () -> assertEquals("refused", event.outcome(), "tampered enrolment outcome is refused"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", "hmac_mismatch"), event.details(),
                            "tampered enrolment names project and reason"));
        }

        @Test
        @DisplayName("an insecure enrolment is a refused security event naming the project and the reason")
        void enrolmentInsecure() {
            var event = AuditEvent.enrolmentInsecure(PROJECT, "mode broader than 0600");

            assertAll(
                    () -> assertEquals("enrolment_insecure", event.event(), "insecure enrolment event name"),
                    () -> assertEquals("security", event.category(), "insecure enrolment category is security"),
                    () -> assertEquals("refused", event.outcome(), "insecure enrolment outcome is refused"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", "mode broader than 0600"),
                            event.details(), "insecure enrolment names project and reason"));
        }

        /** Built like the insecure record above, which is its matched control: same shape, another event name. */
        @Test
        @DisplayName("an invalid enrolment is a refused security event naming the project and the reason, no transaction")
        void enrolmentInvalid() {
            var reason = "build_environment: GH_TOKEN is a credential variable";

            var event = AuditEvent.enrolmentInvalid(PROJECT, reason);
            var insecure = AuditEvent.enrolmentInsecure(PROJECT, reason);

            assertAll(
                    () -> assertEquals("enrolment_invalid", event.event(), "invalid enrolment event name"),
                    () -> assertEquals(AuditEvent.ENROLMENT_INVALID, event.event(),
                            "invalid enrolment constant agrees"),
                    () -> assertEquals("security", event.category(), "invalid enrolment category is security"),
                    () -> assertEquals("refused", event.outcome(), "invalid enrolment outcome is refused"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", reason), event.details(),
                            "invalid enrolment names project and reason"),
                    () -> assertFalse(event.details().containsKey("transaction_id"),
                            "invalid enrolment carries no transaction"),
                    () -> assertEquals(insecure.category(), event.category(), "invalid matches insecure category"),
                    () -> assertEquals(insecure.outcome(), event.outcome(), "invalid matches insecure outcome"),
                    () -> assertEquals(insecure.details(), event.details(), "invalid matches insecure details"));
        }

        @Test
        @DisplayName("an unenrol refused for live work is a refused operator event with the reason scope_active")
        void unenrolRefused() {
            var event = AuditEvent.unenrolRefused(PROJECT);

            assertAll(
                    () -> assertEquals("unenrol_refused", event.event(), "unenrol refusal event name"),
                    () -> assertEquals("operator", event.category(), "unenrol refusal category is operator"),
                    () -> assertEquals("refused", event.outcome(), "unenrol refusal outcome is refused"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "reason", "scope_active"), event.details(),
                            "unenrol refusal names the reason scope_active"));
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
                                    .noneMatch(event -> event.details().containsKey("transaction_id")),
                            "refusal records carry no transaction id"),
                    () -> assertEquals(List.of(
                                    new AuditEvent("enrolment_tampered", "security", "refused",
                                            Map.of("project_id", PROJECT, "reason", "hmac_mismatch")),
                                    new AuditEvent("enrolment_insecure", "security", "refused",
                                            Map.of("project_id", PROJECT, "reason", "symbolic link")),
                                    new AuditEvent("unenrol_refused", "operator", "refused",
                                            Map.of("project_id", PROJECT, "reason", "scope_active"))), refusals,
                            "refusal records equal the expected three"));
        }

        @Test
        @DisplayName("the unconfirmed form of a succeeded record: failed, reason not_confirmed, same transaction")
        void notConfirmed() {
            var succeeded = AuditEvent.enrolmentWritten(PROJECT, TRANSACTION_ID);

            var unconfirmed = succeeded.notConfirmed();

            assertAll(
                    () -> assertEquals("enrolment_written", unconfirmed.event(), "unconfirmed keeps the event name"),
                    () -> assertEquals("operator", unconfirmed.category(), "unconfirmed keeps the category"),
                    () -> assertEquals("failed", unconfirmed.outcome(), "unconfirmed outcome is failed"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "transaction_id", TRANSACTION_ID, "reason",
                            "not_confirmed"), unconfirmed.details(), "unconfirmed names reason not_confirmed"),
                    () -> assertEquals("succeeded", succeeded.outcome(), "the original outcome stays succeeded"));
        }

        @Test
        @DisplayName("only a succeeded record with a transaction id has an unconfirmed form")
        void notConfirmedRefused() {
            var refusal = AuditEvent.unenrolRefused(PROJECT);
            var unconfirmed = AuditEvent.enrolmentRemoved(PROJECT, TRANSACTION_ID).notConfirmed();
            var withoutTransaction = new AuditEvent("enrolment_written", "operator", "succeeded",
                    Map.of("project_id", PROJECT));

            assertAll(
                    () -> assertThrows(IllegalStateException.class, refusal::notConfirmed,
                            "a refusal has no unconfirmed form"),
                    () -> assertThrows(IllegalStateException.class, unconfirmed::notConfirmed,
                            "an unconfirmed record cannot be unconfirmed again"),
                    () -> assertThrows(IllegalStateException.class, withoutTransaction::notConfirmed,
                            "a record without transaction has no unconfirmed form"));
        }

        private void assertSucceededOperatorEvent(String name, AuditEvent event) {
            assertAll(
                    () -> assertEquals(name, event.event(), "succeeded operator event name"),
                    () -> assertEquals("operator", event.category(), "succeeded operator category"),
                    () -> assertEquals("succeeded", event.outcome(), "succeeded operator outcome"),
                    () -> assertEquals(Map.of("project_id", PROJECT, "transaction_id", TRANSACTION_ID),
                            event.details(), "succeeded operator names project and transaction"));
        }
    }

    @Test
    @DisplayName("a missing store, scope type, transaction, target list, project or reason is refused")
    void missingArguments() {
        var targets = List.of("mcp/status.json");
        var store = Path.of(Generators.letterStrings(3, 10).next());
        var storeName = Generators.letterStrings(5, 20).next();
        var project = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var scopeId = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var reconciliation = Generators.letterStrings(5, 20).next();
        var reason = Generators.letterStrings(5, 20).next();
        var sha = "%064x".formatted(Generators.longs().next());
        var truncatedBytes = Generators.longs(1, 1_000).next();

        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.jsonlTailRepaired(null, truncatedBytes, sha, TRANSACTION_ID),
                        "tail repair needs a store"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.jsonlTailRepaired(store, truncatedBytes, null, TRANSACTION_ID),
                        "tail repair needs the SHA-256"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.jsonlTailRepaired(store, truncatedBytes, sha, null),
                        "tail repair needs a transaction id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward(null, null, TRANSACTION_ID, targets),
                        "roll-forward needs a scope type"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward("plan", scopeId, null, targets),
                        "roll-forward needs a transaction id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.transactionRolledForward("plan", scopeId, TRANSACTION_ID, null),
                        "roll-forward needs a target list"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.crossScopeReconciled(null, storeName, TRANSACTION_ID),
                        "cross-scope needs a reconciliation rule"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.crossScopeReconciled(reconciliation, null, TRANSACTION_ID),
                        "cross-scope needs a store"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.crossScopeReconciled(reconciliation, storeName, null),
                        "cross-scope needs a transaction id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentTampered(null, reason),
                        "tampered enrolment needs a project id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentTampered(project, null),
                        "tampered enrolment needs a reason"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentInsecure(null, reason),
                        "insecure enrolment needs a project id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentInsecure(project, null),
                        "insecure enrolment needs a reason"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentInvalid(null, reason),
                        "invalid enrolment needs a project id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentInvalid(project, null),
                        "invalid enrolment needs a reason"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentWritten(null, TRANSACTION_ID),
                        "written enrolment needs a project id"),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentWritten(project, null),
                        "written enrolment needs a transaction id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentRemoved(null, TRANSACTION_ID),
                        "removed enrolment needs a project id"),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.enrolmentRemoved(project, null),
                        "removed enrolment needs a transaction id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentRebaselined(null, TRANSACTION_ID),
                        "rebaselined enrolment needs a project id"),
                () -> assertThrows(NullPointerException.class,
                        () -> AuditEvent.enrolmentRebaselined(project, null),
                        "rebaselined enrolment needs a transaction id"),
                () -> assertThrows(NullPointerException.class, () -> AuditEvent.unenrolRefused(null),
                        "unenrol refusal needs a project id"));
    }
}
