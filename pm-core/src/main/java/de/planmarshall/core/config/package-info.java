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
 * The machine's and a project's stores that the onboarding writes and everything else reads: the consent store
 * {@code consents.json} with the consent-before-acting rule, the model catalogue {@code model-catalogue.json}, the role
 * assignments {@code role-assignments.json} with the release's default assignment and the proposal {@code roles.default}
 * shows, and the settings-conflict refusal that carries the value a change collided with. The machine's provider
 * defaults live in {@code de.planmarshall.runtime.config} of {@code pm-runtime}, because this package names no provider.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/12-machine-stores.adoc} (consent, model catalogue, role assignments, the
 * operator-file revision rule)</li>
 * <li>{@code doc/specification/model-work/01-roles-and-configuration.adoc} (role assignment, default assignment, steward
 * role)</li>
 * <li>{@code doc/specification/domain-extensions/06-configuration.adoc}</li>
 * <li>{@code doc/specification/store-schemas/07-project-configuration.adoc}</li>
 * <li>{@code doc/specification/store-schemas/08-project-configuration-local.adoc}</li>
 * <li>{@code doc/specification/store-schemas/09-derived-facts-and-build-timings.adoc}</li>
 * <li>{@code doc/specification/timeouts.adoc}</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.core.config;

import org.jspecify.annotations.NullMarked;
