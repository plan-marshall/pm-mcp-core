/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.testing.generators;

import java.util.Optional;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.plan.UnitReference;

/**
 * Generates a valid {@link UnitReference} of a project unit: a generated unit id, its file and a generated SHA-256
 * digest.
 *
 * @since 0.1
 */
public final class UnitReferenceGenerator implements TypedGenerator<UnitReference> {

    @Override
    public UnitReference next() {
        var suffix = Generators.integers(1, 999).next();
        var unit = "unit-" + suffix;
        return new UnitReference(unit, Optional.of("units/" + unit + ".md"), "%064x".formatted(Generators.longs().next()),
                UnitOrigin.PROJECT);
    }

    @Override
    public Class<UnitReference> getType() {
        return UnitReference.class;
    }
}
