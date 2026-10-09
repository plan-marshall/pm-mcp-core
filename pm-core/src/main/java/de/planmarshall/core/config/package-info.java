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
 * The configuration: its split into team, local and machine files, the derived facts, and the keys of job budgets
 * and retention.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/domain-extensions/06-configuration.adoc}</li>
 * <li>{@code doc/specification/store-schemas/07-project-configuration.adoc}</li>
 * <li>{@code doc/specification/store-schemas/08-project-configuration-local.adoc}</li>
 * <li>{@code doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc}</li>
 * <li>{@code doc/specification/timeouts.adoc}</li>
 * </ul>
 */
package de.planmarshall.core.config;
