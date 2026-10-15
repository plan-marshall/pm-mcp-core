/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Names the model verification corpus of the checkout of plan-marshall-mcp (test helper).
 * <p>
 * The checkout is named by the system property {@value #ROOT_PROPERTY}, which the build passes from the Maven
 * property of the same name (by default {@code ../plan-marshall-mcp} beside the repository; override it with
 * {@code -Dpm.product.root=<path>}). When the checkout or its corpus is absent, the calling test is aborted through a
 * JUnit assumption whose message names the missing path, so it is reported as skipped and never passes unseen.
 */
public final class ProductCorpus {

    /** The system property naming the root of the plan-marshall-mcp checkout. */
    public static final String ROOT_PROPERTY = "pm.product.root";

    /** The corpus below the root of the checkout. */
    public static final String CORPUS_DIRECTORY = "test/model/verification";

    private ProductCorpus() {
    }

    /**
     * @return the corpus root of the plan-marshall-mcp checkout
     * @throws org.opentest4j.TestAbortedException if the property is unset or the corpus is absent
     */
    public static Path root() {
        var property = System.getProperty(ROOT_PROPERTY);
        assumeTrue(property != null && !property.isBlank(),
                "the plan-marshall-mcp checkout is not configured: system property " + ROOT_PROPERTY + " is unset");
        var corpus = Path.of(property).toAbsolutePath().normalize().resolve(CORPUS_DIRECTORY);
        assumeTrue(Files.isDirectory(corpus), () -> "the corpus of the plan-marshall-mcp checkout is absent: " + corpus
                + " does not exist (set -D" + ROOT_PROPERTY + "=<path>)");
        return corpus;
    }
}
