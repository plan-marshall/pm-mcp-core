/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.primitive.FootprintBoundary.Violated;
import de.planmarshall.provider.git.primitive.FootprintBoundary.Within;
import de.planmarshall.provider.git.primitive.StagingSets.StagingSet;

/**
 * The footprint check before anything is staged: the staging set's outside paths and the site's pinned-file and
 * main-checkout breaches, with the offending files bounded to fifty names.
 */
@EnableGeneratorController
@DisplayName("FootprintBoundary")
class FootprintBoundaryTest {

    private final TypedGenerator<String> names = Generators.letterStrings(1, 16);

    @Test
    @DisplayName("nothing outside and no breach is within the boundary")
    void within() {
        var set = new StagingSet(new TreeSet<>(Set.of(names.next())), new TreeSet<>());

        assertInstanceOf(Within.class, FootprintBoundary.check(set, List.of(), List.of()),
                "A clean footprint is within the boundary");
    }

    @Test
    @DisplayName("each of the three halves breaks the boundary, offenders sorted and named once")
    void eachHalfBreaksTheBoundary() {
        var outside = "z-" + names.next();
        var pinned = "m-" + names.next();
        var mainCheckout = "a-" + names.next();
        var set = new StagingSet(new TreeSet<>(), new TreeSet<>(Set.of(outside)));

        var verdict = FootprintBoundary.check(set, List.of(pinned, outside), List.of(mainCheckout));

        var violated = assertInstanceOf(Violated.class, verdict, "A breach violates the boundary");
        assertAll("offending files",
                () -> assertEquals(List.of(mainCheckout, pinned, outside), violated.offendingFiles(),
                        "Offenders are sorted and named once"),
                () -> assertEquals(3, violated.offendingCount(), "The count names every offender"));
    }

    @Test
    @DisplayName("a pinned-file breach alone breaks the boundary with nothing outside")
    void pinnedFileAloneBreaksTheBoundary() {
        var pinned = names.next();
        var set = new StagingSet(new TreeSet<>(Set.of(names.next())), new TreeSet<>());

        var verdict = FootprintBoundary.check(set, List.of(pinned), List.of());

        var violated = assertInstanceOf(Violated.class, verdict, "A pinned-file breach violates the boundary");
        assertAll("pinned-file offender",
                () -> assertEquals(List.of(pinned), violated.offendingFiles(), "The pinned file is the offender"),
                () -> assertEquals(1, violated.offendingCount(), "The count names the one pinned file"));
    }

    @Test
    @DisplayName("a main-checkout breach alone breaks the boundary with nothing outside")
    void mainCheckoutAloneBreaksTheBoundary() {
        var mainCheckout = names.next();
        var set = new StagingSet(new TreeSet<>(Set.of(names.next())), new TreeSet<>());

        var verdict = FootprintBoundary.check(set, List.of(), List.of(mainCheckout));

        var violated = assertInstanceOf(Violated.class, verdict, "A main-checkout breach violates the boundary");
        assertAll("main-checkout offender",
                () -> assertEquals(List.of(mainCheckout), violated.offendingFiles(),
                        "The main-checkout path is the offender"),
                () -> assertEquals(1, violated.offendingCount(), "The count names the one main-checkout path"));
    }

    @Test
    @DisplayName("more than fifty offenders are bounded to fifty names with the total count")
    void offendersAreBounded() {
        var outside = new TreeSet<String>();
        IntStream.range(0, 60).forEach(index -> outside.add("f%03d-%s".formatted(index, names.next())));
        var set = new StagingSet(new TreeSet<>(), outside);

        var violated = assertInstanceOf(Violated.class, FootprintBoundary.check(set, List.of(), List.of()),
                "Sixty outside paths violate the boundary");

        assertAll("bounded offenders",
                () -> assertEquals(outside.stream().limit(FootprintBoundary.OFFENDING_FILES_LIMIT).toList(),
                        violated.offendingFiles(), "The first fifty offenders are named"),
                () -> assertEquals(60, violated.offendingCount(), "The count names all sixty offenders"));
    }

    @Test
    @DisplayName("a violation names at least one file and no more than its count")
    void violationInvariants() {
        var none = List.<String>of();
        var one = List.of(names.next());

        assertAll("violation invariants",
                () -> assertThrows(IllegalArgumentException.class, () -> new Violated(none, 0),
                        "A violation without offenders is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new Violated(one, 0),
                        "A count below the named files is refused"));
    }
}
