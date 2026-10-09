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

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import lombok.experimental.UtilityClass;

/**
 * The assertion of the total lock order (PM-IMPL-7): whether a transaction that holds some keys may acquire another
 * one. It is a pure function of the held keys and the requested key; it opens no file and takes no lock, so the
 * lock manager can ask it on every acquisition and the order can be tested at every level boundary without a file.
 * <p>
 * The rules, in this order:
 * <ol>
 * <li>A key the transaction already holds may be requested again.</li>
 * <li>Nothing is acquired after a leaf lock.</li>
 * <li>Any other key must come after every held key in the order of {@link LockKey#compareTo(LockKey)}.</li>
 * </ol>
 *
 * @since 0.1
 */
@UtilityClass
public class LockOrder {

    /**
     * @param held      the keys the transaction holds
     * @param requested the key the transaction asks for
     * @return whether the request is a re-entry, permitted, or a violation of the order
     * @throws NullPointerException if an argument or a held key is {@code null}
     */
    public static Decision check(Collection<LockKey> held, LockKey requested) {
        Objects.requireNonNull(requested, "requested");
        var heldInOrder = held.stream().sorted().toList();
        if (heldInOrder.contains(requested)) {
            return new Reentry();
        }
        if (heldInOrder.isEmpty()) {
            return new Permitted();
        }
        var last = heldInOrder.getLast();
        if (last.level() == LockLevel.LEAF || requested.compareTo(last) < 0) {
            return new Violation(heldInOrder, requested);
        }
        return new Permitted();
    }

    /**
     * The answer of the order assertion.
     *
     * @since 0.1
     */
    public sealed interface Decision permits Reentry, Permitted, Violation {
    }

    /**
     * The requested key is already held by the transaction; nothing is to be acquired.
     *
     * @since 0.1
     */
    public record Reentry() implements Decision {
    }

    /**
     * The requested key comes after every held key and may be acquired.
     *
     * @since 0.1
     */
    public record Permitted() implements Decision {
    }

    /**
     * The requested key must not be acquired while the held keys are held.
     *
     * @param held      the keys the transaction holds, in the lock order
     * @param requested the refused key
     * @since 0.1
     */
    public record Violation(List<LockKey> held, LockKey requested) implements Decision {

        /**
         * @throws NullPointerException if a component or a held key is {@code null}
         */
        public Violation {
            held = List.copyOf(held);
            Objects.requireNonNull(requested, "requested");
        }
    }
}
