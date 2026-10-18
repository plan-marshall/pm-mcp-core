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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.EnrolmentSidecar;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.audit.AuditHead;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Refusal;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Store;
import de.planmarshall.runtime.credentials.CredentialStoreChoice.Writer;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.enrolment.EnrolmentSidecars;
import de.planmarshall.runtime.enrolment.HarnessesSidecarCheck;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The credential store as the operator's recorded choice (PM-CRED-2, PM-CRED-4): {@code credentials.store} of
 * {@code machine-config.json} is read strictly, written only by its two owners under the machine configuration lock,
 * and decides the one store the runtime uses; before the choice no credential is entered, and an unavailable recorded
 * OS store is never replaced by the file store.
 */
@EnableTestLogger
@DisplayName("CredentialStoreChoice: the operator's recorded store")
class CredentialStoreChoiceTest {

    private static final String SERVICE = "de.planmarshall.mcp";
    private static final String USER = System.getProperty("user.name");
    private static final String BUS = "unix:path=/run/user/1/bus";
    private static final CredentialAccount GITHUB = CredentialAccount.global("github");

    @TempDir
    Path base;

    private final List<AuditEvent> events = new ArrayList<>();
    private final List<String> probes = new ArrayList<>();
    private final MemoryStore keychain = new MemoryStore(SecretStore.KEYCHAIN);
    private final MemoryStore secretService = new MemoryStore(SecretStore.SECRET_SERVICE);
    private FileLockManager locks;
    private LockTransaction transaction;

    @BeforeEach
    void openTransaction() {
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
        Files.write(recordFile, new CredentialStoreMoveRecord(Store.OS, Store.FILE, Instant.parse("2026-10-17T15:00:00Z"),
                CredentialStoreMoveRecord.Phase.COPYING, false).write());
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
            assertTrue(linux(true).recorded().isEmpty());
        }

        @Test
        @DisplayName("a document without the key records no store")
        void absentKey() throws Exception {
            writeMachineConfig(document(",\"web\":{\"enabled\":true}"));

            assertTrue(linux(true).recorded().isEmpty());
        }

        @Test
        @DisplayName("credentials without a store record no store")
        void absentStoreInCredentials() throws Exception {
            writeMachineConfig(document(",\"credentials\":{}"));

            assertTrue(linux(true).recorded().isEmpty());
        }

        @Test
        @DisplayName("os is the OS store")
        void os() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));

            assertEquals(Optional.of(Store.OS), linux(true).recorded());
        }

        @Test
        @DisplayName("file is the file store")
        void file() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"file\"}"));

            assertEquals(Optional.of(Store.FILE), linux(true).recorded());
        }

        @ParameterizedTest(name = "credentials.store {0}")
        @ValueSource(strings = {"\"keyring\"", "\"OS\"", "\"\"", "1", "null", "[\"os\"]"})
        @DisplayName("any other value is refused as unreadable, never read as no store")
        void invalidValue(String value) throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":" + value + "}"));
            var choice = linux(true);

            var refusal = assertThrows(StoreRefusal.class, choice::recorded);

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refusal.outcome()),
                    () -> assertEquals(machineConfig().toAbsolutePath().normalize(), refusal.file()));
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
                    assertThrows(StoreRefusal.class, choice::recorded).outcome());
        }

        @Test
        @DisplayName("a document of a newer format version is refused as newer")
        void newerFormat() throws Exception {
            writeMachineConfig("{\"format_version\":2,\"revision\":1,\"credentials\":{\"store\":\"os\"}}");
            var choice = linux(true);

            assertEquals(StoreRefusal.Outcome.FORMAT_NEWER,
                    assertThrows(StoreRefusal.class, choice::recorded).outcome());
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
                    + "\"lsp_idle_timeout_seconds\":900,\"credentials\":{\"store\":\"file\"}}\n", readMachineConfig());
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
                    readMachineConfig());
        }

        @Test
        @DisplayName("a document without credentials gains the key after its other members")
        void addsCredentials() throws Exception {
            writeMachineConfig(document(",\"web\":{\"enabled\":false}"));
            var choice = linux(true);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(document(",\"web\":{\"enabled\":false},\"credentials\":{\"store\":\"os\"}")
                            .replace("\"revision\":3", "\"revision\":4") + "\n", readMachineConfig()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()));
        }

        @Test
        @DisplayName("the write of the move holds the credentials and the machine configuration lock and logs the store")
        void lockedAndLogged() throws Exception {
            openMove();

            linux(true).record(transaction, Store.FILE, Writer.STORE_MOVE);

            assertAll(
                    () -> assertTrue(transaction.holds(LockKey.credentials(base))),
                    () -> assertTrue(transaction.holds(LockKey.machineConfiguration(base))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_RECORDED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("the aspect records through its own path, never in the transaction of the move")
        void aspectNotInTheTransactionOfTheMove() {
            var choice = linux(true);

            assertThrows(IllegalArgumentException.class,
                    () -> choice.record(transaction, Store.FILE, Writer.CREDENTIAL_STORE_ASPECT));

            assertAll(
                    () -> assertFalse(transaction.holds(LockKey.credentials(base))),
                    () -> assertFalse(Files.exists(machineConfig())));
        }

        @Test
        @DisplayName("every other writer is refused with aspect_owned, and nothing is locked or written")
        void aspectOwned() {
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class,
                    () -> choice.record(transaction, Store.OS, Writer.OTHER_CONFIGURATION));

            assertAll(
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, refusal.reason()),
                    () -> assertEquals("aspect_owned", refusal.reason().code()),
                    () -> assertFalse(transaction.holds(LockKey.credentials(base))),
                    () -> assertFalse(transaction.holds(LockKey.machineConfiguration(base))),
                    () -> assertFalse(Files.exists(machineConfig())));
        }

        @Test
        @DisplayName("only the credential-store aspect and the move own the key")
        void owners() {
            assertAll(
                    () -> assertTrue(Writer.CREDENTIAL_STORE_ASPECT.owner()),
                    () -> assertTrue(Writer.STORE_MOVE.owner()),
                    () -> assertFalse(Writer.OTHER_CONFIGURATION.owner()));
        }

        @Test
        @DisplayName("while the record of a move exists the aspect is refused with aspect_owned and nothing is written")
        void aspectRefusedWhileAMoveIsOpen() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            openMove();
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class,
                    () -> choice.record(locks, USER, Store.FILE));

            assertAll(
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, refusal.reason()),
                    () -> assertTrue(refusal.getMessage().contains(CredentialStoreMoveRecord.FILE_NAME),
                            refusal.getMessage()),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()));
        }

        @Test
        @DisplayName("while the record of a move exists the move itself records the store")
        void moveRecordsWhileItsRecordExists() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            openMove();
            var choice = linux(true);

            choice.record(transaction, Store.FILE, Writer.STORE_MOVE);

            assertEquals(Optional.of(Store.FILE), choice.recorded());
        }

        @Test
        @DisplayName("without the record of a move the store move is refused and nothing is written")
        void storeMoveRefusedOutsideAMove() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            var choice = linux(true);

            var refusal = assertThrows(IllegalStateException.class,
                    () -> choice.record(transaction, Store.FILE, Writer.STORE_MOVE));

            assertAll(
                    () -> assertTrue(refusal.getMessage().contains(CredentialStoreMoveRecord.FILE_NAME),
                            refusal.getMessage()),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig()),
                    () -> assertFalse(transaction.holds(LockKey.machineConfiguration(base))),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()));
        }

        @Test
        @DisplayName("once the record of the move is gone the aspect is still refused for the other store, since a "
                + "change of the recorded store is a move")
        void aspectRefusedAfterTheMoveForTheOtherStore() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            openMove();
            var choice = linux(true);
            var whileOpen = assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE));
            Files.delete(CredentialStoreMoveRecord.file(base));

            var afterwards = assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE));

            assertAll(
                    () -> assertTrue(whileOpen.getMessage().contains("only the move records"), whileOpen.getMessage()),
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, afterwards.reason()),
                    () -> assertTrue(afterwards.getMessage().contains("a change to 'file' is a move"),
                            afterwards.getMessage()),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()));
        }

        @Test
        @DisplayName("while a store is recorded the aspect is refused for the other store with aspect_owned, and "
                + "nothing is written, carried or deleted")
        void aspectRefusedForTheOtherStore() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            secretService.put(GITHUB, "token");
            var key = new EnrolmentKey(secretService, base.resolve("state"), USER, new SecureRandom()).readOrCreate();
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class, () -> choice.record(locks, USER, Store.FILE));

            assertAll(
                    () -> assertEquals(Refusal.Reason.ASPECT_OWNED, refusal.reason()),
                    () -> assertEquals("aspect_owned", refusal.reason().code()),
                    () -> assertTrue(refusal.getMessage().contains("'os' is recorded"), refusal.getMessage()),
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig()),
                    () -> assertEquals(List.of(CredentialAccount.global(EnrolmentKey.ACCOUNT), GITHUB),
                            secretService.entries()),
                    () -> assertEquals(Optional.of("token"), secretService.get(GITHUB)),
                    () -> assertArrayEquals(key.getEncoded(), new EnrolmentKey(secretService, base.resolve("state"),
                            USER, new SecureRandom()).read().orElseThrow().getEncoded()),
                    () -> assertFalse(Files.exists(base.resolve("credentials"))),
                    () -> assertFalse(Files.exists(base.resolve("state").resolve(EnrolmentKey.FILE_NAME))));
        }

        @Test
        @DisplayName("the aspect for the recorded store again writes nothing and probes no store")
        void aspectForTheRecordedStoreIsANoOp() throws Exception {
            writeMachineConfig(document(",\"credentials\":{\"store\":\"os\"}"));
            var choice = linux(true);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(document(",\"credentials\":{\"store\":\"os\"}"), readMachineConfig()),
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertTrue(probes.isEmpty(), probes.toString()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.CREDENTIAL_STORE_RECORDED.resolveIdentifierString()));
        }

        @Test
        @DisplayName("an unreadable document is refused and left as it is")
        void unreadableKept() throws Exception {
            writeMachineConfig("{\"format_version\":1,\"revision\":\"one\"}");
            openMove();
            var choice = linux(true);

            assertThrows(StoreRefusal.class, () -> choice.record(transaction, Store.OS, Writer.STORE_MOVE));

            assertEquals("{\"format_version\":1,\"revision\":\"one\"}", readMachineConfig());
        }
    }

    @Nested
    @DisplayName("the active store")
    class Active {

        @Test
        @DisplayName("before a store is recorded a credential is refused with onboarding_incomplete naming the step")
        void onboardingIncomplete() {
            var choice = linux(true);

            var refusal = assertThrows(Refusal.class, choice::activeStore);

            assertAll(
                    () -> assertEquals(Refusal.Reason.ONBOARDING_INCOMPLETE, refusal.reason()),
                    () -> assertEquals("onboarding_incomplete", refusal.reason().code()),
                    () -> assertTrue(refusal.getMessage().contains("'store' step"), refusal.getMessage()),
                    () -> assertTrue(probes.isEmpty()));
        }

        @Test
        @DisplayName("with file the file store below <base>/credentials, no OS store probed")
        void file() {
            var choice = linux(true);
            choice.record(locks, USER, Store.FILE);
            probes.clear();

            var store = assertInstanceOf(FileSecretStore.class, choice.activeStore());

            assertAll(
                    () -> assertEquals(base.resolve("credentials/github.json"), store.path(GITHUB)),
                    () -> assertTrue(probes.isEmpty()));
        }

        @Test
        @DisplayName("with os on Linux the Secret Service under the service name")
        void secretService() {
            var choice = linux(true);
            choice.record(locks, USER, Store.OS);
            var store = choice.activeStore();

            store.put(GITHUB, "token");

            assertAll(
                    () -> assertEquals(SecretStore.SECRET_SERVICE, store.name()),
                    () -> assertEquals(Optional.of("token"), store.get(GITHUB)),
                    () -> assertEquals(List.of(GITHUB), store.entries()),
                    () -> assertEquals(Optional.of("token"), secretService.get(GITHUB)),
                    () -> assertTrue(store.delete(GITHUB)),
                    () -> assertEquals("secret-service:/run/user/1/bus:" + SERVICE, probes.getFirst()),
                    () -> assertFalse(Files.exists(base.resolve("credentials"))));
        }

        @Test
        @DisplayName("with os on macOS the Keychain under the service name")
        void keychain() {
            var choice = choice(MachinePaths.Os.MACOS, Map.of(), true);
            choice.record(locks, USER, Store.OS);
            var store = choice.activeStore();

            store.put(GITHUB, "token");

            assertAll(
                    () -> assertEquals(SecretStore.KEYCHAIN, store.name()),
                    () -> assertEquals(Optional.of("token"), keychain.get(GITHUB)),
                    () -> assertEquals("keychain:" + SERVICE, probes.getFirst()));
        }

        @Test
        @DisplayName("with os on macOS the Keychain store is loaded once across every operation")
        void keychainLoadedOnce() {
            var choice = choice(MachinePaths.Os.MACOS, Map.of(), true);
            choice.record(locks, USER, Store.OS);
            var store = choice.activeStore();

            store.put(GITHUB, "token");
            store.get(GITHUB);
            store.entries();
            choice.activeStore().delete(GITHUB);
            choice.backendOf(Store.OS).entries();

            assertEquals(List.of("keychain:" + SERVICE), probes);
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
                    () -> assertEquals(Optional.of("t"), keychain.get(GITHUB)),
                    () -> assertEquals(List.of("keychain", "keychain"), probes));
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
                    () -> assertEquals(SecretStore.FILE, before),
                    () -> assertEquals(SecretStore.SECRET_SERVICE, choice.activeStore().name()));
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
                            () -> assertFalse(Files.exists(base.resolve("credentials"))));
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

                var read = assertThrows(SecretStoreException.class, () -> store.get(GITHUB));
                var write = assertThrows(SecretStoreException.class, () -> store.put(GITHUB, "s3cr3t-token-value"));

                assertAll(
                        () -> assertEquals("credential_store_unavailable", read.reason().code()),
                        () -> assertEquals("credential_store_locked", write.reason().code()),
                        () -> assertTrue(read.getMessage().contains("/run/user/1/bus"), read.getMessage()),
                        () -> assertFalse(write.getMessage().contains("s3cr3t-token-value"), write.getMessage()));
            }

            @Test
            @DisplayName("given as the target of a move it is named as the OS store, not as the recorded store")
            void targetOfAMove() {
                var choice = linux(false);
                choice.record(locks, USER, Store.FILE);
                var target = choice.backendOf(Store.OS);

                var write = assertThrows(SecretStoreException.class, () -> target.put(GITHUB, "t"));

                assertAll(
                        () -> assertEquals(SecretStoreException.Reason.LOCKED, write.reason()),
                        () -> assertTrue(write.getMessage().startsWith("the credential store '" + SecretStore.SECRET_SERVICE + "'"),
                                write.getMessage()),
                        () -> assertFalse(write.getMessage().contains("recorded"), write.getMessage()),
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

                assertEquals(Optional.of("t"), secretService.get(GITHUB));
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
            assertSame(secretService, linux(true).integrityKeyStore());
        }

        @Test
        @DisplayName("before the choice the file store, the key file, where no OS store is available")
        void fileBeforeChoice() {
            assertEquals(SecretStore.FILE, linux(false).integrityKeyStore().name());
        }

        @Test
        @DisplayName("after the choice the recorded store, also where an OS store is available")
        void recordedAfterChoice() {
            var choice = linux(true);
            choice.record(locks, USER, Store.FILE);

            assertEquals(SecretStore.FILE, choice.integrityKeyStore().name());
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
                    () -> assertSame(secretService, first),
                    () -> assertSame(first, later),
                    () -> assertEquals(1, probes.size()));
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
                    () -> assertEquals(SecretStore.FILE, first.name()),
                    () -> assertSame(first, later));
        }

        @Test
        @DisplayName("once a store is recorded the recorded store answers, whatever was found before the choice")
        void recordedAnswersAfterTheFoundStore() {
            var available = new boolean[]{true};
            var choice = flipping(available);
            choice.integrityKeyStore();

            choice.record(locks, USER, Store.FILE);

            assertEquals(SecretStore.FILE, choice.integrityKeyStore().name());
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
                    });
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
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()),
                    () -> assertTrue(keyIn(secretService).read().isEmpty()),
                    () -> assertArrayEquals(created.getEncoded(), afterwards.getEncoded()),
                    () -> assertTrue(verified(afterwards).isEmpty()));
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
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded()),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME))),
                    () -> assertArrayEquals(created.getEncoded(), afterwards.getEncoded()),
                    () -> assertTrue(verified(afterwards).isEmpty()));
        }

        @Test
        @DisplayName("before any key exists nothing is carried and nothing fails")
        void noKeyYet() {
            var choice = choice();

            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertTrue(keyIn(secretService).read().isEmpty()),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME))));
        }

        @Test
        @DisplayName("a sidecar that does not verify under the carried key records nothing, and the found store keeps "
                + "the key")
        void sidecarFails() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(keyIn(new MemoryStore(SecretStore.KEYCHAIN)).readOrCreate());

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"), failure.getMessage()),
                    () -> assertTrue(failure.getMessage().contains(HarnessesSidecarCheck.FILE_NAME),
                            failure.getMessage()),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded()),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME))));
        }

        @Test
        @DisplayName("a target that holds another key records nothing and keeps both keys")
        void targetHoldsAnotherKey() {
            var choice = choice();
            var created = key(choice).readOrCreate();
            var other = keyIn(fileStoreName()).readOrCreate();

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded()),
                    () -> assertArrayEquals(other.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()));
        }

        @Test
        @DisplayName("of the store the key is in: nothing is copied and the key stays")
        void sameStore() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);

            choice.record(locks, USER, Store.OS);

            assertAll(
                    () -> assertEquals(Optional.of(Store.OS), choice.recorded()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded()),
                    () -> assertFalse(Files.exists(stateDirectory.resolve(EnrolmentKey.FILE_NAME))),
                    () -> assertArrayEquals(created.getEncoded(), key(choice).readOrCreate().getEncoded()));
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
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()),
                    () -> assertTrue(keyIn(secretService).read().isEmpty()),
                    () -> assertTrue(verified(key(choice).readOrCreate()).isEmpty()));
        }

        @Test
        @DisplayName("a key that cannot be deleted from the found store after the switch: the recording stands, the "
                + "failure says the key is in both stores, and a later recording of the store carries nothing")
        void deletionAfterTheSwitchFails() throws Exception {
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);
            secretService.failDeletesWith(SecretStoreException.Reason.LOCKED);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.FILE));
            var recordedDocument = readMachineConfig();
            choice.record(locks, USER, Store.FILE);

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.LOCKED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("the recording of 'file' stands"),
                            failure.getMessage()),
                    () -> assertTrue(failure.getMessage().contains("in both stores"), failure.getMessage()),
                    () -> assertFalse(failure.getMessage().contains("nothing was recorded"), failure.getMessage()),
                    () -> assertEquals(Optional.of(Store.FILE), choice.recorded()),
                    () -> assertEquals(recordedDocument, readMachineConfig()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(secretService).read().orElseThrow()
                            .getEncoded()),
                    () -> assertTrue(verified(key(choice).readOrCreate()).isEmpty()));
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

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.FAILED, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"), failure.getMessage()),
                    () -> assertEquals(1, failure.getCause().getSuppressed().length),
                    () -> assertInstanceOf(SecretStoreException.class, failure.getCause().getSuppressed()[0]),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()),
                    () -> assertTrue(keyIn(secretService).read().isPresent()));
        }

        @Test
        @DisplayName("a store that fails while the key is carried fails the recording with its own reason")
        void storeFailsDuringTheCarry() throws Exception {
            osAvailable[0] = false;
            var choice = choice();
            var created = key(choice).readOrCreate();
            signHarnesses(created);

            var failure = assertThrows(SecretStoreException.class, () -> choice.record(locks, USER, Store.OS));

            assertAll(
                    () -> assertEquals(SecretStoreException.Reason.UNAVAILABLE, failure.reason()),
                    () -> assertTrue(failure.getMessage().contains("nothing was recorded"), failure.getMessage()),
                    () -> assertTrue(choice.recorded().isEmpty()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()));
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
                    () -> assertEquals(recordedDocument, readMachineConfig()),
                    () -> assertArrayEquals(created.getEncoded(), keyIn(fileStoreName()).read().orElseThrow()
                            .getEncoded()),
                    () -> assertTrue(keyIn(secretService).read().isEmpty()));
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
                () -> assertTrue(choice.service().startsWith(SERVICE), choice.service()),
                () -> assertTrue(choice.recorded().isEmpty()),
                () -> assertEquals(SecretStore.SECRET_SERVICE, choice.osStoreName()));
    }

    private static void assertReason(SecretStoreException.Reason reason, Executable operation) {
        assertEquals(reason, assertThrows(SecretStoreException.class, operation).reason());
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
