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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.log.PmMcpLogMessages.ERROR;
import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.credentials.SecretStoreException.Reason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The enrolment integrity key (PM-EXEC-2, PM-SEC-5): a keyring entry under the global account
 * {@code enrolment-integrity-key}, or on a host without a keyring the {@code 0600} key file below the state directory;
 * created once, then reused; absent and unreadable are told apart.
 */
@EnableTestLogger
@DisplayName("Enrolment key")
class EnrolmentKeyTest {

    private static final String USER = System.getProperty("user.name");

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
        void createdOnceAndReused() throws Exception {
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

    /** A keyring of the tests: entries in memory, an optional failure of every read, an optional refused write. */
    private static final class InMemoryKeyring implements SecretStore {

        private final Map<String, String> entries = new HashMap<>();
        private SecretStoreException failure;
        private boolean refusePut;

        @Override
        public String name() {
            return SecretStore.KEYCHAIN;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            if (refusePut) {
                throw new SecretStoreException(Reason.FAILED, "write refused");
            }
            entries.put(account.account(), secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            if (failure != null) {
                throw failure;
            }
            return Optional.ofNullable(entries.get(account.account()));
        }

        @Override
        public boolean delete(CredentialAccount account) {
            return entries.remove(account.account()) != null;
        }
    }

    /** The fallback file store as the key sees it: only its name matters, its entries are never used. */
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
    }
}
