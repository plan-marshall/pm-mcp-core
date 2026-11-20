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
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Refusal;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Writer;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Operation;
import de.planmarshall.runtime.credentials.CredentialStoreMoveRecord.Phase;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.EnrolmentSidecars;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.CredentialStoreMoveResume;

/**
 * The credential store as the operator's recorded choice (PM-CRED-2, PM-CRED-4): {@code credentials.store} of
 * {@code machine-config.json} is read strictly, written only by its two owners under the machine configuration lock,
 * and decides the one store the runtime uses; before the choice no credential is entered, and an unavailable recorded
 * OS store is never replaced by the file store.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("CredentialStoreChoice: the operator's recorded store")
class CredentialStoreChoiceTest {

    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    private static final String BUS = "unix:path=/run/user/1/bus";
    private static final CredentialAccount GITHUB = CredentialAccount.global("github");
    private static final TransactionId TRANSACTION = new TransactionId("018f0000-0000-7000-8000-000000000001");

    @TempDir
    Path base;

    private final List<AuditEvent> events = new ArrayList<>();
    private final List<String> probes = new ArrayList<>();
    private final MemoryStore keychain = new MemoryStore(SecretStore.KEYCHAIN);
    private final MemoryStore secretService = new MemoryStore(SecretStore.SECRET_SERVICE);
    private FileLockManager locks;
    private LockTransaction transaction;
    private String secret;

    @BeforeEach
    void openTransaction() {
        secret = Generators.letterStrings(4, 20).next();
        AuditSink sink = events::add;
        locks = new FileLockManager(sink);
        transaction = locks.openTransaction();
    }

    @AfterEach
    void closeTransaction() {
        transaction.close();
    }

    private CredentialStoreChoice choice(MachinePaths.Os os, Map<String, String> environment, boolean osAvailable) {
        return new CredentialStoreChoice(base, SERVICE, os, environment, keyrings(osAvailable));
    }

    private CredentialStoreChoice linux(boolean busAnswers) {
        return choice(MachinePaths.Os.LINUX, Map.of("DBUS_SESSION_BUS_ADDRESS", BUS), busAnswers);
    }

    private CredentialStoreChoice.Keyrings keyrings(boolean available) {
        return new CredentialStoreChoice.Keyrings() {

            @Override
            public SecretStore keychain(String service) {
                probes.add("keychain:" + service);
                if (!available) {
                    throw new SecretStoreException(SecretStoreException.Reason.FAILED, "no Security.framework");
                }
                return keychain;
            }

            @Override
            public Optional<SecretStore> secretService(Path socket, String service) {
                probes.add("secret-service:" + socket + ":" + service);
                return available ? Optional.of(secretService) : Optional.empty();
            }
        };
    }

    private Path machineConfig() {
        return base.resolve(CredentialStoreChoice.FILE_NAME);
    }

    private void writeMachineConfig(String document) throws IOException {
        Files.writeString(machineConfig(), document, StandardCharsets.UTF_8);
    }

    private String readMachineConfig() throws IOException {
        return Files.readString(machineConfig(), StandardCharsets.UTF_8);
    }

    /** Writes the record of a move from the OS store to the file store that is open. */
    private void openMove() throws IOException {
        var recordFile = CredentialStoreMoveRecord.file(base);
        Files.createDirectories(recordFile.getParent());
        Files.write(recordFile, CredentialStoreMoveRecord.copying(Operation.MOVE, Store.OS, Store.FILE,
                Instant.parse("2026-10-17T15:00:00Z"), false, TRANSACTION).write());
    }

    private static String document(String credentials) {
        return "{\"format_version\":1,\"revision\":3,\"max_build_slots\":2,\"lsp_idle_timeout_seconds\":900"
                + credentials + "}";
    }

    @Nested
    @DisplayName("reading the recorded store")
    class Recorded {

        @Test
        @DisplayName("an absent file records no store")
        void absentFile() {
            assertTrue(linux(true).recorded().isEmpty(), "an absent file records no store");
        }

        @Test
        @DisplayName("a document without the key records no store")
        void absentKey() throws Exception {
            writeMachineConfig(document(",\"web\":{\"enabled\":true}"));

            assertTrue(linux(true).recorded().isEmpty(), "a document without the key records no store");
        }

        @Test
        @DisplayName("credentials without a store record no store")
        void absentStoreInCredentials() throws Exception {
            writeMachineConfig(document(",\"credentials\":{}"));

            assertTrue(linux(true).recorded().isEmpty(), "credentials without a store record no store");
        }

        @Test
        @DisplayName("os is the OS store")
        void os() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));

            assertEquals(Optional.of(Store.OS), linux(true).recorded(), "os is read as the OS store");
        }

        @Test
        @DisplayName("file is the file store")
        void file() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"file\"}"));

            assertEquals(Optional.of(Store.FILE), linux(true).recorded(), "file is read as the file store");
        }

        @ParameterizedTest(name = "credentials.store {0}")
        @ValueSource(strings = {"\"keyring\"", "\"OS\"", "\"\"", "1", "null", "[\"os\"]"})
        @DisplayName("any other value is refused as unreadable, never read as no store")
        void invalidValue(String value) throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":" + value + "}"));
            var choice = linux(true);

            var refusal = assertThrows(StoreRefusal.class, choice::recorded,
                    "an invalid store value is refused as unreadable");

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome(),
                            "the refusal for an invalid value is unreadable"),
                    () -> assertEquals(machineConfig().toAbsolutePath().normalize(), refusal.file(),
                            "the refusal names the store file"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"[]", "{\"format_version\":1,\"credentials\":\"os\"}",
                "{\"format_version\":1,\"revision\":-1}", "{\"format_version\":1,\"revision\":1,\"revision\":2}",
                "{\"format_version\":1} {}"})
        @DisplayName("a document that is not in the one shape of the file is refused as unreadable")
        void unreadable(String content) throws Exception {
            writeMachineConfig(content);
            var choice = linux(true);

            assertEquals(StoreRefusal.Outcome.UNREADABLE,
                    assertThrows(StoreRefusal.class, choice::recorded,
                            "a document of the wrong shape is refused").outcome(),
                    "a document of another shape is refused as unreadable");
        }

        @Test
        @DisplayName("a document of a newer format version is refused as newer")
        void newerFormat() throws Exception {
            writeMachineConfig("{\"format_version\":2,\"revision\":1,\"credentials\":{\"store\":\"os\"}}");
            var choice = linux(true);

            assertEquals(StoreRefusal.Outcome.FORMAT_NEWER,
                    assertThrows(StoreRefusal.class, choice::recorded,
                            "a newer format document is refused").outcome(),
                    "a newer format version is refused as newer");
        }
    }

    @Nested
    @DisplayName("recording the store")
    class Record {

        @Test
        @DisplayName("an absent document is created with the required keys at their defaults and revision 1")
        void creates() throws Exception {
            linux(true).record(locks, USER, Store.FILE);

            assertEquals("{\"format_version\":1,\"revision\":1,\"max_build_slots\":2,"
                    + "\"lsp_idle_timeout_seconds\":900,\"credentials\":{\"store\":\"file\"}}\n",
                    readMachineConfig(), "an absent document is created with the defaults");
        }

        @Test
        @DisplayName("a write keeps every foreign member as it is and increments the revision")
        void keepsForeignMembers() throws Exception {
            writeMachineConfig("{\"format_version\":1,\"revision\":4,\"max_build_slots\":3,"
                    + "\"lsp_idle_timeout_seconds\":600,\"web\":{\"enabled\":true,\"port\":7500},"
                    + "\"credentials\":{\"store\":\"file\"},\"plan_defaults\":{\"posture\":\"full\"}}");
            openMove();

            linux(true).record(transaction, Store.OS, Writer.STORE_MOVE);

            assertEquals("{\"format_version\":1,\"revision\":5,\"max_build_slots\":3,"
                    + "\"lsp_idle_timeout_seconds\":600,\"web\":{\"enabled\":true,\"port\":7500},"
                    + "\"credentials\":{\"store\":\"os\"},\"plan_defaults\":{\"posture\":\"full\"}}\n",
                    readMachineConfig(), "foreign members are kept and the revision increments");
        }

        @Test
        @DisplayName("a document without credentials gains the key after its other members")
        void addsCredentials() throws Exception {
            writeMachineConfig(document(",\"web\":{\"enabled\":false}"));
            var choice = linux(true);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(document(",\"web\":{\"enabled\":false},\"credentials\":{\"store\":\"os\"}")
                            .replace("\"revision\":3", "\"revision\":4") + "\n", readMachineConfig(),
                            "a document without credentials gains the key"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the added key records the OS store"));
        }

        @Test
        @DisplayName("the write of the move holds the credentials and the machine configuration lock and logs the store")
        void lockedAndLogged() throws Exception {
            openMove();

            linux(true).record(transaction, Store.FILE, Writer.STORE_MOVE);

            assertAll(
                    () -> assertTrue(transaction.holds(LockKey.credentials(base)),
                            "the move holds the credentials lock"),
                    () -> assertTrue(transaction.holds(LockKey.machineConfiguration(base)),
                            "the move holds the machine configuration lock"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_RECORDED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("the aspect records through its own path, never in the transaction of the move")
        void aspectNotInTheTransactionOfTheMove() {
            var choice = linux(true);

            assertThrows(IllegalArgumentException.class,
                    () -> choice.record(transaction, Store.FILE, Writer.CREDENTIAL_STORE_ASPECT),
                    "the aspect never records in a move's transaction");

            assertAll(
                    () -> assertFalse(transaction.holds(LockKey.credentials(base)),
                            "the refused record holds no credentials lock"),
                    () -> assertFalse(Files.exists(machineConfig()),
                            "the refused record writes no configuration file"));
        }

        @Test
        @DisplayName("every other writer is refused with aspect_owned, and nothing is locked or written")
        void aspectOwned() {
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class,
                    () -> choice.record(transaction, Store.OS, Writer.OTHER_CONFIGURATION),
                    "another writer is refused with aspect_owned");

            assertAll(
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, refusal.reason(),
                            "another writer's refusal is aspect_owned"),
                    () -> assertEquals("aspect_owned", refusal.reason().code(),
                            "the refusal reason carries its code"),
                    () -> assertFalse(transaction.holds(LockKey.credentials(base)),
                            "the refused writer holds no credentials lock"),
                    () -> assertFalse(transaction.holds(LockKey.machineConfiguration(base)),
                            "the refused writer holds no configuration lock"),
                    () -> assertFalse(Files.exists(machineConfig()),
                            "the refused writer writes no configuration file"));
        }

        @Test
        @DisplayName("only the credential-store aspect and the move own the key")
        void owners() {
            assertAll(
                    () -> assertTrue(Writer.CREDENTIAL_STORE_ASPECT.owner(), "the aspect owns the store key"),
                    () -> assertTrue(Writer.STORE_MOVE.owner(), "the move owns the store key"),
                    () -> assertFalse(Writer.OTHER_CONFIGURATION.owner(),
                            "another configuration does not own the key"));
        }

        @Test
        @DisplayName("while the record of a move exists the aspect is refused with aspect_owned and nothing is written")
        void aspectRefusedWhileAMoveIsOpen() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            openMove();
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class,
                    () -> choice.record(locks, USER, Store.FILE),
                    "the aspect is refused while a move record exists");

            assertAll(
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, refusal.reason(),
                            "the refusal while a move is open is aspect_owned"),
                    () -> assertTrue(refusal.getMessage().contains(CredentialStoreMoveRecord.FILE_NAME),
                            "the refusal names the move record file"),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig(),
                            "the refused aspect leaves the document as it is"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the refused aspect records no store"));
        }

        @Test
        @DisplayName("while the record of a move exists the move itself records the store")
        void moveRecordsWhileItsRecordExists() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            openMove();
            var choice = linux(true);

            choice.record(transaction, Store.FILE, Writer.STORE_MOVE);

            assertEquals(Optional.of(Store.FILE), choice.recorded(), "the move records the file store");
        }

        @Test
        @DisplayName("without the record of a move the store move is refused and nothing is written")
        void storeMoveRefusedOutsideAMove() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            var choice = linux(true);

            var refusal = assertThrows(IllegalStateException.class,
                    () -> choice.record(transaction, Store.FILE, Writer.STORE_MOVE),
                    "the store move is refused outside a move record");

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(CredentialStoreMoveRecord.FILE_NAME),
                            "the refusal names the move record file"),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig(),
                            "the refused move leaves the document as it is"),
                    () -> assertFalse(transaction.holds(LockKey.machineConfiguration(base)),
                            "the refused move holds no configuration lock"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the refused move records no new store"));
        }

        @Test
        @DisplayName("once the record of the move is gone the aspect is still refused for the other store, since a "
                + "change of the recorded store is a move")
        void aspectRefusedAfterTheMoveForTheOtherStore() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            openMove();
            var choice = linux(true);
            var whileOpen = assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE),
                    "the aspect is refused while the move record exists");
            Files.delete(CredentialStoreMoveRecord.file(base));

            var afterwards = assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE),
                    "the aspect is still refused after the move");

            assertAll(
                    () -> assertTrue(whileOpen.getMessage().contains("only that operation records"),
                            "the open move is named as the only recorder"),
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, afterwards.reason(),
                            "the later refusal is still aspect_owned"),
                    () -> assertTrue(afterwards.getMessage().contains("a change to 'file' is a move"),
                            "the later refusal says the change is a move"),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig(),
                            "the refused change leaves the document as it is"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the refused change keeps the OS store"));
        }

        @Test
        @DisplayName("while a store is recorded the aspect is refused for the other store with aspect_owned, and "
                + "nothing is written, carried or deleted")
        void aspectRefusedForTheOtherStore() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            secretService.put(GITHUB, secret);
            var key = new EnrolmentKey(secretService, base.resolve("state"), USER, new SecureRandom()).readOrCreate();
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE),
                    "the aspect is refused for the other store");

            assertAll(
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, refusal.reason(),
                            "the other-store refusal is aspect_owned"),
                    () -> assertEquals("aspect_owned", refusal.reason().code(),
                            "the other-store refusal carries its code"),
                    () -> assertTrue(refusal.getMessage().contains("'os' is recorded"),
                            "the refusal names the recorded OS store"),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig(),
                            "the other-store refusal keeps the document"),
                    () -> assertEquals(List.of(CredentialAccount.global(EnrolmentKey.ACCOUNT), GITHUB),
                            secretService.entries(), "both the key entry and the credential are kept"),
                    () -> assertEquals(Optional.of(secret), secretService.get(GITHUB),
                            "the credential value is untouched"),
                    () -> assertArrayEquals(key.getEncoded(), new EnrolmentKey(secretService, base.resolve("state"),
                                    USER, new SecureRandom()).read().orElseThrow().getEncoded(),
                            "the integrity key is still readable"),
                    () -> assertFalse(Files.exists(base.resolve("credentials")),
                            "no file store is created for the other store"),
                    () -> assertFalse(Files.exists(base.resolve("state").resolve(EnrolmentKey.FILE_NAME)),
                            "no key file is written for the other store"));
        }

        @Test
        @DisplayName("the aspect for the recorded store again writes nothing and probes no store")
        void aspectForTheRecordedStoreIsANoOp() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            var choice = linux(true);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig(),
                            "recording the same store keeps the document"),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the recorded store stays the OS store"),
                    () -> assertEquals(List.of(), probes, "recording the same store probes no store"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_RECORDED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("an unreadable document is refused and left as it is")
        void unreadableKept() throws Exception {
            writeMachineConfig("{\"format_version\":1,\"revision\":\"one\"}");
            openMove();
            var choice = linux(true);

            assertThrows(StoreRefusal.class, () -> choice.record(transaction, Store.OS, Writer.STORE_MOVE),
                    "an unreadable document is refused");

            assertEquals("{\"format_version\":1,\"revision\":\"one\"}", readMachineConfig(),
                    "the refused document is left as it is");
        }
    }

    @Nested
    @DisplayName("the active store")
    class Active {

        @Test
        @DisplayName("before a store is recorded a credential is refused with onboarding_incomplete naming the step")
        void onboardingIncomplete() {
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class, choice::activeStore,
                    "an unrecorded store refuses the active store");

            assertAll(
                    () -> assertEquals(Refusal.Reason.ONBOARDING_INCOMPLETE, refusal.reason(),
                            "the refusal names onboarding_incomplete"),
                    () -> assertEquals("onboarding_incomplete", refusal.reason().code(),
                            "onboarding_incomplete is the refusal code"),
                    () -> assertTrue(refusal.getMessage().contains("'store' step"),
                            "the refusal names the onboarding store step"),
                    () -> assertTrue(probes.isEmpty(), "no store is probed before a record"));
        }

        @Test
        @DisplayName("with file the file store below <base>/credentials, no OS store probed")
        void file() {
            var choice = linux(true);
            choice.record(locks, USER, Store.FILE);
            probes.clear();

            var store = assertInstanceOf(FileSecretStore.class, choice.activeStore(),
                    "the active store with file is a file store");

            assertAll(
                    () -> assertEquals(base.resolve("credentials/github.json"), store.path(GITHUB),
                            "the active store keeps credentials below base"),
                    () -> assertTrue(probes.isEmpty(), "the file store probes no OS store"));
        }

        @Test
        @DisplayName("with os on Linux the Secret Service under the service name")
        void secretService() {
            var choice = linux(true);
            choice.record(locks, USER, Store.OS);
            var store = choice.activeStore();

            store.put(GITHUB, secret);

            assertAll(
                    () -> assertEquals(SecretStore.SECRET_SERVICE, store.name(),
                            "the active store is the Secret Service"),
                    () -> assertEquals(Optional.of(secret), store.get(GITHUB),
                            "the active store returns the stored secret"),
                    () -> assertEquals(List.of(GITHUB), store.entries(), "the active store lists the one entry"),
                    () -> assertEquals(Optional.of(secret), secretService.get(GITHUB),
                            "the secret reaches the Secret Service"),
                    () -> assertTrue(store.delete(GITHUB), "the delete removes the stored secret"),
                    () -> assertEquals("secret-service:/run/user/1/bus:" + SERVICE, probes.getFirst(),
                            "the probe names the session bus and service"),
                    () -> assertFalse(Files.exists(base.resolve("credentials")),
                            "no file store is written with os"));
        }

        @Test
        @DisplayName("with os on macOS the Keychain under the service name")
        void keychain() {
            var choice = choice(MachinePaths.Os.MACOS, Map.of(), true);
            choice.record(locks, USER, Store.OS);
            var store = choice.activeStore();

            store.put(GITHUB, secret);

            assertAll(
                    () -> assertEquals(SecretStore.KEYCHAIN, store.name(), "the active store is the Keychain"),
                    () -> assertEquals(Optional.of(secret), keychain.get(GITHUB),
                            "the secret reaches the Keychain"),
                    () -> assertEquals("keychain:" + SERVICE, probes.getFirst(),
                            "the probe names the Keychain service"));
        }

        @Test
        @DisplayName("with os on macOS the Keychain store is loaded once across every operation")
        void keychainLoadedOnce() {
            var choice = choice(MachinePaths.Os.MACOS, Map.of(), true);
            choice.record(locks, USER, Store.OS);
            var store = choice.activeStore();

            store.put(GITHUB, secret);
            store.get(GITHUB);
            store.entries();
            choice.activeStore().delete(GITHUB);
            choice.backendOf(Store.OS).entries();

            assertEquals(List.of("keychain:" + SERVICE), probes, "the Keychain is loaded only once");
        }

        @Test
        @DisplayName("with os on macOS a Keychain that does not load is tried again at the next operation")
        void failedKeychainLoadNotKept() {
            var loads = new boolean[]{false};
            var choice = new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.MACOS, Map.of(),
                    new CredentialStoreChoice.Keyrings() {

                        @Override
                        public SecretStore keychain(String service) {
                            probes.add("keychain");
                            if (!loads[0]) {
                                throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                                        "no Security.framework");
                            }
                            return keychain;
                        }

                        @Override
                        public Optional<SecretStore> secretService(Path socket, String service) {
                            return Optional.empty();
                        }
                    });
            var store = choice.backendOf(Store.OS);
            assertReason(SecretStoreException.Reason.LOCKED, () -> store.put(GITHUB, "t"));

            loads[0] = true;
            store.put(GITHUB, "t");
            store.get(GITHUB);

            assertAll(
                    () -> assertEquals(Optional.of("t"), keychain.get(GITHUB),
                            "a later load serves the Keychain again"),
                    () -> assertEquals(List.of("keychain", "keychain"), probes,
                            "the failed load is tried once more"));
        }

        @Test
        @DisplayName("a switch of the recorded store takes effect for the next store given")
        void switchTakesEffect() throws Exception {
            var choice = linux(true);
            choice.record(locks, USER, Store.FILE);
            var before = choice.activeStore().name();
            openMove();

            choice.record(transaction, Store.OS, Writer.STORE_MOVE);

            assertAll(
                    () -> assertEquals(SecretStore.FILE, before, "the store before the move is the file store"),
                    () -> assertEquals(SecretStore.SECRET_SERVICE, choice.activeStore().name(),
                            "the switched store is the Secret Service"));
        }

        @Nested
        @DisplayName("an unavailable recorded OS store")
        class Unavailable {

            /** @return a bus that does not answer, no session bus address, a Keychain that does not load */
            private List<CredentialStoreChoice> unavailable() {
                return List.of(linux(false), choice(MachinePaths.Os.LINUX, Map.of(), true),
                        choice(MachinePaths.Os.MACOS, Map.of(), false));
            }

            @Test
            @DisplayName("fails a read with credential_store_unavailable and a write with credential_store_locked, "
                    + "and writes no file")
            void refuses() {
                for (var choice : unavailable()) {
                    choice.record(locks, USER, Store.OS);
                    var store = choice.activeStore();

                    assertAll(
                            () -> assertReason(SecretStoreException.Reason.UNAVAILABLE, () -> store.get(GITHUB)),
                            () -> assertReason(SecretStoreException.Reason.UNAVAILABLE, store::entries),
                            () -> assertReason(SecretStoreException.Reason.LOCKED, () -> store.put(GITHUB, "t")),
                            () -> assertReason(SecretStoreException.Reason.LOCKED, () -> store.delete(GITHUB)),
                            () -> assertFalse(Files.exists(base.resolve("credentials")),
                                    "an unavailable store writes no file"));
                }
                LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.RECORDED_OS_STORE_UNAVAILABLE.resolveIdentifierString());
            }

            @Test
            @DisplayName("names the cause and the codes of the API")
            void codes() {
                var choice = linux(false);
                choice.record(locks, USER, Store.OS);
                var store = choice.activeStore();

                var read = assertThrows(SecretStoreException.class, () -> store.get(GITHUB),
                        "a read on an unavailable store throws");
                var write = assertThrows(SecretStoreException.class, () -> store.put(GITHUB, "s3cr3t-token-value"),
                        "a write on an unavailable store throws");

                assertAll(
                        () -> assertEquals("credential_store_unavailable", read.reason().code(),
                                "a read reports credential_store_unavailable"),
                        () -> assertEquals("credential_store_locked", write.reason().code(),
                                "a write reports credential_store_locked"),
                        () -> assertTrue(read.getMessage().contains("/run/user/1/bus"),
                                "the read failure names the session bus"),
                        () -> assertFalse(write.getMessage().contains("s3cr3t-token-value"),
                                "the write failure never holds the value"));
            }

            @Test
            @DisplayName("given as the target of a move it is named as the OS store, not as the recorded store")
            void targetOfAMove() {
                var choice = linux(false);
                choice.record(locks, USER, Store.FILE);
                var target = choice.backendOf(Store.OS);

                var write = assertThrows(SecretStoreException.class, () -> target.put(GITHUB, "t"),
                        "a write to the move target throws");

                assertAll(
                        () -> assertEquals(SecretStoreException.Reason.LOCKED, write.reason(),
                                "the move target refuses with locked"),
                        () -> assertTrue(write.getMessage().startsWith("the credential store '" + SecretStore.SECRET_SERVICE + "'"),
                                "the move target is named as the OS store"),
                        () -> assertFalse(write.getMessage().contains("recorded"),
                                "the move target is not called the recorded one"),
                        () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                                WARN.RECORDED_OS_STORE_UNAVAILABLE.resolveIdentifierString()));
            }

            @Test
            @DisplayName("a store that becomes available later serves again")
            void availableLater() {
                var available = new boolean[]{false};
                var choice = new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.LINUX,
                        Map.of("DBUS_SESSION_BUS_ADDRESS", BUS), new CredentialStoreChoice.Keyrings() {

                            @Override
                            public SecretStore keychain(String service) {
                                return keychain;
                            }

                            @Override
                            public Optional<SecretStore> secretService(Path socket, String service) {
                                return available[0] ? Optional.of(secretService) : Optional.empty();
                            }
                        });
                choice.record(locks, USER, Store.OS);
                var store = choice.activeStore();
                assertReason(SecretStoreException.Reason.LOCKED, () -> store.put(GITHUB, "t"));

                available[0] = true;
                store.put(GITHUB, "t");

                assertEquals(Optional.of("t"), secretService.get(GITHUB),
                        "the recovered store serves the write");
            }

            @Test
            @DisplayName("a locked item met by a read is unavailable; a locked write and other failures keep their reason")
            void lockedItem() {
                var choice = linux(true);
                choice.record(locks, USER, Store.OS);
                var store = choice.activeStore();

                secretService.failWith(SecretStoreException.Reason.LOCKED);
                assertAll(
                        () -> assertReason(SecretStoreException.Reason.UNAVAILABLE, () -> store.get(GITHUB)),
                        () -> assertReason(SecretStoreException.Reason.UNAVAILABLE, store::entries),
                        () -> assertReason(SecretStoreException.Reason.LOCKED, () -> store.put(GITHUB, "t")));
                secretService.failWith(SecretStoreException.Reason.FAILED);
                assertAll(
                        () -> assertReason(SecretStoreException.Reason.FAILED, () -> store.get(GITHUB)),
                        () -> assertReason(SecretStoreException.Reason.FAILED, store::entries));
            }
        }
    }

    @Nested
    @DisplayName("the store of the integrity key")
    class IntegrityKey {

        @Test
        @DisplayName("before the choice the OS store where one is available")
        void osBeforeChoice() {
            assertSame(secretService, linux(true).integrityKeyStore(), "the OS store serves the integrity key");
        }

        @Test
        @DisplayName("before the choice the file store, the key file, where no OS store is available")
        void fileBeforeChoice() {
            assertEquals(SecretStore.FILE, linux(false).integrityKeyStore().name(),
                    "the file store serves the integrity key");
        }

        @Test
        @DisplayName("after the choice the recorded store, also where an OS store is available")
        void recordedAfterChoice() {
            var choice = linux(true);
            choice.record(locks, USER, Store.FILE);

            assertEquals(SecretStore.FILE, choice.integrityKeyStore().name(),
                    "the recorded file store serves the key");
        }

        /** @return a Linux choice whose Secret Service answers while {@code available} holds */
        private CredentialStoreChoice flipping(boolean[] available) {
            return new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.LINUX,
                    Map.of("DBUS_SESSION_BUS_ADDRESS", BUS), new CredentialStoreChoice.Keyrings() {

                        @Override
                        public SecretStore keychain(String service) {
                            return keychain;
                        }

                        @Override
                        public Optional<SecretStore> secretService(Path socket, String service) {
                            probes.add("secret-service");
                            return available[0] ? Optional.of(secretService) : Optional.empty();
                        }
                    });
        }

        @Test
        @DisplayName("before the choice an OS store found once stays the store when it is unavailable later")
        void osStaysAfterOutage() {
            var available = new boolean[]{true};
            var choice = flipping(available);
            var first = choice.integrityKeyStore();

            available[0] = false;
            var later = choice.integrityKeyStore();

            assertAll(
                    () -> assertSame(secretService, first, "the first store found is the OS store"),
                    () -> assertSame(first, later, "the found store stays over an outage"),
                    () -> assertEquals(1, probes.size(), "the store is probed only once"));
        }

        @Test
        @DisplayName("before the choice the file store found once stays the store when an OS store answers later")
        void fileStaysWhenOsAnswersLater() {
            var available = new boolean[]{false};
            var choice = flipping(available);
            var first = choice.integrityKeyStore();

            available[0] = true;
            var later = choice.integrityKeyStore();

            assertAll(
                    () -> assertEquals(SecretStore.FILE, first.name(), "the first store found is the file store"),
                    () -> assertSame(first, later, "the file store stays when os answers"));
        }

        @Test
        @DisplayName("once a store is recorded the recorded store answers, whatever was found before the choice")
        void recordedAnswersAfterTheFoundStore() {
            var available = new boolean[]{true};
            var choice = flipping(available);
            choice.integrityKeyStore();

            choice.record(locks, USER, Store.FILE);

            assertEquals(SecretStore.FILE, choice.integrityKeyStore().name(),
                    "the recorded store answers after the choice");
        }
    }

    /**
     * The first recording of a store carries the integrity key from the store the runtime found before the choice into
     * the recorded store (ADR runtime-credentials/006), so the sidecars signed before the choice verify afterwards and
     * no second key is created.
     */
    @Nested
    @DisplayName("the first recording carries the integrity key")
    class FirstRecording {

        private static final byte[] HARNESSES = "{\"format_version\":1,\"harnesses\":{}}"
                .getBytes(StandardCharsets.UTF_8);
        private static final Instant STARTED = Instant.parse("2026-10-17T15:00:00Z");

        private final SecureRandom random = new SecureRandom();
        private final boolean[] osAvailable = {true};
        private Path stateDirectory;

        @BeforeEach
        void machine() throws IOException {
            stateDirectory = Files.createDirectories(base.resolve("state"));
            Files.createDirectories(new EnrolmentStore(base).directory());
            Files.createDirectories(AuditHead.headFile(base).getParent());
        }

        /** @return a Linux choice whose Secret Service answers while {@link #osAvailable} holds */
        private CredentialStoreChoice choice() {
            return new CredentialStoreChoice(base, SERVICE, MachinePaths.Os.LINUX,
                    Map.of("DBUS_SESSION_BUS_ADDRESS", BUS), new CredentialStoreChoice.Keyrings() {

                        @Override
                        public SecretStore keychain(String service) {
                            return keychain;
                        }

                        @Override
                        public Optional<SecretStore> secretService(Path socket, String service) {
                            return osAvailable[0] ? Optional.of(secretService) : Optional.empty();
                        }
                    }, Clock.fixed(STARTED, ZoneOffset.UTC));
        }

        /** @return the key of the instance, as the enrolments read it: through the store of the integrity key */
        private EnrolmentKey key(CredentialStoreChoice choice) {
            return new EnrolmentKey(choice::integrityKeyStore, stateDirectory, USER, random);
        }

        private EnrolmentKey keyIn(SecretStore store) {
            return new EnrolmentKey(store, stateDirectory, USER, random);
        }

        /** Signs {@code harnesses.json} under the key, as an enrolment before the choice would have. */
        private void signHarnesses(SecretKey key) throws IOException {
            write(base.resolve(HarnessesSidecarCheck.FILE_NAME), HARNESSES);
            write(base.resolve(HarnessesSidecarCheck.FILE_NAME + EnrolmentSidecar.SUFFIX),
                    EnrolmentHmac.sidecar(key, HARNESSES).write());
        }

        private static void write(Path path, byte[] bytes) throws IOException {
            Files.write(path, bytes);
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        }

        private Optional<EnrolmentSidecars.Failure> verified(SecretKey key) {
            try (var verification = locks.openTransaction()) {
                return new EnrolmentSidecars(base, USER).verify(verification, key);
            }
        }

        @Test
        @DisplayName("of file with the key in the OS store: the key file holds it, the OS store no longer, the sidecars "
                + "verify and no second key is created")
        void fileWithTheKeyInTheOsStore() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);

            choice.record(locks, USER, Store.FILE);
            var afterwards = key(choice).readOrCreate();

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the recording records the file store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the key file holds the carried key"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(), "the OS store gives the key up"),
                    () -> assertArrayEquals(created.getEncoded(), afterwards.getEncoded(),
                            "the key afterwards is the same key"),
                    () -> assertTrue(verified(afterwards).isEmpty(),
                            "every sidecar verifies under the carried key"));
        }

        @Test
        @DisplayName("of os with the key file: the OS store holds it, the key file is gone, the sidecars verify and no "
                + "second key is created")
        void osWithTheKeyFile() throws Exception {
            osAvailable[0] = false;
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);
            osAvailable[0] = true;

            choice.record(locks, USER, Store.OS);
            var afterwards = key(choice).readOrCreate();

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the recording records the OS store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded(), "the OS store holds the carried key"),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME)),
                            "the key file is gone after the carry"),
                    () -> assertArrayEquals(created.getEncoded(), afterwards.getEncoded(),
                            "the carried key is unchanged"),
                    () -> assertTrue(verified(afterwards).isEmpty(), "the sidecars verify after the carry"));
        }

        @Test
        @DisplayName("before any key exists nothing is carried and nothing fails")
        void noKeyYet() {
            var choice = choice();

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the first recording still records the store"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(),
                            "no key is carried into the OS store"),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME)),
                            "no key file appears without a key"),
                    () -> assertFalse(Files.exists(CredentialStoreMoveRecord.file(base)),
                            "no key moves, so the recording writes no record"));
        }

        @Test
        @DisplayName("a sidecar that does not verify under the carried key records nothing, and the found store keeps "
                + "the key")
        void sidecarFails() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(keyIn(new MemoryStore(SecretStore.KEYCHAIN)).readOrCreate());

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE),
                    "a sidecar that does not verify refuses");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the sidecar failure reports failed"),
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"),
                            "the failure says nothing was recorded"),
                    () -> assertTrue(failure.getMessage().contains(HarnessesSidecarCheck.FILE_NAME),
                            "the failure names the harnesses sidecar"),
                    () -> assertTrue(choice.recorded().isEmpty(), "a failed sidecar records no store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded(), "the found store keeps the key"),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME)),
                            "no key file is written on a sidecar failure"));
        }

        @Test
        @DisplayName("a target that holds another key records nothing and keeps both keys")
        void targetHoldsAnotherKey() {
            var choice = choice();
            var created = key(choice).readOrCreate();
            var other = keyIn(fileStoreName()).readOrCreate();

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE),
                    "a target that holds another key refuses");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the target failure reports failed"),
                    () -> assertTrue(choice.recorded().isEmpty(),
                            "a target with another key records no store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded(), "the found key stays in the OS store"),
                    () -> assertArrayEquals(other.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the target keeps its own key"));
        }

        @Test
        @DisplayName("of the store the key is in: nothing is copied and the key stays")
        void sameStore() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the recorded store is the OS store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded(), "the key stays in the OS store"),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME)),
                            "no key file appears for the same store"),
                    () -> assertArrayEquals(created.getEncoded(), key(choice).readOrCreate().getEncoded(),
                            "reading again returns the same key"));
        }

        @Test
        @DisplayName("a target that holds the same key already: the key is not copied again, the found store gives it "
                + "up and the sidecars verify")
        void targetHoldsTheSameKey() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);
            keyIn(fileStoreName()).write(created);

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the same-key recording records the file store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the target keeps the same key"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(), "the found store gives the key up"),
                    () -> assertTrue(verified(key(choice).readOrCreate()).isEmpty(),
                            "the sidecars verify under the kept key"));
        }

        @Test
        @DisplayName("a key that cannot be deleted from the other store after the switch: the recording stands, the "
                + "key is in both stores, the record stays and the next start deletes it")
        void deletionAfterTheSwitchFails() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);
            secretService.failDeletesWith(SecretStoreException.Reason.LOCKED);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE),
                    "a failed deletion after the switch refuses");
            var recorded = readRecord();

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.LOCKED, failure.reason(),
                            "the deletion failure keeps its reason"),
                    () -> assertTrue(failure.getMessage().contains("the recording of 'file' stands"),
                            "the failure says the file recording stands"),
                    () -> assertTrue(failure.getMessage().contains("in both stores"),
                            "the failure says the key is in both stores"),
                    () -> assertTrue(failure.getMessage().contains("the next start finishes the deletion"),
                            "the failure names the start that finishes it"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the recording of the file store stands"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the key file holds the recorded key"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded(), "the OS store still holds the key"),
                    () -> assertEquals(Operation.FIRST_RECORDING, recorded.operation(),
                            "the record names the first recording"),
                    () -> assertEquals(Phase.SWITCHED, recorded.phase(),
                            "the record is in the switched phase"),
                    () -> assertEquals(Store.OS, recorded.from(), "the record names the OS store as from"),
                    () -> assertEquals(Store.FILE, recorded.to(), "the record names the file store as to"),
                    () -> assertFalse(recorded.targetHeldKey(), "the target held no key before the carry"),
                    () -> assertTransactionId(recorded.transactionId().value()),
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED,
                            assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE),
                                    "a second recording of the other store is refused").reason(),
                            "a repeated recording is still aspect_owned"));

            secretService.acceptsDeletes();
            var resumed = move(choice).resume();
            var secondRun = move(choice).resume();

            assertResumedAfterTheSwitchFails(choice, created, resumed, secondRun, recorded);
        }

        private void assertResumedAfterTheSwitchFails(CredentialStoreChoice choice, SecretKey created,
                Optional<CredentialStoreMoveRecord> resumed, Optional<CredentialStoreMoveRecord> secondRun,
                CredentialStoreMoveRecord recorded) {
            assertAll(
                    () -> assertEquals(Optional.of(Operation.FIRST_RECORDING),
                            resumed.map(CredentialStoreMoveRecord::operation),
                            "the resume keeps the first recording operation"),
                    () -> assertEquals(resumed.orElseThrow().transactionId().value(), recorded.transactionId()
                            .value(), "the resume keeps the recorded transaction id"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(), "the key of the OS store is deleted"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the resumed key file holds the key"),
                    () -> assertTrue(verified(created).isEmpty(), "every sidecar verifies under the recorded key"),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the resume leaves the file store recorded"),
                    () -> assertFalse(Files.exists(CredentialStoreMoveRecord.file(base)),
                            "the resume ends the move record"),
                    () -> assertTrue(secondRun.isEmpty(), "a second resume on the finished state does nothing"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Placement.class, names = {"FOUND_ONLY", "OTHER_ONLY", "BOTH_SAME",
                "BOTH_DIFFERENT_FOUND_VERIFIES", "BOTH_DIFFERENT_OTHER_VERIFIES"})
        @DisplayName("the first recording of the store the instance found reconciles the two stores: the recorded store "
                + "holds the key the sidecars verify under and the key of the other store is deleted")
        void reconcilesTheOtherStore(Placement placement) throws Exception {
            var choice = choice();
            var kept = arrange(placement, choice);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the reconciled store is the OS store"),
                    () -> assertArrayEquals(kept.getEncoded(), keyIn(secretService).read().orElseThrow().getEncoded(),
                            "the recorded store holds the kept key"),
                    () -> assertTrue(keyIn(fileStoreName()).read().isEmpty(), "the key of the other store is deleted"),
                    () -> assertArrayEquals(kept.getEncoded(), key(choice).readOrCreate().getEncoded(),
                            "reading again returns the kept key"),
                    () -> assertTrue(verified(kept).isEmpty(), "every sidecar verifies under the recorded key"),
                    () -> assertFalse(Files.exists(CredentialStoreMoveRecord.file(base)),
                            "the move record is ended by the recording"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = Placement.class, names = {"BOTH_DIFFERENT_UNDECIDED", "BOTH_DIFFERENT_NEITHER_VERIFIES"})
        @DisplayName("a first recording whose two stores hold different keys that the sidecars do not decide between is "
                + "refused, names both stores and keeps both keys")
        void refusesWhenTheSidecarsDoNotDecide(Placement placement) throws Exception {
            var choice = choice();
            var found = arrange(placement, choice);
            var other = keyIn(fileStoreName()).read().orElseThrow();

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.OS),
                    "an undecided sidecar pair refuses the recording");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the undecided refusal reports failed"),
                    () -> assertTrue(failure.getMessage().contains("the first recording of 'os' is refused"),
                            "the failure says the OS recording is refused"),
                    () -> assertTrue(failure.getMessage().contains("'os' and 'file' hold different integrity keys"),
                            "the failure names both stores and their keys"),
                    () -> assertTrue(choice.recorded().isEmpty(), "the undecided refusal records no store"),
                    () -> assertArrayEquals(found.getEncoded(), keyIn(secretService).read().orElseThrow().getEncoded(),
                            "the refused recording keeps the found key"),
                    () -> assertArrayEquals(other.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the refused recording keeps the other key"),
                    () -> assertFalse(Files.exists(CredentialStoreMoveRecord.file(base)), "no record was written"));
        }

        @Test
        @DisplayName("a target-only key: the store being recorded holds the key, the other none, so nothing is carried "
                + "and the key stays")
        void targetOnlyKey() throws Exception {
            var choice = choice();
            var created = keyIn(fileStoreName()).readOrCreate();
            signHarnesses(created);

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "a target-only key still records the store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the target-only key stays in place"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(), "the OS store holds no key"),
                    () -> assertTrue(verified(created).isEmpty(),
                            "the sidecars verify under the target key"));
        }

        @Test
        @DisplayName("a failure after the copy removes the copy and the record, so a later recording records")
        void failureAfterTheCopyLeavesNothingToResume() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(freshKey());

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE),
                    "a failure after the copy refuses the recording");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"),
                            "the failure says nothing was recorded"),
                    () -> assertTrue(choice.recorded().isEmpty(), "a failed copy records no store"),
                    () -> assertTrue(keyIn(fileStoreName()).read().isEmpty(), "the copy was removed"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded(), "the found key stays in the OS store"),
                    () -> assertFalse(Files.exists(CredentialStoreMoveRecord.file(base)), "the record was deleted"));

            signHarnesses(created);
            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "the later recording records the file store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the later recording writes the key file"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(),
                            "the OS store holds no key afterwards"));
        }

        @Test
        @DisplayName("a store that cannot be read while it is not the one being recorded is taken as holding no key, "
                + "with a warning, and the recording proceeds")
        void unreadableOtherStore() throws Exception {
            var choice = choice();
            var created = keyIn(fileStoreName()).readOrCreate();
            signHarnesses(created);
            osAvailable[0] = false;

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "an unreadable other store still records"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the recording writes the key to the file"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_UNREADABLE_WHILE_RECORDING.resolveIdentifierString()));
        }

        /** @return a key of its own, from a store that is no store of the machine */
        private SecretKey freshKey() {
            return keyIn(new MemoryStore(SecretStore.KEYCHAIN)).readOrCreate();
        }

        private CredentialStoreMoveRecord readRecord() throws IOException {
            var recordFile = CredentialStoreMoveRecord.file(base);
            return CredentialStoreMoveRecord.read(recordFile, Files.readAllBytes(recordFile));
        }

        private CredentialStoreMove move(CredentialStoreChoice choice) {
            return new CredentialStoreMove(base, choice, locks, events::add, USER,
                    Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), ZoneOffset.UTC));
        }

        /**
         * Arranges where the integrity key lies when the OS store, the store this instance found, is the store being
         * recorded.
         *
         * @return the key the placement signs the sidecars under
         */
        private SecretKey arrange(Placement placement, CredentialStoreChoice choice) throws IOException {
            return switch (placement) {
                case FOUND_ONLY -> {
                    var found = key(choice).readOrCreate();
                    signHarnesses(found);
                    yield found;
                }
                case OTHER_ONLY -> {
                    var other = keyIn(fileStoreName()).readOrCreate();
                    signHarnesses(other);
                    yield other;
                }
                case BOTH_SAME -> {
                    var found = key(choice).readOrCreate();
                    keyIn(fileStoreName()).write(found);
                    signHarnesses(found);
                    yield found;
                }
                case BOTH_DIFFERENT_FOUND_VERIFIES -> {
                    var found = key(choice).readOrCreate();
                    keyIn(fileStoreName()).write(freshKey());
                    signHarnesses(found);
                    yield found;
                }
                case BOTH_DIFFERENT_OTHER_VERIFIES -> {
                    key(choice).readOrCreate();
                    var other = keyIn(fileStoreName()).readOrCreate();
                    signHarnesses(other);
                    yield other;
                }
                case BOTH_DIFFERENT_UNDECIDED -> {
                    var found = key(choice).readOrCreate();
                    keyIn(fileStoreName()).write(freshKey());
                    yield found;
                }
                case BOTH_DIFFERENT_NEITHER_VERIFIES -> {
                    var found = key(choice).readOrCreate();
                    keyIn(fileStoreName()).write(freshKey());
                    signHarnesses(freshKey());
                    yield found;
                }
            };
        }

        /**
         * Where the integrity key lies when the OS store, the store this instance found, is the store being recorded.
         */
        enum Placement {
            /** Only the store the instance found holds the key. */
            FOUND_ONLY,
            /** Only the other store holds the key, a key file written while the OS store was unavailable. */
            OTHER_ONLY,
            /** Both hold the same key. */
            BOTH_SAME,
            /** Both hold different keys, and the sidecars are signed under the key of the store being recorded. */
            BOTH_DIFFERENT_FOUND_VERIFIES,
            /** Both hold different keys, and the sidecars are signed under the key of the other store. */
            BOTH_DIFFERENT_OTHER_VERIFIES,
            /** Both hold different keys and no sidecar exists, so both verify. */
            BOTH_DIFFERENT_UNDECIDED,
            /** Both hold different keys and the sidecars are signed under a third key, so neither verifies. */
            BOTH_DIFFERENT_NEITHER_VERIFIES
        }

        /** Asserts that a transaction id is the one written form and carries the instant of the fixed clock. */
        private static void assertTransactionId(String value) {
            var id = new TransactionId(value);
            assertEquals(STARTED.toEpochMilli(),
                    Long.parseLong(id.value().substring(0, 8) + id.value().substring(9, 13), 16),
                    "the transaction id carries the clock's instant");
        }

        @Test
        @DisplayName("neither the integrity key nor a credential value reaches a log record or a message, on the "
                + "succeeding path")
        void noSecretInALogOnSuccess() throws Exception {
            var marker = "MARKER-6a1f0c-success";
            var choice = choice();
            var key = markerKey(marker);
            keyIn(fileStoreName()).write(key);
            signHarnesses(key);
            secretService.put(GITHUB, marker);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "the successful recording records the store"),
                    () -> assertNoMarker(marker, key, null));
        }

        @Test
        @DisplayName("neither the integrity key nor a credential value reaches a log record or a message, on the "
                + "refused path")
        void noSecretInALogOnARefusal() {
            var marker = "MARKER-6a1f0c-refusal";
            var choice = choice();
            var key = markerKey(marker);
            keyIn(secretService).write(key);
            keyIn(fileStoreName()).write(markerKey(marker + "-the-other-key"));
            secretService.put(GITHUB, marker);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.OS),
                    "a differing key pair refuses the recording");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("hold different integrity keys"),
                            "the failure says the keys differ"),
                    () -> assertNoMarker(marker, key, failure));
        }

        @Test
        @DisplayName("neither the integrity key nor a credential value reaches a log record or a message, on the "
                + "failing path before the switch")
        void noSecretInALogOnAFailure() throws Exception {
            var marker = "MARKER-6a1f0c-failure";
            var choice = choice();
            var key = markerKey(marker);
            keyIn(secretService).write(key);
            signHarnesses(markerKey(marker + "-the-other-key"));
            secretService.put(GITHUB, marker);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE),
                    "a sidecar that fails refuses the recording");

            assertAll(
                    () -> assertTrue(failure.getMessage().contains("does not verify"),
                            "the failure says the sidecar does not verify"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_FIRST_RECORDING_FAILED.resolveIdentifierString()),
                    () -> assertNoMarker(marker, key, failure));
        }

        @Test
        @DisplayName("neither the integrity key nor a credential value reaches a log record or a message, on the path "
                + "of a store that cannot be read")
        void noSecretInALogOnAnUnreadableStore() throws Exception {
            var marker = "MARKER-6a1f0c-unreadable";
            var choice = choice();
            var key = markerKey(marker);
            keyIn(fileStoreName()).write(key);
            signHarnesses(key);
            secretService.put(GITHUB, marker);
            osAvailable[0] = false;

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded(),
                            "an unreadable OS store still records file"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.CREDENTIAL_STORE_UNREADABLE_WHILE_RECORDING.resolveIdentifierString()),
                    () -> assertNoMarker(marker, key, null));
        }

        @Test
        @DisplayName("neither the integrity key nor a credential value reaches a log record or a message, on the path "
                + "the next start resumes")
        void noSecretInALogOnTheResume() throws Exception {
            var marker = "MARKER-6a1f0c-resume";
            var choice = choice();
            var key = markerKey(marker);
            keyIn(secretService).write(key);
            signHarnesses(key);
            secretService.put(GITHUB, marker);
            secretService.failDeletesWith(SecretStoreException.Reason.LOCKED);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE),
                    "a failed deletion refuses the recording");
            secretService.acceptsDeletes();
            new CredentialStoreMoveResume(move(choice)).run();

            assertAll(
                    () -> assertTrue(keyIn(secretService).read().isEmpty(),
                            "the resume deletes the key from the OS store"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_FIRST_RECORDING_RESUMED.resolveIdentifierString()),
                    () -> assertNoMarker(marker, key, failure));
        }

        @Test
        @DisplayName("a store being recorded that cannot be read while no key has to be moved is recorded without a "
                + "record")
        void unreadableStoreWithoutAKeyRecordsWithoutARecord() {
            osAvailable[0] = false;
            var choice = choice();

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded(),
                            "an unreadable store without a key records"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(),
                            "the unreadable store holds no key"),
                    () -> assertFalse(Files.exists(CredentialStoreMoveRecord.file(base)), "the key moved, so no record"));
        }

        /** A key of 32 bytes that carries the marker, so a log record that printed it in any form would show it. */
        private static SecretKey markerKey(String marker) {
            return new SecretKeySpec(Arrays.copyOf(marker.getBytes(StandardCharsets.UTF_8), 32),
                    EnrolmentKey.ALGORITHM);
        }

        /** Asserts that neither the marker nor the encoded key is in a log record or in the failure, at any level. */
        private static void assertNoMarker(String marker, SecretKey key, Throwable failure) {
            var encodedKey = Base64.getUrlEncoder().withoutPadding().encodeToString(key.getEncoded());
            for (var level : TestLogLevel.values()) {
                LogAsserts.assertNoLogMessagePresent(level, marker);
                LogAsserts.assertNoLogMessagePresent(level, encodedKey);
            }
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                var message = String.valueOf(cause.getMessage());
                assertFalse(message.contains(marker), "no message of the failure holds the marker");
                assertFalse(message.contains(encodedKey), "no message of the failure holds the key");
            }
        }

        @Test
        @DisplayName("a copy that cannot be removed from the target after a failed sidecar check is added to the cause, "
                + "and nothing is recorded")
        void removalOfTheCopyFails() throws Exception {
            osAvailable[0] = false;
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(keyIn(new MemoryStore(SecretStore.KEYCHAIN)).readOrCreate());
            osAvailable[0] = true;
            secretService.failDeletesWith(SecretStoreException.Reason.FAILED);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.OS),
                    "a failing copy removal refuses the recording");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason(),
                            "the failed removal reports failed"),
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"),
                            "the failure says nothing was recorded"),
                    () -> assertEquals(1, failure.getCause().getSuppressed().length,
                            "the removal failure carries one suppressed error"),
                    () -> assertInstanceOf(SecretStoreException.class, failure.getCause().getSuppressed()[0],
                            "the suppressed error is a store exception"),
                    () -> assertTrue(choice.recorded().isEmpty(), "the failed removal records no store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the original key stays in the file store"),
                    () -> assertTrue(keyIn(secretService).read().isPresent(),
                            "the OS store still holds the copied key"));
        }

        @Test
        @DisplayName("a store that fails while the key is carried fails the recording with its own reason")
        void storeFailsDuringTheCarry() throws Exception {
            osAvailable[0] = false;
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.OS),
                    "the store failing the carry refuses");

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.UNAVAILABLE, failure.reason(),
                            "the carry failure keeps its reason"),
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"),
                            "the failure says nothing was recorded"),
                    () -> assertTrue(choice.recorded().isEmpty(), "the failed carry records no store"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the key file keeps the found key"));
        }

        @Test
        @DisplayName("a later recording of the recorded store carries, writes and deletes nothing")
        void reRecording() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);
            choice.record(locks, USER, Store.FILE);
            var recordedDocument = readMachineConfig();

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(recordedDocument, readMachineConfig(), "a later recording keeps the document"),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded(), "the key file still holds the key"),
                    () -> assertTrue(keyIn(secretService).read().isEmpty(), "the OS store still holds no key"));
        }

        /** @return the file store as the key sees it: the key file below the state directory */
        private SecretStore fileStoreName() {
            return new FileSecretStore(base.resolve("credentials"));
        }
    }

    @Test
    @DisplayName("the running process reads the choice of its machine root under its service name")
    void ofMachinePaths() {
        var choice = CredentialStoreChoice.of(new MachinePaths(base.resolve("pm-base"), MachinePaths.Os.LINUX));

        assertAll(
                () -> assertTrue(choice.service().startsWith(SERVICE), "the service name starts with the product"),
                () -> assertTrue(choice.recorded().isEmpty(), "the process root records no store"),
                () -> assertEquals(SecretStore.SECRET_SERVICE, choice.osStoreName(),
                        "the process root names the Secret Service"));
    }

    private static void assertReason(SecretStoreException.Reason reason, Executable operation) {
        assertEquals(reason, assertThrows(SecretStoreException.class, operation,
                        "the operation is refused with an exception").reason(),
                "the operation refuses with its reason");
    }

    /** An OS store in memory, which can be told to fail every operation, or its deletions only. */
    private static final class MemoryStore implements SecretStore {

        private final String name;
        private final Map<String, String> secrets = new TreeMap<>();
        private SecretStoreException.Reason failure;
        private SecretStoreException.Reason deleteFailure;

        MemoryStore(String name) {
            this.name = name;
        }

        void failWith(SecretStoreException.Reason reason) {
            failure = reason;
        }

        void failDeletesWith(SecretStoreException.Reason reason) {
            deleteFailure = reason;
        }

        /** Stops refusing deletions after a recording that failed on one. */
        void acceptsDeletes() {
            deleteFailure = null;
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
            if (deleteFailure != null) {
                throw new SecretStoreException(deleteFailure, "store refuses to delete with " + deleteFailure.code());
            }
            return secrets.remove(account.account()) != null;
        }

        @Override
        public List<CredentialAccount> entries() {
            failIfAsked();
            return secrets.keySet().stream().map(CredentialAccount::global).toList();
        }
    }
}
