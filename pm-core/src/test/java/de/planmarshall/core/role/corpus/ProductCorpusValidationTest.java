/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.ProductCorpus;

/**
 * The corpus validator accepts the corpus's own items of plan-marshall-mcp (PM-WORK-6) as present in the checkout
 * {@code pm.product.root} names; skipped, with the path named, when the checkout is absent.
 */
@DisplayName("Product corpus validation")
class ProductCorpusValidationTest {

    @Test
    @DisplayName("the corpus of plan-marshall-mcp has no violation")
    void productCorpusIsValid() {
        var corpus = ProductCorpus.root();

        var violations = new CorpusValidator(corpus).validate();

        assertEquals(List.of(), violations,
                () -> violations.stream().map(CorpusViolation::toString).collect(Collectors.joining("\n")));
    }
}
