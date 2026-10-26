/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.QualificationCheck;
import de.planmarshall.core.role.qualification.QualificationRecords;
import de.planmarshall.core.role.qualification.RoleTopics;
import de.planmarshall.core.store.QualificationSettingsStore;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The {@link QualificationLookup} the model caller asks whether a role is qualified (Model Work § Role Qualification;
 * § Qualification Records; § Qualification Records, Operator Level): it looks the role's record up in the project set
 * first and then in the set of the role's artifact, and decides at every call, against the project's current agreement
 * level and allowed gap, which form the record qualifies.
 * <p>
 * The project's levels are read at every check, so a changed level applies to an existing record at its next check
 * without re-grading. The corpus supplies the topics the record must hold and the items the catch rate is measured on;
 * the shipped corpus revision binds the record. A record of another digest or revision, an ungraded record, a missing
 * topic and a topic below the level, the margin or the allowed gap all leave the role unqualified, and its tasks take
 * the conservative fallback.
 *
 * @since 0.1
 */
public final class RecordedQualification implements QualificationLookup {

    private final QualificationRecords records;
    private final QualificationSettingsStore settings;
    private final Supplier<List<ImportedItem>> corpus;
    private final Function<String, RoleTopics> topics;
    private final String corpusRevision;

    /**
     * @param records        the role sets, the project set before the set of the role's artifact
     * @param settings       the project's qualification settings
     * @param corpus         the effective corpus items
     * @param topics         the topics of a role, derived from that role's class and the corpus
     * @param corpusRevision the shipped corpus revision
     */
    public RecordedQualification(QualificationRecords records, QualificationSettingsStore settings,
            Supplier<List<ImportedItem>> corpus, Function<String, RoleTopics> topics, String corpusRevision) {
        this.records = Objects.requireNonNull(records, "records");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.corpus = Objects.requireNonNull(corpus, "corpus");
        this.topics = Objects.requireNonNull(topics, "topics");
        this.corpusRevision = Objects.requireNonNull(corpusRevision, "corpusRevision");
    }

    /**
     * @throws StoreRefusal if the project's local configuration is unreadable
     */
    @Override
    public Optional<RoleForm> qualifiedForm(String roleName, String qualificationDigest, RoleForm artifactForm) {
        Objects.requireNonNull(roleName, "roleName");
        Objects.requireNonNull(qualificationDigest, "qualificationDigest");
        Objects.requireNonNull(artifactForm, "artifactForm");
        var entry = records.lookup(roleName).orElse(null);
        if (entry == null) {
            return Optional.empty();
        }
        var level = settings.read().settings();
        var check = new QualificationCheck(topics.apply(roleName), corpus.get(), level);
        return check.check(roleName, entry, qualificationDigest, corpusRevision, artifactForm).qualifiedForm();
    }
}
