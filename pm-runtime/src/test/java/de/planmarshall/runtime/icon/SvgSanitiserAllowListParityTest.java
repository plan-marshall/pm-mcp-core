/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.icon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR 012 states the sanitiser's allow-lists, and the code is the enforcement: a change to either list alone leaves the
 * other stale. The ADR is read as the document it is (its path is the fixture), and the two lists it names are compared
 * with the sets {@link SvgSanitiser} enforces, so a divergence fails the build.
 */
@DisplayName("SVG allow-list parity with ADR 012")
class SvgSanitiserAllowListParityTest {

    private static final Path ADR = Path.of("doc/adr/runtime-launch/012-the-icon-sanitiser-is-an-allow-list.adoc");
    private static final Pattern ELEMENT_LIST = Pattern.compile("A closed list of elements is kept \\(([^)]*)\\)");
    private static final Pattern ATTRIBUTE_LIST = Pattern.compile("A closed list of attributes is kept:\\s*\\n([^;]*);");
    private static final Pattern BACKTICKED = Pattern.compile("`([^`]+)`");

    @Test
    @DisplayName("the elements the ADR names are the elements the sanitiser keeps")
    void elementsAreTheAdrList() throws Exception {
        assertEquals(SvgSanitiser.ALLOWED_ELEMENTS, list(ELEMENT_LIST),
                "the ADR's element list is the sanitiser's allow-list");
    }

    @Test
    @DisplayName("the attributes the ADR names are the attributes the sanitiser keeps")
    void attributesAreTheAdrList() throws Exception {
        assertEquals(SvgSanitiser.ALLOWED_ATTRIBUTES, list(ATTRIBUTE_LIST),
                "the ADR's attribute list is the sanitiser's allow-list");
    }

    private static Set<String> list(Pattern pattern) throws IOException {
        var document = Files.readString(ADR, StandardCharsets.UTF_8);
        var matcher = pattern.matcher(document);
        if (!matcher.find()) {
            throw new AssertionError("the ADR does not carry the list this test reads");
        }
        var names = new TreeSet<String>();
        var backticked = BACKTICKED.matcher(matcher.group(1));
        while (backticked.find()) {
            names.add(backticked.group(1));
        }
        return Set.copyOf(names);
    }
}
