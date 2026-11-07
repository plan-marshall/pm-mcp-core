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
 * The typed generators of the {@code pm-provider-ci} test artifact (PLAN-V2-92): one
 * {@link de.cuioss.test.generator.TypedGenerator TypedGenerator} per CI contract type that two or more test classes
 * build by hand, and the {@link de.planmarshall.provider.ci.testing.generators.CiGenerators} factory that registers
 * them in one place.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only. A provider
 * module that depends on the CI contract may add it as a test-scope {@code test-jar} dependency; its version comes
 * from the root POM.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.provider.ci.testing.generators;

import org.jspecify.annotations.NullMarked;
