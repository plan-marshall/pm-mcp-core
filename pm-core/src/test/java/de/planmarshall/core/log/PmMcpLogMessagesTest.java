/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.log;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.tools.logging.LogRecord;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The registry numbers every message once (PM-IMPL-1): two messages with one identifier cannot be told apart in a
 * log, and an identifier outside the range of its level reads as another level.
 */
@DisplayName("PmMcpLogMessages")
class PmMcpLogMessagesTest {

    @ParameterizedTest(name = "{0} holds identifiers {1} to {2}")
    @CsvSource({"INFO, 1, 99", "WARN, 100, 199", "ERROR, 200, 299"})
    @DisplayName("every message lies in the range of its level and carries the prefix")
    void ranges(String level, int first, int last) throws Exception {
        var records = records(level);

        assertFalse(records.isEmpty(), level);
        for (LogRecord logRecord : records) {
            assertEquals(PmMcpLogMessages.PREFIX, logRecord.getPrefix());
            assertTrue(logRecord.getIdentifier() >= first && logRecord.getIdentifier() <= last,
                    logRecord.resolveIdentifierString());
        }
    }

    @Test
    @DisplayName("no identifier is used twice")
    void unique() throws Exception {
        var all = new ArrayList<LogRecord>();
        for (String level : List.of("INFO", "WARN", "ERROR")) {
            all.addAll(records(level));
        }
        var seen = new HashSet<Integer>();

        for (LogRecord logRecord : all) {
            assertTrue(seen.add(logRecord.getIdentifier()), logRecord.resolveIdentifierString());
        }
    }

    static Stream<Arguments> enrolmentPairAndPolicyRecords() {
        return Stream.of(
                Arguments.of(PmMcpLogMessages.INFO.ENROLMENT_PAIR_COMPLETED, "PM_MCP-64",
                        "Stopped write of the enrolment of project '%s' completed by the recovery: transaction '%s'"),
                Arguments.of(PmMcpLogMessages.WARN.ENROLMENT_INVALID, "PM_MCP-165",
                        "Enrolment of project '%s' refused with outcome 'enrolment_invalid': %s"),
                Arguments.of(PmMcpLogMessages.WARN.ENROLMENT_PAIR_DISCARDED, "PM_MCP-166",
                        "Incomplete staged write of the enrolment of project '%s' discarded, "
                                + "the enrolment is as it was before the write: %s"),
                Arguments.of(PmMcpLogMessages.WARN.ENROLMENT_AUDIT_REPLAY_FAILED, "PM_MCP-167",
                        "Replay of the owed audit records of unenrolled projects failed: %s"));
    }

    @ParameterizedTest(name = "{1}")
    @MethodSource("enrolmentPairAndPolicyRecords")
    @DisplayName("the records of the enrolment pair, the enrolment policy and the owed-audit replay")
    void enrolmentPairAndPolicy(LogRecord logRecord, String identifier, String template) {
        assertAll(
                () -> assertEquals(identifier, logRecord.resolveIdentifierString()),
                () -> assertEquals(template, logRecord.getTemplate()));
    }

    private static List<LogRecord> records(String level) throws ReflectiveOperationException {
        var holder = Class.forName(PmMcpLogMessages.class.getName() + "$" + level);
        var records = new ArrayList<LogRecord>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && LogRecord.class.isAssignableFrom(field.getType())) {
                records.add((LogRecord) field.get(null));
            }
        }
        return records;
    }
}
