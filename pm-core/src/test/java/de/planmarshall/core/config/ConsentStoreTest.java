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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The consent store {@code consents.json} (PM-SEC-8, Store Schemas § Consent Before Acting): the closed list of kinds,
 * their scope, the digests of {@code run_checkout_scripts}, the revision of the document, and the withdrawal that leaves
 * nothing recorded.
 */
@EnableGeneratorController
@DisplayName("Consent store")
class ConsentStoreTest {

    private static final Instant GRANTED = Instant.ofEpochSecond(
            Generators.longs(1_600_000_000L, 1_900_000_000L).next());
    private static final String SHA = "%064x".formatted(Generators.longs().next());
    private static final String OTHER_SHA = "%064x".formatted(Generators.longs().next());
    private static final String PROJECT = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String OTHER_PROJECT = "o" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String ANY_PROJECT = "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String SCRIPT_BASE = Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT);
    private static final String SCRIPT = "scripts/" + SCRIPT_BASE + ".sh";
    private static final String OTHER_SCRIPT = "scripts/" + SCRIPT_BASE + "-other.sh";
    private static final String DEVICE = "dev-"
            + "%012x".formatted(Generators.longs(0L, 0xFFFF_FFFF_FFFFL).next());

    @TempDir
    Path base;

    private Path file;
    private TestLocks locks;
    private final List<AuditEvent> events = new ArrayList<>();
    private ConsentStore store;

    @BeforeEach
    void createStore() {
        locks = new TestLocks();
        store = new ConsentStore(ConsentStore.file(base), locks, events::add);
        file = base.resolve("consents.json");
    }

    private static Consent machine(ConsentKind kind) {
        return Consent.machine(kind, GRANTED, Consent.Channel.CLI, null);
    }

    @Test
    @DisplayName("an absent document reads no entry at revision 0")
    void absentDocument() {
        var read = store.read();

        assertAll(
                () -> assertTrue(read.consents().isEmpty(), "an absent document holds no consent"),
                () -> assertEquals(0L, read.revision(), "an absent document is at revision 0"),
                () -> assertFalse(read.isRemembered(ConsentKind.SCAN_HARNESSES, null),
                        "nothing is remembered yet"));
    }

    @Test
    @DisplayName("grants a machine kind, increments the revision, and reads it remembered without a project")
    void grantMachineKind() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), store.read().revision());

        var read = store.read();
        assertAll(
                () -> assertEquals(1, read.consents().size(), "one consent entry is stored"),
                () -> assertEquals(1L, read.revision(), "the grant bumps the revision to 1"),
                () -> assertTrue(read.isRemembered(ConsentKind.SCAN_HARNESSES, null),
                        "the machine kind is remembered"),
                () -> assertTrue(read.isRemembered(ConsentKind.SCAN_HARNESSES, ANY_PROJECT),
                        "a machine kind needs no project"),
                () -> assertEquals(ConsentKind.SCAN_HARNESSES, read.consents().getFirst().kind(),
                        "the stored entry keeps the kind"),
                () -> assertEquals(Consent.Channel.CLI, read.consents().getFirst().channel(),
                        "the stored entry keeps the channel"));
    }

    @Test
    @DisplayName("a consent covers its kind and never a wider one")
    void aConsentCoversItsKindOnly() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);

        var read = store.read();
        assertAll(
                () -> assertTrue(read.isRemembered(ConsentKind.SCAN_HARNESSES, null),
                        "the granted kind is remembered"),
                () -> assertFalse(read.isRemembered(ConsentKind.PROBE_HARNESS_LOGIN, null),
                        "probe_harness_login is not covered"),
                () -> assertFalse(read.isRemembered(ConsentKind.PROBE_HARNESS_MODELS, null),
                        "probe_harness_models is not covered"));
    }

    @Test
    @DisplayName("a project kind is remembered for its project only")
    void projectScope() {
        store.grant(Consent.project(ConsentKind.INSPECT_CHECKOUT, PROJECT, GRANTED, Consent.Channel.CLI,
                null), 0L);

        var read = store.read();
        assertAll(
                () -> assertTrue(read.isRemembered(ConsentKind.INSPECT_CHECKOUT, PROJECT),
                        "the project kind is remembered here"),
                () -> assertFalse(read.isRemembered(ConsentKind.INSPECT_CHECKOUT, OTHER_PROJECT),
                        "another project is not covered"),
                () -> assertFalse(read.isRemembered(ConsentKind.INSPECT_CHECKOUT, null),
                        "a project kind is not machine-wide"),
                () -> assertEquals(1, read.ofProject(PROJECT).size(), "the project holds one consent entry"),
                () -> assertTrue(read.ofProject(OTHER_PROJECT).isEmpty(), "another project holds no entry"));
    }

    @Test
    @DisplayName("run_checkout_scripts is remembered per script digest, and asks again for a changed one")
    void checkoutScripts() {
        var granted = new ConsentScript(SCRIPT, SHA);
        store.grant(Consent.checkoutScripts(PROJECT, List.of(granted), GRANTED, Consent.Channel.WEB,
                DEVICE), 0L);

        var read = store.read();
        assertAll(
                () -> assertTrue(read.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, PROJECT,
                        List.of(granted)), "the granted script digest runs"),
                () -> assertFalse(read.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, PROJECT,
                        List.of(new ConsentScript(SCRIPT, OTHER_SHA))), "a changed digest asks again"),
                () -> assertFalse(read.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, PROJECT,
                        List.of(new ConsentScript(OTHER_SCRIPT, SHA))), "another script asks again"));
    }

    @Test
    @DisplayName("granting the same (kind, project) again replaces the entry instead of adding one")
    void replaceEntry() {
        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, GRANTED, Consent.Channel.CLI,
                null), 0L);
        var replacedAt = GRANTED.plusSeconds(60);

        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, replacedAt, Consent.Channel.WEB,
                null), store.read().revision());

        var read = store.read();
        assertAll(
                () -> assertEquals(1, read.consents().size(), "the entry is replaced, not added"),
                () -> assertEquals(replacedAt, read.consents().getFirst().grantedAt(),
                        "the newer grant time replaces the old"),
                () -> assertEquals(Consent.Channel.WEB, read.consents().getFirst().channel(),
                        "the newer channel replaces the old"),
                () -> assertTrue(Files.readString(file, UTF_8).contains("\"revision\":2"),
                        "the second write records revision 2"));
    }

    @Test
    @DisplayName("a write against an older revision is refused with conflict and the entries it holds")
    void staleRevision() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);

        var conflicting = machine(ConsentKind.FETCH_ICONS);
        var conflict = assertThrows(SettingsConflict.class, () -> store.grant(conflicting, 0L),
                "a stale revision is refused");

        assertAll(
                () -> assertEquals(SettingsConflict.ERROR_CODE, conflict.code(),
                        "the refusal keeps the conflict code"),
                () -> assertEquals(SettingsConflict.HTTP_STATUS, conflict.status(),
                        "the refusal keeps the conflict status"),
                () -> assertEquals(409, conflict.status(), "the conflict status is 409"),
                () -> assertEquals(0L, conflict.expectedRevision(), "the refusal names the expected revision"),
                () -> assertEquals(1L, conflict.currentRevision(), "the refusal names the current revision"),
                () -> assertInstanceOf(ConsentStore.Snapshot.class, conflict.currentValue(),
                        "the refusal carries the current snapshot"),
                () -> assertEquals(List.of(ConsentKind.SCAN_HARNESSES),
                        ((ConsentStore.Snapshot) conflict.currentValue()).consents().stream()
                                .map(Consent::kind).toList(), "the snapshot carries the stored kind"),
                () -> assertEquals(1, store.read().consents().size(), "the store keeps its one entry"));
    }

    @Test
    @DisplayName("withdraws the entry of a (kind, project) and records nothing else")
    void withdraw() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);
        var revision = store.read().revision();

        var withdrawn = store.withdraw(ConsentKind.SCAN_HARNESSES, null, revision);

        assertAll(
                () -> assertTrue(withdrawn, "the existing entry is withdrawn"),
                () -> assertTrue(store.read().consents().isEmpty(), "no consent remains after withdrawal"),
                () -> assertEquals(2L, store.read().revision(), "the withdrawal bumps the revision"),
                () -> assertFalse(store.read().isRemembered(ConsentKind.SCAN_HARNESSES, null),
                        "the withdrawn kind is forgotten"));
    }

    @Test
    @DisplayName("withdrawing an entry that is not recorded writes nothing")
    void withdrawAbsent() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);
        var revision = store.read().revision();

        var withdrawn = store.withdraw(ConsentKind.FETCH_ICONS, null, revision);

        assertAll(
                () -> assertFalse(withdrawn, "an absent entry is not withdrawn"),
                () -> assertEquals(revision, store.read().revision(), "a missed withdrawal keeps the revision"),
                () -> assertEquals(1, store.read().consents().size(), "the untouched entry remains"));
    }

    @Test
    @DisplayName("drops every entry of a project and leaves the machine's")
    void removeProject() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);
        store.grant(Consent.project(ConsentKind.INSPECT_CHECKOUT, PROJECT, GRANTED, Consent.Channel.CLI,
                null), store.read().revision());
        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, GRANTED, Consent.Channel.CLI,
                null), store.read().revision());

        var removed = store.removeProject(PROJECT, store.read().revision());

        assertAll(
                () -> assertEquals(2, removed, "both project consents are removed"),
                () -> assertEquals(1, store.read().consents().size(), "the machine consent remains"),
                () -> assertEquals(ConsentKind.SCAN_HARNESSES, store.read().consents().getFirst().kind(),
                        "the machine kind is the survivor"));
    }

    @Test
    @DisplayName("a kind that is never remembered cannot be granted")
    void neverRemembered() {
        var harnessProfileRefused = assertThrows(IllegalArgumentException.class,
                () -> Consent.machine(ConsentKind.VERIFY_HARNESS_PROFILE, GRANTED, Consent.Channel.CLI,
                        null),
                "verify_harness_profile cannot be granted");
        var autoRunRefused = assertThrows(IllegalArgumentException.class,
                () -> Consent.machine(ConsentKind.AUTO_RUN, GRANTED, Consent.Channel.CLI, null),
                "auto_run cannot be granted");

        assertAll(
                () -> assertTrue(harnessProfileRefused.getMessage().contains("verify_harness_profile"),
                        "the refusal names verify_harness_profile"),
                () -> assertTrue(autoRunRefused.getMessage().contains("auto_run"), "the refusal names auto_run"),
                () -> assertFalse(Files.exists(file), "a refused grant writes no document"));
    }

    @Test
    @DisplayName("a failing audit sink aborts the change, so no consent is committed without its record")
    void failingAuditSink() {
        var failing = new ConsentStore(ConsentStore.file(base), locks, event -> {
            throw new UncheckedIOException("audit sink refuses the record",
                    new IOException("unavailable"));
        });
        var consent = machine(ConsentKind.SCAN_HARNESSES);

        var refused = assertThrows(UncheckedIOException.class, () -> failing.grant(consent, 0L),
                "a failing audit sink aborts the grant");

        assertAll(
                () -> assertTrue(refused.getMessage().contains("audit sink"),
                        "the refusal carries the audit sink reason"),
                () -> assertTrue(store.read().consents().isEmpty(), "no consent is committed"),
                () -> assertEquals(0L, store.read().revision(), "the failed write keeps revision 0"),
                () -> assertFalse(Files.exists(file), "the failed write leaves no file"));
    }

    @Test
    @DisplayName("a remembered and a withdrawn consent append their operator records")
    void audit() {
        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, GRANTED, Consent.Channel.CLI,
                null), 0L);
        store.withdraw(ConsentKind.MODEL_COST, PROJECT, store.read().revision());

        assertAll(
                () -> assertEquals(List.of("consent_remembered", "consent_withdrawn"),
                        events.stream().map(AuditEvent::event).toList(), "both operator records are appended"),
                () -> assertEquals(PROJECT,
                        events.getFirst().details().get(AuditEvent.DETAIL_PROJECT_ID),
                        "the record names the project"),
                () -> assertEquals("model_cost",
                        events.getFirst().details().get(AuditEvent.DETAIL_KIND), "the record names the kind"));
    }

    @Test
    @DisplayName("a document that is not the store's shape is refused naming the file")
    void unreadable() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":1,\"consents\":[{\"kind\":\"mystery\"}]}", UTF_8);

        var refusal = assertThrows(StoreRefusal.class, store::read, "a malformed document is refused");
        assertEquals(file, refusal.file(), "the refusal names the consents file");
    }

    @Test
    @DisplayName("the store is guarded by the machine's consents lock only")
    void lockKey() {
        var wrong = new AtomicStoreFile(file, LockKey.machineConfiguration(base));

        var wrongLock = assertThrows(IllegalArgumentException.class,
                () -> new ConsentStore(wrong, locks, events::add), "a wrong lock key is refused");

        assertAll(
                () -> assertTrue(wrongLock.getMessage().contains("consents"),
                        "the refusal names the consents lock"),
                () -> assertEquals(LockKey.consents(base).lockFile(),
                        ConsentStore.file(base).lockKey().lockFile(), "the store uses the consents lock"));
    }
}
