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

import java.time.Instant;
import java.util.List;

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

/** Records and revisions the outline tests share. */
final class OutlineFixtures {

    static final Instant AT = Instant.parse("2026-10-27T11:00:00Z");

    private OutlineFixtures() {
    }

    static OutlineRecord valid() {
        return new OutlineRecord("Two deliverables that write the record and render it",
                "record -> renderer", ScopeEstimate.SINGLE_MODULE,
                List.of(deliverable(1, "Write the intent record", List.of(), "AC-1", "the record"),
                        deliverable(2, "Render the clarified request", List.of(1), "AC-2", "the renderer")));
    }

    static Deliverable deliverable(int number, String title, List<Integer> depends, String satisfies, String note) {
        return new Deliverable(number, title, "The deliverable %s".formatted(note),
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, "core", "pm-core", depends, "The design of %s".formatted(note),
                List.of(Profile.IMPLEMENTATION, Profile.MODULE_TESTING),
                List.of(new AffectedFile("pm-core/src/main/java/%s.java".formatted(number), FileIntent.WRITE_NEW, null)),
                "Create the class %s with its rules".formatted(number),
                List.of(new Verification("mvn verify", "the build is green")),
                List.of(satisfies),
                List.of(new AddedDependency("maven:org.example:library", "It is needed for %s".formatted(note),
                        "Writing it myself was rejected as slower")),
                List.of(new Interface(Direction.PRODUCES, "de.planmarshall.core.Store", "Store open(String id)")),
                List.of(new KeyLink("pm-core/src/main/java/%s.java".formatted(number), "Store",
                        "new Store\\(\\)")));
    }

    static Revision revision(int number, OutlineRecord record) {
        return new Revision(number, AT.plusSeconds(number), "TASK-00" + number, record);
    }
}
