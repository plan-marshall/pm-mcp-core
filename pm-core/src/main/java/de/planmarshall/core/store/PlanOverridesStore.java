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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.store.PlanOverrides.UpdatedBy;

/**
 * The reader and writer of a plan's {@code overrides.json}, the plan's own values (Store Schemas § Plan Overrides,
 * Workflow DSL § Plan Overrides): {@code plans/<plan_id>/overrides.json}, written through a {@link ScopeTransaction} of
 * the plan the caller holds and commits.
 * <p>
 * The document is absent until the first value is set. A write names the revision it was made against; the first change
 * wins, and a later one against an older revision is refused with {@link SettingsConflict} carrying the current revision
 * and the current document, so nothing is merged and nothing is overwritten unseen. The write itself is the caller's
 * act; composing the instance version of the event {@code override}, auditing it and answering with that version are the
 * transaction's, and happen around this store.
 *
 * @since 0.1
 */
public final class PlanOverridesStore {

    /** The name of the plan's overrides document, relative to the plan's directory. */
    public static final String FILE_NAME = "overrides.json";

    private final JournalScope scope;

    /**
     * @param scope the scope of the plan
     * @throws IllegalArgumentException if the scope is no plan scope
     */
    public PlanOverridesStore(JournalScope scope) {
        this.scope = Objects.requireNonNull(scope, "the scope");
        if (scope.type() != JournalScopeType.PLAN) {
            throw new IllegalArgumentException("The plan overrides live in a plan scope, not " + scope.type());
        }
    }

    /**
     * Reads the document without taking the lock.
     *
     * @return the plan's own values; {@link PlanOverrides#absent()} when the plan sets none
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public PlanOverrides read() {
        var file = file();
        final byte[] document;
        try {
            document = Files.readAllBytes(file);
        } catch (NoSuchFileException e) {
            return PlanOverrides.absent();
        } catch (IOException e) {
            throw new UncheckedIOException("The plan overrides cannot be read: " + file, e);
        }
        return PlanOverridesCodec.read(file, document);
    }

    /**
     * Replaces the document with the values the operator set, at the revision after the one it names, and stages it in
     * the transaction. The caller holds the plan's transaction, so the read of the current document inside it is the
     * document no other writer can change before the commit.
     *
     * @param transaction the open transaction of the plan
     * @param values      the values to write; its revision and stamp are replaced by this call's
     * @param revision    the revision the operator read
     * @param updatedBy   the channel and device of the write
     * @param updatedAt   the instant of the write
     * @return the document as written, at the next revision
     * @throws SettingsConflict         if the document holds another revision than {@code revision}
     * @throws StoreRefusal             if the document is unreadable or in another format version
     * @throws UncheckedIOException     if the document cannot be read
     * @throws IllegalArgumentException if the transaction is not the plan's, or a value is not one the schema admits
     */
    public PlanOverrides write(ScopeTransaction transaction, PlanOverrides values, long revision, UpdatedBy updatedBy,
            Instant updatedAt) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(values, "values");
        Objects.requireNonNull(updatedBy, "updatedBy");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (!transaction.scope().equals(scope)) {
            throw new IllegalArgumentException("The plan overrides are written by the plan's own transaction: "
                    + scope.scopeId());
        }
        var current = read();
        if (current.revision() != revision) {
            throw new SettingsConflict(file(), revision, current.revision(), current);
        }
        var written = PlanOverrides.builder()
                .revision(current.revision() + 1)
                .roles(values.roles())
                .axes(values.axes())
                .posture(values.posture())
                .baseBranch(values.baseBranch())
                .integrations(values.integrations())
                .providers(values.providers())
                .updatedAt(updatedAt)
                .updatedBy(updatedBy)
                .build();
        transaction.replace(FILE_NAME, PlanOverridesCodec.write(written));
        return written;
    }

    /** @return the document's file, absolute and normalized */
    public Path file() {
        return scope.resolve(FILE_NAME);
    }
}
