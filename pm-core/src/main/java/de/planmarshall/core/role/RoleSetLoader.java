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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Loads the effective role set of a machine and a project (PM-WORK-1, Role Sets &amp; Precedence).
 * <p>
 * The loader reads the shipped, machine and project artifacts from lowest to highest precedence. An artifact replaces the
 * artifact of the same name of a lower set as a whole and is never merged; a higher set adds a role that the lower sets
 * do not define. An artifact that fails validation leaves its role undefined and removes the lower artifact: an invalid
 * role never falls back.
 * <p>
 * The loader reads the sets afresh at every call, so an edited artifact applies to the next read, as the specification
 * requires at every composition, task creation and offer.
 *
 * @since 0.1
 */
public final class RoleSetLoader {

    private static final String SUFFIX = ".json";

    private RoleSetLoader() {
    }

    /**
     * @param sets the three sets
     * @return the effective role set
     */
    public static RoleSet load(RoleSets sets) {
        var effective = new TreeMap<String, RoleArtifact>();
        var refused = new ArrayList<RoleSet.RefusedRole>();
        for (var entry : sets.shipped().entrySet()) {
            read(entry.getKey(), entry.getValue().document(), RoleSetKind.SHIPPED, effective, refused);
        }
        readDirectory(sets.machineDirectory(), RoleSetKind.MACHINE, effective, refused);
        readDirectory(sets.projectDirectory(), RoleSetKind.PROJECT, effective, refused);
        return new RoleSet(effective, refused);
    }

    private static void readDirectory(Path directory, RoleSetKind kind, Map<String, RoleArtifact> effective,
            List<RoleSet.RefusedRole> refused) {
        for (var name : names(directory)) {
            read(name, bytes(directory.resolve(name + SUFFIX)), kind, effective, refused);
        }
    }

    private static void read(String name, byte[] document, RoleSetKind kind, Map<String, RoleArtifact> effective,
            List<RoleSet.RefusedRole> refused) {
        try {
            effective.put(name, RoleArtifactValidator.validate(document, name));
        } catch (RoleRefusal refusal) {
            effective.remove(name);
            refused.add(new RoleSet.RefusedRole(name, kind, refusal));
        }
    }

    private static List<String> names(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            return List.of();
        }
        try (var entries = Files.list(directory)) {
            return entries.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(SUFFIX))
                    .map(name -> name.substring(0, name.length() - SUFFIX.length()))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("The role set '%s' cannot be listed".formatted(directory), e);
        }
    }

    private static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("The role artifact '%s' cannot be read".formatted(file), e);
        }
    }
}
