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
 * Sonar (SonarQube and SonarCloud) as a code analysis provider: the issue client that reads the open issues of a pull
 * request, below the later adapter of the code-analysis and findings contracts of {@code pm-core}. It is no CI provider;
 * it depends on {@code pm-provider-http} for the shared HTTP base alone.
 * {@link de.planmarshall.provider.sonar.SonarClient} reads the open issues of one pull request, {@link
 * de.planmarshall.provider.sonar.SonarIssue} is one of them, {@link de.planmarshall.provider.sonar.SonarProvider}
 * holds the provider id, and {@link de.planmarshall.provider.sonar.SonarIssueKey} the key a finding of Sonar is
 * answered on.
 * <p>
 * Specification:
 * <ul>
 * <li>{@code doc/specification/credentials-and-enrolment/01-provider-credentials.adoc}</li>
 * <li>{@code doc/specification/phase-workflows/04-finalize-step-bands.adoc}</li>
 * <li>{@code doc/specification/job-runtime/03-parsers-and-gates.adoc}</li>
 * </ul>
 */
@NullMarked
package de.planmarshall.provider.sonar;

import org.jspecify.annotations.NullMarked;
