/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The identifiers of the coverage formats.
 *
 * @since 0.1
 */
@DisplayName("CoverageFormat")
class CoverageFormatTest {

    @Test
    @DisplayName("gives every format a distinct, non-blank identifier")
    void givesEveryFormatADistinctId() {
        Set<String> ids = Arrays.stream(CoverageFormat.values())
                .map(CoverageFormat::id)
                .collect(Collectors.toSet());

        assertEquals(CoverageFormat.values().length, ids.size(), "Every format has a distinct id");
        assertTrue(ids.stream().noneMatch(String::isBlank), "Every format id is non-blank");
    }

    @Test
    @DisplayName("names the format its report parser refused")
    void namesTheRefusedFormat() {
        List<CoverageFormat> formats = List.of(CoverageFormat.values());

        formats.forEach(format -> assertEquals(format.name().toLowerCase(Locale.ROOT), format.id(),
                "The id is the lower-case enum name"));
    }
}
