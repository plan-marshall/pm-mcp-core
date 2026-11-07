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
 * The typed generators of the {@code pm-provider-git} test artifact (PLAN-V2-92): one
 * {@link de.cuioss.test.generator.TypedGenerator TypedGenerator} per git contract type that test classes build by
 * hand, and the {@link de.planmarshall.provider.git.testing.generators.GitGenerators} factory that registers them in
 * one place.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only. A module that
 * may depend on the git contract may add it as a test-scope {@code test-jar} dependency; its version comes from the
 * root POM.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.provider.git.testing.generators;

import org.jspecify.annotations.NullMarked;
