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

import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.credentials.CredentialStoreCheck.CheckLine;
import de.planmarshall.runtime.credentials.CredentialStoreCheck.Outcome;
import de.planmarshall.runtime.credentials.CredentialStoreCheck.Step;

/**
 * The check of the OS store before it is recorded (PM-CRED-2): write, read back and delete one test entry, each one
 * check line; a failed step ends the check and leaves no test entry behind.
 */
@DisplayName("CredentialStoreCheck: the OS store before it is recorded")
class CredentialStoreCheckTest {

    private static final CredentialAccount TEST_ENTRY = CredentialAccount.global(CredentialStoreCheck.ACCOUNT);

    @TempDir
    Path base;

    private final CredentialStoreCheck check = new CredentialStoreCheck(new SecureRandom());
    private final ScriptedStore store = new ScriptedStore();

    @Test
    @DisplayName("a passing check reports write, read back and delete as three ok lines and leaves no entry")
    void passes() {
        var result = check.check(store);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertEquals(List.of(Step.WRITE, Step.READ_BACK, Step.DELETE),
                        result.lines().stream().map(CheckLine::step).toList()),
                () -> assertTrue(result.lines().stream().allMatch(line -> line.outcome() == Outcome.OK)),
                () -> assertEquals("credential_store_check.read_back.ok", result.lines().get(1).messageKey()),
                () -> assertEquals(List.of(SecretStore.SECRET_SERVICE, CredentialStoreCheck.ACCOUNT),
                        result.lines().getFirst().arguments()),
                () -> assertEquals(List.of("put", "get", "delete"), store.calls),
                () -> assertTrue(store.secrets.isEmpty()));
    }

    @Test
    @DisplayName("a failed write ends the check with the store's answer and deletes what was written")
    void writeFails() {
        store.failOn = "put";

        var result = check.check(store);

        var line = result.lines().getFirst();
        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(1, result.lines().size()),
                () -> assertEquals(Outcome.FAILED, line.outcome()),
                () -> assertEquals("failed", line.outcome().code()),
                () -> assertEquals("credential_store_check.write.failed", line.messageKey()),
                () -> assertEquals(Optional.of("credential_store_locked: put refused"), line.answer()),
                () -> assertEquals(List.of("put", "delete"), store.calls),
                () -> assertTrue(store.secrets.isEmpty()));
    }

    @Test
    @DisplayName("a read-back of another value fails the second line and deletes the test entry")
    void readBackDiffers() {
        store.readBack(Optional.of("another value"));

        var result = check.check(store);

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(List.of(Outcome.OK, Outcome.FAILED),
                        result.lines().stream().map(CheckLine::outcome).toList()),
                () -> assertEquals("credential_store_check.read_back.differs", result.lines().get(1).messageKey()),
                () -> assertEquals(Optional.empty(), result.lines().get(1).answer()),
                () -> assertEquals(List.of("put", "get", "delete"), store.calls),
                () -> assertTrue(store.secrets.isEmpty()));
    }

    @Test
    @DisplayName("a read-back that finds nothing fails as missing")
    void readBackMissing() {
        store.readBack(Optional.empty());

        var result = check.check(store);

        assertAll(
                () -> assertEquals("credential_store_check.read_back.missing", result.lines().get(1).messageKey()),
                () -> assertTrue(store.secrets.isEmpty()));
    }

    @Test
    @DisplayName("a read-back the store refuses fails with its answer and deletes the test entry")
    void readBackFails() {
        store.failOn = "get";

        var result = check.check(store);

        assertAll(
                () -> assertEquals("credential_store_check.read_back.failed", result.lines().get(1).messageKey()),
                () -> assertEquals(Optional.of("credential_store_locked: get refused"), result.lines().get(1).answer()),
                () -> assertTrue(store.secrets.isEmpty()));
    }

    @Test
    @DisplayName("a failed delete fails the third line with the store's answer")
    void deleteFails() {
        store.failOn = "delete";
        store.failures = 1;

        var result = check.check(store);

        var line = result.lines().get(2);
        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(Step.DELETE, line.step()),
                () -> assertEquals(Outcome.FAILED, line.outcome()),
                () -> assertEquals("credential_store_check.delete.failed", line.messageKey()),
                () -> assertEquals(Optional.of("credential_store_locked: delete refused"), line.answer()),
                () -> assertEquals(List.of("put", "get", "delete", "delete"), store.calls),
                () -> assertTrue(store.secrets.isEmpty()));
    }

    @Test
    @DisplayName("a delete that finds no entry fails as missing")
    void deleteMissing() {
        store.deleteFinds = false;

        var result = check.check(store);

        assertEquals("credential_store_check.delete.missing", result.lines().get(2).messageKey());
    }

    @Test
    @DisplayName("the test value never appears in a check line")
    void noValueInLines() {
        store.readBack(Optional.of("wrong"));
        var result = check.check(store);

        var written = store.written.getFirst();
        assertTrue(result.lines().stream().noneMatch(line -> line.toString().contains(written)));
    }

    @Test
    @DisplayName("an unavailable OS store fails the write line with credential_store_unavailable")
    void unavailable() {
        var choice = new CredentialStoreChoice(base, "de.planmarshall.mcp", MachinePaths.Os.LINUX, Map.of(),
                new CredentialStoreChoice.Keyrings() {

                    @Override
                    public SecretStore keychain(String service) {
                        return store;
                    }

                    @Override
                    public Optional<SecretStore> secretService(Path socket, String service) {
                        return Optional.of(store);
                    }
                });

        var result = check.check(choice);

        var line = result.lines().getFirst();
        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(1, result.lines().size()),
                () -> assertEquals("credential_store_check.write.unavailable", line.messageKey()),
                () -> assertTrue(line.answer().orElseThrow().startsWith("credential_store_unavailable: "),
                        line.answer().orElseThrow()),
                () -> assertTrue(store.calls.isEmpty()));
    }

    @Test
    @DisplayName("an available OS store of the choice is checked")
    void availableChoice() {
        var choice = new CredentialStoreChoice(base, "de.planmarshall.mcp", MachinePaths.Os.MACOS, Map.of(),
                new CredentialStoreChoice.Keyrings() {

                    @Override
                    public SecretStore keychain(String service) {
                        return store;
                    }

                    @Override
                    public Optional<SecretStore> secretService(Path socket, String service) {
                        return Optional.empty();
                    }
                });

        assertTrue(check.check(choice).passed());
    }

    /** A store whose answers are scripted per test. */
    private static final class ScriptedStore implements SecretStore {

        private final Map<String, String> secrets = new TreeMap<>();
        private final List<String> calls = new ArrayList<>();
        private final List<String> written = new ArrayList<>();
        private String failOn = "";
        private int failures = Integer.MAX_VALUE;
        private Optional<String> readBack = Optional.empty();
        private boolean readBackScripted;
        private boolean deleteFinds = true;

        void readBack(Optional<String> answer) {
            readBack = answer;
            readBackScripted = true;
        }

        private void failIfAsked(String operation) {
            calls.add(operation);
            if (failOn.equals(operation) && failures > 0) {
                failures--;
                throw new SecretStoreException(SecretStoreException.Reason.LOCKED, operation + " refused");
            }
        }

        @Override
        public String name() {
            return SECRET_SERVICE;
        }

        @Override
        public void put(CredentialAccount account, String secret) {
            failIfAsked("put");
            written.add(secret);
            secrets.put(account.account(), secret);
        }

        @Override
        public Optional<String> get(CredentialAccount account) {
            failIfAsked("get");
            return readBackScripted ? readBack : Optional.ofNullable(secrets.get(account.account()));
        }

        @Override
        public boolean delete(CredentialAccount account) {
            failIfAsked("delete");
            var removed = secrets.remove(account.account()) != null;
            return deleteFinds && removed;
        }

        @Override
        public List<CredentialAccount> entries() {
            return secrets.keySet().stream().map(CredentialAccount::global).toList();
        }
    }

    @Test
    @DisplayName("the test entry is the fixed global account of PM-MCP")
    void account() {
        assertEquals("credential-store-check", TEST_ENTRY.account());
    }
}
