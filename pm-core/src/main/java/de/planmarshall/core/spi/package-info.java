/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
/**
 * The primitive SPI: the interface every deterministic server operation implements. The plan-domain primitives of
 * {@code pm-workflow} and the {@code git.*} and {@code ci.*} primitives of the provider modules build against it.
 * <p>
 * The contract: a {@link de.planmarshall.core.spi.Primitive} has a canonical name, a parameter record whose free
 * components carry {@link de.planmarshall.core.spi.FreeParam}, and a closed enum of
 * {@link de.planmarshall.core.spi.PrimitiveOutcome outcomes}, each of one
 * {@link de.planmarshall.core.spi.OutcomeClass}. It runs for a
 * {@link de.planmarshall.core.spi.WorkflowExecutionContext} and returns a
 * {@link de.planmarshall.core.spi.PrimitiveResult}. It declares the event it waits for
 * ({@link de.planmarshall.core.spi.AwaitedEvent}) and the measure that ends a cycle through its state
 * ({@link de.planmarshall.core.spi.CycleMeasure}).
 * <p>
 * The registry: {@link de.planmarshall.core.spi.PrimitiveRegistry} holds the primitives the assembly passes to its
 * constructor and refuses a primitive whose declarations are malformed or contradict each other.
 * <p>
 * The sub-package {@link de.planmarshall.core.spi.validation} holds the content validators a primitive runs over
 * submitted text.
 * <p>
 * Requirement: PM-IMPL-5.
 */
package de.planmarshall.core.spi;
