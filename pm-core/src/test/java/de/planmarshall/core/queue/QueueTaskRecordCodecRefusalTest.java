/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.StoreRefusal;

/**
 * The queue task codec accepts exactly one shape: another format version, an undeclared field, a missing required
 * field, a repeated field, a wrong type, an unknown enum value and a record that breaks a combination are refused with a
 * {@link StoreRefusal} naming the file.
 */
@SuppressWarnings("java:S5778")
// the assertion table names one throwing builder call per case, by design
@DisplayName("Queue task record codec refusals")
class QueueTaskRecordCodecRefusalTest {

    private static final Path FILE = Path.of("queue", "qt-0000000000000001.json");

    private static String valid() {
        return new String(QueueTaskRecordCodec.write(QueueFixtures.queued("qt-0000000000000001").build()), UTF_8);
    }

    private static StoreRefusal read(String document) {
        return assertThrows(StoreRefusal.class, () -> QueueTaskRecordCodec.read(FILE, document.getBytes(UTF_8)));
    }

    @Test
    @DisplayName("another format version is refused")
    void formatVersion() {
        var document = valid().replace("\"format_version\":1", "\"format_version\":2");
        read(document);
    }

    @Test
    @DisplayName("an undeclared field is refused")
    void undeclared() {
        read(valid().replace("\"role\":", "\"unknown\":1,\"role\":"));
    }

    @Test
    @DisplayName("a missing required field is refused")
    void missing() {
        read(valid().replace("\"form_downgraded\":false,", ""));
    }

    @Test
    @DisplayName("a repeated field is refused")
    void repeated() {
        read(valid().replace("\"role\":", "\"role\":\"reviewer\",\"role\":"));
    }

    @Test
    @DisplayName("a wrong type is refused")
    void wrongType() {
        read(valid().replace("\"status\":\"queued\"", "\"status\":1"));
    }

    @Test
    @DisplayName("an unknown enum value is refused")
    void unknownEnum() {
        read(valid().replace("\"status\":\"queued\"", "\"status\":\"sleeping\""));
    }

    @Test
    @DisplayName("a record that breaks a combination is refused after it is read")
    void invalidCombination() {
        read(valid().replace("\"status\":\"queued\"", "\"status\":\"closed\""));
    }

    @Test
    @DisplayName("content after the document is refused")
    void trailingContent() {
        read(valid() + "{}");
    }
}
