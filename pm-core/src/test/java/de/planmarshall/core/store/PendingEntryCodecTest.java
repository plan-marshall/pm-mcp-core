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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;
import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The pending entry record (PM-CLIENT-1, PM-IMPL-2): written in the order of its schema, read back equal, its raw
 * members {@code facts}, {@code source} and {@code options} written back byte for byte, and every other shape refused
 * as a {@link StoreRefusal} naming the file. The member names, the raw members, the call frame, the condition overlay
 * and the refusal tables are wire contracts; the entry id, the requested id, the hint and the instants are generated.
 */
@EnableGeneratorController
@DisplayName("Pending entry codec")
class PendingEntryCodecTest {

    // Fixture path: the file a refusal names.
    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/pending/ent-0123456789abcdef.json");

    /** Raw members spelled with spaces, newlines and an order no writer of this codec would choose. */
    private static final String FACTS = "{ \"setup\" : {\"z\":1, \"a\" : [ 2,1 ]} ,\n \"candidates\":[] }";
    private static final String SOURCE = "{\"kind\":\"issue\" , \"ref\":\"42\",\"payload\":{\"title\":\"T\"}}";
    private static final String OPTIONS = "{ \"posture\":\"standard\" }";
    private static final String FRAME = "{\"caller_workflow_id\":\"bootstrap-plan\",\"caller_state_id\":"
            + "\"start.choose\",\"subworkflow_id\":\"source-issue\","
            + "\"entry\":\"screen\",\"bound_args\":{\"ref\":\"42\"},\"pushed_at\":\"2026-10-13T08:01:00Z\"}";
    private static final String CONDITION = "{\"kind\":\"precondition_unmet\",\"remedy_attempts\":0,"
            + "\"integrity\":null,\"facts\":{ \"reason\" : \"config_dirty\" },\"raised_at\":\"2026-10-13T08:02:00Z\"}";

    private String entryId;
    private String requestedId;
    private String hint;
    private String epicId;
    private Instant createdAt;
    private Instant updatedAt;

    @BeforeEach
    void generate() {
        entryId = "ent-%016x".formatted(Generators.longs(0, Long.MAX_VALUE).next());
        requestedId = lowerName();
        hint = Generators.letterStrings(1, 20).next() + ' ' + Generators.letterStrings(1, 20).next();
        epicId = "epic-" + lowerName();
        // After the instants of the refusal table, so that its earlier update lies before the creation.
        createdAt = Instant.ofEpochSecond(Generators.longs(1_800_000_000L, 1_900_000_000L).next());
        updatedAt = createdAt.plusSeconds(Generators.longs(0, 100_000L).next());
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
    }

    /** @return the members of a plan entry, each with its JSON value, in schema order */
    private Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("entry_id", quoted(entryId));
        members.put("kind", "\"plan\"");
        members.put("requested_id", quoted(requestedId));
        members.put("hint", quoted(hint));
        members.put("from_epic", "null");
        members.put("created_at", quoted(createdAt.toString()));
        members.put("updated_at", quoted(updatedAt.toString()));
        members.put("bootstrap", bootstrap(bootstrapMembers()));
        return members;
    }

    private static String quoted(String value) {
        return "\"" + value + "\"";
    }

    private static Map<String, String> bootstrapMembers() {
        var members = new LinkedHashMap<String, String>();
        members.put("state", "\"source-issue.screen\"");
        members.put("call_stack", "[" + FRAME + "]");
        members.put("facts", FACTS);
        members.put("source", SOURCE);
        members.put("options", OPTIONS);
        members.put("pending_condition", CONDITION);
        members.put("last_refusal", "\"config_dirty\"");
        return members;
    }

    private static String bootstrap(Map<String, String> members) {
        return object(members);
    }

    private static String object(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static byte[] document(Map<String, String> members) {
        return object(members).getBytes(UTF_8);
    }

    /** @return every required member of the record besides the format version */
    static Stream<String> requiredMembers() {
        return PendingEntryCodec.MEMBERS.stream().filter(member -> !"format_version".equals(member));
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> PendingEntryCodec.read(FILE, document),
                "the document should be refused");
        assertAll(
                () -> assertEquals(outcome, refusal.outcome(), "the refusal should carry its outcome"),
                () -> assertEquals(FILE, refusal.file(), "the refusal should name the file"),
                () -> assertTrue(refusal.getMessage().contains(FILE.toString()),
                        "the message should name the file"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a record in the canonical form is read and written again byte for byte, raw members included")
        void byteForByte() {
            var document = document(members());

            var entry = PendingEntryCodec.read(FILE, document);

            assertAll(
                    () -> assertArrayEquals(document, PendingEntryCodec.write(entry),
                            "the record should be written byte for byte"),
                    () -> assertEquals(FACTS, entry.bootstrap().facts(), "the raw facts should be kept verbatim"),
                    () -> assertEquals(SOURCE, entry.bootstrap().source(), "the raw source should be kept verbatim"),
                    () -> assertEquals(OPTIONS, entry.bootstrap().options(),
                            "the raw options should be kept verbatim"));
        }

        @Test
        @DisplayName("the typed members are read: id, kind, instants, call frame and condition overlay")
        void typedMembers() {
            var entry = PendingEntryCodec.read(FILE, document(members()));

            assertAll(
                    () -> assertEquals(new EntryId(entryId), entry.entryId(), "the entry id should be read"),
                    () -> assertEquals(Kind.PLAN, entry.kind(), "the kind should be read"),
                    () -> assertEquals(requestedId, entry.requestedId(), "the requested id should be read"),
                    () -> assertEquals(hint, entry.hint(), "the hint should be read with its space"),
                    () -> assertEquals(createdAt, entry.createdAt(), "the creation instant should be read"),
                    () -> assertEquals("source-issue", entry.bootstrap().callStack().getFirst().subworkflowId(),
                            "the frame's subworkflow should be read"),
                    () -> assertEquals("screen", entry.bootstrap().callStack().getFirst().entry(),
                            "the frame's entry should be read"),
                    () -> assertEquals("precondition_unmet", entry.bootstrap().condition().orElseThrow().kind(),
                            "the condition kind should be read"),
                    () -> assertEquals("config_dirty", entry.bootstrap().lastRefusal(),
                            "the last refusal should be read"));
        }

        @Test
        @DisplayName("a plan the server starts for an epic row has no source, and round-trips")
        void serverStarted() {
            var members = members();
            members.put("from_epic", quoted(epicId));
            members.put("hint", "null");
            var inner = bootstrapMembers();
            inner.put("source", "null");
            inner.put("call_stack", "[]");
            inner.put("pending_condition", "null");
            inner.put("last_refusal", "null");
            members.put("bootstrap", bootstrap(inner));
            var document = document(members);

            var entry = PendingEntryCodec.read(FILE, document);

            assertAll(
                    () -> assertEquals(epicId, entry.fromEpic(), "the epic of the entry should be read"),
                    () -> assertNull(entry.bootstrap().source(), "a server-started plan has no source"),
                    () -> assertArrayEquals(document, PendingEntryCodec.write(entry),
                            "the entry should round-trip byte for byte"));
        }

        @Test
        @DisplayName("the members are those of the record before this deliverable, less the removed session member")
        void membersWithoutSession() {
            // The work package removes exactly the member `session` of the pending entry record; every other member
            // stays for the work package start-source-intake, which gives the record its directory form.
            assertEquals(PendingEntryCodec.MEMBERS, List.of("format_version", "entry_id", "kind", "requested_id", "hint", "from_epic", "created_at",
                    "updated_at", "bootstrap"), "the members should be the schema's members");
        }

        @Test
        @DisplayName("a stored record without a session member is written and read back byte for byte")
        void withoutSession() {
            var members = members();
            members.remove("session");
            var document = document(members);

            var entry = PendingEntryCodec.read(FILE, document);

            assertArrayEquals(document, PendingEntryCodec.write(entry), "the record should round-trip unchanged");
        }

        @Test
        @DisplayName("a stored record that still carries the removed session member is refused as undeclared")
        void sessionMemberIsRefused() {
            var members = members();
            members.remove("session");
            members.put("session", "{\"format_version\":1}");

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains("session"), "the refusal should name the session member");
        }

        @Test
        @DisplayName("a record built in code is written and read back equal")
        void builtRecord() {
            var entry = PendingEntryRecord.builder()
                    .entryId(new EntryId("ent-%08x".formatted(Generators.integers(0, Integer.MAX_VALUE).next())))
                    .kind(Kind.EPIC).createdAt(createdAt).updatedAt(updatedAt)
                    .bootstrap(Bootstrap.builder().state(lowerName() + "." + lowerName()).callStack(List.of())
                            .facts("{}").options("{}").build())
                    .build();

            assertEquals(entry, PendingEntryCodec.read(FILE, PendingEntryCodec.write(entry)),
                    "the built record should round-trip");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a record of another format version is refused as such, with the version found")
        void otherVersion(int version, Outcome outcome) {
            var members = members();
            members.put("format_version", Integer.toString(version));

            assertEquals(OptionalInt.of(version), assertRefused(outcome, document(members)).found(),
                    "the refusal should carry the version found");
        }

        @ParameterizedTest(name = "without {0}")
        @MethodSource("de.planmarshall.core.store.PendingEntryCodecTest#requiredMembers")
        @DisplayName("a record without a required member is refused naming it")
        void missingMember(String member) {
            var members = members();
            members.remove(member);

            assertTrue(assertRefused(Outcome.UNREADABLE, document(members)).getMessage().contains(member),
                    "the refusal should name the missing member");
        }

        @ParameterizedTest(name = "bootstrap without {0}")
        @ValueSource(strings = {"state", "call_stack", "facts", "source", "options", "pending_condition", "last_refusal"})
        @DisplayName("a bootstrap without a required member is refused naming it")
        void missingBootstrapMember(String member) {
            var inner = bootstrapMembers();
            inner.remove(member);
            var members = members();
            members.put("bootstrap", bootstrap(inner));

            assertTrue(assertRefused(Outcome.UNREADABLE, document(members)).getMessage().contains(member),
                    "the refusal should name the missing member");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                undeclared member              | note             | 1
                entry id of another form       | entry_id         | "entry-1"
                entry id too short             | entry_id         | "ent-0123"
                unknown kind                   | kind             | "lesson"
                from_epic not an id            | from_epic        | "An Epic"
                epic naming a parent epic      | kind             | "epic"
                instant with an offset         | created_at       | "2026-10-13T10:00:00+02:00"
                updated before created         | updated_at       | "2026-10-13T07:00:00Z"
                bootstrap as an array          | bootstrap        | []
                """)
        @DisplayName("a member outside its one shape is refused (the shapes are the schema's contract)")
        void memberShape(String name, String member, String value) {
            var members = members();
            members.put(member, value);
            if ("epic naming a parent epic".equals(name)) {
                members.put("from_epic", quoted(epicId));
            }

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                undeclared bootstrap member | note              | 1
                blank state                 | state             | " "
                facts as an array           | facts             | []
                source as a string          | source            | "issue"
                options as null             | options           | null
                refusal of another form     | last_refusal      | "Config-Dirty"
                unknown condition kind      | pending_condition | {"kind":"x","remedy_attempts":0,"integrity":null,"facts":{},"raised_at":"2026-10-13T08:02:00Z"}
                frame with a boolean argument | call_stack        | [{"caller_workflow_id":"a","caller_state_id":"b","subworkflow_id":"c","entry":"d","bound_args":{"x":true},"pushed_at":"2026-10-13T08:01:00Z"}]
                frame without its entry     | call_stack        | [{"caller_workflow_id":"a","caller_state_id":"b","subworkflow_id":"c","bound_args":{},"pushed_at":"2026-10-13T08:01:00Z"}]
                frame with a numeric entry  | call_stack        | [{"caller_workflow_id":"a","caller_state_id":"b","subworkflow_id":"c","entry":1,"bound_args":{},"pushed_at":"2026-10-13T08:01:00Z"}]
                """)
        @DisplayName("a bootstrap member outside its one shape is refused (the shapes are the schema's contract)")
        void bootstrapShape(String name, String member, String value) {
            var inner = bootstrapMembers();
            inner.put(member, value);
            var members = members();
            members.put("bootstrap", bootstrap(inner));

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @Test
        @DisplayName("a requested id over 64 and a hint over 4000 characters are refused")
        void lengths() {
            var longId = members();
            longId.put("requested_id", quoted(lowerLetters(PendingEntryRecord.MAX_REQUESTED_ID + 1)));
            var longHint = members();
            longHint.put("hint", quoted(lowerLetters(PendingEntryRecord.MAX_HINT + 1)));
            var limits = members();
            limits.put("requested_id", quoted(lowerLetters(PendingEntryRecord.MAX_REQUESTED_ID)));
            limits.put("hint", quoted(lowerLetters(PendingEntryRecord.MAX_HINT)));

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, document(longId)),
                    () -> assertRefused(Outcome.UNREADABLE, document(longHint)),
                    () -> assertEquals(PendingEntryRecord.MAX_HINT,
                            PendingEntryCodec.read(FILE, document(limits)).hint().length(),
                            "a hint at the limit should be accepted"));
        }

        private static String lowerLetters(int length) {
            return Generators.letterStrings(length, length).next().toLowerCase(Locale.ROOT);
        }

        @Test
        @DisplayName("a call stack deeper than five frames is refused")
        void deepCallStack() {
            var inner = bootstrapMembers();
            inner.put("call_stack", "[" + String.join(",", FRAME, FRAME, FRAME, FRAME, FRAME, FRAME) + "]");
            var members = members();
            members.put("bootstrap", bootstrap(inner));

            assertRefused(Outcome.UNREADABLE, document(members));
        }
    }

    @Test
    @DisplayName("a record is older than an instant exactly when it was created before it")
    void createdBefore() {
        var entry = PendingEntryCodec.read(FILE, document(members()));
        var created = entry.createdAt();

        assertAll(
                () -> assertTrue(entry.createdBefore(created.plusSeconds(1)),
                        "a later instant should be after the creation"),
                () -> assertFalse(entry.createdBefore(created), "the creation instant should not count"),
                () -> assertFalse(entry.createdBefore(created.minusSeconds(1)),
                        "an earlier instant should not count"));
    }
}
