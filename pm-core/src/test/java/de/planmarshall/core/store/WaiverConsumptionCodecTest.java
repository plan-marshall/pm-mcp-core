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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.StoreRefusal.Outcome;

/**
 * The line of the waiver consumption log (PM-SEC-3, PM-IMPL-2): written in the order of its schema, read back equal,
 * and every other shape refused as a {@link StoreRefusal} naming the file. The member names and the refused shapes are
 * wire contracts; the waiver id, the gate, the instant, the state id, the commit and the covered names are generated
 * within the grammar of the record.
 */
@EnableGeneratorController
@DisplayName("Waiver consumption codec")
class WaiverConsumptionCodecTest {

    // Fixture path: the file a refusal names.
    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/waiver-consumptions.jsonl");

    static final String WAIVER_ID = waiverId();
    static final String GATE = lower() + "-" + lower();
    static final Instant AT = Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next());
    static final String STATE_ID = lower() + "." + lower();
    static final String HEAD_SHA = headSha();
    static final List<String> COVERED = List.of(lower() + "-" + lower(), lower());

    /** @return a waiver id of the grammar of the record, {@code W-} and digits */
    static String waiverId() {
        return "W-" + Generators.integers(1, 99_999).next();
    }

    /** @return a commit SHA of the grammar of the record, 40 lower-case hex digits */
    static String headSha() {
        var bytes = new byte[20];
        for (var index = 0; index < bytes.length; index++) {
            bytes[index] = Generators.integers(0, 255).next().byteValue();
        }
        return HexFormat.of().formatHex(bytes);
    }

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** One evaluation of the CI gate that relied on the waiver {@link #WAIVER_ID}. */
    static WaiverConsumptionRecord consumption() {
        return new WaiverConsumptionRecord(WAIVER_ID, GATE, AT, STATE_ID, HEAD_SHA, COVERED);
    }

    /** @return the members of the document of {@link #consumption()}, in the order of the schema */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("waiver_id", "\"" + WAIVER_ID + "\"");
        members.put("gate", "\"" + GATE + "\"");
        members.put("at", "\"" + AT + "\"");
        members.put("state_id", "\"" + STATE_ID + "\"");
        members.put("head_sha", "\"" + HEAD_SHA + "\"");
        members.put("covered", COVERED.stream().map(entry -> "\"" + entry + "\"")
                .collect(Collectors.joining(",", "[", "]")));
        return members;
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> WaiverConsumptionCodec.read(FILE, document),
                "the document should be refused by the reader");
        assertAll(
                () -> assertEquals(outcome, refusal.outcome(), "the refusal should carry its outcome"),
                () -> assertEquals(FILE, refusal.file(), "the refusal should name the file read"));
        return refusal;
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a consumption is written in the order of the schema and read back equal")
        void writtenShape() {
            var written = WaiverConsumptionCodec.write(consumption());

            assertAll(
                    () -> assertArrayEquals(document(members()), written,
                            "the line should be written in schema order"),
                    () -> assertEquals(consumption(), WaiverConsumptionCodec.read(FILE, written),
                            "the written line should be read back equal"));
        }

        @Test
        @DisplayName("a consumption that covered nothing named is read back with an empty list")
        void emptyCovered() {
            var empty = new WaiverConsumptionRecord(waiverId(), GATE, AT, STATE_ID, HEAD_SHA, List.of());

            assertEquals(empty, WaiverConsumptionCodec.read(FILE, WaiverConsumptionCodec.write(empty)),
                    "an empty covered list should be read back equal");
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a consumption of another format version is refused as such (versions are the contract)")
        void otherVersion(int version, Outcome outcome) {
            var members = members();
            members.put("format_version", Integer.toString(version));

            assertEquals(OptionalInt.of(version), assertRefused(outcome, document(members)).found(),
                    "the refusal should report the version found");
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"waiver_id", "gate", "at", "state_id", "head_sha", "covered"})
        @DisplayName("a consumption without one of its members is refused naming it (member names are the contract)")
        void missingMember(String member) {
            var members = members();
            members.remove(member);

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains(member), "the refusal should name the missing member");
        }

        @ParameterizedTest(name = "{0}")
        @CsvSource(delimiter = '|', textBlock = """
                waiver id without W-            | waiver_id  | "1"
                empty gate                      | gate       | ""
                instant without zone            | at         | "2026-10-17T11:00:00"
                empty state id                  | state_id   | ""
                short head sha                  | head_sha   | "0123456"
                upper-case head sha             | head_sha   | "0123456789ABCDEF0123456789ABCDEF01234567"
                covered as string               | covered    | "sonar"
                covered with a number           | covered    | [1]
                covered with an empty entry     | covered    | [""]
                undeclared member               | waived_by  | "oliver"
                """)
        @DisplayName("a member outside its one shape is refused (the refused wire shapes are the contract)")
        void memberShape(String name, String member, String value) {
            var members = members();
            members.put(member, value);

            assertRefused(Outcome.UNREADABLE, document(members));
        }
    }

    @Test
    @DisplayName("a consumption is built only with its components, and keeps its own copy of covered")
    void components() {
        var entry = lower();
        var covered = new ArrayList<>(List.of(entry));
        var consumption = new WaiverConsumptionRecord(WAIVER_ID, GATE, AT, STATE_ID, HEAD_SHA, covered);
        covered.add(entry + "x");
        var none = List.<String>of();

        assertAll(
                () -> assertEquals(List.of(entry), consumption.covered(),
                        "the record should keep its own copy of covered"),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord(null, GATE, AT, STATE_ID, HEAD_SHA, none),
                        "a missing waiver id should be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord(WAIVER_ID, GATE, null, STATE_ID, HEAD_SHA, none),
                        "a missing instant should be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord(WAIVER_ID, GATE, AT, STATE_ID, null, none),
                        "a missing head sha should be refused"),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord(WAIVER_ID, GATE, AT, STATE_ID, HEAD_SHA, null),
                        "a missing covered list should be refused"));
    }
}
