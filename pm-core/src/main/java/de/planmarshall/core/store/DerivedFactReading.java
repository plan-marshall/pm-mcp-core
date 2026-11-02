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
import java.util.Optional;

/**
 * What a read of one derived fact can say (Store Schemas § Derived Facts Store, bullet <em>Failure</em>; PM-EXT-6):
 * the value is current, the last value is kept with a failure, the network validity period has run out, or no entry
 * exists.
 * <p>
 * The reading fails closed: only {@link Value} is a value a gate may evaluate. {@link Indeterminate}, {@link Expired}
 * and {@link Absent} answer {@link #couldNotEvaluate()} with the reason, so a consumer that maps a reading onto the
 * three-valued guard result never turns a missing or failed fact into a pass (ADR {@code core-store/058}).
 *
 * @since 0.1
 */
public sealed interface DerivedFactReading
        permits DerivedFactReading.Value, DerivedFactReading.Indeterminate, DerivedFactReading.Expired,
        DerivedFactReading.Absent {

    /**
     * @return the reason the fact cannot be evaluated; empty only for {@link Value}
     */
    Optional<String> couldNotEvaluate();

    /**
     * The fact has a current value inside its validity period.
     *
     * @param fact the entry of the store
     * @since 0.1
     */
    record Value(DerivedFact fact) implements DerivedFactReading {

        /**
         * @throws NullPointerException if the entry is {@code null}
         */
        public Value {
            Objects.requireNonNull(fact, "fact");
        }

        @Override
        public Optional<String> couldNotEvaluate() {
            return Optional.empty();
        }
    }

    /**
     * The entry is {@code stale} or {@code failed}: the stored failure decides why the fact cannot be evaluated.
     *
     * @param reason the failure reason with the kind of the fact
     * @since 0.1
     */
    record Indeterminate(String reason) implements DerivedFactReading {

        /**
         * @throws NullPointerException if the reason is {@code null}
         */
        public Indeterminate {
            Objects.requireNonNull(reason, "reason");
        }

        @Override
        public Optional<String> couldNotEvaluate() {
            return Optional.of(reason);
        }
    }

    /**
     * A {@code current} network fact is past its validity period and must be refreshed before a gate reads it.
     *
     * @since 0.1
     */
    record Expired() implements DerivedFactReading {

        @Override
        public Optional<String> couldNotEvaluate() {
            return Optional.of("The network fact is past its validity period and must be refreshed");
        }
    }

    /**
     * The store holds no entry for the key.
     *
     * @since 0.1
     */
    record Absent() implements DerivedFactReading {

        @Override
        public Optional<String> couldNotEvaluate() {
            return Optional.of("The fact is absent");
        }
    }
}
