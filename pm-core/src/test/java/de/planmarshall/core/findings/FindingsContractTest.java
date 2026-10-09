/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Findings contract")
class FindingsContractTest {

    @Test
    @DisplayName("the dispositions are FIX, ACCEPT and SUPPRESS, named as on the wire")
    void dispositions() {
        var names = Arrays.stream(Disposition.values()).map(Enum::name).toList();

        assertEquals(List.of("FIX", "ACCEPT", "SUPPRESS"), names);
    }

    @Test
    @DisplayName("a source returns the findings of the scope it is asked for")
    void source() {
        FindingSource<String, String> source = scope -> List.of(scope + "/first", scope + "/second");

        var findings = source.fetch("pull-request-7");

        assertEquals(List.of("pull-request-7/first", "pull-request-7/second"), findings);
    }

    @Test
    @DisplayName("a responder receives the finding, its disposition and the reason")
    void responder() {
        var answered = new ArrayList<String>();
        FindingResponder<String> responder = (finding, disposition, reason) -> answered
                .add(finding + " " + disposition + " " + reason);

        responder.respond("first", Disposition.SUPPRESS, "not reachable");

        assertEquals(List.of("first SUPPRESS not reachable"), answered);
    }
}
