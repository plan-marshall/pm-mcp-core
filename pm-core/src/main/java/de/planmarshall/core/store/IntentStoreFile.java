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

import de.planmarshall.core.store.IntentAcceptance.Accepted;
import de.planmarshall.core.store.IntentAcceptance.Context;
import de.planmarshall.core.store.IntentAcceptance.Outcome;
import de.planmarshall.core.store.IntentStore.Revision;

/**
 * The writer and reader of a plan's {@code intent.json} beside {@code request.md} (Filesystem Layout § The Source
 * Directory; Store Schemas § Intent Record).
 * <p>
 * {@link #accept(ScopeTransaction, IntentSubmission, Revision, Context)} reads the store, runs the
 * {@link IntentAcceptance} checks and adds the accepted store to the caller's {@link ScopeTransaction} as one replace
 * target, so the acceptance, the other writes of the same decision and the scope's digest sidecar are journaled
 * together and the store is never written outside the transaction. A refusal adds no target. The directory exists
 * before the first write; this type does not create it.
 *
 * @since 0.1
 */
public final class IntentStoreFile {

    /** The path of the store below the plan's path base. */
    public static final String STORE_PATH = "intent.json";

    private final JournalScope scope;

    /**
     * @param scope the plan scope whose intent record this store holds
     * @throws IllegalArgumentException if the scope is not a plan scope
     */
    public IntentStoreFile(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("An intent store lives in a plan scope: " + scope.type());
        }
    }

    /**
     * Accepts a submission and adds the accepted store to the caller's transaction.
     *
     * @param transaction an open transaction of this store's scope, opened by the caller's lock transaction
     * @param submission  the entry point the record arrived through
     * @param next        the revision the task submitted
     * @param context     what the checks need beyond the record
     * @return the accepted store, or the refusal with its detail
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     * @throws IllegalArgumentException if the store is a target of the transaction already
     * @throws IllegalStateException    if the transaction was committed
     */
    public Outcome accept(ScopeTransaction transaction, IntentSubmission submission, Revision next,
            Context context) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(context, "context");
        var file = store();
        var before = StoreSnapshot.read(file).content().map(document -> IntentCodec.read(file, document)).orElse(null);
        var outcome = IntentAcceptance.accept(before, submission, next, context);
        if (outcome instanceof Accepted accepted) {
            transaction.replace(STORE_PATH, IntentCodec.write(accepted.store()));
        }
        return outcome;
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read.
     *
     * @return the store; empty when the plan holds no accepted record yet
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<IntentStore> snapshot() {
        var file = store();
        return StoreSnapshot.read(file).content().map(document -> IntentCodec.read(file, document));
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return scope.resolve(STORE_PATH);
    }
}
