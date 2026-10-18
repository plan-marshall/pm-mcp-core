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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.runtime.credentials.CredentialAccount;
import de.planmarshall.runtime.credentials.SecretStore;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.credentials.SecretStoreException.Reason;

/**
 * A keyring of the tests of the enrolment key: entries in memory, an optional failure of every read, an optional
 * refused write. It is the active key source "keyring" as the key sees it, without a platform keyring.
 */
final class InMemoryKeyring implements SecretStore {

    /** The entries by account name. */
    final Map<String, String> entries = new HashMap<>();

    /** A failure every read throws, if set. */
    SecretStoreException failure;

    /** Whether every write is refused. */
    boolean refusePut;

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

    @Override
    public List<CredentialAccount> entries() {
        if (failure != null) {
            throw failure;
        }
        return entries.keySet().stream().sorted().map(InMemoryKeyring::account).toList();
    }

    private static CredentialAccount account(String name) {
        int slash = name.indexOf('/');
        return slash < 0 ? CredentialAccount.global(name)
                : CredentialAccount.project(name.substring(0, slash), name.substring(slash + 1));
    }
}
