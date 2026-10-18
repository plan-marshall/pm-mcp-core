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

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
 * and every other shape refused as a {@link StoreRefusal} naming the file.
 */
@DisplayName("Waiver consumption codec")
class WaiverConsumptionCodecTest {

    private static final Path FILE = Path.of("/base/projects/pm-mcp-core/plans/a-plan/waiver-consumptions.jsonl");
    private static final String HEAD_SHA = "0123456789abcdef0123456789abcdef01234567";

    /** One evaluation of the CI gate that relied on the waiver {@code W-1}. */
    static WaiverConsumptionRecord consumption() {
        return new WaiverConsumptionRecord("W-1", "ci-verify", Instant.parse("2026-10-17T11:00:00Z"),
                "finalize.ci-verify", HEAD_SHA, List.of("integration-tests", "sonar"));
    }

    /** @return the members of the document of {@link #consumption()}, in the order of the schema */
    private static Map<String, String> members() {
        var members = new LinkedHashMap<String, String>();
        members.put("format_version", "1");
        members.put("waiver_id", "\"W-1\"");
        members.put("gate", "\"ci-verify\"");
        members.put("at", "\"2026-10-17T11:00:00Z\"");
        members.put("state_id", "\"finalize.ci-verify\"");
        members.put("head_sha", "\"" + HEAD_SHA + "\"");
        members.put("covered", "[\"integration-tests\",\"sonar\"]");
        return members;
    }

    private static byte[] document(Map<String, String> members) {
        return members.entrySet().stream().map(member -> "\"" + member.getKey() + "\":" + member.getValue())
                .collect(Collectors.joining(",", "{", "}")).getBytes(UTF_8);
    }

    private static StoreRefusal assertRefused(Outcome outcome, byte[] document) {
        var refusal = assertThrows(StoreRefusal.class, () -> WaiverConsumptionCodec.read(FILE, document));
        assertAll(
                () -> assertEquals(outcome, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file()));
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
                    () -> assertArrayEquals(document(members()), written),
                    () -> assertEquals(consumption(), WaiverConsumptionCodec.read(FILE, written)));
        }

        @Test
        @DisplayName("a consumption that covered nothing named is read back with an empty list")
        void emptyCovered() {
            var empty = new WaiverConsumptionRecord("W-7", "ci-verify", Instant.parse("2026-10-17T11:00:00Z"),
                    "finalize.ci-verify", HEAD_SHA, List.of());

            assertEquals(empty, WaiverConsumptionCodec.read(FILE, WaiverConsumptionCodec.write(empty)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "format_version {0}")
        @CsvSource({"2, FORMAT_NEWER", "0, FORMAT_OUTDATED"})
        @DisplayName("a consumption of another format version is refused as such")
        void otherVersion(int version, Outcome outcome) {
            var members = members();
            members.put("format_version", Integer.toString(version));

            assertEquals(OptionalInt.of(version), assertRefused(outcome, document(members)).found());
        }

        @ParameterizedTest(name = "without {0}")
        @ValueSource(strings = {"waiver_id", "gate", "at", "state_id", "head_sha", "covered"})
        @DisplayName("a consumption without one of its members is refused naming it")
        void missingMember(String member) {
            var members = members();
            members.remove(member);

            var refusal = assertRefused(Outcome.UNREADABLE, document(members));

            assertTrue(refusal.getMessage().contains(member), refusal.getMessage());
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
        @DisplayName("a member outside its one shape is refused")
        void memberShape(String name, String member, String value) {
            var members = members();
            members.put(member, value);

            assertRefused(Outcome.UNREADABLE, document(members));
        }
    }

    @Test
    @DisplayName("a consumption is built only with its components, and keeps its own copy of covered")
    void components() {
        var at = Instant.parse("2026-10-17T11:00:00Z");
        var covered = new ArrayList<>(List.of("sonar"));
        var consumption = new WaiverConsumptionRecord("W-1", "ci-verify", at, "finalize.ci-verify", HEAD_SHA, covered);
        covered.add("later");
        var none = List.<String>of();

        assertAll(
                () -> assertEquals(List.of("sonar"), consumption.covered()),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord(null, "ci-verify", at, "s", HEAD_SHA, none)),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord("W-1", "ci-verify", null, "s", HEAD_SHA, none)),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord("W-1", "ci-verify", at, "s", null, none)),
                () -> assertThrows(NullPointerException.class,
                        () -> new WaiverConsumptionRecord("W-1", "ci-verify", at, "s", HEAD_SHA, null)));
    }
}
