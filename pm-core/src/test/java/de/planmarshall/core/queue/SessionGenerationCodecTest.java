/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.queue.SessionGeneration.DeliveredSkill;
import de.planmarshall.core.queue.SessionGeneration.Fence;
import de.planmarshall.core.queue.SessionGeneration.Scope;
import de.planmarshall.core.queue.SessionGeneration.Workdir;
import de.planmarshall.core.store.ScopeSessionRow.Client;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The session generation record {@code queue/session-generations.json} (PM-WORK-5, PM-IMPL-2): one format version,
 * one closed shape per entry, an entry key that is a generation id, and what fenced a generation derived from the
 * generation of the same key issued at its fence.
 */
@DisplayName("Session generation codec")
class SessionGenerationCodecTest {

    private static final Path STORE = Path.of("/base/projects/pm-mcp-core/queue/session-generations.json");
    private static final String KEY = "a".repeat(64);
    private static final String OTHER_KEY = "b".repeat(64);
    private static final GenerationId FIRST = new GenerationId("sg-0000000000000001");
    private static final GenerationId SECOND = new GenerationId("sg-0000000000000002");
    private static final Instant ISSUED = Instant.parse("2026-10-13T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-10-13T09:30:00.123456Z");

    private static SessionGeneration current(GenerationId id, String key, GenerationCause cause, Instant issuedAt) {
        return SessionGeneration.issued(id, key, Client.CLAUDE, "session-1", cause, issuedAt, true);
    }

    private static List<SessionGeneration> read(byte[] document) {
        return SessionGenerationCodec.read(STORE, document);
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** The members of a current entry as they are written, each with its JSON value. */
    private static Map<String, String> entryMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("session_key", "\"" + KEY + "\"");
        members.put("client", "\"claude\"");
        members.put("client_session_id", "\"session-1\"");
        members.put("scope", "{\"type\":\"plan\",\"id\":\"lock-manager\"}");
        members.put("workdir", "{\"kind\":\"plan_worktree\",\"plan_id\":\"lock-manager\"}");
        members.put("issued_at", "\"2026-10-13T08:00:00Z\"");
        members.put("issued_by", "\"relay_start\"");
        members.put("session_tasks", "true");
        members.put("offers_suspended", "false");
        members.put("delivered_skills", "[]");
        members.put("fenced_at", "null");
        return members;
    }

    private static String entry(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static byte[] document(String key, Map<String, String> members) {
        return utf8("{\"format_version\":1,\"generations\":{\"" + key + "\":" + entry(members) + "}}");
    }

    private static StoreRefusal refusalOf(byte[] document) {
        return assertThrows(StoreRefusal.class, () -> read(document));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("every member of an entry is written and read back equal, in the order of the record")
        void everyMember() {
            var assigned = current(FIRST, KEY, GenerationCause.NEW_SESSION, ISSUED).toBuilder()
                    .clientSessionId(null)
                    .scope(new Scope(Scope.Type.PLAN, "lock-manager"))
                    .workdir(new Workdir(Workdir.Kind.PLAN_WORKTREE, "lock-manager"))
                    .deliveredSkills(List.of(new DeliveredSkill("skill://pm/plan-marshall/execute", "c".repeat(64),
                            LATER)))
                    .offersSuspended(true)
                    .fence(new Fence(LATER, false))
                    .build();
            var workspace = current(SECOND, OTHER_KEY, GenerationCause.RELAY_START, LATER).withScope(
                    new Scope(Scope.Type.WORKSPACE, null), new Workdir(Workdir.Kind.PROJECT_CHECKOUT, null));

            var document = SessionGenerationCodec.write(List.of(workspace, assigned));

            assertEquals(List.of(workspace, assigned), read(document));
        }

        @Test
        @DisplayName("an entry is written in the one shape of the record, members in schema order")
        void writtenShape() {
            var generation = current(FIRST, KEY, GenerationCause.RELAY_START, ISSUED).withScope(
                    new Scope(Scope.Type.PLAN, "lock-manager"), new Workdir(Workdir.Kind.PLAN_WORKTREE,
                            "lock-manager"));

            assertArrayEquals(document(FIRST.value(), entryMembers()),
                    SessionGenerationCodec.write(List.of(generation)));
        }

        @Test
        @DisplayName("an empty record is written and read back as no generation")
        void emptyRecord() {
            var document = SessionGenerationCodec.write(List.of());

            assertAll(
                    () -> assertArrayEquals(utf8("{\"format_version\":1,\"generations\":{}}"), document),
                    () -> assertEquals(List.of(), read(document)));
        }

        @Test
        @DisplayName("two entries of one generation id are refused by the writer")
        void duplicateIdRefusedByWriter() {
            var generation = current(FIRST, KEY, GenerationCause.ENTER, ISSUED);
            var entries = List.of(generation, generation);

            assertThrows(IllegalArgumentException.class, () -> SessionGenerationCodec.write(entries));
        }
    }

    @Nested
    @DisplayName("what fenced a generation")
    class FenceCause {

        @ParameterizedTest(name = "fenced by {0}")
        @ValueSource(strings = {"relay_start", "new_session", "enter"})
        @DisplayName("a fenced entry is fenced by an enter exactly when its key's entry issued at its fence is one")
        void derivedFromTheFencingEntry(String issuedBy) {
            var cause = GenerationCause.fromWireName(issuedBy).orElseThrow();
            var fenced = current(FIRST, KEY, GenerationCause.RELAY_START, ISSUED).fencedAt(LATER, false);
            var fencing = current(SECOND, KEY, cause, LATER);

            var read = read(SessionGenerationCodec.write(List.of(fenced, fencing)));

            assertAll(
                    () -> assertEquals(new Fence(LATER, cause == GenerationCause.ENTER), read.getFirst().fence()),
                    () -> assertFalse(read.get(1).isFenced()));
        }

        @Test
        @DisplayName("an enter of another session key at the fence instant does not make the entry superseded")
        void otherKeyNeverFences() {
            var fenced = current(FIRST, KEY, GenerationCause.RELAY_START, ISSUED).fencedAt(LATER, false);
            var otherSession = current(SECOND, OTHER_KEY, GenerationCause.ENTER, LATER);

            var read = read(SessionGenerationCodec.write(List.of(fenced, otherSession)));

            assertFalse(read.getFirst().fence().byEnter());
        }

        @Test
        @DisplayName("a fenced entry whose fencing entry is gone reads as fenced, not superseded")
        void fencingEntryAbsent() {
            var fenced = current(FIRST, KEY, GenerationCause.ENTER, ISSUED).fencedAt(LATER, true);

            var read = read(SessionGenerationCodec.write(List.of(fenced)));

            assertEquals(new Fence(LATER, false), read.getFirst().fence());
        }
    }

    @Nested
    @DisplayName("refusal")
    class Refusal {

        @ParameterizedTest(name = "format_version {0}")
        @ValueSource(ints = {0, 2})
        @DisplayName("a record in another format version is refused as outdated or newer")
        void otherVersion(int version) {
            var refusal = refusalOf(utf8("{\"format_version\":" + version + ",\"generations\":{}}"));

            assertAll(
                    () -> assertEquals(version < 1 ? Outcome.FORMAT_OUTDATED : Outcome.FORMAT_NEWER,
                            refusal.outcome()),
                    () -> assertEquals(OptionalInt.of(version), refusal.found()),
                    () -> assertEquals(STORE, refusal.file()));
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"session_key", "client", "client_session_id", "scope", "workdir", "issued_at",
                "issued_by", "session_tasks", "offers_suspended", "delivered_skills", "fenced_at"})
        @DisplayName("an entry without a required member is refused naming it")
        void missingMember(String member) {
            var members = entryMembers();
            members.remove(member);

            var refusal = refusalOf(document(FIRST.value(), members));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertTrue(refusal.getMessage().contains(member), refusal.getMessage()));
        }

        static Stream<Arguments> invalidMembers() {
            return Stream.of(
                    Arguments.of("undeclared member", "worker", "{}"),
                    Arguments.of("session key not a SHA-256", "session_key", "\"ABC\""),
                    Arguments.of("undeclared client", "client", "\"emacs\""),
                    Arguments.of("client session id with a blank", "client_session_id", "\"a b\""),
                    Arguments.of("undeclared cause", "issued_by", "\"restart\""),
                    Arguments.of("instant with an offset", "issued_at", "\"2026-10-13T10:00:00+02:00\""),
                    Arguments.of("session tasks not a boolean", "session_tasks", "\"true\""),
                    Arguments.of("undeclared scope type", "scope", "{\"type\":\"project\",\"id\":null}"),
                    Arguments.of("plan scope without id", "scope", "{\"type\":\"plan\",\"id\":null}"),
                    Arguments.of("workspace scope with id", "scope", "{\"type\":\"workspace\",\"id\":\"x-y\"}"),
                    Arguments.of("undeclared scope member", "scope",
                            "{\"type\":\"plan\",\"id\":\"lock-manager\",\"path\":\"/x\"}"),
                    Arguments.of("undeclared workdir kind", "workdir", "{\"kind\":\"home\",\"plan_id\":null}"),
                    Arguments.of("project checkout with plan", "workdir",
                            "{\"kind\":\"project_checkout\",\"plan_id\":\"lock-manager\"}"),
                    Arguments.of("skill outside PM-MCP", "delivered_skills",
                            "[{\"uri\":\"https://x\",\"digest\":\"" + "c".repeat(64)
                                    + "\",\"delivered_at\":\"2026-10-13T08:00:00Z\"}]"),
                    Arguments.of("skill without digest", "delivered_skills",
                            "[{\"uri\":\"skill://pm/x\",\"delivered_at\":\"2026-10-13T08:00:00Z\"}]"),
                    Arguments.of("fenced at not an instant", "fenced_at", "\"yesterday\""));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidMembers")
        @DisplayName("an entry with a member out of its shape is refused as unreadable")
        void invalidMember(String description, String member, String value) {
            var members = entryMembers();
            members.put(member, value);

            assertEquals(Outcome.UNREADABLE, refusalOf(document(FIRST.value(), members)).outcome());
        }

        @Test
        @DisplayName("a working directory without a scope is refused")
        void workdirWithoutScope() {
            var members = entryMembers();
            members.put("scope", "null");

            assertEquals(Outcome.UNREADABLE, refusalOf(document(FIRST.value(), members)).outcome());
        }

        @ParameterizedTest(name = "key {0}")
        @ValueSource(strings = {"l-0000000000000001", "sg-00000000000000zz", "sg-1"})
        @DisplayName("an entry whose key is no session generation id is refused")
        void keyNotAGenerationId(String key) {
            var refusal = refusalOf(document(key, entryMembers()));

            assertAll(
                    () -> assertEquals(Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertTrue(refusal.getMessage().contains(key), refusal.getMessage()));
        }

        @Test
        @DisplayName("a generation id twice in the record is refused")
        void keyTwice() {
            var entry = entry(entryMembers());
            var document = utf8("{\"format_version\":1,\"generations\":{\"" + FIRST + "\":" + entry + ",\""
                    + FIRST + "\":" + entry + "}}");

            assertEquals(Outcome.UNREADABLE, refusalOf(document).outcome());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"{\"format_version\":1}", "{\"format_version\":1,\"generations\":[]}",
                "{\"format_version\":1,\"generations\":{},\"jobs\":{}}", "{\"format_version\":1,\"generations\":{}",
                "{\"format_version\":1,\"generations\":{}} {}"})
        @DisplayName("a record that is not of the one shape is refused as unreadable, never read as empty")
        void documentShape(String document) {
            assertEquals(Outcome.UNREADABLE, refusalOf(utf8(document)).outcome());
        }
    }
}
