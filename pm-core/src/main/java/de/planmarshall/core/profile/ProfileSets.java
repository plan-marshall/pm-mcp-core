/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The three profile sets of a machine and a project (PM-CLIENT-5, Profile Sets): what the release ships, what the
 * operator installed for the machine, and what it installed for one project.
 * <p>
 * The shipped set is given as its documents, read from the resources of {@code pm-api}; the machine and project sets are
 * the directories below {@code <PM_MCP_BASE>}, whose {@code verification/} subdirectories hold the records and whose
 * {@code <client_id>.json.hmac} sidecars hold the integrity records. A directory that does not exist holds no profile,
 * which is the state of a machine before the first install.
 *
 * @param shipped          the shipped profile by client id
 * @param machineDirectory the machine profile set, {@code <PM_MCP_BASE>/profiles/}; may be {@code null}
 * @param projectDirectory the project profile set, {@code <PM_MCP_BASE>/projects/<project_id>/profiles/}; may be
 *                         {@code null}
 * @since 0.1
 */
public record ProfileSets(Map<String, ShippedProfile> shipped, Path machineDirectory, Path projectDirectory) {

    /** Copies the shipped profiles. */
    public ProfileSets {
        shipped = Collections.unmodifiableMap(new LinkedHashMap<>(shipped));
    }

    /**
     * One shipped profile. The release compiles the document, so it is trusted without a record of the installation;
     * the build test validates it.
     *
     * @param document the profile document, UTF-8 encoded
     * @since 0.1
     */
    public record ShippedProfile(byte[] document) {

        /** Copies the document. */
        public ShippedProfile {
            document = document.clone();
        }

        /** @return a copy of the document, so the hash of the value cannot change from outside */
        @Override
        public byte[] document() {
            return document.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof ShippedProfile(byte[] otherDocument) && Arrays.equals(document, otherDocument);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(document);
        }

        @Override
        public String toString() {
            return "ShippedProfile[document=[" + document.length + " bytes]]";
        }
    }

    /**
     * @param shipped the shipped profile by client id
     * @return the sets of a machine without a project set
     */
    public static ProfileSets of(Map<String, ShippedProfile> shipped) {
        return new ProfileSets(shipped, null, null);
    }

    /**
     * @param shipped          the shipped profiles
     * @param machineDirectory the machine profile set
     * @return the sets with a machine set
     */
    public static ProfileSets withMachine(Map<String, ShippedProfile> shipped, Path machineDirectory) {
        return new ProfileSets(shipped, machineDirectory, null);
    }

    /**
     * @param shipped          the shipped profiles
     * @param machineDirectory the machine profile set
     * @param projectDirectory the project profile set
     * @return the sets with a project set
     */
    public static ProfileSets withProject(Map<String, ShippedProfile> shipped, Path machineDirectory,
            Path projectDirectory) {
        return new ProfileSets(shipped, machineDirectory, projectDirectory);
    }

}
