/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import de.cuioss.test.generator.Generators;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import de.planmarshall.core.store.OutlineRecord.AddedDependency;
import de.planmarshall.core.store.OutlineRecord.AffectedFile;
import de.planmarshall.core.store.OutlineRecord.ChangeType;
import de.planmarshall.core.store.OutlineRecord.Deliverable;
import de.planmarshall.core.store.OutlineRecord.Direction;
import de.planmarshall.core.store.OutlineRecord.ExecutionMode;
import de.planmarshall.core.store.OutlineRecord.FileIntent;
import de.planmarshall.core.store.OutlineRecord.Interface;
import de.planmarshall.core.store.OutlineRecord.KeyLink;
import de.planmarshall.core.store.OutlineRecord.Profile;
import de.planmarshall.core.store.OutlineRecord.ScopeEstimate;
import de.planmarshall.core.store.OutlineRecord.Verification;
import de.planmarshall.core.store.OutlineStore.Revision;

/**
 * Records and revisions the outline tests share. The texts of a record are generated; the deliverable titles, the
 * satisfies identifiers and the affected-file path stay literal because the outline tests assert them in the rendered
 * {@code outline.md}.
 */
final class OutlineFixtures {

    // Fixture instant: the revisions count their submission seconds from it.
    static final Instant AT = Instant.parse("2026-10-27T11:00:00Z");

    private OutlineFixtures() {
    }

    static OutlineRecord valid() {
        // Titles and satisfies identifiers are asserted in the rendered outline by OutlineRecordTest.
        return new OutlineRecord(text(20, 80), text(1, 40), ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, "Write the intent record", List.of(), "AC-1", text(3, 20)),
                        deliverable(2, "Render the clarified request", List.of(1), "AC-2", text(3, 20))));
    }

    static Deliverable deliverable(int number, String title, List<Integer> depends, String satisfies, String note) {
        // Fixture path: the rendered outline names the affected file of the deliverable by this path.
        var path = "pm-core/src/main/java/%s.java".formatted(number);
        return new Deliverable(number, title, note + ' ' + text(2, 60),
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, identifier(), identifier(), depends,
                note + ' ' + text(1, 60),
                List.of(Profile.IMPLEMENTATION, Profile.MODULE_TESTING),
                List.of(new AffectedFile(path, FileIntent.WRITE_NEW, null)),
                text(10, 80),
                List.of(new Verification(text(1, 40), text(5, 60))),
                List.of(satisfies),
                // Coordinate grammar: the record admits only a maven:, npm: or pypi: prefixed coordinate.
                List.of(new AddedDependency("maven:" + identifier(), note + ' ' + text(10, 60), text(10, 60))),
                List.of(new Interface(Direction.PRODUCES, identifier(), text(1, 60))),
                // The pattern is a regular expression, so it carries backslashes: the round trip must escape them.
                List.of(new KeyLink(path, identifier(), "new " + identifier() + "\\(\\)")));
    }

    static Revision revision(int number, OutlineRecord outlineRecord) {
        // The revision admits any task id of 1 to 128 characters; the fixture names it TASK- and a number.
        return new Revision(number, AT.plusSeconds(number), "TASK-" + Generators.integers(1, 999).next(),
                outlineRecord);
    }

    private static String text(int min, int max) {
        return Generators.letterStrings(min, max).next();
    }

    private static String identifier() {
        return Generators.letterStrings(1, 30).next().toLowerCase(Locale.ROOT);
    }
}
