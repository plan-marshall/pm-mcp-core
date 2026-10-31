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

import de.cuioss.test.generator.TypedGenerator;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.plan.UnitReference;
import de.planmarshall.workflow.verify.QuestionClass;
import lombok.experimental.UtilityClass;

/**
 * Registers the generator artifact of {@code pm-workflow} in one place: one factory method per
 * {@link TypedGenerator}, for {@code @TypeGeneratorFactorySource(factoryClass = WorkflowGenerators.class,
 * factoryMethod = "sourceLocations")}.
 *
 * @since 0.1
 */
@UtilityClass
public class WorkflowGenerators {

    /** @return a generator of valid source locations */
    public static TypedGenerator<SourceLocation> sourceLocations() {
        return new SourceLocationGenerator();
    }

    /** @return a generator of valid question classes */
    public static TypedGenerator<QuestionClass> questionClasses() {
        return new QuestionClassGenerator();
    }

    /** @return a generator of valid unit references */
    public static TypedGenerator<UnitReference> unitReferences() {
        return new UnitReferenceGenerator();
    }
}
