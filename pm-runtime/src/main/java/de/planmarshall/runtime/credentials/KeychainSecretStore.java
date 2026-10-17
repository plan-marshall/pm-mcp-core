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

import static java.lang.foreign.ValueLayout.ADDRESS;

import java.io.Serial;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The macOS Keychain backend (PM-CRED-2): generic-password items with {@code kSecAttrService} = the
 * service name and {@code kSecAttrAccount} = the account, through {@code SecItemAdd},
 * {@code SecItemCopyMatching}, {@code SecItemUpdate} and {@code SecItemDelete}; the items of the
 * service name are listed through {@code SecItemCopyMatching} with {@code kSecMatchLimitAll}.
 * <p>
 * Items go to the file-based login keychain. Its default access control list, which
 * {@code SecItemAdd} attaches when no {@code kSecAttrAccess} is given, trusts exactly the creating
 * executable; every other process (a shell of the model included) gets the interactive
 * confirmation prompt. The data-protection keychain ({@code kSecUseDataProtectionKeychain}) has no
 * per-application ACL at all; it isolates by keychain access group, which needs a signed
 * {@code keychain-access-groups} entitlement, and refuses an unentitled binary with
 * {@code errSecMissingEntitlement}.
 */
public final class KeychainSecretStore implements SecretStore {

    private final String service;
    private final boolean dataProtectionKeychain;
    private final MacSecurity security;

    /**
     * @param service the service name ({@link ServiceName})
     * @throws SecretStoreException if Security.framework cannot be loaded
     */
    public KeychainSecretStore(String service) {
        this(service, false);
    }

    /**
     * @param service                the service name
     * @param dataProtectionKeychain whether to address the data-protection keychain instead of the login keychain
     * @throws SecretStoreException if Security.framework cannot be loaded
     */
    KeychainSecretStore(String service, boolean dataProtectionKeychain) {
        this.service = service;
        this.dataProtectionKeychain = dataProtectionKeychain;
        this.security = new MacSecurity();
    }

    @Override
    public String name() {
        return KEYCHAIN;
    }

    @Override
    public void put(CredentialAccount account, String secret) {
        try (var scope = security.scope()) {
            var data = scope.data(secret.getBytes(StandardCharsets.UTF_8));
            var query = query(scope, account);
            int status = security.update(query, scope.dictionary(security.kSecValueData, data));
            if (status == MacSecurity.ITEM_NOT_FOUND) {
                status = security.add(query(scope, account, security.kSecValueData, data));
            }
            check(status, "put", account);
        }
    }

    @Override
    public Optional<String> get(CredentialAccount account) {
        try (var scope = security.scope()) {
            var query = query(scope, account, security.kSecReturnData, security.kCFBooleanTrue, security.kSecMatchLimit,
                    security.kSecMatchLimitOne);
            var result = scope.arena.allocate(ADDRESS);
            int status = security.copyMatching(query, result);
            if (status == MacSecurity.ITEM_NOT_FOUND) {
                return Optional.empty();
            }
            check(status, "get", account);
            var data = scope.own(result.get(ADDRESS, 0));
            return Optional.of(security.utf8(data));
        }
    }

    @Override
    public boolean delete(CredentialAccount account) {
        try (var scope = security.scope()) {
            int status = security.delete(query(scope, account));
            if (status == MacSecurity.ITEM_NOT_FOUND) {
                return false;
            }
            check(status, "delete", account);
            return true;
        }
    }

    /**
     * Lists the generic-password items of the service name: {@code SecItemCopyMatching} with
     * {@code kSecReturnAttributes} and {@code kSecMatchLimitAll}, reading {@code kSecAttrAccount} of
     * each result dictionary; {@code errSecItemNotFound} is an empty store.
     *
     * @throws SecretStoreException {@code credential_store_locked} for a locked keychain,
     *                              {@code credential_store_failed} for any other failure or an
     *                              account that is no credential account
     */
    @Override
    public List<CredentialAccount> entries() {
        try (var scope = security.scope()) {
            var query = serviceQuery(scope, security.kSecReturnAttributes, security.kCFBooleanTrue,
                    security.kSecMatchLimit, security.kSecMatchLimitAll);
            var result = scope.arena.allocate(ADDRESS);
            int status = security.copyMatching(query, result);
            if (status == MacSecurity.ITEM_NOT_FOUND) {
                return List.of();
            }
            check(status, "list", service);
            var items = scope.own(result.get(ADDRESS, 0));
            var accounts = new ArrayList<CredentialAccount>();
            long count = security.arrayCount(items);
            for (long i = 0; i < count; i++) {
                var account = security.dictionaryString(scope, security.arrayValue(items, i), security.kSecAttrAccount)
                        .orElseThrow(() -> new SecretStoreException(SecretStoreException.Reason.FAILED,
                                "Keychain item of '" + service + "' without an account"));
                accounts.add(parse(account));
            }
            return SecretStore.ordered(accounts);
        }
    }

    private CredentialAccount parse(String account) {
        try {
            return CredentialAccount.parse(account);
        } catch (InvalidCredentialNameException e) {
            throw new SecretStoreException(SecretStoreException.Reason.FAILED,
                    "Keychain item of '" + service + "' is no credential account", e);
        }
    }

    private MemorySegment query(MacSecurity.Scope scope, CredentialAccount account, MemorySegment... extra) {
        var entries = new MemorySegment[extra.length + 2];
        entries[0] = security.kSecAttrAccount;
        entries[1] = scope.string(account.account());
        System.arraycopy(extra, 0, entries, 2, extra.length);
        return serviceQuery(scope, entries);
    }

    private MemorySegment serviceQuery(MacSecurity.Scope scope, MemorySegment... extra) {
        var base = new MemorySegment[]{security.kSecClass, security.kSecClassGenericPassword, security.kSecAttrService,
                scope.string(service)};
        int length = base.length + extra.length + (dataProtectionKeychain ? 2 : 0);
        var entries = Arrays.copyOf(base, length);
        System.arraycopy(extra, 0, entries, base.length, extra.length);
        if (dataProtectionKeychain) {
            entries[length - 2] = security.kSecUseDataProtectionKeychain;
            entries[length - 1] = security.kCFBooleanTrue;
        }
        return scope.dictionary(entries);
    }

    private void check(int status, String operation, CredentialAccount account) {
        check(status, operation + " of '" + account.account() + "' in", service);
    }

    private static void check(int status, String operation, String service) {
        if (status == MacSecurity.SUCCESS) {
            return;
        }
        var reason = status == MacSecurity.INTERACTION_NOT_ALLOWED || status == MacSecurity.AUTH_FAILED
                ? SecretStoreException.Reason.LOCKED
                : SecretStoreException.Reason.FAILED;
        throw new KeychainException(reason, status,
                "Keychain " + operation + " '" + service + "' failed with OSStatus " + status);
    }

    /**
     * A failed {@code SecItem} call with its {@code OSStatus}.
     */
    static final class KeychainException extends SecretStoreException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** The {@code OSStatus}. */
        private final int status;

        KeychainException(Reason reason, int status, String message) {
            super(reason, message);
            this.status = status;
        }

        /** @return the {@code OSStatus} */
        int status() {
            return status;
        }
    }
}
