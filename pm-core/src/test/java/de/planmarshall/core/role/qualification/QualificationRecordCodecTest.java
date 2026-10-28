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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
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
@DisplayName("Qualification record codec")
class QualificationRecordCodecTest {

    @TempDir
    Path directory;

    @Test
    @DisplayName("a record round-trips through its canonical text")
    void roundTrip() {
        var entry = fullRecord();

        var document = QualificationRecordCodec.write(entry);
        var read = QualificationRecordCodec.read(directory.resolve("review-code.json"), document);

        assertAll(
                () -> assertEquals(entry, read),
                () -> assertEquals(new String(document, StandardCharsets.UTF_8),
                        new String(QualificationRecordCodec.write(read), StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("the document holds every required member and the canonical text has one form")
    void canonicalText() {
        var text = new String(QualificationRecordCodec.write(fullRecord()), StandardCharsets.UTF_8);

        assertAll(
                () -> assertTrue(text.startsWith("{\"configuration\":")),
                () -> assertTrue(text.contains("\"format_version\":1")),
                () -> assertTrue(text.contains("\"score\":\"ungraded\"")),
                () -> assertTrue(text.contains("\"total_tokens\":\"unmeasured\"")),
                () -> assertTrue(text.contains("\"tokens_in\":3000")),
                () -> assertTrue(text.contains("\"tokens_out\":1000")),
                () -> assertTrue(text.contains("\"answer\":{\"detail\":\"a defect\"}")),
                () -> assertTrue(text.contains("\"missed_sources\":[\"reviews\"]")));
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
                        text().replace("\"checkout\":\"current\"", "\"checkout\":\"elsewhere\"")));

        for (var entry : cases.entrySet()) {
            var bytes = entry.getValue().getBytes(StandardCharsets.UTF_8);
            var refused = assertThrows(StoreRefusal.class,
                    () -> QualificationRecordCodec.read(file, bytes), entry.getKey());
            assertEquals(file, refused.file());
        }
    }

    @Test
    @DisplayName("a record without the fresh form is refused at construction")
    void freshRequired() {
        assertRefused(() -> new QualificationRecord(
                "review-code", "a".repeat(64), QualificationFixtures.configuration(), "corpus-7", List.of(),
                Map.of(), null, 0, QualificationFixtures.GRADED_AT, List.of()));
    }

    @Test
    @DisplayName("a gap without both forms is refused at construction")
    void gapNeedsBothForms() {
        var fresh = QualificationFixtures.form(Map.of(Topic.OWN, new BigDecimal("90")), 10);
        assertRefused(() -> new QualificationRecord(
                "review-code", "a".repeat(64), QualificationFixtures.configuration(), "corpus-7", List.of(),
                Map.of(RoleForm.FRESH, fresh), BigDecimal.ONE, 0, QualificationFixtures.GRADED_AT,
                List.of()));
    }

    private static void assertRefused(Executable executable) {
        assertThrows(IllegalArgumentException.class, executable);
    }

    @Test
    @DisplayName("the ungraded count is the number of ungraded items, never a separate claim")
    void ungradedCountDerived() {
        var graded = QualificationFixtures.recordWithBaseline(Map.of(Topic.OWN, new BigDecimal("90")),
                new BigDecimal("10"));

        assertRefused(() -> new QualificationRecord(graded.role(),
                graded.qualificationDigest(), graded.configuration(), graded.corpusRevision(),
                graded.missedSources(), graded.forms(), null, 1, graded.gradedAt(), List.of()));
    }

    @Test
    @DisplayName("the token split is a measured pair that sums to the total")
    void tokenSplitMustSum() {
        var total = TokenTotal.measured(120, 100, 20);

        assertAll(() -> assertTrue(total.measured()),
                () -> assertEquals(OptionalLong.of(100), total.splitIn()),
                () -> assertEquals(OptionalLong.of(20), total.splitOut()),
                () -> assertEquals(TokenTotal.measured(240, 200, 40), total.plus(total)));
        assertRefused(() -> TokenTotal.measured(120, 100, 10));
        assertRefused(() -> new TokenTotal(120L, 100L, null));
        assertRefused(() -> new TokenTotal(null, 1L, 0L));
    }

    private String text() {
        return new String(QualificationRecordCodec.write(fullRecord()), StandardCharsets.UTF_8);
    }

    private static QualificationRecord fullRecord() {
        var fresh = new QualificationRecord.FormResult(
                Map.of(
                        Topic.OWN, new QualificationRecord.TopicResult(new BigDecimal("90"),
                                new BigDecimal("80"), 10),
                        Topic.GENERIC, new QualificationRecord.TopicResult(new BigDecimal("83.33"),
                                new BigDecimal("70"), 12)),
                22, TokenTotal.measured(4_000, 3_000, 1_000), 1_000);
        var warm = new QualificationRecord.FormResult(
                Map.of(
                        Topic.OWN, new QualificationRecord.TopicResult(new BigDecimal("85"),
                                new BigDecimal("80"), 10),
                        Topic.GENERIC, new QualificationRecord.TopicResult(new BigDecimal("80"),
                                new BigDecimal("70"), 12)),
                22, TokenTotal.unmeasured(), 2_000);
        var items = List.of(
                new QualificationRecord.ItemResult("review/clean-plan", RoleForm.FRESH, Checkout.CURRENT,
                        JsonNode.string("clean"), Grade.of(true), TokenTotal.measured(1_200, 900, 300), 500L),
                new QualificationRecord.ItemResult("review/clean-plan", RoleForm.WARM, Checkout.ITEM_COMMIT,
                        JsonNode.object(Map.of("detail", JsonNode.string("a defect"))), Grade.ungraded(),
                        TokenTotal.unmeasured(), null));
        return new QualificationRecord("review-code", "a".repeat(64), QualificationFixtures.configuration(),
                "corpus-7", List.of("reviews"),
                Map.of(RoleForm.FRESH, fresh, RoleForm.WARM, warm), new BigDecimal("5"), 1,
                QualificationFixtures.GRADED_AT, items);
    }

    @Test
    @DisplayName("the record's topic accessor finds the fresh topic result")
    void topicAccessor() {
        var entry = fullRecord();

        assertEquals(new BigDecimal("90"), entry.topic(Topic.OWN).orElseThrow().agreement());
        assertEquals(2, entry.offered());
    }
}
