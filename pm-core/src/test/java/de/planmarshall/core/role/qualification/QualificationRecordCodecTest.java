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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.StoreRefusal;

/**
 * A qualification record has exactly one wire shape (Store Schemas § Role Qualification Record): every member is
 * written, an ungraded score and an unmeasured token count keep their marks, an object answer survives, and whatever
 * does not parse is a refusal naming the file, never an empty record.
 */
@EnableGeneratorController
@DisplayName("Qualification record codec")
class QualificationRecordCodecTest {

    private static final String DIGEST = "%064x".formatted(Generators.longs().next());
    private static final String CORPUS_REVISION = "corpus-" + Generators.integers(1, 99).next();

    @TempDir
    Path directory;

    @Test
    @DisplayName("a record round-trips through its canonical text")
    void roundTrip() {
        var entry = fullRecord();

        var document = QualificationRecordCodec.write(entry);
        var read = QualificationRecordCodec.read(directory.resolve("review-code.json"), document);

        assertAll(
                () -> assertEquals(entry, read, "the record survives the round trip"),
                () -> assertEquals(new String(document, StandardCharsets.UTF_8),
                        new String(QualificationRecordCodec.write(read), StandardCharsets.UTF_8),
                        "the canonical text is stable on rewrite"));
    }

    @Test
    @DisplayName("a topic below the compiled item count is marked thin and one at it is not")
    void thinMark() {
        var below = recordWithTopicItems(QualificationRecord.TopicResult.THIN_LIMIT - 1);
        var at = recordWithTopicItems(QualificationRecord.TopicResult.THIN_LIMIT);
        var belowText = new String(QualificationRecordCodec.write(below), StandardCharsets.UTF_8);
        var atText = new String(QualificationRecordCodec.write(at), StandardCharsets.UTF_8);

        assertAll(
                () -> assertEquals(19, QualificationRecord.TopicResult.THIN_LIMIT - 1,
                        "the compiled limit is twenty"),
                () -> assertTrue(belowText.contains("\"thin\":true"), "the thinner topic is marked"),
                () -> assertTrue(atText.contains("\"thin\":false"), "the topic at the limit is not marked"),
                () -> assertTrue(below.topic(Topic.OWN).orElseThrow().thin(),
                        "the record answers the mark from the count"),
                () -> assertEquals(below,
                        QualificationRecordCodec.read(directory.resolve("thin.json"),
                                QualificationRecordCodec.write(below)),
                        "the marked topic round-trips"));
    }

    @Test
    @DisplayName("a record without a judge check round-trips without the member")
    void judgeCheckAbsent() {
        var entry = recordWithTopicItems(QualificationRecord.TopicResult.THIN_LIMIT);
        var document = QualificationRecordCodec.write(entry);
        var read = QualificationRecordCodec.read(directory.resolve("judge.json"), document);

        assertAll(
                () -> assertNull(entry.judgeCheck(), "the fixture carries no judge check"),
                () -> assertFalse(new String(document, StandardCharsets.UTF_8).contains("\"judge_check\""),
                        "the absent member is not written"),
                () -> assertEquals(entry, read, "the record survives the round trip"));
    }

    @Test
    @DisplayName("the document holds every required member and the canonical text has one form")
    void canonicalText() {
        var text = new String(QualificationRecordCodec.write(fullRecord()), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(text.startsWith("{\"configuration\":"), "the configuration member comes first"),
                () -> assertTrue(text.contains("\"format_version\":1"), "the format version is written as one"),
                () -> assertTrue(text.contains("\"score\":\"ungraded\""), "the ungraded score keeps its mark"),
                () -> assertTrue(text.contains("\"total_tokens\":\"unmeasured\""),
                        "the unmeasured total keeps its mark"),
                () -> assertTrue(text.contains("\"total_tokens\":4000"), "the measured total is written"),
                () -> assertFalse(text.contains("\"tokens_in\""), "no form or item writes an input split"),
                () -> assertFalse(text.contains("\"tokens_out\""), "no form or item writes an output split"),
                () -> assertTrue(text.contains("\"answer\":{\"detail\":\"a defect\"}"),
                        "an object answer keeps its members"),
                () -> assertTrue(text.contains("\"missed_sources\":[\"reviews\"]"),
                        "the missed sources are listed"));
    }

    @Test
    @DisplayName("an unreadable document is refused with the file named")
    void unreadable() {
        var file = directory.resolve("review-code.json");
        var cases = Map.ofEntries(
                Map.entry("no members", "{\"format_version\":1}"),
                Map.entry("another version", "{\"format_version\":2}"),
                Map.entry("content after", QualificationRecordCodec.write(fullRecord()) + "{}"),
                Map.entry("undeclared member", text().replace("\"role\"", "\"role_\"")),
                Map.entry("a string agreement", text().replace("\"agreement\":90", "\"agreement\":\"90\"")),
                Map.entry("another score word",
                        text().replace("\"score\":\"ungraded\"", "\"score\":\"lost\"")),
                Map.entry("zero tokens",
                        text().replace("\"total_tokens\":\"unmeasured\"", "\"total_tokens\":0")),
                Map.entry("another form", text().replace("\"form\":\"fresh\"", "\"form\":\"lukewarm\"")),
                Map.entry("another checkout",
                        text().replace("\"checkout\":\"current\"", "\"checkout\":\"elsewhere\"")),
                Map.entry("a form input split",
                        text().replace("\"total_tokens\":\"unmeasured\"",
                                "\"total_tokens\":\"unmeasured\",\"tokens_in\":3000")),
                Map.entry("an item output split",
                        text().replace("\"total_tokens\":1200", "\"total_tokens\":1200,\"tokens_out\":300")),
                Map.entry("a thin mark that contradicts the count",
                        text().replace("\"thin\":true", "\"thin\":false")),
                Map.entry("another judge check result",
                        text().replace("\"judge_check\":\"passed\"", "\"judge_check\":\"unknown\"")));

        for (var entry : cases.entrySet()) {
            var bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            var refused = assertThrows(StoreRefusal.class,
                    () -> QualificationRecordCodec.read(file, bytes), entry.getKey());
            assertEquals(file, refused.file(), "the refusal names the record file");
        }
    }

    @Test
    @DisplayName("a record without the fresh form is refused at construction")
    void freshRequired() {
        assertRefused(() -> new QualificationRecord(
                QualificationFixtures.ROLE, DIGEST, QualificationFixtures.configuration(), CORPUS_REVISION,
                List.of(), Map.of(), null, 0, null, QualificationFixtures.GRADED_AT, List.of()));
    }

    @Test
    @DisplayName("a gap without both forms is refused at construction")
    void gapNeedsBothForms() {
        var fresh = QualificationFixtures.form(Map.of(Topic.OWN, new BigDecimal("90")), 10);
        assertRefused(() -> new QualificationRecord(
                QualificationFixtures.ROLE, DIGEST, QualificationFixtures.configuration(), CORPUS_REVISION,
                List.of(), Map.of(RoleForm.FRESH, fresh), BigDecimal.ONE, 0, null,
                QualificationFixtures.GRADED_AT, List.of()));
    }

    private static void assertRefused(Executable executable) {
        assertThrows(IllegalArgumentException.class, executable, "the record construction is refused");
    }

    @Test
    @DisplayName("the ungraded count is the number of ungraded items, never a separate claim")
    void ungradedCountDerived() {
        var graded = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, new BigDecimal("90")),
                new BigDecimal("10"));

        assertRefused(() -> new QualificationRecord(graded.role(),
                graded.qualificationDigest(), graded.configuration(), graded.corpusRevision(),
                graded.missedSources(), graded.forms(), null, 1, null, graded.gradedAt(), List.of()));
    }

    @Test
    @DisplayName("the token split is a measured pair that sums to the total")
    void tokenSplitMustSum() {
        var total = TokenTotal.measured(120, 100, 20);

        assertAll(() -> assertTrue(total.measured(), "the token total is measured"),
                () -> assertEquals(OptionalLong.of(100), total.splitIn(), "the input split is one hundred"),
                () -> assertEquals(OptionalLong.of(20), total.splitOut(), "the output split is twenty"),
                () -> assertEquals(TokenTotal.measured(240, 200, 40), total.plus(total),
                        "adding the total doubles both splits"));
        assertRefused(() -> TokenTotal.measured(120, 100, 10));
        assertRefused(() -> new TokenTotal(120L, 100L, null));
        assertRefused(() -> new TokenTotal(null, 1L, 0L));
    }

    private String text() {
        return new String(QualificationRecordCodec.write(fullRecord()), StandardCharsets.UTF_8);
    }

    /**
     * @param items the graded items of the one topic
     * @return a fresh-only record measured on a single own topic with that count
     */
    private static QualificationRecord recordWithTopicItems(int items) {
        var topic = new QualificationRecord.TopicResult(new BigDecimal("90"), new BigDecimal("10"), items);
        var form = new QualificationRecord.FormResult(Map.of(Topic.OWN, topic), items,
                TokenTotal.measured(1_000), 100);
        return new QualificationRecord(QualificationFixtures.ROLE, DIGEST, QualificationFixtures.configuration(),
                CORPUS_REVISION, List.of(), Map.of(RoleForm.FRESH, form), null, 0, null,
                QualificationFixtures.GRADED_AT, List.of());
    }

    private static QualificationRecord fullRecord() {
        var itemId = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT) + "/clean-plan";
        var itemAnswer = Generators.letterStrings(3, 10).next();
        var fresh = new QualificationRecord.FormResult(
                Map.of(
                        Topic.OWN, new QualificationRecord.TopicResult(new BigDecimal("90"),
                                new BigDecimal("80"), 10),
                        Topic.GENERIC, new QualificationRecord.TopicResult(new BigDecimal("83.33"),
                                new BigDecimal("70"), 12)),
                22, TokenTotal.measured(4_000), 1_000);
        var warm = new QualificationRecord.FormResult(
                Map.of(
                        Topic.OWN, new QualificationRecord.TopicResult(new BigDecimal("85"),
                                new BigDecimal("80"), 10),
                        Topic.GENERIC, new QualificationRecord.TopicResult(new BigDecimal("80"),
                                new BigDecimal("70"), 12)),
                22, TokenTotal.unmeasured(), 2_000);
        var items = List.of(
                new QualificationRecord.ItemResult(itemId, RoleForm.FRESH, Checkout.CURRENT,
                        JsonNode.string(itemAnswer), Grade.of(true), TokenTotal.measured(1_200), 500L),
                new QualificationRecord.ItemResult(itemId, RoleForm.WARM, Checkout.ITEM_COMMIT,
                        JsonNode.object(Map.of("detail", JsonNode.string("a defect"))), Grade.ungraded(),
                        TokenTotal.unmeasured(), null));
        return new QualificationRecord(QualificationFixtures.ROLE, DIGEST, QualificationFixtures.configuration(),
                CORPUS_REVISION, List.of("reviews"),
                Map.of(RoleForm.FRESH, fresh, RoleForm.WARM, warm), new BigDecimal("5"), 1,
                QualificationRecord.JudgeCheckResult.PASSED, QualificationFixtures.GRADED_AT, items);
    }

    @Test
    @DisplayName("the record's topic accessor finds the fresh topic result")
    void topicAccessor() {
        var entry = fullRecord();

        assertEquals(new BigDecimal("90"), entry.topic(Topic.OWN).orElseThrow().agreement(),
                "the own topic agreement is ninety");
        assertEquals(2, entry.offered(), "the record counts two offered items");
    }
}
