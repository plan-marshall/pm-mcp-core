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

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.Builder;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * One entry of the derived facts store (Store Schemas § Derived Facts Store, {@code entry}; PM-EXT-6): the kind, the
 * kind-specific value, the derivation that produced it, the digest of its inputs, the instant it was derived, its
 * state and, for a derivation that failed, the failure record.
 * <p>
 * The state carries the one shape the specification states, and the compact constructor refuses every other:
 * <ul>
 * <li>{@link State#CURRENT} carries a value and no failure;</li>
 * <li>{@link State#STALE} carries the last value and a failure;</li>
 * <li>{@link State#FAILED} carries no value ({@code null} is legal only here) and a failure.</li>
 * </ul>
 * The value is the existing {@link JsonValue} tree of {@code core.role.corpus}, validated against the value schema the
 * kind registered in {@link DerivedFactKinds} (ADR {@code core-store/054}).
 *
 * @param kind        the kind of the fact
 * @param value       the kind-specific value; {@code null} only for {@link State#FAILED}
 * @param source      the derivation that produced the value and its class
 * @param inputDigest the SHA-256 over the canonical derivation inputs, 64 lower-case hexadecimal digits
 * @param derivedAt   the instant the value was derived
 * @param state       whether the value is current, stale or failed
 * @param failure     the failure record; {@code null} only for {@link State#CURRENT}
 * @since 0.1
 */
@Builder(toBuilder = true)
public record DerivedFact(
DerivedFactKind kind,
@Nullable JsonValue value,
Source source,
String inputDigest,
Instant derivedAt,
State state,
@Nullable Failure failure) {

    /** The schema pattern of {@code input_digest} (Store Schemas § Derived Facts Store). */
    public static final String INPUT_DIGEST_TEXT = "^[0-9a-f]{64}$";

    /** The compiled {@link #INPUT_DIGEST_TEXT}. */
    public static final Pattern INPUT_DIGEST = Pattern.compile(INPUT_DIGEST_TEXT);

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the digest is not 64 lower-case hexadecimal digits, or the value and the
     *                                  failure do not have the shape of the state
     */
    public DerivedFact {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(inputDigest, "inputDigest");
        Objects.requireNonNull(derivedAt, "derivedAt");
        Objects.requireNonNull(state, "state");
        if (!INPUT_DIGEST.matcher(inputDigest).matches()) {
            throw new IllegalArgumentException("Not an input digest (" + INPUT_DIGEST_TEXT + "): " + inputDigest);
        }
        switch (state) {
            case CURRENT -> {
                if (value == null || failure != null) {
                    throw new IllegalArgumentException("A current fact carries a value and no failure");
                }
            }
            case STALE -> {
                if (value == null || failure == null) {
                    throw new IllegalArgumentException("A stale fact carries its last value and a failure");
                }
            }
            case FAILED -> {
                if (value != null || failure == null) {
                    throw new IllegalArgumentException("A failed fact carries no value and a failure");
                }
            }
        }
    }

    /** The state of the value of a fact. The set is closed (Store Schemas § Derived Facts Store, {@code state}). */
    public enum State {
        /** The value is current. */
        CURRENT("current"),
        /** The last value is kept, and the derivation that would refresh it failed. */
        STALE("stale"),
        /** No value was ever derived, and the derivation failed. */
        FAILED("failed");

        private final String wireName;

        State(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the state in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a state in a store
         * @return the state of that name; empty if no state has it
         */
        public static Optional<State> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(state -> state.wireName.equals(wireName)).findFirst();
        }
    }

    /** How the value was obtained. The set is closed (Store Schemas § Derived Facts Store, {@code source.class}). */
    public enum DerivationClass {
        /** The derivation read its inputs from the repository or the machine. */
        LOCAL("local"),
        /** The derivation read its inputs from a provider. */
        NETWORK("network");

        private final String wireName;

        DerivationClass(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the class in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a class in a store
         * @return the class of that name; empty if no class has it
         */
        public static Optional<DerivationClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The derivation that produced a value (Store Schemas § Derived Facts Store, {@code source}).
     *
     * @param derivation      the identifier of the derivation
     * @param derivationClass how the value was obtained
     * @since 0.1
     */
    public record Source(String derivation, DerivationClass derivationClass) {

        /** The schema pattern of {@code source.derivation}. */
        public static final String DERIVATION_TEXT = "^[a-z][a-z0-9_.-]{0,63}$";

        /** The compiled {@link #DERIVATION_TEXT}. */
        public static final Pattern DERIVATION = Pattern.compile(DERIVATION_TEXT);

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the derivation does not match its grammar
         */
        public Source {
            Objects.requireNonNull(derivation, "derivation");
            Objects.requireNonNull(derivationClass, "derivationClass");
            if (!DERIVATION.matcher(derivation).matches()) {
                throw new IllegalArgumentException(
                        "Not a derivation identifier (" + DERIVATION_TEXT + "): " + derivation);
            }
        }
    }

    /**
     * The record of a derivation that failed (Store Schemas § Derived Facts Store, {@code failure}).
     *
     * @param reason what failed, at most {@value #MAX_REASON} code points
     * @param at     the instant the derivation failed
     * @since 0.1
     */
    public record Failure(String reason, Instant at) {

        /** The schema bound of {@code failure.reason} in code points. */
        public static final int MAX_REASON = 500;

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the reason is longer than {@value #MAX_REASON} code points
         */
        public Failure {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(at, "at");
            if (reason.codePointCount(0, reason.length()) > MAX_REASON) {
                throw new IllegalArgumentException(
                        "A failure reason is at most " + MAX_REASON + " code points");
            }
        }
    }
}
