/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.log;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.cuioss.tools.logging.LogRecord;
import de.planmarshall.core.log.CoreLogMessages;
import de.planmarshall.core.log.LogMessageListing;

/**
 * The holder numbers every message of {@code pm-workflow} once (PM-IMPL-1): two messages with one identifier cannot
 * be told apart in a log, and an identifier outside the range of its level reads as another level.
 */
// Every datum in this class is a contract: the log-message identifiers, their level ranges and their templates.
@DisplayName("WorkflowLogMessages")
class WorkflowLogMessagesTest {

    /** The generated listing of this holder, from the module directory the tests run in. */
    private static final Path GENERATED = Path.of("..", "doc", "log-messages", "pm-workflow.adoc");

    /** Setting this system property regenerates the committed listing instead of only comparing it. */
    private static final String REGENERATE = "planmarshall.log-messages.regenerate";

    @ParameterizedTest(name = "{0} holds identifiers {1} to {2}")
    @CsvSource({"INFO, 1, 99", "WARN, 100, 199", "ERROR, 200, 299"})
    @DisplayName("every message lies in the range of its level and carries the prefix")
    void ranges(String level, int first, int last) throws Exception {
        var records = records(level);

        assertFalse(records.isEmpty(), level);
        for (LogRecord logRecord : records) {
            assertEquals(WorkflowLogMessages.PREFIX, logRecord.getPrefix());
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

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "PLAN_INSTANCE_VERSION_RECORDED|INFO|2|Plan instance version %s of plan '%s' recorded for the event '%s'",
            "PLAN_INSTANCE_COMPOSITION_REFUSED|WARN|101|Composition '%s' of plan '%s' refused with '%s', the version "
                    + "in force is kept"})
    @DisplayName("the plan instance messages carry their identifier and template")
    void planInstanceMessages(String name, String level, int identifier, String template) throws Exception {
        var holder = Class.forName(WorkflowLogMessages.class.getName() + "$" + level);
        var logRecord = (LogRecord) holder.getField(name).get(null);

        assertEquals(WorkflowLogMessages.PREFIX, logRecord.getPrefix());
        assertEquals(identifier, logRecord.getIdentifier());
        assertEquals(template, logRecord.getTemplate());
    }

    @Test
    @DisplayName("the prefix is not the prefix of the holder of pm-core")
    void prefixOwn() {
        assertNotEquals(CoreLogMessages.PREFIX, WorkflowLogMessages.PREFIX);
    }

    @Test
    @DisplayName("the committed listing is the holder's current generation")
    void generatedListingIsCurrent() throws Exception {
        var expected = LogMessageListing.render(WorkflowLogMessages.class).getBytes(UTF_8);
        if (Boolean.getBoolean(REGENERATE)) {
            Files.write(GENERATED, expected);
        }
        var actual = Files.readAllBytes(GENERATED);

        assertTrue(LogMessageListing.same(expected, actual), "the committed listing matches the holder");
    }

    @Test
    @DisplayName("the byte check names a file with one identifier changed")
    void byteCheckNamesAChangedListing(@TempDir Path tempDir) throws Exception {
        var expected = LogMessageListing.render(WorkflowLogMessages.class);
        var tampered = expected.replace("PM_WORKFLOW-1 |", "PM_WORKFLOW-99 |");
        var changed = tempDir.resolve("pm-workflow.adoc");
        Files.writeString(changed, tampered, UTF_8);

        assertAll(
                () -> assertNotEquals(expected, tampered, "the control changes one identifier"),
                () -> assertFalse(LogMessageListing.same(expected.getBytes(UTF_8), Files.readAllBytes(changed)),
                        "a changed identifier is a mismatch"));
    }

    private static List<LogRecord> records(String level) throws ReflectiveOperationException {
        var holder = Class.forName(WorkflowLogMessages.class.getName() + "$" + level);
        var records = new ArrayList<LogRecord>();
        for (Field field : holder.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && LogRecord.class.isAssignableFrom(field.getType())) {
                records.add((LogRecord) field.get(null));
            }
        }
        return records;
    }
}
