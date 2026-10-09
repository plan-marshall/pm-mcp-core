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
 * The primitive SPI: the interface every deterministic server operation implements, with its outcome classes
 * ({@link de.planmarshall.core.spi.OutcomeClass}) and the events a primitive waits for ({@link
 * de.planmarshall.core.spi.AwaitedEvent}). The plan-domain primitives of {@code pm-workflow} and the {@code git.*}
 * and {@code ci.*} primitives of the provider modules build against it.
 * <p>
 * Specification: {@code doc/specification/workflow-dsl/05-primitives.adoc} (Primitive SPI Contract).
 */
package de.planmarshall.core.spi;
