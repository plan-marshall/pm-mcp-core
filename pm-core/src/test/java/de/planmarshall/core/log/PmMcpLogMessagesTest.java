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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import de.cuioss.tools.logging.LogRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The registry numbers every message once (PM-IMPL-1): two messages with one identifier cannot be told apart in a
 * log, and an identifier outside the range of its level reads as another level.
 */
@DisplayName("PmMcpLogMessages")
class PmMcpLogMessagesTest {

    @ParameterizedTest(name = "{0} holds identifiers {1} to {2}")
    @CsvSource({"INFO, 1, 99", "WARN, 100, 199", "ERROR, 200, 299"})
    @DisplayName("every message lies in the range of its level and carries the prefix")
    void ranges(String level, int first, int last) throws ReflectiveOperationException {
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
    void unique() throws ReflectiveOperationException {
        var all = new ArrayList<LogRecord>();
        for (String level : List.of("INFO", "WARN", "ERROR")) {
            all.addAll(records(level));
        }
        var seen = new HashSet<Integer>();

        for (LogRecord logRecord : all) {
            assertTrue(seen.add(logRecord.getIdentifier()), logRecord.resolveIdentifierString());
        }
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
