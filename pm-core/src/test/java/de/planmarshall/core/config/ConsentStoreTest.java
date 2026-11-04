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
import java.util.Optional;
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
    private static final String SCRIPT = "scripts/" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT)
            + ".sh";
    private static final String OTHER_SCRIPT = "scripts/"
            + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT) + ".sh";
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
        return Consent.machine(kind, GRANTED, Consent.Channel.CLI, Optional.empty());
    }

    @Test
    @DisplayName("an absent document reads no entry at revision 0")
    void absentDocument() {
        var read = store.read();

        assertAll(
                () -> assertTrue(read.consents().isEmpty()),
                () -> assertEquals(0L, read.revision()),
                () -> assertFalse(read.isRemembered(ConsentKind.SCAN_HARNESSES, Optional.empty())));
    }

    @Test
    @DisplayName("grants a machine kind, increments the revision, and reads it remembered without a project")
    void grantMachineKind() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), store.read().revision());

        var read = store.read();
        assertAll(
                () -> assertEquals(1, read.consents().size()),
                () -> assertEquals(1L, read.revision()),
                () -> assertTrue(read.isRemembered(ConsentKind.SCAN_HARNESSES, Optional.empty())),
                () -> assertTrue(read.isRemembered(ConsentKind.SCAN_HARNESSES, Optional.of(ANY_PROJECT))),
                () -> assertEquals(ConsentKind.SCAN_HARNESSES, read.consents().getFirst().kind()),
                () -> assertEquals(Consent.Channel.CLI, read.consents().getFirst().channel()));
    }

    @Test
    @DisplayName("a consent covers its kind and never a wider one")
    void aConsentCoversItsKindOnly() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);

        var read = store.read();
        assertAll(
                () -> assertTrue(read.isRemembered(ConsentKind.SCAN_HARNESSES, Optional.empty())),
                () -> assertFalse(read.isRemembered(ConsentKind.PROBE_HARNESS_LOGIN, Optional.empty())),
                () -> assertFalse(read.isRemembered(ConsentKind.PROBE_HARNESS_MODELS, Optional.empty())));
    }

    @Test
    @DisplayName("a project kind is remembered for its project only")
    void projectScope() {
        store.grant(Consent.project(ConsentKind.INSPECT_CHECKOUT, PROJECT, GRANTED, Consent.Channel.CLI,
                Optional.empty()), 0L);

        var read = store.read();
        assertAll(
                () -> assertTrue(read.isRemembered(ConsentKind.INSPECT_CHECKOUT, Optional.of(PROJECT))),
                () -> assertFalse(read.isRemembered(ConsentKind.INSPECT_CHECKOUT, Optional.of(OTHER_PROJECT))),
                () -> assertFalse(read.isRemembered(ConsentKind.INSPECT_CHECKOUT, Optional.empty())),
                () -> assertEquals(1, read.ofProject(PROJECT).size()),
                () -> assertTrue(read.ofProject(OTHER_PROJECT).isEmpty()));
    }

    @Test
    @DisplayName("run_checkout_scripts is remembered per script digest, and asks again for a changed one")
    void checkoutScripts() {
        var granted = new ConsentScript(SCRIPT, SHA);
        store.grant(Consent.checkoutScripts(PROJECT, List.of(granted), GRANTED, Consent.Channel.WEB,
                Optional.of(DEVICE)), 0L);

        var read = store.read();
        assertAll(
                () -> assertTrue(read.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, Optional.of(PROJECT),
                        List.of(granted))),
                () -> assertFalse(read.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, Optional.of(PROJECT),
                        List.of(new ConsentScript(SCRIPT, OTHER_SHA)))),
                () -> assertFalse(read.isRemembered(ConsentKind.RUN_CHECKOUT_SCRIPTS, Optional.of(PROJECT),
                        List.of(new ConsentScript(OTHER_SCRIPT, SHA)))));
    }

    @Test
    @DisplayName("granting the same (kind, project) again replaces the entry instead of adding one")
    void replaceEntry() {
        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, GRANTED, Consent.Channel.CLI,
                Optional.empty()), 0L);
        var replacedAt = GRANTED.plusSeconds(60);

        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, replacedAt, Consent.Channel.WEB,
                Optional.empty()), store.read().revision());

        var read = store.read();
        assertAll(
                () -> assertEquals(1, read.consents().size()),
                () -> assertEquals(replacedAt, read.consents().getFirst().grantedAt()),
                () -> assertEquals(Consent.Channel.WEB, read.consents().getFirst().channel()),
                () -> assertTrue(Files.readString(file, UTF_8).contains("\"revision\":2")));
    }

    @Test
    @DisplayName("a write against an older revision is refused with conflict and the entries it holds")
    void staleRevision() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);

        var conflicting = machine(ConsentKind.FETCH_ICONS);
        var conflict = assertThrows(SettingsConflict.class, () -> store.grant(conflicting, 0L));

        assertAll(
                () -> assertEquals(SettingsConflict.ERROR_CODE, conflict.code()),
                () -> assertEquals(SettingsConflict.HTTP_STATUS, conflict.status()),
                () -> assertEquals(409, conflict.status()),
                () -> assertEquals(0L, conflict.expectedRevision()),
                () -> assertEquals(1L, conflict.currentRevision()),
                () -> assertInstanceOf(ConsentStore.Snapshot.class, conflict.currentValue()),
                () -> assertEquals(List.of(ConsentKind.SCAN_HARNESSES),
                        ((ConsentStore.Snapshot) conflict.currentValue()).consents().stream()
                                .map(Consent::kind).toList()),
                () -> assertEquals(1, store.read().consents().size()));
    }

    @Test
    @DisplayName("withdraws the entry of a (kind, project) and records nothing else")
    void withdraw() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);
        var revision = store.read().revision();

        var withdrawn = store.withdraw(ConsentKind.SCAN_HARNESSES, Optional.empty(), revision);

        assertAll(
                () -> assertTrue(withdrawn),
                () -> assertTrue(store.read().consents().isEmpty()),
                () -> assertEquals(2L, store.read().revision()),
                () -> assertFalse(store.read().isRemembered(ConsentKind.SCAN_HARNESSES, Optional.empty())));
    }

    @Test
    @DisplayName("withdrawing an entry that is not recorded writes nothing")
    void withdrawAbsent() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);
        var revision = store.read().revision();

        var withdrawn = store.withdraw(ConsentKind.FETCH_ICONS, Optional.empty(), revision);

        assertAll(
                () -> assertFalse(withdrawn),
                () -> assertEquals(revision, store.read().revision()),
                () -> assertEquals(1, store.read().consents().size()));
    }

    @Test
    @DisplayName("drops every entry of a project and leaves the machine's")
    void removeProject() {
        store.grant(machine(ConsentKind.SCAN_HARNESSES), 0L);
        store.grant(Consent.project(ConsentKind.INSPECT_CHECKOUT, PROJECT, GRANTED, Consent.Channel.CLI,
                Optional.empty()), store.read().revision());
        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, GRANTED, Consent.Channel.CLI,
                Optional.empty()), store.read().revision());

        var removed = store.removeProject(PROJECT, store.read().revision());

        assertAll(
                () -> assertEquals(2, removed),
                () -> assertEquals(1, store.read().consents().size()),
                () -> assertEquals(ConsentKind.SCAN_HARNESSES, store.read().consents().getFirst().kind()));
    }

    @Test
    @DisplayName("a kind that is never remembered cannot be granted")
    void neverRemembered() {
        var harnessProfileRefused = assertThrows(IllegalArgumentException.class,
                () -> Consent.machine(ConsentKind.VERIFY_HARNESS_PROFILE, GRANTED, Consent.Channel.CLI,
                        Optional.empty()));
        var autoRunRefused = assertThrows(IllegalArgumentException.class,
                () -> Consent.machine(ConsentKind.AUTO_RUN, GRANTED, Consent.Channel.CLI, Optional.empty()));

        assertAll(
                () -> assertTrue(harnessProfileRefused.getMessage().contains("verify_harness_profile")),
                () -> assertTrue(autoRunRefused.getMessage().contains("auto_run")),
                () -> assertFalse(Files.exists(file)));
    }

    @Test
    @DisplayName("a failing audit sink aborts the change, so no consent is committed without its record")
    void failingAuditSink() {
        var failing = new ConsentStore(ConsentStore.file(base), locks, event -> {
            throw new UncheckedIOException("audit sink refuses the record",
                    new IOException("unavailable"));
        });
        var consent = machine(ConsentKind.SCAN_HARNESSES);

        var refused = assertThrows(UncheckedIOException.class, () -> failing.grant(consent, 0L));

        assertAll(
                () -> assertTrue(refused.getMessage().contains("audit sink")),
                () -> assertTrue(store.read().consents().isEmpty()),
                () -> assertEquals(0L, store.read().revision()),
                () -> assertFalse(Files.exists(file)));
    }

    @Test
    @DisplayName("a remembered and a withdrawn consent append their operator records")
    void audit() {
        store.grant(Consent.project(ConsentKind.MODEL_COST, PROJECT, GRANTED, Consent.Channel.CLI,
                Optional.empty()), 0L);
        store.withdraw(ConsentKind.MODEL_COST, Optional.of(PROJECT), store.read().revision());

        assertAll(
                () -> assertEquals(List.of("consent_remembered", "consent_withdrawn"),
                        events.stream().map(AuditEvent::event).toList()),
                () -> assertEquals(PROJECT,
                        events.getFirst().details().get(AuditEvent.DETAIL_PROJECT_ID)),
                () -> assertEquals("model_cost",
                        events.getFirst().details().get(AuditEvent.DETAIL_KIND)));
    }

    @Test
    @DisplayName("a document that is not the store's shape is refused naming the file")
    void unreadable() throws Exception {
        Files.writeString(file, "{\"format_version\":1,\"revision\":1,\"consents\":[{\"kind\":\"mystery\"}]}", UTF_8);

        var refusal = assertThrows(StoreRefusal.class, store::read);
        assertEquals(file, refusal.file());
    }

    @Test
    @DisplayName("the store is guarded by the machine's consents lock only")
    void lockKey() {
        var wrong = new AtomicStoreFile(file, LockKey.machineConfiguration(base));

        var wrongLock = assertThrows(IllegalArgumentException.class,
                () -> new ConsentStore(wrong, locks, events::add));

        assertAll(
                () -> assertTrue(wrongLock.getMessage().contains("consents")),
                () -> assertEquals(LockKey.consents(base).lockFile(),
                        ConsentStore.file(base).lockKey().lockFile()));
    }
}
