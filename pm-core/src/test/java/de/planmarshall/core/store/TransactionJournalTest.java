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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.store.TransactionJournal.Mode;
import de.planmarshall.core.store.TransactionJournal.Target;

/**
 * The transaction journal document of a scope (PM-IMPL-7, PM-IMPL-2): written and read in exactly one shape, every
 * refusal a {@link StoreRefusal} naming the journal file, and an append target never without the size it is cut back
 * to.
 */
@DisplayName("Transaction journal")
class TransactionJournalTest {

    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/store-substrate/mcp",
            "transaction-journal.json");
    private static final TransactionId ID = new TransactionId("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");
    private static final TransactionId OTHER_ID = new TransactionId("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6c");
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T08:16:00.123Z");
    private static final StoreDigest STATUS_DIGEST = new StoreDigest(42, "a".repeat(64));
    private static final StoreDigest FINDINGS_DIGEST = new StoreDigest(4711, "0123456789abcdef".repeat(4));
    private static final Target REPLACE = new Target("mcp/status.json", Mode.REPLACE,
            "mcp/status.json.tmp." + ID.value(), OptionalLong.empty(), STATUS_DIGEST);
    private static final Target APPEND = new Target("findings.jsonl", Mode.APPEND,
            "findings.jsonl.append." + ID.value(), OptionalLong.of(4096), FINDINGS_DIGEST);

    /** The document of a plan journal with the given target objects, as a writer of another component writes it. */
    private static byte[] document(String formatVersion, String targets) {
        return ("{\"format_version\":" + formatVersion + ",\"transaction_id\":\"" + ID.value()
                + "\",\"scope_type\":\"plan\",\"scope_id\":\"store-substrate\","
                + "\"created_at\":\"2026-10-10T08:16:00.123Z\",\"targets\":[" + targets + "],\"owed_events\":[]}")
                .getBytes(UTF_8);
    }

    private static String replaceTarget() {
        return "{\"path\":\"mcp/status.json\",\"mode\":\"replace\",\"staged_path\":\"mcp/status.json.tmp."
                + ID.value() + "\",\"size_before\":null,\"new_size\":42,\"new_sha256\":\"" + "a".repeat(64) + "\"}";
    }

    /** @return a name and the owed events field that replaces the empty one of a valid document */
    static Stream<Arguments> owedEventShapes() {
        var recordStart = "{\"event\":\"x\",\"category\":\"store\",\"outcome\":\"succeeded\"";
        return Stream.of(
                Arguments.of("owed events missing", ""),
                Arguments.of("owed events not an array", ",\"owed_events\":{}"),
                Arguments.of("owed event without details", ",\"owed_events\":[" + recordStart + "}]"),
                Arguments.of("owed detail of a boolean",
                        ",\"owed_events\":[" + recordStart + ",\"details\":{\"f\":true}}]"),
                Arguments.of("owed event with an undeclared field",
                        ",\"owed_events\":[" + recordStart + ",\"details\":{},\"note\":1}]"));
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> TransactionJournal.read(FILE, document));
        assertAll(
                () -> assertEquals(outcome, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file()),
                () -> assertTrue(refusal.getMessage().contains(FILE.toString()), "the message names the file"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a journal with a replace and an append target is read back equal, and written again byte for byte")
        void bothModes() {
            var journal = new TransactionJournal(ID, JournalScopeType.PLAN, "store-substrate", CREATED_AT,
                    List.of(REPLACE, APPEND));

            var written = journal.write();
            var read = TransactionJournal.read(FILE, written);

            assertAll(
                    () -> assertEquals(journal, read),
                    () -> assertArrayEquals(written, read.write()));
        }

        @Test
        @DisplayName("a journal is written in the documented field order, a replace with a null size before")
        void writtenShape() {
            var journal = new TransactionJournal(ID, JournalScopeType.PLAN, "store-substrate", CREATED_AT,
                    List.of(REPLACE));

            assertArrayEquals(document("2", replaceTarget()), journal.write());
        }

        @Test
        @DisplayName("a journal that owes audit records reads them back equal, in their order, byte for byte")
        void owedEvents() {
            var owed = List.of(
                    AuditEvent.crossScopeReconciled("queue-entry", "workspace/queue.json", ID.value()),
                    AuditEvent.transactionRolledForward("plan", "store-substrate", ID.value(),
                            List.of("mcp/status.json", "findings.jsonl")),
                    AuditEvent.jsonlTailRepaired(Path.of("/base/findings.jsonl"), 17, "a".repeat(64), ID.value()));
            var journal = new TransactionJournal(ID, JournalScopeType.PLAN, "store-substrate", CREATED_AT,
                    List.of(REPLACE), owed);

            var written = journal.write();
            var read = TransactionJournal.read(FILE, written);

            assertAll(
                    () -> assertEquals(owed, read.owedEvents()),
                    () -> assertEquals(journal, read),
                    () -> assertArrayEquals(written, read.write()));
        }

        @Test
        @DisplayName("an owed record with a detail that is no string, integer or list of strings is rejected")
        void unwritableOwedEvent() {
            var event = new AuditEvent("x", "store", "succeeded", Map.of("flag", true));

            assertThrows(IllegalArgumentException.class, () -> OwedAuditEvents.requireWritable(event));
        }

        @Test
        @DisplayName("an owed list detail with a value that is no string is refused by the write, naming detail and value")
        void unwritableOwedListItem() {
            var event = new AuditEvent("x", "store", "succeeded", Map.of("paths", List.of("mcp/status.json", 4711)));
            var journal = new TransactionJournal(ID, JournalScopeType.PLAN, "store-substrate", CREATED_AT,
                    List.of(REPLACE), List.of(event));

            var refusal = assertThrows(IllegalArgumentException.class, journal::write);

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains("'paths'"), refusal.getMessage()),
                    () -> assertTrue(refusal.getMessage().contains("4711"), refusal.getMessage()),
                    () -> assertFalse(refusal.getMessage().contains("%s"), refusal.getMessage()));
        }

        @Test
        @DisplayName("a workspace journal writes and reads a null scope id")
        void workspace() {
            var journal = new TransactionJournal(ID, JournalScopeType.WORKSPACE, null, CREATED_AT, List.of(APPEND));

            var read = TransactionJournal.read(FILE, journal.write());

            assertEquals(journal, read);
        }
    }

    /** The matched pair of PM-WATCH-STORE-49: the size before is required of an append target and of nothing else. */
    @Nested
    @DisplayName("size before")
    class SizeBefore {

        @Test
        @DisplayName("an append target without a size before is rejected when the record is built")
        void appendRecordRejected() {
            var empty = OptionalLong.empty();
            var staged = "findings.jsonl.append." + ID.value();

            assertThrows(IllegalArgumentException.class,
                    () -> new Target("findings.jsonl", Mode.APPEND, staged, empty, FINDINGS_DIGEST));
        }

        @ParameterizedTest(name = "size_before {0}")
        @ValueSource(strings = {"null", "absent"})
        @DisplayName("an append target without an integer size before is refused when the document is read")
        void appendDocumentRefused(String sizeBefore) {
            var field = "absent".equals(sizeBefore) ? "" : "\"size_before\":null,";
            var target = "{\"path\":\"findings.jsonl\",\"mode\":\"append\",\"staged_path\":\"findings.jsonl.append."
                    + ID.value() + "\"," + field + "\"new_size\":4711,\"new_sha256\":\"" + "a".repeat(64) + "\"}";

            assertRefused(Outcome.UNREADABLE, document("2", target));
        }

        @Test
        @DisplayName("a replace target without a size before is accepted, as a record and as a document")
        void replaceAccepted() {
            var withoutField = "{\"path\":\"mcp/status.json\",\"mode\":\"replace\",\"staged_path\":\"mcp/status.json.tmp."
                    + ID.value() + "\",\"new_size\":42,\"new_sha256\":\"" + "a".repeat(64) + "\"}";

            var read = TransactionJournal.read(FILE, document("2", withoutField));

            assertAll(
                    () -> assertEquals(OptionalLong.empty(), REPLACE.sizeBefore()),
                    () -> assertEquals(OptionalLong.empty(), read.targets().getFirst().sizeBefore()),
                    () -> assertEquals(Mode.REPLACE, read.targets().getFirst().mode()));
        }

        @Test
        @DisplayName("a negative size before is rejected")
        void negative() {
            var negative = OptionalLong.of(-1);

            assertThrows(IllegalArgumentException.class,
                    () -> new Target("findings.jsonl", Mode.APPEND, "staged", negative, FINDINGS_DIGEST));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a journal without a target is rejected as a record and refused as a document")
        void emptyTargets() {
            List<Target> none = List.of();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new TransactionJournal(ID, JournalScopeType.PLAN, "store-substrate", CREATED_AT,
                                    none)),
                    () -> assertRefused(Outcome.UNREADABLE, document("2", "")));
        }

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"3, FORMAT_NEWER", "1, FORMAT_OUTDATED"})
        @DisplayName("a document of another format version is refused as such, with the version found and current")
        void otherVersion(int version, Outcome outcome) {
            var refusal = assertRefused(outcome, document(Integer.toString(version), replaceTarget()));

            assertAll(
                    () -> assertEquals(OptionalInt.of(version), refusal.found()),
                    () -> assertEquals(TransactionJournal.FORMAT_VERSION, refusal.current()));
        }

        @Test
        @DisplayName("an undeclared field is refused")
        void undeclaredField() {
            var target = replaceTarget().replace("}", ",\"note\":\"x\"}");

            assertRefused(Outcome.UNREADABLE, document("2", target));
        }

        @Test
        @DisplayName("a duplicate field is refused")
        void duplicateField() {
            var target = replaceTarget().replace("\"mode\":\"replace\",", "\"mode\":\"replace\",\"mode\":\"replace\",");

            assertRefused(Outcome.UNREADABLE, document("2", target));
        }

        @Test
        @DisplayName("an instant with an offset is refused, although it names the same moment")
        void instantWithOffset() {
            var withOffset = new String(document("2", replaceTarget()), UTF_8)
                    .replace("2026-10-10T08:16:00.123Z", "2026-10-10T10:16:00.123+02:00").getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, withOffset);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0192a3b4-c5d6-4e8f-9a0b-1c2d3e4f5a6b", "0192A3B4-C5D6-7E8F-9A0B-1C2D3E4F5A6B",
                "0192a3b4-c5d6-7e8f-ca0b-1c2d3e4f5a6b", "not-an-id"})
        @DisplayName("a transaction id that is not a lower-case UUID of version 7 is refused")
        void notVersion7(String transactionId) {
            var document = new String(document("2", replaceTarget()), UTF_8).replace(ID.value(), transactionId)
                    .getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, document);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                unknown scope type   | "scope_type":"plan"          | "scope_type":"project"
                unknown mode         | "mode":"replace"             | "mode":"rename"
                missing scope id     | "scope_id":"store-substrate" | "scope_id":null
                scope id as number   | "scope_id":"store-substrate" | "scope_id":1
                size as string       | "new_size":42                | "new_size":"42"
                short digest         | "new_sha256":"aaaa           | "new_sha256":"a
                empty path           | "path":"mcp/status.json"     | "path":""
                """)
        @DisplayName("a field outside its one shape is refused")
        void fieldShape(String name, String valid, String invalid) {
            var document = new String(document("2", replaceTarget()), UTF_8).replace(valid, invalid)
                    .getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, document);
        }

        /**
         * A recovery moves the staged path of a target over its store, so a journal is read only when every staged
         * path is the staged file of its own store in this transaction, and every path is relative and normalized.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                staged path of another store       | mcp/status.json        | mcp/references.json.tmp.{id}
                staged path of another transaction | mcp/status.json        | mcp/status.json.tmp.{other}
                suffix of an append for a replace  | mcp/status.json        | mcp/status.json.append.{id}
                absolute path                      | /mcp/status.json       | /mcp/status.json.tmp.{id}
                parent segment in the path         | ../other/status.json   | ../other/status.json.tmp.{id}
                parent segment in the staged path  | mcp/status.json        | mcp/../mcp/status.json.tmp.{id}
                current segment in the path        | ./mcp/status.json      | ./mcp/status.json.tmp.{id}
                """)
        @DisplayName("a target whose staged path is not the staged file of its store in this transaction is refused")
        void foreignStagedPath(String name, String path, String stagedPath) {
            var target = replaceTarget().replace("\"path\":\"mcp/status.json\"", "\"path\":\"" + path + "\"")
                    .replace("\"staged_path\":\"mcp/status.json.tmp." + ID.value() + "\"",
                            "\"staged_path\":\"" + stagedPath.replace("{id}", ID.value())
                                    .replace("{other}", OTHER_ID.value()) + "\"");

            var refusal = assertRefused(Outcome.UNREADABLE, document("2", target));

            assertTrue(refusal.getMessage().contains("staged") || refusal.getMessage().contains("path"),
                    "the refusal names the path: " + refusal.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.TransactionJournalTest#owedEventShapes")
        @DisplayName("owed audit records outside their one shape are refused")
        void owedEventShape(String name, String owedEvents) {
            var document = new String(document("2", replaceTarget()), UTF_8)
                    .replace(",\"owed_events\":[]", owedEvents).getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, document);
        }

        @Test
        @DisplayName("a missing required field is refused")
        void missingField() {
            var target = replaceTarget().replace(",\"new_size\":42", "");

            assertRefused(Outcome.UNREADABLE, document("2", target));
        }

        @Test
        @DisplayName("content that is not JSON is refused")
        void notJson() {
            assertRefused(Outcome.UNREADABLE, "{\"format_version\":1,".getBytes(UTF_8));
        }

        @Test
        @DisplayName("a workspace journal with a scope id is rejected as a record")
        void workspaceWithScopeId() {
            var targets = List.of(APPEND);

            assertThrows(IllegalArgumentException.class,
                    () -> new TransactionJournal(ID, JournalScopeType.WORKSPACE, "store-substrate", CREATED_AT,
                            targets));
        }
    }

    @Nested
    @DisplayName("transaction id")
    class TransactionIds {

        @Test
        @DisplayName("a generated id is a lower-case UUID of version 7 carrying the milliseconds of the clock")
        void generated() {
            var clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

            var id = TransactionId.generate(clock);

            var timestamp = Long.parseLong(id.value().substring(0, 8) + id.value().substring(9, 13), 16);
            assertAll(
                    () -> assertEquals(CREATED_AT.toEpochMilli(), timestamp),
                    () -> assertEquals(id, new TransactionId(id.value())),
                    () -> assertEquals(id.value(), id.toString()));
        }

        @Test
        @DisplayName("two ids of the same instant differ")
        void unique() {
            var clock = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

            assertNotEquals(TransactionId.generate(clock), TransactionId.generate(clock));
        }

        @Test
        @DisplayName("the id is found as the last name segment of a staged file and nowhere else")
        void suffix() {
            assertAll(
                    () -> assertEquals(Optional.of(ID), TransactionId.ofSuffix("status.json.tmp." + ID.value())),
                    () -> assertEquals(Optional.of(ID),
                            TransactionId.ofSuffix("findings.jsonl.append." + ID.value())),
                    () -> assertEquals(Optional.empty(), TransactionId.ofSuffix(ID.value())),
                    () -> assertEquals(Optional.empty(), TransactionId.ofSuffix("status.json")),
                    () -> assertEquals(Optional.empty(),
                            TransactionId.ofSuffix("status.json.tmp." + ID.value().toUpperCase())),
                    () -> assertEquals(Optional.empty(),
                            TransactionId.ofSuffix("status.json.tmp." + ID.value() + ".bak")),
                    () -> assertEquals(Optional.empty(),
                            TransactionId.ofSuffix("status.json-" + ID.value())));
        }
    }

    @Nested
    @DisplayName("store digest")
    class StoreDigests {

        @Test
        @DisplayName("the digest of content is its size and lower-case SHA-256, and matches that content only")
        void ofContent() {
            var content = "abc".getBytes(UTF_8);

            var digest = StoreDigest.of(content);

            assertAll(
                    () -> assertEquals(new StoreDigest(3,
                            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), digest),
                    () -> assertTrue(digest.matches(content)),
                    () -> assertFalse(digest.matches("abd".getBytes(UTF_8))),
                    () -> assertFalse(digest.matches("abcd".getBytes(UTF_8))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", "abc", ""})
        @DisplayName("a SHA-256 that is not 64 lower-case hexadecimal digits is rejected")
        void invalidSha(String sha256) {
            assertThrows(IllegalArgumentException.class, () -> new StoreDigest(3, sha256));
        }

        @Test
        @DisplayName("a negative size is rejected")
        void negativeSize() {
            var sha256 = "a".repeat(64);

            assertThrows(IllegalArgumentException.class, () -> new StoreDigest(-1, sha256));
        }
    }
}
