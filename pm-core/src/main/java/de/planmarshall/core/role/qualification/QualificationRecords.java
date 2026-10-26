/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.List;
import java.util.Optional;

/**
 * The qualification records a role's configuration is looked up in (Model Work § Qualification Records): the project
 * set first, then the set of the role's artifact. A lookup that finds no record answers empty; the caller then treats
 * the role as unqualified and its tasks take the conservative fallback.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface QualificationRecords {

    /**
     * @param role a role name
     * @return the record of the role; empty if no set holds one
     */
    Optional<QualificationRecord> lookup(String role);

    /**
     * @param sources the role sets in lookup order: the project set first, then the set of the artifact
     * @return a lookup that answers with the first record its sources hold
     */
    static QualificationRecords firstOf(QualificationRecords... sources) {
        var ordered = List.of(sources);
        return role -> {
            for (var source : ordered) {
                var found = source.lookup(role);
                if (found.isPresent()) {
                    return found;
                }
            }
            return Optional.empty();
        };
    }
}
