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
 * The CI provider contract of the engine: the identity of the acting credential, one issue read and one
 * issue comment, each answered with the closed {@link de.planmarshall.provider.http.Result} of the shared
 * HTTP base.
 * <p>
 * {@link de.planmarshall.provider.ci.CiProvider} is the contract a forge adapter implements;
 * {@link de.planmarshall.provider.ci.CiProviders} is the explicit registry of a runtime, whose members are
 * passed to the constructor and never discovered on the classpath. The value types
 * ({@link de.planmarshall.provider.ci.Identity}, {@link de.planmarshall.provider.ci.Issue},
 * {@link de.planmarshall.provider.ci.IssueComment}, {@link de.planmarshall.provider.ci.IssueRef},
 * {@link de.planmarshall.provider.ci.RepositoryRef}) carry the provider's answer as structured fields, and
 * {@link de.planmarshall.provider.ci.ForgeUrl} resolves a source's URL to its forge, repository and number.
 * <p>
 * The package names no transport: it depends on the shared HTTP base for the closed result alone, and the
 * request itself belongs to the adapter.
 */
@NullMarked
package de.planmarshall.provider.ci;

import org.jspecify.annotations.NullMarked;
