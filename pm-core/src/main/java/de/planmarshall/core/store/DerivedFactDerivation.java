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

import java.util.Objects;

import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The function a consumer hands to {@link DerivedFactStore#refresh} for one kind (Store Schemas § Derived Facts Store;
 * PM-EXT-6): it derives the value from its inputs, or reports why it could not.
 * <p>
 * A failed derivation is a returned {@link DerivationOutcome.Failed}, never an exception: an exception escaping the
 * function is a programming fault of the consumer, and the store does not turn it into a stored failure. A successful
 * derivation names the derivation identifier and the class of its inputs; the store validates the value and the class
 * against the specification registered for the kind before it writes anything.
 *
 * @since 0.1
 */
public interface DerivedFactDerivation {

    /**
     * Derives one value.
     *
     * @since 0.1
     */
    @FunctionalInterface
    interface Derivation {

        /**
         * @return the derived value, or the reason the derivation failed
         */
        DerivationOutcome derive();
    }

    /**
     * The outcome of one derivation: a value with its provenance, or a failure with its reason. The set is closed.
     *
     * @since 0.1
     */
    sealed interface DerivationOutcome permits DerivationOutcome.Derived, DerivationOutcome.Failed {

        /**
         * The derivation produced a value.
         *
         * @param value       the kind-specific value
         * @param derivation  the identifier of the derivation
         * @param sourceClass how the value was obtained
         * @since 0.1
         */
        record Derived(JsonValue value, String derivation, DerivedFact.DerivationClass sourceClass)
                implements DerivationOutcome {

            /**
             * @throws NullPointerException if a component is {@code null}
             */
            public Derived {
                Objects.requireNonNull(value, "value");
                Objects.requireNonNull(derivation, "derivation");
                Objects.requireNonNull(sourceClass, "sourceClass");
            }
        }

        /**
         * The derivation could not produce a value.
         *
         * @param reason why it failed, carried into the entry as the failure reason
         * @since 0.1
         */
        record Failed(String reason) implements DerivationOutcome {

            /**
             * @throws NullPointerException if the reason is {@code null}
             */
            public Failed {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
