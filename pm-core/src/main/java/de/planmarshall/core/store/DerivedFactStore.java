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

import org.jspecify.annotations.Nullable;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.DerivedFact.DerivationClass;
import de.planmarshall.core.store.DerivedFact.State;
import de.planmarshall.core.store.DerivedFactDerivation.Derivation;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Derived;
import de.planmarshall.core.store.DerivedFactDerivation.DerivationOutcome.Failed;

/**
 * The reader and writer of a project's {@code derived-facts.json} (Store Schemas § Derived Facts Store; PM-EXT-6,
 * PM-IMPL-2, PM-IMPL-7): the cache of the values the server can observe, and the rules that decide when an entry is
 * derived again.
 * <p>
 * <em>The decision table</em> of {@link #refresh}: an entry is derived again when it is absent, when its
 * {@code input_digest} differs from the digest the caller passes, when it is a {@code network} entry at or past
 * {@code derived_at + 86400 s}, or when it is {@code stale} or {@code failed}; otherwise the stored entry is returned
 * and the derivation is never called. The validity period is the constant of the specification, not a setting.
 * <p>
 * <em>The lock.</em> {@link #snapshot()} reads without a lock, as every lock-free snapshot read does. Every write is
 * one short transaction on the store's own {@code locks/derived-facts.lock} key: the derivation runs outside the lock —
 * it may be a provider call — and the transaction re-reads the file under the lock, changes the one entry and writes
 * it through {@link AtomicStoreFile}. A refresh made inside a scope transaction therefore takes the derived-facts lock
 * after the scope lock, which is the total order of {@link LockLevel}. A result another refresher stored meanwhile for
 * the same digest with a later {@code derived_at} is kept and returned, so an older result never overwrites a newer one
 * (ADR {@code core-store/056}).
 * <p>
 * <em>Failure.</em> A failed derivation keeps the last value with {@code state: stale} and records the failure, or
 * writes {@code failed} when no value was ever derived. The last good {@code input_digest} is kept on {@code stale}, so
 * the next call still sees the digest mismatch and retries without a back-off (ADR {@code core-store/057}). A
 * {@code failed} entry names the placeholder derivation {@code unknown} and the first class its kind admits, because
 * the schema requires a source on every entry and no derivation produced a value.
 * <p>
 * <em>The operator-only fact.</em> {@link #modelFacing()} is the only projection a model-facing representation,
 * resource or tool result may be built from; it never holds a key whose kind is {@link DerivedFactKind#operatorOnly()}
 * ({@code agent_identity}), registered or not (ADR {@code core-store/060}). {@link #operatorFacts()} is the whole set
 * for the operator's own clients. Content that does not parse, and another format version, are a {@link StoreRefusal}
 * naming the file; a file that cannot be read at the filesystem level is an {@link UncheckedIOException} from the
 * snapshot read. Neither is a silent rebuild (ADR {@code core-store/059}).
 * <p>
 * The directory of the store exists before the first write and this type does not create it, the pattern of
 * {@link BuildTimingsStore}. No concrete kind is registered here: the composition that owns the store creates the
 * {@link DerivedFactKinds} registry and registers the kinds it derives.
 *
 * @since 0.1
 */
public final class DerivedFactStore {

    /** The validity period of a network derivation (Timeouts &amp; Budgets § Derived Facts: {@code 86400}). */
    public static final Duration NETWORK_VALIDITY = Duration.ofSeconds(86400);

    /** The derivation identifier a failed entry carries when no derivation produced a value. */
    public static final String UNKNOWN_DERIVATION = "unknown";

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;
    private final DerivedFactKinds kinds;

    /**
     * @param storeFile   the store file with the key of the derived-facts lock
     * @param lockManager the lock manager of the runtime
     * @param kinds       the registry of the value schemas the consumer registered
     * @throws IllegalArgumentException if the key of the store file is not a derived-facts lock
     */
    public DerivedFactStore(AtomicStoreFile storeFile, LockManager lockManager, DerivedFactKinds kinds) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        if (storeFile.lockKey().level() != LockLevel.DERIVED_FACTS) {
            throw new IllegalArgumentException(
                    "A derived-facts store is guarded by a derived-facts lock: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read. This is the operator-side view and the input
     * of the two projections; a model-facing representation uses {@link #modelFacing()}.
     *
     * @return the store; the empty one if the store does not exist
     * @throws StoreRefusal         if the store is in another format version or its content does not parse
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public DerivedFacts snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode).orElseGet(DerivedFacts::empty);
    }

    /**
     * @param key the key of a fact
     * @return the entry of the key; empty if the store holds no entry for it. The entry is the raw record — an
     *         operator-side and server-side read, never a model-facing one; a model-facing representation uses
     *         {@link #modelFacing()}
     * @throws StoreRefusal         if the store is in another format version or its content does not parse
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Optional<DerivedFact> fact(DerivedFactKey key) {
        Objects.requireNonNull(key, "key");
        return snapshot().fact(key);
    }

    /**
     * Reads one fact as a {@link DerivedFactReading}: a current value inside its validity period, a stale or failed
     * entry, an expired network entry, or an absent one. The instant is the caller's; the store reads no wall clock.
     * This is a server-side and operator-side read; a model-facing representation uses {@link #modelFacing()}.
     *
     * @param key the key of the fact
     * @param now the instant the validity period is decided at
     * @return the reading of the fact
     * @throws StoreRefusal         if the store is in another format version or its content does not parse
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public DerivedFactReading reading(DerivedFactKey key, Instant now) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(now, "now");
        return readingOf(key, snapshot().fact(key).orElse(null), now);
    }

    /**
     * Refreshes one fact under the decision table and returns its reading after the attempt. The derivation runs
     * outside the lock; the lock is held for the re-read and the write of the one entry only.
     * <p>
     * A derived value is validated against the schema registered for its kind, and its class and qualifier against the
     * kind's specification. A value that violates any of them becomes a failed derivation with that reason and is never
     * written as {@code current}.
     *
     * @param key         the key of the fact
     * @param inputDigest the SHA-256 over the canonical derivation inputs, 64 lower-case hexadecimal digits
     * @param derivation  the derivation to call when the decision table says so
     * @param now         the instant of the attempt
     * @return the reading after the attempt
     * @throws IllegalArgumentException if the digest does not match {@link DerivedFact#INPUT_DIGEST_TEXT}
     * @throws StoreRefusal             if the store is in another format version or its content does not parse
     * @throws UncheckedIOException     if the store exists and cannot be read
     * @throws InternalFaultException   if the lock of the store must not be acquired by the transaction
     */
    public DerivedFactReading refresh(DerivedFactKey key, String inputDigest, Derivation derivation, Instant now) {
        Objects.requireNonNull(key, "key");
        requireDigest(inputDigest);
        Objects.requireNonNull(derivation, "derivation");
        Objects.requireNonNull(now, "now");
        var stored = snapshot().fact(key).orElse(null);
        if (!mustDerive(stored, inputDigest, now)) {
            return readingOf(key, stored, now);
        }
        var outcome = Objects.requireNonNull(derivation.derive(), "The derivation outcome");
        return update(key, inputDigest, validated(key, outcome), now);
    }

    /**
     * The facts a model-facing representation, resource or tool result may be built from: every entry whose kind is
     * not {@link DerivedFactKind#operatorOnly()}, qualified keys of such a kind included. The exclusion is a property
     * of the kind enumeration and does not depend on a registration.
     * <p>
     * Every representation, the {@code pm://project/config} resource and every tool result of the assembly
     * ({@code plan-marshall-mcp}) must be built from this projection; {@code agent_identity} never leaves the
     * server's own and the operator's surfaces.
     *
     * @return the model-facing projection of {@link #snapshot()}
     * @throws StoreRefusal         if the store is in another format version or its content does not parse
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public DerivedFacts modelFacing() {
        var visible = new LinkedHashMap<DerivedFactKey, DerivedFact>();
        snapshot().facts().forEach((key, fact) -> {
            if (!kinds.isOperatorOnly(key.kind())) {
                visible.put(key, fact);
            }
        });
        return new DerivedFacts(visible);
    }

    /**
     * The whole set of facts, operator-only entries included: the operator's own view. A model-facing representation
     * uses {@link #modelFacing()} instead.
     *
     * @return every entry of {@link #snapshot()}
     * @throws StoreRefusal         if the store is in another format version or its content does not parse
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public DerivedFacts operatorFacts() {
        return snapshot();
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private DerivedFactReading update(DerivedFactKey key, String inputDigest, DerivationOutcome outcome, Instant now) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var before = storeFile.read(transaction).map(this::decode).orElseGet(DerivedFacts::empty);
            var stored = before.fact(key).orElse(null);
            if (stored != null && stored.inputDigest().equals(inputDigest)
                    && stored.derivedAt().isAfter(now)) {
                return readingOf(key, stored, now);
            }
            var entry = entryOf(key, inputDigest, outcome, stored, now);
            var facts = new LinkedHashMap<>(before.facts());
            facts.put(key, entry);
            storeFile.write(transaction, DerivedFactsCodec.write(new DerivedFacts(facts)));
            return readingOf(key, entry, now);
        }
    }

    private DerivedFact entryOf(DerivedFactKey key, String inputDigest, DerivationOutcome outcome,
            @Nullable DerivedFact stored, Instant now) {
        if (outcome instanceof Derived(var value, var derivation, var sourceClass)) {
            return DerivedFact.builder()
                    .kind(key.kind())
                    .value(value)
                    .source(new DerivedFact.Source(derivation, sourceClass))
                    .inputDigest(inputDigest)
                    .derivedAt(now)
                    .state(State.CURRENT)
                    .build();
        }
        var failure = new DerivedFact.Failure(truncate(((Failed) outcome).reason()), now);
        if (stored != null && stored.value() != null) {
            return stored.toBuilder().state(State.STALE).failure(failure).build();
        }
        return DerivedFact.builder()
                .kind(key.kind())
                .source(placeholderSource(key.kind()))
                .inputDigest(inputDigest)
                .derivedAt(now)
                .state(State.FAILED)
                .failure(failure)
                .build();
    }

    private DerivationOutcome validated(DerivedFactKey key, DerivationOutcome outcome) {
        if (outcome instanceof Failed) {
            return outcome;
        }
        var derived = (Derived) outcome;
        var spec = kinds.spec(key.kind());
        if (spec.isEmpty()) {
            return new Failed("The fact kind '%s' has no registered value schema".formatted(key.kind().wireName()));
        }
        if (key.qualifier() != null && !spec.get().qualifierAllowed()) {
            return new Failed(
                    "The fact kind '%s' admits no qualifier".formatted(key.kind().wireName()));
        }
        if (!spec.get().classes().contains(derived.sourceClass())) {
            return new Failed("The fact kind '%s' does not admit the source class '%s'".formatted(
                    key.kind().wireName(), derived.sourceClass().wireName()));
        }
        try {
            new DerivedFact.Source(derived.derivation(), derived.sourceClass());
        } catch (IllegalArgumentException _) {
            return new Failed("The derivation identifier is outside its grammar: " + derived.derivation());
        }
        var violations = kinds.validate(key.kind(), derived.value());
        if (!violations.isEmpty()) {
            return new Failed("The value violates its schema: " + String.join("; ", violations));
        }
        return derived;
    }

    private DerivedFact.Source placeholderSource(DerivedFactKind kind) {
        var admitted = kinds.spec(kind).map(DerivedFactKindSpec::classes).orElseGet(Set::of);
        var derivationClass = admitted.contains(DerivationClass.LOCAL) || admitted.isEmpty()
                ? DerivationClass.LOCAL
                : admitted.iterator().next();
        return new DerivedFact.Source(UNKNOWN_DERIVATION, derivationClass);
    }

    private static boolean mustDerive(@Nullable DerivedFact stored, String inputDigest, Instant now) {
        if (stored == null) {
            return true;
        }
        if (!stored.inputDigest().equals(inputDigest)) {
            return true;
        }
        if (stored.source().derivationClass() == DerivationClass.NETWORK
                && !now.isBefore(stored.derivedAt().plus(NETWORK_VALIDITY))) {
            return true;
        }
        return stored.state() != State.CURRENT;
    }

    private static DerivedFactReading readingOf(DerivedFactKey key, @Nullable DerivedFact stored, Instant now) {
        if (stored == null) {
            return new DerivedFactReading.Absent();
        }
        if (stored.state() != State.CURRENT) {
            return new DerivedFactReading.Indeterminate(failureReason(key, stored));
        }
        if (stored.source().derivationClass() == DerivationClass.NETWORK
                && !now.isBefore(stored.derivedAt().plus(NETWORK_VALIDITY))) {
            return new DerivedFactReading.Expired();
        }
        return new DerivedFactReading.Value(stored);
    }

    private static String failureReason(DerivedFactKey key, DerivedFact stored) {
        var failure = Objects.requireNonNull(stored.failure(), "The failure of a stale or failed entry");
        return key.kind().wireName() + ": " + failure.reason();
    }

    private static String truncate(String reason) {
        if (reason.codePointCount(0, reason.length()) <= DerivedFact.Failure.MAX_REASON) {
            return reason;
        }
        return reason.substring(0, reason.offsetByCodePoints(0, DerivedFact.Failure.MAX_REASON));
    }

    private static void requireDigest(String inputDigest) {
        Objects.requireNonNull(inputDigest, "inputDigest");
        if (!DerivedFact.INPUT_DIGEST.matcher(inputDigest).matches()) {
            throw new IllegalArgumentException("Not an input digest (" + DerivedFact.INPUT_DIGEST_TEXT + "): "
                    + inputDigest);
        }
    }

    private DerivedFacts decode(byte[] document) {
        return DerivedFactsCodec.read(storeFile.store(), document);
    }
}
