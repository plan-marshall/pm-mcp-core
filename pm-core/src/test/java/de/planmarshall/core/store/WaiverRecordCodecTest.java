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

import static de.planmarshall.core.store.QuestionRecordCodecTest.json;
import static de.planmarshall.core.store.QuestionRecordCodecTest.text;
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
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;
import de.planmarshall.core.store.WaiverRecord.Channel;
import de.planmarshall.core.store.WaiverRecord.Event;
import de.planmarshall.core.store.WaiverRecord.Mode;
import de.planmarshall.core.store.WaiverRecord.Operator;
import de.planmarshall.core.store.WaiverRecord.ReasonClass;

/**
 * The line of the waiver log (PM-SEC-3, PM-IMPL-2): written in the order of its schema, read back equal per event and
 * mode, and every value outside the closed schema of the waiver record refused as a {@link StoreRefusal} naming the
 * file. The member names, the event, mode, reason class and channel wire names, the refused shapes and the length
 * boundaries of the reason are wire contracts; the ids, the gate, the reasons (with punctuation, a quoted word and a
 * backslash, so the round trip keeps its escapes), the principal, the device and the instant are generated within the
 * grammar of the record.
 */
@EnableGeneratorController
@DisplayName("Waiver record codec")
class WaiverRecordCodecTest {

    // Fixture path: the file a refusal names.
    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/waivers.jsonl");
    private static final Instant AT = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());

    /** One character outside the Basic Multilingual Plane, two UTF-16 units: U+1F600. */
    static final String SUPPLEMENTARY = Character.toString(0x1F600);

    /** The number of the waiver of {@link #met()}; its id is {@code W-} and this number. */
    static final int WAIVER_NUMBER = Generators.integers(1, 99_000).next();
    static final String WAIVER_ID = "W-" + WAIVER_NUMBER;
    static final String GATE = lower() + "-" + lower();
    static final String REASON = text(3);
    static final String REQUEST_ID = "WR-" + Generators.integers(1, 99_999).next();
    static final String PRINCIPAL = lower();
    private static final long RAISE_VALUE = Generators.longs(1, 1_000_000).next();

    static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a device id of the grammar of the record, {@code dev-} and 12 lower-case hex digits */
    static String deviceId() {
        var bytes = new byte[6];
        for (var index = 0; index < bytes.length; index++) {
            bytes[index] = Generators.integers(0, 255).next().byteValue();
        }
        return "dev-" + HexFormat.of().formatHex(bytes);
    }

    /** A met waiver set by the operator from the terminal client. */
    static WaiverRecord met() {
        return WaiverRecord.builder().event(Event.WAIVED).waiverId(WAIVER_ID).gate(GATE).mode(Mode.MET)
                .reason(REASON).reasonClass(ReasonClass.GATE_FALSE_POSITIVE).requestId(REQUEST_ID)
                .operator(new Operator(Channel.CLI, null, PRINCIPAL)).at(AT).build();
    }

    /** @return the members of the document of {@link #met()}, each with its JSON value, in the order of the schema */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"waived\"");
        members.put("waiver_id", json(WAIVER_ID));
        members.put("gate", json(GATE));
        members.put("mode", "\"met\"");
        members.put("reason", json(REASON));
        members.put("reason_class", "\"gate_false_positive\"");
        members.put("request_id", json(REQUEST_ID));
        members.put("operator", "{\"channel\":\"cli\",\"os_principal\":" + json(PRINCIPAL) + "}");
        members.put("at", json(AT.toString()));
        return members;
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> WaiverRecordCodec.read(FILE, document),
                "the document should be refused by the reader");
        assertAll(
                () -> assertEquals(outcome, refusal.outcome(), "the refusal should carry its outcome"),
                () -> assertEquals(FILE, refusal.file(), "the refusal should name the file read"),
                () -> assertTrue(refusal.getMessage().contains(FILE.toString()), "the message names the file"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a met waiver is written in the order of the schema, without the members it does not hold")
        void writtenShape() {
            assertArrayEquals(document(members()), WaiverRecordCodec.write(met()),
                    "the met waiver should be written in schema order");
        }

        @Test
        @DisplayName("a raise carries its ceiling as value, after the mode, and is read back equal")
        void raise() {
            var raise = met().toBuilder().gate("fix_budget:" + lower()).mode(Mode.RAISE).value(RAISE_VALUE)
                    .reasonClass(ReasonClass.DELIBERATE_DEFERRAL).requestId(null).build();

            var written = WaiverRecordCodec.write(raise);

            assertAll(
                    () -> assertEquals(raise, WaiverRecordCodec.read(FILE, written),
                            "the raise should be read back equal"),
                    () -> assertTrue(new String(written, UTF_8)
                            .contains("\"mode\":\"raise\",\"value\":" + RAISE_VALUE + ",\"reason\""),
                            "the value should follow the mode on the line"),
                    () -> assertFalse(new String(written, UTF_8).contains("request_id"),
                            "a raise without request should omit it"));
        }

        @Test
        @DisplayName("a raise to a ceiling of 0 is read back equal (0 is the boundary)")
        void raiseToZero() {
            var raise = met().toBuilder().mode(Mode.RAISE).value(0L).build();

            assertEquals(raise, WaiverRecordCodec.read(FILE, WaiverRecordCodec.write(raise)),
                    "a raise to zero should be read back equal");
        }

        @Test
        @DisplayName("a revocation names only the waiver, its reason, the operator and the instant (wire layout)")
        void revoked() {
            var reason = text(2);
            var device = deviceId();
            var revoked = WaiverRecord.builder().event(Event.REVOKED).waiverId(WAIVER_ID).reason(reason)
                    .operator(new Operator(Channel.WEB, device, PRINCIPAL)).at(AT).build();

            var written = WaiverRecordCodec.write(revoked);

            assertAll(
                    () -> assertEquals(revoked, WaiverRecordCodec.read(FILE, written),
                            "the revocation should be read back equal"),
                    () -> assertEquals("{\"format_version\":1,\"event\":\"revoked\",\"waiver_id\":" + json(WAIVER_ID)
                            + ",\"reason\":" + json(reason) + ",\"operator\":{\"channel\":\"web\","
                            + "\"device_id\":" + json(device) + ",\"os_principal\":" + json(PRINCIPAL) + "},"
                            + "\"at\":" + json(AT.toString()) + "}", new String(written, UTF_8),
                            "the revocation should hold only its members"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ReasonClass.class)
        @DisplayName("every reason class is read back equal")
        void reasonClass(ReasonClass reasonClass) {
            var waiver = met().toBuilder().reasonClass(reasonClass).build();

            assertEquals(waiver, WaiverRecordCodec.read(FILE, WaiverRecordCodec.write(waiver)),
                    "the reason class should be read back equal");
        }

        @Test
        @DisplayName("an explicit null for request_id, value and device_id reads as absent")
        void explicitNulls() {
            var members = members();
            members.put("request_id", "null");
            members.put("value", "null");
            members.put("operator", "{\"channel\":\"cli\",\"device_id\":null,\"os_principal\":" + json(PRINCIPAL)
                    + "}");

            var waiver = WaiverRecordCodec.read(FILE, document(members));

            assertAll(
                    () -> assertNull(waiver.requestId(), "a null request id should read as absent"),
                    () -> assertNull(waiver.value(), "a null value should read as absent"),
                    () -> assertNull(waiver.operator().deviceId(), "a null device id should read as absent"));
        }

        @Test
        @DisplayName("the members may come in any order and read the same record")
        void memberOrder() {
            var reversed = new LinkedHashMap<String, String>();
            var members = members();
            members.keySet().stream().toList().reversed().forEach(key -> reversed.put(key, members.get(key)));

            assertEquals(met(), WaiverRecordCodec.read(FILE, document(reversed)),
                    "reversed members should read the same record");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a record of another format version is refused as such, with the version found (the contract)")
        void otherVersion(int version, Outcome outcome) {
            var members = members();
            members.put("format_version", Integer.toString(version));

            var refusal = assertRefused(outcome, document(members));

            assertEquals(OptionalInt.of(version), refusal.found(), "the refusal should report the version found");
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"event", "waiver_id", "reason", "operator", "at"})
        @DisplayName("a record without a member every line has is refused naming it (member names are the contract)")
        void missingMember(String member) {
            var members = members();
            members.remove(member);

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains(member), "the refusal should name the missing member");
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"gate", "mode", "reason_class"})
        @DisplayName("a waived line without its gate, mode or reason class is refused (member names are the contract)")
        void waivedWithoutConditionalMember(String member) {
            var members = members();
            members.remove(member);

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @Test
        @DisplayName("a raise without its value, or with a null value, is refused")
        void raiseWithoutValue() {
            var absent = members();
            absent.put("mode", "\"raise\"");
            var asNull = members();
            asNull.put("mode", "\"raise\"");
            asNull.put("value", "null");

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, document(absent)),
                    () -> assertRefused(Outcome.UNREADABLE, document(asNull)));
        }

        @Test
        @DisplayName("a value on a met waiver or on a revocation without a mode is refused")
        void valueWithoutRaise() {
            var value = Long.toString(RAISE_VALUE);
            var onMet = members();
            onMet.put("value", value);
            var onRevoked = members();
            onRevoked.put("event", "\"revoked\"");
            onRevoked.remove("mode");
            onRevoked.put("value", value);

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, document(onMet)),
                    () -> assertRefused(Outcome.UNREADABLE, document(onRevoked)));
        }

        @Test
        @DisplayName("an undeclared member, of the record or of its operator, is refused naming it (names: contract)")
        void undeclaredMember() {
            var ofRecord = members();
            ofRecord.put("expires_at", json(AT.plusSeconds(86_400).toString()));
            var operator = members();
            operator.put("operator", "{\"channel\":\"cli\",\"os_principal\":" + json(PRINCIPAL) + ",\"host\":"
                    + json(lower()) + "}");

            assertAll(
                    () -> assertTrue(assertRefused(Outcome.UNREADABLE, document(ofRecord)).getMessage()
                            .contains("expires_at"), "the refusal should name expires_at"),
                    () -> assertTrue(assertRefused(Outcome.UNREADABLE, document(operator)).getMessage()
                            .contains("host"), "the refusal should name the operator host"));
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                unknown event                   | event          | "expired"
                event as null                   | event          | null
                waiver id without W-            | waiver_id      | "1"
                waiver id with letters          | waiver_id      | "W-x1"
                empty gate                      | gate           | ""
                unknown mode                    | mode           | "skip"
                value below 0                   | value          | -1
                value as string                 | value          | "5"
                empty reason                    | reason         | ""
                reason as null                  | reason         | null
                unknown reason class            | reason_class   | "other"
                request id without WR-          | request_id     | "W-3"
                operator as string              | operator       | "cli"
                operator without channel        | operator       | {"os_principal":"oliver"}
                operator without principal      | operator       | {"channel":"cli"}
                unknown channel                 | operator       | {"channel":"quorum","os_principal":"oliver"}
                device id too short             | operator       | {"channel":"web","device_id":"dev-0123","os_principal":"oliver"}
                device id upper case            | operator       | {"channel":"web","device_id":"dev-0123456789AB","os_principal":"oliver"}
                instant with an offset          | at             | "2026-10-17T11:00:00+02:00"
                version as string               | format_version | "1"
                """)
        @DisplayName("a member outside its one shape is refused (the refused wire shapes are the contract)")
        void memberShape(String name, String member, String value) {
            var members = members();
            members.put(member, value);

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @Test
        @DisplayName("a reason of 2000 characters is read, one of 2001 refused (the length boundary)")
        void reasonLength() {
            var longest = members();
            longest.put("reason", "\"" + "r".repeat(2000) + "\"");
            var tooLong = members();
            tooLong.put("reason", "\"" + "r".repeat(2001) + "\"");

            assertAll(
                    () -> assertEquals(2000, WaiverRecordCodec.read(FILE, document(longest)).reason().length(),
                            "a reason of 2000 characters should be read"),
                    () -> assertRefused(Outcome.UNREADABLE, document(tooLong)));
        }

        @ParameterizedTest(name = "{0} supplementary characters, accepted: {1}")
        @CsvSource({"1500, true", "2000, true", "2001, false"})
        @DisplayName("a reason is counted in characters, as the schema counts them, not in UTF-16 units (boundary)")
        void reasonLengthInCodePoints(int characters, boolean accepted) {
            var reason = SUPPLEMENTARY.repeat(characters);
            var members = members();
            members.put("reason", "\"" + reason + "\"");
            var document = document(members);

            if (accepted) {
                assertEquals(reason, WaiverRecordCodec.read(FILE, document).reason(),
                        "a reason within the limit should be read");
            } else {
                assertRefused(Outcome.UNREADABLE, document);
            }
        }

        @Test
        @DisplayName("a member twice and content after the record are refused")
        void duplicateAndTrailing() {
            var once = new String(document(members()), UTF_8);
            var gate = "\"gate\":" + json(GATE) + ",";
            var twice = once.replace(gate, gate + gate);

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, twice.getBytes(UTF_8)),
                    () -> assertRefused(Outcome.UNREADABLE, (once + once).getBytes(UTF_8)));
        }
    }

    @Test
    @DisplayName("a record is built only with its required components")
    void requiredComponents() {
        var withoutEvent = met().toBuilder().event(null);
        var withoutWaiverId = met().toBuilder().waiverId(null);
        var withoutOperator = met().toBuilder().operator(null);
        var withoutInstant = met().toBuilder().at(null);

        assertAll(
                () -> assertThrows(NullPointerException.class, withoutEvent::build,
                        "a record without event should be refused"),
                () -> assertThrows(NullPointerException.class, withoutWaiverId::build,
                        "a record without waiver id should be refused"),
                () -> assertThrows(NullPointerException.class, withoutOperator::build,
                        "a record without operator should be refused"),
                () -> assertThrows(NullPointerException.class, withoutInstant::build,
                        "a record without instant should be refused"),
                () -> assertThrows(NullPointerException.class, () -> new Operator(null, null, PRINCIPAL),
                        "an operator without channel should be refused"),
                () -> assertThrows(NullPointerException.class, () -> new Operator(Channel.CLI, null, null),
                        "an operator without principal should be refused"));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Channel.class)
    @DisplayName("every enum is found by its wire name, and an unknown name by none (wire names are the contract)")
    void wireNames(Channel channel) {
        assertAll(
                () -> assertEquals(channel, Channel.fromWireName(channel.wireName()).orElseThrow(),
                        "the channel should be found by its wire name"),
                () -> assertTrue(Channel.fromWireName("quorum").isEmpty(), "an unknown channel should be none"),
                () -> assertEquals(Event.REVOKED, Event.fromWireName("revoked").orElseThrow(),
                        "the revoked event should be found by name"),
                () -> assertEquals(Mode.RAISE, Mode.fromWireName("raise").orElseThrow(),
                        "the raise mode should be found by name"),
                () -> assertTrue(ReasonClass.fromWireName("Unknown").isEmpty(),
                        "a wrongly cased reason class should be none"));
    }
}
