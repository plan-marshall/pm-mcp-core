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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
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
 * <p>
 * The journal wire JSON, its member names, the mode and scope type wire names, the staged-name suffixes and the
 * SHA-256 golden value of {@code abc} are contracts and stay literal; the transaction ids, the instant, the plan id, the
 * store paths, the sizes and the digests of the targets are generated within their grammar. A document template names
 * the generated values by placeholders: {@code {path}}, {@code {plan}}, {@code {size}}, {@code {sha4}},
 * {@code {sha1}}, {@code {id}}, {@code {other}} and {@code {delete}}.
 */
@EnableGeneratorController
@DisplayName("Transaction journal")
class TransactionJournalTest {

    // Layout name of the journal file of a scope.
    private static final String JOURNAL_NAME = "transaction-journal.json";

    private Path file;
    private TransactionId id;
    private TransactionId otherId;
    private Instant createdAt;
    private String plan;
    private String statusPath;
    private String findingsPath;
    private String deletePath;
    private StoreDigest statusDigest;
    private StoreDigest findingsDigest;
    private Target replace;
    private Target append;

    @BeforeEach
    void createJournalData() {
        createdAt = Instant.ofEpochMilli(Generators.longs(1_700_000_000L, 1_800_000_000L).next() * 1000L
                + Generators.integers(1, 999).next());
        var clock = Clock.fixed(createdAt, ZoneOffset.UTC);
        id = TransactionId.generate(clock);
        otherId = TransactionId.generate(clock);
        plan = "plan-" + lowerName();
        file = Path.of("/base/projects", "pm-" + lowerName(), "plans", plan, "mcp", JOURNAL_NAME);
        statusPath = "mcp/" + lowerName() + ".json";
        findingsPath = lowerName() + ".jsonl";
        deletePath = "pending/ent-" + lowerName() + ".json";
        statusDigest = StoreDigest.of(Generators.letterStrings(1, 200).next().getBytes(UTF_8));
        findingsDigest = StoreDigest.of(Generators.letterStrings(1, 200).next().getBytes(UTF_8));
        replace = new Target(statusPath, Mode.REPLACE, statusPath + ".tmp." + id.value(), OptionalLong.empty(),
                statusDigest);
        append = new Target(findingsPath, Mode.APPEND, findingsPath + ".append." + id.value(),
                OptionalLong.of(Generators.longs(0L, 100_000L).next()), findingsDigest);
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** Fills the placeholders of a document template with the generated values of this test. */
    private String fill(String template) {
        return template.replace("{path}", statusPath).replace("{plan}", plan)
                .replace("{size}", Long.toString(statusDigest.size()))
                .replace("{sha4}", statusDigest.sha256().substring(0, 4))
                .replace("{sha1}", statusDigest.sha256().substring(0, 1)).replace("{delete}", deletePath)
                .replace("{id}", id.value()).replace("{other}", otherId.value());
    }

    /** The document of a plan journal of the current format version with the given target objects. */
    private byte[] document(String targets) {
        return document(Integer.toString(TransactionJournal.FORMAT_VERSION), targets);
    }

    /** The document of a plan journal with the given target objects, as a writer of another component writes it. */
    private byte[] document(String formatVersion, String targets) {
        return ("{\"format_version\":" + formatVersion + ",\"transaction_id\":\"" + id.value()
                + "\",\"scope_type\":\"plan\",\"scope_id\":\"" + plan + "\","
                + "\"created_at\":\"" + createdAt + "\",\"targets\":[" + targets + "],\"owed_events\":[]}")
                .getBytes(UTF_8);
    }

    private String replaceTarget() {
        return fill("{\"path\":\"{path}\",\"mode\":\"replace\",\"staged_path\":\"{path}.tmp.{id}\","
                + "\"size_before\":null,\"new_size\":{size},\"new_sha256\":\"") + statusDigest.sha256() + "\"}";
    }

    private String deleteTarget() {
        return fill("{\"path\":\"{delete}\",\"mode\":\"delete\"}");
    }

    /** @return each other format version with its refusal: the next one is newer, every earlier one outdated */
    static Stream<Arguments> otherVersions() {
        var current = TransactionJournal.FORMAT_VERSION;
        return Stream.concat(Stream.of(Arguments.of(current + 1, Outcome.FORMAT_NEWER)),
                IntStream.range(1, current).mapToObj(version -> Arguments.of(version, Outcome.FORMAT_OUTDATED)));
    }

    /** @return a name and the owed events field that replaces the empty one of a valid document (wire JSON) */
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

    private StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> TransactionJournal.read(file, document),
                "the document should be refused");
        assertAll(
                () -> assertEquals(outcome, refusal.outcome(), "the refusal should carry the outcome"),
                () -> assertEquals(file, refusal.file(), "the refusal should name the journal file"),
                () -> assertTrue(refusal.getMessage().contains(file.toString()),
                        "the message should name the journal file"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a journal with a replace and an append target is read back equal, and written again byte for byte")
        void bothModes() {
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt, List.of(replace, append));

            var written = journal.write();
            var read = TransactionJournal.read(file, written);

            assertAll(
                    () -> assertEquals(journal, read, "the journal should be read back equal"),
                    () -> assertArrayEquals(written, read.write(), "the journal should be written byte for byte"));
        }

        @Test
        @DisplayName("a journal is written in format version 1 (wire contract)")
        void writtenFormatVersion() {
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt, List.of(replace));

            assertTrue(new String(journal.write(), UTF_8).startsWith("{\"format_version\":1,"),
                    "the journal should start with format version 1");
        }

        @Test
        @DisplayName("a journal is written in the documented field order, a replace with a null size before")
        void writtenShape() {
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt, List.of(replace));

            assertArrayEquals(document(replaceTarget()), journal.write(),
                    "the journal should be written in the documented shape");
        }

        @Test
        @DisplayName("a journal that owes audit records reads them back equal, in their order, byte for byte")
        void owedEvents() {
            var owed = List.of(
                    AuditEvent.crossScopeReconciled(lowerName(), "workspace/" + lowerName() + ".json", id.value()),
                    AuditEvent.transactionRolledForward(JournalScopeType.PLAN.wireName(), plan, id.value(),
                            List.of(statusPath, findingsPath)),
                    AuditEvent.jsonlTailRepaired(Path.of("/base", findingsPath), Generators.integers(1, 100).next(),
                            findingsDigest.sha256(), id.value()));
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt, List.of(replace), owed);

            var written = journal.write();
            var read = TransactionJournal.read(file, written);

            assertAll(
                    () -> assertEquals(owed, read.owedEvents(), "the owed records should be read back in order"),
                    () -> assertEquals(journal, read, "the journal should be read back equal"),
                    () -> assertArrayEquals(written, read.write(), "the journal should be written byte for byte"));
        }

        @Test
        @DisplayName("an owed record with a detail that is no string, integer or list of strings is rejected")
        void unwritableOwedEvent() {
            // category and outcome are wire values of the audit record
            var event = new AuditEvent(lowerName(), "store", "succeeded", Map.of(lowerName(), true));

            assertThrows(IllegalArgumentException.class, () -> OwedAuditEvents.requireWritable(event),
                    "a boolean detail should be rejected");
        }

        @Test
        @DisplayName("an owed list detail with a value that is no string is refused by the write, naming detail and value")
        void unwritableOwedListItem() {
            var key = lowerName();
            var number = Generators.integers(1, 100_000).next();
            var event = new AuditEvent(lowerName(), "store", "succeeded", Map.of(key, List.of(statusPath, number)));
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt, List.of(replace),
                    List.of(event));

            var refusal = assertThrows(IllegalArgumentException.class, journal::write,
                    "a number in a list detail should be refused");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains("'" + key + "'"),
                            "the refusal should name the detail"),
                    () -> assertTrue(refusal.getMessage().contains(Integer.toString(number)),
                            "the refusal should name the value"),
                    () -> assertFalse(refusal.getMessage().contains("%s"),
                            "the refusal should have no open placeholder"));
        }

        @Test
        @DisplayName("a workspace journal writes and reads a null scope id")
        void workspace() {
            var journal = new TransactionJournal(id, JournalScopeType.WORKSPACE, null, createdAt, List.of(append));

            var read = TransactionJournal.read(file, journal.write());

            assertEquals(journal, read, "the workspace journal should be read back equal");
        }
    }

    /** A delete target stages nothing and leaves no store, so it is the path and the mode alone. */
    @Nested
    @DisplayName("delete target")
    class DeleteTarget {

        @Test
        @DisplayName("a journal with a delete target beside a replace is read back equal, and written again byte for"
                + " byte")
        void roundTrip() {
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt,
                    List.of(replace, Target.delete(deletePath)));

            var written = journal.write();
            var read = TransactionJournal.read(file, written);

            assertAll(
                    () -> assertEquals(journal, read, "the journal should be read back equal"),
                    () -> assertArrayEquals(written, read.write(), "the journal should be written byte for byte"));
        }

        @Test
        @DisplayName("a delete target is written as its path and mode, without staged path, size before or digest")
        void writtenShape() {
            var journal = new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt,
                    List.of(Target.delete(deletePath)));

            assertArrayEquals(document(deleteTarget()), journal.write(),
                    "the delete should be written as path and mode");
        }

        @Test
        @DisplayName("a delete target has no staged path, no size before and no new digest")
        void components() {
            var target = Target.delete(deletePath);

            assertAll(
                    () -> assertEquals(Mode.DELETE, target.mode(), "the mode should be DELETE"),
                    () -> assertEquals("delete", target.mode().wireName(), "the wire name should be delete"),
                    () -> assertNull(target.stagedPath(), "a delete should have no staged path"),
                    () -> assertEquals(OptionalLong.empty(), target.sizeBefore(),
                            "a delete should have no size before"),
                    () -> assertNull(target.newDigest(), "a delete should have no new digest"));
        }

        @ParameterizedTest(name = "with {0}")
        @ValueSource(strings = {"\"staged_path\":\"{delete}.tmp.{id}\"", "\"size_before\":null",
                "\"size_before\":12", "\"new_size\":42", "\"new_sha256\":\"{sha}\""})
        @DisplayName("a delete target that carries a member of a staged write is refused when the document is read")
        void stagedMemberRefused(String member) {
            var target = deleteTarget().replace("}", ","
                    + fill(member).replace("{sha}", statusDigest.sha256()) + "}");

            var refusal = assertRefused(Outcome.UNREADABLE, document(target));

            assertTrue(refusal.getMessage().contains("delete target"),
                    "the refusal should name the delete target");
        }

        @Test
        @DisplayName("a delete target record with a staged path, a size before or a new digest is rejected")
        void recordRejected() {
            var staged = statusPath + ".tmp." + id.value();
            var size = OptionalLong.of(Generators.longs(0L, 100_000L).next());
            var none = OptionalLong.empty();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Target(statusPath, Mode.DELETE, staged, none, null),
                            "a delete with a staged path should fail"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Target(statusPath, Mode.DELETE, null, size, null),
                            "a delete with a size before should fail"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Target(statusPath, Mode.DELETE, null, none, statusDigest),
                            "a delete with a new digest should fail"),
                    () -> assertThrows(IllegalArgumentException.class, () -> Target.delete(""),
                            "a delete of an empty path should fail"));
        }

        @Test
        @DisplayName("a replace or append target without a staged path or new digest is still rejected")
        void stagedModesKeepTheirMembers() {
            var none = OptionalLong.empty();
            var staged = statusPath + ".tmp." + id.value();

            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> new Target(statusPath, Mode.REPLACE, null, none, statusDigest),
                            "a replace without staged path should fail"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Target(statusPath, Mode.REPLACE, staged, none, null),
                            "a replace without new digest should fail"));
        }

        @Test
        @DisplayName("a delete has no staged file name")
        void noStagedName() {
            assertThrows(IllegalArgumentException.class, () -> id.stagedName(statusPath, Mode.DELETE),
                    "a delete should have no staged file name");
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
            var staged = findingsPath + ".append." + id.value();

            assertThrows(IllegalArgumentException.class,
                    () -> new Target(findingsPath, Mode.APPEND, staged, empty, findingsDigest),
                    "an append without size before should fail");
        }

        @ParameterizedTest(name = "size_before {0}")
        @ValueSource(strings = {"null", "absent"})
        @DisplayName("an append target without an integer size before is refused when the document is read")
        void appendDocumentRefused(String sizeBefore) {
            var field = "absent".equals(sizeBefore) ? "" : "\"size_before\":null,";
            var target = "{\"path\":\"" + findingsPath + "\",\"mode\":\"append\",\"staged_path\":\"" + findingsPath
                    + ".append." + id.value() + "\"," + field + "\"new_size\":" + findingsDigest.size()
                    + ",\"new_sha256\":\"" + findingsDigest.sha256() + "\"}";

            assertRefused(Outcome.UNREADABLE, document(target));
        }

        @Test
        @DisplayName("a replace target without a size before is accepted, as a record and as a document")
        void replaceAccepted() {
            var withoutField = replaceTarget().replace("\"size_before\":null,", "");

            var read = TransactionJournal.read(file, document(withoutField));

            assertAll(
                    () -> assertEquals(OptionalLong.empty(), replace.sizeBefore(),
                            "the replace record should have no size"),
                    () -> assertEquals(OptionalLong.empty(), read.targets().getFirst().sizeBefore(),
                            "the read replace should have no size"),
                    () -> assertEquals(Mode.REPLACE, read.targets().getFirst().mode(),
                            "the read target should be a replace"));
        }

        @Test
        @DisplayName("a negative size before is rejected (boundary)")
        void negative() {
            var negative = OptionalLong.of(-1);
            var staged = findingsPath + ".append." + id.value();

            assertThrows(IllegalArgumentException.class,
                    () -> new Target(findingsPath, Mode.APPEND, staged, negative, findingsDigest),
                    "a negative size before should be rejected");
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
                            () -> new TransactionJournal(id, JournalScopeType.PLAN, plan, createdAt, none),
                            "a journal without target should be rejected"),
                    () -> assertRefused(Outcome.UNREADABLE, document("")));
        }

        @ParameterizedTest(name = "format_version {0}")
        @MethodSource("de.planmarshall.core.store.TransactionJournalTest#otherVersions")
        @DisplayName("a document of another format version is refused as such, with the version found and current")
        void otherVersion(int version, Outcome outcome) {
            var refusal = assertRefused(outcome, document(Integer.toString(version), replaceTarget()));

            assertAll(
                    () -> assertEquals(OptionalInt.of(version), refusal.found(),
                            "the refusal should name the version found"),
                    () -> assertEquals(TransactionJournal.FORMAT_VERSION, refusal.current(),
                            "the refusal should name the current version"));
        }

        @Test
        @DisplayName("a journal of another version is refused as outdated (version boundary 0)")
        void anotherVersionOutdated() {
            var refusal = assertRefused(Outcome.FORMAT_OUTDATED, document("0", replaceTarget()));

            assertAll(
                    () -> assertEquals(OptionalInt.of(0), refusal.found(), "the refusal should name version 0"),
                    () -> assertEquals(1, refusal.current(), "the refusal should name version 1"));
        }

        @Test
        @DisplayName("an undeclared field is refused")
        void undeclaredField() {
            var target = replaceTarget().replace("}", ",\"note\":\"" + lowerName() + "\"}");

            assertRefused(Outcome.UNREADABLE, document(target));
        }

        @Test
        @DisplayName("a duplicate field is refused")
        void duplicateField() {
            var target = replaceTarget().replace("\"mode\":\"replace\",", "\"mode\":\"replace\",\"mode\":\"replace\",");

            assertRefused(Outcome.UNREADABLE, document(target));
        }

        @Test
        @DisplayName("an instant with an offset is refused, although it names the same moment")
        void instantWithOffset() {
            var withOffset = new String(document(replaceTarget()), UTF_8)
                    .replace(createdAt.toString(), createdAt.atOffset(ZoneOffset.ofHours(2)).toString())
                    .getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, withOffset);
        }

        /** The values are fixtures of the id grammar: version 4, upper case, variant c and no UUID at all. */
        @ParameterizedTest
        @ValueSource(strings = {"0192a3b4-c5d6-4e8f-9a0b-1c2d3e4f5a6b", "0192A3B4-C5D6-7E8F-9A0B-1C2D3E4F5A6B",
                "0192a3b4-c5d6-7e8f-ca0b-1c2d3e4f5a6b", "not-an-id"})
        @DisplayName("a transaction id that is not a lower-case UUID of version 7 is refused")
        void notVersion7(String transactionId) {
            var document = new String(document(replaceTarget()), UTF_8).replace(id.value(), transactionId)
                    .getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, document);
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                unknown scope type   | "scope_type":"plan"   | "scope_type":"project"
                unknown mode         | "mode":"replace"      | "mode":"rename"
                missing scope id     | "scope_id":"{plan}"   | "scope_id":null
                scope id as number   | "scope_id":"{plan}"   | "scope_id":1
                size as string       | "new_size":{size}     | "new_size":"{size}"
                short digest         | "new_sha256":"{sha4}  | "new_sha256":"{sha1}
                empty path           | "path":"{path}"       | "path":""
                """)
        @DisplayName("a field outside its one shape is refused (wire JSON with generated values)")
        void fieldShape(String name, String valid, String invalid) {
            var document = new String(document(replaceTarget()), UTF_8).replace(fill(valid), fill(invalid))
                    .getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, document);
        }

        /**
         * A recovery moves the staged path of a target over its store, so a journal is read only when every staged
         * path is the staged file of its own store in this transaction, and every path is relative and normalized.
         */
        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                staged path of another store       | {path}            | {path}x.tmp.{id}
                staged path of another transaction | {path}            | {path}.tmp.{other}
                suffix of an append for a replace  | {path}            | {path}.append.{id}
                absolute path                      | /{path}           | /{path}.tmp.{id}
                parent segment in the path         | ../{plan}/{path}  | ../{plan}/{path}.tmp.{id}
                parent segment in the staged path  | {path}            | mcp/../{path}.tmp.{id}
                current segment in the path        | ./{path}          | ./{path}.tmp.{id}
                """)
        @DisplayName("a target whose staged path is not the staged file of its store in this transaction is refused")
        void foreignStagedPath(String name, String path, String stagedPath) {
            var target = replaceTarget().replace("\"path\":\"" + statusPath + "\"", "\"path\":\"" + fill(path) + "\"")
                    .replace("\"staged_path\":\"" + statusPath + ".tmp." + id.value() + "\"",
                            "\"staged_path\":\"" + fill(stagedPath) + "\"");

            var refusal = assertRefused(Outcome.UNREADABLE, document(target));

            assertTrue(refusal.getMessage().contains("staged") || refusal.getMessage().contains("path"),
                    "the refusal should name the path or staged path");
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.store.TransactionJournalTest#owedEventShapes")
        @DisplayName("owed audit records outside their one shape are refused")
        void owedEventShape(String name, String owedEvents) {
            var document = new String(document(replaceTarget()), UTF_8)
                    .replace(",\"owed_events\":[]", owedEvents).getBytes(UTF_8);

            assertRefused(Outcome.UNREADABLE, document);
        }

        @Test
        @DisplayName("a missing required field is refused")
        void missingField() {
            var target = replaceTarget().replace(",\"new_size\":" + statusDigest.size(), "");

            assertRefused(Outcome.UNREADABLE, document(target));
        }

        @Test
        @DisplayName("content that is not JSON is refused")
        void notJson() {
            assertRefused(Outcome.UNREADABLE, "{\"format_version\":1,".getBytes(UTF_8));
        }

        @Test
        @DisplayName("a workspace journal with a scope id is rejected as a record")
        void workspaceWithScopeId() {
            var targets = List.of(append);

            assertThrows(IllegalArgumentException.class,
                    () -> new TransactionJournal(id, JournalScopeType.WORKSPACE, plan, createdAt, targets),
                    "a workspace journal with scope id should fail");
        }
    }

    @Nested
    @DisplayName("transaction id")
    class TransactionIds {

        @Test
        @DisplayName("a generated id is a lower-case UUID of version 7 carrying the milliseconds of the clock")
        void generated() {
            var clock = Clock.fixed(createdAt, ZoneOffset.UTC);

            var generated = TransactionId.generate(clock);

            var timestamp = Long.parseLong(generated.value().substring(0, 8) + generated.value().substring(9, 13), 16);
            assertAll(
                    () -> assertEquals(createdAt.toEpochMilli(), timestamp,
                            "the id should carry the clock milliseconds"),
                    () -> assertEquals(generated, new TransactionId(generated.value()),
                            "the id should be equal to its value"),
                    () -> assertEquals(generated.value(), generated.toString(), "the id should print as its value"));
        }

        @Test
        @DisplayName("two ids of the same instant differ")
        void unique() {
            var clock = Clock.fixed(createdAt, ZoneOffset.UTC);

            assertNotEquals(TransactionId.generate(clock), TransactionId.generate(clock),
                    "two ids of one instant should differ");
        }

        @Test
        @DisplayName("the id is found as the last name segment of a staged file and nowhere else")
        void suffix() {
            var store = lowerName() + ".json";
            var log = lowerName() + ".jsonl";

            assertAll(
                    () -> assertEquals(Optional.of(id), TransactionId.ofSuffix(store + ".tmp." + id.value()),
                            "the id should follow a replace suffix"),
                    () -> assertEquals(Optional.of(id), TransactionId.ofSuffix(log + ".append." + id.value()),
                            "the id should follow an append suffix"),
                    () -> assertEquals(Optional.empty(), TransactionId.ofSuffix(id.value()),
                            "a bare id should be no staged name"),
                    () -> assertEquals(Optional.empty(), TransactionId.ofSuffix(store),
                            "a store name should carry no id"),
                    // a well-formed id in upper case; a generated id may hold no letter to change in case
                    () -> assertEquals(Optional.empty(),
                            TransactionId.ofSuffix(store + ".tmp.0192A3B4-C5D6-7E8F-9A0B-1C2D3E4F5A6B"),
                            "an upper-case id should not be found"),
                    () -> assertEquals(Optional.empty(),
                            TransactionId.ofSuffix(store + ".tmp." + id.value() + ".bak"),
                            "an id before another suffix should not count"),
                    () -> assertEquals(Optional.empty(),
                            TransactionId.ofSuffix(store + "-" + id.value()),
                            "an id without the staged suffix should not count"));
        }
    }

    @Nested
    @DisplayName("store digest")
    class StoreDigests {

        @Test
        @DisplayName("the digest of content is its size and lower-case SHA-256, and matches that content only (golden)")
        void ofContent() {
            var content = "abc".getBytes(UTF_8);

            var digest = StoreDigest.of(content);

            assertAll(
                    () -> assertEquals(new StoreDigest(3,
                                    "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"), digest,
                            "the digest should be the SHA-256 golden value"),
                    () -> assertTrue(digest.matches(content), "the digest should match its content"),
                    () -> assertFalse(digest.matches("abd".getBytes(UTF_8)),
                            "the digest should not match other bytes"),
                    () -> assertFalse(digest.matches("abcd".getBytes(UTF_8)),
                            "the digest should not match a longer content"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD", "abc", ""})
        @DisplayName("a SHA-256 that is not 64 lower-case hexadecimal digits is rejected (golden and short values)")
        void invalidSha(String sha256) {
            assertThrows(IllegalArgumentException.class, () -> new StoreDigest(3, sha256),
                    "a malformed SHA-256 should be rejected");
        }

        @Test
        @DisplayName("a negative size is rejected")
        void negativeSize() {
            var sha256 = statusDigest.sha256();

            assertThrows(IllegalArgumentException.class, () -> new StoreDigest(-1, sha256),
                    "a negative size should be rejected");
        }
    }
}
