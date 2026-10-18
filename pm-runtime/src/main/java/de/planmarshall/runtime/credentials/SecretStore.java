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

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One credential store. The runtime uses exactly one per machine, the one the operator recorded
 * (PM-CRED-2); a secret never exists in two stores, except for the copies of a move between them,
 * which the move deletes from the old store. Implementations never log or put a secret into an
 * exception message.
 */
public interface SecretStore {

    /** Name of the macOS Keychain store. */
    String KEYCHAIN = "keychain";
    /** Name of the Linux Secret Service store. */
    String SECRET_SERVICE = "secret-service";
    /** Name of the permission-guarded file store. */
    String FILE = "file";

    /** @return {@link #KEYCHAIN}, {@link #SECRET_SERVICE} or {@link #FILE} */
    String name();

    /**
     * Stores or replaces a secret.
     *
     * @param account the account
     * @param secret  the secret
     * @throws SecretStoreException if the store fails
     */
    void put(CredentialAccount account, String secret);

    /**
     * @param account the account
     * @return the secret, or empty if the entry does not exist
     * @throws SecretStoreException if the store fails
     */
    Optional<String> get(CredentialAccount account);

    /**
     * @param account the account
     * @return whether an entry was deleted
     * @throws SecretStoreException if the store fails
     */
    boolean delete(CredentialAccount account);

    /**
     * Enumerates the store (PM-CRED-2): every account of {@code PM-MCP} it holds, global and project
     * entries, the account of the enrolment integrity key included where the key lives in this
     * store. Entries of another service name or another instance are never listed.
     *
     * @return the accounts, ordered by {@link CredentialAccount#account()}; empty for an empty store
     * @throws SecretStoreException if the store fails or holds an entry it cannot name
     */
    List<CredentialAccount> entries();

    /**
     * @param accounts the accounts of a store, in any order
     * @return the accounts ordered by {@link CredentialAccount#account()}, as {@link #entries()} returns them
     */
    static List<CredentialAccount> ordered(Collection<CredentialAccount> accounts) {
        return accounts.stream().sorted(Comparator.comparing(CredentialAccount::account)).toList();
    }

    /**
     * Resolves a credential the way every consumer does: the project entry before the global entry.
     *
     * @param project the enrolment identifier
     * @param key     the credential key
     * @return the secret, or empty if neither entry exists
     * @throws SecretStoreException if the store fails
     */
    default Optional<String> resolve(String project, String key) {
        return get(CredentialAccount.project(project, key)).or(() -> get(CredentialAccount.global(key)));
    }
}
