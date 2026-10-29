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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
@DisplayName("Model catalogue store")
class ModelCatalogueStoreTest {

    private static final Instant RECORDED = Instant.parse("2026-10-29T10:00:00Z");

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
                () -> assertFalse(read.allows("acme-cli", "acme-standard")));
    }

    @Test
    @DisplayName("writes the allowed models per harness and reads them as the role package's catalogue")
    void writeAndRead() {
        store.write(Map.of("acme-cli", List.of(probe("acme-standard"), probe("acme-fast"))), Set.of("acme-cli"), 0L);

        var read = store.read();
        assertAll(
                () -> assertEquals(1L, read.revision()),
                () -> assertTrue(read.allows("acme-cli", "acme-standard")),
                () -> assertFalse(read.allows("acme-cli", "acme-frontier")),
                () -> assertTrue(read.catalogue().allows("acme-cli", "acme-fast")),
                () -> assertFalse(read.catalogue().allows("other-cli", "acme-fast")),
                () -> assertEquals(List.of(probe("acme-standard"), probe("acme-fast")),
                        read.harnesses().get("acme-cli")));
    }

    @Test
    @DisplayName("an entry of a harness that is no longer enrolled is dropped by the write")
    void dropsUnenrolled() {
        store.write(Map.of("acme-cli", List.of(probe("acme-standard")), "gone-cli", List.of(probe("old"))),
                Set.of("acme-cli"), 0L);

        var read = store.read();
        assertAll(
                () -> assertEquals(Set.of("acme-cli"), read.harnesses().keySet()),
                () -> assertFalse(read.allows("gone-cli", "old")));
    }

    @Test
    @DisplayName("a change against an older revision is refused with the current catalogue")
    void conflict() {
        store.write(Map.of("acme-cli", List.of(probe("acme-standard"))), Set.of("acme-cli"), 0L);

        var conflict = assertThrows(SettingsConflict.class,
                () -> store.write(Map.of("acme-cli", List.of(probe("acme-fast"))), Set.of("acme-cli"), 0L));

        assertAll(
                () -> assertEquals(1L, conflict.currentRevision()),
                () -> assertInstanceOf(ModelCatalogueStore.Snapshot.class, conflict.currentValue()),
                () -> assertTrue(store.read().allows("acme-cli", "acme-standard")),
                () -> assertFalse(store.read().allows("acme-cli", "acme-fast")));
    }

    @Test
    @DisplayName("the document of the project is guarded by the project's local-configuration lock")
    void projectStore() throws Exception {
        Files.createDirectories(base.resolve("projects").resolve("pm-mcp-core"));
        var project = new ModelCatalogueStore(ModelCatalogueStore.project(base, "pm-mcp-core"), new TestLocks());

        project.write(Map.of("acme-cli", List.of(probe("acme-standard"))), Set.of("acme-cli"), 0L);

        assertAll(
                () -> assertTrue(project.read().allows("acme-cli", "acme-standard")),
                () -> assertEquals(LockKey.localConfiguration(base, "pm-mcp-core").lockFile(),
                        ModelCatalogueStore.project(base, "pm-mcp-core").lockKey().lockFile()),
                () -> assertTrue(Files.exists(base.resolve("projects").resolve("pm-mcp-core")
                        .resolve(ModelCatalogueStore.FILE_NAME))));
    }

    @Test
    @DisplayName("a harness key outside the client-id grammar is refused by a write and by a read")
    void invalidClientId() throws Exception {
        var file = base.resolve(ModelCatalogueStore.FILE_NAME);
        Files.writeString(file, "{\"format_version\":1,\"revision\":1,\"harnesses\":{\"Upper\":{\"models\":[]}}}",
                UTF_8);

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> store.write(Map.of("Upper", List.of(probe("x"))), Set.of("Upper"), 0L)),
                () -> assertEquals(file, assertThrows(StoreRefusal.class, store::read).file()));
    }

    @Test
    @DisplayName("an unknown model source or a model outside its length is refused")
    void refusedDocument() throws Exception {
        var file = base.resolve(ModelCatalogueStore.FILE_NAME);
        Files.writeString(file, """
                {"format_version":1,"revision":1,"harnesses":{"acme-cli":{"models":[
                  {"model":"x","source":"guess","recorded_at":"2026-10-29T10:00:00Z"}]}}}""", UTF_8);

        assertThrows(StoreRefusal.class, store::read);
        assertThrows(IllegalArgumentException.class,
                () -> new CatalogueModel("", ModelSource.PROBE, RECORDED));
    }

    @Test
    @DisplayName("the store is guarded by a catalogue lock of the machine or a project's local configuration")
    void lockKey() {
        var wrong = new AtomicStoreFile(base.resolve("model-catalogue.json"), LockKey.harnesses(base));

        assertThrows(IllegalArgumentException.class, () -> new ModelCatalogueStore(wrong, new TestLocks()));
    }
}
