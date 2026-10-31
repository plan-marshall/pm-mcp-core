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
 * The typed generators of the {@code pm-workflow} test artifact (PLAN-V2-107): one
 * {@link de.cuioss.test.generator.TypedGenerator TypedGenerator} per workflow type that the assembly's tests reuse,
 * and the {@link de.planmarshall.workflow.testing.generators.WorkflowGenerators} factory that registers them in one
 * place.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only. The
 * dependency rules keep {@code pm-runtime} and the providers from depending on the engine, so the assembly is the only
 * consumer of this artifact; it adds it as a test-scope {@code test-jar} dependency.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.workflow.testing.generators;

import org.jspecify.annotations.NullMarked;
