/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The three role sets of a machine and a project (PM-WORK-1, Role Sets &amp; Precedence): what the release ships, what the
 * operator installed for the machine, and what it installed for one project.
 * <p>
 * The shipped set is given as its documents, read from the resources of {@code classpath:/roles/} which the assembly
 * supplies; the machine and project sets are the directories below {@code <PM_MCP_BASE>}. A directory that does not exist
 * holds no role, which is the state of a machine before the first install.
 *
 * @param shipped          the shipped role document by role name
 * @param machineDirectory the machine role set, {@code <PM_MCP_BASE>/roles/}; may be {@code null}
 * @param projectDirectory the project role set, {@code <PM_MCP_BASE>/projects/<project_id>/roles/}; may be {@code null}
 * @since 0.1
 */
public record RoleSets(Map<String, ShippedRole> shipped, Path machineDirectory, Path projectDirectory) {

    /** Copies the shipped documents. */
    public RoleSets {
        shipped = Collections.unmodifiableMap(new LinkedHashMap<>(shipped));
    }

    /**
     * One shipped role. The release compiles the document, so it is trusted without a record of the installation; the
     * build test validates it.
     * <p>
     * A class rather than a record: the document is an array, whose identity equality a record would inherit, and two
     * shipped documents with the same bytes are the same role.
     *
     * @since 0.1
     */
    public static final class ShippedRole {

        private final byte[] document;

        /**
         * @param document the role document, UTF-8 encoded
         */
        public ShippedRole(byte[] document) {
            this.document = Objects.requireNonNull(document, "document").clone();
        }

        /** @return a copy of the role document, UTF-8 encoded */
        public byte[] document() {
            return document.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShippedRole role && Arrays.equals(document, role.document);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(document);
        }

        @Override
        public String toString() {
            return "ShippedRole[%d bytes]".formatted(document.length);
        }
    }

    /**
     * @param shipped the shipped role by name
     * @return the sets of a machine without a project set
     */
    public static RoleSets of(Map<String, ShippedRole> shipped) {
        return new RoleSets(shipped, null, null);
    }

    /**
     * @param shipped          the shipped roles
     * @param machineDirectory the machine role set
     * @return the sets with a machine set
     */
    public RoleSets withMachine(Path machineDirectory) {
        return new RoleSets(shipped, machineDirectory, projectDirectory);
    }

    /**
     * @param shipped          the shipped roles
     * @param projectDirectory the project role set
     * @return the sets with a project set
     */
    public RoleSets withProject(Path projectDirectory) {
        return new RoleSets(shipped, machineDirectory, projectDirectory);
    }
}
