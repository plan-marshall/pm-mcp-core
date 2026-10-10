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

import java.util.List;
import java.util.Objects;

import de.planmarshall.core.service.LockManager;

/**
 * The startup reconciliation of one write across scopes (PM-IMPL-7): it completes the later writes of an
 * {@link OrderedWrite} that a crash left undone, by deriving them from the authoritative store, which was written
 * first.
 * <p>
 * A reconciler reads the authoritative store under its lock, and applies a derived write only where the derived store
 * does not show it yet, a check-then-act it runs under the lock of the derived store, one transaction per store. It
 * holds no lock when it returns. A reconciler that finds every derived write in place changes nothing and reports
 * nothing.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface CrossScopeReconciler {

    /**
     * One derived write a reconciler applied.
     *
     * @param name  the reconciliation rule that applied
     * @param store the store it wrote
     * @since 0.1
     */
    record Reconciliation(String name, String store) {

        /** @throws NullPointerException if a component is {@code null} */
        public Reconciliation {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(store, "store");
        }
    }

    /**
     * Derives the later writes from the authoritative store and applies those that are missing.
     *
     * @param lockManager the lock manager to take the lock of each store from, one transaction per store
     * @return the derived writes applied, in the order they were applied; empty if every one was in place
     */
    List<Reconciliation> reconcile(LockManager lockManager);
}
