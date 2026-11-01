/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.time.Instant;

import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.core.store.ScopeType;

/**
 * Generates a valid {@link LeaseRecord} of a plan scope: a generated key, an owner served by a generated runtime
 * instance and a claim instant with an expiry one hour later.
 *
 * @since 0.1
 */
public final class LeaseRecordGenerator implements TypedGenerator<LeaseRecord> {

    @Override
    public LeaseRecord next() {
        var suffix = Generators.longs(100, Long.MAX_VALUE).next();
        var acquiredAt = Instant.ofEpochMilli(Generators.integers(0, Integer.MAX_VALUE).next().longValue());
        var owner = new LeaseOwner("project-" + suffix, ScopeType.PLAN, "plan-" + suffix,
                new HolderInstance(suffix, acquiredAt), null);
        return new LeaseRecord("lease-" + suffix, owner, acquiredAt, acquiredAt.plusSeconds(3_600));
    }

    @Override
    public Class<LeaseRecord> getType() {
        return LeaseRecord.class;
    }
}
