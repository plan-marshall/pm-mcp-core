/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.ModelCatalogue;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The model catalogue document (Store Schemas § Model Catalogue, PM-WORK-1): the allowed models per enrolled harness,
 * each with its source and the instant it was recorded, and the read projection the role package validates against.
 * <p>
 * The member names, the format version and the source wire names are the specified document format. The harness ids, the
 * model identifiers, the revision and the instant are generated.
 */
@EnableGeneratorController
@DisplayName("Model catalogue codec")
class ModelCatalogueCodecTest {

    @TempDir
    Path base;

    private Path file;

    private final String harness = "h-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private final String model = "m-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private final long revision = Generators.longs(1, 1_000_000).next();
    private final Instant recordedAt = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    @BeforeEach
    void createFile() {
        file = base.resolve("model-catalogue.json");
    }

    @Test
    @DisplayName("a written catalogue is read back with its revision and its read projection")
    void roundTrip() {
        var models = List.of(new CatalogueModel(model, ModelSource.PROBE, recordedAt),
                new CatalogueModel(model + "-operator", ModelSource.OPERATOR, recordedAt));
        var written = ModelCatalogueCodec.write(Map.of(harness, models), revision);

        var read = ModelCatalogueCodec.read(file, written);

        assertAll(
                () -> assertEquals(revision, read.revision(), "the revision must round-trip"),
                () -> assertEquals(Map.of(harness, models), read.harnesses(),
                        "the allowed models must round-trip"),
                () -> assertEquals(new ModelCatalogue(Map.of(harness, Set.of(model, model + "-operator"))),
                        read.catalogue(), "the read projection names every allowed model"),
                () -> assertTrue(read.catalogue().allows(harness, model),
                        "the projection allows a written model"));
    }

    @Test
    @DisplayName("an unknown member, a negative revision and a harness outside the client-id grammar are refused")
    void refusedDocuments() {
        // Contract literals: the member names and the client-id grammar's upper case
        var unknownBytes = "{\"format_version\":1,\"revision\":1,\"harnesses\":{},\"unknown\":1}".getBytes(UTF_8);
        var negativeBytes = "{\"format_version\":1,\"revision\":-1,\"harnesses\":{}}".getBytes(UTF_8);
        var notAClientIdBytes = "{\"format_version\":1,\"revision\":1,\"harnesses\":{\"Harness\":{\"models\":[]}}}"
                .getBytes(UTF_8);

        var unknown = assertThrows(StoreRefusal.class, () -> ModelCatalogueCodec.read(file, unknownBytes),
                "an unknown member must be refused");
        var negative = assertThrows(StoreRefusal.class, () -> ModelCatalogueCodec.read(file, negativeBytes),
                "a negative revision must be refused");
        var notAClientId = assertThrows(StoreRefusal.class,
                () -> ModelCatalogueCodec.read(file, notAClientIdBytes),
                "a harness outside the client-id grammar must be refused");

        assertAll(
                () -> assertEquals(file, unknown.file(), "the refusal names the file"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, negative.outcome(),
                        "a negative revision makes the document unreadable"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, notAClientId.outcome(),
                        "a bad harness key makes the document unreadable"));
    }

    @Test
    @DisplayName("a source outside the closed list is refused")
    void unknownSource() {
        var document = """
                {"format_version":1,"revision":1,"harnesses":{"%s":{"models":[
                 {"model":"%s","source":"invented","recorded_at":"2026-10-29T10:00:00Z"}]}}}"""
                .formatted(harness, model)
                .getBytes(UTF_8);

        var refused = assertThrows(StoreRefusal.class, () -> ModelCatalogueCodec.read(file, document),
                "an unknown source must be refused");

        assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.outcome(),
                "an unknown source makes the document unreadable");
    }
}
