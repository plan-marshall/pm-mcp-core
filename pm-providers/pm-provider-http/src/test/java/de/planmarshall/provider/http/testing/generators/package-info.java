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
 * The typed generators of the {@code pm-provider-http} test artifact (PLAN-V2-107): one
 * {@link de.cuioss.test.generator.TypedGenerator TypedGenerator} per shared-HTTP type that the provider modules
 * depending on this base build by hand, and the
 * {@link de.planmarshall.provider.http.testing.generators.HttpGenerators} factory that registers them in one place.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only. The provider
 * modules add it as a test-scope {@code test-jar} dependency; the dependency rules keep them from reaching any other
 * provider artifact.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.provider.http.testing.generators;

import org.jspecify.annotations.NullMarked;
