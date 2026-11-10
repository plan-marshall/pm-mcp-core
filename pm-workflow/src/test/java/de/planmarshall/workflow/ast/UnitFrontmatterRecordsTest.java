/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.testing.generators.WorkflowGenerators;

/**
 * Proves that the builder of {@link UnitFrontmatter} builds the value its canonical constructor builds. Every value is
 * generated or a contract enum constant, and the interface elements are real values of the nested record.
 */
@EnableGeneratorController
@DisplayName("UnitFrontmatter builder")
class UnitFrontmatterRecordsTest {

    @Test
    @DisplayName("the builder builds the unit frontmatter the constructor builds")
    void unitFrontmatterBuilderEqualsConstructor() {
        var name = Generators.letterStrings(1, 20).next();
        var description = Generators.letterStrings(1, 40).next();
        var type = Generators.enumValues(UnitType.class).next();
        var icon = Generators.letterStrings(1, 20).next();
        var entries = List.of(new UnitFrontmatter.InterfaceElement(Generators.letterStrings(1, 20).next(),
                Generators.letterStrings(1, 40).next()));
        var exits = List.of(new UnitFrontmatter.InterfaceElement(Generators.letterStrings(1, 20).next(), null));
        var modificationDate = LocalDate.ofEpochDay(Generators.longs(0, 20_000).next());
        var version = Generators.letterStrings(1, 20).next();
        var author = Generators.letterStrings(1, 20).next();
        var signature = Generators.letterStrings(1, 40).next();
        var location = WorkflowGenerators.sourceLocations().next();

        var constructed = new UnitFrontmatter(name, description, type, icon, entries, exits, modificationDate,
                version, author, signature, location);
        var built = UnitFrontmatter.builder()
                .name(name)
                .description(description)
                .type(type)
                .icon(icon)
                .entries(entries)
                .exits(exits)
                .modificationDate(modificationDate)
                .version(version)
                .author(author)
                .signature(signature)
                .location(location)
                .build();

        assertEquals(constructed, built, "the builder builds the same unit frontmatter");
    }
}
