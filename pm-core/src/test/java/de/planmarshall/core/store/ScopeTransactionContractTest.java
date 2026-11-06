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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The value-object contract of a target a scope transaction collects before its commit:
 * {@link ScopeTransaction.PendingTarget} writes its own {@code equals} and {@code hashCode} over its path, its mode,
 * the bytes of its content and its absence flag. The contract checks equality, hashing and the string form for every
 * generated target.
 * <p>
 * The serializable contract of {@link ShouldHandleObjectContracts} is replaced by its deliberate opposite: a pending
 * target is an in-memory value held only until the commit, so the type carries no serialized form. Making it
 * {@link Serializable} would be a production change outside this plan.
 */
@EnableGeneratorController
class ScopeTransactionContractTest implements ShouldHandleObjectContracts<ScopeTransaction.PendingTarget> {

    @Override
    public ScopeTransaction.PendingTarget getUnderTest() {
        var path = Generators.letterStrings(1, 20).next() + "/" + Generators.letterStrings(1, 20).next();
        return new ScopeTransaction.PendingTarget(path, Generators.enumValues(TransactionJournal.Mode.class).next(),
                Generators.strings(0, 40).next().getBytes(UTF_8), Generators.booleans().next());
    }

    @Test
    @DisplayName("two targets with equal bytes in separate arrays are equal and hash alike")
    void shouldCompareContentByValue() {
        var first = getUnderTest();
        var second = new ScopeTransaction.PendingTarget(first.path(), first.mode(), first.content().clone(),
                first.mustBeAbsent());

        assertAll(
                () -> assertEquals(first, second, "equal bytes in two arrays should be equal"),
                () -> assertEquals(second, first, "equality should be symmetric"),
                () -> assertEquals(first.hashCode(), second.hashCode(), "equal targets should hash alike"));
    }

    @Override
    @Test
    @DisplayName("a pending target stays an in-memory value and carries no serialized form")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(ScopeTransaction.PendingTarget.class),
                "A pending target is an in-memory value, not serialized");
    }
}
