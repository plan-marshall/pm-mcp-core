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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.GradedConfiguration;
import de.planmarshall.core.role.qualification.QualificationRecord;
import de.planmarshall.core.role.qualification.QualificationRecords;
import de.planmarshall.core.role.qualification.RoleTopics;
import de.planmarshall.core.role.qualification.TokenTotal;
import de.planmarshall.core.role.qualification.Topic;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.QualificationSettingsStore;

/**
 * The qualification lookup the model caller asks (Model Work § Qualification Records; § Operator Level): the record is
 * read from the project set, and whether it qualifies is decided at every call against the project's current agreement
 * level and allowed gap — so a changed level applies to an existing record without re-grading, and a warm artifact
 * whose record qualifies only fresh is run fresh.
 */
@DisplayName("Recorded qualification")
class RecordedQualificationTest {

    private static final BigDecimal EIGHTY = new BigDecimal("80");
    private static final BigDecimal NINETY = new BigDecimal("90");

    @TempDir
    Path base;

    @Test
    @DisplayName("a fresh record at the level qualifies the fresh form")
    void freshQualifies() {
        var lookup = lookup(80, 5, fresh(90, 90), List.of());

        assertEquals(Optional.of(RoleForm.FRESH),
                lookup.qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.FRESH));
    }

    @Test
    @DisplayName("a changed agreement level applies to an existing record without re-grading")
    void changedLevelApplies() {
        var record = fresh(85, 85);

        var atEighty = lookup(80, 5, record, List.of())
                .qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.FRESH);
        var atNinety = lookup(90, 5, record, List.of())
                .qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.FRESH);

        assertEquals(Optional.of(RoleForm.FRESH), atEighty);
        assertEquals(Optional.empty(), atNinety);
    }

    @Test
    @DisplayName("a record of another digest or corpus revision qualifies nothing")
    void stale() {
        var lookup = lookup(80, 5, fresh(90, 90), List.of());

        assertEquals(Optional.empty(), lookup.qualifiedForm(GraderFixtures.ROLE, "b".repeat(64), RoleForm.FRESH));
    }

    @Test
    @DisplayName("a role without a record qualifies nothing")
    void absent() {
        var lookup = new RecordedQualification(role -> Optional.empty(), settings(80, 5), () -> corpus(),
                this::topics, GraderFixtures.CORPUS_REVISION);

        assertEquals(Optional.empty(), lookup.qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.FRESH));
    }

    @Test
    @DisplayName("a warm artifact is run warm when the warm form qualifies, and fresh when it does not")
    void warmForm() {
        var warmRecord = warm(90, 90, 1);
        var belowLevel = warm(90, 70, 1);
        var tooWideGap = warm(90, 89, 9);

        assertEquals(Optional.of(RoleForm.WARM),
                lookup(80, 5, warmRecord, List.of()).qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.WARM));
        assertEquals(Optional.of(RoleForm.FRESH),
                lookup(80, 5, belowLevel, List.of()).qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.WARM));
        assertEquals(Optional.of(RoleForm.FRESH),
                lookup(80, 5, tooWideGap, List.of()).qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.WARM));
    }

    @Test
    @DisplayName("the project set's record is written and read back by the lookup")
    void projectSet() {
        var store = GraderFixtures.store(directory());
        store.write(fresh(90, 90));

        var lookup = new RecordedQualification(QualificationRecords.firstOf(store), settings(80, 5),
                RecordedQualificationTest::corpus, this::topics, GraderFixtures.CORPUS_REVISION);

        assertTrue(lookup.qualifiedForm(GraderFixtures.ROLE, digest(), RoleForm.FRESH).isPresent());
    }

    private RecordedQualification lookup(int level, int gap, QualificationRecord record, List<ImportedItem> items) {
        var store = GraderFixtures.store(directory());
        store.write(record);
        return new RecordedQualification(QualificationRecords.firstOf(store), settings(level, gap),
                () -> items.isEmpty() ? corpus() : items, this::topics, GraderFixtures.CORPUS_REVISION);
    }

    private RoleTopics topics(String role) {
        return RoleTopics.of(role, RoleClass.READ_ONLY, corpus());
    }

    private static List<ImportedItem> corpus() {
        return List.of(GraderFixtures.item("review/own", GraderFixtures.ROLE, "clean").build(),
                GraderFixtures.item("review/generic", GraderFixtures.GENERIC_ROLE, "clean").build());
    }

    private Path directory() {
        return base.resolve("projects/pm-mcp-core/roles/qualification");
    }

    private QualificationSettingsStore settings(int level, int gap) {
        var file = base.resolve("projects/pm-mcp-core/local-config.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, """
                    {"format_version":1,"revision":1,
                     "settings":{"qualification":{"agreement_level":%d,"allowed_gap":%d}}}
                    """.formatted(level, gap), UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new QualificationSettingsStore(
                new AtomicStoreFile(file, LockKey.localConfiguration(base, "pm-mcp-core")),
                GraderFixtures.locks());
    }

    private static String digest() {
        return GraderFixtures.role().qualificationDigest();
    }

    private static QualificationRecord fresh(int ownAgreement, int genericAgreement) {
        return record(Map.of(Topic.OWN, BigDecimal.valueOf(ownAgreement),
                Topic.GENERIC, BigDecimal.valueOf(genericAgreement)), Map.of(), null);
    }

    private static QualificationRecord warm(int freshAgreement, int warmAgreement, int gap) {
        return record(Map.of(Topic.OWN, BigDecimal.valueOf(freshAgreement),
                        Topic.GENERIC, BigDecimal.valueOf(freshAgreement)),
                Map.of(Topic.OWN, BigDecimal.valueOf(warmAgreement),
                        Topic.GENERIC, BigDecimal.valueOf(warmAgreement)), BigDecimal.valueOf(gap));
    }

    private static QualificationRecord record(Map<Topic, BigDecimal> freshAgreements,
            Map<Topic, BigDecimal> warmAgreements, BigDecimal gap) {
        var forms = new LinkedHashMap<RoleForm, QualificationRecord.FormResult>();
        forms.put(RoleForm.FRESH, form(freshAgreements));
        if (!warmAgreements.isEmpty()) {
            forms.put(RoleForm.WARM, form(warmAgreements));
        }
        GradedConfiguration configuration = GradedConfiguration.of(GraderFixtures.role());
        return new QualificationRecord(GraderFixtures.ROLE, digest(), configuration,
                GraderFixtures.CORPUS_REVISION, List.of(), forms, gap, 0, GraderFixtures.NOW, List.of());
    }

    private static QualificationRecord.FormResult form(Map<Topic, BigDecimal> agreements) {
        var topics = new LinkedHashMap<Topic, QualificationRecord.TopicResult>();
        agreements.forEach((topic, agreement) -> topics.put(topic,
                new QualificationRecord.TopicResult(agreement, BigDecimal.ZERO, 10)));
        return new QualificationRecord.FormResult(topics, 10, TokenTotal.measured(1_000), 100);
    }
}
