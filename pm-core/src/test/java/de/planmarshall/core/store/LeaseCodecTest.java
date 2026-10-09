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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.LeaseCodec.FormatException;
import de.planmarshall.core.store.LeaseCodec.Refusal;

/**
 * The document of a lease store (PM-IMPL-7, PM-IMPL-2): one format version, one closed shape, and one shape per
 * instant.
 */
@DisplayName("Lease codec")
class LeaseCodecTest {

    private static final String PROJECT_ID = "\"project_id\":\"pm-mcp-core\"";
    private static final String SCOPE_TYPE = "\"scope_type\":\"plan\"";
    private static final String SCOPE_ID = "\"scope_id\":\"lock-manager\"";
    private static final String RUNTIME_PID = "\"runtime_pid\":4711";
    private static final String RUNTIME_STARTED_AT = "\"runtime_started_at\":\"2026-10-09T08:15:30.123456Z\"";
    private static final String NOT_ORPHANED = "\"orphaned_at\":null";
    private static final String ORPHANED_AT = "\"orphaned_at\":\"2026-10-09T10:00:00.000001Z\"";
    private static final String KEY = "\"key\":\"plan-marshall/pm-mcp-core\"";
    private static final String ACQUIRED_AT = "\"acquired_at\":\"2026-10-09T08:16:00Z\"";
    private static final String EXPIRES_AT = "\"lease_expires_at\":\"2026-10-09T09:16:00.500Z\"";
    private static final String NO_EXPIRY = "\"lease_expires_at\":null";

    private static final String HOLDER = "{" + RUNTIME_PID + "," + RUNTIME_STARTED_AT + "}";
    private static final String HOLDER_INSTANCE = "\"holder_instance\":" + HOLDER;

    private static final String PLAN_OWNER = "{" + PROJECT_ID + "," + SCOPE_TYPE + "," + SCOPE_ID + ","
            + HOLDER_INSTANCE + "," + NOT_ORPHANED + "}";

    private static final String PLAN_LEASE = "{" + KEY + ",\"owner\":" + PLAN_OWNER + "," + ACQUIRED_AT + ","
            + EXPIRES_AT + "}";

    private static final String ORPHANED_WORKSPACE_LEASE = "{\"key\":\"slot-1\",\"owner\":{" + PROJECT_ID
            + ",\"scope_type\":\"workspace\",\"scope_id\":null," + HOLDER_INSTANCE + "," + ORPHANED_AT + "},"
            + "\"acquired_at\":\"2026-10-09T08:17:00Z\"," + NO_EXPIRY + "}";

    private static byte[] document(String... leases) {
        return utf8("{\"format_version\":1,\"leases\":[" + String.join(",", leases) + "]}");
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static FormatException refusalOf(byte[] document) {
        return assertThrows(FormatException.class, () -> LeaseCodec.read(document));
    }

    static Stream<Arguments> invalidOwners() {
        return Stream.of(
                Arguments.of("project_id in upper case", PROJECT_ID, "\"project_id\":\"PM-MCP\""),
                Arguments.of("project_id too short", PROJECT_ID, "\"project_id\":\"pm\""),
                Arguments.of("project_id with a path", PROJECT_ID, "\"project_id\":\"../pm-core\""),
                Arguments.of("project_id ending with a hyphen", PROJECT_ID, "\"project_id\":\"pm-core-\""),
                Arguments.of("project_id null", PROJECT_ID, "\"project_id\":null"),
                Arguments.of("project_id missing", PROJECT_ID + ",", ""),
                Arguments.of("runtime_pid 0", RUNTIME_PID, "\"runtime_pid\":0"),
                Arguments.of("runtime_pid negative", RUNTIME_PID, "\"runtime_pid\":-1"),
                Arguments.of("runtime_pid a string", RUNTIME_PID, "\"runtime_pid\":\"4711\""),
                Arguments.of("runtime_pid a fraction", RUNTIME_PID, "\"runtime_pid\":4711.0"),
                Arguments.of("runtime_pid beyond a long", RUNTIME_PID, "\"runtime_pid\":99999999999999999999"),
                Arguments.of("runtime_pid missing", RUNTIME_PID + ",", ""),
                Arguments.of("runtime_started_at missing", "," + RUNTIME_STARTED_AT, ""),
                Arguments.of("holder_instance not an object", HOLDER_INSTANCE, "\"holder_instance\":4711"),
                Arguments.of("holder_instance missing", HOLDER_INSTANCE + ",", ""),
                Arguments.of("holder_instance with an undeclared field", RUNTIME_PID, RUNTIME_PID + ",\"host\":\"a\""),
                Arguments.of("scope_type undeclared", SCOPE_TYPE, "\"scope_type\":\"lessons\""),
                Arguments.of("scope_type in upper case", SCOPE_TYPE, "\"scope_type\":\"PLAN\""),
                Arguments.of("scope_type missing", SCOPE_TYPE + ",", ""),
                Arguments.of("scope_id null for a plan", SCOPE_ID, "\"scope_id\":null"),
                Arguments.of("scope_id missing", SCOPE_ID + ",", ""),
                Arguments.of("scope_id outside the grammar", SCOPE_ID, "\"scope_id\":\"Lock Manager\""),
                Arguments.of("scope_id set for the workspace", SCOPE_TYPE, "\"scope_type\":\"workspace\""),
                Arguments.of("scope_id set for the project", SCOPE_TYPE, "\"scope_type\":\"project\""),
                Arguments.of("owner with an undeclared field", NOT_ORPHANED, NOT_ORPHANED + ",\"heartbeat_at\":null"),
                Arguments.of("owner with a field twice", NOT_ORPHANED, NOT_ORPHANED + "," + NOT_ORPHANED));
    }

    static Stream<Arguments> instantFields() {
        return Stream.of(
                Arguments.of("runtime_started_at", RUNTIME_STARTED_AT,
                        "\"runtime_started_at\":\"2026-10-09T08:15:30.123456+00:00\""),
                Arguments.of("orphaned_at", ORPHANED_AT, "\"orphaned_at\":\"2026-10-09T10:00:00.000001+00:00\""),
                Arguments.of("lease_expires_at", NO_EXPIRY, "\"lease_expires_at\":\"2026-10-09T09:16:00+00:00\""));
    }

    static Stream<Arguments> scopeTypes() {
        return Stream.of(
                Arguments.of(ScopeType.PLAN, "plan", true),
                Arguments.of(ScopeType.EPIC, "epic", true),
                Arguments.of(ScopeType.WORKSPACE, "workspace", false),
                Arguments.of(ScopeType.PROJECT, "project", false));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        /**
         * A lease is written by one component and read and written again by another, for example by the runtime
         * that reclaims the lease of a dead holder. If the two spell an instant differently, the reclaim fails or
         * silently changes the record. The reader therefore returns exactly what the writer writes again.
         */
        @Test
        @DisplayName("a document that is read and written again is byte-identical, instants included")
        void byteIdentical() {
            var document = document(PLAN_LEASE, ORPHANED_WORKSPACE_LEASE);

            var written = LeaseCodec.write(LeaseCodec.read(document));

            assertArrayEquals(document, written, () -> new String(written, StandardCharsets.UTF_8));
        }

        @Test
        @DisplayName("every field of a lease is read")
        void fields() {
            var leases = LeaseCodec.read(document(PLAN_LEASE));

            var holder = new HolderInstance(4711, Instant.parse("2026-10-09T08:15:30.123456Z"));
            var owner = new LeaseOwner("pm-mcp-core", ScopeType.PLAN, "lock-manager", holder, null);
            assertEquals(List.of(new LeaseRecord("plan-marshall/pm-mcp-core", owner,
                    Instant.parse("2026-10-09T08:16:00Z"), Instant.parse("2026-10-09T09:16:00.500Z"))), leases);
        }

        @Test
        @DisplayName("a null scope id, a set orphaned_at and a lease without expiry are kept")
        void nullScopeIdAndOrphanedAt() {
            var lease = LeaseCodec.read(document(ORPHANED_WORKSPACE_LEASE)).getFirst();

            assertAll(
                    () -> assertEquals(ScopeType.WORKSPACE, lease.owner().scopeType()),
                    () -> assertNull(lease.owner().scopeId()),
                    () -> assertTrue(lease.owner().isOrphaned()),
                    () -> assertEquals(Instant.parse("2026-10-09T10:00:00.000001Z"), lease.owner().orphanedAt()),
                    () -> assertNull(lease.leaseExpiresAt()));
        }

        @Test
        @DisplayName("a lease without orphaned_at is not orphaned")
        void absentOrphanedAt() {
            var withoutOrphanedAt = PLAN_LEASE.replace("," + NOT_ORPHANED, "");

            var lease = LeaseCodec.read(document(withoutOrphanedAt)).getFirst();

            assertAll(
                    () -> assertFalse(withoutOrphanedAt.contains("orphaned_at")),
                    () -> assertFalse(lease.owner().isOrphaned()));
        }

        @Test
        @DisplayName("a store without leases is an empty list, and the order of the leases is kept")
        void emptyAndOrder() {
            var both = LeaseCodec.read(document(ORPHANED_WORKSPACE_LEASE, PLAN_LEASE));

            assertAll(
                    () -> assertEquals(List.of(), LeaseCodec.read(document())),
                    () -> assertArrayEquals(document(), LeaseCodec.write(List.of())),
                    () -> assertEquals(List.of("slot-1", "plan-marshall/pm-mcp-core"),
                            both.stream().map(LeaseRecord::key).toList()));
        }

        @Test
        @DisplayName("two leases with one key are never written")
        void duplicateKeyNotWritten() {
            var lease = LeaseCodec.read(document(PLAN_LEASE)).getFirst();
            var twice = List.of(lease, lease);

            assertThrows(IllegalArgumentException.class, () -> LeaseCodec.write(twice));
        }
    }

    @Nested
    @DisplayName("one shape per instant")
    class InstantShape {

        /**
         * Each value is a moment or looks like one, and a lenient reader would accept most of them. Accepting one
         * would let the writer change the bytes of the instant on the next write of the store.
         */
        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "\"2026-10-09T10:16:00+02:00\"",
                "\"2026-10-09T08:16:00+00:00\"",
                "\"2026-10-09T08:16:00.000Z\"",
                "\"2026-10-09T08:16:00.5Z\"",
                "\"2026-10-09T08:16:00z\"",
                "\"2026-10-09t08:16:00Z\"",
                "\"2026-10-09T08:16Z\"",
                "\"2026-10-09 08:16:00Z\"",
                "\"2026-10-09T08:16:00\"",
                "\"1791533760\"",
                "\"\"",
                "1791533760",
                "1791533760.5",
                "null" })
        @DisplayName("an instant in another spelling, an epoch number or no instant is unreadable")
        void otherSpelling(String acquiredAt) {
            var lease = PLAN_LEASE.replace(ACQUIRED_AT, "\"acquired_at\":" + acquiredAt);

            var refusal = refusalOf(document(lease));

            assertEquals(Refusal.UNREADABLE, refusal.refusal());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.LeaseCodecTest#instantFields")
        @DisplayName("every instant field refuses another spelling")
        void everyInstantField(String field, String canonical, String otherSpelling) {
            var lease = ORPHANED_WORKSPACE_LEASE.replace(canonical, otherSpelling);

            var refusal = refusalOf(document(lease));

            assertAll(
                    () -> assertEquals(Refusal.UNREADABLE, refusal.refusal()),
                    () -> assertTrue(refusal.getMessage().contains("'" + field + "'"), refusal.getMessage()));
        }
    }

    @Nested
    @DisplayName("format version")
    class FormatVersion {

        @Test
        @DisplayName("a newer format version is refused as newer, whatever else the document holds")
        void newer() {
            var refusal = refusalOf(utf8("{\"slots\":{\"a\":[1,{\"b\":2}]},\"format_version\":2}"));

            assertAll(
                    () -> assertEquals(Refusal.FORMAT_NEWER, refusal.refusal()),
                    () -> assertEquals("format_newer", refusal.refusal().code()),
                    () -> assertEquals(OptionalInt.of(2), refusal.found()));
        }

        @Test
        @DisplayName("an older format version is refused as outdated")
        void outdated() {
            var refusal = refusalOf(utf8("{\"format_version\":0,\"leases\":[]}"));

            assertAll(
                    () -> assertEquals(Refusal.FORMAT_OUTDATED, refusal.refusal()),
                    () -> assertEquals("format_outdated", refusal.refusal().code()),
                    () -> assertEquals(OptionalInt.of(0), refusal.found()));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "{\"leases\":[]}",
                "{\"format_version\":\"1\",\"leases\":[]}",
                "{\"format_version\":1.0,\"leases\":[]}",
                "{\"format_version\":null,\"leases\":[]}",
                "{\"format_version\":1,\"format_version\":1,\"leases\":[]}",
                "{\"format_version\":99999999999,\"leases\":[]}" })
        @DisplayName("a document without one integer format version is unreadable, never an empty store")
        void missingVersion(String document) {
            var refusal = refusalOf(utf8(document));

            assertAll(
                    () -> assertEquals(Refusal.UNREADABLE, refusal.refusal()),
                    () -> assertEquals("unreadable", refusal.refusal().code()),
                    () -> assertEquals(OptionalInt.empty(), refusal.found()));
        }

        @Test
        @DisplayName("the current format version is accepted wherever it stands in the document")
        void versionLast() {
            var leases = LeaseCodec.read(utf8("{\"leases\":[" + PLAN_LEASE + "],\"format_version\":1}"));

            assertEquals(1, leases.size());
        }
    }

    @Nested
    @DisplayName("closed shape")
    class ClosedShape {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.LeaseCodecTest#invalidOwners")
        @DisplayName("an owner outside the schema is unreadable")
        void invalidOwner(String description, String valid, String invalid) {
            var lease = PLAN_LEASE.replace(PLAN_OWNER, PLAN_OWNER.replace(valid, invalid));

            var refusal = refusalOf(document(lease));

            assertEquals(Refusal.UNREADABLE, refusal.refusal(), description);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "",
                "[]",
                "null",
                "{\"format_version\":1}",
                "{\"format_version\":1,\"leases\":{}}",
                "{\"format_version\":1,\"leases\":null}",
                "{\"format_version\":1,\"leases\":[7]}",
                "{\"format_version\":1,\"leases\":[],\"waiting\":[]}",
                "{\"format_version\":1,\"leases\":[],\"leases\":[]}",
                "{\"format_version\":1,\"leases\":[]}{}",
                "{\"format_version\":1,\"leases\":[]} x",
                "{\"format_version\":1,\"leases\":[",
                "{\"format_version\":1,\"leases\":[{\"key\":\"a\"}]}",
                "{\"format_version\":1,\"leases\":[{\"owner\":null}]}" })
        @DisplayName("a document outside the shape is unreadable, never an empty store")
        void invalidDocument(String document) {
            var refusal = refusalOf(utf8(document));

            assertEquals(Refusal.UNREADABLE, refusal.refusal());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = { KEY + ",", "," + ACQUIRED_AT, "," + EXPIRES_AT })
        @DisplayName("a lease without one of its fields is unreadable")
        void missingLeaseField(String field) {
            var lease = PLAN_LEASE.replace(field, "");

            var refusal = refusalOf(document(lease));

            assertEquals(Refusal.UNREADABLE, refusal.refusal());
        }

        @Test
        @DisplayName("a blank key, a key that is no string and an undeclared lease field are unreadable")
        void invalidLease() {
            var blankKey = PLAN_LEASE.replace(KEY, "\"key\":\" \"");
            var numericKey = PLAN_LEASE.replace(KEY, "\"key\":7");
            var undeclared = PLAN_LEASE.replace(KEY, "\"job_id\":\"j\"," + KEY);

            assertAll(
                    () -> assertEquals(Refusal.UNREADABLE, refusalOf(document(blankKey)).refusal()),
                    () -> assertEquals(Refusal.UNREADABLE, refusalOf(document(numericKey)).refusal()),
                    () -> assertEquals(Refusal.UNREADABLE, refusalOf(document(undeclared)).refusal()));
        }

        @Test
        @DisplayName("two leases with one key are unreadable")
        void duplicateKey() {
            var refusal = refusalOf(document(PLAN_LEASE, PLAN_LEASE));

            assertEquals(Refusal.UNREADABLE, refusal.refusal());
        }

        @Test
        @DisplayName("malformed JSON is reported with the place it stops parsing")
        void malformedJson() {
            var refusal = refusalOf(utf8("{\"format_version\":1,\n\"leases\":[not-json]}"));

            assertAll(
                    () -> assertEquals(Refusal.UNREADABLE, refusal.refusal()),
                    () -> assertTrue(refusal.getMessage().contains("line 2"), refusal.getMessage()));
        }
    }

    @Nested
    @DisplayName("records")
    class Records {

        private final HolderInstance holder = new HolderInstance(1, Instant.parse("2026-10-09T08:15:30Z"));

        @Test
        @DisplayName("a holder needs a process id of at least 1 and a start instant")
        void holderInstance() {
            var startedAt = holder.runtimeStartedAt();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new HolderInstance(0, startedAt)),
                    () -> assertThrows(NullPointerException.class, () -> new HolderInstance(1, null)));
        }

        @Test
        @DisplayName("an owner needs a valid project id, and a scope id exactly for a plan and an epic")
        void owner() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LeaseOwner("PM", ScopeType.PROJECT, null, holder, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LeaseOwner(null, ScopeType.PROJECT, null, holder, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LeaseOwner("pm-mcp-core", null, null, holder, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LeaseOwner("pm-mcp-core", ScopeType.PROJECT, null, null, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LeaseOwner("pm-mcp-core", ScopeType.EPIC, null, holder, null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LeaseOwner("pm-mcp-core", ScopeType.EPIC, "Epic", holder, null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LeaseOwner("pm-mcp-core", ScopeType.PROJECT, "a-plan", holder, null)),
                    () -> assertEquals("an-epic",
                            new LeaseOwner("pm-mcp-core", ScopeType.EPIC, "an-epic", holder, null).scopeId()));
        }

        @Test
        @DisplayName("a lease needs a key that is not blank, an owner and the instant of its claim")
        void lease() {
            var owner = new LeaseOwner("pm-mcp-core", ScopeType.PROJECT, null, holder, null);
            var acquiredAt = Instant.parse("2026-10-09T08:16:00Z");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new LeaseRecord("", owner, acquiredAt, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LeaseRecord(null, owner, acquiredAt, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new LeaseRecord("k", null, acquiredAt, null)),
                    () -> assertThrows(NullPointerException.class, () -> new LeaseRecord("k", owner, null, null)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.LeaseCodecTest#scopeTypes")
        @DisplayName("a scope type has the wire name of the schema and is found by it")
        void scopeTypeWireName(ScopeType type, String wireName, boolean hasScopeId) {
            assertAll(
                    () -> assertEquals(wireName, type.wireName()),
                    () -> assertEquals(type, ScopeType.fromWireName(wireName).orElseThrow()),
                    () -> assertEquals(hasScopeId, type.hasScopeId()));
        }

        @Test
        @DisplayName("the scope types are exactly the four of the schema")
        void scopeTypesClosed() {
            assertAll(
                    () -> assertEquals(4, ScopeType.values().length),
                    () -> assertTrue(ScopeType.fromWireName("lessons").isEmpty()));
        }

        @Test
        @DisplayName("a refusal named for its store keeps its reason and the found version")
        void refusalNamesStore() {
            var refusal = refusalOf(utf8("{\"format_version\":3}"));

            var named = refusal.at(Path.of("/base/state/merge-queue.json"));

            assertAll(
                    () -> assertEquals(Refusal.FORMAT_NEWER, named.refusal()),
                    () -> assertEquals(OptionalInt.of(3), named.found()),
                    () -> assertTrue(named.getMessage().startsWith("/base/state/merge-queue.json: "),
                            named.getMessage()));
        }
    }
}
