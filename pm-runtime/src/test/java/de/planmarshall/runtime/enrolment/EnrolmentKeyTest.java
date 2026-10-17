/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.store.EnrolmentStore;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.CredentialStoreChoice;
import de.planmarshall.runtime.credentials.CredentialStoreChoiceFixture;
import de.planmarshall.runtime.credentials.FileSecretStore;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.credentials.SecretStoreException.Reason;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages.ERROR;

/**
 * The enrolment integrity key (PM-EXEC-2, PM-SEC-5): a keyring entry under the global account
 * {@code enrolment-integrity-key}, or on a host without a keyring the {@code 0600} key file below the state directory;
 * created once, then reused; absent and unreadable are told apart.
 */
@EnableTestLogger
@DisplayName("Enrolment key")
class EnrolmentKeyTest {

    private static final String USER = System.getProperty("user.name");
    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path state;

    @Nested
    @DisplayName("in a keyring")
    class Keyring {

        private InMemoryKeyring keyring;
        private EnrolmentKey key;

        @BeforeEach
        void create() {
            keyring = new InMemoryKeyring();
            key = new EnrolmentKey(keyring, state, USER, new SecureRandom());
        }

        @Test
        @DisplayName("is absent before the first enrolment write")
        void absent() {
            assertTrue(key.read().isEmpty());
        }

        @Test
        @DisplayName("is created once under the global account and reused, and no key file is written")
        void createdOnceAndReused() {
            var created = key.readOrCreate();

            var again = key.readOrCreate();

            assertAll(
                    () -> assertArrayEquals(created.getEncoded(), again.getEncoded()),
                    () -> assertArrayEquals(created.getEncoded(), key.read().orElseThrow().getEncoded()),
                    () -> assertEquals(EnrolmentKey.KEY_BYTES, created.getEncoded().length),
                    () -> assertEquals(EnrolmentKey.ALGORITHM, created.getAlgorithm()),
                    () -> assertEquals(1, keyring.entries.size()),
                    () -> assertTrue(keyring.entries.containsKey(EnrolmentKey.ACCOUNT)),
                    () -> assertFalse(Files.exists(key.keyFile())));
        }

        @Test
        @DisplayName("a failing keyring is unreadable, logged without the key")
        void failingKeyring() {
            keyring.failure = new SecretStoreException(Reason.LOCKED, "keyring locked");

            var thrown = assertThrows(SecretStoreException.class, key::read);

            assertAll(
                    () -> assertEquals(Reason.LOCKED, thrown.reason()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a keyring that refuses the new key is unavailable, logged")
        void refusedWrite() {
            keyring.refusePut = true;

            var thrown = assertThrows(SecretStoreException.class, key::readOrCreate);

            assertAll(
                    () -> assertEquals(Reason.FAILED, thrown.reason()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()));
        }

        @Test
        @DisplayName("an entry that is not a key of 32 bytes in base64url is unreadable")
        void malformedEntry() {
            keyring.entries.put(EnrolmentKey.ACCOUNT, "c2hvcnQ");

            assertEquals(Reason.INVALID, assertThrows(SecretStoreException.class, key::read).reason());
        }

        @Test
        @DisplayName("a new key is stored under the global account and leaves the state directory empty")
        void stateDirectoryUntouched() {
            key.readOrCreate();

            assertAll(
                    () -> assertTrue(keyring.entries.containsKey(EnrolmentKey.ACCOUNT)),
                    () -> assertEquals(List.of(), entries(state)));
        }
    }

    @Nested
    @DisplayName("in the key file, created atomically")
    class AtomicKeyFile {

        @Test
        @DisplayName("leaves exactly the complete 0600 key file and no temporary file, created once and reused")
        void completeKeyFileOnly() throws Exception {
            var key = new EnrolmentKey(new FileStoreName(), state, USER, new SecureRandom());

            var created = key.readOrCreate();
            var again = key.readOrCreate();

            var content = Files.readString(key.keyFile(), StandardCharsets.US_ASCII);
            assertAll(
                    () -> assertEquals(List.of(EnrolmentKey.FILE_NAME), entries(state)),
                    () -> assertEquals("rw-------",
                            PosixFilePermissions.toString(Files.getPosixFilePermissions(key.keyFile()))),
                    () -> assertEquals(EnrolmentKey.KEY_BYTES, Base64.getUrlDecoder().decode(content).length),
                    () -> assertArrayEquals(created.getEncoded(), Base64.getUrlDecoder().decode(content)),
                    () -> assertArrayEquals(created.getEncoded(), again.getEncoded()));
        }

        @ParameterizedTest
        @MethodSource("de.planmarshall.runtime.enrolment.EnrolmentKeyTest#partialKeys")
        @DisplayName("a partial or malformed key file is refused as invalid and never replaced")
        void partialKeyNeverReplaced(String content) throws Exception {
            var key = new EnrolmentKey(new FileStoreName(), state, USER, new SecureRandom());
            Files.writeString(key.keyFile(), content, StandardCharsets.US_ASCII);
            Files.setPosixFilePermissions(key.keyFile(), PosixFilePermissions.fromString("rw-------"));
            var before = Files.readAllBytes(key.keyFile());

            var thrown = assertThrows(SecretStoreException.class, key::readOrCreate);

            assertAll(
                    () -> assertEquals(Reason.INVALID, thrown.reason()),
                    () -> assertArrayEquals(before, Files.readAllBytes(key.keyFile())),
                    () -> assertEquals("rw-------",
                            PosixFilePermissions.toString(Files.getPosixFilePermissions(key.keyFile()))),
                    () -> assertEquals(List.of(EnrolmentKey.FILE_NAME), entries(state)));
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a key file with a mode broader than 0600 is refused as insecure and never replaced")
        void insecureKeyNeverReplaced() throws Exception {
            var key = new EnrolmentKey(new FileStoreName(), state, USER, new SecureRandom());
            key.readOrCreate();
            Files.setPosixFilePermissions(key.keyFile(), PosixFilePermissions.fromString("rw-r--r--"));
            var before = Files.readAllBytes(key.keyFile());

            var thrown = assertThrows(SecretStoreException.class, key::readOrCreate);

            assertAll(
                    () -> assertEquals(Reason.INSECURE, thrown.reason()),
                    () -> assertArrayEquals(before, Files.readAllBytes(key.keyFile())),
                    () -> assertEquals("rw-r--r--",
                            PosixFilePermissions.toString(Files.getPosixFilePermissions(key.keyFile()))),
                    () -> assertEquals(List.of(EnrolmentKey.FILE_NAME), entries(state)));
        }

        @Test
        @DisplayName("a key file another writer created between the read and the create is kept and returned")
        void racingWriterKept() {
            var keyFile = state.resolve(EnrolmentKey.FILE_NAME);
            var otherKey = filled((byte) 7);
            var racing = new FixedRandom((byte) 1, () -> writeKeyFile(keyFile, otherKey));
            var key = new EnrolmentKey(new FileStoreName(), state, USER, racing);

            var returned = key.readOrCreate();

            assertAll(
                    () -> assertArrayEquals(otherKey, returned.getEncoded()),
                    () -> assertEquals(encode(otherKey), Files.readString(keyFile, StandardCharsets.US_ASCII)),
                    () -> assertEquals(List.of(EnrolmentKey.FILE_NAME), entries(state)));
        }

        @Test
        @DisplayName("a key file that cannot be created in a missing directory leaves nothing behind")
        void missingDirectoryLeavesNothing() {
            var missing = state.resolve("absent-dir");
            var key = new EnrolmentKey(new FileStoreName(), missing, USER, new FixedRandom((byte) 3, () -> {
            }));

            var thrown = assertThrows(SecretStoreException.class, key::readOrCreate);

            assertFailedWithoutKey(thrown, key, encode(filled((byte) 3)));
            assertFalse(Files.exists(missing));
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a key file that cannot be created in an unwritable directory leaves nothing behind")
        void unwritableDirectoryLeavesNothing() throws Exception {
            assumeFalse("root".equals(USER), "root writes into an unwritable directory");
            var locked = Files.createDirectory(state.resolve("locked"));
            Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("r-x------"));
            var key = new EnrolmentKey(new FileStoreName(), locked, USER, new FixedRandom((byte) 5, () -> {
            }));
            try {
                var thrown = assertThrows(SecretStoreException.class, key::readOrCreate);

                assertFailedWithoutKey(thrown, key, encode(filled((byte) 5)));
                assertEquals(List.of(), entries(locked));
            } finally {
                Files.setPosixFilePermissions(locked, PosixFilePermissions.fromString("rwx------"));
            }
        }

        private void assertFailedWithoutKey(SecretStoreException thrown, EnrolmentKey key, String encoded) {
            assertAll(
                    () -> assertEquals(Reason.FAILED, thrown.reason()),
                    () -> assertTrue(thrown.getMessage().contains(key.keyFile().toString())),
                    () -> assertFalse(thrown.getMessage().contains(encoded)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()),
                    () -> assertTrue(TestLoggerFactory.getTestHandler()
                            .resolveLogMessagesContaining(TestLogLevel.ERROR, encoded).isEmpty()),
                    () -> assertFalse(Files.exists(key.keyFile())));
        }
    }

    static Stream<String> partialKeys() {
        return Stream.of("", "A".repeat(42), "not base64!");
    }

    private static List<String> entries(Path directory) throws IOException {
        try (var listing = Files.list(directory)) {
            return listing.map(entry -> entry.getFileName().toString()).sorted().toList();
        }
    }

    private static byte[] filled(byte value) {
        var bytes = new byte[EnrolmentKey.KEY_BYTES];
        Arrays.fill(bytes, value);
        return bytes;
    }

    private static String encode(byte[] key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key);
    }

    private static void writeKeyFile(Path keyFile, byte[] key) {
        try {
            Files.writeString(keyFile, encode(key), StandardCharsets.US_ASCII);
            Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A random source of the tests: fills every key with one byte value, after running a hook. The key calls it after
     * its read found no key and before it creates one, so the hook can play a racing writer.
     */
    private static final class FixedRandom extends SecureRandom {

        @Serial
        private static final long serialVersionUID = 1L;

        private final byte value;
        private final transient Runnable beforeFill;

        FixedRandom(byte value, Runnable beforeFill) {
            this.value = value;
            this.beforeFill = beforeFill;
        }

        @Override
        public void nextBytes(byte[] bytes) {
            beforeFill.run();
            Arrays.fill(bytes, value);
        }
    }

    @Nested
    @DisplayName("in the key file of a host without a keyring")
    class KeyFile {

        private EnrolmentKey key;

        @BeforeEach
        void create() {
            key = new EnrolmentKey(new FileStoreName(), state, USER, new SecureRandom());
        }

        @Test
        @DisplayName("lies in the state directory and is absent before the first enrolment write")
        void absent() {
            assertAll(
                    () -> assertEquals(state.toAbsolutePath().normalize().resolve("enrolment-hmac.key"),
                            key.keyFile()),
                    () -> assertTrue(key.read().isEmpty()));
        }

        @Test
        @DisplayName("is created once with mode 0600 and reused")
        void createdOnceAndReused() {
            var created = key.readOrCreate();

            var again = key.readOrCreate();

            assertAll(
                    () -> assertArrayEquals(created.getEncoded(), again.getEncoded()),
                    () -> assertEquals("rw-------",
                            PosixFilePermissions.toString(Files.getPosixFilePermissions(key.keyFile()))),
                    () -> assertEquals(43, Files.readString(key.keyFile()).length()));
        }

        @Test
        @DisplayName("a second instance reads the key the first one created")
        void readBySecondInstance() {
            var created = key.readOrCreate();

            var read = new EnrolmentKey(new FileStoreName(), state, USER, new SecureRandom()).read();

            assertArrayEquals(created.getEncoded(), read.orElseThrow().getEncoded());
        }

        @Test
        @DisplayName("a key file with a mode broader than 0600 is unreadable as insecure, logged without the key")
        void insecureMode() throws Exception {
            key.readOrCreate();
            var content = Files.readString(key.keyFile());
            Files.setPosixFilePermissions(key.keyFile(), PosixFilePermissions.fromString("rw-r--r--"));

            var thrown = assertThrows(SecretStoreException.class, key::read);

            assertAll(
                    () -> assertEquals(Reason.INSECURE, thrown.reason()),
                    () -> assertFalse(thrown.getMessage().contains(content)),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()));
        }

        @Test
        @DisplayName("a key file owned by another user is unreadable as insecure")
        void foreignOwner() {
            key.readOrCreate();

            var foreign = new EnrolmentKey(new FileStoreName(), state, "someone-else", new SecureRandom());

            assertEquals(Reason.INSECURE, assertThrows(SecretStoreException.class, foreign::read).reason());
        }

        @ParameterizedTest
        @ValueSource(strings = {"not base64!", "c2hvcnQ", ""})
        @DisplayName("a key file that does not hold a key of 32 bytes in base64url is unreadable, never absent")
        void malformed(String content) throws Exception {
            Files.writeString(key.keyFile(), content, StandardCharsets.US_ASCII);
            Files.setPosixFilePermissions(key.keyFile(), PosixFilePermissions.fromString("rw-------"));

            assertEquals(Reason.INVALID, assertThrows(SecretStoreException.class, key::read).reason());
        }

        @Test
        @DisplayName("a key file that cannot be created is unavailable")
        void notCreatable() {
            var missing = new EnrolmentKey(new FileStoreName(), state.resolve("absent-dir"), USER, new SecureRandom());

            assertEquals(Reason.FAILED, assertThrows(SecretStoreException.class, missing::readOrCreate).reason());
        }
    }

    /**
     * A thread-safe keyring of the tests that counts its writes: the first write can be held at a gate before it
     * stores the entry, standing for an instance paused inside its create, and an action can run right after a write,
     * standing for a writer that bypassed the lock.
     */
    private static final class GatedKeyring implements SecretStore {

        private final Map<String, String> entries = new ConcurrentHashMap<>();
        private final AtomicInteger puts = new AtomicInteger();
        private final CountDownLatch paused = new CountDownLatch(1);
        private final CountDownLatch release;
        private final Consumer<Map<String, String>> afterPut;

        GatedKeyring(boolean gated, Consumer<Map<String, String>> afterPut) {
            this.release = new CountDownLatch(gated ? 1 : 0);
            this.afterPut = afterPut;
        }

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            if (puts.incrementAndGet() == 1) {
                paused.countDown();
                awaitBounded(release);
            }
            entries.put(account.account(), secret);
            afterPut.accept(entries);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            return Optional.ofNullable(entries.get(account.account()));
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return entries.remove(account.account()) != null;
        }

        @Override
        public List<CredentialAccount> entries() {
            return entries.keySet().stream().sorted().map(CredentialAccount::global).toList();
        }
    }

    private static final Duration BOUND = Duration.ofSeconds(5);

    private static void awaitBounded(CountDownLatch latch) {
        try {
            assertTrue(latch.await(BOUND.toMillis(), TimeUnit.MILLISECONDS), "latch not released in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Nested
    @DisplayName("created under the credentials lock and adopted from the keyring")
    class CreationLock {

        @TempDir
        Path base;

        @Test
        @DisplayName("the creation lock is the credentials lock and is taken before any enrolment lock")
        void creationLockOrder() {
            var locks = new FileLockManager(event -> {
            });
            var projectLock = LockKey.enrolment(base, PROJECT);
            var creationLock = EnrolmentKey.creationLock(base);

            try (var transaction = locks.openTransaction()) {
                transaction.acquire(projectLock);

                assertThrows(InternalFaultException.class, () -> transaction.acquire(creationLock));
            }
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(creationLock);
                transaction.acquire(projectLock);

                assertAll(
                        () -> assertEquals(LockKey.credentials(base), creationLock),
                        () -> assertTrue(transaction.holds(creationLock)),
                        () -> assertTrue(transaction.holds(projectLock)));
            }
        }

        @Test
        @DisplayName("a key another writer stored right after our write is adopted, not ours")
        void foreignKeyAdopted() {
            var foreign = filled((byte) 7);
            var keyring = new GatedKeyring(false, entries -> entries.put(EnrolmentKey.ACCOUNT, encode(foreign)));
            var key = new EnrolmentKey(keyring, state, USER, new FixedRandom((byte) 1, () -> {
            }));

            var created = key.readOrCreate();
            var again = key.readOrCreate();

            assertAll(
                    () -> assertArrayEquals(foreign, created.getEncoded()),
                    () -> assertArrayEquals(foreign, again.getEncoded()),
                    () -> assertEquals(1, keyring.puts.get()));
        }

        @Test
        @DisplayName("an entry that vanished after our write is unavailable, logged once without the key")
        void vanishedEntry() {
            var keyring = new GatedKeyring(false, entries -> entries.remove(EnrolmentKey.ACCOUNT));
            var key = new EnrolmentKey(keyring, state, USER, new FixedRandom((byte) 9, () -> {
            }));

            var thrown = assertThrows(SecretStoreException.class, key::readOrCreate);

            assertAll(
                    () -> assertEquals(Reason.FAILED, thrown.reason()),
                    () -> assertTrue(thrown.getMessage().contains("vanished after it was written"),
                            thrown.getMessage()),
                    () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.ERROR,
                            ERROR.ENROLMENT_KEY_UNAVAILABLE.resolveIdentifierString()),
                    () -> assertTrue(TestLoggerFactory.getTestHandler()
                            .resolveLogMessagesContaining(TestLogLevel.ERROR, encode(filled((byte) 9))).isEmpty()));
        }

        @Test
        @DisplayName("a normal create returns the key the keyring stores")
        void normalCreate() {
            var keyring = new GatedKeyring(false, entries -> {
            });
            var key = new EnrolmentKey(keyring, state, USER, new SecureRandom());

            var created = key.readOrCreate();

            assertEquals(keyring.entries.get(EnrolmentKey.ACCOUNT), encode(created.getEncoded()));
        }
    }

    /**
     * Two runtime instances over one base: two keys and two writers of the enrolments, one keyring and one lock
     * manager. The first instance pauses inside its create; the second waits for the credentials lock and adopts the
     * key the first created, so only one key is ever written.
     */
    @Nested
    @DisplayName("two runtime instances")
    class CrossInstance {

        private static final String OTHER = "other-project";

        @TempDir
        Path base;

        private final List<AuditEvent> events = new CopyOnWriteArrayList<>();
        private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();
        private EnrolmentStore store;
        private FileLockManager locks;

        @BeforeEach
        void create() throws IOException {
            Files.createDirectories(base.resolve(EnrolmentStore.DIRECTORY_NAME));
            store = new EnrolmentStore(base);
            locks = new FileLockManager(events::add);
        }

        @AfterEach
        void stop() {
            threads.close();
        }

        private Enrolments instance(EnrolmentKey key) {
            return new Enrolments(store, key, new EnrolmentFileCheck(USER), locks, projectId -> List.of(),
                    events::add);
        }

        private EnrolmentKey keyringKey(SecretStore keyring) {
            return new EnrolmentKey(keyring, base.resolve("state"), USER, new SecureRandom());
        }

        private ProjectEnrolment verifyWith(EnrolmentKey key, String projectId) {
            try (var transaction = locks.openTransaction()) {
                return new EnrolmentCheck(store, key, new EnrolmentFileCheck(USER), events::add)
                        .verify(transaction, projectId);
            }
        }

        private void assertWaitsWhileThePausedCreateHoldsTheLock(Future<?> second) {
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
        }

        @Test
        @DisplayName("two enrols at once: the second waits for the paused create, and the keyring sees one write")
        void twoEnrolsOneKey() throws Exception {
            Files.createDirectories(base.resolve("state"));
            var keyring = new GatedKeyring(true, entries -> {
            });
            var firstKey = keyringKey(keyring);
            var secondKey = keyringKey(keyring);

            var first = threads.submit(() -> instance(firstKey).enrol(EnrolmentCheckTest.enrolment(PROJECT, false),
                    false));
            awaitBounded(keyring.paused);
            var second = threads.submit(() -> instance(secondKey).enrol(EnrolmentCheckTest.enrolment(OTHER, false),
                    false));
            assertWaitsWhileThePausedCreateHoldsTheLock(second);
            var putsWhilePaused = keyring.puts.get();
            keyring.release.countDown();
            first.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            second.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);

            assertAll(
                    () -> assertEquals(1, putsWhilePaused),
                    () -> assertEquals(1, keyring.puts.get()),
                    () -> assertEquals(OTHER, verifyWith(firstKey, OTHER).projectId()),
                    () -> assertEquals(PROJECT, verifyWith(secondKey, PROJECT).projectId()));
        }

        @Test
        @DisplayName("without a gate the same two enrols also write one key")
        void twoEnrolsWithoutGate() throws Exception {
            Files.createDirectories(base.resolve("state"));
            var keyring = new GatedKeyring(false, entries -> {
            });
            var firstKey = keyringKey(keyring);
            var secondKey = keyringKey(keyring);

            var first = threads.submit(() -> instance(firstKey).enrol(EnrolmentCheckTest.enrolment(PROJECT, false),
                    false));
            var second = threads.submit(() -> instance(secondKey).enrol(EnrolmentCheckTest.enrolment(OTHER, false),
                    false));
            first.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            second.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);

            assertAll(
                    () -> assertEquals(1, keyring.puts.get()),
                    () -> assertEquals(OTHER, verifyWith(firstKey, OTHER).projectId()));
        }

        @Test
        @DisplayName("two instances over one state directory write one key file, and both enrolments verify")
        void twoEnrolsOneKeyFile() throws Exception {
            var stateDirectory = Files.createDirectories(base.resolve("state"));
            var credentials = new FileSecretStore(base.resolve("credentials"));
            var firstKey = new EnrolmentKey(credentials, stateDirectory, USER, new SecureRandom());
            var secondKey = new EnrolmentKey(credentials, stateDirectory, USER, new SecureRandom());

            var first = threads.submit(() -> instance(firstKey).enrol(EnrolmentCheckTest.enrolment(PROJECT, false),
                    false));
            var second = threads.submit(() -> instance(secondKey).enrol(EnrolmentCheckTest.enrolment(OTHER, false),
                    false));
            first.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            second.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);

            assertAll(
                    () -> assertEquals(List.of(EnrolmentKey.FILE_NAME), entries(stateDirectory)),
                    () -> assertEquals(OTHER, verifyWith(firstKey, OTHER).projectId()),
                    () -> assertEquals(PROJECT, verifyWith(secondKey, PROJECT).projectId()));
        }

        @Test
        @DisplayName("a re-baseline racing an enrol on a host without a key yet takes the same lock: one write")
        void rebaselineRacingEnrol() throws Exception {
            var stateDirectory = Files.createDirectories(base.resolve("state"));
            var fileKey = new EnrolmentKey(new FileSecretStore(base.resolve("credentials")), stateDirectory, USER,
                    new SecureRandom());
            instance(fileKey).enrol(EnrolmentCheckTest.enrolment(PROJECT, false), false);
            var keyring = new GatedKeyring(true, entries -> {
            });
            var firstKey = keyringKey(keyring);
            var secondKey = keyringKey(keyring);

            var first = threads.submit(() -> instance(firstKey).rebaseline(PROJECT));
            awaitBounded(keyring.paused);
            var second = threads.submit(() -> instance(secondKey).enrol(EnrolmentCheckTest.enrolment(OTHER, false),
                    false));
            assertWaitsWhileThePausedCreateHoldsTheLock(second);
            keyring.release.countDown();
            first.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);
            second.get(BOUND.toMillis(), TimeUnit.MILLISECONDS);

            assertAll(
                    () -> assertEquals(1, keyring.puts.get()),
                    () -> assertEquals(PROJECT, verifyWith(secondKey, PROJECT).projectId()),
                    () -> assertEquals(OTHER, verifyWith(firstKey, OTHER).projectId()));
        }
    }

    /**
     * The key in the store a supplier gives (PM-CRED-2): the recorded store, or before the choice the store the runtime
     * finds; the supplier is asked on every read, so a key moved to another store is read from there.
     */
    @Nested
    @DisplayName("through a supplier of the active store")
    class ActiveStoreSupplier {

        private final AtomicInteger asked = new AtomicInteger();

        @Test
        @DisplayName("the supplier is asked on every read and every create")
        void askedOnEveryRead() {
            var keyring = new InMemoryKeyring();
            var key = new EnrolmentKey(() -> {
                asked.incrementAndGet();
                return keyring;
            }, state, USER, new SecureRandom());

            key.read();
            key.readOrCreate();
            key.read();

            assertEquals(3, asked.get());
        }

        @Test
        @DisplayName("a switch of the supplier from the file store to a keyring reads the moved key")
        void movedKeyRead() {
            var keyring = new InMemoryKeyring();
            var current = new SecretStore[]{new FileStoreName()};
            var key = new EnrolmentKey(() -> current[0], state, USER, new SecureRandom());
            var created = key.readOrCreate();
            keyring.entries.put(EnrolmentKey.ACCOUNT, encode(created.getEncoded()));

            current[0] = keyring;
            var moved = key.read();

            assertAll(
                    () -> assertArrayEquals(created.getEncoded(), moved.orElseThrow().getEncoded()),
                    () -> assertTrue(Files.exists(key.keyFile())));
        }

        @Test
        @DisplayName("a key that was not moved is absent in the newly given store, never taken from the old one")
        void notMovedAbsent() {
            var current = new SecretStore[]{new FileStoreName()};
            var key = new EnrolmentKey(() -> current[0], state, USER, new SecureRandom());
            key.readOrCreate();

            current[0] = new InMemoryKeyring();

            assertTrue(key.read().isEmpty());
        }

        @ParameterizedTest(name = "OS store available: {0}")
        @ValueSource(booleans = {true, false})
        @DisplayName("before the choice the key is created in the found OS store, else in the key file")
        void beforeTheChoice(boolean osStoreAvailable) {
            var keyring = new InMemoryKeyring();
            var key = new EnrolmentKey(() -> osStoreAvailable ? keyring : new FileStoreName(), state, USER,
                    new SecureRandom());

            key.readOrCreate();

            assertAll(
                    () -> assertEquals(osStoreAvailable, keyring.entries.containsKey(EnrolmentKey.ACCOUNT)),
                    () -> assertEquals(!osStoreAvailable, Files.exists(key.keyFile())));
        }

        @Test
        @DisplayName("with file recorded the store of the integrity key of the choice is the key file")
        void recordedFileStore() throws Exception {
            var base = Files.createDirectories(state.resolve("pm-base"));
            var choice = CredentialStoreChoice.of(new MachinePaths(base, MachinePaths.Os.LINUX));
            try (var transaction = new FileLockManager(event -> {
                 }).openTransaction()) {
                choice.record(transaction, CredentialStoreChoice.Store.FILE,
                        CredentialStoreChoice.Writer.CREDENTIAL_STORE_ASPECT);
            }
            var key = new EnrolmentKey(choice::integrityKeyStore, state, USER, new SecureRandom());

            key.readOrCreate();

            assertTrue(Files.exists(key.keyFile()));
        }

        @Test
        @DisplayName("before the choice an outage of the OS store after the key was created there creates no second "
                + "key in the key file")
        void osOutageCreatesNoSecondKey() throws Exception {
            var keyring = new InMemoryKeyring();
            var available = new AtomicBoolean(true);
            var choice = CredentialStoreChoiceFixture.withOsStoreWhile(
                    Files.createDirectories(state.resolve("pm-base")), "de.planmarshall.mcp", keyring, available::get);
            var key = new EnrolmentKey(choice::integrityKeyStore, state, USER, new SecureRandom());
            var created = key.readOrCreate();

            available.set(false);
            var again = key.readOrCreate();

            assertAll(
                    () -> assertArrayEquals(created.getEncoded(), again.getEncoded()),
                    () -> assertTrue(keyring.entries.containsKey(EnrolmentKey.ACCOUNT)),
                    () -> assertFalse(Files.exists(key.keyFile())));
        }

        @Test
        @DisplayName("before the choice an OS store that answers after the key file was created gets no second key")
        void osAnsweringLaterGetsNoSecondKey() throws Exception {
            var keyring = new InMemoryKeyring();
            var available = new AtomicBoolean(false);
            var choice = CredentialStoreChoiceFixture.withOsStoreWhile(
                    Files.createDirectories(state.resolve("pm-base")), "de.planmarshall.mcp", keyring, available::get);
            var key = new EnrolmentKey(choice::integrityKeyStore, state, USER, new SecureRandom());
            var created = key.readOrCreate();

            available.set(true);
            var again = key.readOrCreate();

            assertAll(
                    () -> assertArrayEquals(created.getEncoded(), again.getEncoded()),
                    () -> assertTrue(Files.exists(key.keyFile())),
                    () -> assertTrue(keyring.entries.isEmpty(), keyring.entries.keySet().toString()));
        }

        @Test
        @DisplayName("the constructor with one store refuses no store")
        void fixedStoreRequired() {
            var random = new SecureRandom();
            var npe = assertThrows(NullPointerException.class,
                    () -> new EnrolmentKey((SecretStore) null, state, USER, random));

            assertEquals("store", npe.getMessage());
        }
    }

    /** The file store as the key sees it: only its name matters, its entries are never used. */
    private static final class FileStoreName implements SecretStore {

        @Override
        public String name() {
            return SecretStore.FILE;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            throw new AssertionError("the key file store must not be written through");
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            throw new AssertionError("the key file store must not be read through");
        }

        @Override
        public boolean delete(CredentialAccount account) {
            throw new AssertionError("the key file store must not be deleted through");
        }

        @Override
        public List<CredentialAccount> entries() {
            throw new AssertionError("the key file store must not be listed through");
        }
    }
}
