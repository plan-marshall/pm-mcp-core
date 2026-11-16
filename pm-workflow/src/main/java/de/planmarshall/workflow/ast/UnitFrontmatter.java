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

import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * The frontmatter a unit opens with (PM-IMPL-5): what the unit is for a person (its display name and description),
 * what kind of unit it is (its type), the icon that stands for it in a visual language (its icon id), its interface
 * (the entries through which control arrives, the exits through which it leaves, the inputs a call declares and the
 * configuration keys and fact kinds the unit reads), and where it comes from (its last change, the release it was made
 * with, its author, and a signature reserved for verifying its origin). It is read without the body, so a list of units
 * shows a unit's name, description, type, icon and interface from it alone.
 * <p>
 * The entries, the exits, the reads and the inputs keep the order the frontmatter declares them in; an element is never
 * collapsed into another. The lists are copied into immutable lists.
 *
 * @param name             the display name of the unit, 3 to 100 characters
 * @param description      what the unit does and when it is used, 1 to 1000 characters
 * @param type             the kind of unit
 * @param icon             the stable icon id that stands for the unit, never a file path
 * @param entries          the entries a caller enters the unit through, in declaration order
 * @param exits            the exits the unit returns to its caller through, in declaration order
 * @param readsConfig      the configuration keys the unit reads, in declaration order
 * @param readsFact        the derived fact kinds the unit reads, in declaration order
 * @param inputs           the inputs a caller binds, in declaration order
 * @param modificationDate the date of the last change of the file
 * @param version          the plan-marshall release the unit was created with or delivered by, in the release
 *                         version grammar; not the unit's revision, which the {@code workflow} header counts
 * @param author           who made the unit: {@code plan-marshall} for a unit compiled into the binary, the name of a
 *                         person otherwise
 * @param signature        the signature over the file's content, carried with the unit and never verified;
 *                         {@code null} when the frontmatter carries none
 * @param location         where the opening {@code ---} of the frontmatter was read
 * @since 0.1
 */
@Builder
public record UnitFrontmatter(
String name,
String description,
UnitType type,
String icon,
List<InterfaceElement> entries,
List<InterfaceElement> exits,
List<String> readsConfig,
List<String> readsFact,
List<InputDecl> inputs,
LocalDate modificationDate,
String version,
String author,
@Nullable String signature,
SourceLocation location) {

    /**
     * Copies the entries, the exits, the reads and the inputs into immutable lists that keep their order.
     *
     * @param name             the display name of the unit
     * @param description      what the unit does and when it is used
     * @param type             the kind of unit
     * @param icon             the stable icon id that stands for the unit
     * @param entries          the entries, in declaration order
     * @param exits            the exits, in declaration order
     * @param readsConfig      the configuration keys the unit reads
     * @param readsFact        the derived fact kinds the unit reads
     * @param inputs           the inputs a caller binds
     * @param modificationDate the date of the last change of the file
     * @param version          the release the unit was created with or delivered by
     * @param author           who made the unit
     * @param signature        the signature over the file's content, or {@code null}
     * @param location         where the opening {@code ---} was read
     */
    public UnitFrontmatter {
        entries = List.copyOf(entries);
        exits = List.copyOf(exits);
        readsConfig = List.copyOf(readsConfig);
        readsFact = List.copyOf(readsFact);
        inputs = List.copyOf(inputs);
    }

    /**
     * A frontmatter without reads and inputs, the form of a unit that declares neither.
     *
     * @param name             the display name of the unit
     * @param description      what the unit does and when it is used
     * @param type             the kind of unit
     * @param icon             the stable icon id that stands for the unit
     * @param entries          the entries, in declaration order
     * @param exits            the exits, in declaration order
     * @param modificationDate the date of the last change of the file
     * @param version          the release the unit was created with or delivered by
     * @param author           who made the unit
     * @param signature        the signature over the file's content, or {@code null}
     * @param location         where the opening {@code ---} was read
     */
    public UnitFrontmatter(String name, String description, UnitType type, String icon,
            List<InterfaceElement> entries, List<InterfaceElement> exits, LocalDate modificationDate, String version,
            String author, @Nullable String signature, SourceLocation location) {
        this(name, description, type, icon, entries, exits, List.of(), List.of(), List.of(), modificationDate,
                version, author, signature, location);
    }

    /**
     * One entry or one exit of a unit's interface: its name and the optional description on the line below it.
     *
     * @param name        the name of the entry (an identifier) or of the exit (an outcome id)
     * @param description what a caller hands over or may assume (an entry), or what it can rely on (an exit), 1 to 500
     *                    characters; {@code null} when the frontmatter declares none
     */
    public record InterfaceElement(String name, @Nullable String description) {
    }
}
