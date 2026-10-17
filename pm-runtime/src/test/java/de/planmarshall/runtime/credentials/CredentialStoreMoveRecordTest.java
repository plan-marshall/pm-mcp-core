/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;

/**
 * The record of the open credential-store move (PM-CRED-2, PM-IMPL-2): written in its one shape and read back
 * unchanged; every other shape is refused as unreadable, another format version as outdated or newer.
 */
@DisplayName("CredentialStoreMoveRecord: the record of the open move")
class CredentialStoreMoveRecordTest {

    private static final Path FILE = Path.of("/pm-base/state/credential-store-move.json");
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");

    private static CredentialStoreMoveRecord read(String document) {
        return CredentialStoreMoveRecord.read(FILE, document.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("a record is written in its one shape and read back unchanged")
    void roundTrip() {
        var moveRecord = new CredentialStoreMoveRecord(Store.OS, Store.FILE, STARTED, Phase.COPYING);

        var document = new String(moveRecord.write(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertEquals("""
                        {"format_version":1,"from":"os","to":"file","started_at":"2026-10-17T15:00:00Z",\
                        "phase":"copying"}
                        """, document),
                () -> assertEquals(moveRecord, read(document)));
    }

    @Test
    @DisplayName("switched() keeps the stores and the start and changes only the phase")
    void switched() {
        var moveRecord = new CredentialStoreMoveRecord(Store.FILE, Store.OS, STARTED, Phase.COPYING);

        assertEquals(new CredentialStoreMoveRecord(Store.FILE, Store.OS, STARTED, Phase.SWITCHED),
                moveRecord.switched());
    }

    @Test
    @DisplayName("the record lies below the state directory of the base")
    void file() {
        assertEquals(Path.of("/pm-base/state/credential-store-move.json"),
                CredentialStoreMoveRecord.file(Path.of("/pm-base")));
    }

    @Test
    @DisplayName("a move from a store to itself is not a record")
    void sameStores() {
        assertThrows(IllegalArgumentException.class,
                () -> new CredentialStoreMoveRecord(Store.OS, Store.OS, STARTED, Phase.COPYING));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\"}",
            "{\"format_version\":1,\"from\":\"keyring\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"done\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"os\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T17:00:00+02:00\","
                    + "\"phase\":\"copying\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"entries\":2}",
            "{\"format_version\":1,\"from\":\"os\",\"from\":\"file\",\"to\":\"file\","
                    + "\"started_at\":\"2026-10-17T15:00:00Z\",\"phase\":\"copying\"}",
            "{\"format_version\":1,\"from\":1,\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\"}",
            "[]"})
    @DisplayName("a document outside the one shape is refused as unreadable, naming the file")
    void unreadable(String document) {
        var refusal = assertThrows(StoreRefusal.class, () -> read(document));

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome()),
                () -> assertEquals(FILE, refusal.file()));
    }

    @Test
    @DisplayName("a record of a newer format version is refused as newer")
    void newerVersion() {
        var refusal = assertThrows(StoreRefusal.class, () -> read(
                "{\"format_version\":2,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                        + "\"phase\":\"copying\"}"));

        assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, refusal.outcome());
    }
}
