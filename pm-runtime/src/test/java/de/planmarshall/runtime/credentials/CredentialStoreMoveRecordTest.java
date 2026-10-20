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
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;

/**
 * The record of the open credential-store operation (PM-CRED-2, PM-IMPL-2): written in its one shape and read back
 * unchanged; every other shape is refused as unreadable, another format version as outdated or newer.
 */
@DisplayName("CredentialStoreMoveRecord: the record of the open operation")
class CredentialStoreMoveRecordTest {

    private static final Path FILE = Path.of("/pm-base/state/credential-store-move.json");
    private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");
    private static final TransactionId TRANSACTION = new TransactionId("018f0000-0000-7000-8000-000000000001");

    private static CredentialStoreMoveRecord read(String document) {
        return CredentialStoreMoveRecord.read(FILE, document.getBytes(StandardCharsets.UTF_8));
    }

    /** A move record, the operation this test mostly reads. */
    private static CredentialStoreMoveRecord move(Store from, Store to, Phase phase, boolean targetHeldKey) {
        return new CredentialStoreMoveRecord(from, to, STARTED, phase, targetHeldKey, Operation.MOVE, TRANSACTION);
    }

    @Test
    @DisplayName("a record of a move is written in its one shape and read back unchanged")
    void roundTrip() {
        var moveRecord = move(Store.OS, Store.FILE, Phase.COPYING, false);

        var document = new String(moveRecord.write(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertEquals("""
                        {"format_version":1,"from":"os","to":"file","started_at":"2026-10-17T15:00:00Z",\
                        "phase":"copying","target_held_key":false,"operation":"move",\
                        "transaction_id":"018f0000-0000-7000-8000-000000000001"}
                        """, document),
                () -> assertEquals(moveRecord, read(document)));
    }

    @Test
    @DisplayName("a record of a first recording is written in the same shape and read back with its operation")
    void firstRecordingRoundTrip() {
        var firstRecording = new CredentialStoreMoveRecord(Store.FILE, Store.OS, STARTED, Phase.COPYING, true,
                Operation.FIRST_RECORDING, TRANSACTION);

        var document = new String(firstRecording.write(), StandardCharsets.UTF_8);

        assertAll(
                () -> assertEquals("""
                        {"format_version":1,"from":"file","to":"os","started_at":"2026-10-17T15:00:00Z",\
                        "phase":"copying","target_held_key":true,"operation":"first_recording",\
                        "transaction_id":"018f0000-0000-7000-8000-000000000001"}
                        """, document),
                () -> assertEquals(firstRecording, read(document)));
    }

    @Test
    @DisplayName("copying() starts the operation in phase copying and switched() changes only the phase")
    void phases() {
        var copying = CredentialStoreMoveRecord.copying(Operation.FIRST_RECORDING, Store.FILE, Store.OS, STARTED, true,
                TRANSACTION);

        assertAll(
                () -> assertEquals(new CredentialStoreMoveRecord(Store.FILE, Store.OS, STARTED, Phase.COPYING, true,
                        Operation.FIRST_RECORDING, TRANSACTION), copying),
                () -> assertEquals(new CredentialStoreMoveRecord(Store.FILE, Store.OS, STARTED, Phase.SWITCHED, true,
                        Operation.FIRST_RECORDING, TRANSACTION), copying.switched()));
    }

    @Test
    @DisplayName("the record lies below the state directory of the base")
    void file() {
        assertEquals(Path.of("/pm-base/state/credential-store-move.json"),
                CredentialStoreMoveRecord.file(Path.of("/pm-base")));
    }

    @Test
    @DisplayName("a record from a store to itself is not a record")
    void sameStores() {
        assertThrows(IllegalArgumentException.class, () -> move(Store.OS, Store.OS, Phase.COPYING, false));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,"
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":\"false\",\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"keyring\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"done\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"carry\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"not-a-transaction-id\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018F0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"os\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T17:00:00+02:00\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":\"os\",\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\",\"entries\":2}",
            "{\"format_version\":1,\"from\":\"os\",\"from\":\"file\",\"to\":\"file\","
                    + "\"started_at\":\"2026-10-17T15:00:00Z\",\"phase\":\"copying\",\"target_held_key\":false,"
                    + "\"operation\":\"move\",\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
            "{\"format_version\":1,\"from\":1,\"to\":\"file\",\"started_at\":\"2026-10-17T15:00:00Z\","
                    + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                    + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}",
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
                        + "\"phase\":\"copying\",\"target_held_key\":false,\"operation\":\"move\","
                        + "\"transaction_id\":\"018f0000-0000-7000-8000-000000000001\"}"));

        assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, refusal.outcome());
    }
}
