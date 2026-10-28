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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.store.OutlineStore.Revision;

/**
 * The writer and reader of a plan's {@code outline.json} (Store Schemas § Outline Record): the store
 * {@link OutlineRenderer} renders {@code outline.md} from.
 * <p>
 * {@link #accept(ScopeTransaction, Revision)} reads the store and adds the accepted store to the caller's
 * {@link ScopeTransaction} as one replace target, so the acceptance, the other writes of the same decision and the
 * scope's digest sidecar are journaled together. The directory exists before the first write; this type does not
 * create it.
 *
 * @since 0.1
 */
public final class OutlineStoreFile {

    /** The path of the store below the plan's path base. */
    public static final String STORE_PATH = "outline.json";

    private final JournalScope scope;

    /**
     * @param scope the plan scope whose outline this store holds
     * @throws IllegalArgumentException if the scope is not a plan scope
     */
    public OutlineStoreFile(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("An outline store lives in a plan scope: " + scope.type());
        }
    }

    /**
     * Accepts the next revision and adds the accepted store to the caller's transaction.
     *
     * @param transaction an open transaction of this store's scope, opened by the caller's lock transaction
     * @param next        the revision the task submitted
     * @return the store after the acceptance
     * @throws IllegalArgumentException if the revision does not follow the current one, or the store is a target of
     *                                  the transaction already
     * @throws StoreRefusal             if the store is in another format version or unreadable
     * @throws UncheckedIOException     if the store exists and cannot be read
     * @throws IllegalStateException    if the transaction was committed
     */
    public OutlineStore accept(ScopeTransaction transaction, Revision next) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(next, "next");
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("The transaction of the %s scope %s is not the transaction of the plan "
                    + "scope %s".formatted(transaction.scope().type().wireName(), transaction.scope().scopeId(),
                    scope.scopeId()));
        }
        var file = store();
        var before = StoreSnapshot.read(file).content().map(document -> OutlineCodec.read(file, document)).orElse(null);
        var after = before == null ? OutlineStore.first(next) : before.accept(next);
        transaction.replace(STORE_PATH, OutlineCodec.write(after));
        return after;
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read.
     *
     * @return the store; empty when the plan holds no accepted outline yet
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<OutlineStore> snapshot() {
        var file = store();
        return StoreSnapshot.read(file).content().map(document -> OutlineCodec.read(file, document));
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return scope.resolve(STORE_PATH);
    }
}
