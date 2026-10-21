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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * What profile validation needs to know that is not part of the profile document (PM-CLIENT-5): the paths a client's
 * bundle owns and the harness homes of this installation.
 * <p>
 * The loader itself names no harness, so the two sets are data the caller reads from the installation's bundle
 * manifests and directory layout and hands to the validator. A path a bundle manages may never be writable by a
 * worker, and a harness home may never be the writable path as a whole, so validation refuses both. Both sets are
 * matched as normalized, home-relative or absolute spellings; an empty set enforces only the rules the profile
 * carries by itself, which is the shipped-profile build test before a bundle is installed.
 *
 * @param managedBundlePaths the paths of settings, command and skill files and directories a bundle manages, and any
 *                           shared configuration file in which the bundle owns an {@code enforcement} element
 * @param harnessHomes       the home directories of the harnesses of this installation, which are never writable as a
 *                           whole
 * @since 0.1
 */
public record ProfileRules(Set<String> managedBundlePaths, Set<String> harnessHomes) {

    /** Copies the sets and refuses a null member. */
    public ProfileRules {
        managedBundlePaths = copy(managedBundlePaths, "managedBundlePaths");
        harnessHomes = copy(harnessHomes, "harnessHomes");
    }

    /** @return the rules that bind from the profile alone */
    public static ProfileRules none() {
        return new ProfileRules(Set.of(), Set.of());
    }

    private static Set<String> copy(Set<String> values, String name) {
        var copy = new LinkedHashSet<String>();
        for (var value : Objects.requireNonNull(values, name)) {
            copy.add(Objects.requireNonNull(value, name + " entry"));
        }
        return Collections.unmodifiableSet(copy);
    }
}
