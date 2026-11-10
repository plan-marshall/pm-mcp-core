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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;

/**
 * Proves that the builder of {@link CredentialStoreMoveRecord} builds the value its canonical constructor builds,
 * including the null refusal for its two stores. Every value is generated; the two distinct stores are the contract
 * pair a move runs between.
 */
@EnableGeneratorController
@DisplayName("CredentialStoreMoveRecord builder")
class CredentialStoreMoveRecordRecordsTest {

    @Test
    @DisplayName("the builder builds the move record the constructor builds")
    void credentialStoreMoveRecordBuilderEqualsConstructor() {
        var from = Store.OS;
        var to = Store.FILE;
        var startedAt = Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next());
        var phase = Generators.enumValues(CredentialStoreMoveRecord.Phase.class).next();
        var targetHeldKey = Generators.booleans().next();
        var operation = Generators.enumValues(CredentialStoreMoveRecord.Operation.class).next();
        var transactionId = TransactionId.generate(Clock.fixed(startedAt, ZoneOffset.UTC));

        var constructed = new CredentialStoreMoveRecord(from, to, startedAt, phase, targetHeldKey, operation,
                transactionId);
        var built = CredentialStoreMoveRecord.builder()
                .from(from)
                .to(to)
                .startedAt(startedAt)
                .phase(phase)
                .targetHeldKey(targetHeldKey)
                .operation(operation)
                .transactionId(transactionId)
                .build();

        assertEquals(constructed, built, "the builder builds the same move record");
    }

    @Test
    @DisplayName("the builder refuses a missing from store like the constructor")
    void credentialStoreMoveRecordBuilderRefusesMissingFrom() {
        var startedAt = Instant.ofEpochMilli(Generators.longs(0, 1_900_000_000_000L).next());

        var builder = CredentialStoreMoveRecord.builder()
                .to(Store.FILE)
                .startedAt(startedAt)
                .phase(Generators.enumValues(CredentialStoreMoveRecord.Phase.class).next())
                .targetHeldKey(Generators.booleans().next())
                .operation(Generators.enumValues(CredentialStoreMoveRecord.Operation.class).next())
                .transactionId(TransactionId.generate(Clock.fixed(startedAt, ZoneOffset.UTC)));
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("from", refusal.getMessage(), "the refusal names the missing from store");
    }
}
