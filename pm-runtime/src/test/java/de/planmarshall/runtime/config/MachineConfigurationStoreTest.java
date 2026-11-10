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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
 * <p>
 * The member names, the format version, the provider, variant and credential-store wire names and the schema defaults
 * are the specified document format. The revisions, the limits, the credential keys and the free text of the members
 * the store copies through are generated; the free text keeps a dash, a dot and a space, because it goes through a
 * parse and a write of the document.
 */
@EnableGeneratorController
@DisplayName("Machine configuration store")
class MachineConfigurationStoreTest {

    @TempDir
    Path base;

    private Path file;
    private MachineConfigurationStore store;

    private final long revision = Generators.longs(1, 1_000_000).next();
    private final int maxBuildSlots = Generators.integers(1, 16).next();
    private final int lspIdleTimeout = Generators.integers(60, 86_400).next();
    private final String harness = freeText();
    private final String landing = freeText();
    private final String githubKey = credentialKey();
    private final Instant verifiedAt = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());

    @BeforeEach
    void createStore() {
        store = new MachineConfigurationStore(MachineConfigurationStore.file(base), new TestLocks());
        file = base.resolve(MachineConfigurationStore.FILE_NAME);
    }

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated credential key of the grammar of {@link ProviderDefault}, with an inner dash */
    private static String credentialKey() {
        return lower() + "-" + lower();
    }

    /** @return a generated free text with a dash, a dot and a space, none of which JSON escapes */
    private static String freeText() {
        return lower() + "-" + lower() + " " + lower() + "." + lower();
    }

    /** @return the operator's document at the generated revision, with members the store does not own */
    private String document() {
        return """
                {"format_version":1,"revision":%d,"max_build_slots":%d,"lsp_idle_timeout_seconds":%d,
                 "credentials":{"store":"os"},
                 "harnesses":{"default":"%s"},
                 "plan_defaults":{"landing":"%s"}}""".formatted(revision, maxBuildSlots, lspIdleTimeout, harness,
                landing);
    }

    private ProviderDefaults defaults() {
        return new ProviderDefaults(Map.of(
                Provider.GITHUB, ProviderDefault.verified(result(), ProviderVariant.NATIVE)
                        .withCredentialKey(githubKey),
                Provider.SONAR, ProviderDefault.unavailable(null)));
    }

    private VerificationResult result() {
        return new VerificationResult(true, verifiedAt,
                List.of(new VerificationResult.Check("origin", true)));
    }

    @Test
    @DisplayName("an absent document reads no provider at revision 0")
    void absent() {
        var read = store.read();

        assertAll(
                () -> assertTrue(read.providers().providers().isEmpty(), "an absent document holds no provider"),
                () -> assertEquals(0L, read.revision(), "an absent document is at revision 0"));
    }

    @Test
    @DisplayName("reads the provider defaults of the document and ignores the members it does not own")
    void read() throws Exception {
        var gitKey = credentialKey();
        Files.writeString(file, """
                {"format_version":1,"revision":%d,"max_build_slots":%d,"lsp_idle_timeout_seconds":%d,
                 "credentials":{"store":"file"},
                 "providers":{"git":{"available":true,"variant":"cli","credential_key":"%s",
                  "verification":{"passed":true,"at":"2026-10-29T10:00:00Z",
                   "checks":[{"check":"origin","ok":true}]}}}}"""
                .formatted(revision, maxBuildSlots, lspIdleTimeout, gitKey), UTF_8);

        var read = store.read();
        var git = read.providers().of(Provider.GIT).orElseThrow();

        assertAll(
                () -> assertEquals(revision, read.revision(), "the revision of the document must be read"),
                () -> assertTrue(git.available(), "the git provider must be available"),
                () -> assertEquals(ProviderVariant.CLI, git.variant(),
                        "the variant of git must be read"),
                () -> assertEquals(gitKey, git.credentialKey(),
                        "the credential key of git must be read"),
                () -> assertEquals(List.of(new VerificationResult.Check("origin", true)),
                        git.verification().checks(), "the verification's checks must be read"),
                () -> assertFalse(read.providers().available(Provider.GITHUB),
                        "an unnamed provider is not available"));
    }

    @Test
    @DisplayName("a write replaces providers alone, keeps credentials.store and the other members, and bumps the revision")
    void writeProviders() throws Exception {
        Files.writeString(file, document(), UTF_8);

        store.writeProviders(defaults(), revision);

        var read = store.read();
        var written = Files.readString(file, UTF_8);
        assertAll(
                () -> assertEquals(revision + 1, read.revision(), "the write must bump the revision by one"),
                () -> assertEquals(defaults(), read.providers(), "the written defaults must be read back"),
                () -> assertTrue(read.providers().available(Provider.GITHUB), "github must be available"),
                () -> assertFalse(read.providers().available(Provider.SONAR), "sonar must not be available"),
                () -> assertTrue(written.contains("\"credentials\":{\"store\":\"os\"}"),
                        "the credential store must be kept"),
                () -> assertTrue(written.contains("\"harnesses\":{\"default\":\"" + harness + "\"}"),
                        "the harness default must be kept verbatim"),
                () -> assertTrue(written.contains("\"landing\":\"" + landing + "\""),
                        "the plan default must be kept verbatim"),
                () -> assertTrue(written.contains("\"max_build_slots\":" + maxBuildSlots + ","),
                        "the build slot limit must be kept"));
    }

    @Test
    @DisplayName("a change against an older revision is refused with the current value, and nothing is written")
    void conflict() throws Exception {
        var document = document();
        Files.writeString(file, document, UTF_8);
        var stale = defaults();
        long staleRevision = Generators.longs(0, revision - 1).next();

        var conflict = assertThrows(SettingsConflict.class, () -> store.writeProviders(stale, staleRevision),
                "a write against an older revision is refused");

        assertAll(
                () -> assertEquals(staleRevision, conflict.expectedRevision(),
                        "the conflict must name the stale revision"),
                () -> assertEquals(revision, conflict.currentRevision(),
                        "the conflict must name the current revision"),
                () -> assertEquals(ProviderDefaults.none(), conflict.currentValue(),
                        "the conflict must carry the current value"),
                () -> assertEquals(document, Files.readString(file, UTF_8),
                        "a refused write must leave the document"));
    }

    @Test
    @DisplayName("an absent document is created with its required members at their schema defaults")
    void create() throws Exception {
        store.writeProviders(defaults(), 0L);

        var written = Files.readString(file, UTF_8);
        var read = store.read();
        assertAll(
                () -> assertEquals(defaults(), read.providers(), "the written defaults must be read back"),
                () -> assertEquals(1L, read.revision(), "a created document is at revision 1"),
                () -> assertTrue(written.contains("\"max_build_slots\":"
                                + MachineConfigurationCodec.DEFAULT_MAX_BUILD_SLOTS),
                        "the build slot limit takes its schema default"),
                () -> assertTrue(written.contains("\"lsp_idle_timeout_seconds\":"
                                + MachineConfigurationCodec.DEFAULT_LSP_IDLE_TIMEOUT_SECONDS),
                        "the LSP idle timeout takes its schema default"));
    }

    @Test
    @DisplayName("an empty set of defaults writes no providers member")
    void emptyProviders() throws Exception {
        Files.writeString(file, """
                {"format_version":1,"revision":%d,"max_build_slots":%d,"lsp_idle_timeout_seconds":%d,
                 "providers":{"git":{"available":true,"variant":"native",
                  "verification":{"passed":true,"at":"2026-10-29T10:00:00Z",
                   "checks":[{"check":"origin","ok":true}]}}}}""".formatted(revision, maxBuildSlots,
                lspIdleTimeout),
                UTF_8);

        store.writeProviders(ProviderDefaults.none(), revision);

        var written = Files.readString(file, UTF_8);
        assertAll(
                () -> assertTrue(store.read().providers().providers().isEmpty(),
                        "no provider may be read after the write"),
                () -> assertFalse(written.contains("\"providers\""), "no providers member may be written"));
    }

    @Test
    @DisplayName("a cli variant is refused for a provider without one")
    void cliVariantOfForgejo() {
        var cliForgejo = ProviderDefault.verified(result(), ProviderVariant.CLI);
        var cli = Map.of(Provider.FORGEJO, cliForgejo);

        var refused = assertThrows(IllegalArgumentException.class, () -> new ProviderDefaults(cli),
                "a cli variant of forgejo must be refused");

        // Contract literal: the wire name of the provider the refusal names
        assertTrue(refused.getMessage().contains("forgejo"), "the refusal must name the provider");
    }

    @Test
    @DisplayName("a document in another format version or with an unknown provider is refused naming the file")
    void refusedDocuments() throws Exception {
        // A fixture path beside the store; format version 2 is the next, unsupported version
        var version = base.resolve("other.json");
        Files.writeString(version, "{\"format_version\":2,\"revision\":%d}".formatted(revision), UTF_8);
        // Longer than every provider wire name, so it names none of them
        var unknown = Generators.letterStrings(13, 20).next().toLowerCase(Locale.ROOT);
        Files.writeString(file, ("{\"format_version\":1,\"revision\":%d,\"max_build_slots\":%d,"
                + "\"lsp_idle_timeout_seconds\":%d,\"providers\":{\"%s\":{\"available\":true}}}")
                .formatted(revision, maxBuildSlots, lspIdleTimeout, unknown), UTF_8);
        var versionBytes = Files.readAllBytes(version);

        var outdated = assertThrows(StoreRefusal.class,
                () -> MachineConfigurationCodec.read(version, versionBytes),
                "a newer format version must be refused");
        var unknownProvider = assertThrows(StoreRefusal.class, store::read,
                "an unknown provider must be refused");

        assertAll(
                () -> assertEquals(StoreRefusal.Outcome.FORMAT_NEWER, outdated.outcome(),
                        "the refusal must name a newer format"),
                () -> assertEquals(file, unknownProvider.file(), "the refusal must name the file"));
    }

    @Test
    @DisplayName("the store file must be machine-config.json below the base of its lock key")
    void foreignPath() {
        var foreign = base.resolve("state").resolve(MachineConfigurationStore.FILE_NAME);
        var storeFile = new AtomicStoreFile(foreign, LockKey.machineConfiguration(base));

        var refused = assertThrows(IllegalArgumentException.class,
                () -> new MachineConfigurationStore(storeFile, new TestLocks()),
                "a store file outside the base of its lock key is refused");

        assertAll(
                () -> assertTrue(refused.getMessage().contains(MachineConfigurationStore.FILE_NAME),
                        "the refusal must name the expected file"),
                () -> assertEquals(MachineConfigurationStore.FILE_NAME,
                        MachineConfigurationStore.file(base).store().getFileName().toString(),
                        "the store file is machine-config.json below the base"));
    }

    @Test
    @DisplayName("an omitted variant is refused for a provider with a cli variant")
    void variantOmitted() {
        var github = ProviderDefault.verified(result(), null);
        var git = ProviderDefault.verified(result(), null);

        var refusedGithub = assertThrows(IllegalArgumentException.class,
                () -> new ProviderDefaults(Map.of(Provider.GITHUB, github)),
                "github records a cli variant and must name one");
        var refusedGit = assertThrows(IllegalArgumentException.class,
                () -> new ProviderDefaults(Map.of(Provider.GIT, git)),
                "git records a cli variant and must name one");

        assertAll(
                () -> assertTrue(refusedGithub.getMessage().contains("github"),
                        "the refusal must name the provider"),
                () -> assertTrue(refusedGit.getMessage().contains("git"),
                        "the refusal must name the provider"));
    }

    @Test
    @DisplayName("the verification result is written, read back and the other members kept")
    void verificationRoundTrip() throws Exception {
        Files.writeString(file, document(), UTF_8);
        var result = new VerificationResult(true, verifiedAt, List.of(
                new VerificationResult.Check("origin", true),
                new VerificationResult.Check("whoami", false)));
        var written = new ProviderDefaults(Map.of(Provider.GITHUB,
                ProviderDefault.verified(result, ProviderVariant.NATIVE).withCredentialKey(githubKey)));

        store.writeProviders(written, revision);

        var read = store.read().providers().of(Provider.GITHUB).orElseThrow();
        var text = Files.readString(file, UTF_8);
        assertAll(
                () -> assertEquals(result, read.verification(), "the verification result must round-trip"),
                () -> assertTrue(text.contains("\"credentials\":{\"store\":\"os\"}"),
                        "the credential store must be kept"));
    }

    @Test
    @DisplayName("an available provider without a verification result is refused")
    void unverifiedAvailableRefused() {
        var refused = assertThrows(IllegalArgumentException.class,
                () -> new ProviderDefaults(Map.of(Provider.GITHUB,
                        new ProviderDefault(true, ProviderVariant.NATIVE, null, null))),
                "an available provider without a verification result is refused");

        assertTrue(refused.getMessage().contains("verification"), "the refusal must name the verification");
    }

    @Test
    @DisplayName("a document that records a provider available without a verification result is refused")
    void documentWithoutVerification() throws Exception {
        Files.writeString(file, """
                {"format_version":1,"revision":%d,"max_build_slots":%d,"lsp_idle_timeout_seconds":%d,
                 "providers":{"git":{"available":true,"variant":"native"}}}""".formatted(revision, maxBuildSlots,
                lspIdleTimeout), UTF_8);

        var refused = assertThrows(StoreRefusal.class, store::read,
                "a document without a verification result for an available provider is refused");

        assertAll(
                () -> assertEquals(file, refused.file(), "the refusal must name the file"),
                () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.outcome(),
                        "the refusal must read as unreadable"));
    }

    @Test
    @DisplayName("the store is guarded by the machine configuration lock only")
    void lockKey() {
        var wrong = new AtomicStoreFile(file, LockKey.consents(base));
        var wrongLocks = new TestLocks();

        var refused = assertThrows(IllegalArgumentException.class,
                () -> new MachineConfigurationStore(wrong, wrongLocks), "a store under another lock is refused");

        assertAll(
                () -> assertTrue(refused.getMessage().contains("machine configuration"),
                        "the refusal must name the expected lock"),
                () -> assertEquals(LockKey.machineConfiguration(base).lockFile(),
                        MachineConfigurationStore.file(base).lockKey().lockFile(),
                        "the store file uses the configuration lock"));
    }
}
