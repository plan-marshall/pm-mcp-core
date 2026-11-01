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
 * The typed generators of the {@code pm-runtime} test artifact (PLAN-V2-107), beside the {@code TestBases} the
 * assembly already reuses: one {@link de.cuioss.test.generator.TypedGenerator TypedGenerator} per runtime type that
 * the assembly's tests build by hand, and the
 * {@link de.planmarshall.runtime.testing.generators.RuntimeGenerators} factory that registers them in one place.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only. The fakes of
 * these tests ({@code FakeBus}, {@code FakeSecretService}) stay where they are; they are replaced by EasyMock doubles
 * in PLAN-V2-106.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.runtime.testing.generators;

import org.jspecify.annotations.NullMarked;
