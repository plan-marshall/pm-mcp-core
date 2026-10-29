/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.credentials;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.config.Consent;
import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.core.config.ConsentStore;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.config.MachineConfigurationStore;
import de.planmarshall.runtime.credentials.CredentialStoreAspect.State;
import de.planmarshall.runtime.credentials.CredentialStoreAspect.Transition;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The engine half of the credential-store aspect (State Catalogue § Credential Store): the states and their order, the
 * consent {@code check_credential_store} before the OS store is checked, the file store checked without one, and a failed
 * move that keeps the old store and reports {@code credential_store_failed}.
 */
@DisplayName("Credential store aspect")
class CredentialStoreAspectTest {

    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    private static final String BUS = "unix:path=/run/user/1/bus";
    private static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @TempDir
    Path base;

    private final MemoryStore secretService = new MemoryStore(SecretStore.SECRET_SERVICE);
    private final List<AuditEvent> events = new ArrayList<>();
    private FileLockManager locks;
    private ConsentStore consents;
    private CredentialStoreChoice choice;
    private CredentialStoreAspect aspect;

    @BeforeEach
    void openRuntime() throws Exception {
        AuditSink sink = events::add;
        locks = new FileLockManager(sink);
        consents = new ConsentStore(ConsentStore.file(base), locks, events::add);
        choice = choice(true);
        Files.createDirectories(base.resolve("state"));
        Files.createDirectories(base.resolve("locks"));
        aspect = new CredentialStoreAspect(base, choice, locks, sink, USER, Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom());
    }

    private CredentialStoreChoice choice(boolean osAvailable) {
        return new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.LINUX, Map.of("DBUS_SESSION_BUS_ADDRESS", BUS),
                new CredentialStoreChoice.Keyrings() {

                    @Override
                    public SecretStore keychain(String service) {
                        throw new SecretStoreException(SecretStoreException.Reason.FAILED, "not on macOS");
                    }

                    @Override
                    public Optional<SecretStore> secretService(Path socket, String service) {
                        return osAvailable ? Optional.of(secretService) : Optional.empty();
                    }
                });
    }

    private ConsentGate gate() {
        return new ConsentGate(consents.read(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("detect offers the OS store where one is available, and the file store always")
    void detect() {
        var detection = aspect.detect();

        assertAll(
                () -> assertEquals(List.of(Store.OS, Store.FILE), detection.offered()),
                () -> assertFalse(detection.moveInterrupted()),
                () -> assertEquals(List.of(Store.FILE),
                        new CredentialStoreAspect(base, choice(false), locks, events::add, USER,
                                Clock.systemUTC(), new SecureRandom()).detect().offered()));
    }

    @Test
    @DisplayName("choose sends an OS store through the consent state and the file store straight to its check")
    void choose() {
        assertAll(
                () -> assertEquals(State.CONSENT, aspect.choose(Store.OS).next()),
                () -> assertEquals(State.CHECK, aspect.choose(Store.FILE).next()),
                () -> assertEquals(State.UNAVAILABLE, new CredentialStoreAspect(base, choice(false), locks, events::add,
                        USER, Clock.systemUTC(), new SecureRandom()).choose(Store.OS).next()));
    }

    @Test
    @DisplayName("no to the consent leaves the scope in the consent state and writes nothing")
    void consentNo() {
        var transition = aspect.consent(gate(), ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty());

        assertAll(
                () -> assertEquals(State.CONSENT, transition.next()),
                () -> assertTrue(transition.lines().isEmpty()),
                () -> assertFalse(Files.exists(base.resolve("machine-config.json"))),
                () -> assertEquals(Optional.empty(), choice.recorded()));
    }

    @Test
    @DisplayName("yes to the consent reaches the check, and yes_remember records the consent")
    void consentYes() {
        var yes = aspect.consent(gate(), ConsentGate.Answer.YES, Consent.Channel.CLI, Optional.empty());
        var remembered = aspect.consent(gate(), ConsentGate.Answer.YES_REMEMBER, Consent.Channel.CLI,
                Optional.empty());

        assertAll(
                () -> assertEquals(State.CHECK, yes.next()),
                () -> assertTrue(yes.toRecord().isEmpty()),
                () -> assertEquals(State.CHECK, remembered.next()),
                () -> assertEquals(Consent.Channel.CLI, remembered.toRecord().orElseThrow().channel()));
    }

    @Test
    @DisplayName("a remembered consent passes the consent state without a question")
    void consentRemembered() {
        consents.grant(Consent.machine(ConsentKind.CHECK_CREDENTIAL_STORE, NOW,
                Consent.Channel.CLI, Optional.empty()), 0L);

        var transition = aspect.consent(gate(), ConsentGate.Answer.NO, Consent.Channel.CLI, Optional.empty());

        assertAll(
                () -> assertEquals(State.CHECK, transition.next()),
                () -> assertTrue(transition.toRecord().isEmpty()));
    }

    @Test
    @DisplayName("the file store is checked without a consent and recorded last")
    void fileStoreCheckAndRecord() {
        var checked = aspect.check(Store.FILE);
        var recorded = aspect.record(Store.FILE);

        assertAll(
                () -> assertEquals(State.RECORD, checked.next()),
                () -> assertEquals(3, checked.lines().size()),
                () -> assertTrue(checked.lines().stream().allMatch(line -> line.outcome()
                        == CredentialStoreCheck.Outcome.OK)),
                () -> assertEquals(State.DONE, recorded.next()),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded()));
    }

    @Test
    @DisplayName("a failed check reaches store.unavailable with the check's own line")
    void checkFailed() {
        secretService.failWith(SecretStoreException.Reason.LOCKED);

        var transition = aspect.check(Store.OS);

        assertAll(
                () -> assertEquals(State.UNAVAILABLE, transition.next()),
                () -> assertTrue(transition.reason().orElseThrow().startsWith("credential_store_check.write.")),
                () -> assertFalse(transition.usable()));
    }

    @Test
    @DisplayName("a passed check with another store recorded reaches store.confirm-move with the entry count")
    void confirmMove() {
        aspect.record(Store.FILE);
        new FileSecretStore(base.resolve("credentials")).put(CredentialAccount.global("github"), "secret");

        var checked = aspect.check(Store.OS);
        var confirmed = aspect.confirmMove();

        assertAll(
                () -> assertEquals(State.CONFIRM_MOVE, checked.next()),
                () -> assertEquals(State.MOVE, confirmed.next()),
                () -> assertEquals(1, confirmed.moveEntries().orElseThrow()));
    }

    @Test
    @DisplayName("a failed move keeps the old store and reports credential_store_failed")
    void moveFailed() {
        aspect.record(Store.FILE);
        secretService.put(CredentialAccount.global("github"), "secret");
        secretService.failWith(SecretStoreException.Reason.FAILED);

        var transition = aspect.move(Store.OS);

        assertAll(
                () -> assertEquals(State.UNAVAILABLE, transition.next()),
                () -> assertEquals(CredentialStoreAspect.CREDENTIAL_STORE_FAILED, transition.reason().orElseThrow()),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded()));
    }

    @Test
    @DisplayName("a move into an empty target switches the store and reaches store.record")
    void move() {
        aspect.record(Store.FILE);

        var transition = aspect.move(Store.OS);

        assertAll(
                () -> assertEquals(State.RECORD, transition.next()),
                () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                () -> assertEquals(State.DONE, aspect.record(Store.OS).next()));
    }

    @Test
    @DisplayName("detect reports a move an earlier run began")
    void interrupted() throws Exception {
        var record = CredentialStoreMoveRecord.file(base);
        Files.createDirectories(record.getParent());
        Files.writeString(record, "{}");

        assertTrue(aspect.detect().moveInterrupted());
    }

    @Test
    @DisplayName("recording the recorded store again changes nothing")
    void recordAgain() {
        aspect.record(Store.FILE);
        var revision = machineConfigurationRevision();

        var transition = aspect.record(Store.FILE);

        assertAll(
                () -> assertEquals(State.DONE, transition.next()),
                () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                () -> assertEquals(revision, machineConfigurationRevision()));
    }

    private long machineConfigurationRevision() {
        return new MachineConfigurationStore(
                MachineConfigurationStore.file(base), locks).read().revision();
    }

    @Test
    @DisplayName("transitions are values: an unavailable store is not usable and carries its reason")
    void transitions() {
        Transition transition = aspect.check(Store.FILE);

        assertAll(
                () -> assertTrue(transition.usable()),
                () -> assertTrue(transition.reason().isEmpty()),
                () -> assertTrue(transition.toRecord().isEmpty()));
    }

    /** A secret store in memory, which a test can make fail. */
    private static final class MemoryStore implements SecretStore {

        private final String name;
        private final Map<String, String> secrets = new TreeMap<>();
        private SecretStoreException.Reason failure;

        MemoryStore(String name) {
            this.name = name;
        }

        void failWith(SecretStoreException.Reason reason) {
            failure = reason;
        }

        private void failIfAsked() {
            if (failure != null) {
                throw new SecretStoreException(failure, "store fails with " + failure.code());
            }
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            failIfAsked();
            secrets.put(account.account(), secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            failIfAsked();
            return Optional.ofNullable(secrets.get(account.account()));
        }

        @Override
        public boolean delete(CredentialAccount account) {
            failIfAsked();
            return secrets.remove(account.account()) != null;
        }

        @Override
        public List<CredentialAccount> entries() {
            failIfAsked();
            return secrets.keySet().stream().map(CredentialAccount::global).toList();
        }
    }
}
