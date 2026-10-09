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
 * Sonar (SonarQube and SonarCloud) as a code analysis provider: the adapter of the code-analysis and findings
 * contracts of {@code pm-core}. It is no CI provider; of {@code pm-provider-ci} it uses the shared HTTP base alone.
 * {@link de.planmarshall.provider.sonar.SonarProvider} holds the provider id, {@link
 * de.planmarshall.provider.sonar.SonarIssueKey} the key a finding of Sonar is answered on.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/cli-and-security/02-credentials-and-operator.adoc}</li>
 * <li>{@code doc/specification/phase-workflows/04-finalize-step-bands.adoc}</li>
 * <li>{@code doc/specification/job-runtime/03-parsers-and-gates.adoc}</li>
 * </ul>
 */
package de.planmarshall.provider.sonar;
