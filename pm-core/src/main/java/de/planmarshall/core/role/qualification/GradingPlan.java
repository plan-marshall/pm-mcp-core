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

import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.corpus.ImportResult;
import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * Everything one grading run needs (Model Work § Grading; § Corpus Sources): the role's effective configuration, the
 * corpus it is graded on with the verdict of the import that produced it, the revision of that corpus, the project's
 * own repository, the grading profile, the judge, and the record the last grading of the same digest left.
 *
 * @param role           the role's effective configuration
 * @param items          the items the import generated
 * @param importResult   the verdict of the import
 * @param corpusRevision the revision of the corpus
 * @param projectRepository the repository of the project; an item whose origin names it and whose commit is present is
 *                      graded at that commit
 * @param profile        how often each item is offered
 * @param judge          the judge configuration and its instrument-check items
 * @param checkouts      whether a commit of the project's repository is present
 * @param previous       the record of an earlier grading of the same digest and revision; its graded items are reused
 * @since 0.1
 */
public record GradingPlan(EffectiveRole role, List<ImportedItem> items, ImportResult importResult,
String corpusRevision, URI projectRepository, GradingProfile profile, JudgeConfiguration judge,
ItemCheckout checkouts, Optional<QualificationRecord> previous) {

    /**
     * @throws IllegalArgumentException if the corpus revision is empty
     */
    public GradingPlan {
        Objects.requireNonNull(role, "role");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(importResult, "importResult");
        Objects.requireNonNull(corpusRevision, "corpusRevision");
        if (corpusRevision.isEmpty()) {
            throw new IllegalArgumentException("'corpusRevision' must not be empty");
        }
        Objects.requireNonNull(projectRepository, "projectRepository");
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(judge, "judge");
        Objects.requireNonNull(checkouts, "checkouts");
        Objects.requireNonNull(previous, "previous");
    }
}
