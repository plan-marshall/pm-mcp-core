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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
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

import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The model catalogue of the machine and of a project (Store Schemas § Model Catalogue, PM-WORK-1): the allowed models per
 * enrolled harness with their source and time, the revision of the document, and the drop of a harness that is no longer
 * enrolled.
 */
@EnableGeneratorController
@DisplayName("Model catalogue store")
class ModelCatalogueStoreTest {

    private static final Instant RECORDED = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final String HARNESS = "p" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String OTHER_HARNESS = "q" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String GONE_HARNESS = "g" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String MODEL = "m" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FAST_MODEL = "f" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String FRONTIER_MODEL = "r" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String OLD_MODEL = "o" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String PROJECT = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

    @TempDir
    Path base;

    private ModelCatalogueStore store;

    @BeforeEach
    void createStore() {
        store = new ModelCatalogueStore(ModelCatalogueStore.machine(base), new TestLocks());
    }

    private static CatalogueModel probe(String model) {
        return new CatalogueModel(model, ModelSource.PROBE, RECORDED);
    }

    @Test
    @DisplayName("an absent catalogue reads no harness at revision 0")
    void absent() {
        var read = store.read();

        assertAll(
                () -> assertTrue(read.harnesses().isEmpty()),
                () -> assertEquals(0L, read.revision()),
                () -> assertFalse(read.allows(HARNESS, MODEL)));
    }

    @Test
    @DisplayName("writes the allowed models per harness and reads them as the role package's catalogue")
    void writeAndRead() {
        store.write(Map.of(HARNESS, List.of(probe(MODEL), probe(FAST_MODEL))), Set.of(HARNESS), 0L);

        var read = store.read();
        assertAll(
                () -> assertEquals(1L, read.revision()),
                () -> assertTrue(read.allows(HARNESS, MODEL)),
                () -> assertFalse(read.allows(HARNESS, FRONTIER_MODEL)),
                () -> assertTrue(read.catalogue().allows(HARNESS, FAST_MODEL)),
                () -> assertFalse(read.catalogue().allows(OTHER_HARNESS, FAST_MODEL)),
                () -> assertEquals(List.of(probe(MODEL), probe(FAST_MODEL)),
                        read.harnesses().get(HARNESS)));
    }

    @Test
    @DisplayName("an entry of a harness that is no longer enrolled is dropped by the write")
    void dropsUnenrolled() {
        store.write(Map.of(HARNESS, List.of(probe(MODEL)), GONE_HARNESS, List.of(probe(OLD_MODEL))),
                Set.of(HARNESS), 0L);

        var read = store.read();
        assertAll(
                () -> assertEquals(Set.of(HARNESS), read.harnesses().keySet()),
                () -> assertFalse(read.allows(GONE_HARNESS, OLD_MODEL)));
    }

    @Test
    @DisplayName("a change against an older revision is refused with the current catalogue")
    void conflict() {
        var enrolled = Set.of(HARNESS);
        store.write(Map.of(HARNESS, List.of(probe(MODEL))), enrolled, 0L);
        var stale = Map.of(HARNESS, List.of(probe(FAST_MODEL)));

        var conflict = assertThrows(SettingsConflict.class, () -> store.write(stale, enrolled, 0L));

        assertAll(
                () -> assertEquals(1L, conflict.currentRevision()),
                () -> assertInstanceOf(ModelCatalogueStore.Snapshot.class, conflict.currentValue()),
                () -> assertTrue(store.read().allows(HARNESS, MODEL)),
                () -> assertFalse(store.read().allows(HARNESS, FAST_MODEL)));
    }

    @Test
    @DisplayName("the document of the project is guarded by the project's local-configuration lock")
    void projectStore() throws Exception {
        Files.createDirectories(base.resolve("projects").resolve(PROJECT));
        var project = new ModelCatalogueStore(ModelCatalogueStore.project(base, PROJECT), new TestLocks());

        project.write(Map.of(HARNESS, List.of(probe(MODEL))), Set.of(HARNESS), 0L);

        assertAll(
                () -> assertTrue(project.read().allows(HARNESS, MODEL)),
                () -> assertEquals(LockKey.localConfiguration(base, PROJECT).lockFile(),
                        ModelCatalogueStore.project(base, PROJECT).lockKey().lockFile()),
                () -> assertTrue(Files.exists(base.resolve("projects").resolve(PROJECT)
                        .resolve(ModelCatalogueStore.FILE_NAME))));
    }

    @Test
    @DisplayName("a harness key outside the client-id grammar is refused by a write and by a read")
    void invalidClientId() throws Exception {
        var file = base.resolve(ModelCatalogueStore.FILE_NAME);
        Files.writeString(file, "{\"format_version\":1,\"revision\":1,\"harnesses\":{\"Upper\":{\"models\":[]}}}",
                UTF_8);
        var upper = Map.of("Upper", List.of(probe(MODEL)));
        var upperSet = Set.of("Upper");

        var writeRefused = assertThrows(IllegalArgumentException.class,
                () -> store.write(upper, upperSet, 0L));
        var readRefused = assertThrows(StoreRefusal.class, store::read);

        assertAll(
                () -> assertTrue(writeRefused.getMessage().contains("Upper")),
                () -> assertEquals(file, readRefused.file()));
    }

    @Test
    @DisplayName("an unknown model source or a model outside its length is refused")
    void refusedDocument() throws Exception {
        var file = base.resolve(ModelCatalogueStore.FILE_NAME);
        Files.writeString(file, """
                {"format_version":1,"revision":1,"harnesses":{"%s":{"models":[
                  {"model":"%s","source":"guess","recorded_at":"%s"}]}}}"""
                .formatted(HARNESS, MODEL, RECORDED), UTF_8);

        assertThrows(StoreRefusal.class, store::read);
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogueModel("", ModelSource.PROBE, RECORDED));
    }

    @Test
    @DisplayName("the store is guarded by a catalogue lock of the machine or a project's local configuration")
    void lockKey() {
        var wrong = new AtomicStoreFile(base.resolve("model-catalogue.json"), LockKey.harnesses(base));

        var wrongLock = new TestLocks();
        var refused = assertThrows(IllegalArgumentException.class, () -> new ModelCatalogueStore(wrong, wrongLock));

        assertTrue(refused.getMessage().contains("catalogue"));
    }
}
