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
 * The ported-primitive tests of the git contract (PM-TEST-2, PM-MIG-4): Java tests of a primitive whose
 * {@code plan-marshall} original is classified {@code Ported} in the migration inventory.
 * <p>
 * The convention:
 * <ul>
 * <li>One {@code Ported} script is adapted at a time. Its original script and its pytest suite at the commit the
 * migration inventory pins ({@link de.planmarshall.provider.git.ported.PortedFixtures#INVENTORY_PIN}) are the
 * guide; they are read by hand while the tests are written, never at test time.</li>
 * <li>The test data are plain files derived from them and committed below
 * {@code src/test/resources/ported/<primitive>/}: one scenario per file, headed either
 * {@code original: plan-marshall@<pin> <pytest path>::<case>} (derived from a pytest case) or
 * {@code deliberate: <name>} (a deliberate change or a known defect of the original, asserting the corrected
 * behaviour, never a value of the original). The roster {@code original-cases.txt} lists every pytest case of the
 * original suite, each mapped to its fixtures or excluded with a reason.</li>
 * <li>The fixtures are enumerated from their directory, never listed by hand
 * ({@link de.planmarshall.provider.git.ported.PortedFixtures}).</li>
 * <li>Nothing runs Python, starts a {@code plan-marshall} script, reads a {@code plan-marshall} checkout, or compares a
 * Java result with a Python result, live or recorded.</li>
 * <li>The legacy-to-target mapping lives only in this package and its data. For {@code git.worktree-sha} it is held
 * by {@link de.planmarshall.provider.git.ported.WorktreeShaScenario}: the original's {@code None} is
 * {@code GitOutcome.UNAVAILABLE}, and its {@code str | Path} argument is a {@link java.nio.file.Path}.</li>
 * </ul>
 * <p>
 * Owner and removal trigger of the legacy mapping: this package owns it. The mapping of a primitive is removed when
 * that {@code Ported} primitive is released and the pin of the migration inventory no longer advances for it; until
 * then every advance of the pin re-verifies the original at the new commit before a fixture is derived or changed.
 */
package de.planmarshall.provider.git.ported;
