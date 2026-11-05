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
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;

/**
 * The codec of the three files of the audit trail (PM-SEC-6, PM-IMPL-2): every shape round trips, is written in one
 * canonical form whose SHA-256 is stable, and is refused for every field it does not declare, every field it misses,
 * another format version and an id or a hash out of its form.
 */
@EnableGeneratorController
@DisplayName("Audit codec")
class AuditCodecTest {

    private static final Path LOG = Path.of("/base/logs/audit.jsonl");
    private static final Path HEAD = Path.of("/base/logs/audit.head");
    private static final Path INDEX = Path.of("/base/logs/audit-index.jsonl");
    private static final String EVENT_ID = "01890a5d-ac96-774b-bcce-b302099a8057";
    private static final String OTHER_EVENT_ID = "01890a5d-ac97-7c4b-9cce-b302099a8058";
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String OTHER_HASH = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210";
    private static final Instant AT = Instant.parse("2026-10-14T09:30:15.250Z");

    private static final AuditHeadRecord HEAD_RECORD = new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 3, EVENT_ID, HASH,
            AuditCodec.GENESIS_HASH, OTHER_HASH);

    private static final AuditIndexEntry INDEX_ENTRY = new AuditIndexEntry(AuditCodec.GENESIS_HASH,
            AuditIndexEntry.rotatedFileName(EVENT_ID), EVENT_ID, OTHER_EVENT_ID, 12, HASH);

    private String scopeId;
    private AuditRecord planRecord;
    private String planLine;

    @BeforeEach
    void createRecord() {
        scopeId = Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT);
        planRecord = AuditRecord.builder()
                .eventId(EVENT_ID)
                .prevHash(HASH)
                .timestamp(AT)
                .category(Category.STORE)
                .event("transaction_rolled_forward")
                .scopeType(Scope.PLAN)
                .scopeId(scopeId)
                .actor("oliver")
                .outcome(Outcome.SUCCEEDED)
                .details(Map.of("transaction_id", OTHER_EVENT_ID, "targets", List.of("a.json", "b.json"), "count", 2,
                        "identity_mismatch", false))
                .build();
        planLine = "{\"format_version\":1,\"event_id\":\"" + EVENT_ID + "\",\"prev_hash\":\""
                + HASH + "\",\"timestamp\":\"2026-10-14T09:30:15.250Z\",\"category\":\"store\","
                + "\"event\":\"transaction_rolled_forward\",\"scope_type\":\"plan\",\"scope_id\":\"" + scopeId + "\","
                + "\"actor\":\"oliver\",\"outcome\":\"succeeded\",\"details\":{\"count\":2,\"identity_mismatch\":false,"
                + "\"targets\":[\"a.json\",\"b.json\"],\"transaction_id\":\"" + OTHER_EVENT_ID + "\"}}";
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] bytes(String text) {
        return text.getBytes(UTF_8);
    }

    @Nested
    @DisplayName("record")
    class Records {

        @Test
        @DisplayName("is written as its canonical line: schema order, details sorted, no whitespace, no newline")
        void canonicalLine() {
            assertEquals(planLine, new String(AuditCodec.writeRecord(planRecord), UTF_8),
                    "the canonical line is written as expected");
        }

        @Test
        @DisplayName("round trips, an integer detail read back as a long")
        void roundTrip() {
            var read = AuditCodec.readRecord(LOG, AuditCodec.writeRecord(planRecord));

            assertAll("a record round trips through its canonical line",
                    () -> assertEquals(planRecord, read, "the record is read back unchanged"),
                    () -> assertEquals(2L, read.details().get("count"), "an integer detail reads back as a long"),
                    () -> assertArrayEquals(AuditCodec.writeRecord(planRecord), AuditCodec.writeRecord(read),
                            "the read record writes the same bytes"));
        }

        @Test
        @DisplayName("a machine-level record without a scope id round trips")
        void machineRecord() {
            var machine = planRecord.withScopeType(Scope.MACHINE).withScopeId(null).withDetails(Map.of());

            assertEquals(machine, AuditCodec.readRecord(LOG, AuditCodec.writeRecord(machine)),
                    "a machine record round trips unchanged");
        }

        @Test
        @DisplayName("a record without the optional scope and details is read with no scope and empty details")
        void optionalFieldsAbsent() {
            var line = "{\"format_version\":1,\"event_id\":\"" + EVENT_ID + "\",\"prev_hash\":\"" + HASH
                    + "\",\"timestamp\":\"2026-10-14T09:30:15.250Z\",\"category\":\"job\",\"event\":\"worker_started\","
                    + "\"actor\":\"claude\",\"outcome\":\"allowed\"}";

            var read = AuditCodec.readRecord(LOG, bytes(line));

            assertAll("optional scope and details are absent",
                    () -> assertNull(read.scopeType(), "the scope type is absent"),
                    () -> assertNull(read.scopeId(), "the scope id is absent"),
                    () -> assertEquals(Map.of(), read.details(), "the details are empty"),
                    () -> assertEquals(Category.JOB, read.category(), "the category is read from the line"),
                    () -> assertEquals(Outcome.ALLOWED, read.outcome(), "the outcome is read from the line"));
        }

        @Test
        @DisplayName("the SHA-256 of the canonical line is stable and is the SHA-256 of its exact bytes")
        void stableHash() {
            var line = AuditCodec.writeRecord(planRecord);

            assertAll("the canonical line hash is stable",
                    () -> assertEquals(sha256(bytes(planLine)), AuditCodec.sha256Hex(line),
                            "the hash is that of the canonical bytes"),
                    () -> assertEquals(AuditCodec.sha256Hex(line),
                            AuditCodec.sha256Hex(AuditCodec.writeRecord(planRecord)),
                            "writing the record again hashes the same"),
                    () -> assertEquals(AuditCodec.GENESIS_HASH, "0".repeat(64),
                            "the genesis hash is sixty-four zeros"));
        }

        @Test
        @DisplayName("is a checkpoint only for the checkpoint event")
        void checkpoint() {
            assertAll("only the checkpoint event is a checkpoint",
                    () -> assertFalse(planRecord.isCheckpoint(), "an ordinary record is no checkpoint"),
                    () -> assertTrue(planRecord.withEvent(AuditRecord.EVENT_CHECKPOINT).isCheckpoint(),
                            "the checkpoint event is a checkpoint"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "\"format_version\":1,\"bogus\":1,",
                "\"format_version\":1,\"event_id\":\"" + EVENT_ID + "\",",
                "\"format_version\":1,\"category\":\"other\",",
                "\"format_version\":1,\"outcome\":\"maybe\",",
                "\"format_version\":1,\"scope_type\":\"lessons\","})
        @DisplayName("a line with an undeclared field, a field twice or an undeclared value is refused as unreadable")
        void undeclared(String prefix) {
            var line = planLine.replace("\"format_version\":1,", prefix);
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "an undeclared field or value is refused");

            assertAll("the refusal is unreadable and names the log",
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal outcome is unreadable"),
                    () -> assertTrue(refusal.getMessage().contains(LOG.toString()), refusal::getMessage));
        }

        @Test
        @DisplayName("a stored line of the entry scope is read as that scope")
        void entryScope() {
            var line = planLine.replace("\"scope_type\":\"plan\",\"scope_id\":\"" + scopeId + "\"",
                    "\"scope_type\":\"entry\",\"scope_id\":\"ent-0123456789abcdef\"");
            var input = bytes(line);

            var readRecord = AuditCodec.readRecord(LOG, input);

            assertAll("an entry scope is read as that scope",
                    () -> assertEquals(AuditRecord.Scope.ENTRY, readRecord.scopeType(),
                            "the scope type is read as entry"),
                    () -> assertEquals("ent-0123456789abcdef", readRecord.scopeId(), "the entry scope id is kept"));
        }

        @Test
        @DisplayName("a stored line of the removed category identity is refused as unreadable")
        void removedIdentityCategory() {
            var line = planLine.replace("\"category\":\"store\"", "\"category\":\"identity\"");
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "the removed category is refused");

            assertAll("the refusal is unreadable and names identity",
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal outcome is unreadable"),
                    () -> assertTrue(refusal.getMessage().contains("identity"), refusal::getMessage));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"event_id", "prev_hash", "timestamp", "category", "event", "actor", "outcome"})
        @DisplayName("a line without a required field is refused as unreadable")
        void missingField(String field) {
            var line = planLine.replaceFirst("\"" + field + "\":(\"[^\"]*\"),", "");
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "a line without a required field is refused");

            assertAll("the field is gone and the refusal names it",
                    () -> assertFalse(line.contains("\"" + field + "\""), "the field was removed"),
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal outcome is unreadable"),
                    () -> assertTrue(refusal.getMessage().contains(field), refusal::getMessage));
        }

        @Test
        @DisplayName("a line of another format version is refused as newer, not as unreadable")
        void otherFormatVersion() {
            var line = planLine.replace("\"format_version\":1", "\"format_version\":2");
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "a line of another version is refused");

            assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, refusal.outcome(),
                    "the refusal outcome is format newer");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "01890a5d-ac96-474b-bcce-b302099a8057",
                "01890A5D-AC96-774B-BCCE-B302099A8057",
                "not-an-id"})
        @DisplayName("an event id that is no lower-case UUID of version 7 is refused")
        void malformedEventId(String eventId) {
            var line = planLine.replace(EVENT_ID, eventId);
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "a malformed event id is refused");

            assertTrue(refusal.getMessage().contains("event_id"), refusal::getMessage);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "0123",
                "0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF0123456789ABCDEF",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdeg"})
        @DisplayName("a prev_hash that is no 64 lower-case hex digits is refused")
        void malformedHash(String hash) {
            var line = planLine.replace(HASH, hash);
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "a malformed prev_hash is refused");

            assertTrue(refusal.getMessage().contains("prev_hash"), refusal::getMessage);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"{\"x\":1.5}", "{\"x\":null}", "{\"x\":{}}", "{\"x\":[1]}", "[]"})
        @DisplayName("details that are no object of strings, integers, booleans or string arrays are refused")
        void malformedDetails(String details) {
            var line = planLine.substring(0, planLine.indexOf("\"details\":")) + "\"details\":" + details + "}";
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readRecord(LOG, input),
                    "malformed details are refused");

            assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                    "the refusal outcome is unreadable");
        }

        @Test
        @DisplayName("a detail of another type is refused when the record is built")
        void unsupportedDetail() {
            var builder = planRecord.toBuilder()
                    .details(Map.of(Generators.letterStrings(1, 5).next(), 1.5));

            assertThrows(IllegalArgumentException.class, builder::build,
                    "an unsupported detail type is refused");
        }

        @Test
        @DisplayName("a blank event or actor is refused when the record is built")
        void blankText() {
            assertAll("a blank event or actor is refused",
                    () -> assertThrows(IllegalArgumentException.class, () -> planRecord.withEvent(" "),
                            "a blank event is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> planRecord.withActor(""),
                            "a blank actor is refused"));
        }
    }

    @Nested
    @DisplayName("head")
    class Heads {

        @Test
        @DisplayName("round trips in its canonical form, the signed content being the same without the hmac")
        void roundTrip() {
            var document = new String(AuditCodec.writeHead(HEAD_RECORD), UTF_8);
            var signed = new String(AuditCodec.signedContent(HEAD_RECORD), UTF_8);

            assertAll("the head round trips in canonical form",
                    () -> assertEquals(HEAD_RECORD, AuditCodec.readHead(HEAD, bytes(document)),
                            "the head is read back unchanged"),
                    () -> assertEquals("{\"format_version\":1,\"file\":\"audit.jsonl\",\"record_count\":3,"
                            + "\"last_event_id\":\"" + EVENT_ID + "\",\"head_hash\":\"" + HASH
                            + "\",\"index_head_hash\":\"" + AuditCodec.GENESIS_HASH + "\"}", signed,
                            "the signed content has schema order"),
                    () -> assertEquals(signed.substring(0, signed.length() - 1) + ",\"hmac\":\"" + OTHER_HASH + "\"}",
                            document, "the document appends the hmac"));
        }

        @Test
        @DisplayName("the signed content does not depend on the hmac")
        void signedContentWithoutHmac() {
            assertArrayEquals(AuditCodec.signedContent(HEAD_RECORD),
                    AuditCodec.signedContent(HEAD_RECORD.withHmac(AuditCodec.GENESIS_HASH)),
                    "the signed content ignores the hmac");
        }

        @Test
        @DisplayName("the head of an empty log has no last event id and round trips")
        void emptyLog() {
            var empty = new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 0, null, AuditCodec.GENESIS_HASH,
                    AuditCodec.GENESIS_HASH, OTHER_HASH);

            assertEquals(empty, AuditCodec.readHead(HEAD, AuditCodec.writeHead(empty)),
                    "an empty head round trips unchanged");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"\"file\":\"audit.jsonl\"", "\"hmac\":\"" + OTHER_HASH + "\""})
        @DisplayName("a head without a required field is refused as unreadable")
        void missingField(String field) {
            var document = new String(AuditCodec.writeHead(HEAD_RECORD), UTF_8).replace(field + ",", "")
                    .replace("," + field, "");
            var input = bytes(document);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readHead(HEAD, input),
                    "a head without a required field is refused");

            assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                    "the refusal outcome is unreadable");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "\"file\":\"audit-1.jsonl\"",
                "\"record_count\":-1",
                "\"record_count\":0",
                "\"last_event_id\":\"x\"",
                "\"bogus\":true"})
        @DisplayName("a head with an undeclared field or a value out of its form is refused as unreadable")
        void malformed(String replacement) {
            var field = replacement.substring(0, replacement.indexOf(':'));
            var document = new String(AuditCodec.writeHead(HEAD_RECORD), UTF_8);
            var edited = document.contains(field) ? document.replaceFirst(field + ":[^,}]*", replacement)
                    : document.replace("}", "," + replacement + "}");
            var input = bytes(edited);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readHead(HEAD, input),
                    "a malformed head is refused");

            assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                    "the refusal outcome is unreadable");
        }

        @Test
        @DisplayName("a head of another format version is refused as outdated or newer")
        void otherFormatVersion() {
            var document = new String(AuditCodec.writeHead(HEAD_RECORD), UTF_8).replace("\"format_version\":1",
                    "\"format_version\":0");
            var input = bytes(document);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readHead(HEAD, input),
                    "a head of another version is refused");

            assertEquals(StoreRefusal.Outcome.FORMAT_OUTDATED, refusal.outcome(),
                    "the refusal outcome is format outdated");
        }
    }

    @Nested
    @DisplayName("index entry")
    class IndexEntries {

        @Test
        @DisplayName("round trips in its canonical line, named after the first event of the rotated file")
        void roundTrip() {
            var line = new String(AuditCodec.writeIndexEntry(INDEX_ENTRY), UTF_8);

            assertAll("the index entry round trips canonically",
                    () -> assertEquals(INDEX_ENTRY, AuditCodec.readIndexEntry(INDEX, bytes(line)),
                            "the entry is read back unchanged"),
                    () -> assertEquals("{\"format_version\":1,\"prev_hash\":\"" + AuditCodec.GENESIS_HASH
                            + "\",\"file\":\"audit-" + EVENT_ID + ".jsonl\",\"first_event_id\":\"" + EVENT_ID
                            + "\",\"last_event_id\":\"" + OTHER_EVENT_ID + "\",\"record_count\":12,"
                            + "\"checkpoint_hash\":\"" + HASH + "\"}", line,
                            "the entry line is written as expected"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {
                "\"file\":\"audit.jsonl\"",
                "\"record_count\":0",
                "\"checkpoint_hash\":\"abc\"",
                "\"first_event_id\":\"x\"",
                "\"bogus\":1"})
        @DisplayName("an entry with an undeclared field or a value out of its form is refused as unreadable")
        void malformed(String replacement) {
            var field = replacement.substring(0, replacement.indexOf(':'));
            var line = new String(AuditCodec.writeIndexEntry(INDEX_ENTRY), UTF_8);
            var edited = line.contains(field) ? line.replaceFirst(field + ":[^,}]*", replacement)
                    : line.replace("}", "," + replacement + "}");
            var input = bytes(edited);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readIndexEntry(INDEX, input),
                    "a malformed index entry is refused");

            assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                    "the refusal outcome is unreadable");
        }

        @Test
        @DisplayName("an entry without its checkpoint hash is refused as unreadable")
        void missingField() {
            var line = new String(AuditCodec.writeIndexEntry(INDEX_ENTRY), UTF_8)
                    .replace(",\"checkpoint_hash\":\"" + HASH + "\"", "");
            var input = bytes(line);

            var refusal = assertThrows(StoreRefusal.class, () -> AuditCodec.readIndexEntry(INDEX, input),
                    "an entry without a checkpoint hash is refused");

            assertTrue(refusal.getMessage().contains("checkpoint_hash"), refusal::getMessage);
        }
    }
}
