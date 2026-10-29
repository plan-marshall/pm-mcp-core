/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.config.SettingsConflict;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The machine's provider defaults and the revision rule of {@code machine-config.json} (Store Schemas § Machine
 * Configuration Store, § Machine Provider Defaults): the member is written and the other members of the operator's
 * document survive, among them {@code credentials.store}; a change against an older revision is refused with the current
 * value.
 */
@DisplayName("Machine configuration store")
class MachineConfigurationStoreTest {

    private static final String DOCUMENT = """
            {"format_version":1,"revision":3,"max_build_slots":4,"lsp_idle_timeout_seconds":900,
             "credentials":{"store":"os"},
             "harnesses":{"default":"acme-cli"},
             "plan_defaults":{"landing":"landing.main"}}""";

    @TempDir
    Path base;

    private Path file;
    private MachineConfigurationStore store;

    @BeforeEach
    void createStore() {
        store = new MachineConfigurationStore(MachineConfigurationStore.file(base), new TestLocks());
        file = base.resolve(MachineConfigurationStore.FILE_NAME);
    }

    private static ProviderDefaults defaults() {
        return new ProviderDefaults(Map.of(
                Provider.GITHUB, new ProviderDefault(true, Optional.of(ProviderVariant.NATIVE),
                        Optional.of("github")),
                Provider.SONAR, new ProviderDefault(false, Optional.of(ProviderVariant.NATIVE), Optional.empty())));
    }

    @Test
    @DisplayName("an absent document reads no provider at revision 0")
    void absent() {
        var read = store.read();

        assertAll(
                () -> assertTrue(read.providers().providers().isEmpty()),
                () -> assertEquals(0L, read.revision()));
    }

    @Test
    @DisplayName("reads the provider defaults of the document and ignores the members it does not own")
    void read() throws Exception {
        Files.writeString(file, """
                {"format_version":1,"revision":5,"max_build_slots":2,"lsp_idle_timeout_seconds":900,
                 "credentials":{"store":"file"},
                 "providers":{"git":{"available":true,"variant":"cli","credential_key":"git-https"}}}""", UTF_8);

        var read = store.read();
        var git = read.providers().of(Provider.GIT).orElseThrow();

        assertAll(
                () -> assertEquals(5L, read.revision()),
                () -> assertTrue(git.available()),
                () -> assertEquals(ProviderVariant.CLI, git.variant().orElseThrow()),
                () -> assertEquals("git-https", git.credentialKey().orElseThrow()),
                () -> assertFalse(read.providers().available(Provider.GITHUB)));
    }

    @Test
    @DisplayName("a write replaces providers alone, keeps credentials.store and the other members, and bumps the revision")
    void writeProviders() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);

        store.writeProviders(defaults(), 3L);

        var read = store.read();
        var written = Files.readString(file, UTF_8);
        assertAll(
                () -> assertEquals(4L, read.revision()),
                () -> assertEquals(defaults(), read.providers()),
                () -> assertTrue(read.providers().available(Provider.GITHUB)),
                () -> assertFalse(read.providers().available(Provider.SONAR)),
                () -> assertTrue(written.contains("\"credentials\":{\"store\":\"os\"}"), written),
                () -> assertTrue(written.contains("\"harnesses\":{\"default\":\"acme-cli\"}"), written),
                () -> assertTrue(written.contains("\"landing\":\"landing.main\""), written),
                () -> assertTrue(written.contains("\"max_build_slots\":4"), written));
    }

    @Test
    @DisplayName("a change against an older revision is refused with the current value, and nothing is written")
    void conflict() throws Exception {
        Files.writeString(file, DOCUMENT, UTF_8);
        var stale = defaults();

        var conflict = assertThrows(SettingsConflict.class, () -> store.writeProviders(stale, 2L));

        assertAll(
                () -> assertEquals(2L, conflict.expectedRevision()),
                () -> assertEquals(3L, conflict.currentRevision()),
                () -> assertEquals(ProviderDefaults.none(), conflict.currentValue()),
                () -> assertEquals(DOCUMENT, Files.readString(file, UTF_8)));
    }

    @Test
    @DisplayName("an absent document is created with its required members at their schema defaults")
    void create() throws Exception {
        store.writeProviders(defaults(), 0L);

        var written = Files.readString(file, UTF_8);
        var read = store.read();
        assertAll(
                () -> assertEquals(defaults(), read.providers()),
                () -> assertEquals(1L, read.revision()),
                () -> assertTrue(written.contains("\"max_build_slots\":2"), written),
                () -> assertTrue(written.contains("\"lsp_idle_timeout_seconds\":900"), written));
    }

    @Test
    @DisplayName("an empty set of defaults writes no providers member")
    void emptyProviders() throws Exception {
        Files.writeString(file, """
                {"format_version":1,"revision":3,"max_build_slots":2,"lsp_idle_timeout_seconds":900,
                 "providers":{"git":{"available":true}}}""", UTF_8);

        store.writeProviders(ProviderDefaults.none(), 3L);

        var written = Files.readString(file, UTF_8);
        assertAll(
                () -> assertTrue(store.read().providers().providers().isEmpty()),
                () -> assertFalse(written.contains("\"providers\""), written));
    }

    @Test
    @DisplayName("a cli variant is refused for a provider without one")
    void cliVariantOfForgejo() {
        var cliForgejo = new ProviderDefault(true, Optional.of(ProviderVariant.CLI), Optional.empty());
        var cli = Map.of(Provider.FORGEJO, cliForgejo);

        var refused = assertThrows(IllegalArgumentException.class, () -> new ProviderDefaults(cli));

        assertTrue(refused.getMessage().contains("forgejo"));
    }

    @Test
    @DisplayName("a document in another format version or with an unknown provider is refused naming the file")
    void refusedDocuments() throws Exception {
        var version = base.resolve("other.json");
        Files.writeString(version, "{\"format_version\":2,\"revision\":0}", UTF_8);
        Files.writeString(file, "{\"format_version\":1,\"revision\":0,\"max_build_slots\":2,"
                + "\"lsp_idle_timeout_seconds\":900,\"providers\":{\"mystery\":{\"available\":true}}}", UTF_8);
        var versionBytes = Files.readAllBytes(version);

        var outdated = assertThrows(StoreRefusal.class,
                () -> MachineConfigurationCodec.read(version, versionBytes));
        var unknownProvider = assertThrows(StoreRefusal.class, store::read);

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, outdated.outcome()),
                () -> assertEquals(file, unknownProvider.file()));
    }

    @Test
    @DisplayName("the store is guarded by the machine configuration lock only")
    void lockKey() {
        var wrong = new AtomicStoreFile(file, LockKey.consents(base));
        var wrongLocks = new TestLocks();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> new MachineConfigurationStore(wrong, wrongLocks));

        assertAll(
                () -> assertTrue(refused.getMessage().contains("machine configuration")),
                () -> assertEquals(LockKey.machineConfiguration(base).lockFile(),
                        MachineConfigurationStore.file(base).lockKey().lockFile()));
    }
}
