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
 * The machine configuration and what the machine alone decides of its defaults: the {@code providers} of
 * {@code machine-config.json} that the provider step of the machine's onboarding writes
 * ({@code doc/specification/store-schemas/12-machine-stores.adoc} § Machine Configuration Store, § Machine Provider
 * Defaults), and the machine's own check of a landing variant
 * ({@code doc/specification/workflow-dsl/09-plan-instance.adoc} § Plan Behaviour Axes).
 * <p>
 * The package lies in the runtime because the document's other writer, {@code credentials.store}, is the runtime's
 * {@code CredentialStoreChoice}: one module reads and writes the machine document, and {@code pm-core} stays
 * provider-neutral, as its architecture guard requires.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/store-schemas/12-machine-stores.adoc} § Machine Configuration Store, § Machine Provider
 * Defaults</li>
 * <li>{@code doc/specification/credentials-and-enrolment/01-provider-credentials.adoc} § Credential Store as an Operator
 * Choice, § Provider Verification</li>
 * </ul>
 */
package de.planmarshall.runtime.config;
