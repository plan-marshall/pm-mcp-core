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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * The frontmatter a unit opens with (PM-IMPL-5): what the unit is for a person (its display name and description),
 * what kind of unit it is (its type), its interface (the entries through which control arrives and the exits through
 * which it leaves), and where it comes from (its last change, the release it was made with, its author, and a
 * signature reserved for verifying its origin). It is read without the body, so a list of units shows a unit's name,
 * description, type and interface from it alone.
 * <p>
 * The entries and the exits keep the order the frontmatter declares them in; an element is never collapsed into
 * another. The lists are copied into immutable lists.
 *
 * @param name             the display name of the unit, 3 to 100 characters
 * @param description      what the unit does and when it is used, 1 to 1000 characters
 * @param type             the kind of unit
 * @param entries          the entries a caller enters the unit through, in declaration order
 * @param exits            the exits the unit returns to its caller through, in declaration order
 * @param modificationDate the date of the last change of the file
 * @param version          the plan-marshall release the unit was created with or delivered by, in the release
 *                         version grammar; not the unit's revision, which the {@code workflow} header counts
 * @param author           who made the unit: {@code plan-marshall} for a unit compiled into the binary, the name of a
 *                         person otherwise
 * @param signature        the signature over the file's content, carried with the unit and never verified
 * @param location         where the opening {@code ---} of the frontmatter was read
 * @since 0.1
 */
public record UnitFrontmatter(
String name,
String description,
UnitType type,
List<InterfaceElement> entries,
List<InterfaceElement> exits,
LocalDate modificationDate,
String version,
String author,
Optional<String> signature,
SourceLocation location) {

    /**
     * Copies the entries and the exits into immutable lists that keep their order.
     *
     * @param name             the display name of the unit
     * @param description      what the unit does and when it is used
     * @param type             the kind of unit
     * @param entries          the entries, in declaration order
     * @param exits            the exits, in declaration order
     * @param modificationDate the date of the last change of the file
     * @param version          the release the unit was created with or delivered by
     * @param author           who made the unit
     * @param signature        the signature over the file's content
     * @param location         where the opening {@code ---} was read
     */
    public UnitFrontmatter {
        entries = List.copyOf(entries);
        exits = List.copyOf(exits);
    }

    /**
     * One entry or one exit of a unit's interface: its name and the optional description on the line below it.
     *
     * @param name        the name of the entry (an identifier) or of the exit (an outcome id)
     * @param description what a caller hands over or may assume (an entry), or what it can rely on (an exit), 1 to 500
     *                    characters; empty when the frontmatter declares none
     */
    public record InterfaceElement(String name, Optional<String> description) {
    }
}
