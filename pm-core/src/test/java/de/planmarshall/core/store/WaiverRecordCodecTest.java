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

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
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
 * file.
 */
@DisplayName("Waiver record codec")
class WaiverRecordCodecTest {

    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/waivers.jsonl");
    private static final Instant AT = Instant.parse("2026-10-17T09:00:00Z");

    /** One character outside the Basic Multilingual Plane, two UTF-16 units: U+1F600. */
    static final String SUPPLEMENTARY = Character.toString(0x1F600);

    /** A met waiver set by the operator from the terminal client. */
    static WaiverRecord met() {
        return WaiverRecord.builder().event(Event.WAIVED).waiverId("W-1").gate("ci-verify").mode(Mode.MET)
                .reason("known flaky check").reasonClass(ReasonClass.GATE_FALSE_POSITIVE).requestId("WR-3")
                .operator(new Operator(Channel.CLI, null, "oliver")).at(AT).build();
    }

    /** @return the members of the document of {@link #met()}, each with its JSON value, in the order of the schema */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("event", "\"waived\"");
        members.put("waiver_id", "\"W-1\"");
        members.put("gate", "\"ci-verify\"");
        members.put("mode", "\"met\"");
        members.put("reason", "\"known flaky check\"");
        members.put("reason_class", "\"gate_false_positive\"");
        members.put("request_id", "\"WR-3\"");
        members.put("operator", "{\"channel\":\"cli\",\"os_principal\":\"oliver\"}");
        members.put("at", "\"2026-10-17T09:00:00Z\"");
        return members;
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> WaiverRecordCodec.read(FILE, document));
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
        @DisplayName("a met waiver is written in the order of the schema, without the members it does not hold")
        void writtenShape() {
            assertArrayEquals(document(members()), WaiverRecordCodec.write(met()));
        }

        @Test
        @DisplayName("a raise carries its ceiling as value, after the mode, and is read back equal")
        void raise() {
            var raise = met().toBuilder().gate("fix_budget:ci-failure").mode(Mode.RAISE).value(5L)
                    .reasonClass(ReasonClass.DELIBERATE_DEFERRAL).requestId(null).build();

            var written = WaiverRecordCodec.write(raise);

            assertAll(
                    () -> assertEquals(raise, WaiverRecordCodec.read(FILE, written)),
                    () -> assertTrue(new String(written, UTF_8).contains("\"mode\":\"raise\",\"value\":5,\"reason\"")),
                    () -> assertFalse(new String(written, UTF_8).contains("request_id")));
        }

        @Test
        @DisplayName("a raise to a ceiling of 0 is read back equal")
        void raiseToZero() {
            var raise = met().toBuilder().mode(Mode.RAISE).value(0L).build();

            assertEquals(raise, WaiverRecordCodec.read(FILE, WaiverRecordCodec.write(raise)));
        }

        @Test
        @DisplayName("a revocation names only the waiver, its reason, the operator and the instant")
        void revoked() {
            var revoked = WaiverRecord.builder().event(Event.REVOKED).waiverId("W-1").reason("gate is fixed")
                    .operator(new Operator(Channel.WEB, "dev-0123456789ab", "oliver")).at(AT).build();

            var written = WaiverRecordCodec.write(revoked);

            assertAll(
                    () -> assertEquals(revoked, WaiverRecordCodec.read(FILE, written)),
                    () -> assertEquals("{\"format_version\":1,\"event\":\"revoked\",\"waiver_id\":\"W-1\","
                            + "\"reason\":\"gate is fixed\",\"operator\":{\"channel\":\"web\","
                            + "\"device_id\":\"dev-0123456789ab\",\"os_principal\":\"oliver\"},"
                            + "\"at\":\"2026-10-17T09:00:00Z\"}", new String(written, UTF_8)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(ReasonClass.class)
        @DisplayName("every reason class is read back equal")
        void reasonClass(ReasonClass reasonClass) {
            var waiver = met().toBuilder().reasonClass(reasonClass).build();

            assertEquals(waiver, WaiverRecordCodec.read(FILE, WaiverRecordCodec.write(waiver)));
        }

        @Test
        @DisplayName("an explicit null for request_id, value and device_id reads as absent")
        void explicitNulls() {
            var members = members();
            members.put("request_id", "null");
            members.put("value", "null");
            members.put("operator", "{\"channel\":\"cli\",\"device_id\":null,\"os_principal\":\"oliver\"}");

            var waiver = WaiverRecordCodec.read(FILE, document(members));

            assertAll(
                    () -> assertNull(waiver.requestId()),
                    () -> assertNull(waiver.value()),
                    () -> assertNull(waiver.operator().deviceId()));
        }

        @Test
        @DisplayName("the members may come in any order and read the same record")
        void memberOrder() {
            var reversed = new LinkedHashMap<String, String>();
            var members = members();
            members.keySet().stream().toList().reversed().forEach(key -> reversed.put(key, members.get(key)));

            assertEquals(met(), WaiverRecordCodec.read(FILE, document(reversed)));
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

            var refusal = assertRefused(outcome, document(members));

            assertEquals(OptionalInt.of(version), refusal.found());
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"event", "waiver_id", "reason", "operator", "at"})
        @DisplayName("a record without a member every line has is refused naming it")
        void missingMember(String member) {
            var members = members();
            members.remove(member);

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains(member), refusal.getMessage());
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"gate", "mode", "reason_class"})
        @DisplayName("a waived line without its gate, mode or reason class is refused")
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
            var onMet = members();
            onMet.put("value", "3");
            var onRevoked = members();
            onRevoked.put("event", "\"revoked\"");
            onRevoked.remove("mode");
            onRevoked.put("value", "3");

            assertAll(
                    () -> assertRefused(Outcome.UNREADABLE, document(onMet)),
                    () -> assertRefused(Outcome.UNREADABLE, document(onRevoked)));
        }

        @Test
        @DisplayName("an undeclared member, of the record or of its operator, is refused naming it")
        void undeclaredMember() {
            var ofRecord = members();
            ofRecord.put("expires_at", "\"2026-10-18T09:00:00Z\"");
            var operator = members();
            operator.put("operator", "{\"channel\":\"cli\",\"os_principal\":\"oliver\",\"host\":\"box\"}");

            assertAll(
                    () -> assertTrue(assertRefused(Outcome.UNREADABLE, document(ofRecord)).getMessage()
                            .contains("expires_at")),
                    () -> assertTrue(assertRefused(Outcome.UNREADABLE, document(operator)).getMessage()
                            .contains("host")));
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
        @DisplayName("a member outside its one shape is refused")
        void memberShape(String name, String member, String value) {
            var members = members();
            members.put(member, value);

            assertRefused(Outcome.UNREADABLE, document(members));
        }

        @Test
        @DisplayName("a reason of 2000 characters is read, one of 2001 refused")
        void reasonLength() {
            var longest = members();
            longest.put("reason", "\"" + "r".repeat(2000) + "\"");
            var tooLong = members();
            tooLong.put("reason", "\"" + "r".repeat(2001) + "\"");

            assertAll(
                    () -> assertEquals(2000, WaiverRecordCodec.read(FILE, document(longest)).reason().length()),
                    () -> assertRefused(Outcome.UNREADABLE, document(tooLong)));
        }

        @ParameterizedTest(name = "{0} supplementary characters, accepted: {1}")
        @CsvSource({"1500, true", "2000, true", "2001, false"})
        @DisplayName("a reason is counted in characters, as the schema counts them, not in UTF-16 units")
        void reasonLengthInCodePoints(int characters, boolean accepted) {
            var reason = SUPPLEMENTARY.repeat(characters);
            var members = members();
            members.put("reason", "\"" + reason + "\"");
            var document = document(members);

            if (accepted) {
                assertEquals(reason, WaiverRecordCodec.read(FILE, document).reason());
            } else {
                assertRefused(Outcome.UNREADABLE, document);
            }
        }

        @Test
        @DisplayName("a member twice and content after the record are refused")
        void duplicateAndTrailing() {
            var once = new String(document(members()), UTF_8);
            var twice = once.replace("\"gate\":\"ci-verify\",", "\"gate\":\"ci-verify\",\"gate\":\"ci-verify\",");

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
                () -> assertThrows(NullPointerException.class, withoutEvent::build),
                () -> assertThrows(NullPointerException.class, withoutWaiverId::build),
                () -> assertThrows(NullPointerException.class, withoutOperator::build),
                () -> assertThrows(NullPointerException.class, withoutInstant::build),
                () -> assertThrows(NullPointerException.class, () -> new Operator(null, null, "oliver")),
                () -> assertThrows(NullPointerException.class, () -> new Operator(Channel.CLI, null, null)));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Channel.class)
    @DisplayName("every enum is found by its wire name, and an unknown name by none")
    void wireNames(Channel channel) {
        assertAll(
                () -> assertEquals(channel, Channel.fromWireName(channel.wireName()).orElseThrow()),
                () -> assertTrue(Channel.fromWireName("quorum").isEmpty()),
                () -> assertEquals(Event.REVOKED, Event.fromWireName("revoked").orElseThrow()),
                () -> assertEquals(Mode.RAISE, Mode.fromWireName("raise").orElseThrow()),
                () -> assertTrue(ReasonClass.fromWireName("Unknown").isEmpty()));
    }
}
