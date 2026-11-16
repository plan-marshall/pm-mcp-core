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
 * The assertion-message scanner of the {@code junit-core} rule, published in the {@code pm-core} test artifact
 * (PLAN-V2-103, ADR 012): one place for the scan logic, and one thin guard per module that names its own base package
 * and reads its own {@code src/test/java}.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only; the
 * {@code *Test} exclusion keeps the scanner's own controls out of the artifact.
 *
 * @since 0.1
 */
package de.planmarshall.core.testing.assertions;
