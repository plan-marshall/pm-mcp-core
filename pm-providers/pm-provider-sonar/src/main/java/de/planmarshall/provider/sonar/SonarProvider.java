/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.sonar;

/**
 * The identity of Sonar among the code analysis providers (PM-IMPL-1).
 *
 * @since 0.1
 */
public final class SonarProvider {

    /**
     * The provider id: the tag of the results of Sonar, the source of its findings in the findings store, and the
     * provider of the gate {@code analysis_gate:sonar}.
     */
    public static final String ID = "sonar";

    private SonarProvider() {
    }
}
