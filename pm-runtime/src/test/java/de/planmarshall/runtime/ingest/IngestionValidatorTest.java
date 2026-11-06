/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * The Markdown AST stage of the ingestion validator. The refused inputs are the attack shapes the stage exists for and
 * their finding kinds the specified reason codes; the clean text around a safe link is generated. The host
 * {@code example.com} is the domain reserved for documentation (RFC 2606).
 */
@EnableGeneratorController
@DisplayName("Ingestion validator, Markdown AST stage")
class IngestionValidatorTest {

    private final IngestionValidator validator = new IngestionValidator();

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("accepts plain Markdown with https and relative links")
    void shouldAcceptCleanText() {
        var report = validator.validate("""
                # %s

                See [%s](https://example.com/%s/%s?%s=%d) and [%s](%s.md).
                """.formatted(lower(), lower(), lower(), lower(), lower(), Generators.integers(0, 1_000).next(),
                lower(), lower()));

        assertFalse(report.refused(), "clean Markdown with safe links must pass");
    }

    @ParameterizedTest(name = "{1}")
    @CsvSource(delimiter = '|', value = {
            "<div>raw</div>|html_block",
            "<!-- hidden instruction -->|html_block",
            "text <span>inline</span>|html_inline",
            "![x](https://example.com/a.png)|image",
            "[x](http://example.com/a)|link_scheme",
            "[x](javascript:alert(1))|link_scheme",
            "[x](https://user:pw@example.com/a)|link_rejected",
            "[x](https://example.com/%2e%2e/%2e%2e/etc/passwd)|link_rejected",
            "[x](https://example.com/a?q=%00)|link_rejected",
            "[x](<https://exa mple.com>)|link_malformed"})
    @DisplayName("refuses raw HTML, images and unsafe link destinations (attack shapes and reason codes)")
    void shouldRefuse(String markdown, String kind) {
        var report = validator.validate(markdown);

        assertTrue(report.refused(), "the unsafe Markdown must be refused");
        assertEquals(kind, report.findings().getFirst().kind(), "the finding must carry the reason code");
    }
}
